package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.api.CourseFixtures.Run;
import com.runtrack.course.usecases.port.ActivityArchive;
import com.runtrack.shared.id.ActivityId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * L'historisation contre une vraie base : c'est le seul endroit où la géométrie PostGIS est
 * réellement construite et validée, et où la purge de rétention efface de vraies lignes.
 */
class ActivityArchiveApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ActivityArchive archive;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private void finish(Account owner, Run run) throws Exception {
        mvc.perform(post("/api/v1/activities/" + run.id() + "/finish")
                .header("Authorization", owner.bearer())).andExpect(status().isNoContent());
    }

    /**
     * Une course courte, et elle ne peut pas être autre chose.
     *
     * <p>Le filtre du §4 écarte tout point daté à plus de soixante secondes au-delà de l'heure
     * serveur. Une course démarrée à l'instant ne peut donc recevoir qu'une minute de trace : y
     * faire tenir quatre kilomètres demanderait une vitesse que le même filtre refuserait comme
     * invraisemblable. Le découpage en tronçons sur plusieurs kilomètres se vérifie donc en
     * mémoire — {@code SplitCalculatorTest}, {@code ActivityArchivalTest} —, et ce qui se vérifie
     * ici est ce qu'eux ne peuvent pas prouver : la géométrie PostGIS et la purge de rétention.
     */
    private Run aFinishedRunOf(Account owner, int points) throws Exception {
        Run run = fixtures.startRun(owner);
        fixtures.ingest(owner, run, 1, points);
        finish(owner, run);
        return run;
    }

    @Test
    void finishingProducesAPolylineAndItsSplits() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = aFinishedRunOf(marie, 55);

        mvc.perform(get("/api/v1/activities/" + run.id() + "/track")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.polyline").isNotEmpty())
                .andExpect(jsonPath("$.rawPointCount").value(55))
                .andExpect(jsonPath("$.frozenAt").exists())
                .andExpect(jsonPath("$.pointsPurgedAt").doesNotExist());

        mvc.perform(get("/api/v1/activities/" + run.id() + "/splits")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kilometerIndex").value(1))
                // Cent-soixante mètres : le tronçon est entamé, jamais complet.
                .andExpect(jsonPath("$.items[0].complete").value(false));
    }

    /** La géométrie est construite par PostGIS : si le WKT était mal formé, l'insertion échouerait. */
    @Test
    void theGeometryIsAValidLineString() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = aFinishedRunOf(marie, 50);

        String geometryType = jdbc.queryForObject(
                "SELECT ST_GeometryType(geom::geometry) FROM activity_tracks WHERE activity_id = ?",
                String.class, java.util.UUID.fromString(run.id()));

        assertThat(geometryType).isEqualTo("ST_LineString");
    }

    /** Une course en cours n'a pas de trace figée : son tracé se suit en direct. */
    @Test
    void aRunningActivityHasNoArchivedTrackYet() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = fixtures.startRun(marie);
        fixtures.ingest(marie, run, 1, 10);

        mvc.perform(get("/api/v1/activities/" + run.id() + "/track")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRACK_NOT_ARCHIVED"));
    }

    /** La trace suit la visibilité de sa course, comme toute autre lecture (§5.5). */
    @Test
    void aPrivateRunKeepsItsTrackToItself() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        Run run = fixtures.startRun(marie, "PRIVATE");
        fixtures.ingest(marie, run, 1, 100);
        finish(marie, run);

        mvc.perform(get("/api/v1/activities/" + run.id() + "/track")
                        .header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    /**
     * La rétention du lot 1 : passé 90 jours, les points bruts s'effacent et la trace reste.
     *
     * <p>La course est vieillie en base plutôt qu'attendue : c'est la seule façon d'exercer une
     * règle qui se compte en mois.
     */
    @Test
    void afterRetentionTheRawPointsAreErasedAndTheTrackRemains() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = aFinishedRunOf(marie, 50);
        var activityId = new ActivityId(java.util.UUID.fromString(run.id()));

        Instant now = Instant.parse("2026-12-31T03:40:00Z");
        jdbc.update("UPDATE activity_tracks SET frozen_at = ? WHERE activity_id = ?",
                now.minus(Duration.ofDays(120)).atOffset(java.time.ZoneOffset.UTC),
                activityId.value());

        assertThat(archive.purgePointsArchivedBefore(now.minus(Duration.ofDays(90)), now, 100))
                .isPositive();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM track_points WHERE activity_id = ?",
                Long.class, activityId.value())).isZero();
        assertThat(archive.find(activityId)).get()
                .extracting(track -> track.pointsPurgedAt().isPresent())
                .isEqualTo(true);

        // La trace reste affichable : c'est tout l'objet de la rétention.
        mvc.perform(get("/api/v1/activities/" + run.id() + "/track")
                        .header("Authorization", marie.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsPurgedAt").exists());
    }

    /** Une course récente n'est jamais purgée, même si le travail passe. */
    @Test
    void aRecentlyArchivedRunKeepsItsPoints() throws Exception {
        Account marie = fixtures.newAccount();
        Run run = aFinishedRunOf(marie, 50);
        Instant now = Instant.now();

        archive.purgePointsArchivedBefore(now.minus(Duration.ofDays(90)), now, 100);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM track_points WHERE activity_id = ?",
                Long.class, java.util.UUID.fromString(run.id()))).isEqualTo(50);
    }
}

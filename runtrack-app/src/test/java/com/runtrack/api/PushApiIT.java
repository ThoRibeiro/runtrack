package com.runtrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrack.api.CourseFixtures.Account;
import com.runtrack.notification.usecases.port.PushThrottle;
import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Les appareils, les heures calmes et le garde-fou anti-spam, contre une vraie base et un vrai
 * Dragonfly.
 *
 * <p>L'envoyeur reste celui du journal : c'est le réglage par défaut, et c'est ce qui permet de
 * vérifier toute la chaîne sans compte Firebase ni réseau sortant — exactement ce que le §7
 * demandait de ce troisième envoyeur.
 */
class PushApiIT extends ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PushThrottle throttle;

    private CourseFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new CourseFixtures(mvc, json);
    }

    private void registerDevice(Account owner, String token) throws Exception {
        mvc.perform(post("/api/v1/users/me/devices")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"platform\":\"ANDROID\"}".formatted(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token));
    }

    private int deviceCountOf(Account owner) throws Exception {
        var listed = mvc.perform(get("/api/v1/users/me/devices")
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(listed.getResponse().getContentAsString()).get("items").size();
    }

    @Test
    void registeringThenForgettingADevice() throws Exception {
        Account marie = fixtures.newAccount();
        String token = "token-" + UUID.randomUUID();

        registerDevice(marie, token);
        assertThat(deviceCountOf(marie)).isEqualTo(1);

        mvc.perform(delete("/api/v1/users/me/devices/" + token)
                .header("Authorization", marie.bearer())).andExpect(status().isNoContent());
        assertThat(deviceCountOf(marie)).isZero();
    }

    /** Le client réenregistre à chaque lancement : ce doit être sans effet visible. */
    @Test
    void registeringTheSameDeviceTwiceLeavesOneDevice() throws Exception {
        Account marie = fixtures.newAccount();
        String token = "token-" + UUID.randomUUID();

        registerDevice(marie, token);
        registerDevice(marie, token);

        assertThat(deviceCountOf(marie)).isEqualTo(1);
    }

    /**
     * Un téléphone prêté ou revendu garde son jeton et change de propriétaire.
     *
     * <p>Refuser le second enregistrement laisserait les push de l'ancien compte continuer à
     * arriver sur un appareil qui ne lui appartient plus.
     */
    @Test
    void aTokenFollowsThePhoneNotTheAccount() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        String token = "token-" + UUID.randomUUID();

        registerDevice(marie, token);
        registerDevice(paul, token);

        assertThat(deviceCountOf(marie)).isZero();
        assertThat(deviceCountOf(paul)).isEqualTo(1);
    }

    @Test
    void nobodyForgetsSomeoneElsesDevice() throws Exception {
        Account marie = fixtures.newAccount();
        Account paul = fixtures.newAccount();
        String token = "token-" + UUID.randomUUID();
        registerDevice(marie, token);

        mvc.perform(delete("/api/v1/users/me/devices/" + token)
                        .header("Authorization", paul.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
        assertThat(deviceCountOf(marie)).isEqualTo(1);
    }

    @Test
    void anUnknownPlatformIsARequestError() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(post("/api/v1/users/me/devices")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"platform\":\"CARRIER_PIGEON\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    }

    @Test
    void declaringADeviceNeedsAnAccount() throws Exception {
        mvc.perform(post("/api/v1/users/me/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"abc\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quietHoursAreStoredWithTheirTimeZone() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(patch("/api/v1/users/me/notification-preferences")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"muted":[],"quietHours":{"from":"22:00","to":"07:00",
                                 "zone":"Europe/Paris"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHours.zone").value("Europe/Paris"));

        mvc.perform(get("/api/v1/users/me/notification-preferences")
                        .header("Authorization", marie.bearer()))
                .andExpect(jsonPath("$.quietHours.from").value("22:00:00"))
                .andExpect(jsonPath("$.quietHours.to").value("07:00:00"));
    }

    /** Envoyer des préférences sans plage efface celle qui existait. */
    @Test
    void omittingQuietHoursClearsThem() throws Exception {
        Account marie = fixtures.newAccount();
        mvc.perform(patch("/api/v1/users/me/notification-preferences")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"muted":[],"quietHours":{"from":"22:00","to":"07:00","zone":"UTC"}}
                                """))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/users/me/notification-preferences")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muted\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHours").doesNotExist());
    }

    @Test
    void anUnknownTimeZoneIsARequestError() throws Exception {
        Account marie = fixtures.newAccount();

        mvc.perform(patch("/api/v1/users/me/notification-preferences")
                        .header("Authorization", marie.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"muted":[],"quietHours":{"from":"22:00","to":"07:00",
                                 "zone":"Mars/Olympus"}}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_VALUE"));
    }

    /**
     * Le garde-fou anti-spam contre le vrai Dragonfly.
     *
     * <p>C'est l'atomicité de {@code SET NX EX} qui fait la garantie, et aucun double en mémoire ne
     * peut en témoigner : il faut la commande réelle.
     */
    @Test
    void theAntiSpamGuardLetsOnlyTheFirstPushThrough() {
        UserId marie = new UserId(UUID.randomUUID());
        UserId paul = new UserId(UUID.randomUUID());
        Duration window = Duration.ofMinutes(30);

        assertThat(throttle.allow(marie, paul, NotificationType.FRIEND_STARTED_ACTIVITY, window)).isTrue();
        assertThat(throttle.allow(marie, paul, NotificationType.FRIEND_STARTED_ACTIVITY, window)).isFalse();
    }

    /** Il est par couple et par nature : ce qu'un coureur consomme ne bloque pas les autres. */
    @Test
    void theGuardIsScopedToOnePairAndOneNature() {
        UserId marie = new UserId(UUID.randomUUID());
        UserId paul = new UserId(UUID.randomUUID());
        UserId lea = new UserId(UUID.randomUUID());
        Duration window = Duration.ofMinutes(30);

        throttle.allow(marie, paul, NotificationType.FRIEND_STARTED_ACTIVITY, window);

        assertThat(throttle.allow(marie, lea, NotificationType.FRIEND_STARTED_ACTIVITY, window)).isTrue();
        assertThat(throttle.allow(marie, paul, NotificationType.FRIEND_FINISHED_ACTIVITY, window)).isTrue();
    }
}

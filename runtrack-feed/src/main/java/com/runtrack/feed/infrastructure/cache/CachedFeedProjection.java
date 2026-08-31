package com.runtrack.feed.infrastructure.cache;

import com.runtrack.feed.usecases.model.entry.FeedEntry;
import com.runtrack.feed.usecases.port.FeedProjection;
import com.runtrack.platform.cache.CacheGateway;
import com.runtrack.platform.cache.CacheKey;
import com.runtrack.platform.cache.CacheProperties;
import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * La première page du fil, mémorisée trente secondes.
 *
 * <p>C'est la requête ouverte à chaque lancement de l'application, et la seule du fil dont le
 * résultat se répète : les pages suivantes portent un curseur, donc chacune est unique et n'a
 * personne à qui servir. La cacher est ce que le §6 appelle {@code feed:{userId}:head}.
 *
 * <p><b>Aucune invalidation ciblée, et c'est un écart argumenté au §6.</b> Le tableau y prévoit
 * « nouvelle activité d'un suivi » ; l'appliquer demanderait, au démarrage de chaque course, de
 * parcourir tous les abonnés du coureur pour purger leur tête de fil — c'est-à-dire exactement le
 * fan-out à l'écriture que la décision du lot 1 a rejeté, réintroduit par la porte du cache. Sur un
 * compte très suivi, ce serait des dizaines de milliers d'évictions par course démarrée.
 *
 * <p>Avec trente secondes de TTL, <b>l'expiration est l'invalidation</b>. Le prix est qu'une course
 * démarrée peut mettre une demi-minute à apparaître dans le fil d'un ami — qui, lui, est déjà
 * prévenu par une notification et par le direct, tous deux immédiats. C'est le seul endroit du
 * système où l'on accepte un retard visible, et c'est celui où il coûte le moins.
 *
 * <p>Décorateur du port, comme l'impose le §6 : {@code FeedReader} ignore l'existence du cache,
 * et une seule des sept opérations passe réellement par lui.
 */
@Component
@org.springframework.context.annotation.Primary
class CachedFeedProjection implements FeedProjection {

    private final FeedProjection delegate;
    private final CacheGateway cache;
    private final CacheProperties properties;

    CachedFeedProjection(
            @org.springframework.beans.factory.annotation.Qualifier("jdbcFeedProjection")
            FeedProjection delegate, CacheGateway cache, CacheProperties properties) {

        this.delegate = delegate;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public List<FeedEntry> headOf(UserId reader, java.util.Collection<UserId> owners, int limit) {
        StoredHead stored = cache.getOrLoad(
                CacheKey.feedHead(reader.toString()), StoredHead.class, properties.feedHeadTtl(),
                () -> StoredHead.of(delegate.headOf(reader, owners, limit)));
        return stored == null ? List.of() : stored.toEntries();
    }

    // Tout le reste passe droit. Une page à curseur est unique, et les écritures de projection
    // n'ont évidemment rien à faire dans un cache.

    @Override
    public List<FeedEntry> page(java.util.Collection<UserId> owners, Optional<Instant> before,
            int limit) {

        return delegate.page(owners, before, limit);
    }

    @Override
    public void upsert(FeedEntry entry) {
        delegate.upsert(entry);
    }

    @Override
    public void updateVisibility(ActivityId activityId, String effectiveScope) {
        delegate.updateVisibility(activityId, effectiveScope);
    }

    @Override
    public void remove(ActivityId activityId) {
        delegate.remove(activityId);
    }

    @Override
    public void adjustLikes(ActivityId activityId, int delta) {
        delegate.adjustLikes(activityId, delta);
    }

    @Override
    public void adjustComments(ActivityId activityId, int delta) {
        delegate.adjustComments(activityId, delta);
    }

    @Override
    public Optional<FeedEntry> find(ActivityId activityId) {
        return delegate.find(activityId);
    }

    /**
     * La forme mémorisée.
     *
     * <p>Un record à plat plutôt que {@link FeedEntry} directement : celui-ci porte des
     * {@code Optional} et des types du domaine, et la forme de ce qui dort dans un cache doit
     * pouvoir évoluer sans que le domaine ait à s'y plier. Un changement de forme passe en
     * {@code v2} de clé, comme l'exige le §6.
     */
    record StoredHead(List<StoredEntry> entries) {

        static StoredHead of(List<FeedEntry> entries) {
            return new StoredHead(entries.stream().map(StoredEntry::of).toList());
        }

        List<FeedEntry> toEntries() {
            return entries.stream().map(StoredEntry::toEntry).toList();
        }
    }

    record StoredEntry(
            UUID activityId,
            UUID ownerId,
            String type,
            String title,
            String status,
            String effectiveScope,
            double distanceMeters,
            long movingTimeSeconds,
            Instant startedAt,
            Instant endedAt,
            long likeCount,
            long commentCount) {

        static StoredEntry of(FeedEntry entry) {
            return new StoredEntry(
                    entry.activityId().value(), entry.ownerId().value(), entry.type(), entry.title(),
                    entry.status(), entry.effectiveScope().name(), entry.distanceMeters(),
                    entry.movingTimeSeconds(), entry.startedAt(), entry.endedAt().orElse(null),
                    entry.likeCount(), entry.commentCount());
        }

        FeedEntry toEntry() {
            return new FeedEntry(
                    new ActivityId(activityId), new UserId(ownerId), type, title, status,
                    AudienceScope.valueOf(effectiveScope), distanceMeters, movingTimeSeconds,
                    startedAt, Optional.ofNullable(endedAt), likeCount, commentCount);
        }
    }
}

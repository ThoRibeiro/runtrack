package com.runtrack.course.internal.infra.rest;

import com.runtrack.course.internal.application.IngestionResult;
import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.course.internal.infra.rest.dto.PointDtos;
import com.runtrack.shared.measure.Elevation;
import com.runtrack.shared.measure.GeoPoint;
import java.util.List;
import java.util.OptionalInt;

/** DTO de points vers domaine, et résultat d'ingestion vers DTO. */
final class PointMapper {

    private PointMapper() {
    }

    static List<TrackPoint> toDomain(List<PointDtos.PointDto> points) {
        return points.stream().map(PointMapper::toDomain).toList();
    }

    private static TrackPoint toDomain(PointDtos.PointDto point) {
        return new TrackPoint(
                point.sequenceNumber(),
                new GeoPoint(point.latitude(), point.longitude()),
                Elevation.ofMeters(point.elevation()),
                point.recordedAt(),
                point.accuracyMeters(),
                optional(point.heartRate()),
                optional(point.cadence()));
    }

    static PointDtos.IngestionResponse toResponse(IngestionResult result) {
        return new PointDtos.IngestionResponse(
                ActivityMapper.toStats(result.stats()),
                result.lastAcceptedSequence(),
                result.acceptedCount(),
                result.rejected().stream()
                        .map(rejected -> new PointDtos.RejectedPoint(
                                rejected.sequenceNumber(), rejected.reason().name()))
                        .toList());
    }

    /** Un capteur absent est une donnée manquante, pas un zéro : {@code null} devient vide. */
    private static OptionalInt optional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}

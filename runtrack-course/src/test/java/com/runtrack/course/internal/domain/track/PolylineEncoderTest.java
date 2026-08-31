package com.runtrack.course.internal.domain.track;

import static org.assertj.core.api.Assertions.assertThat;

import com.runtrack.shared.measure.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolylineEncoderTest {

    /**
     * Le vecteur de référence de la documentation Google.
     *
     * <p>Le figer ici, c'est garantir qu'une carte tierce saura lire ce qu'on produit : le format
     * est un contrat avec des bibliothèques qu'on ne contrôle pas.
     */
    @Test
    void matchesTheReferenceVectorOfTheFormat() {
        List<GeoPoint> positions = List.of(
                new GeoPoint(38.5, -120.2),
                new GeoPoint(40.7, -120.95),
                new GeoPoint(43.252, -126.453));

        assertThat(PolylineEncoder.encode(positions)).isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }

    @Test
    void anEmptyTrackEncodesToNothing() {
        assertThat(PolylineEncoder.encode(List.of())).isEmpty();
    }

    @Test
    void aSinglePositionStillEncodes() {
        assertThat(PolylineEncoder.encode(List.of(new GeoPoint(50.6292, 3.0573))).length())
                .isPositive();
    }

    /**
     * Les écarts partent des valeurs arrondies, pas des originales.
     *
     * <p>Sans cela, chaque point ajouterait son erreur d'arrondi à la précédente et la trace
     * dériverait de plusieurs mètres au bout d'une longue sortie — visible sur une carte.
     */
    @Test
    void roundingErrorsDoNotAccumulateAlongTheTrack() {
        double degreesPerMeter = 1 / 111_320d;
        var positions = new java.util.ArrayList<GeoPoint>();
        for (int index = 0; index < 500; index++) {
            positions.add(new GeoPoint(50.6292 + index * degreesPerMeter, 3.0573));
        }

        String encoded = PolylineEncoder.encode(positions);
        double lastLatitude = decodeLastLatitude(encoded);

        assertThat(lastLatitude).isCloseTo(positions.getLast().latitude(),
                org.assertj.core.data.Offset.offset(1e-5));
    }

    /** Décodage minimal : on ne cumule que la latitude, seule dimension que le test fait varier. */
    private static double decodeLastLatitude(String encoded) {
        long latitude = 0;
        int index = 0;
        boolean readingLatitude = true;
        while (index < encoded.length()) {
            long result = 0;
            int shift = 0;
            int character;
            do {
                character = encoded.charAt(index++) - 63;
                result |= (long) (character & 0x1f) << shift;
                shift += 5;
            } while (character >= 0x20);
            long value = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
            if (readingLatitude) {
                latitude += value;
            }
            readingLatitude = !readingLatitude;
        }
        return latitude / 1e5;
    }
}

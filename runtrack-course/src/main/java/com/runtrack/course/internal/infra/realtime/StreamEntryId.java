package com.runtrack.course.internal.infra.realtime;

/**
 * La comparaison de deux identifiants d'entrée de stream.
 *
 * <p>Un identifiant Redis a la forme {@code millisecondes-séquence}. Les comparer comme des
 * chaînes donne un ordre faux dès que les nombres n'ont pas la même longueur — {@code "9-0"}
 * passerait après {@code "10-0"} — et c'est de cette comparaison que dépend la détection d'un
 * trou dans la reprise.
 */
final class StreamEntryId {

    private StreamEntryId() {
    }

    static int compare(String left, String right) {
        long[] parsedLeft = parse(left);
        long[] parsedRight = parse(right);
        int byTime = Long.compare(parsedLeft[0], parsedRight[0]);
        return byTime != 0 ? byTime : Long.compare(parsedLeft[1], parsedRight[1]);
    }

    /** Une séquence absente vaut zéro : c'est la convention de Redis pour {@code "1700000000"}. */
    private static long[] parse(String id) {
        int separator = id.indexOf('-');
        if (separator < 0) {
            return new long[] {Long.parseLong(id), 0};
        }
        return new long[] {
            Long.parseLong(id.substring(0, separator)),
            Long.parseLong(id.substring(separator + 1)),
        };
    }
}

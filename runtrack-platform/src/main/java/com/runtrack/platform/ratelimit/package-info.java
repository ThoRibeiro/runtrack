/**
 * Le comptage d'appels dans Dragonfly, pour brider ce qui doit l'être (§9).
 *
 * <p>Dans {@code platform} parce que trois modules en ont besoin — l'authentification, l'ingestion
 * de points et les commentaires — et qu'une limite implémentée trois fois se règle trois fois.
 */
package com.runtrack.platform.ratelimit;

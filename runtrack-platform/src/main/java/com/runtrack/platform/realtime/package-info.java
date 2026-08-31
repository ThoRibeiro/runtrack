/**
 * Le direct, en technique pure : Stream Dragonfly pour le fan-out entre instances, registre
 * d'émetteurs SSE pour la diffusion locale.
 *
 * <p>Ici, un événement n'a pas de sens : c'est un nom et une charge utile déjà sérialisée. Ce
 * sont les modules qui savent ce qu'ils diffusent — {@code course} les positions d'une course,
 * {@code notification} les notifications d'un utilisateur — et cette couche transporte.
 */
package com.runtrack.platform.realtime;

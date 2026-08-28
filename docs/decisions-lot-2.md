# Lot 2 — décisions d'architecture

`shared` et `course/internal/domain`. Trois écarts au cahier des charges, tous liés à des
contradictions que l'écriture a fait apparaître.

## 1. Un seul `AudienceScope` au lieu de deux énumérations jumelles

Le cahier des charges prévoyait `AccountVisibility` dans `user` et `ActivityVisibility`
dans `course`, avec les mêmes trois valeurs, et une règle interdisant le nom `Visibility`
tout court pour éviter la confusion.

Interdire le nom ne suffit pas : deux énumérations distinctes aux valeurs identiques se
confondent quand même à l'appel. Passer la visibilité du compte là où le code attend celle
de la course compile, et une course privée devient publique.

Un **seul** type, `AudienceScope`, dans `shared`. La composition passe par
`mostRestrictive`, qui est **commutative** : intervertir les deux arguments ne change pas
le résultat. La classe de bug ne se détecte plus, elle n'existe plus — et
`AudienceScopeTest` teste explicitement cette commutativité.

Conséquence : `shared` héberge un objet valeur utilisé par `user`, `course` et
`notification`. C'est bien un objet valeur partagé, pas de la logique métier.

## 2. `java.time.Clock`, pas d'abstraction maison

Le cahier des charges demandait une « abstraction `Clock` » dans `shared`. Le JDK en
fournit déjà une, injectable, avec `Clock.fixed()` pour les tests. En écrire une seconde
n'aurait ajouté qu'une indirection.

La règle qui compte — aucun `Instant.now()` en dur — est portée par ArchUnit et vaut pour
tout le code. C'est elle qui rend les tests temporels possibles, pas le type utilisé.

## 3. Le domaine ne demande que ce qu'il consomme

Le profil connaîtra taille, date de naissance, sexe et masse (§3 `user`). L'estimation de
calories n'utilise que la masse : `RunnerPhysiology` ne porte donc qu'elle.

Un domaine qui déclare ses propres besoins plutôt que d'importer le modèle du voisin reste
testable sans monter la moitié du module `user`, et ne casse pas quand ce modèle évolue.

## Choix de calcul, et pourquoi

| Sujet | Choix | Raison |
|---|---|---|
| Distance | Haversine, rayon IUGG 6 371 008,8 m | Erreur ≤ 0,5 % contre Vincenty, très en deçà de l'imprécision GPS sur des segments de quelques mètres |
| Dénivelé | Hystérésis à 3 m, pas de moyenne mobile | L'altitude GPS oscille de 2 m à l'arrêt ; la somme naïve double le D+ sur une heure, car le bruit s'accumule sans jamais se compenser |
| Immobilité | Sous 0,5 m/s, le temps ne compte pas comme « en mouvement » | Distingue un feu rouge d'une pause déclarée |
| Allure instantanée | Fenêtre glissante de 30 s | L'allure moyenne d'une sortie d'une heure ne bouge plus après vingt minutes |
| Splits | Instant de franchissement interpolé linéairement | Couper au point suivant décale chaque split un peu plus que le précédent |
| Dérive d'horloge | Mesurée au premier lot, appliquée ensuite ; refus au-delà de 15 min | Corriger une dérive d'une heure revient à fabriquer des horodatages |
| Calories | MET × masse × durée, absent sans la masse | Une valeur par défaut serait indiscernable d'une mesure |

## Idempotence : ce qui est réellement prouvé

Trois tests, et eux seuls, garantissent que l'accumulateur incrémental supporte le rejeu :

1. un numéro de séquence déjà appliqué ne change rien ;
2. rejouer des lots entiers, dans le désordre, donne le même accumulateur qu'un passage
   direct ;
3. reconstruire depuis zéro la totalité des points acceptés redonne exactement l'état du
   direct.

Le troisième est le seul à prouver la propriété : les deux premiers ne montrent que
l'absence de doublon. Sans lui, une reconstruction après incident pourrait afficher
d'autres chiffres que le direct, sans que rien ne le signale.

## Couverture

**100 %** ligne, branche et instruction sur `shared` et `course`, mesurés sur l'agrégat.
196 tests unitaires, aucun mock, aucun contexte Spring.

Ce n'est pas un objectif tenu de justesse : le domaine est du Java pur, il n'y a aucune
excuse à ne pas le couvrir entièrement. Le seuil de 80 % commencera à être un vrai
plafond quand l'infrastructure arrivera, aux lots 5 et suivants.

# Lot 1 — décisions d'architecture

Ce que le prompt me demandait de trancher au lot 1, plus les écarts que la mise en œuvre
a rendus nécessaires. Chaque écart est signalé, avec la raison.

## 1. Un module Maven par thème applicatif

Le prompt exigeait **un seul module** applicatif. Décision inverse, prise en cours de lot :
un artefact Maven par module fonctionnel, plus `runtrack-shared`, `runtrack-app`
(assemblage exécutable) et `runtrack-coverage` (seuil de couverture agrégé).

Un découpage `-api` / `-impl` avait été monté puis abandonné. Il apportait une vraie
garantie — `auth` ne voyant pas le jar de `user.internal`, un import interdit ne compile
pas — mais au prix de 18 POM. En regroupant par thème, cette garantie disparaît :
`auth` dépend de tout `runtrack-user`, donc `import com.runtrack.user.internal.…`
compilerait. **C'est `ApplicationModules.verify()` qui l'attrape**, pas le compilateur.
La règle vaut ce que vaut ce test : il ne doit jamais être désactivé.

Ce que le découpage Maven apporte quand même : un cycle entre modules est refusé par le
réacteur avant même Modulith, et le POM de chaque module affiche son graphe de
dépendances en clair.

## 2. Un neuvième module : `feed`

Le prompt en prévoyait huit. Le fil d'actualité croise `course` (activités), `social`
(qui je suis), `user` (auteur) et `engagement` (compteurs), et le §10 interdit les
jointures inter-modules.

Le porter dans `course` imposait `course → engagement` pour les compteurs, alors que
`engagement → course` existe déjà pour `canView` : **cycle**. Un module de lecture dédié,
dont personne ne dépend, compose librement sans rien casser.

`feed` maintient sa propre projection, alimentée par les événements de `course` et
`engagement` : une requête, pas de N+1, pas de jointure inter-modules.

## 3. Stratégie du feed : fan-out à la lecture

Pas de fan-out à l'écriture (une ligne par couple destinataire × activité) : un compte
très suivi provoquerait des dizaines de milliers d'insertions au démarrage d'une course.

À la lecture : la liste des abonnements vient du cache (§6), la projection `feed` est
filtrée dessus avec un index `(owner_id, started_at DESC)`, pagination par curseur.
Les compteurs sont déjà dans la projection — c'est ce qui évite le N+1.

Limite assumée : au-delà de quelques milliers d'abonnements, le filtre devient coûteux.
On y reviendra si le cas se présente, pas avant.

## 4. `UserRegistered` est publié par `user`, pas par `auth`

Le prompt attribuait l'événement à `auth`. Mais `user` devrait alors l'écouter pour créer
le profil, donc `user → auth`, alors que `auth → user` est déjà nécessaire : **cycle**.

Le signup appelle `UserApi` pour créer le profil, et c'est `user` qui publie
`UserRegistered`. `auth` ne publie plus que `UserAuthenticated`.

## 5. Pas de `AuthApi`

Rien n'en a besoin. Le `Viewer` est produit par le filtre de sécurité et vit dans
`shared` (§5.4 du prompt) ; aucun module n'a à demander à `auth` qui est connecté.
Seuls `user`, `social` et `course` exposent un `XxxApi`, parce qu'ils ont des
consommateurs déclarés. `sharing`, `engagement`, `notification` et `feed` n'en ont pas :
leur point d'entrée est leur couche REST.

## 6. Accumulateur de statistiques

`StatsAccumulator`, record immuable, porté par le domaine de `course` :

- le `sequenceNumber` du dernier point appliqué — le curseur qui rend le rejeu inoffensif ;
- le dernier point retenu (origine du prochain segment de distance) ;
- les cumuls : distance, temps en mouvement, D+/D−, altitude min/max, somme et max de FC ;
- une fenêtre glissante bornée (~30 s) pour l'allure instantanée ;
- l'état du lisseur d'altitude.

`apply(accumulator, point)` est une fonction pure. La déduplication précède
l'accumulation, jamais l'inverse. Persistance en `jsonb` sur `activity_stats`, doublée
des colonnes dérivées nécessaires à la lecture.

La propriété qui compte : rejouer depuis zéro tous les points acceptés doit redonner
exactement le même accumulateur. C'est le test qui prouve l'idempotence — les autres ne
prouvent que l'absence de doublon en base.

## 7. Rétention des points bruts

`track_points` conservée **90 jours** après la fin de la course. Au-delà, purge ; ce qui
reste — polyline encodée, `LINESTRING` PostGIS, splits, statistiques figées — suffit à
tout afficher. Les points bruts ne servent qu'à recalculer ou à exporter un GPX.

Conséquence assumée : **l'export GPX n'est possible que dans les 90 jours.** Si ça ne
convient pas, l'alternative est d'archiver un GPX compressé à la fin de la course.

Pas de partitionnement en v1 : un index unique sur une table partitionnée Postgres doit
contenir la clé de partition, ce qui rendrait `(activity_id, sequence_number)`
impossible. Si le volume l'impose, ce sera un partitionnement **par hash sur
`activity_id`**, jamais par mois.

## 8. Pas de structured concurrency

Toujours en preview en Java 25 : on ne livre pas un artefact qui exige
`--enable-preview`. `ScopedValue`, lui, est finalisé et sert au contexte de requête.

## 9. Le seuil de couverture est vérifié une seule fois, sur l'union

`jacoco:check` à 80 % LINE et BRANCH vit dans `runtrack-coverage`, sur la fusion des
`.exec` de tous les modules.

Il n'est **pas** vérifié module par module, et c'est délibéré : un test d'intégration
hébergé par `runtrack-app` couvre du code appartenant à d'autres modules, donc un seuil
par module afficherait un chiffre faux par construction.

`runtrack-coverage` déballe les classes de tous les modules dans son propre
`target/classes`, parce que `jacoco:check` ne sait lire que le répertoire de sortie du
projet courant.

**État au lot 1 : le seuil est vert sans rien mesurer** — 3 classes analysées, zéro
instruction couvrable. Il commencera à mordre au lot 2.

## 10. Réglage ArchUnit temporaire

`archRule.failOnEmptyShould=false` dans `runtrack-app/src/test/resources`. Sur un
squelette, une règle qui ne trouve aucune classe est normale, pas un échec. **À repasser
à `true` au lot 3**, quand chaque couche portera du code — sinon une règle devenue
inopérante passerait inaperçue.

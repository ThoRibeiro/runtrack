# RunTrack — back-end

Suivi de courses à pied entre amis : un coureur démarre, ses amis sont notifiés, ils
suivent sa position et ses statistiques en direct, et la course est historisée à
l'arrivée avec likes et commentaires.

Java 25 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 · PostgreSQL 17 + PostGIS ·
Dragonfly · modèle bloquant sur virtual threads.

## Démarrer en local

```bash
docker compose up -d                 # Postgres + PostGIS, Dragonfly
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
mvn install                          # compile, tests, couverture, pose les modules dans ~/.m2
mvn -pl runtrack-app spring-boot:run  # profil `local` par défaut
```

Puis : <http://localhost:8080/swagger-ui.html> pour l'API, et `docs/api/runtrack.http`
pour la parcourir de bout en bout.

Trois choses font échouer un premier lancement, et leurs messages ne le disent pas :

- **le build exige un JDK 25**, vérifié par `maven-enforcer-plugin`. Si `java -version`
  répond autre chose, le `JAVA_HOME` ci-dessus est obligatoire — l'erreur parle de la
  version détectée sans dire qu'elle vient du PATH ;
- **les tests d'intégration démarrent des conteneurs.** Sans Docker en marche, une
  cinquantaine d'ITs échouent d'un bloc sur `Could not find a valid Docker environment`,
  ce qui ressemble à une panne de code ;
- **`mvn verify` ne suffit pas avant un `-pl`.** `verify` s'arrête avant `install` : les
  modules ne sont jamais posés dans `~/.m2`, donc `mvn -pl runtrack-app spring-boot:run`
  — qui ne construit que ce module — cherche `runtrack-platform`, `runtrack-user` et les
  autres dans le dépôt local et ne les y trouve pas (`Could not find artifact
  com.runtrack:…:0.1.0-SNAPSHOT`). D'où le `mvn install` ci-dessus, à refaire après
  chaque modification d'un module dont `runtrack-app` dépend.

### Profils

| Profil | Ce qu'il change |
|---|---|
| `local` (défaut) | envoyeur push `logging` — pas de compte Firebase requis —, journaux lisibles, CORS ouvert sur les ports de développement, `health` détaillé |
| `test` | utilisé par les tests d'intégration : battements et délais raccourcis pour qu'un test observe en centaines de millisecondes ce qui prend des secondes en production |
| `prod` | envoyeur `fcm`, journaux **JSON structurés** (ECS), CORS fermé par défaut, endpoints d'exploitation sur un port séparé que l'ingress n'expose pas |

Aucun secret dans le dépôt : tout arrive par variable d'environnement
(`RUNTRACK_DB_*`, `RUNTRACK_JWT_*`, `RUNTRACK_FCM_*`, `RUNTRACK_CORS_ORIGINS`).

## Carte des modules

Le découpage de premier niveau est **fonctionnel**. L'architecture hexagonale s'applique
à l'intérieur de chaque module, pas au-dessus.

| Module | Rôle |
|---|---|
| `runtrack-shared` | Identifiants, objets valeur, `Viewer`, erreurs, horloge. Ne dépend de rien. |
| `runtrack-platform` | Noyau technique partagé : erreurs RFC 9457, cache Dragonfly, canal SSE, supervision de l'outbox. Pas un domaine. |
| `runtrack-user` | Profil, préférences, suppression RGPD |
| `runtrack-auth` | Inscription, connexion, JWT, refresh rotatif |
| `runtrack-social` | Abonnements, demandes, blocages |
| `runtrack-course` | Le domaine riche : cycle de vie, ingestion GPS, statistiques, live |
| `runtrack-sharing` | Liens de partage publics et révocables. Le filtre du §5.4 réachemine vers `course`, qui l'ignore. |
| `runtrack-engagement` | Likes et commentaires |
| `runtrack-notification` | Notifications in-app, push, préférences |
| `runtrack-feed` | Projection de lecture du fil, tenue par événements. Fan-out à la lecture. |
| `runtrack-app` | Assemblage exécutable + tests de frontières |
| `runtrack-coverage` | Rapport de couverture agrégé et seuil de build |

Le graphe des dépendances autorisées :

```
auth ─────────┐
social ───────┼──▶ user
course ───────┘
sharing ──────┐
engagement ───┼──▶ course
              │
notification ─┴──▶ user, social, course, engagement   (personne ne dépend de lui)
feed ────────────▶ user, social, course, engagement   (personne ne dépend de lui)
```

Il est déclaré à deux endroits, volontairement : dans les `pom.xml` (Maven refuse un
cycle) et dans les `@ApplicationModule(allowedDependencies = …)` (Modulith refuse en plus
d'atteindre le `internal` du voisin, ce que le classpath autoriserait).

Chaque module a la même forme :

```
course/
├── CourseApi.java     seul point d'entrée pour les autres modules
├── event/             événements publiés — contrat inter-modules
├── usecases/          le cœur, invisible des autres modules
│   ├── model/         Java pur : ni Spring, ni Jakarta, ni Jackson, ni JPA
│   ├── service/       cas d'usage
│   └── port/          ports sortants
└── infrastructure/    les adaptateurs
    ├── endpoint/      @RestController + dto/
    ├── repository/    implémentations des ports + entity/
    └── cache/ realtime/
```

Le vocabulaire est celui des APIs **Lark** (`usecases/` + `infrastructure/`), pour qu'un
relecteur habitué à `wishlist-api` s'y retrouve sans traduction. Deux écarts assumés :

- **les ports vivent dans `usecases/port/`**, pas dans `infrastructure/repository/`. La
  dépendance va vers le centre, et c'est une règle ArchUnit qui le vérifie à chaque build ;
- **un seul `@ControllerAdvice`, global**, dans `runtrack-platform`. Un advice scopé par
  `assignableTypes` laisse les endpoints d'une seconde version sans mapping — c'est le
  piège documenté sur `wishlist-api`, où les mêmes exceptions remontent en 500.

`runtrack-shared` et `runtrack-platform` tiennent le rôle de `infrastructure/technical/`.

## Le cache

Huit entrées, toutes versionnées (`cache:v1:…`), toutes posées par un **décorateur de port** —
jamais une annotation dispersée. Les cas d'usage ignorent que le cache existe.

| Donnée | TTL | Ce qui l'invalide |
|---|---|---|
| Profil public, visibilité de compte | 10 min | `UserProfileUpdated` |
| Abonnés, abonnements | 5 min | follow, unfollow, blocage |
| Résumé d'une course | 24 h | édition, suppression |
| Compteurs likes / commentaires | 1 min | like, unlike, commentaire, suppression |
| Résolution d'un jeton de partage | 15 min | révocation du lien |
| Tête du fil | 30 s | l'expiration, plus les gestes du propriétaire |

Deux choses ne sont **pas** cachées, et c'est délibéré :

- **la décision `canView`** — cardinalité N×M, invalidation impossible à cibler ; on cache les
  faits et on recompose la décision en mémoire, ce qui est gratuit et exact ;
- **les blocages** — une valeur périmée de cinq minutes laisserait quelqu'un voir ce qu'il ne doit
  plus voir. Un cache qui peut rouvrir une porte n'est pas un cache.

**La tête du fil n'a pas d'invalidation par abonné**, et c'est un écart argumenté : purger les
abonnés d'un coureur à chaque course démarrée serait le fan-out à l'écriture rejeté au lot 1,
réintroduit par la porte du cache. Le propriétaire, lui, est purgé — supprimer sa course et la
revoir dans son fil est un défaut visible, là où trente secondes de retard chez un abonné ne le
sont pas : il est déjà prévenu par une notification et par le direct.

Un **verrou anti-stampede** protège le recalcul : quand l'entrée des abonnés d'un compte très
suivi expire, tout le trafic en cours constate le manque en même temps. Un seul recalcule, les
autres relisent — et chargent quand même si le cache est resté vide, parce qu'un verrou perdu ne
doit pas figer la lecture.

## Les deux flux qui traversent tout

### Le direct — de la montre à l'écran d'un spectateur

Le point difficile n'est pas d'envoyer un événement, c'est qu'un spectateur connecté à
l'instance **B** voie une course dont les points arrivent sur l'instance **A**.

```
  téléphone du coureur                     spectateurs
         │                                  ▲        ▲
         │ POST /race/v1/{id}/points        │ SSE    │ SSE
         ▼                                  │        │
   ┌───────────────┐                 ┌──────┴──┐  ┌──┴──────┐
   │  instance A   │                 │  inst.A │  │  inst.B │
   │               │                 └────┬────┘  └────┬────┘
   │ PointIngestion│                      │ registre   │ registre
   │   ├ filtre    │                      │ d'émetteurs│
   │   ├ dédup     │                      ▲            ▲
   │   ├ accumule  │                      │            │
   │   └ COMMIT ───┼──────────┐           │  XREAD     │  XREAD
   └───────────────┘          │           └────────────┴──────┐
                              ▼                                │
                   ┌──────────────────────────────────┐        │
                   │ Dragonfly Stream                 │────────┘
                   │ live:activity:{id}:events        │
                   │ (MAXLEN borné, id: = Last-Event-ID)
                   └──────────────────────────────────┘
```

Trois choses ne sont pas interchangeables dans ce schéma :

1. **la publication attend le commit.** Annoncer une position avant, c'est l'annoncer
   alors qu'un conflit optimiste peut encore l'effacer — et le §4 rend ce conflit
   ordinaire, pas exceptionnel ;
2. **on s'abonne au stream avant de lire l'instantané**, et l'instantané est inséré *en
   tête* de la file du spectateur. L'inverse perdrait les événements des quelques
   millisecondes de la lecture en base, sans que personne ne s'en aperçoive ;
3. **une instance ne suit un sujet que tant qu'elle a un abonné pour lui.** C'est ce qui
   borne le nombre de connexions Lettuce ouvertes.

Chaque événement porte un `id:` — l'identifiant d'entrée du stream. Le client le renvoie
en `Last-Event-ID` et reprend sans trou ; si l'historique a été tronqué entre-temps, il
retombe sur l'instantané plutôt que sur un trou silencieux.

### La chaîne de notification — de la course au téléphone d'un ami

```
  POST /race/v1               ┌─ transaction métier ────────────┐
        │                     │ ActivityLifecycle.start()       │
        └────────────────────▶│   ├ écrit la course             │
                              │   └ publishEvent(ActivityStarted)
                              └───────────┬─────────────────────┘
                                          │ COMMIT
                                          ▼
                         ┌────────────────────────────────┐
                         │ Event Publication Registry     │  ← l'outbox, fournie par
                         │ (table event_publication)      │    Modulith, pas écrite
                         └───────────┬────────────────────┘
                                     │ @ApplicationModuleListener
                        ┌────────────┴────────────┐   (autre fil, après commit)
                        ▼                         ▼
              ┌──────────────────┐      ┌──────────────────┐
              │  notification    │      │      feed        │
              │  ├ audience      │      │  └ projection    │
              │  ├ préférences   │      └──────────────────┘
              │  └ écrit la boîte│
              └────────┬─────────┘
                       │ après la transaction d'écriture
          ┌────────────┴────────────┐
          ▼                         ▼
   ┌─────────────┐         ┌────────────────┐
   │ SSE in-app  │         │ PushDelivery   │
   │ (LiveChannel│         │  ├ heures calmes│
   │  de platform)│        │  ├ anti-spam   │
   └─────────────┘         │  └ FCM par lots│
                           └────────────────┘
```

Ce que le registre apporte et qu'on n'écrit donc pas : exécution après commit, en
asynchrone, événement persisté **avant** traitement et marqué complété après, rejeu au
redémarrage. Ce qui reste à notre charge et qu'on voit dans le schéma : le recul
exponentiel entre deux reprises, l'arrêt après cinq échecs, et le fait qu'aucun appel à
Firebase ne se trouve dans la transaction métier.

**L'identifiant de corrélation traverse tout cela explicitement.** Un `ScopedValue` ne
franchit pas un changement de fil : il est donc transporté dans le champ `correlationId`
de chaque événement, et la portée est rouverte à l'entrée de chaque écouteur. Sans ce
geste, les journaux ne seraient corrélés que sur le chemin HTTP — et on ne s'en
apercevrait qu'en cherchant, en production, pourquoi une notification est partie.

## Exploitation

| Endpoint | Ce qu'il sert |
|---|---|
| `/actuator/health/liveness` | l'état de la JVM seule — Kubernetes redémarre le pod si elle tombe |
| `/actuator/health/readiness` | la JVM **et** ses dépendances (Postgres, Dragonfly) — le pod sort du service sans redémarrer |
| `/actuator/prometheus` | toutes les métriques |
| `/actuator/eventpublications` | l'état de l'outbox : `deadLettered` au-dessus de zéro appelle quelqu'un |
| `/actuator/modulith` | la carte des modules, générée |

Les métriques métier, au-delà de celles de la JVM :

| Métrique | Ce qu'elle dit |
|---|---|
| `runtrack.activities.live` | courses en cours d'enregistrement |
| `runtrack.points.accepted` / `.rejected` | débit d'ingestion, et part de ce que le filtre écarte |
| `runtrack.ingestion` | durée d'un lot, de la réception à la publication |
| `runtrack.live.subscribers` | émetteurs SSE ouverts sur cette instance |
| `runtrack.live.subscribers.dropped` | abonnés déconnectés faute de suivre le débit |
| `runtrack.cache.hit` / `.miss` | efficacité du cache |
| `runtrack.events.incomplete` / `.dead_lettered` | santé de l'outbox |
| `runtrack.push.delivered` / `.failed` | ce qui part vers les téléphones, et ce qui n'y arrive pas |
| `runtrack.ratelimit.rejected` / `.degraded` | quotas atteints, et quotas non appliqués faute de Dragonfly |

En production, les journaux sont en **JSON ECS** et portent le `correlationId` : une
requête se suit d'un bout à l'autre, écouteurs asynchrones compris.

## Ce que le build vérifie

- `ApplicationModules.verify()` — frontières, cycles, interfaces nommées.
- **11 règles ArchUnit** intra-module : domaine sans framework, sens des dépendances,
  JPA et cache confinés à `infra`, aucun `reactor.*`, pas de Lombok, injection par
  constructeur, aucun `Instant.now()` en dur, aucun type nommé `Visibility` tout court.
- **Couverture 80 % LINE et BRANCH**, sur l'union unitaires + intégration de tous les
  modules, `haltOnFailure`. Le rapport agrégé :
  `runtrack-coverage/target/site/jacoco-aggregate/index.html`.
- La documentation Modulith, régénérée à chaque build dans **`docs/modules/`** —
  diagrammes PlantUML et canevas par module, versionnés avec le code plutôt que perdus
  au premier `mvn clean`.

## Documentation publiée

Le site est construit et déployé **à chaque commit sur `main`** par
`.github/workflows/ci.yml`, sur GitHub Pages :

| Page | Contenu |
|---|---|
| `/` | la documentation générale : découpage, flux temps réel, chaîne de notification, décisions |
| `/api.html` | la référence de l'API, Swagger UI sur la description générée |
| `/openapi.json` | la description brute, **écrite par le build** — pas à la main |
| `/coverage/` | le rapport JaCoCo agrégé |

La description OpenAPI n'est pas produite par une étape à part : c'est `OpenApiIT` qui l'écrit,
**après** que ses assertions ont vérifié son contenu. Le fichier publié est donc exactement celui
que le build vient de valider — une génération séparée pourrait publier un document vide sans
que rien ne l'attrape.

Le « Try it out » de Swagger vise `http://localhost:8080` : Pages ne sert que des fichiers, et
c'est l'application locale qui répond.

## API

### L'indexation des URL

Un chemin est `<ressource>/v1/…`. Le premier segment nomme **ce qu'on manipule**, jamais le
module qui répond : les likes d'une course sont sous `race/v1`, bien qu'ils soient servis par
`engagement`, et le bilan d'un coureur sous `user/v1`, bien qu'il vienne de `course`. Un
endpoint qui déménage d'un module à l'autre ne change donc pas d'URL.

| Préfixe | Ce qu'il adresse |
|---|---|
| `auth/v1` | inscription, connexion, jeton, mot de passe |
| `user/v1` | profil, abonnements, blocages, appareils, préférences, bilans, `user/v1/{id}/races` |
| `race/v1` | cycle de vie d'une course, points, trace, direct, likes, commentaires, liens de partage |
| `comment/v1` | un commentaire déjà écrit : édition, suppression |
| `share-link/v1` | révocation d'un lien de partage |
| `shared/v1` | la lecture par lien de partage — réacheminée vers `race/v1`, sans contrôleur à elle |
| `notification/v1` | notifications, compteur, flux SSE |
| `feed/v1` | le fil d'actualité |

La version est **par ressource**, et c'est l'intérêt du découpage : faire passer les courses en
`race/v2` n'oblige pas à reversionner l'authentification. Il n'y a pas de `/api/v1/**` : le
schéma précédent a été remplacé, sans alias de compatibilité.

- **Documentation interactive** : <http://localhost:8080/swagger-ui.html>, générée par
  springdoc depuis les contrôleurs — jamais écrite à la main, donc jamais en retard sur le
  code. Un groupe par préfixe : la page s'ouvre sur `8-tout`, et le sélecteur en haut mène à
  la ressource qu'on cherche — l'UI n'affiche qu'un groupe à la fois.
- **Description brute** : `/v3/api-docs/8-tout`, ou `/v3/api-docs/<groupe>` pour une ressource.
- **Les dossiers sont des décorateurs** : `ApiFolders` (dans `platform`) déclare les cinq
  dossiers de l'UI, et un contrôleur porte `@ApiFolders.Races` au lieu d'un `@Tag` recopié.
  Un dossier regroupe des contrôleurs de modules différents — `/user/v1` est servi par quatre
  d'entre eux — et son libellé n'existe donc qu'à un seul endroit. Reste, par endpoint, le seul
  texte qu'aucun décorateur ne peut deviner : `@Operation(summary = …)`. `OpenApiIT` refuse une
  opération sans résumé ou rangée dans un dossier non déclaré.
- **Le bilan personnel** `GET /user/v1/me/stats?period=` est servi par `course` malgré son URL :
  les courses lui appartiennent, et faire dépendre `user` de `course` fermerait un cycle. Même
  arrangement pour `/user/v1/me/devices`, porté par `notification` — une URL décrit ce que le
  client demande, pas quel module le sert.
- **Collection prête à l'emploi** : `docs/api/runtrack.http` — s'ouvre dans IntelliJ ou dans
  VS Code, et se suit de haut en bas : créer un compte, courir, partager, commenter.

## Documentation

- `docs/decisions-lot-1.md` — structure, découpage Maven, seuil de couverture.
- `docs/decisions-lot-2.md` — noyau partagé et domaine des courses : composition des
  visibilités, choix de calcul (haversine, hystérésis du dénivelé, splits interpolés) et
  ce qui prouve réellement l'idempotence de l'accumulateur.

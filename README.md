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
mvn verify                           # compile, tests, seuil de couverture
mvn -pl runtrack-app spring-boot:run
```

Le build **exige un JDK 25** (`maven-enforcer-plugin` le vérifie). Maven tourne peut-être
sur un JDK plus ancien par défaut : d'où le `JAVA_HOME` ci-dessus.

## Carte des modules

Le découpage de premier niveau est **fonctionnel**. L'architecture hexagonale s'applique
à l'intérieur de chaque module, pas au-dessus.

| Module | Rôle |
|---|---|
| `runtrack-shared` | Identifiants, objets valeur, `Viewer`, erreurs, horloge. Ne dépend de rien. |
| `runtrack-user` | Profil, préférences, suppression RGPD |
| `runtrack-auth` | Inscription, connexion, JWT, refresh rotatif |
| `runtrack-social` | Abonnements, demandes, blocages |
| `runtrack-course` | Le domaine riche : cycle de vie, ingestion GPS, statistiques, live |
| `runtrack-sharing` | Liens de partage publics et révocables |
| `runtrack-engagement` | Likes et commentaires |
| `runtrack-notification` | Notifications in-app, push, préférences |
| `runtrack-feed` | Projection de lecture du fil d'actualité |
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
└── internal/          invisible des autres modules
    ├── domain/        Java pur : ni Spring, ni Jakarta, ni Jackson, ni JPA
    ├── application/   cas d'usage + ports sortants
    └── infra/         rest/ jpa/ cache/ realtime/
```

## Ce que le build vérifie

- `ApplicationModules.verify()` — frontières, cycles, interfaces nommées.
- **11 règles ArchUnit** intra-module : domaine sans framework, sens des dépendances,
  JPA et cache confinés à `infra`, aucun `reactor.*`, pas de Lombok, injection par
  constructeur, aucun `Instant.now()` en dur, aucun type nommé `Visibility` tout court.
- **Couverture 80 % LINE et BRANCH**, sur l'union unitaires + intégration de tous les
  modules, `haltOnFailure`. Le rapport agrégé :
  `runtrack-coverage/target/site/jacoco-aggregate/index.html`.
- La documentation Modulith, régénérée à chaque build dans
  `runtrack-app/target/spring-modulith-docs/`.

## Documentation

- `docs/decisions-lot-1.md` — structure, découpage Maven, seuil de couverture.
- `docs/decisions-lot-2.md` — noyau partagé et domaine des courses : composition des
  visibilités, choix de calcul (haversine, hystérésis du dénivelé, splits interpolés) et
  ce qui prouve réellement l'idempotence de l'accumulateur.

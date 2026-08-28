# Lot 3 — décisions et défauts trouvés

`auth` et `user` de bout en bout : domaine, cas d'usage, JPA, REST, sécurité.

## Ce que l'infrastructure a révélé et que les tests unitaires ne pouvaient pas voir

Cinq défauts réels, tous trouvés par les tests d'intégration.

### 1. La révocation défensive était annulée par son propre rejet

Le cas d'usage détectait le rejeu d'un jeton volé, révoquait toute la famille, puis levait
une exception pour refuser la requête. **Ce rejet annulait la transaction — donc la
révocation avec.** Le voleur gardait une chaîne intacte, et rien ne l'aurait signalé.

Corrigé par un composant `SessionRevocation` en `REQUIRES_NEW`. Un composant distinct, et
non une méthode privée : un appel interne ne traverse pas le proxy Spring, et
`REQUIRES_NEW` sur une méthode privée n'a strictement aucun effet.

### 2. `/api/v1/users/*` en accès public capturait `/users/me`

L'ordre des motifs de sécurité laissait n'importe qui lire l'adresse e-mail et la
physiologie d'un compte. La règle authentifiée est désormais **avant** la règle publique.
C'est une fuite de données, trouvée par un test qui attendait 401.

### 3. Spring Security répondait 403 au lieu de 401

Sans `AuthenticationEntryPoint`, une requête sans jeton donnait 403. La distinction compte
pour le client : 401 veut dire « authentifie-toi » et justifie de rafraîchir le jeton, 403
veut dire « ce n'est pas pour toi » et ne le justifie jamais.

### 4. `@Enumerated` sur des colonnes `String`, et `CHAR(64)` contre `varchar`

Deux divergences entre les entités et les migrations, attrapées par
`ddl-auto: validate`. C'est exactement ce que ce réglage sert à trouver.

### 5. Le conteneur de test mourait entre deux classes

`@Container` arrête le conteneur à la fin de chaque classe, alors que Spring garde son
contexte en cache et le réutilise : les classes suivantes héritaient d'une source de
données pointant vers un conteneur éteint. Remplacé par un conteneur singleton démarré une
fois pour toute la JVM.

## Pièges de Spring Boot 4 rencontrés

| Attendu | Réalité en 4.1.1 |
|---|---|
| `flyway-core` active la migration | Non : c'est `spring-boot-flyway` qui porte l'auto-configuration |
| `org.springframework.boot.autoconfigure.domain.EntityScan` | Déplacé dans `org.springframework.boot.persistence.autoconfigure` |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | Déplacé dans `spring-boot-webmvc-test` |
| `com.fasterxml.jackson.databind.ObjectMapper` | **Jackson 3** par défaut : `tools.jackson.databind` |

Les auto-configurations sont éclatées en artefacts dédiés en 4.x. Ajouter la bibliothèque
ne suffit plus, il faut aussi le module Boot correspondant.

## Choix de conception

### Argon2id pour les mots de passe, SHA-256 pour les jetons

Les deux ne protègent pas la même chose. Un mot de passe est court et choisi par un
humain, donc devinable : il faut un hachage lent (19 MiB, 2 itérations, ~50 ms —
recommandation OWASP). Un jeton tiré de 256 bits d'aléa n'est pas devinable, quelle que
soit la vitesse : le hacher lentement ne protégerait rien et rendrait chaque
rafraîchissement coûteux. L'empreinte est là pour qu'une fuite de la base ne livre aucun
jeton utilisable, rien de plus.

### RS256 plutôt que HS256

La clé publique suffit à vérifier un jeton : un service qui ne fait que lire n'a jamais
besoin du secret de signature. Avec HS256, tout vérificateur peut aussi forger.

Sans clés configurées, une paire éphémère est tirée au démarrage. Les jetons ne survivent
alors pas à un redémarrage — c'est voulu : une configuration incomplète se voit tout de
suite plutôt que de passer en production sans qu'on s'en aperçoive.

### Aucune réponse ne dit si un compte existe

Adresse inconnue et mot de passe faux rendent le même 403 avec le même code. La demande de
réinitialisation répond 202 dans tous les cas. Chacune de ces distinctions ferait de
l'endpoint un énumérateur de comptes.

### Un seul `SingleUseToken` pour deux usages

Confirmation d'adresse et réinitialisation partagent la mécanique et ne diffèrent que par
la durée de vie (1 jour contre 30 minutes — un lien de réinitialisation reste une porte
d'entrée tant qu'il vit). Deux classes jumelles auraient signifié corriger deux fois le
même défaut.

### Le module `platform`

Traduction des erreurs en RFC 9457, horloge et source d'aléa. Déclaré **module partagé** et
non fonctionnel : le découpage de premier niveau est métier, mais ces classes doivent
vivre dans un package que Modulith connaît, sinon elles apparaîtraient comme un domaine de
plus.

### `UserApi.summaries(Collection)`

Existe à côté de `summary(id)` pour que le fil et les listes d'abonnés ne bouclent pas :
un appel par ligne affichée est exactement le N+1 que le §10 interdit.

### Suppression de compte

Anonymisation, pas effacement. Les courses, likes et commentaires référencent
l'identifiant ; supprimer la ligne réécrirait l'historique d'autres utilisateurs.
Effacé : identifiant public, adresse, nom affiché, biographie, avatar, toute la
physiologie. Conservé : l'identifiant technique et la date d'inscription.

### Migrations par module

Un emplacement Flyway par module, versions réparties par centaines (user = 1xx,
auth = 2xx, …) : deux `V1` dans deux emplacements entreraient en collision. **Aucune clé
étrangère inter-modules** — elle recréerait par la base le couplage que l'architecture
interdit.

## Couverture

**90,6 %** ligne, **92,2 %** branche sur l'agrégat, seuil à 80/80. 368 tests, dont 37 tests
d'intégration contre un vrai PostgreSQL+PostGIS.

Les zones les moins couvertes sont la sécurité (68 %) et le mailer local (67 %) : chemins
d'erreur de lecture de clés RSA, essentiellement. À reprendre au lot 13.

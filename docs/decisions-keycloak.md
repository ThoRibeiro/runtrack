# Keycloak — décisions et découpage du chantier

Le remplacement de l'authentification maison (inscription, connexion, mot de passe) par un
fournisseur d'identité OIDC. Ce document est écrit **avant** le code : il fixe ce qui est
tranché, pour que chaque lot se relise sans rouvrir le débat.

## Pourquoi, et ce que cela ne règle pas

**Pourquoi** : l'application est destinée aux stores. Dès qu'une connexion sociale est
proposée, Apple impose « Sign in with Apple » (règle App Store 4.8) — l'écrire à la main est
un chantier, le déléguer est une configuration. Viennent avec : MFA, verrouillage après N
échecs, révocation de session à distance, et la disparition d'environ 1 500 lignes de code
sensible maintenues seul.

**Ce que cela ne règle pas** : la stabilité des sessions. Le seul défaut connu est la
rotation stricte de `Authentication.refresh` — une réponse perdue en réseau fait rejouer un
jeton déjà consommé, et toute la famille est révoquée. Côté client, `RefreshCoordinator`
sérialise déjà les renouvellements ; le trou restant est serveur. Keycloak fait la **même**
rotation avec détection de rejeu : il expose seulement un réglage de tolérance.

> **Conséquence assumée** : la fenêtre de grâce envisagée côté maison n'est **pas** écrite.
> Elle disparaîtrait avec le code qu'elle corrige. Le sujet est reporté au lot 4, où il
> devient un réglage de realm (`refresh-token-max-reuse`).

## Ce qui ne bouge pas

`ViewerAuthenticationFilter` décode déjà par un `JwtDecoder` de Spring Security et construit
un `Viewer` depuis `jwt.getSubject()`. `Viewer` vit dans `shared`, et **aucun module ne
dépend d'`auth`**. Les huit autres modules, tous les contrôleurs et toutes les règles d'accès
sont donc hors de ce chantier.

**Règle qui tient tout le reste : le mot « Keycloak » ne sort pas d'`auth`.** Ailleurs, on ne
connaît qu'un `Viewer`. La règle ArchUnit existante le vérifie déjà.

## Décisions

### 1. Le `sub` Keycloak **est** le `UserId`

Pas de table de correspondance. `UserId.of()` accepte n'importe quel UUID ; seule la
génération en UUIDv7 se perd, et aucun tri ne s'appuie dessus. Une correspondance coûterait
une lecture par requête, un cache, et son invalidation — pour ne rien apporter.

Le moment rend la décision gratuite : **aucun compte réel en base**. C'est le seul coût de ce
chantier qui ne baisse jamais avec le temps.

### 2. Le profil naît à la première requête authentifiée

Plus de `/signup` maison, donc plus de point où créer le profil. Un provisioning **paresseux**
s'en charge : jeton valide dont le `sub` est inconnu → `user` crée le profil et publie
`UserRegistered`. L'événement existe déjà, donc notifications et fil d'actualité ne changent
pas d'une ligne.

L'alternative — un webhook Keycloak à l'inscription — ajoute un chemin réseau qui peut
échouer en silence, et laisse un compte sans profil sans que personne le sache.

### 3. Le pseudo reste au domaine

`handle` est unique et porte une règle métier. Il n'est **pas** un attribut du realm : le
front demande « choisis ton pseudo » à la première connexion. Déléguer son unicité à l'IdP
reviendrait à déporter une règle du domaine dans une interface d'administration.

### 4. La bascule passe par une propriété, jamais par un profil

`runtrack.auth.provider` vaut `local` (défaut) ou `keycloak`, comme `runtrack.mail.provider`.
C'est ce qui permet d'exercer un vrai jeton Keycloak **sans quitter le profil local**, et de
livrer les lots sans big bang.

### 5. Le décodeur ne parle pas au réseau au démarrage

`NimbusJwtDecoder.withJwkSetUri(...)` plus un validateur d'`iss`, et non
`withIssuerLocation(...)`. Ce dernier va chercher la configuration OIDC **au démarrage** :
l'API ne démarrerait alors plus si Keycloak est en retard au boot, ou tombé. Avec le JWKS
paresseux, l'API démarre seule et ne dépend de Keycloak qu'à la première validation.

### 6. Le realm est versionné dans le dépôt

`keycloak/realm-runtrack.json`, importé au démarrage du conteneur. Sans cela, la
configuration d'identité n'existe que dans une interface web : ni relisible en diff, ni
reproductible, ni restaurable.

### 7. Aucun Keycloak dans le build

Les tests d'intégration signent leurs jetons avec une clé locale, via un `JwtDecoder` de
test. Un Keycloak en Testcontainers ajouterait son démarrage et un realm à provisionner à
**chaque** exécution — le build passerait de deux à quatre minutes pour ne rien prouver de
plus : ce qui est testé ici, c'est que l'application accepte un jeton valide, pas que
Keycloak sait en fabriquer.

### 8. On supprime en dernier

Tant que le front n'est pas basculé, `AuthController` et les jetons maison restent en place.
Supprimer d'abord, c'est se priver de tout chemin de repli.

### 9. Une adresse déjà prise arrête l'accueil du compte

Rattacher une identité fédérée à un profil existant sur la seule foi de l'adresse est le
scénario classique de prise de contrôle : il suffit qu'un fournisseur laisse déclarer une
adresse non vérifiée. `provisionFederated` lève donc un conflit plutôt que de fusionner. Le
jour où une fusion sera voulue, elle demandera une preuve — un lien envoyé à l'adresse, pas
une supposition.

### 10. L'existence se teste sur le cache, pas en base

L'accueil d'un compte est appelé à **chaque** requête authentifiée, ingestion de points
comprise — une fois par seconde et par coureur. Il interroge donc `UserApi.summary`, que le
décorateur de `user` sert depuis Dragonfly, et non `exists`, qui partirait en base à chaque
appel. Conséquence : `UserCacheInvalidation` écoute désormais `UserRegistered`, sinon un
« profil absent » mis en cache juste avant sa création survivrait jusqu'à la fin de son délai.

## Découpage

Chaque lot se termine sur un `mvn -o verify` vert, seuil de couverture compris, et une
branche poussée.

| Lot | Contenu | Fini quand |
| --- | --- | --- |
| **1** ✅ | Le realm, la stack locale, la bascule du décodeur | Un jeton Keycloak est accepté par l'API, `provider=local` reste le défaut et ne change rien |
| **2** ✅ | Le pont identité : `sub` = `UserId`, provisioning paresseux, `UserRegistered` | Une première requête d'un compte Keycloak inconnu crée son profil |
| **3** | Les tests : décodeur de test, `CourseFixtures` qui forge son jeton | Les 171 ITs passent sans `/auth/v1/signup`, build toujours à deux minutes |
| **4** | Le front : `expo-auth-session` + PKCE, écran « choisis ton pseudo », réglages de rotation du realm | Connexion, refresh et déconnexion fonctionnent sur web, iOS et Android |
| **5** | La suppression : `AuthController`, `Credentials`, les deux familles de jetons, les mails, la table `V201` | Plus une ligne de mot de passe dans le dépôt |

Les lots 1 à 3 sont livrables sans que rien ne change pour l'utilisateur : l'application
accepte les deux mondes. C'est le lot 4 qui bascule, et le lot 5 qui ferme la porte.

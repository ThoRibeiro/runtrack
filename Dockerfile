# Image de production, en deux étages.
#
# Le premier construit, le second exécute. Livrer l'image de construction reviendrait à
# embarquer le JDK complet, Maven et le dépôt local — plusieurs centaines de mégaoctets, et
# autant de surface d'attaque, pour faire tourner un jar.

# ---------- Étage 1 : construction ----------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

# Les poms d'abord, seuls. Tant qu'aucune dépendance ne change, cette couche est réutilisée et
# la reconstruction ne retélécharge rien — ce qui n'arriverait jamais en copiant tout d'un bloc.
COPY pom.xml .
COPY runtrack-shared/pom.xml runtrack-shared/
COPY runtrack-platform/pom.xml runtrack-platform/
COPY runtrack-user/pom.xml runtrack-user/
COPY runtrack-auth/pom.xml runtrack-auth/
COPY runtrack-social/pom.xml runtrack-social/
COPY runtrack-course/pom.xml runtrack-course/
COPY runtrack-sharing/pom.xml runtrack-sharing/
COPY runtrack-engagement/pom.xml runtrack-engagement/
COPY runtrack-notification/pom.xml runtrack-notification/
COPY runtrack-feed/pom.xml runtrack-feed/
COPY runtrack-app/pom.xml runtrack-app/
COPY runtrack-coverage/pom.xml runtrack-coverage/
RUN mvn -B -q dependency:go-offline -DskipTests

COPY . .
# Les tests ne tournent pas ici : ils exigent Docker (Testcontainers), et lancer des conteneurs
# depuis une construction d'image demande un accès au démon qu'on ne veut pas donner. C'est la
# CI qui exécute `mvn verify`, avant de construire l'image.
RUN mvn -B -q -DskipTests package

# ---------- Étage 2 : exécution ----------
FROM eclipse-temurin:25-jre-alpine

# Utilisateur non privilégié : un processus compromis ne doit pas être root dans son conteneur.
RUN addgroup -S runtrack && adduser -S runtrack -G runtrack
USER runtrack

WORKDIR /app
COPY --from=build --chown=runtrack:runtrack /build/runtrack-app/target/runtrack-app-*.jar app.jar

# 8080 pour l'API, 8081 pour les endpoints d'exploitation que l'ingress n'expose pas.
EXPOSE 8080 8081

# MaxRAMPercentage plutôt qu'un Xmx fixe : la JVM lit alors la limite du conteneur, et la même
# image tient dans un pod de 512 Mo comme dans un pod de 4 Go sans qu'on la reconstruise.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# ExitOnOutOfMemoryError : une JVM à court de mémoire répond encore aux sondes tout en ne
# servant plus rien. Mieux vaut mourir et laisser l'orchestrateur remplacer le pod.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

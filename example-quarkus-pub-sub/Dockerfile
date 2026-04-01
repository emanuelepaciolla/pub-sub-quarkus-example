# =============================================================================
# Multi-stage build — Quarkus fast-jar
# =============================================================================
#
# Stage 1: compila con Maven e produce target/quarkus-app/
# Stage 2: copia solo il runtime (nessun JDK, nessun Maven)
#
# Build + run con docker-compose (vedi docker-compose.full.yml):
#   docker-compose -f docker-compose.full.yml up --build
# =============================================================================

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Scarica le dipendenze Maven prima di copiare il sorgente.
# Questo layer viene ricreato SOLO se cambia pom.xml, non ad ogni modifica
# del codice → build successive molto più veloci grazie alla cache Docker.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Compila e pacchettizza (salta i test: vengono eseguiti in CI, non in fase di build)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /deployments

# Quarkus fast-jar: lib/ è separata dal codice applicativo.
# Questo permette a Docker di cachare il layer delle dipendenze separatamente
# dal codice → rebuild più veloci quando cambia solo l'applicazione.
COPY --from=build /app/target/quarkus-app/lib/    ./lib/
COPY --from=build /app/target/quarkus-app/*.jar    ./
COPY --from=build /app/target/quarkus-app/app/     ./app/
COPY --from=build /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

# quarkus.http.host=0.0.0.0: in container il server deve ascoltare su tutte le
# interfacce, non solo localhost (default di Quarkus).
ENTRYPOINT ["java", \
  "-Dquarkus.http.host=0.0.0.0", \
  "-Djava.util.logging.manager=org.jboss.logmanager.LogManager", \
  "-jar", "/deployments/quarkus-run.jar"]

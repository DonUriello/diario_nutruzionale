# ============================================================
#  Costruzione
# ============================================================
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# Le dipendenze cambiano molto meno del codice: copiando prima il solo
# pom.xml, Docker riusa la cache di questo strato finché non tocchi le
# dipendenze, e le ricompilazioni non riscaricano mezzo Maven Central.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# -DskipTests non esegue i test ma li COMPILA lo stesso: basta un file di
# test con una dipendenza mancante per far fallire il build di produzione.
# -Dmaven.test.skip=true salta anche la compilazione.
RUN mvn -B clean package -Dmaven.test.skip=true


# ============================================================
#  Immagine finale
# ============================================================
FROM eclipse-temurin:25-jre
WORKDIR /app

# Non girare come root.
RUN useradd --system --uid 1001 app
USER app

COPY --from=build --chown=app /src/target/*.jar application.jar

# Render assegna la porta tramite la variabile PORT e ignora EXPOSE:
# è application.yml a leggerla (server.port: ${PORT:8080}).
EXPOSE 8080

# Senza questo la JVM in container piccoli sottostima la memoria disponibile.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "application.jar"]

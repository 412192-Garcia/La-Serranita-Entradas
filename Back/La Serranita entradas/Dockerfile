# syntax=docker/dockerfile:1

# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Se copia sólo el pom primero para que Docker cachee las dependencias descargadas
# mientras no cambie el pom.xml: recompilar por un cambio de código no re-descarga nada.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Etapa 2: runtime ----------
# JRE (no JDK) alpine: liviano, no hace falta el toolchain de compilación en producción.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
# Sin esto el JVM arranca en UTC (default del contenedor) y todos los LocalDateTime.now()
# quedan 3 horas adelantados respecto de la hora real en el parque.
ENV TZ=America/Argentina/Buenos_Aires

ENTRYPOINT ["java", "-jar", "app.jar"]

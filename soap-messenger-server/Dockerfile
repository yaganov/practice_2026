FROM maven:3.9.16-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY src src

RUN chmod +x mvnw \
	&& ./mvnw test

FROM build AS package

RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine-3.23 AS runtime

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=package /app/target/*.jar app.jar

RUN chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

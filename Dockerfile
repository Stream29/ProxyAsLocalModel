FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties gradlew /app/
COPY --chown=gradle:gradle gradle/ /app/gradle/

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon dependencies
COPY --chown=gradle:gradle src/ /app/src/
RUN ./gradlew build --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

RUN chown root:app /app && chmod 2775 /app

COPY --from=build --chmod=644 /app/build/libs/*.jar /app/app.jar

EXPOSE 11434 1234

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
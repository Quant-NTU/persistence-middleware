ARG GRADLE_VERSION=latest
ARG OPENJDK_VERSION=21-slim

FROM gradle:${GRADLE_VERSION} AS gradle
COPY --chown=gradle:gradle . /app/model
WORKDIR /app/model
RUN gradle build -x test

FROM gradle:${GRADLE_VERSION} AS dev

FROM openjdk:${OPENJDK_VERSION} AS jdk

FROM jdk AS local
COPY --from=gradle /app/model/build/libs/*.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=local", "-jar", "/app/server.jar"]

FROM jdk AS stag
COPY --from=gradle /app/model/build/libs/*.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=stag", "-jar", "/app/server.jar"]

FROM jdk AS prod
COPY --from=gradle /app/model/build/libs/*.jar /app/server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "/app/server.jar"]
FROM gradle:jdk8 AS builder
WORKDIR /app
COPY . /app
RUN ./gradlew clean jar

FROM jboss/keycloak:14.0.0
COPY --from=builder /app/build/libs/docker-user-namespace-mapper.jar /opt/jboss/keycloak/providers/docker-user-namespace-mapper.jar
CMD ["-Dkeycloak.profile.feature.docker=enabled"]

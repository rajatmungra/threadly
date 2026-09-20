FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

ARG MODULE
RUN test -n "$MODULE" || (echo "MODULE build-arg is required" && exit 1)

COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/
COPY identity-service/pom.xml identity-service/
COPY community-service/pom.xml community-service/
COPY post-service/pom.xml post-service/
COPY comment-service/pom.xml comment-service/
COPY notification-service/pom.xml notification-service/

COPY ${MODULE}/src ${MODULE}/src

RUN mvn clean package -pl ${MODULE} -am -DskipTests --batch-mode

FROM eclipse-temurin:21-jre

ARG MODULE
RUN test -n "$MODULE" || (echo "MODULE build-arg is required" && exit 1)

WORKDIR /app

COPY --from=builder --chown=1000:1000 /workspace/${MODULE}/target/*.jar app.jar

USER 1000:1000

ENTRYPOINT ["java", "-jar", "app.jar"]

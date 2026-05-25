FROM maven:3.9.11-eclipse-temurin-25 AS build

ARG SERVICE_DIR
WORKDIR /workspace

COPY pom.xml pom.xml
COPY services services
RUN mvn -q -pl "${SERVICE_DIR}" -am -DskipTests package

FROM eclipse-temurin:25-jre

ARG SERVICE_DIR
ARG SERVICE_JAR
WORKDIR /app

COPY --from=build "/workspace/${SERVICE_DIR}/target/${SERVICE_JAR}" /app/service.jar
COPY --from=build "/workspace/${SERVICE_DIR}/target/libs" /app/libs

ENV VERICOV_SERVICE_HOST=0.0.0.0
EXPOSE 8080

CMD ["sh", "-c", "java -Dserver.host=${VERICOV_SERVICE_HOST:-0.0.0.0} -jar /app/service.jar"]

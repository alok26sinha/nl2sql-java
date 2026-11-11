FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -pl nl2sql-server -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/nl2sql-server/target/nl2sql-*.jar /app/nl2sql.jar

ENV NL2SQL_SERVER_HOST=0.0.0.0
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/nl2sql.jar"]

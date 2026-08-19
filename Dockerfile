FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app
COPY --from=backend-build /build/target/nirikshan-0.0.1-SNAPSHOT.jar /app/nirikshan.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xms64m", "-Xmx160m", "-XX:MaxMetaspaceSize=160m", "-XX:MaxDirectMemorySize=32m", "-XX:ActiveProcessorCount=1", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/nirikshan.jar"]

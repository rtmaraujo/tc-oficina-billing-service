FROM eclipse-temurin:17-jdk-alpine as build
WORKDIR /app
COPY src/main/java /app/src/main/java
COPY src/main/resources /app/src/main/resources
COPY pom.xml /app
RUN ./mvnw clean package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/tc-oficina-billing-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

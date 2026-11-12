# Build from the repository root: docker build -f order-service/Dockerfile -t shop/order-service .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY contracts contracts
COPY messaging-support messaging-support
COPY order-service/pom.xml order-service/
COPY product-service/pom.xml product-service/
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -pl order-service -am package -DskipTests
RUN java -Djarmode=tools -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
# Dependencies change less often than our code, so they go into earlier layers.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
USER 1001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]

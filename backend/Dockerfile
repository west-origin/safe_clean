FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Docker 없이 실행 가능한 단위·슬라이스·구조 테스트를 빌드 때 실행한다.
# Testcontainers 통합 테스트는 CI의 mvnw verify에서 실행한다.
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jre-noble
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/api-*.jar /app/api.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]

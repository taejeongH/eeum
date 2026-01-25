# 1. 빌드 단계
FROM gradle:8.5-jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src/backend

RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar --no-daemon -x test

# 2. 실행 단계
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
EXPOSE 8080

COPY --from=build /home/gradle/src/backend/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=secret"]
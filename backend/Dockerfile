# 1. 빌드 단계
FROM gradle:8.5-jdk21 AS build
# 현재 폴더(backend/)의 모든 내용을 복사
COPY --chown=gradle:gradle .. /home/gradle/src
WORKDIR /home/gradle/src

# 이미 backend 폴더 안이므로 바로 실행
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar --no-daemon -x test

# 2. 실행 단계
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
EXPOSE 8080

# 빌드 결과물 위치도 현재 폴더 기준의 build/libs로 변경
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=secret"]
# Java API with the built listener UI. Build context: repository root.
FROM node:24.15.0-alpine3.22 AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
COPY contracts/ /src/contracts/
RUN npm run build

FROM maven:3.9.13-eclipse-temurin-21 AS java
WORKDIR /src
COPY pom.xml ./
COPY radio-ingestion/ radio-ingestion/
COPY radio-editorial/ radio-editorial/
COPY radio-edition/ radio-edition/
COPY radio-persistence/ radio-persistence/
COPY radio-api/ radio-api/
COPY contracts/ contracts/
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21.0.10_7-jre
WORKDIR /app
COPY --from=java /src/radio-api/target/classes classes/radio-api
COPY --from=java /src/radio-edition/target/classes classes/radio-edition
COPY --from=java /src/radio-editorial/target/classes classes/radio-editorial
COPY --from=java /src/radio-ingestion/target/classes classes/radio-ingestion
COPY --from=web /src/web/dist web
USER 10001
EXPOSE 8151
ENTRYPOINT ["java", "-cp", "/app/classes/radio-api:/app/classes/radio-edition:/app/classes/radio-editorial:/app/classes/radio-ingestion", "ro.bitboy.dailysignals.DailySignals"]
CMD ["8151"]

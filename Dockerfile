FROM eclipse-temurin:11

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew clean assemble

EXPOSE 8080

CMD ["java", "-Dserver.port=${PORT}", "-jar", "build/libs/b-cocina.war"]
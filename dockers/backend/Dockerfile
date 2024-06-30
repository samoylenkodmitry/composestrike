FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
SHELL ["/bin/ls", "-l"]
COPY ../../server/build/libs/server-all.jar /app/server.jar
ENTRYPOINT ["java", "-jar", "server.jar"]
FROM openjdk:8-jre-alpine
COPY target/uberjar/central-system.jar /app/central-system.jar
WORKDIR /app
EXPOSE 8080
CMD ["java", "-jar", "central-system.jar"]

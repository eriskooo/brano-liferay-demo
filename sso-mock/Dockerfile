# JAR zbuildí docker/build-and-deploy (mvn -f sso-mock/pom.xml verify) – image ho len zabalí.
FROM eclipse-temurin:21-jre
COPY target/sso-mock.jar /app/sso-mock.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/sso-mock.jar"]

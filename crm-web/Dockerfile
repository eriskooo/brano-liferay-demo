# WAR zbuildí docker/build-and-deploy (mvn -f crm-web/pom.xml verify) – image ho len zabalí.
FROM eclipse-temurin:21-jre
COPY target/crm-web.war /app/crm-web.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/crm-web.war"]

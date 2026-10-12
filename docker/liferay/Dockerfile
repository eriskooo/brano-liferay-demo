# Liferay Portal 6.2 CE GA6 (Tomcat 7) na Jave 8 – 1:1 s legacy.
# Java 8 MUSÍ mať update <= 255: zabalená ICU4J neprečíta "1.8.0_504" (Invalid version number ... greater than 255),
# portál potom nevytvorí company a padá na ArrayIndexOutOfBoundsException v PortalInstances.
FROM azul/zulu-openjdk:8u252

RUN apt-get update && apt-get install -y --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*

ARG BUNDLE_URL=https://releases-cdn.liferay.com/portal/6.2.5-ga6/liferay-portal-tomcat-6.2-ce-ga6-20160112152609836.zip
ARG BUNDLE_SHA256=930f3969a19dd2bdd847dbdd48e8176fa4884ef2608f5fec38efcad9b279dc8c

# Bundle sa sťahuje pri builde image (do repa nepatrí); HSQL z bundlu sa zmaže, portál si pri prvom štarte
# vytvorí čistú schému v pomenovanom volume.
RUN curl -fsSL -o /tmp/liferay.zip "$BUNDLE_URL" \
    && echo "$BUNDLE_SHA256  /tmp/liferay.zip" | sha256sum -c - \
    && unzip -q /tmp/liferay.zip -d /opt \
    && mv /opt/liferay-portal-6.2-ce-ga6 /opt/liferay \
    && rm -rf /tmp/liferay.zip /opt/liferay/data/hsql

COPY portal-ext.properties /opt/liferay/portal-ext.properties
# API SSO portálu do spoločného classloadera (legacy: provided JBoss modul). Kopíruje ho build-and-deploy.
COPY sso-portal-api.jar /opt/liferay/tomcat-7.0.62/lib/ext/sso-portal-api.jar

EXPOSE 8080
CMD ["/opt/liferay/tomcat-7.0.62/bin/catalina.sh", "run"]

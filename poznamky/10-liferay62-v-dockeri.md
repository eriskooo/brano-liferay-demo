# Liferay 6.2 lokálně v Dockeru (jak rozběhnout „starou“ aplikaci)

Zjištěno při stavbě dema (2026-10). Recept je v `docker/liferay/Dockerfile`.

## Recept
- Oficiální Docker image pro 6.2 neexistuje (oficiální jsou od 7.0). Vlastní image: bundle
  `liferay-portal-tomcat-6.2-ce-ga6-*.zip` z `releases-cdn.liferay.com` (≈300 MB, Tomcat 7.0.62) + JDK 8.
- **Java 8 s update ≤ 255** (např. `azul/zulu-openjdk:8u252`). Na 8u504 portál nenastartuje: přibalená ICU4J
  parsuje `java.version` a vyhodí `IllegalArgumentException: Invalid version number: Version number may be negative
  or greater than 255`; následně nevznikne company a každý request padá na `ArrayIndexOutOfBoundsException: 0`
  v `PortalInstances._getDefaultCompanyId`. Na reálném projektu: pozor při „bezpečnostním“ upgradu JDK 8.
- Přibalená HSQL se smaže, portál si při prvním startu vytvoří čisté schéma (`Create tables and populate with
  default data`). Chyba `object not found: LOCK_` při prvním startu je očekávaná.
- `portal-ext.properties`: `setup.wizard.enabled=false`; admin `test@liferay.com` / `test`.
- Start 55–80 s; první request během „zahřívání“ může vrátit 400.
- Build pluginů bez soukromého repa: Liferay 6.2.5 API (`portal-service`, `util-*`) a Spring 4.3.6 jsou v Maven
  Central; JDK 21 s `maven.compiler.release=8` vyrobí Java 8 bytecode.

## Deploy
- Hot deploy: WAR do `deploy/` (v demu mount `docker/deploy`). Po zpracování ho Liferay smaže. Úspěch v logu:
  `N portlets for <war> are available for use`, `Hook for <war> is available for use`.
- Název WAR-u: Liferay z `xyz-portlets.war` udělá kontext `xyz-portlet` – ID portletů jsou `<portlet>_WAR_xyzportlet`.
- Pořadí: theme před portlety, pokud ho portlety mají v `required-deployment-contexts`.
- JAR v `tomcat/lib/ext` (náhrada JBoss modulu) nelze hot-deploynout – mění se rebuildem image.
- Po `docker compose up` s novým image jsou WAR-y pryč (webapps není ve volume) – skript je nasadí znovu.

## Oprávnění a stránky
- Host nevidí portlet, který není na stránce („You do not have the roles required…“). Řešení: portlet umístit
  na stránku (startup hook) nebo `add-default-resource=true` (runtime portlet v theme).
- Startup hook (`application.startup.events`) běží při každém deployi i startu – musí být idempotentní.

## Jedna adresa pro staré i nové stránky
- nginx před portálem; `proxy_set_header Host $http_host`, aby portál skládal URL na veřejnou adresu.
- Cookies nerozlišují port ani aplikaci: dvě aplikace za jednou adresou si přepíší `JSESSIONID`. Mock SSO proto
  `HttpSession` nepoužívá; nová aplikace v podprojektu 2 musí mít jiný název session cookie nebo žádnou session.

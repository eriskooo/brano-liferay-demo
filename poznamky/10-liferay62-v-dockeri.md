# Liferay 6.2 lokálne v Dockeri (ako rozbehnúť „starú“ appku)

Zistené pri stavbe dema (2026-10). Recept je v `docker/liferay/Dockerfile`.

## Recept
- Oficiálny Docker image pre 6.2 neexistuje (oficiálne sú od 7.0). Vlastný image: bundle
  `liferay-portal-tomcat-6.2-ce-ga6-*.zip` z `releases-cdn.liferay.com` (≈300 MB, Tomcat 7.0.62) + JDK 8.
- **Java 8 s update ≤ 255** (napr. `azul/zulu-openjdk:8u252`). Na 8u504 portál nenaštartuje: zabalená ICU4J
  parsuje `java.version` a hodí `IllegalArgumentException: Invalid version number: Version number may be negative
  or greater than 255`; následne nevznikne company a každý request padá na `ArrayIndexOutOfBoundsException: 0`
  v `PortalInstances._getDefaultCompanyId`. Na reálnom projekte: pozor pri „bezpečnostnom“ upgrade JDK 8.
- Bundlovaná HSQL sa zmaže, portál si pri prvom štarte vytvorí čistú schému (`Create tables and populate with
  default data`). Chyba `object not found: LOCK_` pri prvom štarte je očakávaná.
- `portal-ext.properties`: `setup.wizard.enabled=false`; admin `test@liferay.com` / `test`.
- Štart 55–80 s; prvý request počas „zahrievania“ môže vrátiť 400.
- Build pluginov bez súkromného repa: Liferay 6.2.5 API (`portal-service`, `util-*`) a Spring 4.3.6 sú v Maven
  Central; JDK 21 s `maven.compiler.release=8` vyrobí Java 8 bytecode.

## Deploy
- Hot deploy: WAR do `deploy/` (v deme mount `docker/deploy`). Po spracovaní ho Liferay zmaže. Úspech v logu:
  `N portlets for <war> are available for use`, `Hook for <war> is available for use`.
- Názov WAR-u: Liferay z `xyz-portlets.war` spraví kontext `xyz-portlet` – ID portletov sú `<portlet>_WAR_xyzportlet`.
- Poradie: theme pred portletmi, ak ho portlety majú v `required-deployment-contexts`.
- JAR v `tomcat/lib/ext` (náhrada JBoss modulu) sa nedá hot-deploynúť – mení sa rebuildom image.
- Po `docker compose up` s novým image sú WAR-y preč (webapps nie je vo volume) – skript ich nasadí znova.

## Oprávnenia a stránky
- Hosť nevidí portlet, ktorý nie je na stránke („You do not have the roles required…“). Riešenie: portlet umiestniť
  na stránku (startup hook) alebo `add-default-resource=true` (runtime portlet v theme).
- Startup hook (`application.startup.events`) beží pri každom deployi aj štarte – musí byť idempotentný.

## Jedna adresa pre staré aj nové stránky
- nginx pred portálom; `proxy_set_header Host $http_host`, aby portál skladal URL na verejnú adresu.
- Cookies nerozlišujú port ani aplikáciu: dve appky za jednou adresou si prepíšu `JSESSIONID`. Mock SSO preto
  `HttpSession` nepoužíva; nová appka v podprojekte 2 musí mať iný názov session cookie alebo žiadnu session.

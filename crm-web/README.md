# Nová appka „po“ (Spring Boot) – zmigrovaný blacklist

Portlet **blacklist ne-zákazníkov** zo starej appky (`../legacy`, Liferay 6.2) prepísaný na Spring Boot 4.1.1 + JSP.
Beží **súčasne** so starou appkou za jedným nginx: blacklist obsluhuje nová appka, dashboard ostáva v Liferay.
Obe appky zdieľajú identitu (SSO mock, cookie `SSO_ID`) a session (Redis hash `crm:session:{SSO_ID}`).

## Spustenie

Predpoklady: Docker Desktop, JDK 21, Maven 3.9, Git Bash.

```bash
bash docker/build-and-deploy.sh      # build legacy + sso-mock + crm-web, docker compose up, deploy do Liferay
bash docker/smoke.sh                 # stará appka (blacklist priamo na :8080)
bash docker/smoke-migration.sh       # súbeh – na konci "MIGRATION SMOKE OK"
```

| Adresa | Čo |
|---|---|
| http://localhost:8000 | **vstup** – nginx: `/web/…` Liferay, `/crm/…` nová appka, `/sso/…` SSO mock |
| http://localhost:8000/crm/blacklist/search | blacklist v novej appke (sem vedie aj starý odkaz „Blacklist“) |
| http://localhost:8000/web/crm/dashboard | dashboard v starej appke |
| http://localhost:8080/web/crm/blacklist | **starý** blacklist priamo v Liferay – na porovnanie „pred/po“ |
| http://localhost:5540 | RedisInsight – zdieľaná session |

## Preklikanie súbehu

1. Otvor **http://localhost:8000** – nová appka vyžaduje prihlásenie, preto ťa hneď presmeruje na SSO mock:
   vyber *Správca*, zákazník *C-1001* → **Prihlásiť**.
2. Navigácia **Blacklist** v Liferay (alebo http://localhost:8000/web/crm/blacklist) → nginx presmeruje na
   `/crm/blacklist/search`. Hlavička vyzerá rovnako, ale je to Spring Boot – ladiaci riadok ukazuje
   *zdroj čítania = Redis (nová appka), flag starej appky = PORTLET, SSO_ID = …*.
3. Vyhľadaj priezvisko `vzork` → otvor **Peter Vzorka**. Hlavička novej appky ukáže *Naposledy otvorený záznam*.
   V termináli: `bash docker/show-session.sh` → v hashi je `APP_LAST_VIEWED_RECORD {"id":1,"fullName":"Peter Vzorka"}`
   zapísaný **novou** appkou.
4. Klikni **Dashboard** (navigácia novej appky vedie do Liferay). Pri flagu **PORTLET** hlavička starej appky posledný
   záznam **neukáže** – číta svoju PortletSession, kam nová appka nezapisuje.
5. `bash docker/session-flag.sh REDIS` a obnov dashboard → hlavička Liferay ukáže
   **„Naposledy otvorený záznam: Peter Vzorka“** z novej appky. Ladiaci riadok novej appky ukáže *flag starej appky = REDIS*.
   Poučenie: flag sa prepína na `REDIS` najneskôr s nasadením prvej zmigrovanej stránky.
6. **Zmeniť zákazníka** (v ľubovoľnej appke) → SSO mock → *C-1002*: obe appky ukážu nového zákazníka
   (dashboard iné produkty). Zákazníka vlastní SSO portál.
7. V novej appke zmeň stav záznamu → hláška sa zobrazí raz (prešla cez Redis). Odstránenie identifikátora (AJAX)
   ide len cez POST.
8. **Odhlásiť** → prihlás sa ako *Čitateľ* → v novej appke nevidí zmeny ani „Nový záznam“, priamy POST dostane 403.
9. **Pred/po:** otvor http://localhost:8080/web/crm/blacklist (starý portlet) a porovnaj URL, správanie a kód –
   `docs/MIGRACIA.md`.
10. `docker compose -f docker/docker-compose.yml stop redis` → nová appka beží ďalej (bez hlášok a posledného
    záznamu), `… start redis` ho vráti.

## Čo je kde

| Koncept | Súbor |
|---|---|
| fasáda session (kontrakt legacy, nová implementácia) | `src/main/java/com/example/crm/session/CrmSession.java`, `SharedCrmSession.java` |
| identita zo SSO | `session/SsoClient.java`, `SsoIdentityResolver.java` |
| zdieľaná session (Redis, JSON kontrakt) | `session/RedisSharedSessionStore.java`, `SharedSessionKeys.java` |
| „portlet“ = skupina controllerov | `web/portlet/LegacyPortlet.java`, `LegacyPortletInterceptor.java` |
| hlavička (náhrada theme + portletu header) | `webapp/WEB-INF/jsp/layout/header.jspf`, `web/portlet/LayoutModelInterceptor.java` |
| tagy s rovnakým rozhraním | `web/tag/*`, `webapp/WEB-INF/tld/*.tld` |
| blacklist | `blacklist/*Controller.java`, `webapp/WEB-INF/jsp/blacklist/*.jsp` |

## Testy

```bash
mvn -q -B -f crm-web/pom.xml verify    # unit, MockMvc, Tomcat + Jasper, Redis cez Testcontainers (potrebuje Docker)
```

## Vedomé obmedzenia

- Každá appka má vlastné in-memory dáta blacklistu (rovnaký seed). Legacy backend beží v JVM Liferay, nová appka ho
  zdieľať nemôže – zmena v novej appke sa v starom blackliste na :8080 neprejaví. Zdieľa sa len session.
- Stránky novej appky berú CSS z Liferay (`/html/themes/classic/…`) cez nginx – mimo :8000 sú len so základným štýlom.

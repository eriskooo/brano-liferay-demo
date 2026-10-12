# Legacy appka „pred“ (Liferay 6.2)

Príklady **blacklist ne-zákazníkov** a **zrušenie služby** ako portlety v reálnom Liferay 6.2 – rovnaký stack
a mechanizmy ako legacy CRM, napísané nanovo a anonymizované. Podprojekt 2 ich zmigruje na Spring Boot.

## Stack

| Vec | Verzia |
|---|---|
| Portál | Liferay Portal 6.2 CE GA6 (Tomcat 7.0.62, HSQL) v Dockeri |
| Java | 8 (Zulu 8u252 – update musí byť ≤ 255, viď `poznamky/10`) |
| Framework | Spring 4.3.6 Portlet MVC, JSP + JSTL, vlastné taglibs |
| Zdieľaná session | Redis 7.4 (Jedis 2.9), JSON |
| SSO portál | mock – Spring Boot 4.1.1 / Java 21 (`../sso-mock`) |

## Moduly (rovnaké delenie ako legacy)

| Modul | Čo obsahuje | V reálnom legacy |
|---|---|---|
| `sso-portal-api` | API SSO portálu, service locator, HTTP klient (shaded JAR v `tomcat/lib/ext`) | `provided` API + JBoss modul |
| `frontend-base-customization` | session fasáda `SessionManagerCustom` + duálna session, ACL, tagy `portal:*`/`common:*`, `DispatcherPortletCustom`, `BaseCrmController` | `FBC` |
| `customercare-portlets` | portlety `blacklist`, `dashboard`, `header`, in-memory backend, JSP, startup hook, hooked `status.jsp` | `CP`, `J` |
| `customercare-theme` | Velocity theme, vkladá portlet hlavičky | `customercare-theme` |

## Spustenie

Predpoklady: Docker Desktop (~3 GB RAM pre Liferay), JDK 21, Maven 3.9, Git Bash (smoke test).

```bash
bash docker/build-and-deploy.sh     # build + docker compose up + hot deploy (PowerShell: docker\build-and-deploy.ps1)
bash docker/smoke.sh                # end-to-end overenie, na konci "SMOKE OK"
```

Prvý štart trvá 1–3 minúty (stiahnutie bundlu, vytvorenie DB). Log: `docker compose -f docker/docker-compose.yml logs -f liferay`.
Zastavenie: `docker compose -f docker/docker-compose.yml down` (dáta portálu ostanú vo volume; `down -v` ich zmaže).

| Adresa | Čo |
|---|---|
| http://localhost:8000 | **CRM** (nginx → Liferay + SSO mock), začni tu |
| http://localhost:8000/sso/login | SSO portál (mock) – prihlásenie, zmena zákazníka, odhlásenie |
| http://localhost:5540 | RedisInsight – zdieľaná session v Redise |
| http://localhost:8080 | priamo Liferay (admin `test@liferay.com` / `test`) |

Demo operátori: **Správca** (`spravca`) – všetko; **Čitateľ** (`citatel`) – len čítanie blacklistu.
Zákazníci: `C-1001` Ján Vzorový (3 produkty), `C-1002` Firma Ukážka s.r.o. (2 produkty).

## Preklikanie

1. Otvor **http://localhost:8000** → presmeruje na `/web/crm/blacklist`. Nie si prihlásený: hlavička aj portlet
   ukážu „Prihlásiť“. Pod hlavičkou je ladiaci riadok *Zdieľaná session: zdroj čítania = PORTLET, SSO_ID = -*.
2. Klikni **Prihlásiť** → stránka SSO mocku. Vyber *Správca* a zákazníka *C-1001* → **Prihlásiť**.
   Vrátiš sa do CRM: hlavička ukazuje operátora, zákazníka a v ladiacom riadku tvoje `SSO_ID`.
3. **Blacklist – vyhľadávanie:** priezvisko `vzork` → **Hľadať**. Action fáza nastaví render parameter
   a zobrazia sa výsledky (Peter Vzorka, Peter Vzorkový). Skús aj meno `Mária` (diakritika) alebo prázdny formulár
   (chyba „Zadajte aspoň jedno kritérium.“ – validačná chyba prežila action → render).
4. **Detail:** klikni na *Peter Vzorka*. Hlavička teraz ukazuje *Naposledy otvorený záznam: Peter Vzorka* –
   to je hodnota zo session fasády (a v Redise).
   - **Zmena stavu a platnosti** → *Neaktívny* → **Uložiť**: hláška v hlavičke (raz), stav sa zmení.
   - **Pridať poznámku**: poznámka s menom operátora; prázdna poznámka → chybová hláška.
   - **Odstrániť** pri identifikátore: AJAX (resource fáza) prekreslí len zoznam; posledný identifikátor
     sa odstrániť nedá (hláška vo fragmente).
5. **Nový záznam** (na stránke vyhľadávania): zmena *Dôvodu* cez AJAX prekreslí povolené *Podmienky*.
   Odošli neúplný formulár → chyby pri poliach; vyplnený → detail nového záznamu.
6. **Dashboard** (navigácia hore): produkty zákazníka zo SSO. **Zrušiť** pri *Tarif Neobmedzený* →
   formulár (`page=tariffDeactivation`). Zmena typu alebo dátumu podania zavolá JSON resource a nastaví
   *Najskôr:*. Príliš skorý dátum → chyba; správny → potvrdenie so žiadosťou.
   Legacy pasca `@SessionAttributes`: pri *Tarif Neobmedzený* zvoľ *Zrušenie* a dátum „Ku dňu“ skorší než *Najskôr*
   → **Odoslať** skončí chybou (formulár ostane v session). Vráť sa na produkty a otvor *Tarif Štandard* – typ a dátumy
   ostali z predchádzajúceho formulára. Po úspešnom odoslaní sa formulár zo session odstráni (`setComplete`), preto
   sa pasca ukáže len po neúspešnom pokuse; migrácia ju odstráni úplne.
7. **Zmena zákazníka:** v hlavičke **Zmeniť zákazníka** → v SSO mocku vyber `C-1002` → dashboard ukáže iné produkty.
   Zákazníka vlastní SSO portál, nie CRM.
8. **ACL:** **Odhlásiť** → prihlás sa ako *Čitateľ*. Blacklist vidí, ale bez tlačidiel zmeny a bez „Nový záznam“;
   dashboard ukáže *Nemáte oprávnenie DEACTIVATION.*
9. **Chybová stránka portálu:** http://localhost:8000/web/crm/neexistuje → prepísaná `status.jsp` (hook).

## Zdieľaná session naživo

Fasáda (`SessionManagerCustom`) zapisuje hlášky a vybrané atribúty **do PortletSession aj do Redisu**
(hash `crm:session:{SSO_ID}`, JSON, TTL 30 min). Feature flag `crm:flags:session-read-source` určuje, odkiaľ číta.

- **V prehliadači:** ladiaci riadok pod hlavičkou ukazuje zdroj čítania a `SSO_ID`.
- **V termináli:** `bash docker/show-session.sh` (PowerShell: `docker\show-session.ps1`) vypíše flag
  a všetky zdieľané session s TTL a obsahom, napr.
  `APP_LAST_VIEWED_RECORD  {"id":1,"fullName":"Peter Vzorka"}`. Spusti ho po každom kroku preklikania –
  uvidíš, ako pribúdajú a miznú hlášky (`APP_MESSAGES`) a mení sa posledný záznam.
- **V GUI:** http://localhost:5540 → databáza *crm-shared-session* (zaregistruje ju `build-and-deploy`;
  ak chýba: *Add database*, host `redis`, port `6379`) → kľúče `crm:session:*`.
- **Prepnutie čítania na Redis** (bez reštartu a deployu): `bash docker/session-flag.sh REDIS`
  (`docker\session-flag.ps1 REDIS`). Obnov stránku – ladiaci riadok ukáže `REDIS`, hlavička číta posledný
  záznam z Redisu. Späť: `session-flag.sh PORTLET`.
- **Výpadok Redisu:** `docker compose -f docker/docker-compose.yml stop redis` – CRM beží ďalej (zápis do Redisu
  sa len zaloguje, pri flagu `REDIS` sa číta zo starej session). `… start redis` ho vráti.
- **Čo sa nezdieľa:** formuláre v `@SessionAttributes` (kritériá vyhľadávania, nový záznam, žiadosť o zrušenie)
  ostávajú v `PortletSession` – zdieľa sa len to, čo ide cez fasádu a je v registri `SharedKeys`.

V podprojekte 2 bude za tým istým nginx aj Spring Boot appka (`/crm/`), ktorá prečíta ten istý hash.

## Súbeh so zmigrovanou appkou
Blacklist je zmigrovaný do `../crm-web` – na :8000 ho nginx presmeruje do novej appky, starý portlet je na
http://localhost:8080/web/crm/blacklist. Návod na preklikanie súbehu: [`../crm-web/README.md`](../crm-web/README.md).

## Testy

```bash
mvn -q -B -f legacy/pom.xml verify     # unit + wiring testy (JUnit 5, portlet mocky Spring 4.3)
mvn -q -B -f sso-mock/pom.xml verify   # SSO mock (MockMvc)
bash docker/smoke.sh                   # bežiaci stack end-to-end
```

## Známe obmedzenia (vedome)

- Render fáza portletu nevie presmerovať – bez SSO session portlet ukáže odkaz „Prihlásiť“, nie redirect.
- Theme nededí z Liferay `_styled`, admin dockbar je preto bez štýlov.
- Pre Liferay je každý operátor hosť; identitu pozná len SSO mock (v legacy autologin cez SSO portál).
- HSQL namiesto Oracle, Tomcat namiesto JBoss, `params="page=…"` namiesto vlastného dashboard dispatchu.

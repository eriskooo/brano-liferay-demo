# Playbook: ako zmigrovať jeden portlet / jednu obrazovku na reálnom projekte

Skratky ciest (skutočné hodnoty `$FE` a `<pkg>` sú v lokálnom `LOCAL-aliasy.md`):
- `CP` = `customercare-portlets/src/main`
- `J` = `CP/java/<pkg>/crm/frontend` (controllery, handlery, interceptory, utils)
- `FBC` = `frontend-base-customization/src/main/java/<pkg>/crm/frontend/base` (session, tagy, portal utils)

## 0. Mapa „kde čo hľadať“
| Hľadám | Kde |
|---|---|
| zoznam portletov a ich triedy | `CP/webapp/WEB-INF/portlet.xml`, `liferay-portlet.xml` |
| Spring kontext portletu (handler mapping, interceptory, scan) | `CP/webapp/WEB-INF/<portlet>-portlet.xml` |
| root kontext (backend importy) | `CP/resources/applicationContext.xml` → `context-jboss.xml` / `context-local.xml` → `portlet-base-context.xml` |
| controllery | `J/portlet/controller/<oblasť>/` |
| spoločný base controller | `J/portlet/controller/BaseCustomerCareController.java` (dedí z externej core knižnice) |
| dashboard dispatch podľa `page` | `J/handler/DashboardHandlerMapping.java`, register `J/handler/dashboardpages/DashboardPages.java` |
| page-flow objednávok | `J/handler/CustomScenarioHandlerMapping.java` |
| typy požiadaviek zákazníka | `J/handler/customerrequest/CustomerRequestTypeHandlerMapping.java` + anotácia `@CrType` |
| interceptory | `J/interceptor/` |
| modely / validátory | `J/portlet/model/<oblasť>/`, `J/portlet/validator/<oblasť>/` |
| JSP | `CP/webapp/WEB-INF/jsp/<portlet>/` |
| TLD a tag files | `CP/webapp/WEB-INF/tld/`, `CP/webapp/WEB-INF/tags/` |
| implementácia `portal:*URL` tagov (tu je Liferay!) | `FBC/jsp/tag/portal/AbstractPortletUrlTag.java` a potomkovia |
| i18n | `FBC/service/i18/MessageSourceServiceCustomImpl.java` (+ `MessageSource` z DB v `portlet-base-context.xml`) |
| session fasáda | `FBC/service/SessionManagerCustom.java`, `FBC/service/impl/SessionManagerCustomImpl.java` |
| friendly URL | `CP/resources/friendly/*.xml`, `J/portlet/CustomerContextFriendlyURLMapper.java` |
| startup hook (provisioning portálu) | `CP/java/<pkg>/crm/hook/ConfigurationHook.java` |
| PoC náhrady | `customercare-web/target/classes/...` (len bytecode), `MIGRATION_PROGRESS.md` |

Užitočné príkazy:
```bash
grep -rl "import com.liferay" CP/java FBC          # kde je priama Liferay závislosť
grep -rn "@ModelAttribute\|@SessionAttributes" J/portlet/controller/<oblasť>
grep -rhoE "<[a-z]+:[A-Za-z]+" CP/webapp/WEB-INF/jsp/<portlet> | sort | uniq -c   # aké tagy JSP používa
```

## 1. Inventúra portletu (pred písaním kódu)
1. V `portlet.xml` nájdi triedu a v `<portlet>-portlet.xml` zisti handler mapping
   (štandardný annotation vs. vlastný dispatch) a interceptory.
2. Vypíš všetky controllery a pre každý: render/action/resource metódy, `@ModelAttribute`,
   `@SessionAttributes`, ktoré session kľúče číta/píše (`getSessionManager()`), ktoré backend služby volá.
3. Vypíš JSP a tagy, ktoré používajú; pre každý vlastný tag zisti triedu v TLD a či siaha na Liferay/portlet API.
4. Zisti ACL privilégiá (`aclPageFilterTag privilegeType=...`) → budúce pravidlá autorizácie.
5. Zisti, odkiaľ sa na portlet odkazuje (deeplinky, `dashboardUrl`, iné portlety) → URL kontrakt.

## 2. Návrh cieľa
- URL: každá render fáza → `GET /oblasť/...`, action → `POST` + redirect, resource → JSON endpoint.
- Session: čo musí prežiť medzi requestmi? Čo je kontext používateľa/zákazníka (príde odinakiaľ)?
- Backend: zostáva rovnaký interface. Najprv postav adaptér/port, za ním stub, potom reálnu implementáciu.

## 3. Implementácia (po malých krokoch, každý s testom)
1. Port (interface) pre backend službu + in-memory implementácia.
2. Controller s testom (`MockMvc` / `@WebMvcTest`): najprv GET (zoznam/detail), potom POST + validácia.
3. View: buď preniesť JSP a nahradiť tagy, alebo nová šablóna/REST + frontend. Rozhodnutie závisí od cieľovej architektúry.
4. Autorizácia podľa ACL privilégií.
5. Porovnanie s pôvodným správaním (screenshot/scenár) → zápis do zoznamu rozdielov.

## 4. Definition of done pre jeden portlet
- [ ] žiadny import `com.liferay.*` ani `javax.portlet.*` v novom kóde a jeho závislostiach
- [ ] každý tag je buď plne implementovaný, alebo zapísaný ako dlh (žiadne tiché no-op)
- [ ] všetky `@ModelAttribute`/`@SessionAttributes` zmapované
- [ ] scenáre end-to-end prejdú proti stubu aj reálnemu backendu
- [ ] ACL pravidlá overené testom

## Ukážka v deme
Celý postup na jednom portlete (blacklist) aj so súbehom starej a novej appky: `docs/MIGRACIA.md`
(pred → po po súboroch), `crm-web/README.md` (preklikanie), `docker/smoke-migration.sh` (overenie).

# Playbook: jak zmigrovat jeden portlet / jednu obrazovku na reálném projektu

Zkratky cest (skutečné hodnoty `$FE` a `<pkg>` jsou v lokálním `LOCAL-aliasy.md`):
- `CP` = `customercare-portlets/src/main`
- `J` = `CP/java/<pkg>/crm/frontend` (controllery, handlery, interceptory, utils)
- `FBC` = `frontend-base-customization/src/main/java/<pkg>/crm/frontend/base` (session, tagy, portal utils)

## 0. Mapa „kde co hledat“
| Hledám | Kde |
|---|---|
| seznam portletů a jejich třídy | `CP/webapp/WEB-INF/portlet.xml`, `liferay-portlet.xml` |
| Spring kontext portletu (handler mapping, interceptory, scan) | `CP/webapp/WEB-INF/<portlet>-portlet.xml` |
| root kontext (backend importy) | `CP/resources/applicationContext.xml` → `context-jboss.xml` / `context-local.xml` → `portlet-base-context.xml` |
| controllery | `J/portlet/controller/<oblasť>/` |
| společný base controller | `J/portlet/controller/BaseCustomerCareController.java` (dědí z externí core knihovny) |
| dashboard dispatch podle `page` | `J/handler/DashboardHandlerMapping.java`, registr `J/handler/dashboardpages/DashboardPages.java` |
| page-flow objednávek | `J/handler/CustomScenarioHandlerMapping.java` |
| typy požadavků zákazníka | `J/handler/customerrequest/CustomerRequestTypeHandlerMapping.java` + anotace `@CrType` |
| interceptory | `J/interceptor/` |
| modely / validátory | `J/portlet/model/<oblasť>/`, `J/portlet/validator/<oblasť>/` |
| JSP | `CP/webapp/WEB-INF/jsp/<portlet>/` |
| TLD a tag files | `CP/webapp/WEB-INF/tld/`, `CP/webapp/WEB-INF/tags/` |
| implementace `portal:*URL` tagů (tady je Liferay!) | `FBC/jsp/tag/portal/AbstractPortletUrlTag.java` a potomci |
| i18n | `FBC/service/i18/MessageSourceServiceCustomImpl.java` (+ `MessageSource` z DB v `portlet-base-context.xml`) |
| session fasáda | `FBC/service/SessionManagerCustom.java`, `FBC/service/impl/SessionManagerCustomImpl.java` |
| friendly URL | `CP/resources/friendly/*.xml`, `J/portlet/CustomerContextFriendlyURLMapper.java` |
| startup hook (provisioning portálu) | `CP/java/<pkg>/crm/hook/ConfigurationHook.java` |
| náhrady z PoC | `customercare-web/target/classes/...` (jen bytecode), `MIGRATION_PROGRESS.md` |

Užitečné příkazy:
```bash
grep -rl "import com.liferay" CP/java FBC          # kde je přímá závislost na Liferay
grep -rn "@ModelAttribute\|@SessionAttributes" J/portlet/controller/<oblasť>
grep -rhoE "<[a-z]+:[A-Za-z]+" CP/webapp/WEB-INF/jsp/<portlet> | sort | uniq -c   # jaké tagy JSP používá
```

## 1. Inventura portletu (před psaním kódu)
1. V `portlet.xml` najdi třídu a v `<portlet>-portlet.xml` zjisti handler mapping
   (standardní annotation vs. vlastní dispatch) a interceptory.
2. Vypiš všechny controllery a pro každý: render/action/resource metody, `@ModelAttribute`,
   `@SessionAttributes`, které session klíče čte/zapisuje (`getSessionManager()`), které backendové služby volá.
3. Vypiš JSP a tagy, které používají; pro každý vlastní tag zjisti třídu v TLD a zda sahá na Liferay/portlet API.
4. Zjisti ACL oprávnění (`aclPageFilterTag privilegeType=...`) → budoucí pravidla autorizace.
5. Zjisti, odkud se na portlet odkazuje (deeplinky, `dashboardUrl`, jiné portlety) → URL kontrakt.
6. **Mapa transakcí:** které managery volané z controllerů portletu otevírají transakci (`@Transactional`,
   `TransactionTemplate`), s jakou propagací a které backendové služby v ní volají → seznam API operací backendu
   ([07-pasce.md](07-pasce.md) bod 27).

## 2. Návrh cíle
- URL: každá render fáze → `GET /oblasť/...`, action → `POST` + redirect, resource → JSON endpoint.
- Session: co musí přežít mezi requesty? Co je kontext uživatele/zákazníka (přijde odjinud)?
- Backend: zůstává stejné rozhraní. Nejprve postav adaptér/port, za ním stub, potom reálnou implementaci.
- Transakce: port odpovídá **celé transakční operaci** (ne jednotlivým službám uvnitř ní); její hranice a propagace
  zůstávají na straně backendu. Data přes port jen jako DTO, ne entity s lazy kolekcemi.

## 3. Implementace (po malých krocích, každý s testem)
1. Port (rozhraní) pro backendovou službu + in-memory implementace.
2. Controller s testem (`MockMvc` / `@WebMvcTest`): nejprve GET (seznam/detail), potom POST + validace.
3. View: buď přenést JSP a nahradit tagy, nebo nová šablona/REST + frontend. Rozhodnutí závisí na cílové architektuře.
4. Autorizace podle ACL oprávnění.
5. Porovnání s původním chováním (screenshot/scénář) → zápis do seznamu rozdílů.

## 4. Definition of done pro jeden portlet
- [ ] žádný import `com.liferay.*` ani `javax.portlet.*` v novém kódu a jeho závislostech
- [ ] každý tag je buď plně implementovaný, nebo zapsaný jako dluh (žádné tiché no-op)
- [ ] všechny `@ModelAttribute`/`@SessionAttributes` zmapované
- [ ] end-to-end scénáře projdou proti stubu i reálnému backendu
- [ ] ACL pravidla ověřená testem
- [ ] každá transakční operace má e2e scénář s chybou uprostřed (nic se nezapíše, resp. REQUIRES_NEW zápis zůstane)

## Ukázka v demu
Celý postup na jednom portletu (blacklist) i se souběhem staré a nové aplikace: `docs/MIGRACIA.md`
(před → po po souborech), `crm-web/README.md` (proklikání), `docker/smoke-migration.sh` (ověření).

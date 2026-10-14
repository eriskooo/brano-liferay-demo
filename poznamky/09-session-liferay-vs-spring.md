# Session: Liferay vs. Spring Boot (před → po)

Jak se v legacy přistupuje k session, čím to nahradit a co je „duální session“ z podkladů.
Ukázky jsou napsané nově podle vzorů, které se v legacy kódu opakují (klíče a třídy jsou anonymizované).
Strana „Spring“ odpovídá implementačnímu plánu dema (`CrmSession`, `HttpSessionCrmSession`).
Zkratky viz [00-konvencie.md](00-konvencie.md), alias `J` (adresář controllerů) viz [06](06-playbook-migracie-portletu.md).

## 1. Dva významy „sdílené session“

### 1.1 Dnes: session sdílená se SSO portálem (legacy)
- Přihlášený operátor a část kontextu (např. vybraný zákazník) nežijí v Liferay, ale v **SSO portálu**.
  Fasáda session manageru (`FBC/service/SessionManagerCustom.java`) je odtamtud čte přes service locator.
- Controllery přistupují k session prakticky jen přes fasádu (`getSessionManager()` ~414× v ~114 souborech).
  Je to **jediný bod**, na kterém stojí návrh v 1.2.

### 1.2 Při migraci: „duální session“ (slide 12 prezentace)
Podklady: Spring Session + Redis, jediný zápisový bod = fasáda, feature flag pro zdroj čtení.
Zbytek této sekce je **odvozený**, prezentace ho dále nerozpracovává.

Aplikace se nemigruje najednou, takže po určitou dobu poběží staré a nové stránky vedle sebe:

```
prohlížeč ──► reverse proxy (routing podle URL)
                 ├── stará Liferay app  (nezmigrované stránky)
                 └── nová Spring app    (zmigrované stránky)
```

Operátor přechází mezi starými a novými stránkami, obě aplikace musí vidět stejný stav
(operátor, aktuální zákazník, rozpracovaná objednávka…; prezentace uvádí 30+ objektů, neověřeno).

- **Zápis:** fasáda zapisuje každou změnu do obou úložišť – staré `PortletSession`/`HttpSession` i Redis.
  Controllery o tom nevědí, kontrakt fasády se nemění.
- **Čtení:** feature flag určuje zdroj. Nejprve staré úložiště (dokud se Redis neověří), potom Redis;
  při problémech lze přepnout zpět bez nasazení.
- **Nová aplikace** čte z Redisu přes Spring Session.
- **Na konci** stará aplikace zanikne, zůstane jen Redis (dává smysl i pro cluster).

### 1.3 Pasti duální session (odvozené, ne z podkladů)
- **Serializace:** stará aplikace Java 8 + `javax`, nová Java 21 + Jakarta. Stejný objekt v Redisu musí
  přečíst obě → spíše JSON / jednoduchá DTO než Java serializace doménových tříd.
- **Cookie:** obě aplikace musí najít stejnou session (stejná doména a path, společné ID session).
- **Timeout a odhlášení:** expirace sladěná v obou úložištích.
- **Mutace bez `setAttribute`:** Spring Session/Redis zapíše jen atribut, který se znovu nastavil.
  Úprava seznamu „na místě“ (`list.add(...)`) se nezreplikuje.
- **Identita ze SSO portálu:** duální session ji nevyřeší. Nová aplikace stále musí vědět, kdo je přihlášený
  ([ANALYZA](../docs/ANALYZA.md) kap. 4, riziko č. 1).

## 2. Liferay (legacy): tři způsoby přístupu

### 2.1 Přímo přes `PortletSession` se scope (~38× `PORTLET_SCOPE`, ~18× `APPLICATION_SCOPE`)

```java
@Controller
@RequestMapping("VIEW")
public class SearchController {

    @RenderMapping
    public String view(RenderRequest request, Model model) {
        PortletSession ps = request.getPortletSession();

        // PORTLET_SCOPE: vidí to jen tento portlet
        // (Liferay to v HttpSession uloží pod klíčem "javax.portlet.p.<portletId>?LAST_FILTER")
        SearchFilter filter = (SearchFilter) ps.getAttribute("LAST_FILTER", PortletSession.PORTLET_SCOPE);

        // APPLICATION_SCOPE: sdílené všemi portlety v tomtéž WAR
        String tab = (String) ps.getAttribute("ACTIVE_TAB", PortletSession.APPLICATION_SCOPE);
        ...
    }

    @ActionMapping(params = "action=search")
    public void search(ActionRequest request, ActionResponse response, @ModelAttribute SearchFilter filter) {
        request.getPortletSession().setAttribute("LAST_FILTER", filter, PortletSession.PORTLET_SCOPE);
        response.setRenderParameter("render", "result");   // action → render v tomtéž portletu
    }
}
```

### 2.2 Přes fasádu session manageru (hlavní způsob)

```java
public class DeactivationController extends BaseCrmController {

    @RenderMapping(params = "page=deactivation")
    public String form(Model model) {
        CustomerCustom customer = getSessionManager().getCustomer();   // ve skutečnosti ze SSO portálu
        if (customer == null) {
            getSessionManager().pushError("error.noCustomer");
            return "error";
        }
        ...
    }
}
```

Controller neví, odkud data přicházejí. Fasáda uvnitř dělá dvě různé věci:

```java
// identita a zákazník: externí session SSO portálu
public CustomerCustom getCustomer() {
    PortalSession ps = portalSessionManager.getPortalSession(requestAware.getRequest());
    return ps == null ? null : map(ps.getSessionCustomer());
}

// ostatní (objednávka, modely): PortletSession přes pomocníka
public void setOrder(Order order) {
    requestAware.setSessionAttribute(KEY_ORDER, order, PortletSession.APPLICATION_SCOPE);
}
```

Nejčastější volání fasády (orientačně z grepu): `pushError`, `getOrder`, `getCustomerRequest`,
`getCustomer`, `pushInfo`, `setOrder`, `flushAll`, `flushModel`.

### 2.3 `@SessionAttributes` (~101×): model, který přežije action → render

```java
@Controller
@RequestMapping("VIEW")
@SessionAttributes("createForm")        // Spring Portlet MVC ho drží v PortletSession (portlet scope)
public class CreateController {
    @ModelAttribute("createForm") CreateForm init() { return new CreateForm(); }
    ...
    @ActionMapping(params = "action=save")
    public void save(@ModelAttribute("createForm") CreateForm form, SessionStatus status, ...) {
        ...
        status.setComplete();            // bez tohoto zůstane formulář v session navždy
    }
}
```

## 3. Spring Boot (demo)

### 3.1 Náhrada `PortletSession` = obyčejná `HttpSession`
Portletové scope neexistují, je jedna session. Namespace je třeba nahradit **prefixy klíčů**,
jinak se klíče bývalých portletů střetnou.

```java
@GetMapping("/blacklist")
public String view(HttpSession session, Model model) {
    // bývalé PORTLET_SCOPE: namespace dáváme do klíče sami
    SearchFilter filter = (SearchFilter) session.getAttribute("BLACKLIST_LAST_FILTER");
    ...
}

@PostMapping("/blacklist/search")
public String search(@ModelAttribute SearchFilter filter, HttpSession session) {
    session.setAttribute("BLACKLIST_LAST_FILTER", filter);
    return "redirect:/blacklist/result";     // action → render = POST → redirect → GET (PRG)
}
```

### 3.2 Fasáda `CrmSession`: stejný kontrakt, jiná implementace
Controller vypadá téměř stejně jako v legacy, mění se jen to, co leží pod fasádou.

```java
@GetMapping("/dashboard/deactivation")
public String form(CrmSession crmSession, Model model) {      // injektuje CrmSessionArgumentResolver
    CustomerContext customer = crmSession.getCustomer()
            .orElseThrow(() -> new NotFoundException("customer"));
    ...
}

@PostMapping("/dashboard/deactivation")
public String submit(@Valid DeactivationForm form, BindingResult result, CrmSession crmSession) {
    ...
    crmSession.pushInfo("deactivation.done");     // hláška přežije redirect, layout ji zobrazí jednou
    return "redirect:/dashboard/deactivation/done";
}
```

Implementace (zkrácená):

```java
public final class HttpSessionCrmSession implements CrmSession {
    static final String PREFIX = "APP_";          // náhrada za oddělení portlet/application scope

    public static CrmSession of(HttpServletRequest request) { return new HttpSessionCrmSession(request); }

    public Optional<CustomerContext> getCustomer() {
        return Optional.ofNullable((CustomerContext) read(PREFIX + "CUSTOMER"));
    }
    public void setCustomer(CustomerContext customer) {
        request.getSession(true).setAttribute(PREFIX + "CUSTOMER", customer);
    }
    private void push(UiMessage message) {
        List<UiMessage> messages = new ArrayList<>(current());
        messages.add(message);
        // vždy znovu setAttribute: Spring Session/Redis změnu uvnitř seznamu nezaznamená
        request.getSession(true).setAttribute(PREFIX + "MESSAGES", messages);
    }
    ...
}
```

V produkci by pod touto fasádou byl Spring Session + Redis a feature flag „zdroj čtení“ (1.2).
Controllery se přitom nemění.

### 3.3 `@SessionAttributes` funguje stejně, jen nad `HttpSession`

```java
@Controller
@SessionAttributes("createForm")
public class BlacklistCreateController {
    @PostMapping("/blacklist/create")
    public String save(@ModelAttribute("createForm") CreateForm form, SessionStatus status) {
        ...
        status.setComplete();
        return "redirect:/blacklist";
    }
}
```

## 4. Jak se najde správná session

Automaticky: session manager nic nevybírá, vždy se ptá **aktuálního requestu** a ten svou session zná.

### 4.1 Cookie → `HttpSession` (servlet kontejner)

```
1. request:  bez cookie → Tomcat vytvoří session, pošle  Set-Cookie: JSESSIONID=ABC123
2. request:  Cookie: JSESSIONID=ABC123 → Tomcat najde v paměti session "ABC123"
```

Kontejner drží mapu `id → HttpSession` a `request.getSession()` vrátí session podle cookie.
Stejné v Liferay i ve Spring Bootu, kód na to nesahá.

### 4.2 Request → vlákno (`ThreadLocal`)
Request zpracovává jedno vlákno. Spring na začátku uloží request do `RequestContextHolder` (`ThreadLocal`)
a na konci ho smaže (legacy: `DispatcherPortlet`, Boot: `DispatcherServlet`).

### 4.3 Legacy: cesta ze singletonu k session
`sessionManager` je v XML konfiguraci **singleton** (jedna instance pro všechny uživatele),
session v sobě držet nemůže, vždy si ji dohledá:

```
controller → getSessionManager().getOrder()
   → requestAwareManager.getPortletRequest()     ← aktuální request tohoto vlákna
   → portletRequest.getPortletSession()          ← Liferay: obal nad HttpSession (4.1)
   → getAttribute(KEY, APPLICATION_SCOPE)
```

- Odkud `requestAwareManager` bere request, není ověřeno (základní třída je v chybějícím JAR).
  Téměř jistě z `RequestContextHolder`: testy před voláním ručně nastavují
  `RequestContextHolder.setRequestAttributes(new PortletRequestAttributes(mockRequest))` a jedna utilita
  odtud request čte stejně.
- Identita a zákazník: `portalSessionManager.getPortalSession(request)` hledá session SSO portálu podle
  údajů z requestu. API je `provided` JAR, jak přesně páruje (cookie / SSO ID), není známo.

### 4.4 Spring Boot demo: bez `ThreadLocal`

```
request (Cookie: JSESSIONID=ABC123)
  → DispatcherServlet → controller potřebuje parametr CrmSession
  → CrmSessionArgumentResolver dostane od Springu aktuální HttpServletRequest
  → HttpSessionCrmSession.of(request)     ← nový lehký obal pro každý request
  → request.getSession()                  ← kontejner podle cookie (4.1)
```

Fasáda se vytváří pro každý request a request dostane přímo: je vidět, odkud session přišla,
a v testu stačí `MockHttpServletRequest` (nic se nenastavuje do `ThreadLocal`).

### 4.5 Se Spring Session + Redis
Přibude `SessionRepositoryFilter`, který obalí request:

```
Cookie: SESSION=xyz → filter → request.getSession() načte session "xyz" z Redisu
```

Kód nad tím se nemění, `request.getSession()` jen vrátí session z Redisu místo paměti kontejneru.

## 5. Shrnutí

| | Liferay | Spring Boot |
|---|---|---|
| Objekt session | `PortletSession` (obal nad `HttpSession` portálu) | `HttpSession` |
| Oddělení portletů | `PORTLET_SCOPE` (automatický namespace podle portletId) | ruční prefixy klíčů |
| Sdílení mezi portlety | `APPLICATION_SCOPE` | běžný klíč v `HttpSession` |
| Identita a zákazník | session SSO portálu (přes fasádu) | demo: fake login do `HttpSession`; produkce: nevyřešeno |
| Controllery | `getSessionManager().x()` | `crmSession.x()`, stejný tvar |
| Redis / cluster | – | doplní se pod fasádu (Spring Session), controllery se nemění |

## 6. Na co si dát pozor při migraci

```bash
# přímé přístupy se scope – tady se ztratí automatický namespace
grep -rn "getPortletSession()" J/portlet/controller/<oblasť>
grep -rn "PORTLET_SCOPE\|APPLICATION_SCOPE" J/portlet/controller/<oblasť>
# modely držené v session mezi fázemi
grep -rn "@SessionAttributes" J/portlet/controller/<oblasť>
# práce mimo vlákno requestu – tam RequestContextHolder (a tedy session manager) nefunguje
grep -rn "@Async\|ExecutorService\|CompletableFuture\|new Thread" J
```

- Přímé `getPortletSession()` s `PORTLET_SCOPE`: po přechodu na jednu `HttpSession` mohou dva bývalé
  portlety potichu sdílet stejný klíč → přidej prefix podle oblasti.
- Volání přes fasádu jsou bezpečná, stačí vyměnit implementaci.
- `@SessionAttributes` bez `status.setComplete()` = model visí v session až do jejího vypršení
  (u Redisu je to i paměť a síť).
- Session manager přes `ThreadLocal` funguje jen ve vlákně requestu. V `@Async`, vlastním executoru
  nebo po skončení requestu vrátí `RequestContextHolder` `null` (nebo request jiného uživatele,
  pokud se kontext nevyčistil). Údaje ze session je třeba načíst předem a předat jako parametr.

## 7. Implementováno v demu: duální session ve staré aplikaci

Stará aplikace (`legacy/`, Liferay 6.2) implementuje návrh z 1.2:
- fasáda `SessionManagerCustom` (`legacy/frontend-base-customization/.../session/`): identita a zákazník ze SSO
  mocku přes `sso-portal-api`, ostatní přes `SessionStore`;
- `DualSessionStore`: zápis do `PortletSession` (`APPLICATION_SCOPE`, prefix `APP_`) i do Redisu
  (`RedisSessionStore`, hash `crm:session:{SSO_ID}`, JSON, TTL 30 min), čtení podle flagu
  `crm:flags:session-read-source` (`ReadSourceFlag`, default `PORTLET`);
- sdílí se jen registr `SharedKeys` (hlášky, poslední kritéria, poslední záznam) – formuláře
  v `@SessionAttributes` ne;
- výpadek Redisu request neshodí (zápis se zaloguje, čtení spadne zpět na `PortletSession`).

Jak to vidět naživo: `legacy/README.md`, sekce „Sdílená session živě“.

## 8. Souběh v praxi (demo)

Stará aplikace (`legacy/`) a nová (`crm-web/`) běží za jedním nginx a sdílejí hash `crm:session:{SSO_ID}`:
- nová aplikace zapisuje sdílené klíče jen do Redisu, stará do PortletSession i Redisu;
- stará aplikace čte podle flagu – při `PORTLET` nevidí zápisy nové, při `REDIS` ano;
- identitu obě berou ze SSO (cookie `SSO_ID`), zákazníka mění jen SSO portál.
Proklikání: `crm-web/README.md`; automaticky: `docker/smoke-migration.sh`.

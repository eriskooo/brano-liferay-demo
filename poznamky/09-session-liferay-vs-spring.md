# Session: Liferay vs. Spring Boot (pred → po)

Ako sa v legacy pristupuje do session, čím to nahradiť a čo je „duálna session“ z podkladov.
Ukážky sú napísané nanovo podľa vzorov, ktoré sa v legacy kóde opakujú (kľúče a triedy sú anonymizované).
Strana „Spring“ zodpovedá implementačnému plánu dema (`CrmSession`, `HttpSessionCrmSession`).
Skratky viď [00-konvencie.md](00-konvencie.md), alias `J` (adresár controllerov) viď [06](06-playbook-migracie-portletu.md).

## 1. Dva významy „zdieľanej session“

### 1.1 Dnes: session zdieľaná so SSO portálom (legacy)
- Prihlásený operátor a časť kontextu (napr. vybraný zákazník) nežijú v Liferay, ale v **SSO portáli**.
  Fasáda session managera (`FBC/service/SessionManagerCustom.java`) ich odtiaľ číta cez service locator.
- Controllery pristupujú k session prakticky len cez fasádu (`getSessionManager()` ~414× v ~114 súboroch).
  Je to **jediný bod**, na ktorom stojí návrh v 1.2.

### 1.2 Pri migrácii: „duálna session“ (slide 12 prezentácie)
Podklady: Spring Session + Redis, jediný zápisový bod = fasáda, feature flag pre zdroj čítania.
Zvyšok tejto sekcie je **odvodený**, prezentácia ho ďalej nerozpracúva.

Aplikácia sa nemigruje naraz, takže istý čas pobežia staré a nové stránky vedľa seba:

```
prehliadač ──► reverse proxy (routing podľa URL)
                 ├── stará Liferay app  (nezmigrované stránky)
                 └── nová Spring app    (zmigrované stránky)
```

Operátor prechádza medzi starými a novými stránkami, obe aplikácie musia vidieť ten istý stav
(operátor, aktuálny zákazník, rozpracovaná objednávka…; prezentácia uvádza 30+ objektov, neoverené).

- **Zápis:** fasáda zapisuje každú zmenu do oboch úložísk – stará `PortletSession`/`HttpSession` aj Redis.
  Controllery o tom nevedia, kontrakt fasády sa nemení.
- **Čítanie:** feature flag určuje zdroj. Najprv staré úložisko (kým sa Redis overí), potom Redis;
  pri problémoch sa dá prepnúť späť bez nasadenia.
- **Nová aplikácia** číta z Redisu cez Spring Session.
- **Na konci** stará aplikácia zanikne, ostane len Redis (dáva zmysel aj pre cluster).

### 1.3 Pasce duálnej session (odvodené, nie z podkladov)
- **Serializácia:** stará app Java 8 + `javax`, nová Java 21 + Jakarta. Ten istý objekt v Redise musia
  prečítať obe → skôr JSON / jednoduché DTO než Java serializácia doménových tried.
- **Cookie:** obe aplikácie musia nájsť tú istú session (rovnaká doména a path, spoločné ID session).
- **Timeout a odhlásenie:** expirácia zosúladená v oboch úložiskách.
- **Mutácia bez `setAttribute`:** Spring Session/Redis zapíše len atribút, ktorý sa znova nastavil.
  Úprava zoznamu „na mieste“ (`list.add(...)`) sa nezreplikuje.
- **Identita zo SSO portálu:** duálna session ju nevyrieši. Nová app stále musí vedieť, kto je prihlásený
  ([ANALYZA](../docs/ANALYZA.md) kap. 4, riziko č. 1).

## 2. Liferay (legacy): tri spôsoby prístupu

### 2.1 Priamo cez `PortletSession` so scope (~38× `PORTLET_SCOPE`, ~18× `APPLICATION_SCOPE`)

```java
@Controller
@RequestMapping("VIEW")
public class SearchController {

    @RenderMapping
    public String view(RenderRequest request, Model model) {
        PortletSession ps = request.getPortletSession();

        // PORTLET_SCOPE: vidí to len tento portlet
        // (Liferay to v HttpSession uloží pod kľúčom "javax.portlet.p.<portletId>?LAST_FILTER")
        SearchFilter filter = (SearchFilter) ps.getAttribute("LAST_FILTER", PortletSession.PORTLET_SCOPE);

        // APPLICATION_SCOPE: zdieľané všetkými portletmi v tom istom WAR
        String tab = (String) ps.getAttribute("ACTIVE_TAB", PortletSession.APPLICATION_SCOPE);
        ...
    }

    @ActionMapping(params = "action=search")
    public void search(ActionRequest request, ActionResponse response, @ModelAttribute SearchFilter filter) {
        request.getPortletSession().setAttribute("LAST_FILTER", filter, PortletSession.PORTLET_SCOPE);
        response.setRenderParameter("render", "result");   // action → render v tom istom portlete
    }
}
```

### 2.2 Cez fasádu session managera (hlavný spôsob)

```java
public class DeactivationController extends BaseCrmController {

    @RenderMapping(params = "page=deactivation")
    public String form(Model model) {
        CustomerCustom customer = getSessionManager().getCustomer();   // v skutočnosti zo SSO portálu
        if (customer == null) {
            getSessionManager().pushError("error.noCustomer");
            return "error";
        }
        ...
    }
}
```

Controller nevie, odkiaľ dáta prichádzajú. Fasáda vnútri robí dve rôzne veci:

```java
// identita a zákazník: externá session SSO portálu
public CustomerCustom getCustomer() {
    PortalSession ps = portalSessionManager.getPortalSession(requestAware.getRequest());
    return ps == null ? null : map(ps.getSessionCustomer());
}

// ostatné (objednávka, modely): PortletSession cez pomocníka
public void setOrder(Order order) {
    requestAware.setSessionAttribute(KEY_ORDER, order, PortletSession.APPLICATION_SCOPE);
}
```

Najčastejšie volania fasády (orientačne z grepu): `pushError`, `getOrder`, `getCustomerRequest`,
`getCustomer`, `pushInfo`, `setOrder`, `flushAll`, `flushModel`.

### 2.3 `@SessionAttributes` (~101×): model, ktorý prežije action → render

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
        status.setComplete();            // bez tohto zostane formulár v session navždy
    }
}
```

## 3. Spring Boot (demo)

### 3.1 Náhrada `PortletSession` = obyčajná `HttpSession`
Portletové scope neexistujú, je jedna session. Namespace treba nahradiť **prefixmi kľúčov**,
inak sa kľúče bývalých portletov pobijú.

```java
@GetMapping("/blacklist")
public String view(HttpSession session, Model model) {
    // bývalé PORTLET_SCOPE: namespace dávame do kľúča sami
    SearchFilter filter = (SearchFilter) session.getAttribute("BLACKLIST_LAST_FILTER");
    ...
}

@PostMapping("/blacklist/search")
public String search(@ModelAttribute SearchFilter filter, HttpSession session) {
    session.setAttribute("BLACKLIST_LAST_FILTER", filter);
    return "redirect:/blacklist/result";     // action → render = POST → redirect → GET (PRG)
}
```

### 3.2 Fasáda `CrmSession`: rovnaký kontrakt, iná implementácia
Controller vyzerá takmer rovnako ako v legacy, mení sa len to, čo leží pod fasádou.

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
    crmSession.pushInfo("deactivation.done");     // hláška prežije redirect, layout ju zobrazí raz
    return "redirect:/dashboard/deactivation/done";
}
```

Implementácia (skrátená):

```java
public final class HttpSessionCrmSession implements CrmSession {
    static final String PREFIX = "APP_";          // náhrada za oddelenie portlet/application scope

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
        // vždy znova setAttribute: Spring Session/Redis zmenu vnútri zoznamu nezaznamená
        request.getSession(true).setAttribute(PREFIX + "MESSAGES", messages);
    }
    ...
}
```

V produkcii by pod touto fasádou bol Spring Session + Redis a feature flag „zdroj čítania“ (1.2).
Controllery sa pri tom nemenia.

### 3.3 `@SessionAttributes` funguje rovnako, len nad `HttpSession`

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

## 4. Zhrnutie

| | Liferay | Spring Boot |
|---|---|---|
| Objekt session | `PortletSession` (obal nad `HttpSession` portálu) | `HttpSession` |
| Oddelenie portletov | `PORTLET_SCOPE` (automatický namespace podľa portletId) | ručné prefixy kľúčov |
| Zdieľanie medzi portletmi | `APPLICATION_SCOPE` | bežný kľúč v `HttpSession` |
| Identita a zákazník | session SSO portálu (cez fasádu) | demo: fake login do `HttpSession`; produkcia: nevyriešené |
| Controllery | `getSessionManager().x()` | `crmSession.x()`, rovnaký tvar |
| Redis / cluster | – | doplní sa pod fasádu (Spring Session), controllery sa nemenia |

## 5. Na čo si dať pozor pri migrácii

```bash
# priame prístupy so scope – tu sa stratí automatický namespace
grep -rn "getPortletSession()" J/portlet/controller/<oblasť>
grep -rn "PORTLET_SCOPE\|APPLICATION_SCOPE" J/portlet/controller/<oblasť>
# modely držané v session medzi fázami
grep -rn "@SessionAttributes" J/portlet/controller/<oblasť>
```

- Priame `getPortletSession()` s `PORTLET_SCOPE`: po prechode na jednu `HttpSession` môžu dva bývalé
  portlety potichu zdieľať ten istý kľúč → pridaj prefix podľa oblasti.
- Volania cez fasádu sú bezpečné, stačí vymeniť implementáciu.
- `@SessionAttributes` bez `status.setComplete()` = model visí v session do jej vypršania
  (pri Redise to je aj pamäť a sieť).

# Cesta requestu: JSP → server (Liferay portlet vs. Spring Boot)

Co se doopravdy pošle na server, když uživatel odešle formulář nebo klikne na odkaz, a jak server najde
Java metodu, která request zpracuje. Před = `legacy/` (Liferay 6.2, Spring Portlet MVC), po = `crm-web/`
(Spring Boot, Spring MVC). Příklad je změna stavu v detailu blacklistu. Zkratky viz [00-konvencie.md](00-konvencie.md).

## 1. JSP nic neposílá

JSP běží **na serveru** a jeho výsledkem je HTML. Do prohlížeče už žádné JSP nepřijde, jen hotové HTML.
Request na server posílá **prohlížeč**, a to ve třech situacích:

| Spouštěč v HTML | Co prohlížeč pošle |
|---|---|
| `<a href="URL">` | `GET URL` |
| `<form method="post" action="URL">` + submit | `POST URL`, tělo = pole formuláře (`application/x-www-form-urlencoded`) |
| JS (`XMLHttpRequest`/`fetch`) s URL z `data-*` atributu | `GET`/`POST URL`, odpověď si JS vloží do stránky |

JSP při vykreslení rozhoduje jen o tom, **jaká URL se zapíše** do `href`, `action` nebo `data-url`. URL vyrábějí
vlastní tagy `portal:renderURL`, `portal:actionURL` a `portal:resourceURL`. To je celé tajemství migrace:
**JSP zůstává stejné, mění se jen implementace tagu, a tím URL.**

```jsp
<portal:actionURL var="changeUrl">
    <portal:param name="viewPage" value="detail"/>
    <portal:param name="action" value="changeAction"/>
</portal:actionURL>
<form id="changeForm" method="post" action="${changeUrl}">
    <input type="hidden" name="detailId" value="42"/>
    <select name="state">…</select>
    <input type="date" name="validTo"/>
</form>
```

## 2. Co odejde po submitu

Formulář je v obou aplikacích stejný, liší se jen hodnota `action`.

**Před (Liferay)** – URL míří na **stránku portálu** a portlet je v ní zakódovaný parametry (zjednodušeně, `<P>` =
ID portletu, tady `blacklist_WAR_customercareportlet`):

```http
POST /web/crm/blacklist?p_auth=Xy12Ab&p_p_id=<P>&p_p_lifecycle=1&p_p_state=normal&p_p_mode=view
     &_<P>_viewPage=detail&_<P>_action=changeAction HTTP/1.1
Cookie: JSESSIONID=…; SSO_ID=…
Content-Type: application/x-www-form-urlencoded

detailId=42&state=INACTIVE&validTo=2026-12-31
```

- `p_p_id` určuje, kterému portletu na stránce request patří.
- `p_p_lifecycle` určuje fázi: `0` = render, `1` = action, `2` = resource.
- `p_auth` je CSRF token Liferay. Tag ho přidá do action URL sám, a bez něj Liferay akci odmítne.
- Parametry z `portal:param` dostanou namespace `_<P>_`. Pole formuláře v demu namespace nemají, protože
  `liferay-portlet.xml` má `requires-namespaced-parameters=false` stejně jako legacy.

**Po (crm-web)** – URL je obyčejná cesta. Tag ji skládá podle konvence `{ctx}{base}/{action}`
(`crm-web/.../web/tag/PortletUrlTag.java:65`, `ActionUrlTag.java`) a `viewPage` zahodí, protože controller určí cesta:

```http
POST /crm/blacklist/changeAction HTTP/1.1
Cookie: CRM_SESSION=…; SSO_ID=…
Content-Type: application/x-www-form-urlencoded

detailId=42&state=INACTIVE&validTo=2026-12-31
```

Tělo formuláře je **stejné**. Mění se jen řádek s URL.

## 3. Jak server najde Java metodu

### Před: Liferay → portlet → Spring Portlet MVC

```
prohlížeč ── POST /web/crm/blacklist?p_p_id=<P>&p_p_lifecycle=1&… ──►
  Tomcat → Liferay (ROOT webapp) – /web/crm/blacklist = stránka „blacklist“ na webu „crm“
    ├─ ověří p_auth, oprávnění na stránku
    ├─ p_p_id → portlet „blacklist“ z WAR customercare-portlet
    ├─ p_p_lifecycle=1 → ACTION fáze, zavolá processAction() v našem WAR
    │    DispatcherPortletCustom (portlet.xml, potomek Spring DispatcherPortlet)
    │      ├─ kontext blacklist-portlet.xml, mapování podle anotací:
    │      │    třída  @RequestMapping(value="VIEW", params="viewPage=detail") → BlacklistDetailController
    │      │    metoda @ActionMapping(params="action=changeAction")          → change(...)
    │      ├─ @RequestParam detailId/state/validTo ← parametry requestu (tělo formuláře)
    │      └─ change() nemá výstup; nastaví render parametry viewPage=detail, detailId=42
    └─ v TÉMŽE requestu RENDER fáze všech portletů na stránce
         blacklist: @RenderMapping detail(...) → "blacklist/detail"
           → InternalResourceViewResolver → ViewRendererServlet → /WEB-INF/jsp/blacklist/detail.jsp
         + theme, hlavička, ostatní portlety → jedna HTML stránka
◄── 200 OK, celá stránka portálu
```

Klíčové body:
- **Směruje portál, ne URL cesta.** Cesta vybere stránku portálu, portlet a fázi vyberou parametry
  `p_p_*` a metodu vyberou parametry uvnitř portletu (`viewPage`, `action`). Proto má legacy u controllerů
  `params = "..."` místo cest.
- **Action a render proběhnou v jednom requestu.** Po submitu přijde rovnou `200` s HTML. F5 proto formulář
  odešle znovu (Liferay 6.2 bez `action-url-redirect`).
- Stav z action do renderu se předává render parametry (`response.setRenderParameter`), viz
  `legacy/.../blacklist/BlacklistDetailController.java` `backToDetail`.

### Po: Spring Boot → DispatcherServlet

```
prohlížeč ── POST /crm/blacklist/changeAction ──►
  nginx (/crm → crm-web) → embedded Tomcat, kontext /crm
    DispatcherServlet
      ├─ RequestMappingHandlerMapping: /blacklist (třída) + /changeAction (@PostMapping)
      │    → BlacklistDetailController.change(...)
      ├─ interceptory (WebConfig): LegacyPortletInterceptor = identita ze SSO_ID, ACL @RequiresPrivilege,
      │    base path pro URL tagy; LayoutModelInterceptor = model hlavičky
      ├─ vazba parametrů: @RequestParam detailId, @Valid @ModelAttribute BlacklistChangeForm (state, validTo)
      │    + BindingResult; CrmSession přes CrmSessionArgumentResolver
      └─ change() vrátí "redirect:/blacklist/detail?detailId=42"
◄── 302 Location: /crm/blacklist/detail?detailId=42

prohlížeč ── GET /crm/blacklist/detail?detailId=42 ──►
    DispatcherServlet → @GetMapping("/detail") detail(...) → "blacklist/detail"
      → view resolver (spring.mvc.view.prefix/suffix) → /WEB-INF/jsp/blacklist/detail.jsp
◄── 200 OK, HTML stránka
```

Klíčové body:
- **Směruje URL cesta.** `@RequestMapping` na třídě + `@GetMapping`/`@PostMapping` na metodě. Parametry
  `p_p_*`, `viewPage` ani `action` už nejsou potřeba.
- **Action a render jsou dva requesty** (POST → 302 → GET, vzor PRG). F5 obnoví jen GET. Při chybě validace se
  stránka vykreslí rovnou z POSTu bez redirectu (`crm-web/.../blacklist/BlacklistDetailController.java:51`).
- Co v legacy dělal portál (identita, kontrola přístupu, kompozice stránky s hlavičkou), dělají teď interceptory
  a layout JSP. Viz [09](09-session-liferay-vs-spring.md) a `docs/MIGRACIA.md` §4–7.

## 4. AJAX (resource fáze)

JS nikdy neskládá URL sám, bere ji z atributu, který vyrobil `portal:resourceURL`:

```jsp
<portal:resourceURL id="removeIdentAction" var="removeUrl">
    <portal:param name="detailId" value="${detailId}"/>
    <portal:param name="identId" value="${i.id}"/>
</portal:resourceURL>
<a href="#" data-url="${removeUrl}" …>
```

| | Před | Po |
|---|---|---|
| URL | `/web/crm/blacklist?p_p_id=<P>&p_p_lifecycle=2&p_p_resource_id=removeIdentAction&…` | `/crm/blacklist/resource/removeIdentAction?detailId=42&identId=7` |
| kdo zpracuje | Liferay → `serveResource()` → `@ResourceMapping("removeIdentAction")` | `DispatcherServlet` → `@PostMapping("/resource/removeIdentAction")` |
| odpověď | jen HTML fragment portletu (bez theme a ostatních portletů) | stejný HTML fragment |

Resource fáze je jediná, kde i Liferay vrací jen výstup portletu, a ne celou stránku. Proto se mapuje 1:1 na samostatný endpoint.

## 5. Je to tedy REST?

Není. Po migraci jsou to běžné Spring MVC controllery (`@Controller`, ne `@RestController`), které vracejí
**název view** (HTML) nebo **redirect**, nikdy data. Mizí jen mezivrstva portálu: místo „stránka portálu
+ `p_p_id` + fáze + `action`“ má každá obrazovka a akce vlastní URL. Názvy jako `changeAction` jsou slovesa
převzatá z legacy (styl RPC) a zůstávají kvůli tomu, aby se JSP a JS neměnily.

| Fáze | Po migraci | Vrací |
|---|---|---|
| render | `GET /crm/blacklist/detail` | HTML stránku |
| action | `POST /crm/blacklist/changeAction` | 302 redirect (při chybě validace HTML) |
| resource | `POST/GET /crm/blacklist/resource/…` | HTML fragment (příp. JSON) |

Skutečné REST API (JSON, `PATCH /blacklist/{id}`, UI v JS) by znamenalo přepsat frontend. To migrace záměrně
nedělá. Po jejím dokončení to jde dělat obrazovku po obrazovce, protože controllery a služby už budou čisté Spring komponenty.

## 6. Kde to ověřit na reálném projektu

- **Network v DevTools** (F12): odešli formulář a podívej se na URL, metodu, tělo a status (Liferay: `200` +
  `p_p_lifecycle=1`; Boot: `302` → `GET`).
- **Vyrobení URL:** tag z `.tld` → třída handleru (`FBC/.../tag/*PortletUrl*`). Tady je skrytá závislost
  na `LiferayPortletURL`, viz [07](07-pasce.md) bod 3.
- **Výběr metody:** `@RequestMapping(params=…)` na třídě + `@ActionMapping`/`@RenderMapping`/`@ResourceMapping`
  na metodě. Hledej `grep -rn "params *= *\"action=" J/portlet/controller/<oblasť>`.
- **Mapování fází na HTTP** a URL tagy: `docs/MIGRACIA.md` §2–3, [05](05-mapa-konceptov.md).

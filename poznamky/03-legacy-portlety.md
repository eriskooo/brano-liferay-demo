# Legacy modul `customercare-portlets` – čo a ako používa z Liferay

Cesty relatívne k `$FE/customercare-portlets/src/main` (= `CP`). Overené vlastnými príkazmi
(grep/find) sú označené ✔, zvyšok je z prieskumu agenta a nie je ručne overený.

## Rozsah
- ~1418 Java súborov (~227 k LOC), **1241 JSP** ✔, 219 `.tag`, 41 testov.
- **13 portletov** v `webapp/WEB-INF/portlet.xml` ✔ (JSR-286 / Portlet 2.0).
  Oblasti: kontext zákazníka + hlavička + fake login, objednávky (order), dashboard, billing,
  požiadavky zákazníka, scoring, blacklist ne-zákazníkov, vyhľadávanie dokumentov, kontaktné osoby,
  2 vyradené (decommissioned) portlety.
- Typ: **Spring Portlet MVC** (anotácie) ✔ – žiadny `MVCPortlet`, `GenericPortlet` ani JSF.
  `@Controller` 246, `@RenderMapping` 174, `@ActionMapping` 347, `@ResourceMapping` 598 ✔,
  `@SessionAttributes` 101, `@ModelAttribute` 745.
- Portlet triedy = vlastné podtriedy Spring `DispatcherPortlet`; každý portlet má vlastný Spring
  kontext `WEB-INF/<portlet>-portlet.xml` s `InternalResourceViewResolver` → `/WEB-INF/jsp/<portlet>/`.

## Kľúčová lekcia: portletové fázy
Portlet má **render / action / resource** fázu (`@RenderMapping`, `@ActionMapping`, `@ResourceMapping`).
Action spracuje POST a *nemá* výstup; render potom vykreslí HTML; resource = AJAX/JSON/súbory.
V servlet svete je to jeden request → v migrácii: action+render = POST-Redirect-GET,
resource = samostatný GET/POST endpoint (REST).

## Liferay API (koľko toho naozaj je)
- **Service Builder: nepoužíva sa** (žiadny `service.xml`) ✔. Perzistencia je vo vlastnom backende.
- 119 Java súborov importuje `com.liferay.*` ✔. Najmä `PortalUtil` (175 volaní, hlavne
  `getHttpServletRequest` – „dostaň sa k servlet requestu“), `ThemeDisplay` (6 súborov),
  `LanguageUtil` (4 súbory, len jazyk/krajina).
- **Permissions: Liferay sa nepoužíva na business autorizáciu** – vlastné ACL
  (ACL služba + JSP tagy typu `aclFilter` / `aclPageFilterTag privilegeType=...`).
  Liferay `PermissionChecker` len v štartovacom hooku.
- `*LocalServiceUtil` (60×) len v **startup hooku**, ktorý pri štarte portálu vytvára site, stránky,
  umiestňuje portlety, Expando stĺpce a Journal články (CMS obsah prázdneho košíka).
- `liferay-hook.xml` ✔: len `portal.properties` (startup event) + `custom-jsp-dir` (prepis
  `top_meta.jspf` a chybových stránok 403/404/500).
- **Friendly URL**: 5 route súborov v `resources/friendly/` ✔, mapper pre 5 portletov
  (2 vlastné mappery), napr. dashboard `/{cuRefNo}/{caRefNo}/{tsRefNo}/{assetRefNo}/{page}/{action}`.
- Liferay taglibs v JSP takmer nie sú (`liferay-ui`/`aui` 0 použití, `liferay-theme` 29 súborov
  hlavne kvôli ceste k obrázkom theme). Dominujú **vlastné taglibs** `portal:` (~7200 použití)
  a `common:` (~9500 použití). URL tagy vo vnútri volajú `MimeResponse.createActionURL()` a pretypujú
  na `LiferayPortletURL` → **tu je skutočná závislosť na Liferay, skrytá v tagoch**.
- Theme: separátny WAR, portlet WAR ho vyžaduje (`required-deployment-contexts`).
- JBoss: `jboss-deployment-structure.xml` závisí na module Liferay portálu a modulov SSO portálu.

## Session
- Fasáda session manager (interface ~780 LOC, impl ~1750 LOC) v `frontend-base-customization`,
  dedí z triedy z externej knižnice (zdroj nie je k dispozícii).
- Zdieľaná session so **SSO portálom** cez service locator (API je `provided` závislosť).
- `getSessionManager()` volaný 414× v 114 súboroch. `PortletSession` 51× (hlavne `PORTLET_SCOPE`).

## Backend
- **Backend beží v tom istom JVM a tom istom Spring kontexte** – `applicationContext.xml` importuje
  backend kontexty (DAO, služby, transakcie, WS/REST integračné klienty). Žiadne remoting/EJB.
  ⇒ „backend“ = Java interfacy v jare, ktorý v archíve chýba/je mimo podkladov.

## Dispatch a interceptory
- Dashboard a order používajú **vlastné HandlerMappingy**: controller sa vyberá podľa parametra
  `page` (registr stránok) resp. podľa scenára/kroku (page-flow pre objednávky).
- 18 interceptorov (kontext účtu, deeplinky, kopírovanie render parametrov po action fáze,
  analytika, platby, logovanie…).
- Spoločný base controller ~1900 LOC (dedí z externej knižnice).

## Kandidáti na demo
| Feature | Kde | Veľkosť | Liferay závislosť | Samostatnosť |
|---|---|---|---|---|
| Blacklist ne-zákazníkov | vlastný portlet, 4 controllery, 4 JSP | ~4 200 LOC | nepriama (URL tagy, base controller, dispatcher) | **najvyššia** – štandardný annotation mapping, search→detail→create, validátory, ACL, 1 backend služba |
| Plánované odstávky | stránka v dashboard portlete | ~570 LOC | žiadna priama; závisí od dashboard dispatchu | stredná – volá integračných klientov |
| Zrušenie služby (deaktivácia tarifu) | stránka v dashboard portlete + manager | ~3 800 LOC | `PortalUtil`, session manager, dashboard dispatch | nízka – 7+ backend služieb, veľký manager |

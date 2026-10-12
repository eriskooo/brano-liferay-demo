# Pasce pri migrácii (zistené z kódu a logov)

1. **Stratený form objekt v modeli.** Portletové controllery majú `@ModelAttribute` metódy na úrovni
   triedy, ktoré sa volajú pred každou fázou. Keď sa viac portletových controllerov zlúči do jedného
   web controllera, tieto metódy sa nepresunú a `<form:form modelAttribute="x">` spadne s
   `Neither BindingResult nor plain target object for bean name 'x'` (presne toto je v PoC logu).
   → Pri migrácii každého controllera vypíš všetky `@ModelAttribute` a `@SessionAttributes` a over ich.
2. **Action → render.** V portletoch `@ActionMapping` nič nevracia, render ide hneď po ňom
   s render parametrami (`response.setRenderParameter`). V servlete to musí byť explicitné:
   POST → redirect → GET (PRG), parametre do URL alebo do flash atribútov.
3. **Skrytá Liferay závislosť v tagoch.** JSP nemusia obsahovať nič z Liferay, ale vlastný tag
   `portal:actionURL` vo vnútri pretypuje na `LiferayPortletURL`. Hľadaj závislosti aj v tag handleroch
   (`.tld` → trieda), nielen v JSP.
4. **Kompatibilné tagy, ktoré „nič nerobia“.** Ak sa tag nahradí no-op stubom, stránka sa vykreslí,
   ale funkcia zmizne potichu (napr. i18n vypisuje kľúče). Každý no-op tag musí byť v zozname dlhu.
5. **Dispatch podľa parametra.** Dashboard/order nevyberajú controller podľa URL, ale vlastným
   `HandlerMapping` podľa `page` / scenára. Treba to rozbiť na skutočné URL, inak sa stratí
   prehľadnosť a bookmarky.
6. **Session je zdieľaná so SSO portálom.** Identita a kontext zákazníka nie sú v Liferay, ale
   v externom portáli – bez neho aplikácia nevie, kto je prihlásený. To je architektonický problém,
   nie technický prepis.
7. **Backend in-process.** Frontend importuje backend Spring kontexty → migrácia frontendu
   ťahá celý backend classpath (Hibernate 4, Spring 4, javax). Upgrade na moderný Spring Boot
   (Jakarta) to **nedovolí bez upgradu backendu** alebo bez oddelenia (REST/klient).
8. **`javax` → `jakarta`.** Spring Boot 3+ = Jakarta EE. Legacy JSP tagy, validátory
   (`javax.validation`) aj servlet API treba prepísať na `jakarta.*`.
9. **Meranie pokroku.** „100 % JSP“ v PoC neznamená funkčnosť – merať scenáre end-to-end.
10. **Interné URL a tajomstvá v kóde.** Zdrojáky obsahujú interné hostnames, testovacie kľúče a root
    POM heslá k DB v profiloch. Pri akomkoľvek kopírovaní von z repa treba sanitizáciu.
11. **Java 8 update > 255 zhodí Liferay 6.2.** Stará ICU4J v portáli neprečíta `1.8.0_504`. Detail
    a riešenie: [10-liferay62-v-dockeri.md](10-liferay62-v-dockeri.md).
12. **Kolízia `JSESSIONID` za jednou adresou.** Stará a nová appka (aj SSO) za tým istým hostom zdieľajú cookies
    bez ohľadu na port/cestu `/`. Druhá appka musí mať vlastný názov session cookie alebo session nepoužívať.
13. **Render fáza nevie presmerovať ani nastaviť cookie.** Kontrola prihlásenia v render fáze portletu môže len
    vykresliť odkaz; redirect funguje v action fáze. Kľúč zdieľanej session preto nie je vlastná cookie, ale
    `SSO_ID`, ktorú obe appky už majú.
14. **Hosť a portlety.** Identitu má SSO, Liferay vidí hosťa: každý portlet potrebuje VIEW pre Guest (stránka
    + inštancia portletu, runtime portlet `add-default-resource`). Inak „You do not have the roles required“.
15. **Implicit model medzi action a render.** Spring Portlet MVC prenesie model (aj validačné chyby) z action
    do renderu cez session (render parameter `implicitModel`). V servlet MVC to nie je – chyby sa vykreslia
    priamo (bez redirectu) alebo cez flash atribúty.

16. **Liferay premenuje WAR `…-portlets` na kontext `…-portlet`.** ID portletov sú potom `<meno>_WAR_<kontext bez pomlčiek>`
    (`blacklist_WAR_customercareportlet`). Kto skladá ID ručne (hook, `$theme.runtime`, deeplinky), musí použiť
    skutočný kontext – v deme preto `finalName` = `customercare-portlet`.
17. **Dvojité escapovanie URL vo `<form:form>`.** Spring form tagy escapujú `action` aj `data-*` atribúty samy;
    URL z vlastného tagu, ktorý už escapuje, skončí ako `&amp;amp;` a action/resource požiadavka nefunguje.
    URL pre Spring form tagy treba brať neescapované (`escapeXml="false"`).
18. **Oprávnenia z hooku pri prvom deployi.** Hook beží skôr, než sa zaregistrujú portlety toho istého WAR-u, takže
    `setResourcePermissions` pre ich akcie zlyhá (`…#VIEW`). Hosť VIEW napriek tomu dostal z predvolených oprávnení
    portletu pri umiestnení na stránku – na explicitné oprávnenia sa pri prvom štarte nespoliehaj.

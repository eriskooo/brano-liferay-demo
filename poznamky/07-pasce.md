# Pasti při migraci (zjištěné z kódu a logů)

1. **Ztracený form objekt v modelu.** Portletové controllery mají `@ModelAttribute` metody na úrovni
   třídy, které se volají před každou fází. Když se více portletových controllerů sloučí do jednoho
   webového controlleru, tyto metody se nepřesunou a `<form:form modelAttribute="x">` spadne s
   `Neither BindingResult nor plain target object for bean name 'x'` (přesně tohle je v logu PoC).
   → Při migraci každého controlleru vypiš všechny `@ModelAttribute` a `@SessionAttributes` a ověř je.
2. **Action → render.** V portletech `@ActionMapping` nic nevrací, render jde hned po něm
   s render parametry (`response.setRenderParameter`). V servletu to musí být explicitní:
   POST → redirect → GET (PRG), parametry do URL nebo do flash atributů.
3. **Skrytá závislost na Liferay v tazích.** JSP nemusí obsahovat nic z Liferay, ale vlastní tag
   `portal:actionURL` uvnitř přetypuje na `LiferayPortletURL`. Hledej závislosti i v tag handlerech
   (`.tld` → třída), nejen v JSP.
4. **Kompatibilní tagy, které „nic nedělají“.** Pokud se tag nahradí no-op stubem, stránka se vykreslí,
   ale funkce potichu zmizí (např. i18n vypisuje klíče). Každý no-op tag musí být v seznamu dluhu.
5. **Dispatch podle parametru.** Dashboard/order nevybírají controller podle URL, ale vlastním
   `HandlerMapping` podle `page` / scénáře. Je třeba to rozbít na skutečné URL, jinak se ztratí
   přehlednost a záložky.
6. **Session je sdílená se SSO portálem.** Identita a kontext zákazníka nejsou v Liferay, ale
   v externím portálu – bez něj aplikace neví, kdo je přihlášený. To je architektonický problém,
   ne technický přepis.
7. **Backend in-process.** Frontend importuje backendové Spring kontexty → migrace frontendu
   táhne celý backendový classpath (Hibernate 4, Spring 4, javax). Upgrade na moderní Spring Boot
   (Jakarta) to **nedovolí bez upgradu backendu** nebo bez oddělení (REST/klient).
8. **`javax` → `jakarta`.** Spring Boot 3+ = Jakarta EE. Legacy JSP tagy, validátory
   (`javax.validation`) i servlet API je třeba přepsat na `jakarta.*`.
9. **Měření pokroku.** „100 % JSP“ v PoC neznamená funkčnost – měřit end-to-end scénáře.
10. **Interní URL a tajemství v kódu.** Zdrojáky obsahují interní hostnames, testovací klíče a v root
    POM hesla k DB v profilech. Při jakémkoli kopírování ven z repa je nutná sanitizace.
11. **Java 8 update > 255 shodí Liferay 6.2.** Stará ICU4J v portálu nepřečte `1.8.0_504`. Detail
    a řešení: [10-liferay62-v-dockeri.md](10-liferay62-v-dockeri.md).
12. **Kolize `JSESSIONID` za jednou adresou.** Stará a nová aplikace (i SSO) za stejným hostem sdílejí cookies
    bez ohledu na port/cestu `/`. Druhá aplikace musí mít vlastní název session cookie, nebo session nepoužívat.
13. **Render fáze neumí přesměrovat ani nastavit cookie.** Kontrola přihlášení v render fázi portletu může jen
    vykreslit odkaz; redirect funguje v action fázi. Klíčem sdílené session proto není vlastní cookie, ale
    `SSO_ID`, kterou obě aplikace už mají.
14. **Host a portlety.** Identitu má SSO, Liferay vidí hosta: každý portlet potřebuje VIEW pro Guest (stránka
    + instance portletu, runtime portlet `add-default-resource`). Jinak „You do not have the roles required“.
15. **Implicitní model mezi action a render.** Spring Portlet MVC přenese model (i validační chyby) z action
    do renderu přes session (render parametr `implicitModel`). V servlet MVC to není – chyby se vykreslí
    přímo (bez redirectu) nebo přes flash atributy.

16. **Liferay přejmenuje WAR `…-portlets` na kontext `…-portlet`.** ID portletů jsou pak `<meno>_WAR_<kontext bez pomlček>`
    (`blacklist_WAR_customercareportlet`). Kdo skládá ID ručně (hook, `$theme.runtime`, deeplinky), musí použít
    skutečný kontext – v demu proto `finalName` = `customercare-portlet`.
17. **Dvojité escapování URL ve `<form:form>`.** Spring form tagy escapují `action` i `data-*` atributy samy;
    URL z vlastního tagu, který už escapuje, skončí jako `&amp;amp;` a action/resource požadavek nefunguje.
    URL pro Spring form tagy je třeba brát neescapované (`escapeXml="false"`).
18. **Oprávnění z hooku při prvním deployi.** Hook běží dřív, než se zaregistrují portlety téhož WAR, takže
    `setResourcePermissions` pro jejich akce selže (`…#VIEW`). Host VIEW přesto dostal z výchozích oprávnění
    portletu při umístění na stránku – na explicitní oprávnění se při prvním startu nespoléhej.

19. **Flag zdroje čtení při souběhu.** Dokud stará aplikace čte `PORTLET`, nevidí nic, co zapíše nová aplikace (ta starou
    session nemá). Flag je třeba přepnout na `REDIS` nejpozději s nasazením první zmigrované stránky.
20. **JSON kontrakt mezi Java 8 a Java 21.** Sdílené hodnoty jsou jen jednoduchá DTO v JSON; nová aplikace musí ignorovat
    neznámá pole a snášet `null` (Jackson 2 a Jackson 3 se liší výchozím nastavením).
21. **Session cookie nové aplikace.** Za jedním hostem musí mít jiný název a cestu (`CRM_SESSION`, `/crm`) – jinak přepíše
    `JSESSIONID` portálu.
22. **Liferay session přežije změnu operátora v SSO.** Bez kontroly vlastníka (`SSO_ID`) vidí operátor B stav A.
23. **`HtmlUtils.htmlEscape(text)` bez kódování** mění slovenské znaky na entity (`&yacute;`). V tazích a výstupu
    používej `htmlEscape(text, "UTF-8")` – escapují se jen `& < > " '`.
24. **Spring Boot 4 je rozdělený na moduly.** Např. `RestClient.Builder` bez `spring-boot-starter-restclient` neexistuje,
    přestože se kód zkompiluje – chyba se ukáže až při startu kontextu.
25. **Částečné bindování `@SessionAttributes`.** Pole, které request neobsahuje, zůstane z předchozího formuláře
    v session (v legacy i po migraci). Prohlížeč posílá všechna pole, ruční testy (curl) musí také.
26. **nginx `return 302` za proxy s jiným portem** skládá absolutní URL s interním portem – `absolute_redirect off`.
27. **Transakce otevřená frontendem.** Managery ve frontendu (`@Transactional`, `TransactionTemplate`) obepínají několik
    backendových volání ve stejném JVM. Po oddělení backendu (jiný proces/kontejner) by každé volání mělo vlastní
    transakci → částečný zápis při chybě uprostřed. Takový manager nepřenášej do nového frontendu; přesuň ho k backendu
    jako jednu API operaci se stejnou hranicí a propagací (pozor na `…NewTx` = REQUIRES_NEW a `SUPPORTS`).
    Najdeš je: `grep -rln "@Transactional\|TransactionTemplate" CP/java`. Kontejnerizace sama nic neřeší –
    backend je knihovna uvnitř WAR.

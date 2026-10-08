# Zadanie podľa podkladov (prezentácie + Q&A dokument)

Zdroj: dve prezentácie v `input/` (`*-AI.pptx` a jej verzia s komentármi klienta – 14 slajdov,
4 komentáre) a Q&A dokument (obsah je v `BLOCKS` v generátore `input/*-odpovedi-komentare.py`).
Skratky viď [00-konvencie.md](00-konvencie.md).

## Čo podklady hovoria o cieli
- Cieľ = **odstrániť Liferay** (nepodporované/rizikové knižnice) pri **zachovaní funkcionality 1:1**
  a **zachovaní technickej filozofie 1:1**: žiadne SPA, žiadny presun logiky FE↔BE, stavovosť
  (session) zostáva (slide 2).
- Cieľový stack v podkladoch: **Spring MVC + JSP na plain Tomcat** (slide 5), nie Spring Boot,
  nie REST+SPA. JSP sa kopírujú/zdieľajú, portlet tagy sa reimplementujú bez Liferay (slide 13).
- Server services a backend integrácie „necháváme“ (slide 4).
- Theme → statické CSS/JS vo WAR. Liferay CMS sa nepoužíva; preklady idú z externého CMS
  a do JSP sa majú injektovať prekladové kľúče.
- Session: „duálna session“ Liferay → Spring Session (Redis), jediný zápisový bod = session manager
  (fasáda), feature flag pre zdroj čítania (slide 12). Prezentácia uvádza 375+ controllerov
  a 30+ objektov v session (neoverené čísla).
- Deeplinker/Backlinker (generovanie interných odkazov a „späť“) → vlastný URL builder;
  podľa prezentácie „treba ešte rozpracovať“.
- Klient sa pýta, či to pobeží na JBoss (komentár) – Q&A: technicky áno, neoverené.

## PoC ukážky v prezentácii
- Ukážka 1: zoznam tarifov/produktov → klik → formulár „Zrušenie služby“ (dátum podania,
  typ: prevod na iného zákazníka / zrušenie / prevod na predplatenú kartu, „ku dňu“).
- Ukážka 1 obsahuje aj obrazovku „Plánované odstávky služieb“ (filter podľa ID služby + tabuľka
  udalostí s detailom) – na mock dátach.
- Ukážka 2: pôvodná Liferay verzia vs. zmigrovaná PoC verzia toho istého formulára „Zrušenie služby“.
  Pozorovanie zo screenshotov: Liferay verzia je vnorená v layoute portálu (horné menu, zákazník
  v hlavičke – to dodáva theme + iné portlety), PoC verzia má vlastnú jednoduchú hlavičku.
  PoC verzia navyše zobrazuje pole „Ke dni“ (dátum účinnosti), Liferay screenshot nie – pravdepodobne
  sa zobrazuje podmienene podľa zvoleného typu (neoverené).
- Slide 13: tabuľka tagov, ktoré bolo treba prepísať (URL tagy → `PortletResponse.createRenderURL()`
  / `createResourceURL()`, i18n tag → `LanguageUtil` + `ThemeDisplay`, ostatné nepriamo alebo vôbec).
- Druhá prezentácia: Non-Customer Blacklist (vyhľadávanie + vytvorenie), Documents,
  Customer Request History – na mock dátach.

## AS-IS architektúra (slide 14, obrázok)
Prehliadač (HTML/JS, AJAX) → JBoss + Liferay: JSP view, controller, services (Java 8, Spring 4.3.6,
Hibernate), DAO/JMX + JPA → Oracle 19; navonok message broker, integračná vrstva (REST/SOAP),
SSO portál (session).

## Čo v podkladoch NIE JE (overené fulltextom prezentácií a Q&A)
- **JSF** – v projekte ani v podkladoch (0 `.xhtml`, 0 `faces-config.xml`, 0 `javax.faces`).
  Portlety sú Spring Portlet MVC + JSP.
- **Návrh security** – žiadny (Spring Security ani iné). Len: identita používateľa je zapuzdrená
  v session fasáde (nastavenie externého/interného používateľa a zákazníka); kontrakt fasády sa má
  zachovať a implementácia vymeniť (preč od `PortletSession`/Liferay). Autorizácia = existujúce
  vlastné ACL. ⇒ Pri migrácii security **nemeniť**, kým nie je architektonické rozhodnutie.

## Rozhodnutie pre demo (používateľ, 2026-10-08)
- Držať sa podkladov: **Spring MVC controllery + JSP** (server-side), AJAX/resource volania → JSON
  endpointy (to je „REST“ časť). Žiadne SPA, žiadny JSF.
- Rozsah: blacklist ne-zákazníkov + zjednodušené zrušenie služby. Java 21.
- Security: nemeniť – session fasáda + fake login + vlastná ACL ako v legacy.

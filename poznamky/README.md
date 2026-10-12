# Znalostná báza: migrácia legacy CRM z Liferay 6.2

Cieľ: aby sa človek, ktorý si tento adresár prenesie na reálny projekt, vedel rýchlo zorientovať
a migráciu previesť. Poznámky sú anonymizované (idú na GitHub). Skutočné názvy dosadíš podľa
lokálneho, git-ignorovaného súboru `LOCAL-aliasy.md`, ktorý sa prenáša ručne.

| Súbor | Obsah |
|---|---|
| [00-konvencie.md](00-konvencie.md) | skratky, aliasy, pravidlá anonymizácie |
| [01-zadanie-z-podkladov.md](01-zadanie-z-podkladov.md) | čo chce klient a dodávateľ (prezentácie, Q&A), cieľová filozofia |
| [02-logy.md](02-logy.md) | čo ukázali logy z behu PoC |
| [03-legacy-portlety.md](03-legacy-portlety.md) | inventár legacy modulu, Liferay API, session, backend, kandidáti na demo |
| [04-poc-modul-customercare-web.md](04-poc-modul-customercare-web.md) | čo a ako urobil predchádzajúci PoC, jeho diery |
| [05-mapa-konceptov.md](05-mapa-konceptov.md) | Liferay/portlet koncept → náhrada |
| [06-playbook-migracie-portletu.md](06-playbook-migracie-portletu.md) | kde čo hľadať + postup migrácie jedného portletu + DoD |
| [07-pasce.md](07-pasce.md) | konkrétne pasce zistené v kóde a logoch |
| [09-session-liferay-vs-spring.md](09-session-liferay-vs-spring.md) | session pred → po: PortletSession scope, fasáda, `@SessionAttributes`, ako sa nájde správna session, duálna session |
| [10-liferay62-v-dockeri.md](10-liferay62-v-dockeri.md) | ako rozbehnúť Liferay 6.2 lokálne (Java 8 ≤ 255, HSQL, deploy, oprávnenia hosťa, nginx) |

Odporúčané poradie na reálnom projekte: 00 → 01 → 03 → 05 → 06 → 07 (→ 04, ak sa nadväzuje na PoC).
Detailná analýza s odkazmi na súbory: [`../docs/ANALYZA.md`](../docs/ANALYZA.md).

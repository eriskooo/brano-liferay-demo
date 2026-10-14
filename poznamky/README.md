# Znalostní báze: migrace legacy CRM z Liferay 6.2

Cíl: aby se člověk, který si tento adresář přenese na reálný projekt, dokázal rychle zorientovat
a migraci provést. Poznámky jsou anonymizované (jdou na GitHub). Skutečné názvy doplníš podle
lokálního, git-ignorovaného souboru `LOCAL-aliasy.md`, který se přenáší ručně.

| Soubor | Obsah |
|---|---|
| [00-konvencie.md](00-konvencie.md) | zkratky, aliasy, pravidla anonymizace |
| [01-zadanie-z-podkladov.md](01-zadanie-z-podkladov.md) | co chce klient a dodavatel (prezentace, Q&A), cílová filozofie |
| [02-logy.md](02-logy.md) | co ukázaly logy z běhu PoC |
| [03-legacy-portlety.md](03-legacy-portlety.md) | inventář legacy modulu, Liferay API, session, backend, kandidáti na demo |
| [04-poc-modul-customercare-web.md](04-poc-modul-customercare-web.md) | co a jak udělal předchozí PoC, jeho mezery |
| [05-mapa-konceptov.md](05-mapa-konceptov.md) | Liferay/portlet koncept → náhrada |
| [06-playbook-migracie-portletu.md](06-playbook-migracie-portletu.md) | kde co hledat + postup migrace jednoho portletu + DoD, ukázka v demu |
| [07-pasce.md](07-pasce.md) | konkrétní pasti zjištěné v kódu a lozích |
| [09-session-liferay-vs-spring.md](09-session-liferay-vs-spring.md) | session před → po: PortletSession scope, fasáda, `@SessionAttributes`, jak se najde správná session, duální session |
| [10-liferay62-v-dockeri.md](10-liferay62-v-dockeri.md) | jak rozběhnout Liferay 6.2 lokálně (Java 8 ≤ 255, HSQL, deploy, oprávnění hosta, nginx) |
| [11-appka-v-inom-jazyku.md](11-appka-v-inom-jazyku.md) | kontrakt pro další aplikaci v jiném jazyce za stranglerem (identita, Redis session, cookies) – ověřeno Rust kopií |

Doporučené pořadí na reálném projektu: 00 → 01 → 03 → 05 → 06 → 07 (→ 04, pokud se navazuje na PoC).
Detailní analýza s odkazy na soubory: [`../docs/ANALYZA.md`](../docs/ANALYZA.md).

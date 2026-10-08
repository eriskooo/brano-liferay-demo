# Konvencie poznámok a anonymizácia

Tento repozitár (vrátane `poznamky/` a `CLAUDE.md`) pôjde na GitHub. Adresár `input/` obsahuje
klientske podklady, je **read-only a git-ignorovaný**.

Pravidlá pre všetko mimo `input/`:
- žiadne názvy klienta, dodávateľa, ľudí, interných systémov, interné URL, heslá, dáta z logov,
- žiadny skopírovaný klientsky kód – ukážky „pred“ sú prepísané na minimálne anonymizované príklady.

Skratky používané v poznámkach a v `docs/`:
| Skratka | Význam |
|---|---|
| `$FE` | koreň rozbaleného `frontend` Maven projektu v `input/` |
| `<pkg>` | koreňový Java balík legacy kódu (zámerne neuvádzaný) |
| legacy CRM | pôvodná aplikácia – CRM pre zákaznícku podporu telco operátora |
| klient | telco operátor, pre ktorého aplikácia beží |
| dodávateľ | firma, ktorá aplikáciu vyvíja a pripravila PoC |
| SSO portál | externý portál, ktorý drží identitu a časť session používateľa |
| externý CMS | systém, z ktorého sa ťahajú preklady/texty |
| integračná vrstva | backend (REST/SOAP), cez ktorý ide väčšina business volaní |

Názvy modulov `customercare-*` a `frontend-base-customization` sú generické a ostávajú.

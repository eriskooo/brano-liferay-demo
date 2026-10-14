# Konvence poznámek a anonymizace

Tento repozitář (včetně `poznamky/` a `CLAUDE.md`) půjde na GitHub. Adresář `input/` obsahuje
klientské podklady, je **read-only a git-ignorovaný**.

Pravidla pro vše mimo `input/`:
- žádné názvy klienta, dodavatele, lidí, interních systémů, interní URL, hesla, data z logů,
- žádný zkopírovaný klientský kód – ukázky „před“ jsou přepsány na minimální anonymizované příklady.

Zkratky používané v poznámkách a v `docs/`:
| Zkratka | Význam |
|---|---|
| `$FE` | kořen rozbaleného `frontend` Maven projektu v `input/` |
| `<pkg>` | kořenový Java balík legacy kódu (záměrně neuváděný) |
| legacy CRM | původní aplikace – CRM pro zákaznickou podporu telco operátora |
| klient | telco operátor, pro kterého aplikace běží |
| dodavatel | firma, která aplikaci vyvíjí a připravila PoC |
| SSO portál | externí portál, který drží identitu a část session uživatele |
| externí CMS | systém, ze kterého se stahují překlady/texty |
| integrační vrstva | backend (REST/SOAP), přes který jde většina business volání |

Názvy modulů `customercare-*` a `frontend-base-customization` jsou generické a zůstávají.

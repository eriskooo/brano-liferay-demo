# Další aplikace v jiném jazyce za stranglerem

Ověřeno v demu Rust kopií (`crm-web-rs`). Aplikace v libovolném jazyce se připojí, pokud dodrží tento kontrakt –
nic jiného z Javy/Liferay nepotřebuje.

## Identita
- Cookie `SSO_ID` (Path=/, nastavuje SSO portál). Ověřit platný tvar (`[A-Za-z0-9-]{1,64}`) – hodnota jde do URL i do klíče v Redisu.
- `GET {sso}/api/sessions/{id}` → `{"operator":{"login","displayName"},"customer":{"customerId","displayName"}}`.
  404 = nepřihlášen (stránka → `/sso/login?returnUrl=<url-encoded cesta>`, AJAX → 401); jiná chyba/timeout = „SSO nedostupné“, ne odhlášení.
- Krátké timeouty (1 s / 2 s) – na SSO se aplikace ptá při každém requestu.

## Sdílená session
- Redis hash `crm:session:{SSO_ID}`, TTL 30 min obnovovat při čtení i zápisu.
- Pole s prefixem `APP_`: `APP_MESSAGES` (pole `{"level":"INFO|ERROR","key"}`), `APP_BLACKLIST_LAST_CRITERIA`, `APP_LAST_VIEWED_RECORD`.
- JSON: camelCase, enumy jako řetězce, `null` pro neodeslaná pole; **neznámá pole ignorovat** (jiná aplikace může přidat pole dříve).
- Hlášky: zapisovat vždy celý nový seznam; po zobrazení pole smazat (zobrazí se právě jednou v kterékoli aplikaci).
- Výpadek Redisu nesmí shodit request (timeout ~500 ms, log, pokračovat bez session) a po návratu Redisu je třeba se znovu připojit.
- Flag `crm:flags:session-read-source` patří staré aplikaci – nová ho jen zobrazuje.

## Za jedním nginx
- Vlastní kontext (`/crm`, `/crm-rs`) a **vlastní session cookie s Path na kontext** – jinak přepíše `JSESSIONID` Liferay nebo cookie jiné aplikace.
- CSS témata tahat z Liferay přes nginx (stejný vzhled); mimo :8000 je stránka bez stylu.
- AJAX rozlišovat hlavičkou `X-Requested-With: XMLHttpRequest` (fragment / 401 / 403 bez layoutu).

## Pasti
- Texty a statické soubory nekopírovat – číst z jednoho zdroje, jinak se aplikace rozejdou.
- Regex `\d` v některých jazycích bere i Unicode číslice (Rust) – pro formáty identifikátorů `[0-9]`.
- Redirect po POST: Spring dává 302, některé frameworky 303 – pro shodné chování nastavit explicitně.

# Ďalšia appka v inom jazyku za stranglerom

Overené v deme Rust kópiou (`crm-web-rs`). Appka v ľubovoľnom jazyku sa pripojí, ak dodrží tento kontrakt –
nič iné z Javy/Liferay nepotrebuje.

## Identita
- Cookie `SSO_ID` (Path=/, nastavuje SSO portál). Platný tvar overiť (`[A-Za-z0-9-]{1,64}`) – hodnota ide do URL aj do kľúča v Redise.
- `GET {sso}/api/sessions/{id}` → `{"operator":{"login","displayName"},"customer":{"customerId","displayName"}}`.
  404 = neprihlásený (stránka → `/sso/login?returnUrl=<url-encoded cesta>`, AJAX → 401); iná chyba/timeout = „SSO nedostupné“, nie odhlásenie.
- Krátke timeouty (1 s / 2 s) – SSO sa pýta pri každom requeste.

## Zdieľaná session
- Redis hash `crm:session:{SSO_ID}`, TTL 30 min obnovovať pri čítaní aj zápise.
- Polia s prefixom `APP_`: `APP_MESSAGES` (pole `{"level":"INFO|ERROR","key"}`), `APP_BLACKLIST_LAST_CRITERIA`, `APP_LAST_VIEWED_RECORD`.
- JSON: camelCase, enumy ako reťazce, `null` pre neodoslané polia; **neznáme polia ignorovať** (iná appka môže pridať pole skôr).
- Hlášky: zapisovať vždy celý nový zoznam; po zobrazení pole zmazať (zobrazia sa práve raz v ktorejkoľvek appke).
- Výpadok Redisu nesmie zhodiť request (timeout ~500 ms, log, pokračovať bez session) a po návrate Redisu sa treba znova pripojiť.
- Flag `crm:flags:session-read-source` patrí starej appke – nová ho len zobrazuje.

## Za jedným nginx
- Vlastný kontext (`/crm`, `/crm-rs`) a **vlastná session cookie s Path na kontext** – inak prepíše `JSESSIONID` Liferay alebo cookie inej appky.
- CSS témy ťahať z Liferay cez nginx (rovnaký vzhľad); mimo :8000 je stránka bez štýlu.
- AJAX rozlišovať hlavičkou `X-Requested-With: XMLHttpRequest` (fragment / 401 / 403 bez layoutu).

## Pasce
- Texty a statické súbory nekopírovať – čítať z jedného zdroja, inak sa appky rozídu.
- Regex `\d` v niektorých jazykoch berie aj Unicode číslice (Rust) – pre formáty identifikátorov `[0-9]`.
- Redirect po POST: Spring dáva 302, niektoré frameworky 303 – pre zhodné správanie nastaviť explicitne.

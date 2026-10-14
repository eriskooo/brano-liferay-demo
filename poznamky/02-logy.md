# Co říkají logy (`input/*_war.log`, `input/*_war_error.log`)

- Oba logy jsou z lokálního běhu migračního modulu **customercare-web** (Tomcat 9, port 8080),
  dva starty aplikace v jeden den. Error log = tentýž obsah bez INFO řádků (ověřeno `diff`).
- Při startu `StubServiceBeanFactory` vytváří **no-op stuby pro 16 backend služeb**
  (zákazník/party, faktury, platební brána, vyhledávání entit, požadavky zákazníka, kampaně,
  servis HW, přenos čísla, scoring, hromadné akce, dokumenty, …).
  ⇒ backend nebyl k dispozici, aplikace běží „offline“ s prázdnými odpověďmi.
- Jediná chyba: `IllegalStateException: Neither BindingResult nor plain target object for bean name
  '<model>'` u `<form:input>` v JSP formuláře pro deaktivaci tarifu
  (`WEB-INF/jsp/dashboard/tariffDeactivationRequest/…View.jsp`).
  Typická příčina: controller před renderem nevložil command objekt do modelu. V portletovém světě ho
  dodával `@ModelAttribute` v render fázi; při přepisu na servlet MVC se to ztratilo.
  Je to přesně formulář „Zrušení služby“ z PoC prezentace → dobrý kandidát na demo i poučení.
- Hibernate Validator 4.2.0.Final → staré JSR-303 (`javax.validation`).
- Logy obsahují interní názvy balíků a služeb → do repa z nich nic nekopírovat.

# Čo hovoria logy (`input/*_war.log`, `input/*_war_error.log`)

- Oba logy sú z lokálneho behu migračného modulu **customercare-web** (Tomcat 9, port 8080),
  dva štarty aplikácie v jeden deň. Error log = ten istý obsah bez INFO riadkov (overené `diff`).
- Pri štarte `StubServiceBeanFactory` vytvára **no-op stuby pre 16 backend služieb**
  (zákazník/party, faktúry, platobná brána, vyhľadávanie entít, požiadavky zákazníka, kampane,
  servis HW, prenos čísla, scoring, hromadné akcie, dokumenty, …).
  ⇒ backend nebol k dispozícii, aplikácia beží „offline“ s prázdnymi odpoveďami.
- Jediná chyba: `IllegalStateException: Neither BindingResult nor plain target object for bean name
  '<model>'` pri `<form:input>` v JSP formulára na deaktiváciu tarifu
  (`WEB-INF/jsp/dashboard/tariffDeactivationRequest/…View.jsp`).
  Typická príčina: controller pred renderom nevložil command objekt do modelu. V portletovom svete ho
  dodával `@ModelAttribute` v render fáze; pri prepise na servlet MVC sa to stratilo.
  Je to presne formulár „Zrušenie služby“ z PoC prezentácie → dobrý kandidát na demo aj poučenie.
- Hibernate Validator 4.2.0.Final → staré JSR-303 (`javax.validation`).
- Logy obsahujú interné názvy balíkov a služieb → do repa nič z nich nekopírovať.

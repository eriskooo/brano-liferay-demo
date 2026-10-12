package com.example.crm.frontend.base.session;

import com.example.sso.api.PortalCustomer;
import com.example.sso.api.PortalOperator;
import java.util.List;

/**
 * Fasáda session – jediný bod, cez ktorý controllery pracujú so session (v legacy ~400 volaní).
 * Kontrakt ostáva pri migrácii rovnaký, mení sa len to, čo je pod ním. Metódy vracajú {@code null}
 * namiesto {@code Optional} – legacy štýl (Java 8 kód písaný v štýle Javy 6).
 */
public interface SessionManagerCustom {

    /** Prihlásený operátor zo SSO portálu, alebo {@code null}. */
    PortalOperator getOperator();

    /** Zákazník vybraný v SSO portáli, alebo {@code null}. */
    PortalCustomer getCustomer();

    /** Či je operátor prihlásený v SSO portáli. */
    boolean isLoggedIn();

    /** URL prihlásenia v SSO portáli s návratom na aktuálnu stránku. */
    String getLoginUrl();

    /** Pridá informačnú hlášku (i18n kľúč) – zobrazí sa v nasledujúcom renderi. */
    void pushInfo(String key);

    /** Pridá chybovú hlášku (i18n kľúč). */
    void pushError(String key);

    /** Vráti a zmaže čakajúce hlášky. */
    List<UiMessage> popMessages();

    /** Uloží atribút session (kľúč dostane prefix {@code APP_}). */
    void setGlobalAttribute(String key, Object value);

    /** Prečíta atribút session, alebo {@code null}. */
    Object getGlobalAttribute(String key);

    /** Odstráni atribút session. */
    void removeGlobalAttribute(String key);

    /** Zmaže všetky atribúty fasády. */
    void flushAll();
}

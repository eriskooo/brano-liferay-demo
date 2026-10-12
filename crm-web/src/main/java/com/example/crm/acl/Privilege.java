package com.example.crm.acl;

/**
 * Privilégiá – názvy sa v JSP používajú ako reťazec v atribúte {@code privilegeType}, rovnako ako v legacy ACL tagoch.
 * Podklady nenavrhujú zmenu security, preto demo zachováva model „privilégium na operátora“.
 */
public enum Privilege {
    BLACKLIST_VIEW,
    BLACKLIST_MODIFY,
    DEACTIVATION
}

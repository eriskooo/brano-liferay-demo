package com.example.crm.frontend.base.acl;

import com.example.crm.frontend.base.session.SessionManagerCustom;
import com.example.sso.api.PortalOperator;

/** ACL pre aktuálne prihláseného operátora – používajú ho controllery, JSP tagy aj DispatcherPortletCustom. */
public class OperatorAcl {

    private final SessionManagerCustom sessionManager;
    private final AclService aclService;

    public OperatorAcl(SessionManagerCustom sessionManager, AclService aclService) {
        this.sessionManager = sessionManager;
        this.aclService = aclService;
    }

    /** Či má aktuálny operátor privilégium (neprihlásený nemá žiadne). */
    public boolean hasPrivilege(Privilege privilege) {
        PortalOperator operator = sessionManager.getOperator();
        return operator != null && aclService.hasPrivilege(operator.getLogin(), privilege);
    }

    /** Variant pre JSP tagy (privilégium ako text); neznáme meno je chyba v JSP. */
    public boolean hasPrivilege(String privilegeName) {
        try {
            return hasPrivilege(Privilege.valueOf(privilegeName));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Neznáme privilégium '" + privilegeName + "'", e);
        }
    }

    /** Vyhodí {@link AclDeniedException}, ak aktuálny operátor privilégium nemá. */
    public void checkPrivilege(Privilege privilege) {
        if (!hasPrivilege(privilege)) {
            throw new AclDeniedException(privilege);
        }
    }
}

package com.example.crm.frontend.base.acl;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Demo ACL: dvaja vymyslení operátori. SSO portál pozná len identitu, privilégiá sú vecou CRM. */
public class InMemoryAclService implements AclService {

    private final Map<String, Set<Privilege>> privileges = new HashMap<>();

    public InMemoryAclService() {
        privileges.put("citatel", EnumSet.of(Privilege.BLACKLIST_VIEW));
        privileges.put("spravca", EnumSet.allOf(Privilege.class));
    }

    @Override
    public boolean hasPrivilege(String operatorLogin, Privilege privilege) {
        if (operatorLogin == null) {
            return false;
        }
        Set<Privilege> granted = privileges.get(operatorLogin);
        return (granted == null ? Collections.<Privilege>emptySet() : granted).contains(privilege);
    }
}

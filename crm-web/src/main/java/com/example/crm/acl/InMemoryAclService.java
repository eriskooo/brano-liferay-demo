package com.example.crm.acl;

import java.util.Map;
import java.util.Set;

import com.example.crm.session.OperatorUser;
import org.springframework.stereotype.Service;

/** Pevné privilégiá demo operátorov (loginy zodpovedajú SSO mocku). */
@Service
public class InMemoryAclService implements AclService {

    private final Map<String, Set<Privilege>> privilegesByLogin = Map.of(
            "citatel", Set.of(Privilege.BLACKLIST_VIEW),
            "spravca", Set.of(Privilege.BLACKLIST_VIEW, Privilege.BLACKLIST_MODIFY, Privilege.DEACTIVATION));

    @Override
    public boolean hasPrivilege(OperatorUser operator, Privilege privilege) {
        return privilegesOf(operator).contains(privilege);
    }

    @Override
    public Set<Privilege> privilegesOf(OperatorUser operator) {
        return privilegesByLogin.getOrDefault(operator.login(), Set.of());
    }
}

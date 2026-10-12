package com.example.crm.acl;

import java.util.Set;

import com.example.crm.session.OperatorUser;

/** Port na ACL – v legacy vlastná ACL služba backendu; demo má in-memory implementáciu. */
public interface AclService {

    boolean hasPrivilege(OperatorUser operator, Privilege privilege);

    Set<Privilege> privilegesOf(OperatorUser operator);
}

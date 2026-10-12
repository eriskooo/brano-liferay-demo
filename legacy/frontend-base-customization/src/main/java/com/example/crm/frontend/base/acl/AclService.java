package com.example.crm.frontend.base.acl;

/** Port na ACL službu. V legacy je za ním backend; pri migrácii sa ponechá (prípadne obalí Spring Security). */
public interface AclService {

    /** Či má operátor s daným loginom privilégium. */
    boolean hasPrivilege(String operatorLogin, Privilege privilege);
}

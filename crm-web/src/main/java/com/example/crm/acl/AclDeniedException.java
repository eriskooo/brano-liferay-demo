package com.example.crm.acl;

/** Operátor nemá privilégium – web vrstva ju mapuje na HTTP 403. */
public class AclDeniedException extends RuntimeException {

    private final Privilege privilege;

    public AclDeniedException(Privilege privilege) {
        super("Chýba privilégium " + privilege);
        this.privilege = privilege;
    }

    public Privilege getPrivilege() {
        return privilege;
    }
}

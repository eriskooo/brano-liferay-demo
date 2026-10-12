package com.example.crm.frontend.base.acl;

/** Chýbajúce privilégium. Exception resolver z nej spraví stránku 403 (resource: HTTP 403). */
public class AclDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Privilege privilege;

    public AclDeniedException(Privilege privilege) {
        super("Chýba privilégium " + privilege);
        this.privilege = privilege;
    }

    public Privilege getPrivilege() {
        return privilege;
    }
}

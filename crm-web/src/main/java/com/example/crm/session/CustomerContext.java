package com.example.crm.session;

import java.io.Serializable;

/** Zákazník vybraný v SSO portáli – CRM ho nemení, len číta. */
public record CustomerContext(String customerId, String displayName) implements Serializable {
}

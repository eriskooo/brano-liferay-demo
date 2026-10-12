package com.example.crm.frontend.backend.blacklist;

/** Porušené business pravidlo – nesie i18n kľúč hlášky pre používateľa. */
public class BlacklistRuleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String messageKey;

    public BlacklistRuleException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}

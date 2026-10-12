package com.example.crm.blacklist;

/** Porušené business pravidlo – nesie i18n kľúč hlášky pre používateľa. */
public class BlacklistRuleException extends RuntimeException {

    private final String messageKey;

    public BlacklistRuleException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}

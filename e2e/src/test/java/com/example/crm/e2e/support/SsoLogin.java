package com.example.crm.e2e.support;

import com.microsoft.playwright.Page;

/** Prihlásenie cez UI SSO mocku – identitu vlastní SSO portál, nie CRM (cookie SSO_ID platí pre obe appky). */
public final class SsoLogin {

    private SsoLogin() {
    }

    /** Prihlási operátora so zákazníkom; po návrate ostane na stránke SSO (cieľ otvorí volajúci). */
    public static void login(Page page, String ssoLoginUrl, String operator, String customer) {
        // returnUrl na SSO stránku: návrat nesmie viesť do žiadnej z testovaných appiek (log testu by mal šum).
        page.navigate(ssoLoginUrl + "?returnUrl=%2Fsso%2Flogin");
        page.locator("select[name=operator]").selectOption(operator);
        page.locator("select[name=customer]").selectOption(customer);
        page.locator("form[action='/sso/login'] button[type=submit]").click();
    }
}

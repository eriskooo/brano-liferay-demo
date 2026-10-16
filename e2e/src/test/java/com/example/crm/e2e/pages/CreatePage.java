package com.example.crm.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** Nový záznam: zmena dôvodu cez AJAX prekreslí povolené podmienky. */
public final class CreatePage {

    private final Page page;

    public CreatePage(Page page) {
        this.page = page;
        page.locator("#createForm").waitFor();
    }

    /** Zvolí dôvod a počká na AJAX odpoveď s podmienkami. */
    public CreatePage selectReason(String reason) {
        page.waitForResponse(response -> response.url().contains("refreshCreateConditionTypes"),
                () -> page.locator("#createForm [name=stateReason]").selectOption(reason));
        return this;
    }

    /** Ponúkané podmienky (bez prázdnej voľby). */
    public Locator conditionTypeOptions() {
        return page.locator("#conditionTypeBox select option:not([value=''])");
    }

    /** Vyplní a odošle formulár; „platné od“ ostáva predvyplnené (dnes). Dátum vo formáte yyyy-MM-dd. */
    public DetailPage submit(String firstName, String lastName, String birthDate, String conditionType,
                             String identifierType, String identifierValue) {
        page.locator("#createForm [name=firstName]").fill(firstName);
        page.locator("#createForm [name=lastName]").fill(lastName);
        page.locator("#createForm [name=birthDate]").fill(birthDate);
        page.locator("#conditionTypeBox select").selectOption(conditionType);
        page.locator("#createForm [name=identifierType]").selectOption(identifierType);
        page.locator("#createForm [name=identifierValue]").fill(identifierValue);
        page.locator("#createForm button[type=submit]").click();
        return new DetailPage(page);
    }
}

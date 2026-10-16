package com.example.crm.e2e.pages;

import com.microsoft.playwright.Page;

/** Vyhľadávanie blacklistu – rovnaké ID elementov v starej aj novej appke. */
public final class SearchPage {

    private final Page page;

    public SearchPage(Page page) {
        this.page = page;
        page.locator("#searchForm").waitFor();
    }

    /** Hľadá podľa mena (ostatné polia prázdne). */
    public ResultPage searchByFirstName(String firstName) {
        return search("firstName", firstName);
    }

    /** Hľadá podľa priezviska (ostatné polia prázdne). */
    public ResultPage searchByLastName(String lastName) {
        return search("lastName", lastName);
    }

    /** Či operátor vidí tlačidlo „Nový záznam“. */
    public boolean canCreate() {
        return page.locator("#gotoCreateForm").count() > 0;
    }

    /** Otvorí formulár nového záznamu. */
    public CreatePage openCreate() {
        page.locator("#gotoCreateForm button[type=submit]").click();
        return new CreatePage(page);
    }

    private ResultPage search(String field, String value) {
        page.locator("#searchForm [name=" + field + "]").fill(value);
        page.locator("#searchForm button[type=submit]").click();
        return new ResultPage(page);
    }
}

package com.example.crm.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.List;

/** Detail záznamu: zmena stavu, poznámky, odstránenie identifikátora (AJAX). */
public final class DetailPage {

    private static final String PAGE_MARKER = "() => window.__e2eMarker = 'detail'";

    private final Page page;

    public DetailPage(Page page) {
        this.page = page;
        page.locator("#recordSummary").waitFor();
    }

    /** Nadpis stránky (obsahuje meno osoby). */
    public String heading() {
        return page.locator("h2").first().innerText().trim();
    }

    /** Zobrazený stav (text v jazyku UI). */
    public String state() {
        return page.locator("#recordState").innerText().trim();
    }

    /** Hlášky z hlavičky (info aj chyby). */
    public List<String> messages() {
        return page.locator(".crm-message").allInnerTexts().stream().map(String::trim).toList();
    }

    /** Celý viditeľný text – na chyby, ktoré appky zobrazujú na rôznych miestach. */
    public String bodyText() {
        return page.locator("body").innerText();
    }

    /** Zoznam identifikátorov (prekresľuje ho AJAX). */
    public Locator identifiers() {
        return page.locator("#identifiers");
    }

    /** Zmení stav a platnosť; prázdne {@code validTo} = bez dátumu. */
    public DetailPage changeState(String state, String validTo) {
        page.locator("#changeForm [name=state]").selectOption(state);
        page.locator("#changeForm [name=validTo]").fill(validTo);
        page.locator("#changeForm button[type=submit]").click();
        return new DetailPage(page);
    }

    /** Pridá poznámku. */
    public DetailPage addNote(String text) {
        page.locator("#noteForm [name=text]").fill(text);
        page.locator("#noteForm button[type=submit]").click();
        return new DetailPage(page);
    }

    /** Odstráni identifikátor cez AJAX a počká na odpoveď servera. */
    public void removeIdentifier(long identifierId) {
        page.waitForResponse(response -> "POST".equals(response.request().method())
                        && response.url().contains("removeIdent"),
                () -> page.locator("#remove-ident-" + identifierId).click());
    }

    /** Či operátor vidí ovládacie prvky zmien. */
    public boolean canModify() {
        return page.locator("#changeForm").count() > 0
                || page.locator("#noteForm").count() > 0
                || page.locator("a[id^='remove-ident-']").count() > 0;
    }

    /** Označí aktuálny dokument – po AJAX akcii sa overí, že sa stránka neprenačítala. */
    public void markPage() {
        page.evaluate(PAGE_MARKER);
    }

    /** Či je to stále ten istý dokument ako pri {@link #markPage()}. */
    public boolean isSamePage() {
        return "detail".equals(page.evaluate("() => window.__e2eMarker"));
    }
}

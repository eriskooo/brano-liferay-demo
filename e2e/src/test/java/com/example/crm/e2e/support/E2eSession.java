package com.example.crm.e2e.support;

import com.microsoft.playwright.Page;

/** Všetko, čo test potrebuje: prihlásená stránka na vstupe cieľa, DB a log od začiatku testu. */
public record E2eSession(Page page, AppTarget target, DbProbe db, LogProbe log) {
}

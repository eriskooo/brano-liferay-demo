package com.example.crm.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.Map;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Jeden prehliadač na celý beh (drahý štart); každý test dostane vlastný čistý BrowserContext. */
final class BrowserHolder implements ExtensionContext.Store.CloseableResource {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(BrowserHolder.class);

    private final Playwright playwright;
    private final Browser browser;

    private BrowserHolder(E2eConfig config) {
        boolean chrome = "chrome".equals(config.browser());
        // Kanál chrome = nainštalovaný Google Chrome; Playwright vtedy nesťahuje vlastné Chromium.
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(chrome ? Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1") : Map.of()));
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(!config.headed());
        if (chrome) {
            options.setChannel("chrome");
        }
        browser = playwright.chromium().launch(options);
    }

    /** Prehliadač pre celý beh testov (uzavrie ho JUnit na konci). */
    static Browser browser(ExtensionContext context, E2eConfig config) {
        return context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(BrowserHolder.class, key -> new BrowserHolder(config), BrowserHolder.class)
                .browser;
    }

    @Override
    public void close() {
        // Len playwright.close(): ukončí driver aj ním spustený prehliadač. Browser.close() s kanálom chrome
        // na Windows občas nikdy nedostane odpoveď a test JVM by po dobehnutí testov visel.
        playwright.close();
    }
}

package com.example.crm.e2e.support;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Životný cyklus e2e testu: reset DB → značka v logu → čistý prehliadač → SSO prihlásenie → vstup cieľa.
 * Po teste hygiena logu (ERROR/stacktrace = zlyhanie, aj keď UI prešlo) a pri zlyhaní artefakty.
 */
public class E2eExtension implements BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback,
        ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(E2eExtension.class);
    private static final String DEFAULT_OPERATOR = "spravca";
    private static final String CUSTOMER = "C-1001";

    @Override
    public void beforeEach(ExtensionContext context) {
        E2eConfig config = E2eConfig.fromSystemProperties();
        AppTarget target = AppTarget.of(config);
        DbProbe db = new JdbcDbProbe(config);
        db.reset();
        LogProbe log = new FileLogProbe(target.appLog());
        log.mark();
        BrowserContext browserContext = BrowserHolder.browser(context, config).newContext();
        browserContext.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        Page page = browserContext.newPage();
        SsoLogin.login(page, config.ssoUrl(), operator(context), CUSTOMER);
        page.navigate(target.entryUrl());
        context.getStore(NAMESPACE).put(E2eSession.class, new E2eSession(page, target, db, log));
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        E2eSession e2e = session(context);
        if (e2e == null) {
            return; // beforeEach zlyhal (DB/prehliadač) – jeho výnimka je už príčinou zlyhania testu
        }
        List<String> errors = e2e.log().errors();
        Tracing tracing = e2e.page().context().tracing();
        if (context.getExecutionException().isPresent() || !errors.isEmpty()) {
            Path dir = Path.of("target", "e2e-artifacts", e2e.target().name(),
                    context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName());
            saveArtifacts(e2e, dir);
            tracing.stop(new Tracing.StopOptions().setPath(dir.resolve("trace.zip")));
        } else {
            tracing.stop();
        }
        if (!errors.isEmpty()) {
            throw new AssertionError("Hygiena logu " + e2e.target().appLog() + " – počas testu vznikli chyby:\n"
                    + String.join("\n", errors));
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        E2eSession e2e = session(context);
        if (e2e != null) {
            e2e.page().context().close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == E2eSession.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return session(extensionContext);
    }

    private static E2eSession session(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(E2eSession.class, E2eSession.class);
    }

    private static String operator(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.getAnnotation(Operator.class))
                .map(Operator::value)
                .orElse(DEFAULT_OPERATOR);
    }

    private static void saveArtifacts(E2eSession e2e, Path dir) {
        try {
            Files.createDirectories(dir);
            e2e.page().screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("screenshot.png")).setFullPage(true));
            Files.writeString(dir.resolve("app.log"), e2e.log().excerpt(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Artefakty do " + dir + " sa nepodarilo uložiť", e);
        }
    }
}

package com.example.kb.guide;

import java.util.List;

/**
 * HTML guide pre Neo4j Browser (:play). Browser nevie čítať .cypher súbory, preto ich prevádzame na slajdy;
 * blok s triedou "runnable" sa klikom vloží do editora.
 */
public final class GuideGenerator {

    private static final String INTRO = """
            <slide class="row-fluid">
              <div class="col-sm-12">
                <h3>Znalostní báze projektu v grafu</h3>
                <p>Graf má dvě vrstvy: <b>strukturu</b> vygenerovanou z kódu (moduly, služby, trasy nginx, portlety,
                endpointy, tabulky, poznámky) a ručně psané <b>lekce</b> (pasti z poznamky/07, koncepty z poznamky/05)
                ukotvené na strukturu.</p>
                <p>Na každém dalším slajdu je blok s dotazem: tlačítko <b>Execute in Query tab</b> (▶) ho rovnou
                spustí, <b>Edit in Cypher editor</b> ho jen vloží do editoru. Výsledek uvidíš jako graf; záložkami
                Graph / Table / Raw nad výsledkem ho zobrazíš i jako tabulku.</p>
                <p>Uzel (kroužek) má <b>label</b> (barva) a vlastnosti; vztah (šipka) má <b>typ</b> a směr.</p>
              </div>
            </slide>
            """;

    private GuideGenerator() {
    }

    /** Vygeneruje celý guide: úvodný slajd + jeden slajd na dotaz. */
    public static String render(List<SavedQuery> queries) {
        StringBuilder html = new StringBuilder();
        html.append("<article class=\"guide\">\n<carousel class=\"deck container-fluid\">\n");
        // Názov guide berie Browser z article > carousel > h4.
        html.append("<h4>Znalostní báze v grafu</h4>\n");
        html.append(INTRO);
        for (int i = 0; i < queries.size(); i++) {
            html.append(slide(i + 1, queries.get(i)));
        }
        html.append("</carousel>\n</article>\n");
        return html.toString();
    }

    private static String slide(int number, SavedQuery q) {
        StringBuilder s = new StringBuilder();
        s.append("<slide class=\"row-fluid\">\n  <div class=\"col-sm-12\">\n");
        s.append("    <h3>").append(number).append(". ").append(escape(q.title())).append("</h3>\n");
        s.append("    <p>").append(escape(q.text())).append("</p>\n");
        if (!q.teaches().isEmpty()) {
            s.append("    <p><em>Cypher: ").append(escape(q.teaches())).append("</em></p>\n");
        }
        s.append("    <pre class=\"pre-scrollable code runnable\">").append(escape(q.cypher())).append("</pre>\n");
        if (!q.sql().isEmpty()) {
            s.append("    <p>Totéž v SQL (jen pro srovnání, nespouští se):</p>\n");
            s.append("    <pre class=\"pre-scrollable code\">").append(escape(q.sql())).append("</pre>\n");
        }
        s.append("  </div>\n</slide>\n");
        return s.toString();
    }

    /** HTML escapovanie textu slajdu a dotazu (šípky vzťahov &lt;- a -&gt;). */
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

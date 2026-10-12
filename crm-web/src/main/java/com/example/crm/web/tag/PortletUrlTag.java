package com.example.crm.web.tag;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import com.example.crm.web.portlet.PortletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.springframework.web.util.HtmlUtils;

/**
 * Spoločný základ {@code <portal:actionURL|renderURL|resourceURL>} – ROVNAKÉ ROZHRANIE ako legacy tagy,
 * nová implementácia.
 *
 * <p>Legacy tag si od portletového kontajnera vypýtal {@code LiferayPortletURL} (URL zakódované do stránky portálu
 * s ID portletu a fázou). Tu sa z tých istých parametrov poskladá obyčajná servletová URL podľa jednoduchej
 * konvencie: {@code {contextPath}{basePath}{segment}?ostatné=parametre}. JSP sa preto nemenia.
 * Toto je jediné miesto, kde sa „portletové“ URL prekladajú – v reálnom projekte by tu bola tabuľka routovania
 * (deeplinker z podkladov), nie konvencia.
 */
public abstract class PortletUrlTag extends TagSupport {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, List<String>> parameters = new LinkedHashMap<>();
    private String var;
    private boolean escapeXml = true;

    public void setVar(String var) {
        this.var = var;
    }

    public void setEscapeXml(boolean escapeXml) {
        this.escapeXml = escapeXml;
    }

    void addParameter(String name, String value) {
        parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value == null ? "" : value);
    }

    @Override
    public int doStartTag() {
        // Jasper inštancie tagov opakovane používa – parametre z predchádzajúceho použitia sa musia zahodiť.
        parameters.clear();
        return EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException {
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        String basePath = (String) request.getAttribute(PortletRequestAttributes.BASE_PATH);
        if (basePath == null) {
            throw new JspException(tagName() + ": chýba base path portletu (request atribút "
                    + PortletRequestAttributes.BASE_PATH + "). Má controller anotáciu @LegacyPortlet?");
        }
        Map<String, List<String>> remaining = new LinkedHashMap<>(parameters);
        String url = request.getContextPath() + basePath + resolvePath(remaining) + toQueryString(remaining);
        try {
            if (var != null) {
                pageContext.setAttribute(var, url);
            } else {
                pageContext.getOut().write(escapeXml ? HtmlUtils.htmlEscape(url, "UTF-8") : url);
            }
        } catch (IOException e) {
            throw new JspException(e);
        }
        return EVAL_PAGE;
    }

    /** Vráti časť cesty za base path (napr. {@code /searchAction}) a odoberie z mapy parametre, ktoré spotreboval. */
    protected abstract String resolvePath(Map<String, List<String>> parameters) throws JspException;

    protected abstract String tagName();

    protected static String take(Map<String, List<String>> parameters, String name) {
        List<String> values = parameters.remove(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    protected String segment(String value, String what) throws JspException {
        if (value == null || value.isBlank()) {
            throw new JspException(tagName() + ": chýba " + what);
        }
        if (!SAFE_SEGMENT.matcher(value).matches()) {
            throw new JspException(tagName() + ": nepovolený segment URL '" + value + "'");
        }
        return "/" + value;
    }

    private static String toQueryString(Map<String, List<String>> parameters) {
        StringJoiner query = new StringJoiner("&", "?", "");
        query.setEmptyValue("");
        parameters.forEach((name, values) -> values.forEach(value -> query.add(encode(name) + "=" + encode(value))));
        return query.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void release() {
        super.release();
        parameters.clear();
        var = null;
        escapeXml = true;
    }
}

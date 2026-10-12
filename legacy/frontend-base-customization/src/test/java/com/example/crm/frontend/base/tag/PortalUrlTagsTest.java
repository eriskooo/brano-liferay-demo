package com.example.crm.frontend.base.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.portlet.LiferayPortletURL;
import com.liferay.portal.kernel.util.JavaConstants;
import javax.portlet.PortletURL;
import javax.portlet.RenderResponse;
import javax.servlet.jsp.JspException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockPageContext;

class PortalUrlTagsTest {

    private final RenderResponse response = mock(RenderResponse.class);
    private final LiferayPortletURL url = mock(LiferayPortletURL.class);
    private final MockPageContext pageContext = TagTestSupport.pageContext(null, null);

    @BeforeEach
    void exposePortletResponse() {
        // Liferay vystaví portlet response JSP-čku ako request atribút – presne odtiaľ ho legacy tagy berú.
        pageContext.getRequest().setAttribute(JavaConstants.JAVAX_PORTLET_RESPONSE, response);
        when(response.createActionURL()).thenReturn(url);
        when(response.createRenderURL()).thenReturn(url);
        when(response.createResourceURL()).thenReturn(url);
        when(url.toString()).thenReturn("/web/crm/blacklist?p_p_id=blacklist&action=searchAction");
    }

    @Test
    @DisplayName("actionURL nastaví parametre a vypíše escapované URL")
    void should_writeEscapedUrlWithParams_when_actionUrl() throws JspException {
        ActionUrlTag tag = new ActionUrlTag();
        run(tag, "action", "searchAction", "viewPage", "searchresult");

        verify(url).setParameter("action", new String[] {"searchAction"});
        verify(url).setParameter("viewPage", new String[] {"searchresult"});
        assertThat(TagTestSupport.content(pageContext))
                .isEqualTo("/web/crm/blacklist?p_p_id=blacklist&amp;action=searchAction");
    }

    @Test
    @DisplayName("var uloží URL do page scope; escapeXml=false nechá & bez zmeny")
    void should_storeRawUrlInVar_when_escapeDisabled() throws JspException {
        RenderUrlTag tag = new RenderUrlTag();
        tag.setVar("u");
        tag.setEscapeXml(false);
        run(tag);

        assertThat(pageContext.getAttribute("u")).isEqualTo("/web/crm/blacklist?p_p_id=blacklist&action=searchAction");
        assertThat(TagTestSupport.content(pageContext)).isEmpty();
    }

    @Test
    @DisplayName("resourceURL nastaví ID resource a kopírovanie render parametrov (Liferay rozšírenie)")
    void should_setResourceIdAndCopyFlag_when_resourceUrl() throws JspException {
        ResourceUrlTag tag = new ResourceUrlTag();
        tag.setId("removeIdentAction");
        tag.setCopyCurrentRenderParameters(true);
        run(tag);

        verify(url).setResourceID("removeIdentAction");
        verify(url).setCopyCurrentRenderParameters(true);
    }

    @Test
    @DisplayName("Rovnaké meno parametra viackrát = viac hodnôt")
    void should_collectMultipleValues_when_paramRepeated() throws JspException {
        run(new RenderUrlTag(), "id", "1", "id", "2");

        verify(url).setParameter("id", new String[] {"1", "2"});
    }

    @Test
    @DisplayName("Mimo JSP portletu (bez javax.portlet.response) je JspException s menom tagu")
    void should_fail_when_noPortletResponse() {
        pageContext.getRequest().removeAttribute(JavaConstants.JAVAX_PORTLET_RESPONSE);

        assertThatThrownBy(() -> run(new ActionUrlTag()))
                .isInstanceOf(JspException.class)
                .hasMessageContaining("portal:actionURL");
    }

    @Test
    @DisplayName("Portál bez Liferay (iná implementácia PortletURL) je JspException – závislosť je explicitná")
    void should_fail_when_urlIsNotLiferay() {
        when(response.createActionURL()).thenReturn(mock(PortletURL.class));

        assertThatThrownBy(() -> run(new ActionUrlTag()))
                .isInstanceOf(JspException.class)
                .hasMessageContaining("LiferayPortletURL");
    }

    @Test
    @DisplayName("portal:param mimo URL tagu je chyba v JSP")
    void should_fail_when_paramOutsideUrlTag() {
        ParamTag param = new ParamTag();
        param.setPageContext(pageContext);
        param.setName("x");
        param.setValue("y");

        assertThatThrownBy(param::doStartTag).isInstanceOf(JspException.class).hasMessageContaining("portal:param");
    }

    private void run(PortletUrlTagSupport tag, String... nameValues) throws JspException {
        tag.setPageContext(pageContext);
        tag.doStartTag();
        for (int i = 0; i < nameValues.length; i += 2) {
            ParamTag param = new ParamTag();
            param.setPageContext(pageContext);
            param.setParent(tag);
            param.setName(nameValues[i]);
            param.setValue(nameValues[i + 1]);
            param.doStartTag();
            param.doEndTag();
        }
        tag.doEndTag();
    }
}

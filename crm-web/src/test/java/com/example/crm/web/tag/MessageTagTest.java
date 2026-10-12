package com.example.crm.web.tag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTagTest {

    private final TagTestSupport page = new TagTestSupport();

    @Test
    void resolvesKeyWithArgumentsFromSpringMessageSource() throws Exception {
        MessageTag tag = tag("greeting");
        tag.doStartTag();
        MessageArgTag arg = new MessageArgTag();
        arg.setPageContext(page.pageContext);
        arg.setParent(tag);
        arg.setValue("svet");
        arg.doStartTag();
        tag.doEndTag();

        assertThat(page.output()).isEqualTo("Ahoj svet");
    }

    @Test
    void escapesHtmlInMessage() throws Exception {
        MessageTag tag = tag("html");
        tag.doStartTag();
        tag.doEndTag();

        assertThat(page.output()).isEqualTo("&lt;b&gt;tučné&lt;/b&gt;");
    }

    @Test
    void missingKeyIsVisibleInsteadOfSilent() throws Exception {
        MessageTag tag = tag("neexistuje");
        tag.doStartTag();
        tag.doEndTag();

        assertThat(page.output()).isEqualTo("??neexistuje??");
    }

    @Test
    void showMessageOnlyIfExistsSuppressesMissingKey() throws Exception {
        MessageTag tag = tag("neexistuje");
        tag.setShowMessageOnlyIfExists(true);
        tag.doStartTag();
        tag.doEndTag();

        assertThat(page.output()).isEmpty();
    }

    @Test
    void varStoresUnescapedText() throws Exception {
        MessageTag tag = tag("html");
        tag.setVar("text");
        tag.doStartTag();
        tag.doEndTag();

        assertThat(page.pageContext.getAttribute("text")).isEqualTo("<b>tučné</b>");
    }

    private MessageTag tag(String key) {
        MessageTag tag = new MessageTag();
        tag.setPageContext(page.pageContext);
        tag.setKey(key);
        return tag;
    }
}

package com.example.crm.web.tag;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclTagsTest {

    private final TagTestSupport page = new TagTestSupport();

    @Test
    void aclFilterShowsBodyOnlyWithPrivilege() throws Exception {
        page.loginAs("spravca");
        assertThat(filter("BLACKLIST_MODIFY").doStartTag()).isEqualTo(Tag.EVAL_BODY_INCLUDE);

        page.loginAs("citatel");
        assertThat(filter("BLACKLIST_MODIFY").doStartTag()).isEqualTo(Tag.SKIP_BODY);
    }

    @Test
    void aclFilterHidesBodyWithoutOperator() throws Exception {
        assertThat(filter("BLACKLIST_VIEW").doStartTag()).isEqualTo(Tag.SKIP_BODY);
    }

    @Test
    void unknownPrivilegeFailsLoudly() {
        assertThatThrownBy(() -> filter("NEEXISTUJE").doStartTag())
                .isInstanceOf(JspException.class)
                .hasMessageContaining("NEEXISTUJE");
    }

    @Test
    void pageFilterStopsPageWithoutPrivilege() throws Exception {
        page.loginAs("citatel");
        AclPageFilterTag tag = new AclPageFilterTag();
        tag.setPageContext(page.pageContext);
        tag.setPrivilegeType("BLACKLIST_MODIFY");
        tag.setWireframe("4.15.2");

        assertThat(tag.doEndTag()).isEqualTo(Tag.SKIP_PAGE);
        assertThat(page.output()).contains("Chýba BLACKLIST_MODIFY");
    }

    @Test
    void pageFilterContinuesWithPrivilege() throws Exception {
        page.loginAs("spravca");
        AclPageFilterTag tag = new AclPageFilterTag();
        tag.setPageContext(page.pageContext);
        tag.setPrivilegeType("BLACKLIST_MODIFY");

        assertThat(tag.doEndTag()).isEqualTo(Tag.EVAL_PAGE);
    }

    private AclFilterTag filter(String privilege) {
        AclFilterTag tag = new AclFilterTag();
        tag.setPageContext(page.pageContext);
        tag.setPrivilegeType(privilege);
        return tag;
    }
}

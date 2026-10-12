package com.example.crm.frontend.base.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.frontend.base.acl.AclDeniedException;
import com.example.crm.frontend.base.acl.Privilege;
import com.example.sso.api.SsoUnavailableException;
import java.util.Properties;
import javax.portlet.ResourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.mock.web.portlet.MockRenderRequest;
import org.springframework.mock.web.portlet.MockRenderResponse;
import org.springframework.mock.web.portlet.MockResourceRequest;
import org.springframework.mock.web.portlet.MockResourceResponse;
import org.springframework.web.portlet.ModelAndView;

class SimpleMappingExceptionResolverCustomTest {

    private final SimpleMappingExceptionResolverCustom resolver = new SimpleMappingExceptionResolverCustom();

    @BeforeEach
    void configureLikeInXml() {
        Properties mappings = new Properties();
        mappings.setProperty(AclDeniedException.class.getName(), "error/403");
        mappings.setProperty(NotFoundException.class.getName(), "error/404");
        mappings.setProperty(TypeMismatchException.class.getName(), "error/404");
        mappings.setProperty(SsoUnavailableException.class.getName(), "error/sso");
        resolver.setExceptionMappings(mappings);
        resolver.setDefaultErrorView("error/error");
    }

    @Test
    @DisplayName("Render: ACL výnimka → view 403 a privilégium v requeste")
    void should_render403WithPrivilege_when_aclDenied() {
        MockRenderRequest request = new MockRenderRequest();

        ModelAndView view = resolver.resolveException(request, new MockRenderResponse(), null,
                new AclDeniedException(Privilege.BLACKLIST_MODIFY));

        assertThat(view.getViewName()).isEqualTo("error/403");
        assertThat(request.getAttribute("privilege")).isEqualTo("BLACKLIST_MODIFY");
    }

    @Test
    @DisplayName("Resource: HTTP status podľa výnimky")
    void should_setHttpStatus_when_resourceFails() {
        assertThat(statusFor(new NotFoundException("x"))).isEqualTo("404");
        assertThat(statusFor(new AclDeniedException(Privilege.DEACTIVATION))).isEqualTo("403");
        assertThat(statusFor(new TypeMismatchException("abc", Long.class))).isEqualTo("400");
        assertThat(statusFor(new SsoUnavailableException("down", null))).isEqualTo("503");
        assertThat(statusFor(new IllegalStateException("bug"))).isEqualTo("500");
    }

    @Test
    @DisplayName("Neznáma výnimka v renderi → všeobecná chybová stránka")
    void should_useDefaultView_when_exceptionUnmapped() {
        ModelAndView view = resolver.resolveException(new MockRenderRequest(), new MockRenderResponse(), null,
                new IllegalStateException("bug"));

        assertThat(view.getViewName()).isEqualTo("error/error");
    }

    private String statusFor(Exception exception) {
        MockResourceResponse response = new MockResourceResponse();
        resolver.resolveException(new MockResourceRequest(), response, null, exception);
        return response.getProperty(ResourceResponse.HTTP_STATUS_CODE);
    }
}

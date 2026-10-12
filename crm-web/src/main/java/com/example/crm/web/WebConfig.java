package com.example.crm.web;

import com.example.crm.acl.AclService;
import com.example.crm.session.CrmSessions;
import com.example.crm.web.portlet.LayoutModel;
import com.example.crm.web.portlet.LayoutModelInterceptor;
import com.example.crm.web.portlet.LegacyPortletInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Náhrada per-portlet Spring kontextov – jedna konfigurácia; poradie: prihlásenie + ACL, potom layout. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CrmSessions sessions;
    private final AclService aclService;
    private final LayoutModel layout;

    public WebConfig(CrmSessions sessions, AclService aclService, LayoutModel layout) {
        this.sessions = sessions;
        this.aclService = aclService;
        this.layout = layout;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LegacyPortletInterceptor(sessions.identities(), aclService));
        registry.addInterceptor(new LayoutModelInterceptor(layout));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CrmSessionArgumentResolver(sessions));
    }
}

package com.example.crm.frontend.hook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.bean.BeanLocator;
import com.liferay.portal.kernel.bean.PortalBeanLocatorUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.service.GroupLocalService;
import com.liferay.portal.service.LayoutLocalService;
import com.liferay.portal.service.LayoutSetLocalService;
import com.liferay.portal.service.ResourcePermissionLocalService;
import com.liferay.portal.service.RoleLocalService;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalService;
import com.liferay.portal.service.permission.PortletPermission;
import com.liferay.portal.service.permission.PortletPermissionUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrmStartupActionTest {

    private static final long COMPANY = 20155L;
    private static final long GROUP = 30001L;

    // *LocalServiceUtil v 6.2 si službu nájde cez PortalBeanLocatorUtil a uloží staticky (setService je prázdny
    // deprecated) – mocky sú preto statické, podstrčené raz cez BeanLocator a pred každým testom resetované.
    private static final GroupLocalService groups = mock(GroupLocalService.class);
    private static final LayoutLocalService layouts = mock(LayoutLocalService.class);
    private static final LayoutSetLocalService layoutSets = mock(LayoutSetLocalService.class);
    private static final RoleLocalService roles = mock(RoleLocalService.class);
    private static final UserLocalService users = mock(UserLocalService.class);
    private static final ResourcePermissionLocalService permissions = mock(ResourcePermissionLocalService.class);
    private static final PortletPermission portletPermission = mock(PortletPermission.class);
    private final Group site = mock(Group.class);

    @BeforeAll
    static void injectLiferayServices() {
        BeanLocator locator = mock(BeanLocator.class);
        when(locator.locate(GroupLocalService.class.getName())).thenReturn(groups);
        when(locator.locate(LayoutLocalService.class.getName())).thenReturn(layouts);
        when(locator.locate(LayoutSetLocalService.class.getName())).thenReturn(layoutSets);
        when(locator.locate(RoleLocalService.class.getName())).thenReturn(roles);
        when(locator.locate(UserLocalService.class.getName())).thenReturn(users);
        when(locator.locate(ResourcePermissionLocalService.class.getName())).thenReturn(permissions);
        PortalBeanLocatorUtil.setBeanLocator(locator);
        new PortletPermissionUtil().setPortletPermission(portletPermission);
    }

    @BeforeEach
    void stubLiferayServices() throws Exception {
        reset(groups, layouts, layoutSets, roles, users, permissions, portletPermission);
        when(users.getDefaultUserId(COMPANY)).thenReturn(10L);
        Role guest = mock(Role.class);
        when(guest.getRoleId()).thenReturn(7L);
        when(roles.getRole(COMPANY, RoleConstants.GUEST)).thenReturn(guest);
        when(site.getGroupId()).thenReturn(GROUP);
        when(portletPermission.getPrimaryKey(anyLong(), anyString())).thenReturn("pk");
    }

    @Test
    @DisplayName("Prvý štart: vytvorí site, obe stránky s portletmi, nastaví theme a VIEW pre hosťa")
    void should_createSiteAndPages_when_missing() throws Exception {
        when(groups.addGroup(anyLong(), anyLong(), any(), anyLong(), anyLong(), eq("CRM"), anyString(), anyInt(),
                anyBoolean(), anyInt(), eq("/crm"), anyBoolean(), anyBoolean(), any(ServiceContext.class))).thenReturn(site);
        Layout layout = newLayout();
        when(layouts.addLayout(anyLong(), eq(GROUP), eq(false), anyLong(), anyString(), anyString(), anyString(),
                anyString(), eq(false), anyString(), any(ServiceContext.class))).thenReturn(layout);
        when(layouts.updateLayout(anyLong(), anyBoolean(), anyLong(), anyString())).thenReturn(layout);

        new CrmStartupAction().run(new String[] {String.valueOf(COMPANY)});

        verify(layouts).addLayout(anyLong(), eq(GROUP), eq(false), anyLong(), eq("Blacklist"), anyString(), anyString(),
                anyString(), eq(false), eq("/blacklist"), any(ServiceContext.class));
        verify(layouts).addLayout(anyLong(), eq(GROUP), eq(false), anyLong(), eq("Dashboard"), anyString(), anyString(),
                anyString(), eq(false), eq("/dashboard"), any(ServiceContext.class));
        verify((LayoutTypePortlet) layout.getLayoutType()).addPortletId(10L, "blacklist_WAR_customercareportlet",
                "column-1", -1, false);
        verify(layoutSets).updateLookAndFeel(GROUP, false, CrmStartupAction.THEME_ID, "01", "", false);
        verify(permissions, times(2)).setResourcePermissions(eq(COMPANY), anyString(),
                eq(ResourceConstants.SCOPE_INDIVIDUAL), eq("pk"), eq(7L), any(String[].class));
    }

    @Test
    @DisplayName("Ďalší štart (reštart Liferay, redeploy): nič nevytvára znova")
    void should_notDuplicate_when_siteAndPagesExist() throws Exception {
        when(groups.fetchFriendlyURLGroup(COMPANY, "/crm")).thenReturn(site);
        Layout layout = newLayout();
        when(layouts.fetchLayoutByFriendlyURL(eq(GROUP), eq(false), anyString())).thenReturn(layout);

        new CrmStartupAction().run(new String[] {String.valueOf(COMPANY)});

        verify(groups, never()).addGroup(anyLong(), anyLong(), any(), anyLong(), anyLong(), anyString(), anyString(),
                anyInt(), anyBoolean(), anyInt(), anyString(), anyBoolean(), anyBoolean(), any(ServiceContext.class));
        verify(layouts, never()).addLayout(anyLong(), anyLong(), anyBoolean(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyBoolean(), anyString(), any(ServiceContext.class));
    }

    private static Layout newLayout() {
        Layout layout = mock(Layout.class);
        LayoutTypePortlet type = mock(LayoutTypePortlet.class);
        when(layout.getLayoutType()).thenReturn(type);
        when(layout.getGroupId()).thenReturn(GROUP);
        when(layout.getPlid()).thenReturn(500L);
        when(layout.getTypeSettings()).thenReturn("layout-template-id=1_column");
        return layout;
    }
}

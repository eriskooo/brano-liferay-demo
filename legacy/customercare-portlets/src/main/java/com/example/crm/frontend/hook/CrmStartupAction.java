package com.example.crm.frontend.hook;

import com.liferay.portal.kernel.events.ActionException;
import com.liferay.portal.kernel.events.SimpleAction;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.GroupConstants;
import com.liferay.portal.model.Layout;
import com.liferay.portal.model.LayoutConstants;
import com.liferay.portal.model.LayoutTypePortlet;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.LayoutLocalServiceUtil;
import com.liferay.portal.service.LayoutSetLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.service.permission.PortletPermissionUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Startup hook ako v legacy: pri štarte portálu (a pri každom deployi WAR-u) vytvorí site, stránky a rozmiestni
 * portlety cez {@code *LocalServiceUtil}. Musí byť idempotentný. Po migrácii odpadá celý – nie je portál,
 * stránky sú URL cesty v controlleroch.
 */
public class CrmStartupAction extends SimpleAction {

    static final String SITE_NAME = "CRM";
    static final String SITE_FRIENDLY_URL = "/crm";
    static final String THEME_ID = "customercare_WAR_customercaretheme";

    private static final Log LOG = LogFactory.getLog(CrmStartupAction.class);
    private static final String[][] PAGES = {
            {"/blacklist", "Blacklist", "blacklist_WAR_customercareportlet"},
            {"/dashboard", "Dashboard", "dashboard_WAR_customercareportlet"},
    };

    @Override
    public void run(String[] companyIds) throws ActionException {
        for (String companyId : companyIds) {
            try {
                setUp(Long.parseLong(companyId));
            } catch (PortalException | SystemException e) {
                throw new ActionException("Nastavenie site CRM zlyhalo pre company " + companyId, e);
            }
        }
    }

    private void setUp(long companyId) throws PortalException, SystemException {
        long userId = UserLocalServiceUtil.getDefaultUserId(companyId);
        ServiceContext serviceContext = new ServiceContext();
        serviceContext.setCompanyId(companyId);
        serviceContext.setUserId(userId);
        serviceContext.setAddGuestPermissions(true);
        serviceContext.setAddGroupPermissions(true);

        Group site = GroupLocalServiceUtil.fetchFriendlyURLGroup(companyId, SITE_FRIENDLY_URL);
        if (site == null) {
            site = GroupLocalServiceUtil.addGroup(userId, GroupConstants.DEFAULT_PARENT_GROUP_ID, null, 0,
                    GroupConstants.DEFAULT_LIVE_GROUP_ID, SITE_NAME, "Demo legacy CRM", GroupConstants.TYPE_SITE_OPEN,
                    true, GroupConstants.DEFAULT_MEMBERSHIP_RESTRICTION, SITE_FRIENDLY_URL, true, true, serviceContext);
            LOG.info("Vytvorená site " + SITE_NAME);
        }
        LayoutSetLocalServiceUtil.updateLookAndFeel(site.getGroupId(), false, THEME_ID, "01", "", false);
        long guestRoleId = RoleLocalServiceUtil.getRole(companyId, RoleConstants.GUEST).getRoleId();
        for (String[] page : PAGES) {
            ensurePage(companyId, site, userId, guestRoleId, serviceContext, page[0], page[1], page[2]);
        }
    }

    private void ensurePage(long companyId, Group site, long userId, long guestRoleId, ServiceContext serviceContext,
                            String friendlyUrl, String name, String portletId) throws PortalException, SystemException {
        Layout layout = LayoutLocalServiceUtil.fetchLayoutByFriendlyURL(site.getGroupId(), false, friendlyUrl);
        if (layout == null) {
            layout = LayoutLocalServiceUtil.addLayout(userId, site.getGroupId(), false,
                    LayoutConstants.DEFAULT_PARENT_LAYOUT_ID, name, name, "", LayoutConstants.TYPE_PORTLET, false,
                    friendlyUrl, serviceContext);
            LayoutTypePortlet type = (LayoutTypePortlet) layout.getLayoutType();
            type.setLayoutTemplateId(userId, "1_column", false);
            type.addPortletId(userId, portletId, "column-1", -1, false);
            layout = LayoutLocalServiceUtil.updateLayout(layout.getGroupId(), layout.isPrivateLayout(),
                    layout.getLayoutId(), layout.getTypeSettings());
            LOG.info("Vytvorená stránka " + friendlyUrl + " s portletom " + portletId);
        }
        try {
            // Hosť = každý operátor (identitu pozná len SSO, nie Liferay). Pri prvom deployi ešte portlet nemusí
            // byť zaregistrovaný – vtedy to dorobí ďalší štart alebo predvolené oprávnenia portletu.
            ResourcePermissionLocalServiceUtil.setResourcePermissions(companyId, portletId,
                    ResourceConstants.SCOPE_INDIVIDUAL, PortletPermissionUtil.getPrimaryKey(layout.getPlid(), portletId),
                    guestRoleId, new String[] {ActionKeys.VIEW});
        } catch (PortalException | SystemException | RuntimeException e) {
            LOG.warn("Oprávnenie VIEW pre hosťa na " + portletId + " sa nepodarilo nastaviť: " + e.getMessage());
        }
    }
}

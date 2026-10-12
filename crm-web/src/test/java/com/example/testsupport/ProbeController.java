package com.example.testsupport;

import com.example.crm.acl.Privilege;
import com.example.crm.acl.RequiresPrivilege;
import com.example.crm.session.CrmSession;
import com.example.crm.web.portlet.LegacyPortlet;
import com.example.crm.web.portlet.PortletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Testovací „portlet“ na overenie interceptorov bez závislosti na skutočných obrazovkách. */
@Controller
@LegacyPortlet("/probe")
@RequestMapping("/probe")
public class ProbeController {

    @GetMapping("/open")
    @ResponseBody
    public String open(HttpServletRequest request) {
        return (String) request.getAttribute(PortletRequestAttributes.BASE_PATH);
    }

    @GetMapping("/modify")
    @ResponseBody
    @RequiresPrivilege(Privilege.BLACKLIST_MODIFY)
    public String modify() {
        return "ok";
    }

    @PostMapping("/notify")
    public String notifyAndRedirect(CrmSession session) {
        session.pushInfo("blacklist.detail.changed");
        return "redirect:/probe/page";
    }

    @GetMapping("/page")
    public String page() {
        return "error/404";
    }
}

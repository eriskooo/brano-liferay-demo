<%@ include file="/WEB-INF/jsp/init.jspf" %>
<div class="crm-login-required">
    <p><common:message key="common.loginRequired"/></p>
    <a id="loginLink" class="btn btn-primary" href="${fn:escapeXml(loginUrl)}"><common:message key="common.login"/></a>
</div>

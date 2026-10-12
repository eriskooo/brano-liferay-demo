<%@ include file="/WEB-INF/jsp/init.jspf" %>
<portal:renderURL var="homeUrl"/>
<div class="portlet-msg-error crm-error-404">
    <common:message key="error.404"/>
    <a href="${homeUrl}"><common:message key="common.back"/></a>
</div>

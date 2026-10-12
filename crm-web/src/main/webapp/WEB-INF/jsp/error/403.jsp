<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="error.403.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<h1><common:message key="error.403.title"/></h1>
<p class="crm-error-403"><common:message key="error.403.text"/> <code><c:out value="${privilege}"/></code></p>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

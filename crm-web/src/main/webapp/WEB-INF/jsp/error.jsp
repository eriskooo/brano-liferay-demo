<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="error.generic.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<h1><common:message key="error.generic.title"/> <c:out value="${status}"/></h1>
<p><c:out value="${error}"/></p>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

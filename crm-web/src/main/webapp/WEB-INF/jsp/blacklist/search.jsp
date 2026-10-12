<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="blacklist.search.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<common:aclPageFilterTag privilegeType="BLACKLIST_VIEW" wireframe="blacklist-search"/>
<h1><common:message key="blacklist.search.title"/></h1>

<%-- Rovnaký zápis ako v legacy JSP: URL vzniká z parametrov, tag ju preloží na /blacklist/searchAction. --%>
<portal:actionURL var="searchUrl">
    <portal:param name="action" value="searchAction"/>
</portal:actionURL>

<form:form modelAttribute="searchCriteria" action="${searchUrl}" method="post" cssClass="panel">
    <form:errors path="anyCriterionFilled" cssClass="field-error" element="p"/>
    <label><common:message key="blacklist.field.firstName"/> <form:input path="firstName"/></label>
    <label><common:message key="blacklist.field.lastName"/> <form:input path="lastName"/></label>
    <label><common:message key="blacklist.field.identifier"/> <form:input path="identifierValue"/></label>
    <label><common:message key="blacklist.field.state"/>
        <form:select path="state">
            <form:option value=""><common:message key="common.any"/></form:option>
            <c:forEach var="state" items="${states}">
                <form:option value="${state}"><common:message key="blacklist.state.${state}"/></form:option>
            </c:forEach>
        </form:select>
    </label>
    <div class="actions">
        <button type="submit" class="primary btn btn-primary"><common:message key="common.search"/></button>
        <portal:renderURL var="newSearchUrl"><portal:param name="render" value="newSearch"/></portal:renderURL>
        <a href="${newSearchUrl}"><common:message key="blacklist.search.clear"/></a>
        <common:aclFilter privilegeType="BLACKLIST_MODIFY">
            <portal:renderURL var="createUrl"><portal:param name="render" value="create"/></portal:renderURL>
            <a href="${createUrl}" class="button"><common:message key="blacklist.create.title"/></a>
        </common:aclFilter>
    </div>
</form:form>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

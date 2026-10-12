<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="blacklist.result.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<common:aclPageFilterTag privilegeType="BLACKLIST_VIEW" wireframe="blacklist-result"/>
<h1><common:message key="blacklist.result.title"/></h1>
<table class="grid table table-striped table-bordered">
    <thead>
    <tr>
        <th><common:message key="blacklist.field.lastName"/>, <common:message key="blacklist.field.firstName"/></th>
        <th><common:message key="blacklist.field.birthDate"/></th>
        <th><common:message key="blacklist.detail.identifiers"/></th>
        <th><common:message key="blacklist.field.state"/></th>
        <th><common:message key="blacklist.field.stateReason"/></th>
        <th></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach var="record" items="${results}">
        <tr>
            <td><c:out value="${record.fullName}"/></td>
            <td>${record.birthDate}</td>
            <td><c:forEach var="identifier" items="${record.identifiers}" varStatus="loop"><c:out value="${identifier.value}"/><c:if test="${not loop.last}">, </c:if></c:forEach></td>
            <td><common:message key="blacklist.state.${record.state}"/></td>
            <td><common:message key="blacklist.reason.${record.stateReason}"/></td>
            <td>
                <portal:renderURL var="detailUrl">
                    <portal:param name="render" value="detail"/>
                    <portal:param name="detailId" value="${record.id}"/>
                </portal:renderURL>
                <a href="${detailUrl}"><common:message key="common.detail"/></a>
            </td>
        </tr>
    </c:forEach>
    </tbody>
</table>
<portal:renderURL var="backUrl"><portal:param name="render" value="search"/></portal:renderURL>
<p><a href="${backUrl}"><common:message key="blacklist.result.backToSearch"/></a></p>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

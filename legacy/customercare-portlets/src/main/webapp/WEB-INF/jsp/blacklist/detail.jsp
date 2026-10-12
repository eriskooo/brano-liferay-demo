<%@ include file="/WEB-INF/jsp/init.jspf" %>
<portal:renderURL var="backUrl"><portal:param name="viewPage" value="searchresult"/></portal:renderURL>
<portal:actionURL var="changeUrl">
    <portal:param name="viewPage" value="detail"/>
    <portal:param name="action" value="changeAction"/>
</portal:actionURL>
<portal:actionURL var="noteUrl">
    <portal:param name="viewPage" value="detail"/>
    <portal:param name="action" value="addNoteAction"/>
</portal:actionURL>

<h2><common:message key="blacklist.detail.title"/>: <c:out value="${record.fullName}"/></h2>
<div id="crm-ajax-error"></div>
<table id="recordSummary">
    <tr><th><common:message key="blacklist.result.birthDate"/></th><td>${record.birthDate}</td></tr>
    <tr><th><common:message key="blacklist.result.state"/></th>
        <td id="recordState"><common:message key="blacklist.state.${record.state}"/></td></tr>
    <tr><th><common:message key="blacklist.detail.reason"/></th>
        <td><common:message key="blacklist.reason.${record.stateReason}"/></td></tr>
    <tr><th><common:message key="blacklist.detail.condition"/></th>
        <td><common:message key="blacklist.condition.${record.conditionType}"/></td></tr>
    <tr><th><common:message key="blacklist.detail.validFrom"/></th><td>${record.validFrom}</td></tr>
    <tr><th><common:message key="blacklist.detail.validTo"/></th>
        <td id="recordValidTo">
            <c:choose>
                <c:when test="${empty record.validTo}"><common:message key="blacklist.detail.unlimited"/></c:when>
                <c:otherwise>${record.validTo}</c:otherwise>
            </c:choose>
        </td></tr>
</table>

<h3><common:message key="blacklist.detail.identifiers"/></h3>
<c:set var="identifiers" value="${record.identifiers}"/>
<c:set var="detailId" value="${record.id}"/>
<div id="identifiers"><%@ include file="/WEB-INF/jsp/blacklist/fragments/identifiers-list.jspf" %></div>

<h3><common:message key="blacklist.detail.notes"/></h3>
<c:choose>
    <c:when test="${empty record.notes}"><p><common:message key="blacklist.detail.noNotes"/></p></c:when>
    <c:otherwise>
        <ul id="notes">
            <c:forEach items="${record.notes}" var="n">
                <li>${n.createdAt} <c:out value="${n.author}"/>: <c:out value="${n.text}"/></li>
            </c:forEach>
        </ul>
    </c:otherwise>
</c:choose>

<common:aclFilter privilegeType="BLACKLIST_MODIFY">
    <form id="noteForm" method="post" action="${noteUrl}">
        <input type="hidden" name="detailId" value="${record.id}"/>
        <textarea name="text" rows="2" cols="60"></textarea>
        <button type="submit"><common:message key="blacklist.detail.addNote"/></button>
    </form>
    <fieldset>
        <legend><common:message key="blacklist.detail.change"/></legend>
        <form id="changeForm" method="post" action="${changeUrl}">
            <input type="hidden" name="detailId" value="${record.id}"/>
            <label for="state"><common:message key="blacklist.result.state"/></label>
            <select id="state" name="state">
                <c:forEach items="${states}" var="s">
                    <option value="${s}" ${s == record.state ? 'selected' : ''}><common:message key="blacklist.state.${s}"/></option>
                </c:forEach>
            </select>
            <label for="validTo"><common:message key="blacklist.detail.validTo"/></label>
            <input id="validTo" type="date" name="validTo" value="${record.validTo}"/>
            <button type="submit"><common:message key="blacklist.detail.save"/></button>
        </form>
    </fieldset>
</common:aclFilter>
<a href="${backUrl}"><common:message key="common.back"/></a>

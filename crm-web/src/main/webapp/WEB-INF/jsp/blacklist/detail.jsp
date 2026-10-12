<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="blacklist.detail.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<common:aclPageFilterTag privilegeType="BLACKLIST_VIEW" wireframe="blacklist-detail"/>
<%-- Telo stránky je 1:1 legacy detail.jsp. Rozdiely: detailId ide v URL akcie (controller ho berie ako parameter)
     a formuláre sú form:form, aby sa pri validačnej chybe zobrazili chyby pri poliach. --%>
<portal:renderURL var="backUrl"><portal:param name="viewPage" value="searchresult"/></portal:renderURL>
<portal:actionURL var="changeUrl" escapeXml="false">
    <portal:param name="viewPage" value="detail"/>
    <portal:param name="action" value="changeAction"/>
    <portal:param name="detailId" value="${record.id}"/>
</portal:actionURL>
<portal:actionURL var="noteUrl" escapeXml="false">
    <portal:param name="viewPage" value="detail"/>
    <portal:param name="action" value="addNoteAction"/>
    <portal:param name="detailId" value="${record.id}"/>
</portal:actionURL>

<h2><common:message key="blacklist.detail.title"/>: <c:out value="${record.fullName}"/></h2>
<div id="crm-ajax-error"></div>
<table id="recordSummary" class="table table-striped table-bordered">
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
<%-- Ten istý fragment vracia AJAX endpoint – stránka aj resource fáza zdieľajú jeden JSP (obal div#identifiers je v ňom). --%>
<jsp:include page="/WEB-INF/jsp/blacklist/fragments/identifiers.jsp"/>

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
    <form:form id="noteForm" method="post" action="${noteUrl}" modelAttribute="noteForm">
        <form:textarea path="text" rows="2" cols="60"/>
        <button type="submit" class="btn"><common:message key="blacklist.detail.addNote"/></button>
        <form:errors path="text" cssClass="field-error"/>
    </form:form>
    <fieldset>
        <legend><common:message key="blacklist.detail.change"/></legend>
        <form:form id="changeForm" method="post" action="${changeUrl}" modelAttribute="changeForm">
            <label for="state"><common:message key="blacklist.result.state"/></label>
            <form:select path="state">
                <c:forEach items="${states}" var="s">
                    <form:option value="${s}"><common:message key="blacklist.state.${s}"/></form:option>
                </c:forEach>
            </form:select>
            <form:errors path="state" cssClass="field-error"/>
            <label for="validTo"><common:message key="blacklist.detail.validTo"/></label>
            <form:input path="validTo" type="date"/>
            <form:errors path="validTo" cssClass="field-error"/>
            <button type="submit" class="btn"><common:message key="blacklist.detail.save"/></button>
        </form:form>
    </fieldset>
</common:aclFilter>
<a href="${backUrl}"><common:message key="common.back"/></a>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

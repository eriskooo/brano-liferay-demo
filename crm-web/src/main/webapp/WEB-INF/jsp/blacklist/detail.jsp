<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="blacklist.detail.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<common:aclPageFilterTag privilegeType="BLACKLIST_VIEW" wireframe="blacklist-detail"/>
<h1><common:message key="blacklist.detail.title"/>: <c:out value="${record.fullName}"/></h1>

<dl class="panel">
    <dt><common:message key="blacklist.field.birthDate"/></dt><dd>${record.birthDate}</dd>
    <dt><common:message key="blacklist.field.state"/></dt><dd><common:message key="blacklist.state.${record.state}"/></dd>
    <dt><common:message key="blacklist.field.stateReason"/></dt><dd><common:message key="blacklist.reason.${record.stateReason}"/></dd>
    <dt><common:message key="blacklist.field.conditionType"/></dt><dd><common:message key="blacklist.condition.${record.conditionType}"/></dd>
    <dt><common:message key="blacklist.field.validFrom"/></dt><dd>${record.validFrom}</dd>
    <dt><common:message key="blacklist.field.validTo"/></dt>
    <dd><c:choose><c:when test="${empty record.validTo}"><common:message key="common.unlimited"/></c:when><c:otherwise>${record.validTo}</c:otherwise></c:choose></dd>
</dl>

<h2><common:message key="blacklist.detail.identifiers"/></h2>
<%-- Ten istý fragment vracia AJAX endpoint – stránka aj resource fáza zdieľajú jeden JSP. --%>
<jsp:include page="/WEB-INF/jsp/blacklist/fragments/identifiers.jsp"/>

<common:aclFilter privilegeType="BLACKLIST_MODIFY">
    <h2><common:message key="blacklist.detail.change"/></h2>
    <portal:actionURL var="changeUrl">
        <portal:param name="action" value="changeAction"/>
        <portal:param name="detailId" value="${record.id}"/>
    </portal:actionURL>
    <form:form modelAttribute="changeForm" action="${changeUrl}" method="post" cssClass="panel">
        <label><common:message key="blacklist.field.state"/>
            <form:select path="state">
                <c:forEach var="state" items="${states}">
                    <form:option value="${state}"><common:message key="blacklist.state.${state}"/></form:option>
                </c:forEach>
            </form:select>
        </label>
        <form:errors path="state" cssClass="field-error"/>
        <label><common:message key="blacklist.field.validTo"/> <form:input path="validTo" type="date"/></label>
        <form:errors path="validTo" cssClass="field-error"/>
        <p class="hint"><common:message key="blacklist.detail.deactivateHint"/></p>
        <button type="submit" class="primary btn btn-primary"><common:message key="common.save"/></button>
    </form:form>
</common:aclFilter>

<h2><common:message key="blacklist.detail.notes"/></h2>
<ul class="plain">
    <c:forEach var="note" items="${record.notes}">
        <li><strong><c:out value="${note.author}"/></strong> (${note.createdAt}): <c:out value="${note.text}"/></li>
    </c:forEach>
</ul>
<common:aclFilter privilegeType="BLACKLIST_MODIFY">
    <portal:actionURL var="noteUrl">
        <portal:param name="action" value="addNoteAction"/>
        <portal:param name="detailId" value="${record.id}"/>
    </portal:actionURL>
    <form:form modelAttribute="noteForm" action="${noteUrl}" method="post" cssClass="panel">
        <form:textarea path="text" rows="3" cols="60"/>
        <form:errors path="text" cssClass="field-error"/>
        <div class="actions"><button type="submit" class="btn"><common:message key="blacklist.detail.addNote"/></button></div>
    </form:form>
</common:aclFilter>

<portal:renderURL var="resultUrl"><portal:param name="render" value="searchresult"/></portal:renderURL>
<p><a href="${resultUrl}"><common:message key="common.back"/></a></p>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

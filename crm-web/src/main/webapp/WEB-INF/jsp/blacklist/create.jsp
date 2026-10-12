<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titleKey" value="blacklist.create.title"/>
<%@ include file="/WEB-INF/jsp/layout/header.jspf" %>
<common:aclPageFilterTag privilegeType="BLACKLIST_MODIFY" wireframe="blacklist-create"/>
<h1><common:message key="blacklist.create.title"/></h1>

<portal:actionURL var="createUrl"><portal:param name="action" value="createAction"/></portal:actionURL>
<portal:resourceURL var="conditionTypesUrl" id="refreshCreateConditionTypes"/>

<form:form modelAttribute="createForm" action="${createUrl}" method="post" cssClass="panel">
    <fieldset>
        <legend><common:message key="blacklist.create.person"/></legend>
        <label><common:message key="blacklist.field.firstName"/> <form:input path="firstName"/></label>
        <form:errors path="firstName" cssClass="field-error"/>
        <label><common:message key="blacklist.field.lastName"/> <form:input path="lastName"/></label>
        <form:errors path="lastName" cssClass="field-error"/>
        <label><common:message key="blacklist.field.birthDate"/> <form:input path="birthDate" type="date"/></label>
        <form:errors path="birthDate" cssClass="field-error"/>
    </fieldset>

    <fieldset>
        <legend><common:message key="blacklist.create.identifier"/></legend>
        <label><common:message key="blacklist.field.identifierType"/>
            <form:select path="identifierType">
                <form:option value="">&mdash;</form:option>
                <c:forEach var="type" items="${identifierTypes}">
                    <form:option value="${type}"><common:message key="blacklist.identType.${type}"/></form:option>
                </c:forEach>
            </form:select>
        </label>
        <form:errors path="identifierType" cssClass="field-error"/>
        <label><common:message key="blacklist.field.identifier"/> <form:input path="identifierValue"/></label>
        <form:errors path="identifierValue" cssClass="field-error"/>
        <form:errors path="identifierValid" cssClass="field-error"/>
    </fieldset>

    <fieldset>
        <legend><common:message key="blacklist.create.classification"/></legend>
        <label><common:message key="blacklist.field.stateReason"/>
            <%-- data-ajax-refresh: náhrada legacy JS, ktorý volal resource URL portletu --%>
            <form:select path="stateReason" data-ajax-refresh="${conditionTypesUrl}" data-ajax-target="#conditionTypes">
                <form:option value="">&mdash;</form:option>
                <c:forEach var="reason" items="${stateReasons}">
                    <form:option value="${reason}"><common:message key="blacklist.reason.${reason}"/></form:option>
                </c:forEach>
            </form:select>
        </label>
        <form:errors path="stateReason" cssClass="field-error"/>
        <label><common:message key="blacklist.field.conditionType"/>
            <jsp:include page="/WEB-INF/jsp/blacklist/fragments/conditionTypes.jsp"/>
        </label>
        <form:errors path="conditionType" cssClass="field-error"/>
        <label><common:message key="blacklist.field.validFrom"/> <form:input path="validFrom" type="date"/></label>
        <form:errors path="validFrom" cssClass="field-error"/>
        <label><common:message key="blacklist.field.validTo"/> <form:input path="validTo" type="date"/></label>
        <form:errors path="validTo" cssClass="field-error"/>
        <form:errors path="periodValid" cssClass="field-error"/>
    </fieldset>

    <div class="actions">
        <button type="submit" class="primary btn btn-primary"><common:message key="common.save"/></button>
        <portal:renderURL var="backUrl"><portal:param name="render" value="search"/></portal:renderURL>
        <a href="${backUrl}"><common:message key="common.back"/></a>
    </div>
</form:form>
<%@ include file="/WEB-INF/jsp/layout/footer.jspf" %>

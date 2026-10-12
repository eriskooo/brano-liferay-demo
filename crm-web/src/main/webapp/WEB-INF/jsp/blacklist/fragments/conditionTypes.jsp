<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<%-- Obyčajný <select> (nie form:select): fragment sa vracia aj samostatne cez AJAX, mimo <form:form>. --%>
<select name="conditionType" id="conditionTypes">
    <option value="">&mdash;</option>
    <c:forEach var="condition" items="${conditionTypes}">
        <option value="${condition}" ${condition == createForm.conditionType ? 'selected' : ''}><common:message key="blacklist.condition.${condition}"/></option>
    </c:forEach>
</select>

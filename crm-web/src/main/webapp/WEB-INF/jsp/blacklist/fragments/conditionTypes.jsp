<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<%-- Select podmienok 1:1 ako legacy conditionTypes-select.jspf: vo formulári (include) aj ako AJAX fragment.
     Obyčajný <select>, lebo fragment resource fázy nemá kontext <form:form>. --%>
<select id="conditionType" name="conditionType">
    <option value=""><common:message key="blacklist.create.choose"/></option>
    <c:forEach items="${conditionTypes}" var="t">
        <option value="${t}" ${t == createForm.conditionType ? 'selected' : ''}><common:message key="blacklist.condition.${t}"/></option>
    </c:forEach>
</select>

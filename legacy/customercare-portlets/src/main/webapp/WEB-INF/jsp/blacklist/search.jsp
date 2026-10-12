<%@ include file="/WEB-INF/jsp/init.jspf" %>
<%-- URL pre Spring form tagy bez escapovania: <form:form> hodnotu atribútu escapuje sám (inak &amp;amp;). --%>
<portal:actionURL var="searchUrl" escapeXml="false"><portal:param name="action" value="searchAction"/></portal:actionURL>
<portal:actionURL var="clearUrl"><portal:param name="action" value="clearFieldsAction"/></portal:actionURL>
<portal:actionURL var="gotoCreateUrl"><portal:param name="action" value="gotoCreateAction"/></portal:actionURL>

<h2><common:message key="blacklist.search.title"/></h2>
<form:form id="searchForm" method="post" action="${searchUrl}" modelAttribute="searchCriteria">
    <form:errors path="*" cssClass="field-error" element="div"/>
    <div>
        <label for="firstName"><common:message key="blacklist.search.firstName"/></label>
        <form:input path="firstName"/>
    </div>
    <div>
        <label for="lastName"><common:message key="blacklist.search.lastName"/></label>
        <form:input path="lastName"/>
    </div>
    <div>
        <label for="identifierValue"><common:message key="blacklist.search.identifier"/></label>
        <form:input path="identifierValue"/>
    </div>
    <div>
        <label for="state"><common:message key="blacklist.search.state"/></label>
        <form:select path="state">
            <form:option value=""><common:message key="blacklist.search.anyState"/></form:option>
            <c:forEach items="${states}" var="s">
                <form:option value="${s}"><common:message key="blacklist.state.${s}"/></form:option>
            </c:forEach>
        </form:select>
    </div>
    <button type="submit"><common:message key="blacklist.search.submit"/></button>
</form:form>
<form id="clearForm" method="post" action="${clearUrl}" class="crm-inline">
    <button type="submit"><common:message key="blacklist.search.clear"/></button>
</form>
<common:aclFilter privilegeType="BLACKLIST_MODIFY">
    <form id="gotoCreateForm" method="post" action="${gotoCreateUrl}" class="crm-inline">
        <button type="submit"><common:message key="blacklist.search.create"/></button>
    </form>
</common:aclFilter>

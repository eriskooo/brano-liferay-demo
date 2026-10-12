<%@ include file="/WEB-INF/jsp/init.jspf" %>
<%-- URL pre Spring form tagy bez escapovania: <form:form> hodnotu atribútu escapuje sám (inak &amp;amp;). --%>
<portal:actionURL var="submitUrl" escapeXml="false"><portal:param name="action" value="submit"/></portal:actionURL>
<portal:resourceURL id="earliestDate" var="earliestUrl" escapeXml="false"/>
<portal:renderURL var="productsUrl"/>

<h2><common:message key="dashboard.deactivation.title"/></h2>
<p><common:message key="dashboard.deactivation.product"/>: <strong id="deactivationProduct"><c:out value="${product.name}"/></strong></p>
<div id="crm-ajax-error"></div>
<form:form id="deactivationForm" method="post" action="${submitUrl}" modelAttribute="deactivationForm"
           data-earliest-url="${earliestUrl}">
    <form:hidden path="productId"/>
    <fieldset>
        <legend><common:message key="dashboard.deactivation.type"/></legend>
        <c:forEach items="${deactivationTypes}" var="t">
            <label class="radio-label"><form:radiobutton path="type" value="${t}" onchange="Crm.earliestDate(this.form)"/>
                <common:message key="dashboard.deactivationType.${t}"/></label><br/>
        </c:forEach>
        <form:errors path="type" cssClass="field-error"/>
    </fieldset>
    <div>
        <label for="noticeDate"><common:message key="dashboard.deactivation.noticeDate"/></label>
        <form:input path="noticeDate" type="date" onchange="Crm.earliestDate(this.form)"/>
        <form:errors path="noticeDate" cssClass="field-error"/>
    </div>
    <div>
        <label for="effectiveDate"><common:message key="dashboard.deactivation.effectiveDate"/></label>
        <form:input path="effectiveDate" type="date"/>
        <common:message key="dashboard.deactivation.earliest"/> <span id="earliestHint">${earliestDate}</span>
        <form:errors path="effectiveDate" cssClass="field-error"/>
    </div>
    <button type="submit" class="btn btn-primary"><common:message key="dashboard.deactivation.submit"/></button>
</form:form>
<a href="${productsUrl}"><common:message key="dashboard.deactivation.backToProducts"/></a>

<%@ include file="/WEB-INF/jsp/init.jspf" %>
<portal:renderURL var="productsUrl"/>
<h2><common:message key="dashboard.deactivation.title"/></h2>
<p id="deactivationDone">
    <common:message key="dashboard.deactivation.done">
        <common:msgarg value="${requestNumber}"/>
        <common:msgarg value="${deactivationRequest.productName}"/>
        <common:msgarg value="${deactivationRequest.effectiveDate}"/>
    </common:message>
</p>
<a href="${productsUrl}"><common:message key="dashboard.deactivation.backToProducts"/></a>

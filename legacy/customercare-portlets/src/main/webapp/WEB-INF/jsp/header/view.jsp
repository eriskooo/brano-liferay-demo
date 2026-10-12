<%@ include file="/WEB-INF/jsp/init.jspf" %>
<div class="crm-header">
    <c:choose>
        <c:when test="${empty operator}">
            <span><common:message key="common.loginRequired"/></span>
            <a id="headerLogin" class="crm-button" href="${fn:escapeXml(loginUrl)}"><common:message key="common.login"/></a>
        </c:when>
        <c:otherwise>
            <span id="headerOperator"><common:message key="common.operator"/>: <c:out value="${operator.displayName}"/></span>
            <span id="headerCustomer"><common:message key="common.customer"/>:
                <c:choose>
                    <c:when test="${empty customer}"><common:message key="common.noCustomer"/></c:when>
                    <c:otherwise><c:out value="${customer.displayName}"/> (<c:out value="${customer.customerId}"/>)</c:otherwise>
                </c:choose>
            </span>
            <c:if test="${not empty lastViewed}">
                <span id="headerLastViewed"><common:message key="common.lastViewed"/>: <c:out value="${lastViewed.fullName}"/></span>
            </c:if>
            <a id="changeCustomer" href="${fn:escapeXml(changeCustomerUrl)}"><common:message key="common.changeCustomer"/></a>
            <form id="logoutForm" method="post" action="/sso/logout" class="crm-inline">
                <button type="submit"><common:message key="common.logout"/></button>
            </form>
        </c:otherwise>
    </c:choose>
    <%-- Len pre demo zdieľanej session: odkiaľ fasáda práve číta a pod akým kľúčom je hash v Redise. --%>
    <div id="sessionDebug" class="crm-session-debug">
        <common:message key="common.sessionDebug"/>: <common:message key="common.readSource"/> = <c:out value="${readSource}"/>,
        <common:message key="common.ssoSession"/> = <c:out value="${empty ssoId ? '-' : ssoId}"/>
    </div>
    <c:forEach items="${messages}" var="m">
        <div class="${m.level == 'ERROR' ? 'portlet-msg-error' : 'portlet-msg-success'} crm-message">
            <common:message key="${m.key}"/>
        </div>
    </c:forEach>
</div>

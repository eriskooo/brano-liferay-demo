<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<%-- Zoznam identifikátorov 1:1 ako legacy identifiers-list.jspf. Legacy volal Crm.removeIdentifier cez onclick;
     tu ten istý odkaz nesie data-ajax-post a obsluhuje ho crm.js (odpoveď nahradí celý div#identifiers). --%>
<div id="identifiers">
<c:if test="${not empty errorKey}">
    <div class="portlet-msg-error"><common:message key="${errorKey}"/></div>
</c:if>
<ul>
    <c:forEach items="${identifiers}" var="i">
        <li>
            <common:message key="blacklist.identifier.${i.type}"/>: <c:out value="${i.value}"/>
            <common:aclFilter privilegeType="BLACKLIST_MODIFY">
                <portal:resourceURL id="removeIdentAction" var="removeUrl">
                    <portal:param name="detailId" value="${detailId}"/>
                    <portal:param name="identId" value="${i.id}"/>
                </portal:resourceURL>
                <a id="remove-ident-${i.id}" href="#" data-ajax-post="${removeUrl}" data-ajax-target="#identifiers"><common:message key="blacklist.detail.removeIdentifier"/></a>
            </common:aclFilter>
        </li>
    </c:forEach>
</ul>
</div>

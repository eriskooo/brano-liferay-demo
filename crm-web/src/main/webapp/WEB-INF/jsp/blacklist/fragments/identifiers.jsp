<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<div id="identifiers">
    <c:if test="${not empty errorKey}"><p class="field-error"><common:message key="${errorKey}"/></p></c:if>
    <ul class="plain">
        <c:forEach var="identifier" items="${identifiers}">
            <li>
                <common:message key="blacklist.identType.${identifier.type}"/>: <c:out value="${identifier.value}"/>
                <common:aclFilter privilegeType="BLACKLIST_MODIFY">
                    <portal:resourceURL var="removeUrl" id="removeIdentAction">
                        <portal:param name="detailId" value="${detailId}"/>
                        <portal:param name="identId" value="${identifier.id}"/>
                    </portal:resourceURL>
                    <button type="button" class="link" data-ajax-post="${removeUrl}" data-ajax-target="#identifiers"><common:message key="common.remove"/></button>
                </common:aclFilter>
            </li>
        </c:forEach>
    </ul>
</div>

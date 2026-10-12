<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%-- Hook custom-jsp-dir: portálová stránka chýb (neexistujúca stránka, chýbajúce oprávnenie). Po migrácii ju
     nahradí chybová stránka Spring Boot – portál už nebude. --%>
<div class="crm-portal-error" id="crmPortalError">
    <h2>Stránka nie je dostupná</h2>
    <p>Požadovaná stránka neexistuje alebo k nej nemáte prístup.</p>
    <p><a href="/web/crm/blacklist">Späť do CRM</a></p>
</div>

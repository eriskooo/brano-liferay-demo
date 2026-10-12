/*
 * AJAX volania resource fázy portletov. URL dodáva JSP v data-url (tag portal:resourceURL), JS ich neskladá.
 * Legacy používa jQuery; demo drží závislosti minimálne (čistý XMLHttpRequest, beží aj v starom prehliadači).
 */
var Crm = window.Crm || {};

Crm.request = function (method, url, onSuccess) {
    var xhr = new XMLHttpRequest();
    xhr.open(method, url, true);
    xhr.onreadystatechange = function () {
        if (xhr.readyState !== 4) {
            return;
        }
        if (xhr.status === 200) {
            onSuccess(xhr.responseText);
        } else if (xhr.status === 401) {
            // SSO session vypršala – znovunačítanie stránky ukáže výzvu na prihlásenie.
            window.location.reload();
        } else {
            var box = document.getElementById('crm-ajax-error');
            if (box) {
                box.textContent = 'Chyba ' + xhr.status;
                box.style.display = 'block';
            }
        }
    };
    xhr.send(null);
};

Crm.withParams = function (url, params) {
    var query = [];
    for (var name in params) {
        if (params.hasOwnProperty(name)) {
            query.push(encodeURIComponent(name) + '=' + encodeURIComponent(params[name]));
        }
    }
    return url + (url.indexOf('?') < 0 ? '?' : '&') + query.join('&');
};

/** Odstránenie identifikátora: POST na resource URL, odpoveď je HTML fragment zoznamu. */
Crm.removeIdentifier = function (link) {
    Crm.request('POST', link.getAttribute('data-url'), function (html) {
        document.getElementById(link.getAttribute('data-target')).innerHTML = html;
    });
    return false;
};

/** Povolené podmienky podľa dôvodu: GET, odpoveď je HTML fragment <select>. */
Crm.refreshConditionTypes = function (select) {
    var url = Crm.withParams(select.getAttribute('data-url'), {stateReason: select.value});
    Crm.request('GET', url, function (html) {
        document.getElementById(select.getAttribute('data-target')).innerHTML = html;
    });
};

/** Najskorší dátum účinnosti: GET, odpoveď je JSON {"earliestDate":"RRRR-MM-DD"}. */
Crm.earliestDate = function (form) {
    var type = form.querySelector('[name=type]:checked') || form.querySelector('[name=type]');
    var notice = form.querySelector('[name=noticeDate]');
    var effective = form.querySelector('[name=effectiveDate]');
    if (!type || !type.value || !notice.value) {
        return;
    }
    var url = Crm.withParams(form.getAttribute('data-earliest-url'), {type: type.value, noticeDate: notice.value});
    Crm.request('GET', url, function (json) {
        var earliest = JSON.parse(json).earliestDate;
        effective.min = earliest;
        if (!effective.value || effective.value < earliest) {
            effective.value = earliest;
        }
        document.getElementById('earliestHint').textContent = earliest;
    });
};

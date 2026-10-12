// Náhrada portletových AJAX volaní (resource fáza). Legacy JS volal resource URL portletu;
// tu volá bežný HTTP endpoint. Hlavička X-Requested-With hovorí serveru, že nechceme celú stránku
// (chyby vráti bez layoutu: 401 / 403).
const AJAX_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' };

async function replaceWithFragment(url, options, targetSelector) {
  const response = await fetch(url, { ...options, headers: { ...AJAX_HEADERS, ...(options.headers || {}) } });
  if (response.status === 401) { window.location.reload(); return; }
  document.querySelector(targetSelector).outerHTML = await response.text();
}

// <button data-ajax-post="URL" data-ajax-target="#id"> – POST na resource URL, odpoveď (HTML fragment) nahradí cieľ.
document.addEventListener('click', (event) => {
  const button = event.target.closest('[data-ajax-post]');
  if (!button) return;
  event.preventDefault();
  replaceWithFragment(button.dataset.ajaxPost, { method: 'POST' }, button.dataset.ajaxTarget);
});

// <select data-ajax-refresh="URL" data-ajax-target="#id"> – pri zmene GET s hodnotou poľa, fragment nahradí cieľ.
document.addEventListener('change', (event) => {
  const field = event.target.closest('[data-ajax-refresh]');
  if (!field) return;
  const url = new URL(field.dataset.ajaxRefresh, window.location.href);
  url.searchParams.set(field.name, field.value);
  replaceWithFragment(url, { method: 'GET' }, field.dataset.ajaxTarget);
});

// <form data-earliest-date-url="URL"> – JSON endpoint vráti najskorší dátum „ku dňu“ podľa typu a dátumu podania.
document.addEventListener('change', async (event) => {
  const form = event.target.closest('form[data-earliest-date-url]');
  if (!form) return;
  const type = form.querySelector('input[name="type"]:checked');
  const notice = form.querySelector('input[name="noticeDate"]');
  if (!type || !notice.value) return;
  const url = new URL(form.dataset.earliestDateUrl, window.location.href);
  url.searchParams.set('type', type.value);
  url.searchParams.set('noticeDate', notice.value);
  const response = await fetch(url, { headers: { ...AJAX_HEADERS, Accept: 'application/json' } });
  if (!response.ok) return;
  const { earliestDate } = await response.json();
  const effective = form.querySelector('input[name="effectiveDate"]');
  effective.min = earliestDate;
  if (!effective.value || effective.value < earliestDate) effective.value = earliestDate;
});

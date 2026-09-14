/**
 * Тёплая Компания 4.0 — Google Apps Script API skeleton.
 * Не включён в Alpha 6 Android runtime; подготовлен как следующий этап.
 */
const API_VERSION = 'tk4-v1';
const DEFAULT_SPREADSHEET_ID = '1vTdo0kJmHQP-N4JOnqpzlSQ75uznf_Czo_w4s442WWU';

function doGet(e) {
  const action = (e && e.parameter && e.parameter.action) || 'health';
  if (action === 'health') return json_({ok:true, api:API_VERSION, now:new Date().toISOString()});
  if (action === 'pull') return pull_(e);
  return json_({ok:false, error:'UNKNOWN_ACTION'});
}

function doPost(e) {
  try {
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    if (body.action === 'push') return push_(body);
    return json_({ok:false, error:'UNKNOWN_ACTION'});
  } catch (err) {
    return json_({ok:false, error:String(err)});
  }
}

function pull_(e) {
  // TODO production: validate user/session and role before returning data.
  // TODO read only records changed after `since` and return stable IDs.
  return json_({ok:true, api:API_VERSION, since:(e.parameter.since||null), records:[]});
}

function push_(body) {
  // TODO production: role checks, schema validation, idempotency key for money operations,
  // optimistic concurrency and AuditLog.
  const changes = Array.isArray(body.changes) ? body.changes : [];
  return json_({ok:true, accepted:changes.length, conflicts:[]});
}

function spreadsheet_() {
  const id = PropertiesService.getScriptProperties().getProperty('SPREADSHEET_ID') || DEFAULT_SPREADSHEET_ID;
  return SpreadsheetApp.openById(id);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

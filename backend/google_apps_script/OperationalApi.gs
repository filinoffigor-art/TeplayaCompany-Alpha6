/** TK4 normalized operational API. Deploy only after verifyOperationalDatabase() passes.
 * No source-sheet access, automatic schema writes, demo data or credential logging.
 * Monetary storage is integer kopecks. Compatibility responses use roubles.
 */
const OP = Object.freeze({version:'tk4-v3-connected',book:'1msnOiHA2W_M2OI6eJLDFcL_mP1L_LWIirqsVZIa3IUQ',
  source:'1vTdo0kJmHQP-N4JOnqpzlSQ75uznf_Czo_w4s442WWU',tz:'Europe/Moscow',mediaFolder:'1SKj1XR8Due3S59LKSvi0uJIBr8lJm1CA'});
let opTables_={};
function opBook_(){if(OP.book===OP.source)throw Error('SOURCE_IS_READ_ONLY');return SpreadsheetApp.openById(OP.book);}
function opTable_(name){
  if(opTables_[name])return opTables_[name];
  const sheet=opBook_().getSheetByName(name);if(!sheet)throw Error('SCHEMA_MISSING_'+name);
  const values=sheet.getDataRange().getValues(),headers=(values.shift()||[]).map(String);
  if(new Set(headers.filter(Boolean)).size!==headers.filter(Boolean).length)throw Error('AMBIGUOUS_SCHEMA');
  const rows=values.map((v,i)=>{const r={};headers.forEach((h,c)=>{if(h)r[h]=v[c]===''?null:v[c];});Object.defineProperty(r,'_row',{value:i+2});return r;}).filter(r=>r.ID);
  return opTables_[name]={sheet:sheet,headers:headers,rows:rows};
}
const OP_SOFT_DELETE_TABLES = new Set(['Objects','Clients','Income','Expenses','CashTransfers','Leads','Measurements','TechTasks','Assignments','DailyPlans','PaymentSchedule','Calendar','Media','Employees','SalaryAccruals']);
function opRows_(name){
  const rows=opTable_(name).rows;
  return OP_SOFT_DELETE_TABLES.has(name)?rows.filter(r=>!r.DeletedAt):rows;
}
function opRawFind_(name,id){return opTable_(name).rows.find(r=>String(r.ID)===String(id));}
function opFind_(name,id){return opRows_(name).find(r=>String(r.ID)===String(id));}
function opStr_(x){return x==null?'':String(x);}
function opYes_(x){return x===true||/^(true|да|1)$/i.test(opStr_(x));}
function opRole_(a){return opStr_(a.role).trim().toUpperCase();}
function opAdmin_(a){return ['OWNER','ADMIN'].indexOf(opRole_(a))>=0;}
function opFinance_(a){return opAdmin_(a)||(opRole_(a)==='PARTNER'&&a.finance===true);}
function opRequired_(x,code){const s=opStr_(x).trim();if(!s)throw Error(code||'REQUIRED_FIELD');return s;}
function opMinor_(x){const n=Number(x);if(x==null||x===''||!Number.isFinite(n)||n<=0||!Number.isSafeInteger(Math.round(n*100)))throw Error('INVALID_AMOUNT');return Math.round(n*100);}
function opRub_(n){return Number.isSafeInteger(n)?n/100:null;}
function opNumber_(n){return typeof n==='number'&&Number.isFinite(n)?n:null;}
function opId_(prefix){return prefix+'-'+Utilities.getUuid();}
function opToday_(){return Utilities.formatDate(new Date(),OP.tz,'yyyy-MM-dd');}
function opDate_(value){
  if(value instanceof Date)return Utilities.formatDate(value,OP.tz,'yyyy-MM-dd');
  const s=opStr_(value).slice(0,10);if(!/^\d{4}-\d{2}-\d{2}$/.test(s))return null;
  const d=new Date(s+'T12:00:00Z');return !isNaN(d)&&d.toISOString().slice(0,10)===s?s:null;
}
function opPeriod_(period,today){
  const d=new Date((today||opToday_())+'T12:00:00Z'),y=d.getUTCFullYear(),m=d.getUTCMonth();let start,end;
  switch(period){
    case 'Сегодня':start=new Date(d);end=new Date(d);break;
    case 'Неделя':start=new Date(d);start.setUTCDate(d.getUTCDate()-(d.getUTCDay()+6)%7);end=new Date(start);end.setUTCDate(start.getUTCDate()+6);break;
    case 'Квартал':start=new Date(Date.UTC(y,Math.floor(m/3)*3,1));end=new Date(Date.UTC(y,Math.floor(m/3)*3+3,0));break;
    case 'Год':start=new Date(Date.UTC(y,0,1));end=new Date(Date.UTC(y+1,0,0));break;
    case 'Месяц':start=new Date(Date.UTC(y,m,1));end=new Date(Date.UTC(y,m+1,0));break;
    default:throw Error('INVALID_PERIOD');
  }return {from:start.toISOString().slice(0,10),to:end.toISOString().slice(0,10)};
}
function opInPeriod_(date,period){const d=opDate_(date);return !!d&&d>=period.from&&d<=period.to;}
function opCanonical_(value){if(Array.isArray(value))return '['+value.map(opCanonical_).join(',')+']';if(value&&typeof value==='object')return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+opCanonical_(value[k])).join(',')+'}';return JSON.stringify(value);}
function opHash_(value){return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,opStr_(value),Utilities.Charset.UTF_8).map(b=>('0'+((b+256)%256).toString(16)).slice(-2)).join('');}
function opEqualSecret_(a,b){a=opStr_(a);b=opStr_(b);let diff=a.length^b.length;for(let i=0;i<a.length;i++)diff|=a.charCodeAt(i)^(b.charCodeAt(i)||0);return diff===0;}
function opJson_(value){return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);}
function opAuth_(body){
  const user=opFind_('Users',opRequired_(body.userId,'AUTH_REQUIRED'));
  if(!user||!opYes_(user.Active)||!body.token||!body.deviceId||user.Device_ID!==body.deviceId||!opEqualSecret_(user.TokenHash,opHash_(body.token)))throw Error('AUTH_DENIED');
  return {userId:user.ID,name:user.Name,role:user.Role,finance:opYes_(user.Finance),employeeId:user.Employee_ID||user.ID};
}
function doGet(e){try{const p=e&&e.parameter||{};if(!p.action||p.action==='health')return opJson_({ok:true,api:OP.version,spreadsheetId:OP.book});return opJson_({ok:false,error:'POST_REQUIRED'});}catch(e){return opJson_({ok:false,error:'API_UNAVAILABLE'});}}
function doPost(e){
  opTables_={};let lock;
  try{
    const b=JSON.parse(e&&e.postData&&e.postData.contents||'{}');
    if(b.action==='health')return opJson_({ok:true,api:OP.version,spreadsheetId:OP.book});
    lock=LockService.getScriptLock();if(!lock.tryLock(15000))throw Error('REQUEST_BUSY');
    opRecover_();
    if(b.action==='pair')return opJson_(opPair_(b));
    const auth=opAuth_(b);if(b.action==='bootstrap'){
      const key='op3:'+opHash_(opCanonical_(auth)+':'+(b.period||'Месяц')+':'+PropertiesService.getScriptProperties().getProperty('OP_DATA_REVISION'));
      const cache=CacheService.getScriptCache(),cached=cache.get(key);if(cached)return opJson_(JSON.parse(cached));
      const response=opBootstrap_(auth,b.period||'Месяц'),serialized=JSON.stringify(response);
      if(serialized.length<35000)cache.put(key,serialized,30);return opJson_(response);
    }
    return opJson_(opCommand_(auth,b));
  }catch(error){const code=opStr_(error&&error.message);return opJson_({ok:false,error:/^[A-Z][A-Z0-9_]{2,80}$/.test(code)?code:'REQUEST_FAILED'});}
  finally{if(lock&&lock.hasLock())lock.releaseLock();}
}
function opSettings_(){const values={};opRows_('Settings').forEach(r=>values[r.Key]=r.Value);return values;}
function opAllowedObjects_(auth,objects){
  const role=opRole_(auth);if(opAdmin_(auth)||role==='PARTNER')return objects;
  if(role==='MANAGER')return objects.filter(o=>o.Manager_ID===auth.userId||o.Manager_ID===auth.employeeId);
  if(role==='ENGINEER')return objects.filter(o=>o.Engineer_ID===auth.userId||o.Engineer_ID===auth.employeeId);
  if(role==='INSTALLER'){const ids=new Set(opRows_('Assignments').filter(a=>a.Installer_ID===auth.employeeId).map(a=>a.Object_ID));return objects.filter(o=>ids.has(o.ID));}
  return [];
}
function opIncomeSigned_(r){return r.Status==='CONFIRMED'&&Number.isSafeInteger(r.AmountMinor)?(r.Operation==='Возврат клиенту'?-1:1)*r.AmountMinor:0;}
function opExpenseSigned_(r){return ['CONFIRMED','REFUND'].indexOf(r.Status)>=0&&Number.isSafeInteger(r.AmountMinor)?(r.Status==='REFUND'?-1:1)*r.AmountMinor:0;}
function opMonthRu_(iso){
  const month=Number(opStr_(iso).slice(5,7)),year=opStr_(iso).slice(0,4);
  const names=['','января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
  return (names[month]||'месяца')+' '+year;
}
function opPersistMonthlyFinanceKpis_(range,kpis){
  if(!range||kpis.turnover==null||kpis.expenses==null||kpis.netProfit==null)return;
  const id='KPI-MONTH-'+range.from.slice(0,7),today=opToday_(),status=today>range.to?'ЗАФИКСИРОВАНО':'ТЕКУЩИЙ ПЕРИОД';
  const previous=opFind_('PlanFact',id);
  const stable={
    'Период':'Месяц · '+opMonthRu_(range.from),
    'Период с':range.from,'Период по':range.to,'Расчёт по':today,
    'Поступления, ₽':kpis.turnover,'Расходы, ₽':kpis.expenses,'Чистая прибыль, ₽':kpis.netProfit,'Статус':status
  };
  const same=previous&&Object.keys(stable).every(k=>opStr_(previous[k])===opStr_(stable[k]));
  if(!same)opWrite_('PlanFact',Object.assign({},previous||{ID:id},stable,{'Обновлено':new Date().toISOString()}));

  const book=opBook_(),dash=book.getSheetByName('Дашборд');
  if(dash){
    dash.getRange(3,2,1,5).setValues([[opMonthRu_(range.from),'','по '+range.to.split('-').reverse().join('.'),'','Расчёт по '+today.split('-').reverse().join('.')]]);
    dash.getRange(6,1,1,5).setValues([[kpis.turnover,'',kpis.expenses,'',kpis.netProfit]]);
    dash.getRange(10,7,1,1).setValues([[kpis.netProfit]]);
    dash.getRange(11,7,1,1).setValues([['Все подтверждённые доходы − все подтверждённые расходы за период, включая налоги. Внутренние передачи денег не считаются расходом.']]);
  }
  const summary=book.getSheetByName('Сводка приложения');
  if(summary){
    summary.getRange(2,1,3,3).setValues([
      ['Поступления — '+opMonthRu_(range.from),kpis.turnover,'Все подтверждённые доходы за период минус возвраты клиентам'],
      ['Расходы — '+opMonthRu_(range.from),kpis.expenses,'Все подтверждённые расходы за период, включая налоги; внутренние переводы исключены'],
      ['Денежный результат — '+opMonthRu_(range.from),kpis.netProfit,'Поступления минус расходы']
    ]);
    summary.getRange(6,1,1,3).setValues([['Чистая прибыль — '+opMonthRu_(range.from),kpis.netProfit,'Все подтверждённые доходы − все подтверждённые расходы за период, включая налоги']]);
  }
}
function opBalances_(people,income,expenses,transfers){
  const out={};people.forEach(p=>{
    let balance=opNumber_(p.OpeningMinor),received=0,spent=0,incoming=0,outgoing=0;
    income.forEach(r=>{if(r.Recipient_ID===p.ID)received+=opIncomeSigned_(r);});
    expenses.forEach(r=>{if(r.Person_ID===p.ID)spent+=opExpenseSigned_(r);});
    transfers.filter(r=>r.Status==='CONFIRMED').forEach(r=>{if(r.ToPerson_ID===p.ID)incoming+=r.AmountMinor;if(r.Person_ID===p.ID)outgoing+=r.AmountMinor;});
    out[p.Name]={Accountable_ID:p.ID,balance:balance==null?null:opRub_(balance+received-spent+incoming-outgoing),opening:opRub_(balance),clientReceipts:opRub_(received),expenses:opRub_(spent),incomingTransfers:opRub_(incoming),outgoingTransfers:opRub_(outgoing)};
  });return out;
}
function opClosedStatus_(status){return ['Работы завершены','Завершён','Закрыт 100%'].indexOf(opStr_(status))>=0;}
function opAssignmentAmount_(row){const n=Number(row&&row['Согласованная сумма']);return Number.isFinite(n)&&n>0?n:0;}
function opPayrollPaid_(installerId,objectId){return opRows_('SalaryAccruals').filter(r=>r.Installer_ID===installerId&&r.Object_ID===objectId).reduce((n,r)=>n+(Number(r['Выплачено'])||0),0);}
function opInstallerAnalytics_(auth,objects,range,employees){
  const role=opRole_(auth),allowedIds=new Set(objects.map(o=>o.ID));
  let assignments=opRows_('Assignments').filter(a=>allowedIds.has(a.Object_ID));
  if(role==='INSTALLER')assignments=assignments.filter(a=>a.Installer_ID===auth.employeeId);
  const plans=opRows_('DailyPlans').filter(d=>allowedIds.has(d.Object_ID));
  const stats=[],objectPay=[];
  employees.filter(e=>/INSTALLER|МОНТАЖ|НАЁМ/i.test(opStr_(e.Role)+' '+opStr_(e.WorkerKind))).forEach(employee=>{
    const own=assignments.filter(a=>a.Installer_ID===employee.ID),objectIds=new Set(own.map(a=>a.Object_ID)),dates=new Set();
    plans.forEach(d=>{
      if(!objectIds.has(d.Object_ID)||!(Number(d['Факт объём'])>0)||!opInPeriod_(d['Дата'],range))return;
      const active=own.some(a=>a.Object_ID===d.Object_ID&&(!opDate_(a['Назначен с'])||d['Дата']>=opDate_(a['Назначен с']))&&(!opDate_(a['Назначен до'])||d['Дата']<=opDate_(a['Назначен до'])));
      if(active)dates.add(opDate_(d['Дата']));
    });
    const closedIds=new Set(),closedInPeriod=new Set();let earned=0,earnedAll=0;
    own.forEach(a=>{const object=objects.find(o=>o.ID===a.Object_ID);if(!object||!opClosedStatus_(object.Status))return;closedIds.add(object.ID);earnedAll+=opAssignmentAmount_(a);const end=opDate_(object.ActualEnd);if(end&&opInPeriod_(end,range)){closedInPeriod.add(object.ID);earned+=opAssignmentAmount_(a);}});
    let paidAll=0,dueAll=0;
    const grouped={};own.forEach(a=>{const key=a.Object_ID;(grouped[key]||(grouped[key]=[])).push(a);});
    Object.keys(grouped).forEach(objectId=>{
      const object=objects.find(o=>o.ID===objectId);if(!object)return;
      const agreed=grouped[objectId].reduce((n,a)=>n+opAssignmentAmount_(a),0),paid=opPayrollPaid_(employee.ID,objectId),remaining=Math.max(0,agreed-paid);
      paidAll+=paid;dueAll+=remaining;
      objectPay.push({installerId:employee.ID,objectId:objectId,address:object.Address,status:object.Status,closed:opClosedStatus_(object.Status),closedDate:opDate_(object.ActualEnd),agreed:agreed,earned:opClosedStatus_(object.Status)?agreed:0,paid:paid,remaining:remaining});
    });
    stats.push({installerId:employee.ID,workDays:dates.size,closedObjects:closedInPeriod.size,earned:earned,earnedAll:earnedAll,paidAll:paidAll,dueAll:dueAll});
  });
  return {stats:stats,objects:objectPay};
}
function opBitrixConfig_(){
  const p=PropertiesService.getScriptProperties(),base=opStr_(p.getProperty('BITRIX_WEBHOOK_BASE')).trim().replace(/\/+$/,'');
  return {base:base,category:opStr_(p.getProperty('BITRIX_MEASUREMENTS_CATEGORY_ID')).trim(),surveyField:opStr_(p.getProperty('BITRIX_SURVEY_ID_FIELD')).trim(),
    stage:opStr_(p.getProperty('BITRIX_MEASUREMENTS_STAGE_ID')).trim(),phoneField:opStr_(p.getProperty('BITRIX_SURVEY_PHONE_FIELD')).trim(),
    addressField:opStr_(p.getProperty('BITRIX_SURVEY_ADDRESS_FIELD')).trim(),workTypeField:opStr_(p.getProperty('BITRIX_SURVEY_WORKTYPE_FIELD')).trim(),
    dateField:opStr_(p.getProperty('BITRIX_SURVEY_DATE_FIELD')).trim(),lastSync:opStr_(p.getProperty('BITRIX_MEASUREMENTS_LAST_SYNC')).trim()};
}
function opBitrixReady_(cfg){return !!(cfg&&cfg.base&&cfg.category&&cfg.surveyField);}
function opBitrixCall_(cfg,method,payload){
  if(!opBitrixReady_(cfg))throw Error('BITRIX_NOT_CONFIGURED');
  const response=UrlFetchApp.fetch(cfg.base+'/'+method+'.json',{method:'post',payload:payload||{},muteHttpExceptions:true});
  const code=response.getResponseCode(),body=JSON.parse(response.getContentText()||'{}');
  if(code<200||code>=300||body.error)throw Error('BITRIX_REQUEST_FAILED');
  return body;
}
function opBitrixDealFields_(cfg,survey){
  const fields={TITLE:survey.Name||survey['Клиент']||'Замер',CATEGORY_ID:cfg.category,COMMENTS:'Тёплая Компания 4.0 · Замер'};
  fields[cfg.surveyField]=survey.Survey_ID||survey.ID;if(cfg.stage)fields.STAGE_ID=cfg.stage;
  if(cfg.phoneField)fields[cfg.phoneField]=survey.Phone||survey['Телефон']||'';
  if(cfg.addressField)fields[cfg.addressField]=survey['Адрес']||'';
  if(cfg.workTypeField)fields[cfg.workTypeField]=survey['Вид работ']||'';
  if(cfg.dateField)fields[cfg.dateField]=opDate_(survey.Date||survey['Дата'])||opToday_();
  return fields;
}
function opBitrixFlatFields_(fields){const payload={};Object.keys(fields).forEach(k=>payload['fields['+k+']']=fields[k]);return payload;}
function opBitrixSyncMeasurements_(auth){
  if(!opAdmin_(auth)&&['PARTNER','ENGINEER','MANAGER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_CRM');
  const cfg=opBitrixConfig_();if(!opBitrixReady_(cfg))throw Error('BITRIX_NOT_CONFIGURED');
  let pushed=0,pulled=0,created=0;
  const local=opRows_('Measurements');
  local.forEach(survey=>{
    const query={};query['filter[CATEGORY_ID]']=cfg.category;query['filter['+cfg.surveyField+']']=survey.Survey_ID||survey.ID;query['select[0]']='ID';
    const found=opBitrixCall_(cfg,'crm.deal.list',query),deal=found.result&&found.result[0],fields=opBitrixDealFields_(cfg,survey);
    if(deal&&deal.ID){const payload=opBitrixFlatFields_(fields);payload.id=deal.ID;opBitrixCall_(cfg,'crm.deal.update',payload);}
    else opBitrixCall_(cfg,'crm.deal.add',opBitrixFlatFields_(fields));
    pushed++;
  });
  let start=0,pages=0;
  do{
    const payload={start:start};payload['filter[CATEGORY_ID]']=cfg.category;
    ['ID','TITLE','BEGINDATE','DATE_CREATE',cfg.surveyField,cfg.phoneField,cfg.addressField,cfg.workTypeField,cfg.dateField].filter(Boolean).forEach((field,i)=>payload['select['+i+']']=field);
    const page=opBitrixCall_(cfg,'crm.deal.list',payload),deals=Array.isArray(page.result)?page.result:[];
    deals.forEach(deal=>{
      let surveyId=opStr_(deal[cfg.surveyField]).trim(),survey=surveyId?opFind_('Measurements',surveyId):null;
      if(!survey){surveyId=opId_('SURVEY');const date=opDate_(cfg.dateField?deal[cfg.dateField]:deal.BEGINDATE)||opDate_(deal.DATE_CREATE)||opToday_();
        const record={ID:surveyId,Survey_ID:surveyId,Date:date,Name:opStr_(deal.TITLE).trim()||'Замер из Bitrix24',Phone:cfg.phoneField?opStr_(deal[cfg.phoneField]):null,Manager_ID:null,Engineer_ID:null,'Дата':date,'Клиент':opStr_(deal.TITLE).trim()||'Замер из Bitrix24','Телефон':cfg.phoneField?opStr_(deal[cfg.phoneField]):null,'Вид работ':cfg.workTypeField?opStr_(deal[cfg.workTypeField]):null,'Комментарий':'Синхронизировано из Bitrix24','Адрес':cfg.addressField?opStr_(deal[cfg.addressField]):null,ConvertedToObject:false,Object_ID:null,Revision:1};
        opWrite_('Measurements',record);const update={id:deal.ID};update['fields['+cfg.surveyField+']']=surveyId;opBitrixCall_(cfg,'crm.deal.update',update);created++;
      }else{
        const fields={Name:opStr_(deal.TITLE).trim()||survey.Name,'Клиент':opStr_(deal.TITLE).trim()||survey['Клиент']};
        if(cfg.phoneField){fields.Phone=opStr_(deal[cfg.phoneField]);fields['Телефон']=fields.Phone;}if(cfg.addressField)fields['Адрес']=opStr_(deal[cfg.addressField]);if(cfg.workTypeField)fields['Вид работ']=opStr_(deal[cfg.workTypeField]);
        opWrite_('Measurements',Object.assign({},survey,fields,{Revision:Number(survey.Revision||1)+1}));pulled++;
      }
    });
    start=page.next==null?null:Number(page.next);pages++;
  }while(start!=null&&pages<10);
  const at=new Date().toISOString();PropertiesService.getScriptProperties().setProperty('BITRIX_MEASUREMENTS_LAST_SYNC',at);PropertiesService.getScriptProperties().setProperty('OP_DATA_REVISION',Utilities.getUuid());
  return {ok:true,pushed:pushed,pulled:pulled,created:created,lastSync:at,funnel:'Замеры'};
}

function opBootstrap_(auth,period){
  const settings=opSettings_();if(settings.MIGRATION_STATUS!=='READY')throw Error('MIGRATION_NOT_READY');
  const range=opPeriod_(period),finance=opFinance_(auth),role=opRole_(auth),allObjects=opRows_('Objects'),objects=opAllowedObjects_(auth,allObjects),ids=new Set(objects.map(o=>o.ID));
  const income=opRows_('Income'),expenses=opRows_('Expenses'),transfers=opRows_('CashTransfers'),clients=opRows_('Clients'),people=opRows_('AccountablePersons');
  const byObject=rows=>rows.filter(r=>ids.has(r.Object_ID));
  const full=opAdmin_(auth)||role==='PARTNER';
  const employees=opRows_('Employees').filter(r=>full||r.ID===auth.employeeId||role==='ENGINEER'&&opRows_('Assignments').some(a=>ids.has(a.Object_ID)&&a.Installer_ID===r.ID));
  const leads=opRows_('Leads').filter(r=>full||r.Manager_ID===auth.userId),surveys=opRows_('Measurements').filter(r=>full||r.Manager_ID===auth.userId||r.Engineer_ID===auth.userId);
  const periodObjectDate=o=>opDate_(o.ActualStart)||opDate_(o.PlanStart)||opDate_(o.ActualEnd);
  const periodObjects=objects.filter(o=>{const d=periodObjectDate(o);return d&&opInPeriod_(d,range);});
  const contractObjects=periodObjects.filter(o=>Number.isSafeInteger(o.ContractMinor)&&o.ContractMinor>0);
  const periodSurveys=surveys.filter(r=>opInPeriod_(r.Date||r['Дата'],range));
  const surveyDone=r=>opYes_(r.ConvertedToObject)||/ПРОВЕД|ЗАВЕРШ|ВЫПОЛН/i.test(opStr_(r.Status||r['Статус']));
  const kpis={
    objectsInWork:objects.filter(o=>o.Status==='В работе').length,
    objectsInWorkAmount:null,
    plannedObjects:objects.filter(o=>['Запланирован','Подтверждён','Подтверждён клиентом','Готов к монтажу'].indexOf(o.Status)>=0).length,
    leads:leads.filter(r=>opInPeriod_(r.Date||r['Дата'],range)).length,
    surveys:periodSurveys.length,
    surveysCompleted:periodSurveys.filter(surveyDone).length,
    surveysScheduled:periodSurveys.filter(r=>!surveyDone(r)).length,
    contracts:contractObjects.length,
    averageCheck:contractObjects.length?opRub_(Math.round(contractObjects.reduce((n,o)=>n+o.ContractMinor,0)/contractObjects.length)):0,
    closedProfit:null,netProfit:null,profit:null
  };
  const active=objects.filter(o=>o.Status==='В работе');if(role!=='INSTALLER'&&active.every(o=>Number.isSafeInteger(o.ContractMinor)))kpis.objectsInWorkAmount=opRub_(active.reduce((n,o)=>n+o.ContractMinor,0));
  const publicObjects=objects.map(o=>{const client=clients.find(c=>c.ID===o.Client_ID)||{};const paid=income.filter(i=>i.Object_ID===o.ID).reduce((n,i)=>n+opIncomeSigned_(i),0);
    const r={id:o.ID,clientId:o.Client_ID,address:o.Address,client:client.Name,phone:client.Phone,workType:o.WorkType,status:o.Status,progress:opNumber_(o.Progress),planStart:opDate_(o.PlanStart),planEnd:opDate_(o.PlanEnd),factStart:opDate_(o.ActualStart),factEnd:opDate_(o.ActualEnd),engineer:o.Engineer_ID,responsible:o.LegacyResponsible,brigade:o.LegacyCrew,revision:o.Revision||1};
    if(role!=='INSTALLER'){r.contract=opRub_(o.ContractMinor);r.paid=opRub_(paid);r.remaining=o.ContractMinor==null?null:opRub_(o.ContractMinor-paid);}return r;});
  const payments=byObject(opRows_('PaymentSchedule')).map(p=>{const received=income.filter(i=>i.PaymentPlan_ID===p.ID).reduce((n,i)=>n+opIncomeSigned_(i),0),amount=opNumber_(p.AmountMinor),left=amount==null?null:Math.max(0,amount-received),date=opDate_(p.Date||p['Плановая дата']);
    return {id:p.ID,objectId:p.Object_ID,plannedDate:date,plannedAmount:opRub_(amount),actualPaid:opRub_(received),remaining:opRub_(left),stageName:p['Наименование этапа']||p.StageName,status:left==null?'Не заполнено':left===0?'Оплачено':date&&date<opToday_()?'Просрочено':'План',overdueDays:date&&date<opToday_()&&left>0?Math.floor((new Date(opToday_())-new Date(date))/86400000):0};});
  const workforce=opInstallerAnalytics_(auth,objects,range,employees),bitrix=opBitrixConfig_(),bitrixReady=opBitrixReady_(bitrix);
  const response={ok:true,api:OP.version,serverTime:new Date().toISOString(),period:period,user:{id:auth.userId,name:auth.name,role:role,finance:finance,admin:opAdmin_(auth)},
    scope:{enforced:true,userId:auth.userId},coverage:{from:settings.COVERAGE_FROM,to:settings.COVERAGE_TO,complete:range.from>=settings.COVERAGE_FROM&&range.to<=settings.COVERAGE_TO},
    capabilities:{financialEditsV1:finance,financialDeletesV1:finance,dailyProgressV1:true,stagePhotoMetaV1:true,installerCreateV1:opAdmin_(auth)||role==='PARTNER',installerProfileV1:full,installerPaymentsV1:finance,bitrixMeasurementsV1:bitrixReady&&(opAdmin_(auth)||['PARTNER','ENGINEER','MANAGER'].indexOf(role)>=0),personalReimbursementsV1:false,assignmentPeriodsV1:false,hiredWorkersV1:false},kpis:kpis,objects:publicObjects,
    integrations:{bitrixMeasurements:{configured:bitrixReady,funnel:'Замеры',lastSync:bitrix.lastSync||null}},
    employees:employees.map(r=>{const item={id:r.ID,name:r.Name||r['ФИО'],role:r.Role||r['Роль'],active:r.Active==null?'true':opStr_(r.Active),phone:r.Phone||r['Телефон'],workerKind:r.WorkerKind,revision:r.Revision||1};if(full){item.address=r.Address||'';item.relativeName=r.RelativeName||'';item.relativePhone=r.RelativePhone||'';item.serviceContractSigned=opYes_(r.ServiceContractSigned);}return item;}),
    installerStats:workforce.stats,installerObjectPay:workforce.objects,
    leads:leads.map(opPublicRow_),surveys:surveys.map(opPublicRow_),techTasks:byObject(opRows_('TechTasks')).map(opPublicRow_),dayPlans:byObject(opRows_('DailyPlans')).filter(r=>r.TechTask_ID).map(opPublicRow_),assignments:byObject(opRows_('Assignments')).map(opPublicRow_),calendar:byObject(opRows_('Calendar')).map(opPublicRow_),media:byObject(opRows_('Media')).map(opPublicRow_),paymentPlan:role==='INSTALLER'?[]:payments,
    payroll:opRows_('SalaryAccruals').filter(r=>full||role==='INSTALLER'&&r.Installer_ID===auth.employeeId).map(opPublicRow_),attention:[],lists:{}};
  if(role==='INSTALLER'){response.assignments=response.assignments.filter(r=>r.Installer_ID===auth.employeeId);response.calendar=response.calendar.filter(r=>r.Installer_ID===auth.employeeId);response.techTasks=response.techTasks.map(r=>({TechTask_ID:r.TechTask_ID,Object_ID:r.Object_ID,'План начала':r['План начала'],'План окончания':r['План окончания'],'Комментарий':r['Комментарий']}));}
  if(finance){
    const pi=income.filter(r=>opInPeriod_(r.Date,range)),pe=expenses.filter(r=>opInPeriod_(r.Date,range));
    const incomeMinor=pi.reduce((n,r)=>n+opIncomeSigned_(r),0),expenseMinor=pe.reduce((n,r)=>n+opExpenseSigned_(r),0);
    kpis.turnover=opRub_(incomeMinor);kpis.expenses=opRub_(expenseMinor);
    const confirmedIncome=pi.filter(r=>r.Status==='CONFIRMED'&&r.Operation!=='Возврат клиенту');
    kpis.turnoverFinal=opRub_(confirmedIncome.filter(r=>/Окончатель/i.test(opStr_(r.PaymentKind))).reduce((n,r)=>n+r.AmountMinor,0));
    kpis.turnoverIntermediate=opRub_(confirmedIncome.filter(r=>!/Окончатель/i.test(opStr_(r.PaymentKind))).reduce((n,r)=>n+r.AmountMinor,0));
    // Чистая прибыль компании за период: все подтверждённые доходы минус все подтверждённые расходы.
    // Налоги входят автоматически, если они заведены как подтверждённый расход. Внутренние передачи денег не являются расходом.
    kpis.netProfit=opRub_(incomeMinor-expenseMinor);kpis.profit=kpis.netProfit;
    const paid=pi.filter(r=>r.Status==='CONFIRMED'&&r.Operation==='Приход');kpis.averagePayment=paid.length?opRub_(Math.round(paid.reduce((n,r)=>n+r.AmountMinor,0)/paid.length)):null;
    const outgoing=pe.filter(r=>r.Status==='CONFIRMED');kpis.averageExpense=outgoing.length?opRub_(Math.round(outgoing.reduce((n,r)=>n+r.AmountMinor,0)/outgoing.length)):null;
    response.accountable=opBalances_(people,income,expenses,transfers);const balances=Object.values(response.accountable).map(a=>a.balance);kpis.totalAccountable=balances.every(x=>x!=null)?balances.reduce((n,x)=>n+x,0):null;
    response.income=pi.map(r=>opIncomeDto_(r,objects,people));response.expenses=pe.concat(transfers.filter(r=>opInPeriod_(r.Date,range))).map(r=>opExpenseDto_(r,objects,people));response.historyComplete=true;
    kpis.debt=payments.some(p=>p.remaining==null)?null:payments.filter(p=>p.status==='Просрочено').reduce((n,p)=>n+p.remaining,0);
    kpis.plannedReceipts=payments.some(p=>p.remaining==null)?null:payments.filter(p=>p.status==='План').reduce((n,p)=>n+p.remaining,0);
    kpis.remainingToReceive=publicObjects.reduce((n,o)=>n+(typeof o.remaining==='number'?Math.max(0,o.remaining):0),0);
    response.financeAnalytics={expenseCategories:opGroupExpenses_(pe,'Category'),cashFlow:null,suppliers:null};
    if(period==='Месяц')opPersistMonthlyFinanceKpis_(range,kpis);
  }
  const quality=opRows_('DataQuality').filter(r=>r.Status==='OPEN'&&(full||ids.has(r.EntityID)));
  response.attention=quality.map(r=>({id:r.ID,title:r.Message,subtitle:r.EntityType+' · '+r.Field,severity:r.Severity==='CRITICAL'?'red':'yellow',target:r.EntityType==='Objects'?'object:'+r.EntityID:'kpi:notifications'}));
  return response;
}
function opPublicRow_(r){const out={};Object.keys(r).forEach(k=>{if(!/^(RawJSON|LiveRawJSON|Source|LegacyID|Migration|Token|Pairing)/.test(k))out[k]=r[k];});return out;}
function opIncomeDto_(r,objects,people){const o=objects.find(o=>o.ID===r.Object_ID),p=people.find(p=>p.ID===r.Recipient_ID);return {id:r.ID,entity:'Income',revision:r.Revision||1,objectId:r.Object_ID,object:o?o.Address:'Не заполнено',date:r.Date,operation:r.Operation,paymentKind:r.PaymentKind,amount:opRub_(r.AmountMinor),recipient:p?p.Name:null,comment:r.Comment,status:r.Status};}
function opExpenseDto_(r,objects,people){const o=objects.find(o=>o.ID===r.Object_ID),p=people.find(p=>p.ID===r.Person_ID);return {id:r.ID,entity:r.Type==='Перевод подотчёта'?'CashTransfers':'Expenses',revision:r.Revision||1,objectId:r.Object_ID,object:o?o.Address:'',date:r.Date,type:r.Type,article:r.Category,description:r.Description,amount:opRub_(r.AmountMinor),responsible:p?p.Name:null,status:r.Status};}
function opGroupExpenses_(rows,key){const map={};rows.forEach(r=>{const amount=opExpenseSigned_(r);if(amount){const label=r[key]||'Не заполнено';map[label]=(map[label]||0)+amount;}});return Object.keys(map).map(label=>({label:label,amount:opRub_(map[label])}));}

/* Audited write-ahead journal. Every entity is addressed by an immutable ID.
 * A retry recovers PREPARED changes before any subsequent read or write.
 */
function opWrite_(table,record){
  const t=opTable_(table),existing=t.rows.find(r=>r.ID===record.ID),row=existing?existing._row:t.sheet.getLastRow()+1;
  Object.keys(record).forEach(k=>{if(t.headers.indexOf(k)<0)throw Error('SCHEMA_MISSING_COLUMN');});
  if(row>t.sheet.getMaxRows())t.sheet.insertRowsAfter(t.sheet.getMaxRows(),Math.max(100,row-t.sheet.getMaxRows()));
  t.sheet.getRange(row,1,1,t.headers.length).setValues([t.headers.map(h=>{const v=record[h];const cell=v==null?'':typeof v==='object'?JSON.stringify(v):v;return typeof cell==='string'&&/^[=+@]/.test(cell)?"'"+cell:cell;})]);
  delete opTables_[table];SpreadsheetApp.flush();
  PropertiesService.getScriptProperties().setProperty('OP_DATA_REVISION',Utilities.getUuid());
}
function opComparable_(r){const result={};Object.keys(r||{}).forEach(k=>{let v=r[k];if(v!==null&&v!==undefined&&v!=='')result[k]=typeof v==='object'?JSON.stringify(v):v;});return opCanonical_(result);}
function opChanges_(event){return typeof event.ChangesJSON==='string'?JSON.parse(event.ChangesJSON):event.ChangesJSON;}
function opApplyEvent_(event){
  for(const change of opChanges_(event)){
    if(change.table==='Users'||change.table==='AuditLog'||change.table.indexOf('LEGACY')===0)throw Error('FORBIDDEN_TRANSACTION_TARGET');
    const actual=opFind_(change.table,change.after.ID);
    if(actual&&opComparable_(actual)===opComparable_(change.after))continue;
    if(opComparable_(actual)!==opComparable_(change.before))throw Error('RECOVERY_CONFLICT');
    opWrite_(change.table,change.after);
  }
  opWrite_('AuditLog',Object.assign({},event,{CommitState:'COMMITTED'}));
}
function opRecover_(){opRows_('AuditLog').filter(r=>r.CommitState==='PREPARED').forEach(opApplyEvent_);}
function opTransaction_(auth,key,hash,action,reason,changes,result){
  const existing=opRows_('AuditLog').find(r=>r.Transaction_ID===key);
  if(existing){if(existing.RequestHash!==hash)throw Error('IDEMPOTENCY_CONFLICT');if(existing.CommitState!=='COMMITTED')opApplyEvent_(existing);return typeof existing.ResultJSON==='string'?JSON.parse(existing.ResultJSON):existing.ResultJSON;}
  if(JSON.stringify(changes).length>45000)throw Error('TRANSACTION_TOO_LARGE');
  const event={ID:opId_('AUDIT'),Actor_ID:auth.userId,CreatedAt:new Date().toISOString(),EntityType:changes[0].table,EntityID:changes[0].after.ID,Action:action,Reason:reason||'Создание записи',BeforeJSON:changes[0].before,AfterJSON:changes[0].after,Transaction_ID:key,RequestHash:hash,CommitState:'PREPARED',ChangesJSON:changes,ResultJSON:result};
  opWrite_('AuditLog',event);opApplyEvent_(event);return result;
}
function opPair_(body){
  const device=opRequired_(body.deviceId,'AUTH_REQUIRED'),code=opRequired_(body.pairingCode,'PAIRING_CODE_REQUIRED'),requestedName=opStr_(body.userName).trim();
  const cache=CacheService.getScriptCache(),key='pair:'+opHash_(device+':'+requestedName),attempt=Number(cache.get(key)||0);
  if(attempt>=10)throw Error('PAIRING_RATE_LIMIT');cache.put(key,String(attempt+1),300);
  let user=null;
  if(body.userId)user=opFind_('Users',body.userId);
  if(!user){
    const matches=opRows_('Users').filter(u=>opYes_(u.Active)&&u.PairingCode&&opEqualSecret_(u.PairingCode,code)&&(!requestedName||opStr_(u.Name).trim()===requestedName));
    if(matches.length>1)throw Error('PAIRING_AMBIGUOUS');
    user=matches[0]||null;
  }
  if(!user||!opYes_(user.Active)||!user.PairingCode||!opEqualSecret_(user.PairingCode,code))throw Error('PAIRING_DENIED');
  const token=Utilities.getUuid().replace(/-/g,'')+Utilities.getUuid().replace(/-/g,'');
  opWrite_('Users',Object.assign({},user,{PairingCode:null,TokenHash:opHash_(token),Device_ID:device,UpdatedAt:new Date().toISOString()}));
  return {ok:true,userId:user.ID,name:user.Name,role:user.Role,token:token};
}
function opPerson_(value){const rows=opRows_('AccountablePersons'),byId=rows.find(r=>r.ID===value);if(byId)return byId;
  const matches=rows.filter(r=>r.Name===value);if(matches.length!==1)throw Error('ACCOUNTABLE_ID_REQUIRED');return matches[0];}
function opObjectAccess_(auth,id){const o=opAllowedObjects_(auth,opRows_('Objects')).find(r=>r.ID===id);if(!o)throw Error('OBJECT_NOT_FOUND');return o;}
function opChange_(table,before,fields){return {table:table,before:before||null,after:Object.assign({},before||{},fields,{Revision:before?Number(before.Revision||1)+1:1})};}
function opDateRequired_(value){const d=opDate_(value);if(!d)throw Error('INVALID_DATE');return d;}
function opPick_(source,keys){const out={};keys.forEach(k=>{if(source[k]!==undefined)out[k]=source[k];});return out;}
function opCommand_(auth,body){
  const action=opStr_(body.action),requestId=opRequired_(body.idempotencyKey||body.requestId,'REQUEST_ID_REQUIRED');if(requestId.length>120)throw Error('INVALID_REQUEST_ID');
  const allowed=['amount','objectId','paymentKind','operation','method','payer','recipient','comment','documentNo','paymentPlanId','type','article','description','attention','quantity','unit','unitPrice','paymentStatus','accountable','installerId','name','paymentType','from','to','date','client','phone','address','relativeName','relativePhone','serviceContractSigned','workType','brigade','status','planStart','planEnd','progress','contract','channel','responsible','expectedRevision','reason','entity','entityId','surveyId','source','owner','engineer','engineerId','materialsReady','technology','area','safetyNotes','days','assignments','payments','techTaskId','dayPlanId','actualQty','mimeType','mediaType','stage'];
  const data=opPick_(body,allowed);if(action==='uploadMedia')data.contentHash=opHash_(opStr_(body.base64));
  if(action==='syncBitrixMeasurements')return opBitrixSyncMeasurements_(auth);
  const hash=opHash_(opCanonical_({action:action,data:data})),key=auth.userId+':'+requestId;
  const old=opRows_('AuditLog').find(r=>r.Transaction_ID===key);if(old){if(old.RequestHash!==hash)throw Error('IDEMPOTENCY_CONFLICT');return Object.assign({ok:true},typeof old.ResultJSON==='string'?JSON.parse(old.ResultJSON):old.ResultJSON);}
  let changes=[],result={},reason=opStr_(data.reason),now=new Date().toISOString();
  if(['addIncome','addExpense','transfer','editFinance','deleteFinance'].indexOf(action)>=0&&!opFinance_(auth))throw Error('FORBIDDEN_FINANCE');
  if(action==='addIncome'){
    const object=opObjectAccess_(auth,data.objectId),person=opPerson_(data.recipient),amount=opMinor_(data.amount),operation=data.operation||'Приход';if(['Приход','Возврат клиенту'].indexOf(operation)<0)throw Error('INVALID_OPERATION');
    if(data.paymentPlanId){const payment=opFind_('PaymentSchedule',data.paymentPlanId);if(!payment||payment.Object_ID!==object.ID)throw Error('PAYMENT_PLAN_MISMATCH');}
    const id=opId_('INC');changes.push(opChange_('Income',null,{ID:id,Date:opDateRequired_(data.date||opToday_()),Operation:operation,PaymentKind:opRequired_(data.paymentKind),AmountMinor:amount,Method:opRequired_(data.method),Payer:data.payer||null,Document:data.documentNo||null,Recipient_ID:person.ID,LegacyRecipient:person.Name,Object_ID:object.ID,PaymentPlan_ID:data.paymentPlanId||null,Comment:data.comment||null,Status:'CONFIRMED',DataStatus:'IMPORTED'}));result={id:id};
  }else if(action==='addExpense'){
    if(data.type==='Перевод подотчёта')throw Error('USE_TRANSFER_ACTION');const person=opPerson_(data.accountable),amount=opMinor_(data.amount||Number(data.quantity||1)*Number(data.unitPrice));
    const object=data.objectId?opObjectAccess_(auth,data.objectId):null;if(data.type==='По объекту'&&!object)throw Error('OBJECT_REQUIRED');
    const status={'Оплачено':'CONFIRMED','Возврат':'REFUND','К оплате':'PLANNED'}[data.paymentStatus];if(!status)throw Error('INVALID_PAYMENT_STATUS');
    if(data.installerId&&!opFind_('Employees',data.installerId))throw Error('INSTALLER_NOT_FOUND');
    const id=opId_('EXP');changes.push(opChange_('Expenses',null,{ID:id,Date:opDateRequired_(data.date||opToday_()),Type:data.type||'Прочий расход',Category:opRequired_(data.article),Description:opRequired_(data.description||data.comment),AmountMinor:amount,Quantity:Number(data.quantity)||1,Unit:data.unit||null,PriceMinor:data.unitPrice?opMinor_(data.unitPrice):null,Method:opRequired_(data.method),LegacyStatus:data.paymentStatus,Status:status,Person_ID:person.ID,LegacyPerson:person.Name,Object_ID:object?object.ID:null,Installer_ID:data.installerId||null,Comment:data.comment||null,DataStatus:'IMPORTED'}));result={id:id};
  }else if(action==='transfer'){
    const from=opPerson_(data.from),to=opPerson_(data.to);if(from.ID===to.ID)throw Error('TRANSFER_TO_SELF');const id=opId_('TRF');changes.push(opChange_('CashTransfers',null,{ID:id,Date:opDateRequired_(data.date||opToday_()),Type:'Перевод подотчёта',Category:'Внутренний перевод',Description:'Передача между подотчётными лицами',AmountMinor:opMinor_(data.amount),Person_ID:from.ID,ToPerson_ID:to.ID,LegacyPerson:from.Name,Status:'CONFIRMED',LegacyStatus:'Оплачено',Comment:data.comment||null,DataStatus:'IMPORTED'}));result={id:id};
  }else if(action==='editFinance'){
    if(['Income','Expenses','CashTransfers'].indexOf(data.entity)<0)throw Error('INVALID_ENTITY');reason=opRequired_(data.reason,'EDIT_REASON_REQUIRED');const record=opFind_(data.entity,data.entityId);if(!record)throw Error('RECORD_NOT_FOUND');if(Number(data.expectedRevision)!==Number(record.Revision||1))throw Error('REVISION_CONFLICT');changes.push(opChange_(data.entity,record,{AmountMinor:opMinor_(data.amount)}));result={id:record.ID,revision:Number(record.Revision||1)+1};
  }else if(action==='deleteFinance'){
    if(['Income','Expenses','CashTransfers'].indexOf(data.entity)<0)throw Error('INVALID_ENTITY');
    reason=opRequired_(data.reason,'DELETE_REASON_REQUIRED');
    const record=opRawFind_(data.entity,data.entityId);
    if(!record||record.DeletedAt)throw Error('RECORD_NOT_FOUND');
    if(Number(data.expectedRevision)!==Number(record.Revision||1))throw Error('REVISION_CONFLICT');
    const deletedAt=new Date().toISOString();
    changes.push(opChange_(data.entity,record,{DeletedAt:deletedAt,DeletedBy:auth.userId,DeleteReason:reason}));
    result={id:record.ID,revision:Number(record.Revision||1)+1,deleted:true,deletedAt:deletedAt};
  }else if(action==='createObject'||action==='convertSurveyToObject'){
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER','MANAGER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_OBJECTS');
    let survey;if(action==='convertSurveyToObject'){survey=opFind_('Measurements',data.surveyId);if(!survey||!opAdmin_(auth)&&opRole_(auth)!=='PARTNER'&&survey.Manager_ID!==auth.userId&&survey.Engineer_ID!==auth.userId)throw Error('SURVEY_NOT_FOUND');if(survey.Object_ID)return {ok:true,id:survey.Object_ID,objectId:survey.Object_ID};data.address=survey['Адрес'];data.client=survey.Name||survey['Клиент'];data.phone=survey.Phone||survey['Телефон'];data.workType=survey['Вид работ'];}
    const clientId=opId_('CLIENT'),id=opId_('OBJ');changes.push(opChange_('Clients',null,{ID:clientId,Name:opRequired_(data.client),Phone:data.phone||null,DataStatus:data.phone?'IMPORTED':'Требует заполнения'}));
    changes.push(opChange_('Objects',null,{ID:id,Client_ID:clientId,Name:opRequired_(data.address),Address:data.address,WorkType:data.workType||null,LegacyCrew:data.brigade||null,Status:'Запланирован',PlanStart:data.planStart?opDateRequired_(data.planStart):null,PlanEnd:data.planEnd?opDateRequired_(data.planEnd):null,ContractMinor:data.contract?opMinor_(data.contract):null,Progress:0,Comment:data.comment||null,Source:data.channel||null,Manager_ID:opRole_(auth)==='MANAGER'?auth.userId:null,Engineer_ID:opRole_(auth)==='ENGINEER'?auth.userId:null,LegacyResponsible:auth.name,DataStatus:'Требует заполнения'}));
    if(survey)changes.push(opChange_('Measurements',survey,{Object_ID:id,ConvertedToObject:true}));result={id:id,objectId:id};
  }else if(action==='updateObject'||action==='updateObjectStatus'){
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_OBJECTS');const object=opObjectAccess_(auth,data.objectId);if(Number(data.expectedRevision)!==Number(object.Revision||1))throw Error('REVISION_CONFLICT');
    const fields={};for(const pair of [['workType','WorkType'],['brigade','LegacyCrew'],['comment','Comment']])if(data[pair[0]]!==undefined)fields[pair[1]]=opStr_(data[pair[0]]);
    if(data.contract!==undefined){if(!opFinance_(auth))throw Error('FORBIDDEN_FINANCE');fields.ContractMinor=opMinor_(data.contract);}
    if(data.planStart!==undefined)fields.PlanStart=opDateRequired_(data.planStart);if(data.planEnd!==undefined)fields.PlanEnd=opDateRequired_(data.planEnd);
    if((fields.PlanStart||object.PlanStart)&&(fields.PlanEnd||object.PlanEnd)&&(fields.PlanEnd||object.PlanEnd)<(fields.PlanStart||object.PlanStart))throw Error('INVALID_DATE_RANGE');
    if(data.progress!==undefined){const progress=Number(data.progress);if(!Number.isFinite(progress)||progress<0||progress>100)throw Error('INVALID_PROGRESS');fields.Progress=progress;}
    if(data.status!==undefined){if(['Запланирован','Подтверждён','Подтверждён клиентом','Готов к монтажу','В работе','Приостановлен','Работы завершены','Завершён','Закрыт 100%','Отменён'].indexOf(data.status)<0)throw Error('INVALID_STATUS');if(data.status==='В работе')opReady_(object.ID);fields.Status=data.status;if(data.status==='В работе'&&!object.ActualStart)fields.ActualStart=opToday_();if(['Работы завершены','Завершён','Закрыт 100%'].indexOf(data.status)>=0&&!object.ActualEnd)fields.ActualEnd=opToday_();}
    if(data.client!==undefined||data.phone!==undefined){const client=opFind_('Clients',object.Client_ID);if(!client)throw Error('CLIENT_NOT_FOUND');const cf={};if(data.client!==undefined)cf.Name=opRequired_(data.client);if(data.phone!==undefined)cf.Phone=opStr_(data.phone);changes.push(opChange_('Clients',client,cf));}
    changes.push(opChange_('Objects',object,fields));result={id:object.ID,revision:Number(object.Revision||1)+1};
  }else if(action==='createInstaller'){
    if(!opAdmin_(auth)&&opRole_(auth)!=='PARTNER')throw Error('FORBIDDEN_EMPLOYEES');
    const name=opRequired_(data.name),phone=opStr_(data.phone).trim();
    if(opRows_('Employees').some(e=>opStr_(e.Name).trim().toLowerCase()===name.toLowerCase()&&(phone===''||opStr_(e.Phone).trim()===phone)))throw Error('EMPLOYEE_ALREADY_EXISTS');
    const id=opId_('EMP');changes.push(opChange_('Employees',null,{
      ID:id,Name:name,Phone:phone||null,Role:'INSTALLER',Active:true,WorkerKind:'STAFF',
      Address:opStr_(data.address).trim()||null,RelativeName:opStr_(data.relativeName).trim()||null,
      RelativePhone:opStr_(data.relativePhone).trim()||null,ServiceContractSigned:opYes_(data.serviceContractSigned),
      ProfileUpdatedAt:new Date().toISOString()
    }));result={id:id};
  }else if(action==='updateInstallerProfile'){
    if(!opAdmin_(auth)&&opRole_(auth)!=='PARTNER')throw Error('FORBIDDEN_EMPLOYEES');
    const employee=opFind_('Employees',opRequired_(data.installerId));if(!employee)throw Error('INSTALLER_NOT_FOUND');
    if(Number(data.expectedRevision)!==Number(employee.Revision||1))throw Error('REVISION_CONFLICT');
    const fields={ProfileUpdatedAt:new Date().toISOString()};
    if(data.name!==undefined)fields.Name=opRequired_(data.name);
    if(data.phone!==undefined)fields.Phone=opStr_(data.phone).trim()||null;
    if(data.address!==undefined)fields.Address=opStr_(data.address).trim()||null;
    if(data.relativeName!==undefined)fields.RelativeName=opStr_(data.relativeName).trim()||null;
    if(data.relativePhone!==undefined)fields.RelativePhone=opStr_(data.relativePhone).trim()||null;
    if(data.serviceContractSigned!==undefined)fields.ServiceContractSigned=opYes_(data.serviceContractSigned);
    changes.push(opChange_('Employees',employee,fields));result={id:employee.ID,revision:Number(employee.Revision||1)+1};
  }else if(action==='payInstaller'){
    if(!opFinance_(auth))throw Error('FORBIDDEN_FINANCE');
    const installer=opFind_('Employees',opRequired_(data.installerId));if(!installer)throw Error('INSTALLER_NOT_FOUND');
    const object=opObjectAccess_(auth,opRequired_(data.objectId)),assignments=opRows_('Assignments').filter(a=>a.Installer_ID===installer.ID&&a.Object_ID===object.ID);
    if(!assignments.length)throw Error('ASSIGNMENT_NOT_FOUND');
    const agreed=assignments.reduce((n,a)=>n+opAssignmentAmount_(a),0),paid=opPayrollPaid_(installer.ID,object.ID),amount=Number(data.amount),remaining=Math.max(0,agreed-paid);
    if(!Number.isFinite(amount)||amount<=0||amount>remaining)throw Error('PAYMENT_EXCEEDS_REMAINING');
    const paymentType=data.paymentType==='FINAL'?'FINAL':'PARTIAL';if(paymentType==='FINAL'&&(!opClosedStatus_(object.Status)||Math.abs(amount-remaining)>0.009))throw Error('FINAL_PAYMENT_REQUIRES_CLOSED_OBJECT');
    const rows=opRows_('SalaryAccruals').filter(r=>r.Installer_ID===installer.ID&&r.Object_ID===object.ID),row=rows[0];
    if(row)changes.push(opChange_('SalaryAccruals',row,{'Выплачено':(Number(row['Выплачено'])||0)+amount}));
    else{const id=opId_('SAL');changes.push(opChange_('SalaryAccruals',null,{ID:id,Accrual_ID:id,Object_ID:object.ID,Installer_ID:installer.ID,'Монтажник':installer.Name||installer['ФИО'],'Начислено':opClosedStatus_(object.Status)?agreed:0,'Выплачено':amount}));}
    result={installerId:installer.ID,objectId:object.ID,paid:paid+amount,remaining:Math.max(0,remaining-amount),paymentType:paymentType};
  }else if(action==='addLead'||action==='addSurvey'){
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER','MANAGER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_CRM');const table=action==='addLead'?'Leads':'Measurements',id=opId_(action==='addLead'?'LEAD':'SURVEY'),date=opDateRequired_(data.date||opToday_());
    const record={ID:id,Date:date,Name:opRequired_(data.client),Phone:data.phone||null,Manager_ID:opRole_(auth)==='MANAGER'?auth.userId:null,Engineer_ID:opRole_(auth)==='ENGINEER'?auth.userId:null,'Дата':date,'Клиент':data.client,'Телефон':data.phone||null,'Вид работ':data.workType||null,'Комментарий':data.comment||null};
    if(table==='Leads'){record.Lead_ID=id;record['Статус']='Новый';record['Источник']=data.source||null;}else{record.Survey_ID=id;record['Адрес']=opRequired_(data.address);record.ConvertedToObject=false;}changes.push(opChange_(table,null,record));result={id:id};
  }else if(action==='updateDailyProgress'){
    if(['OWNER','ADMIN','PARTNER','ENGINEER','INSTALLER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_DAILY_PROGRESS');
    const day=opFind_('DailyPlans',opRequired_(data.dayPlanId));if(!day)throw Error('RECORD_NOT_FOUND');
    const object=opObjectAccess_(auth,day.Object_ID);if(data.objectId&&data.objectId!==object.ID)throw Error('OBJECT_MISMATCH');
    if(opRole_(auth)==='INSTALLER'&&!opRows_('Assignments').some(a=>a.Object_ID===object.ID&&a.Installer_ID===auth.employeeId))throw Error('FORBIDDEN_DAILY_PROGRESS');
    if(Number(data.expectedRevision)!==Number(day.Revision||1))throw Error('REVISION_CONFLICT');
    const actual=Number(data.actualQty);if(!Number.isFinite(actual)||actual<0)throw Error('INVALID_QUANTITY');
    const state=data.status==='Выполнено'?'Выполнено':'В работе';
    changes.push(opChange_('DailyPlans',day,{'Факт объём':actual,'Отчёт дня':data.comment||null,'Статус':state,'Обновлено':now,'Обновил':auth.name||auth.userId}));
    result={id:day.ID,revision:Number(day.Revision||1)+1};
  }else if(action==='uploadMedia'){
    const object=opObjectAccess_(auth,data.objectId);if(['OWNER','ADMIN','PARTNER','ENGINEER','INSTALLER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_MEDIA');
    if(!/^image\/(jpeg|png|webp)$/.test(opStr_(data.mimeType)))throw Error('INVALID_IMAGE_TYPE');
    const bytes=Utilities.base64Decode(opRequired_(body.base64));if(bytes.length>6*1024*1024)throw Error('IMAGE_TOO_LARGE');
    const folder=DriveApp.getFolderById(OP.mediaFolder),name='TK4-'+opHash_(key),files=folder.getFilesByName(name);let file;
    if(files.hasNext()){file=files.next();if(opHash_(Utilities.base64Encode(file.getBlob().getBytes()))!==data.contentHash)throw Error('IDEMPOTENCY_CONFLICT');}
    else file=folder.createFile(Utilities.newBlob(bytes,data.mimeType,name));
    const id=opId_('MEDIA');changes.push(opChange_('Media',null,{ID:id,Media_ID:id,Object_ID:object.ID,Installer_ID:opRole_(auth)==='INSTALLER'?auth.employeeId:null,'Дата':opToday_(),'Тип':data.mediaType||'Фото','Этап':data.stage||null,Drive_File_ID:file.getId(),URL:file.getUrl(),'Комментарий':data.comment||null,UploadedBy:auth.userId,CreatedAt:now}));result={id:id,url:file.getUrl()};
  }else if(action==='saveTechTask'){
    const task=opNewTask_(auth,data);changes=task.changes;result=task.result;
  }else throw Error('ACTION_NOT_SUPPORTED');
  return Object.assign({ok:true},opTransaction_(auth,key,hash,action,reason,changes,result));
}
function opReady_(id){const t=opRows_('TechTasks').find(r=>r.Object_ID===id);if(!t||!opYes_(t['Материалы готовы'])||!opRows_('Assignments').some(r=>r.Object_ID===id)||!opRows_('DailyPlans').some(r=>r.TechTask_ID===t.ID))throw Error('OBJECT_NOT_READY');}
function opNewTask_(auth,data){
  if(!opAdmin_(auth)&&['PARTNER','ENGINEER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_TECH');const object=opObjectAccess_(auth,data.objectId);
  if(opRows_('TechTasks').some(r=>r.Object_ID===object.ID)||data.techTaskId)throw Error('TECH_TASK_EDIT_REQUIRES_PATCH');
  if(['Подтверждён','Подтверждён клиентом','Готов к монтажу','В работе'].indexOf(object.Status)<0||!opDate_(object.PlanStart))throw Error('CONFIRMED_OBJECT_REQUIRED');
  const start=opDateRequired_(data.planStart),end=opDateRequired_(data.planEnd);if(end<start)throw Error('INVALID_DATE_RANGE');
  const id=opId_('TZ'),changes=[],workers=data.assignments,days=data.days,payments=data.payments;
  if(!Array.isArray(workers)||!workers.length||!Array.isArray(days)||!days.length||!Array.isArray(payments))throw Error('TASK_DETAILS_REQUIRED');
  const received=opRows_('Income').filter(r=>r.Object_ID===object.ID).reduce((n,r)=>n+opIncomeSigned_(r),0);
  if(!Number.isSafeInteger(object.ContractMinor)||payments.reduce((n,p)=>n+opMinor_(p.plannedAmount),0)!==Math.max(0,object.ContractMinor-received))throw Error('PAYMENT_PLAN_TOTAL_MISMATCH');
  changes.push(opChange_('TechTasks',null,{ID:id,TechTask_ID:id,Object_ID:object.ID,'Статус ТЗ':data.materialsReady?'Готово':'Черновик',Engineer_ID:auth.userId,'Инженер':auth.name,'Вид работ':object.WorkType,'План начала':start,'План окончания':end,'Материалы готовы':!!data.materialsReady,'Комментарий':data.comment||null,'Технология':data.technology||null}));
  const workerIds=new Set();workers.forEach(w=>{const employee=opFind_('Employees',w.installerId);if(!employee||workerIds.has(employee.ID))throw Error('INVALID_INSTALLER');workerIds.add(employee.ID);const aid=opId_('ASN');changes.push(opChange_('Assignments',null,{ID:aid,Assignment_ID:aid,TechTask_ID:id,Object_ID:object.ID,Installer_ID:employee.ID,'Монтажник':employee.Name||employee['ФИО'],'Согласованная сумма':opRub_(opMinor_(w.agreedAmount)),'Назначен с':start,'Назначен до':end,'Статус':'Назначен'}));const cid=opId_('CAL');changes.push(opChange_('Calendar',null,{ID:cid,Calendar_ID:cid,Object_ID:object.ID,TechTask_ID:id,Installer_ID:employee.ID,'Монтажник':employee.Name||employee['ФИО'],'План начало':start,'План конец':end,'Статус':'Подтверждён'}));});
  days.forEach((d,i)=>{const date=opDateRequired_(d.date);if(date<start||date>end)throw Error('STAGE_OUTSIDE_OBJECT_DATES');const did=opId_('DAY');changes.push(opChange_('DailyPlans',null,{ID:did,DayPlan_ID:did,TechTask_ID:id,Object_ID:object.ID,'День №':Number(d.dayNo)||i+1,'Дата':date,'Задача':opRequired_(d.task),'Ед. изм.':opRequired_(d.unit),'План объём':opRub_(opMinor_(d.plannedQty)),'Описание работ':opRequired_(d.description),'На что обратить внимание':d.attention||null,'Факт объём':0,'Отчёт дня':null,'Статус':'План','Обновлено':null,'Обновил':null}));});
  payments.forEach((p,i)=>{const pid=opId_('PAY'),date=opDateRequired_(p.plannedDate),amount=opMinor_(p.plannedAmount);changes.push(opChange_('PaymentSchedule',null,{ID:pid,PaymentPlan_ID:pid,TechTask_ID:id,Object_ID:object.ID,Date:date,AmountMinor:amount,'Этап №':i+1,'Наименование этапа':opRequired_(p.stageName),'Плановая дата':date,'Плановая сумма':opRub_(amount)}));});
  return {changes:changes,result:{id:id,revision:1}};
}
function verifyOperationalDatabase(){
  opTables_={};const settings=opSettings_();if(settings.SOURCE_SPREADSHEET_ID!==OP.source||Number(settings.SCHEMA_VERSION)!==3)throw Error('MIGRATION_IDENTITY_MISMATCH');
  for(const name of ['Objects','Clients','Income','Expenses','CashTransfers','AccountablePersons','Users','AuditLog','TechTasks','Assignments','PaymentSchedule','DailyPlans','Calendar','Employees','SalaryAccruals','Leads','Measurements','Media','DataQuality'])opRows_(name);
  const people=opRows_('AccountablePersons'),balances=opBalances_(people,opRows_('Income'),opRows_('Expenses'),opRows_('CashTransfers'));
  if(people.some(p=>balances[p.Name].balance!==opRub_(p.CalculatedBalanceMinor)))throw Error('MIGRATION_BALANCE_MISMATCH');
  const report={ok:true,spreadsheetId:OP.book,objects:opRows_('Objects').length,income:opRows_('Income').length,expenses:opRows_('Expenses').length,transfers:opRows_('CashTransfers').length,users:opRows_('Users').length,balancesReconciled:true};
  if(settings.MIGRATION_STATUS==='READY'){
    const user=opRows_('Users').find(u=>opYes_(u.Active)&&['OWNER','ADMIN'].indexOf(u.Role)>=0);if(!user)throw Error('OWNER_REQUIRED');
    const bootstrap=opBootstrap_({userId:user.ID,name:user.Name,role:user.Role,finance:opYes_(user.Finance),employeeId:user.Employee_ID||user.ID},'Месяц');
    if(!bootstrap.ok||bootstrap.objects.length!==report.objects)throw Error('BOOTSTRAP_MISMATCH');
    report.bootstrapVerified=true;report.techTasks=bootstrap.techTasks.length;report.assignments=bootstrap.assignments.length;
  }
  console.log(JSON.stringify(report));return report;
}
function dailyMaintenance(){return {ok:true,api:OP.version};}

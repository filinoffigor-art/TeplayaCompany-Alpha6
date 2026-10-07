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
function opHasColumns_(name,columns){try{const headers=opTable_(name).headers;return columns.every(column=>headers.indexOf(column)>=0);}catch(error){return false;}}
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
function opAccountableBalanceMinor_(person,income,expenses,transfers){
  let balance=opNumber_(person.OpeningMinor),received=0,spent=0,incoming=0,outgoing=0;
  if(balance==null)return null;
  income.forEach(r=>{if(r.Recipient_ID===person.ID)received+=opIncomeSigned_(r);});
  expenses.forEach(r=>{if(r.Person_ID===person.ID)spent+=opExpenseSigned_(r);});
  transfers.filter(r=>r.Status==='CONFIRMED').forEach(r=>{if(r.ToPerson_ID===person.ID)incoming+=r.AmountMinor;if(r.Person_ID===person.ID)outgoing+=r.AmountMinor;});
  return balance+received-spent+incoming-outgoing;
}
function opBalances_(people,income,expenses,transfers){
  const out={};people.forEach(p=>{
    let opening=opNumber_(p.OpeningMinor),received=0,spent=0,incoming=0,outgoing=0,reimbursed=0;
    income.forEach(r=>{if(r.Recipient_ID===p.ID)received+=opIncomeSigned_(r);});
    expenses.forEach(r=>{if(r.Person_ID===p.ID)spent+=opExpenseSigned_(r);});
    transfers.filter(r=>r.Status==='CONFIRMED').forEach(r=>{
      if(r.ToPerson_ID===p.ID){incoming+=r.AmountMinor;if(r.Type==='Возмещение собственных средств')reimbursed+=r.AmountMinor;}
      if(r.Person_ID===p.ID)outgoing+=r.AmountMinor;
    });
    const balance=opening==null?null:opening+received-spent+incoming-outgoing;
    out[p.Name]={Accountable_ID:p.ID,revision:Number(p.Revision||1),balance:opRub_(balance),opening:opRub_(opening),clientReceipts:opRub_(received),expenses:opRub_(spent),incomingTransfers:opRub_(incoming),outgoingTransfers:opRub_(outgoing),receivedFromCompany:opRub_(incoming),personalInvested:null,personalReimbursed:opRub_(reimbursed)};
  });return out;
}

function opAssignmentDto_(r,payroll){
  const accrualRows=payroll.filter(p=>p.Assignment_ID===r.ID||p.Assignment_ID===r.Assignment_ID);
  const accruedMinor=Number.isSafeInteger(r.AccruedMinor)?r.AccruedMinor:accrualRows.reduce((n,p)=>{
    if(Number.isSafeInteger(p.AccruedMinor))return n+p.AccruedMinor;
    const value=Number(p['Начислено']);return n+(Number.isFinite(value)?Math.round(value*100):0);
  },0);
  const paidMinor=Number.isSafeInteger(r.PaidMinor)?r.PaidMinor:accrualRows.reduce((n,p)=>{
    if(Number.isSafeInteger(p.PaidMinor))return n+p.PaidMinor;
    const value=Number(p['Выплачено']);return n+(Number.isFinite(value)?Math.round(value*100):0);
  },0);
  const unallocated=Number.isSafeInteger(r.UnallocatedFixedMinor)?r.UnallocatedFixedMinor:null;
  const paymentType=r.Payment_Type||null;
  const remaining=paymentType==='FIXED'?unallocated:(accruedMinor||paidMinor?Math.max(0,accruedMinor-paidMinor):0);
  return {
    ID:r.ID,Assignment_ID:r.Assignment_ID||r.ID,Installer_ID:r.Installer_ID,Object_ID:r.Object_ID,TechTask_ID:r.TechTask_ID||null,installerName:r['Монтажник']||null,agreedAmount:opNumber_(r['Согласованная сумма']),['Монтажник']:r['Монтажник']||null,['Согласованная сумма']:opNumber_(r['Согласованная сумма']),
    startDate:opDate_(r.Period_Start||r['Назначен с']),endDate:opDate_(r.Period_End||r['Назначен до']),
    actualDays:Number(r.Actual_Days||0),paymentType:paymentType,dailyRate:opRub_(opNumber_(r.DailyRateMinor)),
    accrued:opRub_(accruedMinor),paid:opRub_(paidMinor),remaining:opRub_(remaining),
    revision:Number(r.Revision||1),Obligation_ID:r.Obligation_ID||null,unallocatedFixedAmount:opRub_(unallocated),
    status:r['Статус']||r.Status||null,parentAssignmentId:r.Parent_Assignment_ID||null
  };
}
function opAssignmentForRole_(r,payroll,role){const dto=opAssignmentDto_(r,payroll);if(role==='MANAGER'){for(const key of ['agreedAmount','Согласованная сумма','dailyRate','accrued','paid','remaining','unallocatedFixedAmount'])delete dto[key];}return dto;}
function opDateAdd_(date,days){const d=new Date(opDateRequired_(date)+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+days);return d.toISOString().slice(0,10);}
function opRangesOverlap_(aStart,aEnd,bStart,bEnd){
  const aS=opDate_(aStart),bS=opDate_(bStart);if(!aS||!bS)return false;
  const aE=opDate_(aEnd)||'9999-12-31',bE=opDate_(bEnd)||'9999-12-31';
  return aS<=bE&&bS<=aE;
}
function opAssignmentRange_(r){return {start:opDate_(r.Period_Start||r['Назначен с']),end:opDate_(r.Period_End||r['Назначен до'])};}
function opMinorZero_(x){const n=Number(x);if(x==null||x===''||!Number.isFinite(n)||n<0||!Number.isSafeInteger(Math.round(n*100)))throw Error('INVALID_AMOUNT');return Math.round(n*100);}
function opAssignmentSchemaReady_(){return opHasColumns_('Assignments',['Parent_Assignment_ID','Payment_Type','DailyRateMinor','Period_Start','Period_End','Actual_Days','AccruedMinor','PaidMinor','UnallocatedFixedMinor','Obligation_ID','Closed_At'])&&opHasColumns_('Calendar',['Assignment_ID','Period_Start','Period_End'])&&opHasColumns_('SalaryAccruals',['Assignment_ID','Obligation_ID','Accrual_Event_ID','Expense_ID','Recognition_Basis','AccruedMinor','PaidMinor']);}
function opHiredWorkerSchemaReady_(){return opAssignmentSchemaReady_()&&opHasColumns_('WorkDays',['WorkDay_ID','Installer_ID','Assignment_ID','Object_ID','Work_Date','Actual_Days','DailyRateMinor','Accrual_Event_ID','CreatedAt','Actor_ID','Revision']);}
function opSafeMultiply_(a,b){const n=Number(a)*Number(b);if(!Number.isSafeInteger(n))throw Error('INVALID_AMOUNT');return n;}

function opBootstrap_(auth,period){
  const settings=opSettings_();if(settings.MIGRATION_STATUS!=='READY')throw Error('MIGRATION_NOT_READY');
  const range=opPeriod_(period),finance=opFinance_(auth),role=opRole_(auth),allObjects=opRows_('Objects'),objects=opAllowedObjects_(auth,allObjects),ids=new Set(objects.map(o=>o.ID));
  const income=opRows_('Income'),expenses=opRows_('Expenses'),transfers=opRows_('CashTransfers'),clients=opRows_('Clients'),people=opRows_('AccountablePersons');
  const byObject=rows=>rows.filter(r=>ids.has(r.Object_ID));
  const full=opAdmin_(auth)||role==='PARTNER';
  const employees=opRows_('Employees').filter(r=>full||r.ID===auth.employeeId||role==='ENGINEER'&&opRows_('Assignments').some(a=>ids.has(a.Object_ID)&&a.Installer_ID===r.ID));
  const leads=opRows_('Leads').filter(r=>full||r.Manager_ID===auth.userId),surveys=opRows_('Measurements').filter(r=>full||r.Manager_ID===auth.userId||r.Engineer_ID===auth.userId);
  const kpis={objectsInWork:objects.filter(o=>o.Status==='В работе').length,objectsInWorkAmount:null,leads:leads.filter(r=>opInPeriod_(r.Date,range)).length,surveysCompleted:null,surveysScheduled:null,contracts:null,averageCheck:null,closedProfit:null};
  const active=objects.filter(o=>o.Status==='В работе');if(role!=='INSTALLER'&&active.every(o=>Number.isSafeInteger(o.ContractMinor)))kpis.objectsInWorkAmount=opRub_(active.reduce((n,o)=>n+o.ContractMinor,0));
  const publicObjects=objects.map(o=>{const client=clients.find(c=>c.ID===o.Client_ID)||{};const paid=income.filter(i=>i.Object_ID===o.ID).reduce((n,i)=>n+opIncomeSigned_(i),0);
    const r={id:o.ID,clientId:o.Client_ID,address:o.Address,client:client.Name,phone:client.Phone,workType:o.WorkType,status:o.Status,progress:opNumber_(o.Progress),planStart:opDate_(o.PlanStart),planEnd:opDate_(o.PlanEnd),factStart:opDate_(o.ActualStart),factEnd:opDate_(o.ActualEnd),engineer:o.Engineer_ID,responsible:o.LegacyResponsible,brigade:o.LegacyCrew,revision:o.Revision||1};
    if(role!=='INSTALLER'){r.contract=opRub_(o.ContractMinor);r.paid=opRub_(paid);r.remaining=o.ContractMinor==null?null:opRub_(o.ContractMinor-paid);}return r;});
  const payments=byObject(opRows_('PaymentSchedule')).map(p=>{const received=income.filter(i=>i.PaymentPlan_ID===p.ID).reduce((n,i)=>n+opIncomeSigned_(i),0),amount=opNumber_(p.AmountMinor),left=amount==null?null:Math.max(0,amount-received),date=opDate_(p.Date||p['Плановая дата']);
    return {id:p.ID,objectId:p.Object_ID,techTaskId:p.TechTask_ID||null,revision:Number(p.Revision||1),plannedDate:date,plannedAmount:opRub_(amount),actualPaid:opRub_(received),remaining:opRub_(left),stageName:p['Наименование этапа']||p.StageName,status:left==null?'Не заполнено':left===0?'Оплачено':date&&date<opToday_()?'Просрочено':'План',overdueDays:date&&date<opToday_()&&left>0?Math.floor((new Date(opToday_())-new Date(date))/86400000):0};});
  const payrollRows=opRows_('SalaryAccruals'),workforceWrite=(opAdmin_(auth)||role==='PARTNER'||role==='ENGINEER')&&opAssignmentSchemaReady_();
  const response={ok:true,api:OP.version,serverTime:new Date().toISOString(),period:period,user:{id:auth.userId,name:auth.name,role:role,finance:finance,admin:opAdmin_(auth)},
    scope:{enforced:true,userId:auth.userId},coverage:{from:settings.COVERAGE_FROM,to:settings.COVERAGE_TO,complete:range.from>=settings.COVERAGE_FROM&&range.to<=settings.COVERAGE_TO},
    capabilities:{rbacV1:true,financialEditsV1:finance,financialDeletesV1:finance,personalReimbursementsV1:finance&&people.length>1,assignmentPeriodsV1:workforceWrite,hiredWorkersV1:workforceWrite&&opHiredWorkerSchemaReady_(),techTaskEditsV2:(opAdmin_(auth)||role==='PARTNER'||role==='ENGINEER')},kpis:kpis,objects:publicObjects,
    employees:employees.map(r=>({id:r.ID,name:r.Name||r['ФИО'],role:r.Role||r['Роль'],active:r.Active==null?'true':opStr_(r.Active),phone:r.Phone||r['Телефон'],workerKind:r.WorkerKind,revision:Number(r.Revision||1)})),
    leads:leads.map(opPublicRow_),surveys:surveys.map(opPublicRow_),techTasks:byObject(opRows_('TechTasks')).map(opPublicRow_),dayPlans:byObject(opRows_('DailyPlans')).filter(r=>r.TechTask_ID).map(opPublicRow_),assignments:byObject(opRows_('Assignments')).map(r=>opAssignmentForRole_(r,payrollRows,role)),calendar:byObject(opRows_('Calendar')).map(opPublicRow_),media:byObject(opRows_('Media')).map(opPublicRow_),paymentPlan:role==='INSTALLER'?[]:payments,
    payroll:payrollRows.filter(r=>full||role==='INSTALLER'&&r.Installer_ID===auth.employeeId).map(opPublicRow_),attention:[],lists:{}};
  if(role==='INSTALLER'){response.assignments=response.assignments.filter(r=>r.Installer_ID===auth.employeeId);response.calendar=response.calendar.filter(r=>r.Installer_ID===auth.employeeId);response.techTasks=response.techTasks.map(r=>({TechTask_ID:r.TechTask_ID,Object_ID:r.Object_ID,'План начала':r['План начала'],'План окончания':r['План окончания'],'Комментарий':r['Комментарий']}));}
  if(finance){
    const pi=income.filter(r=>opInPeriod_(r.Date,range)),pe=expenses.filter(r=>opInPeriod_(r.Date,range));
    kpis.turnover=opRub_(pi.reduce((n,r)=>n+opIncomeSigned_(r),0));kpis.expenses=opRub_(pe.reduce((n,r)=>n+opExpenseSigned_(r),0));kpis.payrollAccrued=opRub_(pe.filter(r=>r.Status==='ACCRUED'&&r.Installer_ID).reduce((n,r)=>n+(Number.isSafeInteger(r.AmountMinor)?r.AmountMinor:0),0));
    const paid=pi.filter(r=>r.Status==='CONFIRMED'&&r.Operation==='Приход');kpis.averagePayment=paid.length?opRub_(Math.round(paid.reduce((n,r)=>n+r.AmountMinor,0)/paid.length)):null;
    const outgoing=pe.filter(r=>r.Status==='CONFIRMED');kpis.averageExpense=outgoing.length?opRub_(Math.round(outgoing.reduce((n,r)=>n+r.AmountMinor,0)/outgoing.length)):null;
    response.accountable=opBalances_(people,income,expenses,transfers);
    response.fundingAccounts=people.map(p=>({id:p.ID,name:p.Name,balance:opRub_(opAccountableBalanceMinor_(p,income,expenses,transfers)),revision:Number(p.Revision||1)})).filter(p=>p.balance!=null&&p.balance>0);
    const balances=Object.values(response.accountable).map(a=>a.balance);kpis.totalAccountable=balances.every(x=>x!=null)?balances.reduce((n,x)=>n+x,0):null;
    response.income=pi.map(r=>opIncomeDto_(r,objects,people));response.expenses=pe.concat(transfers.filter(r=>opInPeriod_(r.Date,range))).map(r=>opExpenseDto_(r,objects,people));response.historyComplete=true;
    kpis.debt=payments.some(p=>p.remaining==null)?null:payments.filter(p=>p.status==='Просрочено').reduce((n,p)=>n+p.remaining,0);
    kpis.plannedReceipts=payments.some(p=>p.remaining==null)?null:payments.filter(p=>p.status==='План').reduce((n,p)=>n+p.remaining,0);
    response.financeAnalytics={expenseCategories:opGroupExpenses_(pe,'Category'),cashFlow:null,suppliers:null};
  }
  const quality=opRows_('DataQuality').filter(r=>r.Status==='OPEN'&&(full||ids.has(r.EntityID)));
  response.attention=quality.map(r=>({id:r.ID,title:r.Message,subtitle:r.EntityType+' · '+r.Field,severity:r.Severity==='CRITICAL'?'red':'yellow',target:r.EntityType==='Objects'?'object:'+r.EntityID:'kpi:notifications'}));
  return response;
}
function opPublicRow_(r){const out={};Object.keys(r).forEach(k=>{if(!/^(RawJSON|LiveRawJSON|Source|LegacyID|Migration|Token|Pairing)/.test(k))out[k]=r[k];});return out;}
function opIncomeDto_(r,objects,people){const o=objects.find(o=>o.ID===r.Object_ID),p=people.find(p=>p.ID===r.Recipient_ID);return {id:r.ID,entity:'Income',revision:r.Revision||1,objectId:r.Object_ID,object:o?o.Address:'Не заполнено',date:r.Date,operation:r.Operation,paymentKind:r.PaymentKind,amount:opRub_(r.AmountMinor),recipient:p?p.Name:null,comment:r.Comment,status:r.Status};}
function opExpenseDto_(r,objects,people){const o=objects.find(o=>o.ID===r.Object_ID),p=people.find(p=>p.ID===r.Person_ID);return {id:r.ID,entity:r.ToPerson_ID?'CashTransfers':'Expenses',revision:r.Revision||1,objectId:r.Object_ID,object:o?o.Address:'',date:r.Date,type:r.Type,article:r.Category,description:r.Description,amount:opRub_(r.AmountMinor),responsible:p?p.Name:null,status:r.Status};}
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
  const user=opFind_('Users',opRequired_(body.userId,'AUTH_REQUIRED')),device=opRequired_(body.deviceId,'AUTH_REQUIRED');
  const cache=CacheService.getScriptCache(),key='pair:'+opHash_(body.userId+':'+device),attempt=Number(cache.get(key)||0);
  if(attempt>=10)throw Error('PAIRING_RATE_LIMIT');cache.put(key,String(attempt+1),300);
  if(!user||!opYes_(user.Active)||!user.PairingCode||!opEqualSecret_(user.PairingCode,body.pairingCode))throw Error('PAIRING_DENIED');
  const token=Utilities.getUuid().replace(/-/g,'')+Utilities.getUuid().replace(/-/g,'');
  opWrite_('Users',Object.assign({},user,{PairingCode:user.PairingCode,TokenHash:opHash_(token),Device_ID:device,UpdatedAt:new Date().toISOString()}));
  return {ok:true,userId:user.ID,name:user.Name,role:user.Role,token:token};
}
function opPerson_(value){const rows=opRows_('AccountablePersons'),byId=rows.find(r=>r.ID===value);if(byId)return byId;
  const matches=rows.filter(r=>r.Name===value);if(matches.length!==1)throw Error('ACCOUNTABLE_ID_REQUIRED');return matches[0];}
function opObjectAccess_(auth,id){const o=opAllowedObjects_(auth,opRows_('Objects')).find(r=>r.ID===id);if(!o)throw Error('OBJECT_NOT_FOUND');return o;}
function opRequireAction_(auth,action){
  const role=opRole_(auth),allow=roles=>opAdmin_(auth)||roles.indexOf(role)>=0;
  if(['addIncome','addExpense','transfer','editFinance','deleteFinance','reimbursePersonalFunds'].indexOf(action)>=0){if(!opFinance_(auth))throw Error('FORBIDDEN_FINANCE');return;}
  if(['moveInstaller','createHiredWorkerDay','addHiredWorkerDay'].indexOf(action)>=0){if(!allow(['PARTNER','ENGINEER']))throw Error('FORBIDDEN_WORKFORCE');return;}
  if(action==='promoteHiredWorker'){if(!opAdmin_(auth))throw Error('FORBIDDEN_ADMIN');return;}
  if(['createObject','convertSurveyToObject'].indexOf(action)>=0){if(!allow(['PARTNER','ENGINEER','MANAGER']))throw Error('FORBIDDEN_OBJECTS');return;}
  if(['updateObject','updateObjectStatus'].indexOf(action)>=0){if(!allow(['PARTNER','ENGINEER']))throw Error('FORBIDDEN_OBJECTS');return;}
  if(['addLead','addSurvey'].indexOf(action)>=0){if(!allow(['PARTNER','ENGINEER','MANAGER']))throw Error('FORBIDDEN_CRM');return;}
  if(action==='uploadMedia'){if(!allow(['PARTNER','ENGINEER','INSTALLER']))throw Error('FORBIDDEN_MEDIA');return;}
  if(['saveTechTask','saveTechTaskV2'].indexOf(action)>=0){if(!allow(['PARTNER','ENGINEER']))throw Error('FORBIDDEN_TECH');return;}
  throw Error('ACTION_NOT_SUPPORTED');
}
function opChange_(table,before,fields){return {table:table,before:before||null,after:Object.assign({},before||{},fields,{Revision:before?Number(before.Revision||1)+1:1})};}
function opDateRequired_(value){const d=opDate_(value);if(!d)throw Error('INVALID_DATE');return d;}
function opPick_(source,keys){const out={};keys.forEach(k=>{if(source[k]!==undefined)out[k]=source[k];});return out;}
function opCommand_(auth,body){
  const action=opStr_(body.action),requestId=opRequired_(body.idempotencyKey||body.requestId,'REQUEST_ID_REQUIRED');if(requestId.length>120)throw Error('INVALID_REQUEST_ID');opRequireAction_(auth,action);
  const allowed=['amount','objectId','paymentKind','operation','method','payer','recipient','comment','documentNo','paymentPlanId','type','article','description','quantity','unit','unitPrice','paymentStatus','accountable','installerId','from','to','date','client','phone','address','workType','brigade','status','planStart','planEnd','progress','contract','channel','responsible','expectedRevision','reason','entity','entityId','surveyId','source','owner','engineer','engineerId','materialsReady','technology','area','safetyNotes','days','assignments','payments','techTaskId','mimeType','mediaType','stage','Accountable_ID','Funding_Account_ID','Installer_ID','Assignment_ID','Object_ID','transitionDate','returnDate','paymentType','dailyRate','accrueNow','deferAmount','closeObligation','name','workDate','actualDays'];
  const data=opPick_(body,allowed);if(action==='uploadMedia')data.contentHash=opHash_(opStr_(body.base64));
  const hash=opHash_(opCanonical_({action:action,data:data})),key=auth.userId+':'+requestId;
  const old=opRows_('AuditLog').find(r=>r.Transaction_ID===key);if(old){if(old.RequestHash!==hash)throw Error('IDEMPOTENCY_CONFLICT');return Object.assign({ok:true},typeof old.ResultJSON==='string'?JSON.parse(old.ResultJSON):old.ResultJSON);}
  let changes=[],result={},reason=opStr_(data.reason),now=new Date().toISOString();
  if(['addIncome','addExpense','transfer','editFinance','deleteFinance','reimbursePersonalFunds'].indexOf(action)>=0&&!opFinance_(auth))throw Error('FORBIDDEN_FINANCE');
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
  }else if(action==='reimbursePersonalFunds'){
    reason=opRequired_(data.reason,'REIMBURSEMENT_REASON_REQUIRED');
    const target=opFind_('AccountablePersons',opRequired_(data.Accountable_ID,'ACCOUNTABLE_ID_REQUIRED'));
    const source=opFind_('AccountablePersons',opRequired_(data.Funding_Account_ID,'FUNDING_ACCOUNT_REQUIRED'));
    if(!target||!source)throw Error('ACCOUNTABLE_NOT_FOUND');
    if(target.ID===source.ID)throw Error('REIMBURSEMENT_SOURCE_EQUALS_TARGET');
    if(Number(data.expectedRevision)!==Number(target.Revision||1))throw Error('REVISION_CONFLICT');
    const amount=opMinor_(data.amount),incomeNow=opRows_('Income'),expenseNow=opRows_('Expenses'),transferNow=opRows_('CashTransfers');
    const targetBalance=opAccountableBalanceMinor_(target,incomeNow,expenseNow,transferNow),sourceBalance=opAccountableBalanceMinor_(source,incomeNow,expenseNow,transferNow);
    if(targetBalance==null||sourceBalance==null)throw Error('ACCOUNTABLE_BALANCE_UNKNOWN');
    if(amount>Math.max(0,-targetBalance))throw Error('REIMBURSEMENT_EXCEEDS_OUTSTANDING');
    if(sourceBalance<amount)throw Error('INSUFFICIENT_FUNDING');
    const id=opId_('REIMB');
    changes.push(opChange_('CashTransfers',null,{ID:id,Date:opToday_(),Type:'Возмещение собственных средств',Category:'Возмещение личных средств',Description:'Возмещение собственных средств',AmountMinor:amount,Person_ID:source.ID,ToPerson_ID:target.ID,LegacyPerson:source.Name,Status:'CONFIRMED',LegacyStatus:'Оплачено',Comment:reason,DataStatus:'IMPORTED'}));
    changes.push(opChange_('AccountablePersons',target,{}));changes.push(opChange_('AccountablePersons',source,{}));
    result={id:id,Accountable_ID:target.ID,Funding_Account_ID:source.ID,amount:opRub_(amount),balanceAfter:opRub_(targetBalance+amount),revision:Number(target.Revision||1)+1};
  }else if(action==='moveInstaller'){
    if(!opAssignmentSchemaReady_())throw Error('WORKFORCE_SCHEMA_NOT_READY');
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_WORKFORCE');
    reason=opRequired_(data.reason,'MOVE_REASON_REQUIRED');
    const installer=opFind_('Employees',opRequired_(data.Installer_ID,'INSTALLER_ID_REQUIRED'));
    const source=opFind_('Assignments',opRequired_(data.Assignment_ID,'ASSIGNMENT_ID_REQUIRED'));
    if(source)opObjectAccess_(auth,source.Object_ID);
    const target=opObjectAccess_(auth,opRequired_(data.Object_ID,'OBJECT_ID_REQUIRED'));
    if(!installer)throw Error('INSTALLER_NOT_FOUND');
    if(!source||source.Installer_ID!==installer.ID)throw Error('ASSIGNMENT_NOT_FOUND');
    if(source.Object_ID===target.ID)throw Error('MOVE_TO_SAME_OBJECT');
    if(Number(data.expectedRevision)!==Number(source.Revision||1))throw Error('REVISION_CONFLICT');
    const transition=opDateRequired_(data.transitionDate),returnDate=data.returnDate?opDateRequired_(data.returnDate):null;
    if(returnDate&&returnDate<=transition)throw Error('INVALID_RETURN_DATE');
    const sourceRange=opAssignmentRange_(source),closedEnd=opDateAdd_(transition,-1);
    if(sourceRange.start&&transition<sourceRange.start)throw Error('MOVE_BEFORE_ASSIGNMENT_START');
    if(sourceRange.end&&transition>sourceRange.end)throw Error('MOVE_AFTER_ASSIGNMENT_END');
    const targetEnd=returnDate?opDateAdd_(returnDate,-1):null,returnEnd=sourceRange.end&&returnDate?sourceRange.end:null;
    if(returnDate&&returnEnd&&returnDate>returnEnd)throw Error('RETURN_AFTER_ASSIGNMENT_END');
    const others=opRows_('Assignments').filter(r=>r.ID!==source.ID&&r.Installer_ID===installer.ID);
    const overlaps=(start,end)=>others.some(r=>{const range=opAssignmentRange_(r);return range.start&&opRangesOverlap_(start,end,range.start,range.end);});
    if(overlaps(transition,targetEnd)||(returnDate&&overlaps(returnDate,returnEnd)))throw Error('ASSIGNMENT_OVERLAP');
    const type=opRequired_(data.paymentType,'PAYMENT_TYPE_REQUIRED');if(['DAILY','FIXED'].indexOf(type)<0)throw Error('INVALID_PAYMENT_TYPE');
    const targetId=opId_('ASN'),returnId=returnDate?opId_('ASN'):null,changesSource={Period_End:closedEnd,'Назначен до':closedEnd,'Статус':'Перемещён',Closed_At:now};
    let dailyRateMinor=null,obligation=source.Obligation_ID||null,deferredMinor=null,accrueMinor=0,expenseId=null,accrualId=null;
    if(type==='DAILY'){
      dailyRateMinor=opMinor_(data.dailyRate);
    }else{
      let available=opNumber_(source.UnallocatedFixedMinor);
      if(available==null){
        const agreed=Number(source['Согласованная сумма']);
        const accrued=opRows_('SalaryAccruals').filter(r=>r.Assignment_ID===source.ID||r.Assignment_ID===source.Assignment_ID).reduce((n,r)=>{
          if(Number.isSafeInteger(r.AccruedMinor))return n+r.AccruedMinor;const value=Number(r['Начислено']);return n+(Number.isFinite(value)?Math.round(value*100):0);
        },0);
        if(!Number.isFinite(agreed))throw Error('FIXED_OBLIGATION_UNKNOWN');
        available=Math.max(0,Math.round(agreed*100)-accrued);
      }
      accrueMinor=opMinorZero_(data.accrueNow);deferredMinor=opMinorZero_(data.deferAmount);
      if(accrueMinor>available||deferredMinor!==available-accrueMinor)throw Error('FIXED_ALLOCATION_MISMATCH');
      if(data.closeObligation===true&&deferredMinor!==0)throw Error('FIXED_CLOSE_REQUIRES_FULL_ALLOCATION');
      obligation=obligation||opId_('OBL');changesSource.Obligation_ID=obligation;changesSource.UnallocatedFixedMinor=0;
      if(accrueMinor>0){
        accrualId=opId_('ACCR');expenseId=opId_('EXP');
        changes.push(opChange_('SalaryAccruals',null,{ID:accrualId,Accrual_ID:accrualId,Object_ID:source.Object_ID,Installer_ID:installer.ID,'Монтажник':installer.Name,Assignment_ID:source.ID,Obligation_ID:obligation,Accrual_Event_ID:accrualId,Expense_ID:expenseId,Recognition_Basis:'FIXED_MOVE','Начислено':opRub_(accrueMinor),'Выплачено':0,AccruedMinor:accrueMinor,PaidMinor:0}));
        changes.push(opChange_('Expenses',null,{ID:expenseId,Date:opToday_(),Type:'По объекту',Category:'Заработная плата / монтажники',Description:'Начисление монтажнику при перемещении',AmountMinor:accrueMinor,Quantity:1,Unit:'начисление',PriceMinor:accrueMinor,Method:'Начисление',LegacyStatus:'Начислено',Status:'ACCRUED',Person_ID:null,LegacyPerson:null,Object_ID:source.Object_ID,Installer_ID:installer.ID,Comment:reason,DataStatus:'IMPORTED'}));
      }
    }
    changes.push(opChange_('Assignments',source,changesSource));
    const targetFields={ID:targetId,Assignment_ID:targetId,Parent_Assignment_ID:source.ID,TechTask_ID:(opRows_('TechTasks').find(r=>r.Object_ID===target.ID)||{}).ID||null,Object_ID:target.ID,Installer_ID:installer.ID,'Монтажник':installer.Name,Payment_Type:type,DailyRateMinor:dailyRateMinor,Period_Start:transition,Period_End:targetEnd,Actual_Days:0,AccruedMinor:0,PaidMinor:0,UnallocatedFixedMinor:type==='FIXED'?deferredMinor:null,Obligation_ID:obligation,'Назначен с':transition,'Назначен до':targetEnd,'Статус':'Назначен'};
    changes.push(opChange_('Assignments',null,targetFields));
    if(returnDate){
      const returnFields={ID:returnId,Assignment_ID:returnId,Parent_Assignment_ID:source.ID,TechTask_ID:source.TechTask_ID||null,Object_ID:source.Object_ID,Installer_ID:installer.ID,'Монтажник':installer.Name,Payment_Type:type,DailyRateMinor:dailyRateMinor,Period_Start:returnDate,Period_End:returnEnd,Actual_Days:0,AccruedMinor:0,PaidMinor:0,UnallocatedFixedMinor:null,Obligation_ID:obligation,'Назначен с':returnDate,'Назначен до':returnEnd,'Статус':'Запланирован возврат'};
      changes.push(opChange_('Assignments',null,returnFields));
    }
    opRows_('Calendar').filter(r=>r.Installer_ID===installer.ID&&r.Object_ID===source.Object_ID).forEach(calendar=>{
      const range={start:opDate_(calendar.Period_Start||calendar['План начало']),end:opDate_(calendar.Period_End||calendar['План конец'])};
      if(range.start&&opRangesOverlap_(transition,null,range.start,range.end))changes.push(opChange_('Calendar',calendar,{Assignment_ID:calendar.Assignment_ID||source.ID,Period_End:closedEnd,'План конец':closedEnd,'Статус':'Перемещён'}));
    });
    const targetCal=opId_('CAL');changes.push(opChange_('Calendar',null,{ID:targetCal,Calendar_ID:targetCal,Assignment_ID:targetId,Object_ID:target.ID,TechTask_ID:targetFields.TechTask_ID,Installer_ID:installer.ID,'Монтажник':installer.Name,Period_Start:transition,Period_End:targetEnd,'План начало':transition,'План конец':targetEnd,'Статус':'Подтверждён'}));
    if(returnDate){const returnCal=opId_('CAL');changes.push(opChange_('Calendar',null,{ID:returnCal,Calendar_ID:returnCal,Assignment_ID:returnId,Object_ID:source.Object_ID,TechTask_ID:source.TechTask_ID||null,Installer_ID:installer.ID,'Монтажник':installer.Name,Period_Start:returnDate,Period_End:returnEnd,'План начало':returnDate,'План конец':returnEnd,'Статус':'Запланирован возврат'}));}
    result={sourceAssignmentId:source.ID,newAssignmentId:targetId,returnAssignmentId:returnId,accrualEventId:accrualId,expenseId:expenseId,revision:Number(source.Revision||1)+1};
  }else if(action==='createHiredWorkerDay'||action==='addHiredWorkerDay'){
    if(!opHiredWorkerSchemaReady_())throw Error('WORKFORCE_SCHEMA_NOT_READY');
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_WORKFORCE');
    reason=opRequired_(data.reason,'WORKDAY_REASON_REQUIRED');
    const object=opObjectAccess_(auth,opRequired_(data.Object_ID,'OBJECT_ID_REQUIRED'));
    const start=opDateRequired_(data.workDate),actualDays=Number(data.actualDays);
    if(!Number.isInteger(actualDays)||actualDays<1||actualDays>31)throw Error('INVALID_ACTUAL_DAYS');
    const dailyRateMinor=opMinor_(data.dailyRate),totalMinor=opSafeMultiply_(dailyRateMinor,actualDays),end=opDateAdd_(start,actualDays-1);
    let employee,isNew=false;
    if(action==='createHiredWorkerDay'){
      const employeeId=opId_('INST');
      employee={ID:employeeId,Name:opRequired_(data.name,'WORKER_NAME_REQUIRED'),Phone:data.phone||null,Role:'INSTALLER',Active:true,WorkerKind:'HIRED',Revision:1};isNew=true;
    }else{
      employee=opFind_('Employees',opRequired_(data.Installer_ID,'INSTALLER_ID_REQUIRED'));
      if(!employee||employee.WorkerKind!=='HIRED')throw Error('HIRED_WORKER_NOT_FOUND');
      if(Number(data.expectedRevision)!==Number(employee.Revision||1))throw Error('REVISION_CONFLICT');
    }
    const installerId=employee.ID,existingDays=opRows_('WorkDays');
    const dayDates=[];for(let i=0;i<actualDays;i++)dayDates.push(opDateAdd_(start,i));
    if(dayDates.some(date=>existingDays.some(r=>r.Installer_ID===installerId&&opDate_(r.Work_Date)===date)))throw Error('WORKDAY_DUPLICATE');
    const existingAssignments=opRows_('Assignments').filter(r=>r.Installer_ID===installerId);
    if(existingAssignments.some(r=>{const range=opAssignmentRange_(r);return range.start&&opRangesOverlap_(start,end,range.start,range.end);} ))throw Error('ASSIGNMENT_OVERLAP');
    const assignmentId=opId_('ASN'),accrualId=opId_('ACCR'),expenseId=opId_('EXP'),calendarId=opId_('CAL');
    if(isNew)changes.push(opChange_('Employees',null,employee));else changes.push(opChange_('Employees',employee,{}));
    changes.push(opChange_('Assignments',null,{ID:assignmentId,Assignment_ID:assignmentId,Parent_Assignment_ID:null,TechTask_ID:(opRows_('TechTasks').find(r=>r.Object_ID===object.ID)||{}).ID||null,Object_ID:object.ID,Installer_ID:installerId,'Монтажник':employee.Name,Payment_Type:'DAILY',DailyRateMinor:dailyRateMinor,Period_Start:start,Period_End:end,Actual_Days:actualDays,AccruedMinor:totalMinor,PaidMinor:0,UnallocatedFixedMinor:null,Obligation_ID:null,'Назначен с':start,'Назначен до':end,'Статус':'Выполнено'}));
    dayDates.forEach(date=>{const workDayId=opId_('WD');changes.push(opChange_('WorkDays',null,{ID:workDayId,WorkDay_ID:workDayId,Installer_ID:installerId,Assignment_ID:assignmentId,Object_ID:object.ID,Work_Date:date,Actual_Days:1,DailyRateMinor:dailyRateMinor,Accrual_Event_ID:accrualId,CreatedAt:now,Actor_ID:auth.userId}));});
    changes.push(opChange_('SalaryAccruals',null,{ID:accrualId,Accrual_ID:accrualId,Object_ID:object.ID,Installer_ID:installerId,'Монтажник':employee.Name,Assignment_ID:assignmentId,Obligation_ID:null,Accrual_Event_ID:accrualId,Expense_ID:expenseId,Recognition_Basis:'HIRED_WORKDAY','Начислено':opRub_(totalMinor),'Выплачено':0,AccruedMinor:totalMinor,PaidMinor:0}));
    changes.push(opChange_('Expenses',null,{ID:expenseId,Date:start,Type:'По объекту',Category:'Заработная плата / наёмные работники',Description:'Начисление наёмному работнику: '+opRequired_(data.workType,'WORK_TYPE_REQUIRED'),AmountMinor:totalMinor,Quantity:actualDays,Unit:'день',PriceMinor:dailyRateMinor,Method:'Начисление',LegacyStatus:'Начислено',Status:'ACCRUED',Person_ID:null,LegacyPerson:null,Object_ID:object.ID,Installer_ID:installerId,Comment:reason,DataStatus:'IMPORTED'}));
    changes.push(opChange_('Calendar',null,{ID:calendarId,Calendar_ID:calendarId,Assignment_ID:assignmentId,Object_ID:object.ID,TechTask_ID:(opRows_('TechTasks').find(r=>r.Object_ID===object.ID)||{}).ID||null,Installer_ID:installerId,'Монтажник':employee.Name,Period_Start:start,Period_End:end,'План начало':start,'План конец':end,'Статус':'Факт'}));
    result={Installer_ID:installerId,Assignment_ID:assignmentId,Accrual_Event_ID:accrualId,Expense_ID:expenseId,amount:opRub_(totalMinor),paid:0,revision:isNew?1:Number(employee.Revision||1)+1};
  }else if(action==='promoteHiredWorker'){
    if(!opHiredWorkerSchemaReady_())throw Error('WORKFORCE_SCHEMA_NOT_READY');
    if(!opAdmin_(auth))throw Error('FORBIDDEN_ADMIN');
    reason=opRequired_(data.reason,'PROMOTION_REASON_REQUIRED');
    const employee=opFind_('Employees',opRequired_(data.Installer_ID,'INSTALLER_ID_REQUIRED'));
    if(!employee||employee.WorkerKind!=='HIRED')throw Error('HIRED_WORKER_NOT_FOUND');
    if(Number(data.expectedRevision)!==Number(employee.Revision||1))throw Error('REVISION_CONFLICT');
    changes.push(opChange_('Employees',employee,{WorkerKind:'STAFF',Role:'INSTALLER'}));
    result={Installer_ID:employee.ID,workerKind:'STAFF',revision:Number(employee.Revision||1)+1};
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
  }else if(action==='addLead'||action==='addSurvey'){
    if(!opAdmin_(auth)&&['PARTNER','ENGINEER','MANAGER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_CRM');const table=action==='addLead'?'Leads':'Measurements',id=opId_(action==='addLead'?'LEAD':'SURVEY'),date=opDateRequired_(data.date||opToday_());
    const record={ID:id,Date:date,Name:opRequired_(data.client),Phone:data.phone||null,Manager_ID:opRole_(auth)==='MANAGER'?auth.userId:null,Engineer_ID:opRole_(auth)==='ENGINEER'?auth.userId:null,'Дата':date,'Клиент':data.client,'Телефон':data.phone||null,'Вид работ':data.workType||null,'Комментарий':data.comment||null};
    if(table==='Leads'){record.Lead_ID=id;record['Статус']='Новый';record['Источник']=data.source||null;}else{record.Survey_ID=id;record['Адрес']=opRequired_(data.address);record.ConvertedToObject=false;}changes.push(opChange_(table,null,record));result={id:id};
  }else if(action==='uploadMedia'){
    const object=opObjectAccess_(auth,data.objectId);if(['OWNER','ADMIN','PARTNER','ENGINEER','INSTALLER'].indexOf(opRole_(auth))<0)throw Error('FORBIDDEN_MEDIA');
    if(!/^image\/(jpeg|png|webp)$/.test(opStr_(data.mimeType)))throw Error('INVALID_IMAGE_TYPE');
    const bytes=Utilities.base64Decode(opRequired_(body.base64));if(bytes.length>6*1024*1024)throw Error('IMAGE_TOO_LARGE');
    const folder=DriveApp.getFolderById(OP.mediaFolder),name='TK4-'+opHash_(key),files=folder.getFilesByName(name);let file;
    if(files.hasNext()){file=files.next();if(opHash_(Utilities.base64Encode(file.getBlob().getBytes()))!==data.contentHash)throw Error('IDEMPOTENCY_CONFLICT');}
    else file=folder.createFile(Utilities.newBlob(bytes,data.mimeType,name));
    const id=opId_('MEDIA');changes.push(opChange_('Media',null,{ID:id,Media_ID:id,Object_ID:object.ID,Installer_ID:opRole_(auth)==='INSTALLER'?auth.employeeId:null,'Дата':opToday_(),'Тип':data.mediaType||'Фото','Этап':data.stage||null,Drive_File_ID:file.getId(),URL:file.getUrl(),'Комментарий':data.comment||null,UploadedBy:auth.userId,CreatedAt:now}));result={id:id,url:file.getUrl()};
  }else if(action==='saveTechTaskV2'){
    const task=opPatchTask_(auth,data);changes=task.changes;result=task.result;
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
  days.forEach((d,i)=>{const date=opDateRequired_(d.date);if(date<start||date>end)throw Error('STAGE_OUTSIDE_OBJECT_DATES');const did=opId_('DAY');changes.push(opChange_('DailyPlans',null,{ID:did,DayPlan_ID:did,TechTask_ID:id,Object_ID:object.ID,'День №':Number(d.dayNo)||i+1,'Дата':date,'Задача':opRequired_(d.task),'Ед. изм.':opRequired_(d.unit),'План объём':opRub_(opMinor_(d.plannedQty)),'Статус':'План'}));});
  payments.forEach((p,i)=>{const pid=opId_('PAY'),date=opDateRequired_(p.plannedDate),amount=opMinor_(p.plannedAmount);changes.push(opChange_('PaymentSchedule',null,{ID:pid,PaymentPlan_ID:pid,TechTask_ID:id,Object_ID:object.ID,Date:date,AmountMinor:amount,'Этап №':i+1,'Наименование этапа':opRequired_(p.stageName),'Плановая дата':date,'Плановая сумма':opRub_(amount)}));});
  return {changes:changes,result:{id:id,revision:1}};
}

function opChildId_(row,keys){for(const key of keys){const value=opStr_(row&&row[key]).trim();if(value)return value;}return '';}
function opAssertChildRevision_(row,before){if(Number(row.expectedRevision)!==Number(before.Revision||1))throw Error('REVISION_CONFLICT');}
function opPatchTask_(auth,data){
  const task=opFind_('TechTasks',opRequired_(data.techTaskId,'TECH_TASK_ID_REQUIRED'));if(!task)throw Error('TECH_TASK_NOT_FOUND');
  const object=opObjectAccess_(auth,task.Object_ID);if(Number(data.expectedRevision)!==Number(task.Revision||1))throw Error('REVISION_CONFLICT');
  opRequired_(data.reason,'TECH_TASK_EDIT_REASON_REQUIRED');
  const start=opDateRequired_(data.planStart||task['План начала']),end=opDateRequired_(data.planEnd||task['План окончания']);if(end<start)throw Error('INVALID_DATE_RANGE');
  const workers=data.assignments,days=data.days,payments=data.payments;if(!Array.isArray(workers)||!workers.length||!Array.isArray(days)||!days.length||!Array.isArray(payments))throw Error('TASK_DETAILS_REQUIRED');
  const changes=[opChange_('TechTasks',task,{'Статус ТЗ':data.materialsReady?'Готово':'Черновик','План начала':start,'План окончания':end,'Материалы готовы':!!data.materialsReady,'Комментарий':data.comment||null,'Технология':data.technology||task['Технология']||null})];

  const editableAssignments=opRows_('Assignments').filter(r=>r.TechTask_ID===task.ID&&!r.Closed_At&&r['Статус']!=='Перемещён');
  const suppliedAssignmentIds=new Set(workers.map(w=>opChildId_(w,['assignmentId','Assignment_ID','id'])).filter(Boolean));
  if(editableAssignments.some(r=>!suppliedAssignmentIds.has(r.ID)))throw Error('CHILD_REMOVAL_REQUIRES_EXPLICIT_DELETE');
  const workerIds=new Set();
  workers.forEach(w=>{
    const employee=opFind_('Employees',opRequired_(w.installerId,'INSTALLER_ID_REQUIRED'));if(!employee||workerIds.has(employee.ID))throw Error('INVALID_INSTALLER');workerIds.add(employee.ID);
    const childId=opChildId_(w,['assignmentId','Assignment_ID','id']),amount=opRub_(opMinor_(w.agreedAmount));
    if(childId){
      const before=editableAssignments.find(r=>r.ID===childId);if(!before||before.Installer_ID!==employee.ID)throw Error('ASSIGNMENT_NOT_FOUND');opAssertChildRevision_(w,before);
      changes.push(opChange_('Assignments',before,{'Согласованная сумма':amount,'Назначен с':start,'Назначен до':end,Period_Start:start,Period_End:end}));
      const calendar=opRows_('Calendar').find(r=>r.Assignment_ID===before.ID)||(opRows_('Calendar').find(r=>r.TechTask_ID===task.ID&&r.Installer_ID===employee.ID&&r['Статус']!=='Перемещён'));
      if(calendar)changes.push(opChange_('Calendar',calendar,{Assignment_ID:before.ID,'План начало':start,'План конец':end,Period_Start:start,Period_End:end}));
    }else{
      const aid=opId_('ASN');changes.push(opChange_('Assignments',null,{ID:aid,Assignment_ID:aid,TechTask_ID:task.ID,Object_ID:object.ID,Installer_ID:employee.ID,'Монтажник':employee.Name||employee['ФИО'],'Согласованная сумма':amount,'Назначен с':start,'Назначен до':end,Period_Start:start,Period_End:end,'Статус':'Назначен'}));
      const cid=opId_('CAL');changes.push(opChange_('Calendar',null,{ID:cid,Calendar_ID:cid,Assignment_ID:aid,Object_ID:object.ID,TechTask_ID:task.ID,Installer_ID:employee.ID,'Монтажник':employee.Name||employee['ФИО'],'План начало':start,'План конец':end,Period_Start:start,Period_End:end,'Статус':'Подтверждён'}));
    }
  });

  const existingDays=opRows_('DailyPlans').filter(r=>r.TechTask_ID===task.ID),suppliedDayIds=new Set(days.map(d=>opChildId_(d,['dayPlanId','DayPlan_ID','id'])).filter(Boolean));
  if(existingDays.some(r=>!suppliedDayIds.has(r.ID)))throw Error('CHILD_REMOVAL_REQUIRES_EXPLICIT_DELETE');
  days.forEach((d,i)=>{
    const date=opDateRequired_(d.date);if(date<start||date>end)throw Error('STAGE_OUTSIDE_OBJECT_DATES');
    const fields={'День №':Number(d.dayNo)||i+1,'Дата':date,'Задача':opRequired_(d.task),'Ед. изм.':opRequired_(d.unit),'План объём':opRub_(opMinor_(d.plannedQty)),'Статус':d.status||'План'};
    const childId=opChildId_(d,['dayPlanId','DayPlan_ID','id']);
    if(childId){const before=existingDays.find(r=>r.ID===childId);if(!before)throw Error('DAY_PLAN_NOT_FOUND');opAssertChildRevision_(d,before);changes.push(opChange_('DailyPlans',before,fields));}
    else{const did=opId_('DAY');changes.push(opChange_('DailyPlans',null,Object.assign({ID:did,DayPlan_ID:did,TechTask_ID:task.ID,Object_ID:object.ID},fields)));}
  });

  const existingPayments=opRows_('PaymentSchedule').filter(r=>r.TechTask_ID===task.ID),suppliedPaymentIds=new Set(payments.map(p=>opChildId_(p,['paymentPlanId','PaymentPlan_ID','id'])).filter(Boolean));
  if(existingPayments.some(r=>!suppliedPaymentIds.has(r.ID)))throw Error('CHILD_REMOVAL_REQUIRES_EXPLICIT_DELETE');
  const income=opRows_('Income').filter(r=>r.Object_ID===object.ID),existingPaymentIds=new Set(existingPayments.map(r=>r.ID));
  const totalReceived=income.reduce((n,r)=>n+opIncomeSigned_(r),0),linkedReceived=income.filter(r=>existingPaymentIds.has(r.PaymentPlan_ID)).reduce((n,r)=>n+opIncomeSigned_(r),0);
  let plannedTotal=0;
  payments.forEach((p,i)=>{
    const date=opDateRequired_(p.plannedDate),amount=opMinor_(p.plannedAmount);plannedTotal+=amount;
    const childId=opChildId_(p,['paymentPlanId','PaymentPlan_ID','id']),fields={Date:date,AmountMinor:amount,'Этап №':Number(p.stageNo)||i+1,'Наименование этапа':opRequired_(p.stageName),'Плановая дата':date,'Плановая сумма':opRub_(amount)};
    if(childId){
      const before=existingPayments.find(r=>r.ID===childId);if(!before)throw Error('PAYMENT_PLAN_NOT_FOUND');opAssertChildRevision_(p,before);
      const paid=income.filter(r=>r.PaymentPlan_ID===before.ID).reduce((n,r)=>n+opIncomeSigned_(r),0);if(amount<paid)throw Error('PAYMENT_PLAN_BELOW_RECEIVED');
      changes.push(opChange_('PaymentSchedule',before,fields));
    }else{const pid=opId_('PAY');changes.push(opChange_('PaymentSchedule',null,Object.assign({ID:pid,PaymentPlan_ID:pid,TechTask_ID:task.ID,Object_ID:object.ID},fields)));}
  });
  const requiredPlan=Number.isSafeInteger(object.ContractMinor)?Math.max(0,object.ContractMinor-(totalReceived-linkedReceived)):null;if(requiredPlan==null||plannedTotal!==requiredPlan)throw Error('PAYMENT_PLAN_TOTAL_MISMATCH');
  return {changes:changes,result:{id:task.ID,revision:Number(task.Revision||1)+1,preservedChildIds:true}};
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

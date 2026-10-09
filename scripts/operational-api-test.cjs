const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const schema=JSON.parse(fs.readFileSync('backend/google_apps_script/operational-schema.json','utf8'));
const source=fs.readFileSync('backend/google_apps_script/OperationalApi.gs','utf8');
const tables={};let failTable=null;
function sheet(name,rows=[]){const headers=[...new Set([...(schema[name]||['ID']),...rows.flatMap(Object.keys)])];const data=[headers,...rows.map(r=>headers.map(h=>r[h]??''))];
 return {data,getDataRange(){return{getValues:()=>data.map(r=>r.slice())};},getLastRow:()=>data.length,getMaxRows:()=>1000,
 getRange(row,col,height,width){return {setValues(values){if(failTable===name){failTable=null;throw Error('SIMULATED_INTERRUPTION');}for(let r=0;r<height;r++){data[row-1+r]??=Array(headers.length).fill('');for(let c=0;c<width;c++){let v=values[r][c];if(typeof v==='string'&&/^'[=+@]/.test(v))v=v.slice(1);data[row-1+r][col-1+c]=v;}}}};}};
}
const context=vm.createContext({console,Date,Set,Map,JSON,Number,Object,Array,String,Math,Error,
 SpreadsheetApp:{openById(id){assert.notEqual(id,'1vTdo0kJmHQP-N4JOnqpzlSQ75uznf_Czo_w4s442WWU');return{getSheetByName:n=>tables[n]};},flush(){}},
 Utilities:{getUuid:()=>crypto.randomUUID(),DigestAlgorithm:{SHA_256:'sha256'},Charset:{UTF_8:'utf8'},computeDigest:(a,s)=>[...crypto.createHash('sha256').update(s).digest()],formatDate:d=>d.toISOString().slice(0,10)},
 PropertiesService:{getScriptProperties:()=>({getProperty:()=>null,setProperty(){}})},
 CacheService:{getScriptCache:()=>({get:()=>null,put(){}})}});
vm.runInContext(source,context);
const owner={userId:'OWNER',name:'Owner',role:'OWNER',finance:true,employeeId:'OWNER'};
function reset(){for(const n of Object.keys(schema))tables[n]=sheet(n);tables.Settings=sheet('Settings',[{ID:'state',Key:'MIGRATION_STATUS',Value:'READY'},{ID:'from',Key:'COVERAGE_FROM',Value:'2026-09-01'},{ID:'to',Key:'COVERAGE_TO',Value:'2026-09-30'}]);
 tables.Objects=sheet('Objects',[{ID:'O1',Client_ID:'C1',Address:'Object 1',Status:'В работе',ContractMinor:100000,Manager_ID:'M1',Engineer_ID:'E1',Revision:1},{ID:'O2',Client_ID:'C2',Address:'Other object',Status:'Запланирован',ContractMinor:999999,Revision:1}]);
 tables.Clients=sheet('Clients',[{ID:'C1',Name:'Client 1'},{ID:'C2',Name:'Private other client'}]);tables.AccountablePersons=sheet('AccountablePersons',[{ID:'P1',Name:'Person 1',OpeningMinor:-10000},{ID:'P2',Name:'Person 2',OpeningMinor:0}]);vm.runInContext('opTables_={}',context);}
let passed=0;function test(name,fn){reset();fn();passed++;console.log('PASS '+name);}
function command(action,fields={},auth=owner){return context.opCommand_(auth,{action,requestId:'REQUEST-'+passed,...fields});}
test('decimal storage uses kopecks and rejects missing/negative',()=>{assert.equal(context.opMinor_('12.34'),1234);assert.throws(()=>context.opMinor_(null));assert.throws(()=>context.opMinor_(-2));});
test('calendar quarter boundary is inclusive and timezone-stable',()=>{const p=context.opPeriod_('Квартал','2026-09-23');assert.equal(p.from,'2026-07-01');assert.equal(p.to,'2026-09-30');assert.equal(context.opInPeriod_('2026-10-01',p),false);});
test('same key returns same ID and does not duplicate income',()=>{const b={objectId:'O1',recipient:'P1',amount:123.45,paymentKind:'Аванс',method:'Наличные'};const a=command('addIncome',b),again=command('addIncome',b);assert.equal(a.id,again.id);assert.equal(context.opRows_('Income').length,1);assert.equal(context.opRows_('Income')[0].AmountMinor,12345);});
test('same key with different payload is rejected',()=>{const b={objectId:'O1',recipient:'P1',amount:100,paymentKind:'Аванс',method:'Наличные'};command('addIncome',b);assert.throws(()=>command('addIncome',{...b,amount:101}),/IDEMPOTENCY_CONFLICT/);});
test('transfer affects two balances once and never company expenses',()=>{command('transfer',{from:'P1',to:'P2',amount:25});const b=context.opBalances_(context.opRows_('AccountablePersons'),[],[],context.opRows_('CashTransfers'));assert.equal(b['Person 1'].balance,-125);assert.equal(b['Person 2'].balance,25);assert.equal(context.opRows_('Expenses').length,0);});
test('financial edit requires reason and revision',()=>{tables.Expenses=sheet('Expenses',[{ID:'X1',AmountMinor:100,Status:'CONFIRMED',Revision:2}]);assert.throws(()=>command('editFinance',{entity:'Expenses',entityId:'X1',amount:2,expectedRevision:2}),/EDIT_REASON_REQUIRED/);assert.throws(()=>command('editFinance',{entity:'Expenses',entityId:'X1',amount:2,expectedRevision:1,reason:'Correction'}),/REVISION_CONFLICT/);command('editFinance',{entity:'Expenses',entityId:'X1',amount:2,expectedRevision:2,reason:'Correction'});assert.equal(context.opRows_('Expenses')[0].AmountMinor,200);assert.equal(context.opRows_('AuditLog')[0].CommitState,'COMMITTED');});
test('finance delete creates tombstone and deleted income never returns to active bootstrap',()=>{
 tables.Income=sheet('Income',[{ID:'INC1',Date:'2026-09-10',Operation:'Приход',PaymentKind:'Аванс',AmountMinor:12000000,Method:'Наличные',Recipient_ID:'P1',Object_ID:'O1',Status:'CONFIRMED',Revision:3}]);
 vm.runInContext('opTables_={}',context);context.opToday_=()=> '2026-09-23';
 let before=context.opBootstrap_(owner,'Месяц');assert.equal(before.income.length,1);assert.equal(before.kpis.turnover,120000);
 const result=command('deleteFinance',{entity:'Income',entityId:'INC1',expectedRevision:3,reason:'Ошибочный приход'});
 assert.equal(result.deleted,true);
 vm.runInContext('opTables_={}',context);
 const raw=context.opTable_('Income').rows.find(r=>r.ID==='INC1');assert(raw.DeletedAt);assert.equal(raw.DeleteReason,'Ошибочный приход');
 const after=context.opBootstrap_(owner,'Месяц');assert.equal(after.income.length,0);assert.equal(after.kpis.turnover,0);
 assert.equal(context.opRows_('Income').length,0);
});
test('deleted transfer no longer affects accountable balances',()=>{
 tables.CashTransfers=sheet('CashTransfers',[{ID:'T1',Date:'2026-09-10',Type:'Перевод подотчёта',AmountMinor:5000,Person_ID:'P1',ToPerson_ID:'P2',Status:'CONFIRMED',Revision:1}]);
 vm.runInContext('opTables_={}',context);
 let b=context.opBalances_(context.opRows_('AccountablePersons'),[],[],context.opRows_('CashTransfers'));assert.equal(b['Person 1'].balance,-150);assert.equal(b['Person 2'].balance,50);
 command('deleteFinance',{entity:'CashTransfers',entityId:'T1',expectedRevision:1,reason:'Ошибочный перевод'});
 vm.runInContext('opTables_={}',context);
 b=context.opBalances_(context.opRows_('AccountablePersons'),[],[],context.opRows_('CashTransfers'));assert.equal(b['Person 1'].balance,-100);assert.equal(b['Person 2'].balance,0);
});
test('journal resumes interrupted write without double applying',()=>{failTable='Expenses';const b={amount:15,article:'Материалы',description:'Paint',accountable:'P1',paymentStatus:'Оплачено',method:'Наличные'};assert.throws(()=>command('addExpense',b),/SIMULATED_INTERRUPTION/);assert.equal(context.opRows_('AuditLog')[0].CommitState,'PREPARED');context.opRecover_();command('addExpense',b);assert.equal(context.opRows_('Expenses').length,1);assert.equal(context.opRows_('AuditLog')[0].CommitState,'COMMITTED');});
test('untrusted auth fields never enter audit payload',()=>{command('transfer',{from:'P1',to:'P2',amount:1,token:'SECRET_TOKEN',pairingCode:'SECRET_CODE',role:'OWNER'});assert(!JSON.stringify(tables.AuditLog.data).includes('SECRET'));});
test('manager cannot write company finances',()=>{assert.throws(()=>command('transfer',{from:'P1',to:'P2',amount:1},{userId:'M1',role:'MANAGER',finance:true}),/FORBIDDEN_FINANCE/);});
test('pairing resolves the real user by selected name and one-time code instead of a hardcoded ID',()=>{
 tables.Users=sheet('Users',[
  {ID:'REAL-OWNER-17',Name:'Игорь Филинов',Role:'OWNER',Active:true,PairingCode:'8642',Finance:true},
  {ID:'REAL-PARTNER-29',Name:'Константин Шумкин',Role:'PARTNER',Active:true,PairingCode:'1357',Finance:true}
 ]);vm.runInContext('opTables_={}',context);
 const result=context.opPair_({userName:'Игорь Филинов',pairingCode:'8642',deviceId:'DEVICE-NEW'});
 assert.equal(result.userId,'REAL-OWNER-17');assert.equal(result.name,'Игорь Филинов');assert(result.token);
 vm.runInContext('opTables_={}',context);
 const stored=context.opFind_('Users','REAL-OWNER-17');assert.equal(stored.PairingCode,null);assert.equal(stored.Device_ID,'DEVICE-NEW');
});
test('pairing rejects a code that belongs to another selected user',()=>{
 tables.Users=sheet('Users',[
  {ID:'REAL-OWNER-17',Name:'Игорь Филинов',Role:'OWNER',Active:true,PairingCode:'8642'},
  {ID:'REAL-PARTNER-29',Name:'Константин Шумкин',Role:'PARTNER',Active:true,PairingCode:'1357'}
 ]);vm.runInContext('opTables_={}',context);
 assert.throws(()=>context.opPair_({userName:'Игорь Филинов',pairingCode:'1357',deviceId:'DEVICE-X'}),/PAIRING_DENIED/);
});
test('owner can create and update a complete installer profile',()=>{
 const result=command('createInstaller',{name:'Installer New',phone:'+79990000000',address:'Москва, ул. Тестовая, 1',relativeName:'Иван Иванов',relativePhone:'+79991112233',serviceContractSigned:true});assert(result.id);
 let employee=context.opRows_('Employees').find(r=>r.ID===result.id);
 assert.equal(employee.Name,'Installer New');assert.equal(employee.Role,'INSTALLER');assert.equal(employee.WorkerKind,'STAFF');
 assert.equal(employee.Address,'Москва, ул. Тестовая, 1');assert.equal(employee.RelativeName,'Иван Иванов');assert.equal(employee.RelativePhone,'+79991112233');assert.equal(employee.ServiceContractSigned,true);
 const updated=context.opCommand_(owner,{action:'updateInstallerProfile',requestId:'INSTALLER-PROFILE-UPDATE',installerId:result.id,expectedRevision:1,name:'Installer New',phone:'+79990000001',address:'Москва, ул. Новая, 2',relativeName:'Пётр Иванов',relativePhone:'+79994445566',serviceContractSigned:false});
 assert.equal(updated.revision,2);vm.runInContext('opTables_={}',context);employee=context.opFind_('Employees',result.id);
 assert.equal(employee.Phone,'+79990000001');assert.equal(employee.Address,'Москва, ул. Новая, 2');assert.equal(employee.RelativePhone,'+79994445566');assert.equal(employee.ServiceContractSigned,false);
});
test('leader bootstrap fills dashboard KPI statistics from operational rows',()=>{
 context.opToday_=()=> '2026-09-23';
 tables.Objects=sheet('Objects',[
  {ID:'O1',Client_ID:'C1',Address:'Object 1',Status:'В работе',ContractMinor:10000000,PlanStart:'2026-09-05',Revision:1},
  {ID:'O2',Client_ID:'C2',Address:'Object 2',Status:'Запланирован',ContractMinor:20000000,PlanStart:'2026-09-10',Revision:1}
 ]);
 tables.Clients=sheet('Clients',[{ID:'C1',Name:'Client 1'},{ID:'C2',Name:'Client 2'}]);
 tables.Leads=sheet('Leads',[
  {ID:'L1',Date:'2026-09-03',Manager_ID:'OWNER'},
  {ID:'L2',Date:'2026-09-04',Manager_ID:'OWNER'}
 ]);
 tables.Measurements=sheet('Measurements',[
  {ID:'M1',Date:'2026-09-08',ConvertedToObject:true},
  {ID:'M2',Date:'2026-09-09',ConvertedToObject:false}
 ]);
 tables.Income=sheet('Income',[
  {ID:'I1',Date:'2026-09-12',Operation:'Приход',PaymentKind:'Аванс',AmountMinor:3000000,Status:'CONFIRMED',Object_ID:'O1'},
  {ID:'I2',Date:'2026-09-15',Operation:'Приход',PaymentKind:'Окончательный расчёт',AmountMinor:7000000,Status:'CONFIRMED',Object_ID:'O1'}
 ]);
 tables.Expenses=sheet('Expenses',[{ID:'E1',Date:'2026-09-16',Category:'Налоги',AmountMinor:1000000,Status:'CONFIRMED'}]);
 vm.runInContext('opTables_={}',context);
 const r=context.opBootstrap_(owner,'Месяц');
 assert.equal(r.kpis.objectsInWork,1);assert.equal(r.kpis.plannedObjects,1);
 assert.equal(r.kpis.leads,2);assert.equal(r.kpis.surveys,2);assert.equal(r.kpis.surveysCompleted,1);assert.equal(r.kpis.surveysScheduled,1);
 assert.equal(r.kpis.contracts,2);assert.equal(r.kpis.averageCheck,150000);
 assert.equal(r.kpis.turnover,100000);assert.equal(r.kpis.turnoverIntermediate,30000);assert.equal(r.kpis.turnoverFinal,70000);
 assert.equal(r.kpis.expenses,10000);assert.equal(r.kpis.netProfit,90000);
});

test('partial installer payment is allowed before object close and cannot exceed object remainder',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Installer 1',Role:'INSTALLER',Active:true,WorkerKind:'STAFF'}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1','Монтажник':'Installer 1','Согласованная сумма':60000}]);
 tables.SalaryAccruals=sheet('SalaryAccruals',[]);vm.runInContext('opTables_={}',context);
 const paid=context.opCommand_(owner,{action:'payInstaller',requestId:'PAY-INSTALLER-1',installerId:'I1',objectId:'O1',amount:20000,paymentType:'PARTIAL'});
 assert.equal(paid.remaining,40000);const row=context.opRows_('SalaryAccruals')[0];assert.equal(row['Выплачено'],20000);assert.equal(row['Начислено'],0);
 assert.throws(()=>context.opCommand_(owner,{action:'payInstaller',requestId:'PAY-INSTALLER-2',installerId:'I1',objectId:'O1',amount:50000,paymentType:'PARTIAL'}),/PAYMENT_EXCEEDS_REMAINING/);
 assert.throws(()=>context.opCommand_(owner,{action:'payInstaller',requestId:'PAY-INSTALLER-3',installerId:'I1',objectId:'O1',amount:40000,paymentType:'FINAL'}),/FINAL_PAYMENT_REQUIRES_CLOSED_OBJECT/);
});
test('installer stats count distinct worked days and closed-object earnings in selected period',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Installer 1',Role:'INSTALLER',Active:true,WorkerKind:'STAFF'}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1','Монтажник':'Installer 1','Согласованная сумма':60000,'Назначен с':'2026-09-01','Назначен до':'2026-09-30'}]);
 tables.Objects=sheet('Objects',[{ID:'O1',Client_ID:'C1',Address:'Object 1',Status:'Закрыт 100%',ActualEnd:'2026-09-20',ContractMinor:100000,Revision:1}]);
 tables.DailyPlans=sheet('DailyPlans',[
  {ID:'D1',DayPlan_ID:'D1',TechTask_ID:'TZ1',Object_ID:'O1','Дата':'2026-09-10','Факт объём':20},
  {ID:'D2',DayPlan_ID:'D2',TechTask_ID:'TZ1',Object_ID:'O1','Дата':'2026-09-10','Факт объём':15},
  {ID:'D3',DayPlan_ID:'D3',TechTask_ID:'TZ1',Object_ID:'O1','Дата':'2026-09-11','Факт объём':10}
 ]);vm.runInContext('opTables_={}',context);context.opToday_=()=> '2026-09-23';
 const result=context.opBootstrap_(owner,'Месяц'),stat=result.installerStats.find(x=>x.installerId==='I1');
 assert.equal(stat.workDays,2);assert.equal(stat.closedObjects,1);assert.equal(stat.earned,60000);
 assert.equal(result.capabilities.bitrixMeasurementsV1,false);
});
test('manager bootstrap is scoped and excludes company finance',()=>{const r=context.opBootstrap_({userId:'M1',role:'MANAGER',employeeId:'M1'},'Месяц');assert.equal(r.objects.length,1);assert.equal(r.objects[0].id,'O1');assert.equal(r.accountable,undefined);assert.equal(r.income,undefined);assert.equal(r.kpis.expenses,undefined);assert(!JSON.stringify(r).includes('Private other client'));});
test('installer bootstrap excludes contract amounts and other wages',()=>{tables.Assignments=sheet('Assignments',[{ID:'A1',Object_ID:'O1',Installer_ID:'I1'},{ID:'A2',Object_ID:'O1',Installer_ID:'I2'}]);tables.SalaryAccruals=sheet('SalaryAccruals',[{ID:'S1',Installer_ID:'I1',Начислено:100},{ID:'S2',Installer_ID:'I2',Начислено:999999}]);const r=context.opBootstrap_({userId:'U1',role:'INSTALLER',employeeId:'I1'},'Месяц');assert.equal(r.objects.length,1);assert.equal(r.objects[0].contract,undefined);assert.equal(r.assignments.length,1);assert.equal(r.payroll.length,1);assert(!JSON.stringify(r).includes('999999'));});
test('source workday does not masquerade as a technical task',()=>{tables.DailyPlans=sheet('DailyPlans',[{ID:'D1',Object_ID:'O1',Date:'2026-09-01'}]);const r=context.opBootstrap_(owner,'Месяц');assert.equal(r.dayPlans.length,0);});
test('daily technical task fact is revision-safe and stored separately from plan',()=>{
 tables.DailyPlans=sheet('DailyPlans',[{ID:'D1',DayPlan_ID:'D1',TechTask_ID:'TZ1',Object_ID:'O1','День №':1,'Дата':'2026-09-10','Задача':'Утепление пола','Ед. изм.':'м²','План объём':40,'Описание работ':'Задувка пола','На что обратить внимание':'Примыкания','Факт объём':0,'Статус':'План',Revision:1}]);
 vm.runInContext('opTables_={}',context);
 const result=command('updateDailyProgress',{dayPlanId:'D1',objectId:'O1',actualQty:37.5,comment:'Остались примыкания',status:'В работе',expectedRevision:1});
 assert.equal(result.revision,2);
 vm.runInContext('opTables_={}',context);
 const row=context.opRows_('DailyPlans').find(r=>r.ID==='D1');
 assert.equal(row['План объём'],40);assert.equal(row['Факт объём'],37.5);assert.equal(row['Отчёт дня'],'Остались примыкания');assert.equal(row['Статус'],'В работе');
 assert.throws(()=>context.opCommand_(owner,{action:'updateDailyProgress',requestId:'DAILY-STALE-REVISION',dayPlanId:'D1',objectId:'O1',actualQty:40,status:'Выполнено',expectedRevision:1}),/REVISION_CONFLICT/);
});
test('installer may update daily fact only on an assigned object',()=>{
 tables.DailyPlans=sheet('DailyPlans',[{ID:'D1',DayPlan_ID:'D1',TechTask_ID:'TZ1',Object_ID:'O1','Дата':'2026-09-10','Задача':'Пол','Ед. изм.':'м²','План объём':40,Revision:1}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Object_ID:'O1',Installer_ID:'I1'}]);vm.runInContext('opTables_={}',context);
 const installer={userId:'U1',name:'Installer 1',role:'INSTALLER',employeeId:'I1'};
 command('updateDailyProgress',{dayPlanId:'D1',objectId:'O1',actualQty:20,status:'В работе',expectedRevision:1},installer);
 assert.equal(context.opRows_('DailyPlans').find(r=>r.ID==='D1')['Факт объём'],20);
});
test('net profit is all confirmed income minus all confirmed expenses including taxes',()=>{
 tables.Income=sheet('Income',[
  {ID:'NP-I1',Date:'2026-09-10',Operation:'Приход',AmountMinor:10000000,Status:'CONFIRMED'},
  {ID:'NP-I2',Date:'2026-09-11',Operation:'Приход',AmountMinor:5000000,Status:'CONFIRMED'}
 ]);
 tables.Expenses=sheet('Expenses',[
  {ID:'NP-E1',Date:'2026-09-12',Category:'Материалы',AmountMinor:3000000,Status:'CONFIRMED'},
  {ID:'NP-E2',Date:'2026-09-13',Category:'Налоги',AmountMinor:2000000,Status:'CONFIRMED'},
  {ID:'NP-E3',Date:'2026-09-14',Category:'Реклама',AmountMinor:900000,Status:'PLANNED'}
 ]);
 tables.CashTransfers=sheet('CashTransfers',[{ID:'NP-T1',Date:'2026-09-15',AmountMinor:7000000,Status:'CONFIRMED',Person_ID:'P1',ToPerson_ID:'P2'}]);
 const r=context.opBootstrap_(owner,'Месяц');
 assert.equal(r.kpis.turnover,150000);
 assert.equal(r.kpis.expenses,50000);
 assert.equal(r.kpis.netProfit,100000);
 assert.equal(r.kpis.profit,100000);
 assert.equal(r.kpis.closedProfit,null);
});
test('planned expense and transfer cannot inflate actual expense KPI',()=>{assert.equal(context.opExpenseSigned_({Status:'PLANNED',AmountMinor:900}),0);assert.equal(context.opExpenseSigned_({Status:'REFUND',AmountMinor:900}),-900);});
test('formula-like customer input is kept as plain text',()=>{command('createObject',{client:'=IMPORTXML("private")',address:'Test'});assert.equal(context.opRows_('Clients').find(r=>r.Name.startsWith('=')).Name,'=IMPORTXML("private")');});
if(fs.existsSync('migration-local/operational-plan.json'))test('private approved migration reconciles cash and preserved IDs',()=>{
 const plan=JSON.parse(fs.readFileSync('migration-local/operational-plan.json','utf8'));
 for(const [name,rows] of Object.entries(plan.entities))tables[name]=sheet(name,rows);
 tables.Settings=sheet('Settings',[{ID:'state',Key:'MIGRATION_STATUS',Value:'READY'},{ID:'from',Key:'COVERAGE_FROM',Value:'2026-09-01'},{ID:'to',Key:'COVERAGE_TO',Value:'2026-09-30'}]);
 vm.runInContext('opTables_={}',context);context.opToday_=()=> '2026-09-23';
 const result=context.opBootstrap_(owner,'Месяц');
 assert.equal(result.objects.length,14);assert.equal(result.income.length,9);
 assert.equal(result.kpis.turnover,plan.reconciliation.incomeMinor/100);assert.equal(result.kpis.expenses,plan.reconciliation.expensesMinor/100);
 for(const person of plan.entities.AccountablePersons)assert.equal(result.accountable[person.Name].balance,person.CalculatedBalanceMinor/100);
 assert.equal(result.techTasks.length,2);assert.equal(result.assignments.length,4);assert.equal(result.dayPlans.length,8);
 assert.equal(result.coverage.complete,true);assert.equal(context.opBootstrap_(owner,'Год').coverage.complete,false);
});
console.log(`${passed} operational API tests passed`);

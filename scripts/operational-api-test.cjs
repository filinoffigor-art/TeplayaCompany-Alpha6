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
test('journal resumes interrupted write without double applying',()=>{failTable='Expenses';const b={amount:15,article:'Материалы',description:'Paint',accountable:'P1',paymentStatus:'Оплачено',method:'Наличные'};assert.throws(()=>command('addExpense',b),/SIMULATED_INTERRUPTION/);assert.equal(context.opRows_('AuditLog')[0].CommitState,'PREPARED');context.opRecover_();command('addExpense',b);assert.equal(context.opRows_('Expenses').length,1);assert.equal(context.opRows_('AuditLog')[0].CommitState,'COMMITTED');});
test('untrusted auth fields never enter audit payload',()=>{command('transfer',{from:'P1',to:'P2',amount:1,token:'SECRET_TOKEN',pairingCode:'SECRET_CODE',role:'OWNER'});assert(!JSON.stringify(tables.AuditLog.data).includes('SECRET'));});
test('manager cannot write company finances',()=>{assert.throws(()=>command('transfer',{from:'P1',to:'P2',amount:1},{userId:'M1',role:'MANAGER',finance:true}),/FORBIDDEN_FINANCE/);});
test('manager bootstrap is scoped and excludes company finance',()=>{const r=context.opBootstrap_({userId:'M1',role:'MANAGER',employeeId:'M1'},'Месяц');assert.equal(r.objects.length,1);assert.equal(r.objects[0].id,'O1');assert.equal(r.accountable,undefined);assert.equal(r.income,undefined);assert.equal(r.kpis.expenses,undefined);assert(!JSON.stringify(r).includes('Private other client'));});
test('installer bootstrap excludes contract amounts and other wages',()=>{tables.Assignments=sheet('Assignments',[{ID:'A1',Object_ID:'O1',Installer_ID:'I1'},{ID:'A2',Object_ID:'O1',Installer_ID:'I2'}]);tables.SalaryAccruals=sheet('SalaryAccruals',[{ID:'S1',Installer_ID:'I1',Начислено:100},{ID:'S2',Installer_ID:'I2',Начислено:999999}]);const r=context.opBootstrap_({userId:'U1',role:'INSTALLER',employeeId:'I1'},'Месяц');assert.equal(r.objects.length,1);assert.equal(r.objects[0].contract,undefined);assert.equal(r.assignments.length,1);assert.equal(r.payroll.length,1);assert(!JSON.stringify(r).includes('999999'));});
test('source workday does not masquerade as a technical task',()=>{tables.DailyPlans=sheet('DailyPlans',[{ID:'D1',Object_ID:'O1',Date:'2026-09-01'}]);const r=context.opBootstrap_(owner,'Месяц');assert.equal(r.dayPlans.length,0);});
test('missing profit remains null instead of cash-flow fallback',()=>{const r=context.opBootstrap_(owner,'Месяц');assert.equal(r.kpis.closedProfit,null);assert.equal(r.kpis.averagePayment,null);});
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

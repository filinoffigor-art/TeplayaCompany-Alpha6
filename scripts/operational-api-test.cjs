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
test('personal reimbursement is idempotent, bounded and never a second expense',()=>{
 tables.AccountablePersons=sheet('AccountablePersons',[{ID:'P1',Name:'Person 1',OpeningMinor:-3500000,Revision:1},{ID:'P2',Name:'Person 2',OpeningMinor:10000000,Revision:1}]);vm.runInContext('opTables_={}',context);
 const payload={Accountable_ID:'P1',Funding_Account_ID:'P2',amount:20000,expectedRevision:1,reason:'Возмещение собственных средств'};
 const first=command('reimbursePersonalFunds',payload),again=command('reimbursePersonalFunds',payload);
 assert.equal(first.id,again.id);assert.equal(context.opRows_('CashTransfers').length,1);assert.equal(context.opRows_('Expenses').length,0);
 const transfer=context.opRows_('CashTransfers')[0];assert.equal(transfer.Type,'Возмещение собственных средств');assert.equal(transfer.AmountMinor,2000000);
 const balances=context.opBalances_(context.opRows_('AccountablePersons'),context.opRows_('Income'),context.opRows_('Expenses'),context.opRows_('CashTransfers'));
 assert.equal(balances['Person 1'].balance,-15000);assert.equal(balances['Person 2'].balance,80000);assert.equal(balances['Person 1'].personalReimbursed,20000);
 const bootstrap=context.opBootstrap_(owner,'Месяц');assert.equal(bootstrap.capabilities.personalReimbursementsV1,true);assert.equal(bootstrap.fundingAccounts.length,1);assert.equal(bootstrap.fundingAccounts[0].id,'P2');assert.equal(bootstrap.accountable['Person 1'].revision,2);
 const history=bootstrap.expenses.find(r=>r.id===first.id);assert(history);assert.equal(history.entity,'CashTransfers');
});
test('personal reimbursement cannot exceed outstanding personal funds',()=>{
 tables.AccountablePersons=sheet('AccountablePersons',[{ID:'P1',Name:'Person 1',OpeningMinor:-10000,Revision:1},{ID:'P2',Name:'Person 2',OpeningMinor:100000,Revision:1}]);vm.runInContext('opTables_={}',context);
 assert.throws(()=>command('reimbursePersonalFunds',{Accountable_ID:'P1',Funding_Account_ID:'P2',amount:101,expectedRevision:1,reason:'Too much'}),/REIMBURSEMENT_EXCEEDS_OUTSTANDING/);
 assert.equal(context.opRows_('CashTransfers').length,0);
});
test('personal reimbursement cannot overdraw its funding account',()=>{
 tables.AccountablePersons=sheet('AccountablePersons',[{ID:'P1',Name:'Person 1',OpeningMinor:-100000,Revision:1},{ID:'P2',Name:'Person 2',OpeningMinor:5000,Revision:1}]);vm.runInContext('opTables_={}',context);
 assert.throws(()=>command('reimbursePersonalFunds',{Accountable_ID:'P1',Funding_Account_ID:'P2',amount:100,expectedRevision:1,reason:'No funds'}),/INSUFFICIENT_FUNDING/);
 assert.equal(context.opRows_('CashTransfers').length,0);
});
test('fixed assignment move preserves source history, creates bounded periods and accrues once',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Installer 1',Role:'INSTALLER',Active:true,Revision:1}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1','Монтажник':'Installer 1','Согласованная сумма':60000,Payment_Type:'FIXED',Period_Start:'2026-09-01',Period_End:'2026-09-30',UnallocatedFixedMinor:6000000,Obligation_ID:'OBL1','Назначен с':'2026-09-01','Назначен до':'2026-09-30','Статус':'Назначен',Revision:1}]);
 tables.Calendar=sheet('Calendar',[{ID:'C1',Calendar_ID:'C1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1','Монтажник':'Installer 1',Period_Start:'2026-09-01',Period_End:'2026-09-30','План начало':'2026-09-01','План конец':'2026-09-30','Статус':'Подтверждён',Revision:1}]);
 vm.runInContext('opTables_={}',context);context.opToday_=()=> '2026-09-10';
 const payload={Installer_ID:'I1',Assignment_ID:'A1',Object_ID:'O2',transitionDate:'2026-09-10',returnDate:'2026-09-20',expectedRevision:1,paymentType:'FIXED',accrueNow:20000,deferAmount:40000,closeObligation:false,reason:'Переброска на объект O2'};
 const first=command('moveInstaller',payload),again=command('moveInstaller',payload);assert.equal(first.newAssignmentId,again.newAssignmentId);
 const assignments=context.opRows_('Assignments');assert.equal(assignments.length,3);
 const source=assignments.find(r=>r.ID==='A1'),target=assignments.find(r=>r.ID===first.newAssignmentId),back=assignments.find(r=>r.ID===first.returnAssignmentId);
 assert.equal(source.Period_End,'2026-09-09');assert.equal(source.Revision,2);assert.equal(source.UnallocatedFixedMinor,0);
 assert.equal(target.Period_Start,'2026-09-10');assert.equal(target.Period_End,'2026-09-19');assert.equal(target.UnallocatedFixedMinor,4000000);assert.equal(target.Obligation_ID,'OBL1');
 assert.equal(back.Period_Start,'2026-09-20');assert.equal(back.Period_End,'2026-09-30');assert.equal(back.Obligation_ID,'OBL1');assert.equal(back.UnallocatedFixedMinor,null);
 assert.equal(context.opRows_('SalaryAccruals').length,1);assert.equal(context.opRows_('SalaryAccruals')[0].AccruedMinor,2000000);
 assert.equal(context.opRows_('Expenses').length,1);assert.equal(context.opRows_('Expenses')[0].AmountMinor,2000000);assert.equal(context.opRows_('Expenses')[0].Installer_ID,'I1');
 assert.equal(context.opRows_('Calendar').length,3);assert.equal(context.opRows_('Calendar').find(r=>r.ID==='C1').Period_End,'2026-09-09');
 const bootstrap=context.opBootstrap_(owner,'Месяц');assert.equal(bootstrap.capabilities.assignmentPeriodsV1,true);
 const dto=bootstrap.assignments.find(r=>r.Assignment_ID===first.newAssignmentId);assert.equal(dto.unallocatedFixedAmount,40000);assert.equal(dto.revision,1);assert.equal(dto.paymentType,'FIXED');
});
test('assignment move rejects overlapping installer periods before journal writes',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Installer 1',Role:'INSTALLER',Active:true,Revision:1}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1',Payment_Type:'DAILY',Period_Start:'2026-09-01',Period_End:'2026-09-30','Назначен с':'2026-09-01','Назначен до':'2026-09-30',Revision:1},{ID:'A2',Assignment_ID:'A2',Object_ID:'O2',Installer_ID:'I1',Payment_Type:'DAILY',Period_Start:'2026-09-15',Period_End:'2026-09-18','Назначен с':'2026-09-15','Назначен до':'2026-09-18',Revision:1}]);
 vm.runInContext('opTables_={}',context);
 assert.throws(()=>command('moveInstaller',{Installer_ID:'I1',Assignment_ID:'A1',Object_ID:'O2',transitionDate:'2026-09-10',returnDate:'2026-09-20',expectedRevision:1,paymentType:'DAILY',dailyRate:5000,reason:'Overlap'}),/ASSIGNMENT_OVERLAP/);
 assert.equal(context.opRows_('AuditLog').length,0);assert.equal(context.opRows_('Assignments').length,2);
});
test('daily assignment move never converts calendar days into actual days or payroll accrual',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Installer 1',Role:'INSTALLER',Active:true,Revision:1}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1',Payment_Type:'DAILY',DailyRateMinor:400000,Period_Start:'2026-09-01',Period_End:'2026-09-30',Actual_Days:3,'Назначен с':'2026-09-01','Назначен до':'2026-09-30',Revision:1}]);
 vm.runInContext('opTables_={}',context);
 const result=command('moveInstaller',{Installer_ID:'I1',Assignment_ID:'A1',Object_ID:'O2',transitionDate:'2026-09-10',expectedRevision:1,paymentType:'DAILY',dailyRate:5000,reason:'Временный переход'});
 const target=context.opRows_('Assignments').find(r=>r.ID===result.newAssignmentId);assert.equal(target.Actual_Days,0);assert.equal(target.DailyRateMinor,500000);
 assert.equal(context.opRows_('SalaryAccruals').length,0);assert.equal(context.opRows_('Expenses').length,0);
});
test('hired worker creation makes unique work days and one payroll expense with idempotent retry',()=>{
 vm.runInContext('opTables_={}',context);context.opToday_=()=> '2026-09-12';
 const payload={name:'Temp Worker',phone:'+70000000000',Object_ID:'O1',workDate:'2026-09-12',actualDays:2,dailyRate:5000,workType:'Утепление',reason:'Два фактических рабочих дня'};
 const first=command('createHiredWorkerDay',payload),again=command('createHiredWorkerDay',payload);assert.equal(first.Installer_ID,again.Installer_ID);assert.equal(first.Assignment_ID,again.Assignment_ID);
 const employee=context.opRows_('Employees').find(r=>r.ID===first.Installer_ID);assert(employee);assert.equal(employee.WorkerKind,'HIRED');assert.equal(employee.Revision,1);
 const assignment=context.opRows_('Assignments').find(r=>r.ID===first.Assignment_ID);assert.equal(assignment.Actual_Days,2);assert.equal(assignment.AccruedMinor,1000000);assert.equal(assignment.PaidMinor,0);
 const days=context.opRows_('WorkDays').filter(r=>r.Installer_ID===first.Installer_ID);assert.equal(days.length,2);assert.deepEqual(days.map(r=>r.Work_Date),['2026-09-12','2026-09-13']);
 assert.equal(context.opRows_('SalaryAccruals').length,1);assert.equal(context.opRows_('SalaryAccruals')[0].AccruedMinor,1000000);assert.equal(context.opRows_('SalaryAccruals')[0].PaidMinor,0);
 assert.equal(context.opRows_('Expenses').length,1);assert.equal(context.opRows_('Expenses')[0].AmountMinor,1000000);assert.equal(context.opRows_('Expenses')[0].Category,'Заработная плата / наёмные работники');
 const bootstrap=context.opBootstrap_(owner,'Месяц');assert.equal(bootstrap.capabilities.hiredWorkersV1,true);assert.equal(bootstrap.employees.find(r=>r.id===first.Installer_ID).workerKind,'HIRED');assert.equal(bootstrap.kpis.expenses,0);assert.equal(bootstrap.kpis.payrollAccrued,10000);
});
test('adding hired worker day preserves Installer_ID, advances revision and blocks duplicate work date',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Hired 1',Role:'INSTALLER',Active:true,WorkerKind:'HIRED',Revision:1}]);vm.runInContext('opTables_={}',context);
 const payload={Installer_ID:'I1',expectedRevision:1,Object_ID:'O1',workDate:'2026-09-14',actualDays:1,dailyRate:4500,workType:'Фасад',reason:'Фактический день'};
 const result=command('addHiredWorkerDay',payload);assert.equal(result.Installer_ID,'I1');assert.equal(result.revision,2);assert.equal(context.opRows_('Employees').find(r=>r.ID==='I1').Revision,2);
 assert.equal(context.opRows_('WorkDays').length,1);assert.equal(context.opRows_('WorkDays')[0].Work_Date,'2026-09-14');
 assert.throws(()=>context.opCommand_(owner,{action:'addHiredWorkerDay',requestId:'DUP-WORKDAY',...payload,expectedRevision:2}),/WORKDAY_DUPLICATE/);
 assert.equal(context.opRows_('WorkDays').length,1);assert.equal(context.opRows_('Expenses').length,1);
});
test('hired worker promotion keeps installer, assignments, accruals and expense IDs unchanged',()=>{
 tables.Employees=sheet('Employees',[{ID:'I1',Name:'Hired 1',Role:'INSTALLER',Active:true,WorkerKind:'HIRED',Revision:3}]);
 tables.Assignments=sheet('Assignments',[{ID:'A1',Assignment_ID:'A1',Object_ID:'O1',Installer_ID:'I1',Period_Start:'2026-09-01',Period_End:'2026-09-01',Revision:1}]);
 tables.SalaryAccruals=sheet('SalaryAccruals',[{ID:'S1',Accrual_ID:'S1',Object_ID:'O1',Installer_ID:'I1',Assignment_ID:'A1',Expense_ID:'E1',AccruedMinor:500000,PaidMinor:0,Revision:1}]);
 tables.Expenses=sheet('Expenses',[{ID:'E1',Date:'2026-09-01',Category:'Заработная плата / наёмные работники',AmountMinor:500000,Installer_ID:'I1',Status:'CONFIRMED',Revision:1}]);vm.runInContext('opTables_={}',context);
 const payload={Installer_ID:'I1',expectedRevision:3,reason:'Перевод в постоянные монтажники'};const first=command('promoteHiredWorker',payload),again=command('promoteHiredWorker',payload);assert.equal(first.Installer_ID,again.Installer_ID);assert.equal(first.revision,4);
 const employee=context.opRows_('Employees').find(r=>r.ID==='I1');assert.equal(employee.WorkerKind,'STAFF');assert.equal(employee.Revision,4);
 assert.equal(context.opRows_('Assignments')[0].ID,'A1');assert.equal(context.opRows_('SalaryAccruals')[0].ID,'S1');assert.equal(context.opRows_('Expenses')[0].ID,'E1');
});
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

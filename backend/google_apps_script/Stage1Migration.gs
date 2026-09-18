/**
 * OPTIONAL migration. Not called from bootstrap/doPost and not deployed automatically.
 * Run stage1MigrationCheck(), then stage1MigrationBackup(), then stage1MigrationApply().
 * Existing operational cells, IDs and formulas are never rewritten.
 */
const STAGE1_MIGRATION_ID = 'TK4_STAGE1_2026_09_ADDENDUM_V1';
const STAGE1_SCHEMA = [
  {name:'Сотрудники API', row:1, required:['Installer_ID'], columns:['Worker_Kind','Promoted_At','Promoted_By','Revision']},
  {name:'Назначения монтажников', row:1, required:['Assignment_ID','Object_ID','Installer_ID'], columns:['Parent_Assignment_ID','Payment_Type','Daily_Rate','Period_Start','Period_End','Actual_Days','Unallocated_Fixed_Amount','Obligation_ID','Closed_At']},
  {name:'Начисления монтажникам', row:1, required:['Installer_ID','Object_ID'], columns:['Assignment_ID','Accrual_Event_ID','Obligation_ID','Expense_ID','Recognition_Basis']},
  {name:'Технические задания', row:1, required:['TechTask_ID','Object_ID'], columns:['Client_ID','Lead_ID','Deal_ID','Survey_ID']},
  {name:'План ТЗ по дням', row:1, required:['TechTask_ID','Object_ID'], columns:['Stage_ID','Stage_Start','Stage_End','Assignment_ID']},
  {name:'Журнал событий Stage1', row:1, required:[], columns:['Event_ID','Idempotency_Key','Actor_ID','Created_At','Entity_Type','Entity_ID','Operation','Old_Value_JSON','New_Value_JSON','Reason','Request_Hash','Commit_State']},
  {name:'Рабочие дни Stage1', row:1, required:[], columns:['WorkDay_ID','Installer_ID','Assignment_ID','Object_ID','Work_Date','Actual_Days','Daily_Rate','Accrual_Event_ID','Created_At','Actor_ID','Revision']},
  {name:'Возмещения Stage1', row:1, required:[], columns:['Reimbursement_ID','Accountable_ID','Amount','Funding_Account_ID','Created_At','Actor_ID','Reason','Event_ID','Idempotency_Key','Revision']},
  {name:'Связи CRM Stage1', row:1, required:[], columns:['Client_ID','Lead_ID','Deal_ID','Survey_ID','Object_ID','Provider','External_ID','Updated_At','Revision']}
];

function stage1MigrationBook_(){return ss_();}
function stage1MigrationCheck(){
  const book=stage1MigrationBook_();
  const plan=STAGE1_SCHEMA.map(spec=>{
    const sheet=book.getSheetByName(spec.name);
    if(!sheet){if(spec.required.length)throw new Error('Missing existing sheet: '+spec.name);return {name:spec.name,row:spec.row,create:true,headers:[],append:spec.columns.slice()};}
    const headers=sheet.getLastColumn()?sheet.getRange(spec.row,1,1,sheet.getLastColumn()).getDisplayValues()[0].map(String):[];
    const nonempty=headers.filter(Boolean);if(new Set(nonempty).size!==nonempty.length)throw new Error('Ambiguous headers: '+spec.name);
    spec.required.forEach(header=>{if(headers.indexOf(header)<0)throw new Error('Check existing ID header before migration: '+spec.name+' / '+header);});
    return {name:spec.name,row:spec.row,create:false,headers:headers,append:spec.columns.filter(header=>headers.indexOf(header)<0)};
  });
  const fingerprint=Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,JSON.stringify(plan)));
  return {migrationId:STAGE1_MIGRATION_ID,spreadsheetId:book.getId(),plan:plan,fingerprint:fingerprint};
}

function stage1MigrationBackup(){
  const lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    const check=stage1MigrationCheck();const source=DriveApp.getFileById(check.spreadsheetId);
    const backup=source.makeCopy(source.getName()+' — backup '+STAGE1_MIGRATION_ID+' '+new Date().toISOString());
    // Opening the copy proves the backup exists and is readable before any schema write.
    const copied=SpreadsheetApp.openById(backup.getId());if(copied.getSheets().length!==stage1MigrationBook_().getSheets().length)throw new Error('Backup sheet count mismatch');
    const ticket={sourceId:check.spreadsheetId,backupId:backup.getId(),fingerprint:check.fingerprint,createdAt:new Date().toISOString()};
    PropertiesService.getScriptProperties().setProperty(STAGE1_MIGRATION_ID+'_BACKUP',JSON.stringify(ticket));
    return ticket;
  }finally{lock.releaseLock();}
}

function stage1MigrationApply(){
  const lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    const check=stage1MigrationCheck();if(check.plan.every(item=>!item.create&&!item.append.length))return {migrationId:STAGE1_MIGRATION_ID,changed:false};
    const saved=PropertiesService.getScriptProperties().getProperty(STAGE1_MIGRATION_ID+'_BACKUP');if(!saved)throw new Error('Run backup/check first');
    const ticket=JSON.parse(saved);if(ticket.sourceId!==check.spreadsheetId||ticket.fingerprint!==check.fingerprint)throw new Error('Schema changed after backup. Run check and backup again');
    if(DriveApp.getFileById(ticket.backupId).isTrashed())throw new Error('Backup unavailable');
    const book=stage1MigrationBook_();
    check.plan.forEach(item=>{
      const sheet=book.getSheetByName(item.name)||book.insertSheet(item.name);
      if(!item.append.length)return;
      const first=sheet.getLastColumn()+1;const missing=first+item.append.length-1-sheet.getMaxColumns();if(missing>0)sheet.insertColumnsAfter(sheet.getMaxColumns(),missing);
      sheet.getRange(item.row,first,1,item.append.length).setValues([item.append]);
    });
    SpreadsheetApp.flush();
    PropertiesService.getScriptProperties().setProperty(STAGE1_MIGRATION_ID,JSON.stringify({appliedAt:new Date().toISOString(),backupId:ticket.backupId}));
    return {migrationId:STAGE1_MIGRATION_ID,changed:true,backupId:ticket.backupId};
  }finally{lock.releaseLock();}
}

/**
 * OPTIONAL workforce migration for the normalized operational database.
 * Never called from doGet/doPost/bootstrap. Run check -> backup -> apply manually.
 * Only missing columns/tables are added. Existing cells, IDs, formulas and rows are untouched.
 */
const OP_WORKFORCE_MIGRATION_ID='TK4_OPERATIONAL_WORKFORCE_2026_10_V1';
const OP_WORKFORCE_SCHEMA=[
  {name:'Assignments',required:['ID','Assignment_ID','Object_ID','Installer_ID'],columns:['Parent_Assignment_ID','Payment_Type','DailyRateMinor','Period_Start','Period_End','Actual_Days','AccruedMinor','PaidMinor','UnallocatedFixedMinor','Obligation_ID','Closed_At']},
  {name:'Calendar',required:['ID','Calendar_ID','Object_ID','Installer_ID'],columns:['Assignment_ID','Period_Start','Period_End']},
  {name:'SalaryAccruals',required:['ID','Object_ID','Installer_ID'],columns:['Assignment_ID','Obligation_ID','Accrual_Event_ID','Expense_ID','Recognition_Basis','AccruedMinor','PaidMinor']},
  {name:'WorkDays',required:[],columns:['ID','WorkDay_ID','Installer_ID','Assignment_ID','Object_ID','Work_Date','Actual_Days','DailyRateMinor','Accrual_Event_ID','CreatedAt','Actor_ID','Revision']}
];
function opWorkforceMigrationBook_(){const book=opBook_();if(book.getId()!==OP.book||book.getId()===OP.source)throw Error('MIGRATION_WRONG_DATABASE');return book;}
function opWorkforceMigrationCheck(){
  const book=opWorkforceMigrationBook_();
  const plan=OP_WORKFORCE_SCHEMA.map(spec=>{
    const sheet=book.getSheetByName(spec.name);
    if(!sheet){if(spec.required.length)throw Error('MISSING_EXISTING_TABLE_'+spec.name);return {name:spec.name,create:true,headers:[],append:spec.columns.slice()};}
    const headers=sheet.getLastColumn()?sheet.getRange(1,1,1,sheet.getLastColumn()).getDisplayValues()[0].map(String):[];
    const nonempty=headers.filter(Boolean);if(new Set(nonempty).size!==nonempty.length)throw Error('AMBIGUOUS_SCHEMA_'+spec.name);
    spec.required.forEach(header=>{if(headers.indexOf(header)<0)throw Error('MISSING_ID_COLUMN_'+spec.name+'_'+header);});
    return {name:spec.name,create:false,headers:headers,append:spec.columns.filter(header=>headers.indexOf(header)<0)};
  });
  const fingerprint=Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,JSON.stringify(plan)));
  return {migrationId:OP_WORKFORCE_MIGRATION_ID,spreadsheetId:book.getId(),plan:plan,fingerprint:fingerprint};
}
function opWorkforceMigrationBackup(){
  const lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    const check=opWorkforceMigrationCheck(),source=DriveApp.getFileById(check.spreadsheetId);
    const backup=source.makeCopy(source.getName()+' — backup '+OP_WORKFORCE_MIGRATION_ID+' '+new Date().toISOString());
    const copied=SpreadsheetApp.openById(backup.getId());if(copied.getSheets().length!==opWorkforceMigrationBook_().getSheets().length)throw Error('BACKUP_SHEET_COUNT_MISMATCH');
    const ticket={sourceId:check.spreadsheetId,backupId:backup.getId(),fingerprint:check.fingerprint,createdAt:new Date().toISOString()};
    PropertiesService.getScriptProperties().setProperty(OP_WORKFORCE_MIGRATION_ID+'_BACKUP',JSON.stringify(ticket));return ticket;
  }finally{lock.releaseLock();}
}
function opWorkforceMigrationApply(){
  const lock=LockService.getScriptLock();lock.waitLock(30000);
  try{
    const check=opWorkforceMigrationCheck();if(check.plan.every(item=>!item.create&&!item.append.length))return {migrationId:OP_WORKFORCE_MIGRATION_ID,changed:false};
    const saved=PropertiesService.getScriptProperties().getProperty(OP_WORKFORCE_MIGRATION_ID+'_BACKUP');if(!saved)throw Error('BACKUP_REQUIRED');
    const ticket=JSON.parse(saved);if(ticket.sourceId!==check.spreadsheetId||ticket.fingerprint!==check.fingerprint)throw Error('SCHEMA_CHANGED_AFTER_BACKUP');
    if(DriveApp.getFileById(ticket.backupId).isTrashed())throw Error('BACKUP_UNAVAILABLE');
    const book=opWorkforceMigrationBook_();
    check.plan.forEach(item=>{
      const sheet=book.getSheetByName(item.name)||book.insertSheet(item.name);
      if(item.create){sheet.getRange(1,1,1,item.append.length).setValues([item.append]);return;}
      if(!item.append.length)return;
      const first=sheet.getLastColumn()+1,missing=first+item.append.length-1-sheet.getMaxColumns();if(missing>0)sheet.insertColumnsAfter(sheet.getMaxColumns(),missing);
      sheet.getRange(1,first,1,item.append.length).setValues([item.append]);
    });
    SpreadsheetApp.flush();
    PropertiesService.getScriptProperties().setProperty(OP_WORKFORCE_MIGRATION_ID,JSON.stringify({appliedAt:new Date().toISOString(),backupId:ticket.backupId}));
    return {migrationId:OP_WORKFORCE_MIGRATION_ID,changed:true,backupId:ticket.backupId};
  }finally{lock.releaseLock();}
}

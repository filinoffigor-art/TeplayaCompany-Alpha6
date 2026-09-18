const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
class Sheet {
  constructor(name, headers) { this.name=name; this.cells=[headers.slice(),['KEEP_EXISTING_VALUE']]; this.maxColumns=50; }
  getLastColumn(){return Math.max(...this.cells.map(row=>row.length));}
  getMaxColumns(){return this.maxColumns;}
  insertColumnsAfter(at,count){this.maxColumns+=count;}
  getRange(row,column,height,width){return {getDisplayValues:()=>[Array.from({length:width},(_,i)=>this.cells[row-1]?.[column-1+i]??'')],setValues:values=>{assert.equal(height,1);assert.equal(row,1,'Migration writes only headers');for(let i=0;i<width;i++){assert.ok(!this.cells[row-1]?.[column-1+i],'Existing header overwritten');this.cells[row-1]??=[];this.cells[row-1][column-1+i]=values[0][i];}}};}
}
const sheets=new Map();
const properties=new Map();
let copies=0;
const book={getId:()=> 'source',getSheetByName:name=>sheets.get(name),getSheets:()=>[...sheets.values()],insertSheet:name=>{const sheet=new Sheet(name,[]);sheets.set(name,sheet);return sheet;}};
const context=vm.createContext({ss_:()=>book,LockService:{getScriptLock:()=>({waitLock(){},releaseLock(){}})},PropertiesService:{getScriptProperties:()=>({getProperty:key=>properties.get(key),setProperty:(key,value)=>properties.set(key,value)})},DriveApp:{getFileById:()=>({getName:()=> 'source',isTrashed:()=>false,makeCopy:()=>{copies++;return {getId:()=> 'backup'};}})},SpreadsheetApp:{openById:()=>book,flush(){}},Utilities:{DigestAlgorithm:{SHA_256:'sha256'},computeDigest:(_,text)=>crypto.createHash('sha256').update(text).digest(),base64EncodeWebSafe:bytes=>Buffer.from(bytes).toString('base64url')}});
vm.runInContext(fs.readFileSync('backend/google_apps_script/Stage1Migration.gs','utf8'),context);
const specs=vm.runInContext('STAGE1_SCHEMA',context);
for(const spec of specs)if(spec.required.length)sheets.set(spec.name,new Sheet(spec.name,[...spec.required,'ExistingExtra']));
assert.throws(()=>context.stage1MigrationApply(),/backup/i);
context.stage1MigrationCheck();
context.stage1MigrationBackup();
assert.equal(copies,1);
assert.equal(context.stage1MigrationApply().changed,true);
const after=JSON.stringify([...sheets.values()].map(sheet=>sheet.cells));
assert.equal(context.stage1MigrationApply().changed,false);
assert.equal(JSON.stringify([...sheets.values()].map(sheet=>sheet.cells)),after);
for(const sheet of sheets.values())assert.equal(sheet.cells[1][0],'KEEP_EXISTING_VALUE');
console.log('PASS: backup required; existing data preserved; migration retry is no-op');

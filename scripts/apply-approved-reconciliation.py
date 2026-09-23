"""Build the explicitly approved 11 source-priority resolutions + 6 live additions.

No network calls, no mutation of raw snapshots, no arbitrary fuzzy merge.
Output is a separate plan; applying it requires checking the destination revision.
"""
import json,importlib.util,datetime
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('normalize-september.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
plan=json.loads((m.LOCAL/'normalized.json').read_text(encoding='utf-8'))
live=json.loads((m.LOCAL/'live-legacy.json').read_text(encoding='utf-8'))
grids={s['properties']['title']:m.cells(s) for s in live['sheets']}
statefile=m.LOCAL/'approved-resolution-state.json'
state=json.loads(statefile.read_text(encoding='utf-8')) if statefile.exists() else {'approvedDecision':'SOURCE_PRIORITY_11_AND_PRESERVE_EXTRA_6','timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat()}
statefile.write_text(json.dumps(state,indent=2),encoding='utf-8')
audit=[];remap={};extras=[]
for entity,title,rows,idcol,amountcol,personcol in [('Income','Приходы ДС',[14],13,5,9),('Expenses','Расходы',list(range(120,130)),17,9,12)]:
    grid=grids[title]
    for r in rows:
        val=lambda c:m.value(grid.get((r,c),{}))
        oldid=val(idcol)
        if oldid not in plan['reconciliation']['unmatchedLiveIds'][title]:raise ValueError('Approved source/live identity changed')
        candidates=[x for x in plan['entities'][entity] if x['SourceRow']==r and x['SourceSheet']==title]
        if len(candidates)!=1:raise ValueError('Source row is not unique')
        record=candidates[0]
        if record['AmountMinor']!=m.money(val(amountcol)) or record.get('LegacyRecipient' if entity=='Income' else 'LegacyPerson')!=val(personcol):raise ValueError('Approved amount/person no longer matches')
        objectcol=14 if entity=='Income' else 18
        if val(objectcol) and record.get('Object_ID') and val(objectcol)!=record['Object_ID']:raise ValueError('Object mismatch in approved resolution')
        before=dict(record);remap[record['ID']]=oldid;record.update(ID=oldid,LegacyID=oldid,MigrationResolution='APPROVED_SOURCE_PRIORITY',Revision=1)
        audit.append({'ID':m.uid('AUDIT','approved-resolution',r,entity),'Actor_ID':'MIGRATION_USER_APPROVED','CreatedAt':state['timestamp'],'EntityType':entity,'EntityID':oldid,'Action':'MIGRATION_RECONCILIATION','Reason':'User approved September source priority; keep live immutable ID','BeforeJSON':before,'AfterJSON':dict(record)})
grid=grids['Расходы'];people={r['Name']:r['ID'] for r in plan['entities']['AccountablePersons']};objectids={r['ID'] for r in plan['entities']['Objects']}
for r in range(130,136):
    val=lambda c:m.value(grid.get((r,c),{}))
    oldid=val(17)
    if oldid not in plan['reconciliation']['unmatchedLiveIds']['Расходы']:raise ValueError('Approved extra live ID changed')
    if val(18) and val(18) not in objectids:raise ValueError('Extra operation has unknown object')
    rec={'ID':oldid,'LegacyID':oldid,'SourceSpreadsheetId':live['spreadsheetId'],'SourceSheet':'Расходы','SourceRow':r,'SourceColumn':0,
         'MigrationTimestamp':state['timestamp'],'Revision':int(val(22) or 1),'Date':m.date(grid.get((r,1),{})),
         'Type':val(2),'Category':val(4),'Description':val(5),'Quantity':val(6),'Unit':val(7),'PriceMinor':m.money(val(8)),
         'AmountMinor':m.money(val(9)),'Method':val(10),'LegacyStatus':val(11),'Person_ID':people.get(val(12)),
         'LegacyPerson':val(12),'Comment':val(13),'Object_ID':val(18) or None,'ToPerson_ID':None,
         'Status':{'Оплачено':'CONFIRMED','Возврат':'REFUND','К оплате':'PLANNED'}.get(val(11),'INCOMPLETE'),
         'MigrationResolution':'APPROVED_ADDITIONAL_LIVE_OPERATION','DataStatus':'IMPORTED'}
    if not rec['Person_ID'] or rec['AmountMinor'] is None or not rec['Date']:raise ValueError('Incomplete additional financial operation')
    if any(x['ID']==oldid for x in plan['entities']['Expenses']):raise ValueError('Duplicate extra ID')
    extras.append(rec);plan['entities']['Expenses'].append(rec)
    audit.append({'ID':m.uid('AUDIT','approved-extra',r),'Actor_ID':'MIGRATION_USER_APPROVED','CreatedAt':state['timestamp'],'EntityType':'Expenses','EntityID':oldid,'Action':'MIGRATION_ADDITIONAL_OPERATION','Reason':'User approved preserving six additional live operations separately','BeforeJSON':None,'AfterJSON':dict(rec)})
for name in ['SystemAlerts','DataQuality']:
    for row in plan['entities'][name]:
        if row.get('EntityID') in remap:row['EntityID']=remap[row['EntityID']]
plan['entities']['AuditLog'].extend(audit)
extra_total=sum((-1 if x['Status']=='REFUND' else 1)*x['AmountMinor'] for x in extras if x['Status'] in ['CONFIRMED','REFUND'])
for person in plan['entities']['AccountablePersons']:
    delta=sum((-1 if x['Status']=='REFUND' else 1)*x['AmountMinor'] for x in extras if x['Person_ID']==person['ID'] and x['Status'] in ['CONFIRMED','REFUND'])
    person['SourceControlBalanceMinor']=person['LegacyBalanceMinor'];person['AdditionalLiveExpenseMinor']=delta
    person['CalculatedBalanceMinor']-=delta
plan['reconciliation']['approvedResolution']={'sourcePriorityRecords':len(remap),'additionalOperations':len(extras),'additionalExpenseMinor':extra_total,'auditEvents':len(audit)}
plan['reconciliation']['unmatchedLiveIds']={k:[] for k in plan['reconciliation']['unmatchedLiveIds']}
plan['reconciliation']['expenseRecords']=len(plan['entities']['Expenses']);plan['reconciliation']['expensesMinor']+=extra_total
plan['reconciliation']['cutoverReady']=False
plan['reconciliation']['reason']='Financial reconciliation approved. API schema/auth/remaining live entities and source changes must still be checked before activation.'
(m.LOCAL/'reconciled.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
(m.LOCAL/'APPROVED_RECONCILIATION.json').write_text(json.dumps(plan['reconciliation'],ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(plan['reconciliation']['approvedResolution']))

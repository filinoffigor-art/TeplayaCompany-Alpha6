"""Offline, deterministic migration planner. Input snapshots are never modified.

Run against complete Sheets CellData exports in migration-local/. No Google credentials
or network I/O. Monetary arithmetic uses Decimal. Output is private, excluded from Git.
"""
import json, uuid, hashlib, datetime, collections
from decimal import Decimal
from pathlib import Path

SOURCE='1vTdo0kJmHQP-N4JOnqpzlSQ75uznf_Czo_w4s442WWU'
DESTINATION='1msnOiHA2W_M2OI6eJLDFcL_mP1L_LWIirqsVZIa3IUQ'
SCHEMA=3
ROOT=Path(__file__).resolve().parents[1]
LOCAL=ROOT/'migration-local'
NAMESPACE=uuid.UUID('3edc2e78-1b15-4c98-b93c-4a88a3c7c325')
ENTITIES=['Objects','Clients','Leads','Measurements','Income','Expenses','CashTransfers',
 'AccountablePersons','PaymentSchedule','ObjectStages','DailyPlans','Crews','CrewMembers',
 'Employees','Engineers','Managers','Payroll','Materials','ObjectMaterials','WorkTypes','Calendar',
 'PlanFact','Dictionaries','SystemAlerts','DataQuality','MigrationLog','AuditLog','Settings','Integrations']

def uid(entity,sheet,row,col=0,source=SOURCE):
    return entity[:4].upper()+'-'+str(uuid.uuid5(NAMESPACE,f'{source}|{sheet}|{row}|{col}|{entity}'))

def cells(sheet):
    result={}
    for block in sheet.get('data',[]):
        for i,row in enumerate(block.get('rowData',[]),block.get('startRow',0)+1):
            for j,cell in enumerate(row.get('values',[]),block.get('startColumn',0)+1):
                if cell:result[i,j]=cell
    return result

def value(cell):
    val=cell.get('effectiveValue',cell.get('userEnteredValue',{}))
    if 'errorValue' in val:return None
    for key in ('numberValue','stringValue','boolValue'):
        if key in val:return val[key]
    return None

def money(value):
    if value is None or value=='':return None
    try:return int((Decimal(str(value).replace('\u00a0','').replace(' ','').replace(',','.'))*100).quantize(Decimal('1')))
    except Exception:return None

def date(cell):
    text=cell.get('formattedValue','')
    try:return datetime.datetime.strptime(text.strip(),'%d.%m.%Y').date().isoformat()
    except ValueError:pass
    val=value(cell)
    if isinstance(val,(int,float)):
        return (datetime.date(1899,12,30)+datetime.timedelta(days=int(val))).isoformat()
    if isinstance(val,str):
        try:return datetime.date.fromisoformat(val[:10]).isoformat()
        except ValueError:pass
    return None

def build():
    meta=json.loads((LOCAL/'source-metadata.json').read_text(encoding='utf-8'))
    statefile=LOCAL/'migration-state.json'
    state=json.loads(statefile.read_text(encoding='utf-8')) if statefile.exists() else {
        'sourceId':SOURCE,'destinationId':DESTINATION,'schemaVersion':SCHEMA,
        'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    if state['sourceId']!=SOURCE or state['destinationId']!=DESTINATION:raise ValueError('Migration identity changed')
    statefile.write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')
    tables={name:[] for name in ENTITIES};raw=[];source={};stats=[];mapped=set();preserved=collections.Counter()
    for sheetmeta in meta['sheets']:
        prop=sheetmeta['properties'];file=LOCAL/f"raw-{prop['sheetId']}.json"
        sheet=json.loads(file.read_text(encoding='utf-8'))['sheets'][0]
        if sheet['properties']['sheetId']!=prop['sheetId']:raise ValueError('Wrong source snapshot')
        grid=cells(sheet);source[prop['title']]=(prop,grid)
        stats.append({'sheet':prop['title'],'rowsScanned':prop['gridProperties']['rowCount'],
                      'populatedRows':len({r for r,c in grid}),'populatedCells':len(grid)})
        for (r,c),cell in sorted(grid.items()):
            raw.append({'SourceSpreadsheetId':SOURCE,'SourceSheet':prop['title'],'SourceRow':r,'SourceColumn':c,
                        'RawJSON':json.dumps(cell,ensure_ascii=False,separators=(',',':')),'MigrationTimestamp':state['timestamp']})
    live=json.loads((LOCAL/'live-legacy.json').read_text(encoding='utf-8'))
    livegrids={s['properties']['title']:cells(s) for s in live['sheets']}
    def get(sheet,r,c):return value(source[sheet][1].get((r,c),{}))
    def text(sheet,r,c):return str(get(sheet,r,c) or '').strip()
    def dt(sheet,r,c):return date(source[sheet][1].get((r,c),{}))
    def record(entity,sheet,r,c=0,oldid=None):
        mapped.add((sheet,r));rec={'ID':str(oldid) if oldid else uid(entity,source[sheet][0]['sheetId'],r,c),
        'SourceSpreadsheetId':SOURCE,'SourceSheet':sheet,'SourceRow':r,'SourceColumn':c,
        'LegacyID':str(oldid or ''),'MigrationTimestamp':state['timestamp'],'Revision':1}
        tables[entity].append(rec)
        if oldid:preserved[entity]+=1
        return rec
    def issue(entity,rec,field,message,severity='WARNING'):
        alert={'ID':uid('QUALITY',entity,rec['ID'],field),'EntityType':entity,'EntityID':rec['ID'],
          'Field':field,'Severity':severity,'Message':message,'ResponsibleRole':'OWNER',
          'ResponsibleUser':'','CreatedAt':state['timestamp'],'Status':'OPEN',
          'DeepLink':('object:' if entity=='Objects' else 'kpi:notifications')+(rec['ID'] if entity=='Objects' else '')}
        if not any(x['ID']==alert['ID'] for x in tables['DataQuality']):
            tables['DataQuality'].append(alert)
            tables['SystemAlerts'].append(dict(alert))
    def require(entity,rec,fields):
        missing=[f for f in fields if rec.get(f) is None or rec.get(f)=='']
        rec['DataStatus']='Требует заполнения' if missing else 'IMPORTED'
        for field in missing:issue(entity,rec,field,'Не заполнено: '+field)
    def lv(grid,r,c):return value(grid.get((r,c),{}))
    def norm(v):return str(v or '').strip().casefold()
    # Match against several original business fields, never use a name/address as a persistent ID.
    indexes={}
    for sheet,first,idcol,keycols in [('Объекты',6,23,[2,3,4,5]),('Приходы ДС',7,13,[1,2,3,4,5,7,9]),('Расходы',11,17,[1,2,3,4,5,9,11,12])]:
        idx=collections.defaultdict(list);grid=livegrids[sheet]
        for r in sorted({r for r,c in grid if r>=first}):
            old=lv(grid,r,idcol)
            if old:idx[tuple(norm(lv(grid,r,c)) for c in keycols)].append(str(old))
        indexes[sheet]=(idx,keycols)
    used_live=set()
    def existing(sheet,r):
        idx,keycols=indexes[sheet];matches=idx.get(tuple(norm(get(sheet,r,c)) for c in keycols),[])
        candidate=next((x for x in matches if x not in used_live),None)
        if candidate:used_live.add(candidate)
        return candidate
    objectmap=collections.defaultdict(list);clientmap=collections.defaultdict(list)
    for r in sorted({r for r,c in source['Объекты'][1] if r>=6}):
        if not text('Объекты',r,2):continue
        if text('Объекты',r,2)=='ИТОГО' and 'formulaValue' in source['Объекты'][1].get((r,14),{}).get('userEnteredValue',{}):continue
        rec=record('Objects','Объекты',r,oldid=existing('Объекты',r))
        client=record('Clients','Объекты',r,3);client.update(Name=get('Объекты',r,3),Phone=get('Объекты',r,4))
        require('Clients',client,['Name','Phone']);clientmap[norm(client.get('Name'))].append(client['ID'])
        rec.update(Client_ID=client['ID'],Name=get('Объекты',r,2),Address=get('Объекты',r,2),WorkType=get('Объекты',r,5),
          LegacyCrew=get('Объекты',r,6),Status=get('Объекты',r,7),PlanStart=dt('Объекты',r,8),PlanEnd=dt('Объекты',r,9),
          ActualStart=dt('Объекты',r,10),ActualEnd=dt('Объекты',r,11),Progress=get('Объекты',r,13),
          ContractMinor=money(get('Объекты',r,14)),LegacyReceivedMinor=money(get('Объекты',r,15)),
          LegacyPayrollMinor=money(get('Объекты',r,17)),LegacyExpenseMinor=money(get('Объекты',r,18)),
          Comment=get('Объекты',r,20),Source=get('Объекты',r,21),LegacyResponsible=get('Объекты',r,22),Engineer_ID=None,Manager_ID=None)
        objectmap[norm(rec['Name'])].append(rec['ID'])
        require('Objects',rec,['Client_ID','Address','WorkType','ContractMinor','PlanStart','PlanEnd','Engineer_ID','Manager_ID'])
        if rec['Status'] in ['В работе','Завершён','Закрыт 100%'] and not rec['ActualStart']:issue('Objects',rec,'ActualStart','Нет фактической даты начала')
        if rec['Status'] in ['Завершён','Закрыт 100%'] and not rec['ActualEnd']:issue('Objects',rec,'ActualEnd','Нет фактической даты окончания')
        issue('Objects',rec,'PaymentSchedule','В источнике отсутствует отдельный график платежей; долг по срокам не определён')
    def objectlink(entity,rec,name):
        matches=objectmap[norm(name)]
        rec['Object_ID']=matches[0] if len(matches)==1 else None
        if name and len(matches)!=1:issue(entity,rec,'Object_ID','Связь с объектом не найдена однозначно', 'WARNING' if entity=='DailyPlans' else 'CRITICAL')
    persons={}
    for r in [5,6]:
        rec=record('AccountablePersons','Расходы',r)
        rec.update(Name=get('Расходы',r,1),OpeningMinor=money(get('Расходы',r,2)),LegacyBalanceMinor=money(get('Расходы',r,7)))
        persons[norm(rec['Name'])]=rec['ID']
    for r in sorted({r for r,c in source['Приходы ДС'][1] if r>=7}):
        if not any(text('Приходы ДС',r,c) for c in [1,2,3,5,7,9,10]):continue
        if all(source['Приходы ДС'][1].get((r,c),{}).get('userEnteredValue',{}).get('formulaValue') or get('Приходы ДС',r,c) in (None,'') for c in range(1,11)):continue
        rec=record('Income','Приходы ДС',r,oldid=existing('Приходы ДС',r))
        rec.update(Date=dt('Приходы ДС',r,1),Operation=get('Приходы ДС',r,3),PaymentKind=get('Приходы ДС',r,4),
          AmountMinor=money(get('Приходы ДС',r,5)),Method=get('Приходы ДС',r,6),Payer=get('Приходы ДС',r,7),
          Document=get('Приходы ДС',r,8),Recipient_ID=persons.get(norm(get('Приходы ДС',r,9))),
          LegacyRecipient=get('Приходы ДС',r,9),Comment=get('Приходы ДС',r,10))
        objectlink('Income',rec,get('Приходы ДС',r,2))
        rec['Status']='CONFIRMED' if rec['Date'] and rec['AmountMinor'] is not None and rec['Operation'] in ['Приход','Возврат клиенту'] else 'INCOMPLETE'
        require('Income',rec,['Date','AmountMinor','Object_ID','Recipient_ID'])
    for r in sorted({r for r,c in source['Расходы'][1] if r>=11}):
        if not any(text('Расходы',r,c) for c in [1,2,3,4,5,8,12,13,16]):continue
        if all(source['Расходы'][1].get((r,c),{}).get('userEnteredValue',{}).get('formulaValue') or get('Расходы',r,c) in (None,'') for c in [1,2,3,4,5,8,12,13,16]):continue
        transfer=text('Расходы',r,2)=='Перевод подотчёта';entity='CashTransfers' if transfer else 'Expenses'
        rec=record(entity,'Расходы',r,oldid=existing('Расходы',r))
        rec.update(Date=dt('Расходы',r,1),Type=get('Расходы',r,2),Category=get('Расходы',r,4),Description=get('Расходы',r,5),
          Quantity=get('Расходы',r,6),Unit=get('Расходы',r,7),PriceMinor=money(get('Расходы',r,8)),AmountMinor=money(get('Расходы',r,9)),
          Method=get('Расходы',r,10),LegacyStatus=get('Расходы',r,11),Person_ID=persons.get(norm(get('Расходы',r,12))),
          LegacyPerson=get('Расходы',r,12),Comment=get('Расходы',r,13),ToPerson_ID=persons.get(norm(get('Расходы',r,16))))
        rec['Status']={'Оплачено':'CONFIRMED','Возврат':'REFUND','К оплате':'PLANNED'}.get(rec['LegacyStatus'],'INCOMPLETE')
        objectlink(entity,rec,get('Расходы',r,3))
        require(entity,rec,['Date','AmountMinor','Person_ID']+(['ToPerson_ID'] if transfer else ['Category']))
        if not transfer and rec['Type']=='По объекту' and not rec['Object_ID']:issue(entity,rec,'Object_ID','Расход по объекту без подтверждённой связи','CRITICAL')
    # Structured dictionaries retain source cell identity, not name-derived IDs.
    crewmap={}
    for c in range(1,30,2):
        category=text('Списки',1,c)
        for r in sorted({r for r,col in source['Списки'][1] if col==c and r>1}):
            label=text('Списки',r,c)
            if not label:continue
            entity='Crews' if c==3 else 'WorkTypes' if c==1 else 'Dictionaries'
            rec=record(entity,'Списки',r,c);rec.update(Name=label,Category=category)
            if entity=='Crews':crewmap[norm(label)]=rec['ID']
    for rec in tables['Objects']:
        rec['Crew_ID']=crewmap.get(norm(rec['LegacyCrew']))
        if not rec['Crew_ID']:issue('Objects',rec,'Crew_ID','Бригада не назначена или не сопоставлена')
    # Salary grid is retained as reported workdays/amounts, never counted again as paid Expenses.
    for col in [3,5,9,11,15,17,21,23,27,29,33,35]:
        name=text('ЗП Сотрудники',3,col)
        if not name:continue
        employee=record('Employees','ЗП Сотрудники',3,col);employee.update(Name=name,Role='INSTALLER',WorkerKind='UNKNOWN',Phone=None)
        require('Employees',employee,['Phone'])
        for r in range(4,34):
            description=get('ЗП Сотрудники',r,col);amount=money(get('ЗП Сотрудники',r,col+1))
            if description in (None,'') and amount is None:continue
            rec=record('DailyPlans','ЗП Сотрудники',r,col)
            rec.update(Installer_ID=employee['ID'],Date=f'2026-09-{r-3:02d}',Description=description,
                       DayStatus='DAY_OFF' if norm(description)=='выходной' else 'LEGACY_REPORTED',ActualFraction=None)
            if norm(description)=='выходной':rec['Object_ID']=None
            else:objectlink('DailyPlans',rec,description)
            if norm(description)!='выходной' and rec['Object_ID'] is None:issue('DailyPlans',rec,'Object_ID','Нужна проверка текстовой записи рабочего дня')
            if amount is not None:
                pay=record('Payroll','ЗП Сотрудники',r,col+1);pay.update(Installer_ID=employee['ID'],WorkDay_ID=rec['ID'],Object_ID=rec['Object_ID'],
                     Date=rec['Date'],AmountMinor=amount,Kind='LEGACY_UNCLASSIFIED',Expense_ID=None)
                issue('Payroll',pay,'Kind','Уточнить: начисление или выплата; сумма не добавлена повторно в расходы')
    tables['Settings']=[{'ID':'schema','Key':'SCHEMA_VERSION','Value':SCHEMA},{'ID':'source','Key':'SOURCE_SPREADSHEET_ID','Value':SOURCE},
      {'ID':'migration','Key':'MIGRATION_STATUS','Value':'RECONCILIATION_PENDING'},{'ID':'timezone','Key':'TIME_ZONE','Value':'Europe/Moscow'}]
    tables['Integrations']=[{'ID':'telegram','Provider':'TELEGRAM','Enabled':False,'Status':'NOT_CONFIGURED'},
                            {'ID':'bitrix24','Provider':'BITRIX24','Enabled':False,'Status':'NOT_CONFIGURED'}]
    signedincome=lambda r:(-1 if r['Operation']=='Возврат клиенту' else 1)*(r['AmountMinor'] or 0)
    inc=sum(signedincome(r) for r in tables['Income'] if r['Status']=='CONFIRMED')
    exp=sum((-1 if r['Status']=='REFUND' else 1)*(r['AmountMinor'] or 0) for r in tables['Expenses'] if r['Status'] in ['CONFIRMED','REFUND'])
    balances=[]
    for rec in tables['AccountablePersons']:
        pid=rec['ID'];calculated=rec['OpeningMinor']
        if calculated is not None:
            calculated+=sum(signedincome(r) for r in tables['Income'] if r['Recipient_ID']==pid and r['Status']=='CONFIRMED')
            calculated-=sum((-1 if r['Status']=='REFUND' else 1)*(r['AmountMinor'] or 0) for r in tables['Expenses'] if r['Person_ID']==pid and r['Status'] in ['CONFIRMED','REFUND'])
            for tr in tables['CashTransfers']:
                if tr['Status']=='CONFIRMED':calculated+=(int(tr['ToPerson_ID']==pid)-int(tr['Person_ID']==pid))*(tr['AmountMinor'] or 0)
        rec['CalculatedBalanceMinor']=calculated
        balances.append({'personId':pid,'sourceMinor':rec['LegacyBalanceMinor'],'migratedMinor':calculated,'differenceMinor':None if calculated is None else calculated-(rec['LegacyBalanceMinor'] or 0)})
    unmatched={sheet:sorted(old for values in idx.values() for old in values if old not in used_live) for sheet,(idx,cols) in indexes.items()}
    # A used row is never silently discarded: each populated row has a classification.
    rows_audit=[]
    for sheet,(prop,grid) in source.items():
        for r in sorted({r for r,c in grid}):
            kind='NORMALIZED' if (sheet,r) in mapped else 'RAW_ONLY'
            reason='Mapped to operational entities' if kind=='NORMALIZED' else 'Original report/formula/header/blank template or example; preserved in LEGACY and raw cells'
            rows_audit.append({'Sheet':sheet,'Row':r,'Classification':kind,'Reason':reason})
    quality_errors=sum(x['Severity']=='CRITICAL' for x in tables['DataQuality'])
    checks={'sourceSheets':len(stats),'sourceRowsScanned':sum(s['rowsScanned'] for s in stats),'sourcePopulatedCells':len(raw),
      'objects':len(tables['Objects']),'clients':len(tables['Clients']),'incomeRecords':len(tables['Income']),'expenseRecords':len(tables['Expenses']),
      'transfers':len(tables['CashTransfers']),'incomeMinor':inc,'expensesMinor':exp,'balances':balances,'preservedLiveIds':dict(preserved),
      'unmatchedLiveIds':unmatched,'criticalDataIssues':quality_errors,'duplicatesMerged':0,
      'cutoverReady':False,'reason':'Live-only IDs, all financial totals and source immutability must be reconciled before cutover'}
    plan={'state':state,'sourceMeta':meta,'sourceStats':stats,'rawCells':raw,'entities':tables,'rowAudit':rows_audit,'reconciliation':checks}
    plan['sourceDigest']=hashlib.sha256(json.dumps(raw,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
    (LOCAL/'normalized.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
    (LOCAL/'MIGRATION_REPORT.md').write_text('# Migration preview — not cut over\n\n```json\n'+json.dumps(checks,ensure_ascii=False,indent=2)+'\n```\n',encoding='utf-8')
    (LOCAL/'DATA_QUALITY_REPORT.md').write_text('# Data quality\n\n'+''.join(f"- {x['EntityType']} {x['EntityID']} / {x['Field']}: {x['Message']}\n" for x in tables['DataQuality']),encoding='utf-8')
    print(json.dumps(checks,ensure_ascii=False))
    return plan

if __name__=='__main__':build()

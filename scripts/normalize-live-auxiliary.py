"""Carry forward live task/payment/employee IDs into the normalized staging database."""
import json,importlib.util
from pathlib import Path
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('normalize-september.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
p=json.loads((m.LOCAL/'reconciled.json').read_text(encoding='utf-8'))
live=json.loads((m.LOCAL/'live-auxiliary.json').read_text(encoding='utf-8'))
names={'Технические задания':'TechTasks','План оплат':'PaymentSchedule','Назначения монтажников':'Assignments',
       'Календарь монтажей':'Calendar','Медиа объектов':'Media','Лиды':'Leads','Замеры':'Measurements',
       'Начисления монтажникам':'SalaryAccruals','Сотрудники API':'Employees','План ТЗ по дням':'DailyPlans',
       'Инструмент и имущество':'Tools','Форма и СИЗ':'PPE'}
date_headers={'Дата','Плановая дата','План начала','План окончания','Окно от','Окно до','Назначен с','Назначен до','План начало','План конец','Факт начало','Факт конец','Дата начисления','Дата выдачи','Дата возврата','Дата покупки','План замены','Дата возврата/списания','Дата рождения'}
employee_map={};counts={}
for sheet in live['sheets']:
    title=sheet['properties']['title'];entity=names[title];grid=m.cells(sheet)
    headers={c:str(m.value(cell)) for (r,c),cell in grid.items() if r==1 and m.value(cell) is not None}
    output=p['entities'].setdefault(entity,[]);counts[entity]=0
    for row in sorted({r for r,c in grid if r>1}):
        identity=m.value(grid.get((row,1),{}))
        if not identity:continue
        record={h:m.date(grid.get((row,c),{})) if h in date_headers else m.value(grid.get((row,c),{})) for c,h in headers.items()}
        record.update(ID=identity,LegacyID=identity,SourceSpreadsheetId=live['spreadsheetId'],SourceSheet=title,SourceRow=row,
                      MigrationTimestamp=p['state']['timestamp'],Revision=record.get('Revision') or 1,
                      RawJSON={str(c):grid[(row,c)] for r,c in grid if r==row})
        if entity=='Employees':
            matches=[x for x in output if x.get('Name','').strip().casefold()==str(record.get('ФИО','')).strip().casefold()]
            if len(matches)>1:raise ValueError('Ambiguous employee identity')
            if matches:
                existing=matches[0];employee_map[existing['ID']]=identity
                existing.update(ID=identity,LegacyID=identity,Phone=record.get('Телефон'),LiveSourceRow=row,LiveSourceSpreadsheetId=live['spreadsheetId'],LiveRawJSON=record['RawJSON'])
                record=existing
            else:
                record.update(Name=record.get('ФИО'),Phone=record.get('Телефон'),Role=record.get('Роль'),WorkerKind='UNKNOWN');output.append(record)
        else:output.append(record)
        if entity=='PaymentSchedule':record.update(Object_ID=record.get('Object_ID'),Date=record.get('Плановая дата'),AmountMinor=m.money(record.get('Плановая сумма')),PaymentPlan_ID=identity)
        if entity=='Measurements':record.update(Date=record.get('Дата'),Name=record.get('Клиент'),Phone=record.get('Телефон'),Object_ID=record.get('Object_ID'),Survey_ID=identity)
        if entity=='Leads':record.update(Date=record.get('Дата'),Name=record.get('Клиент'),Phone=record.get('Телефон'),Lead_ID=identity)
        counts[entity]+=1
for table in p['entities'].values():
    for row in table:
        for field in ['Installer_ID','Employee_ID','EntityID']:
            if row.get(field) in employee_map:row[field]=employee_map[row[field]]
objects={r['ID'] for r in p['entities']['Objects']};employees={r['ID'] for r in p['entities']['Employees']}
for entity in ['TechTasks','PaymentSchedule','Assignments','Calendar','SalaryAccruals','DailyPlans','Media']:
    for row in p['entities'][entity]:
        if row.get('Object_ID') and row['Object_ID'] not in objects:raise ValueError(entity+' references unknown object')
        if row.get('Installer_ID') and row['Installer_ID'] not in employees:raise ValueError(entity+' references unknown employee')
for table,rows in p['entities'].items():
    ids=[x['ID'] for x in rows if 'ID' in x]
    if len(ids)!=len(set(ids)):raise ValueError('Duplicate ID in '+table)
p['reconciliation']['liveAuxiliaryCounts']=counts
p['reconciliation']['preservedEmployeeMappings']=len(employee_map)
(m.LOCAL/'operational-plan.json').write_text(json.dumps(p,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'imported':counts,'employeeIdMappings':len(employee_map)}))

"""Prepare explicit cell-level reconciliation against a freshly read staging snapshot.

Offline only. Never deletes rows, changes legacy tabs, or writes to the source.
The caller must recheck the snapshot before applying each prepared patch.
"""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];LOCAL=ROOT/'migration-local'
def read(name):return json.loads((LOCAL/name).read_text(encoding='utf-8'))
plan=read('operational-plan.json');meta=read('staging-meta.json');before=read('staging-before.json')
assert meta['spreadsheetId']==plan['state']['destinationId']!=plan['state']['sourceId']
assert plan['reconciliation']['approvedResolution']['sourcePriorityRecords']==11
schema=json.loads((ROOT/'backend/google_apps_script/operational-schema.json').read_text(encoding='utf-8'))
def value(cell):
 v=cell.get('userEnteredValue',cell.get('effectiveValue',{}))
 return next(iter(v.values()),None)
def grid(sheet):
 return {(b.get('startRow',0)+i,b.get('startColumn',0)+j):value(c) for b in sheet.get('data',[]) for i,r in enumerate(b.get('rowData',[])) for j,c in enumerate(r.get('values',[])) if value(c) is not None}
def scalar(v):return json.dumps(v,ensure_ascii=False,separators=(',',':')) if isinstance(v,(dict,list)) else v
def cell(v):
 v=scalar(v)
 return {} if v is None else {'userEnteredValue':{'boolValue' if isinstance(v,bool) else 'numberValue' if isinstance(v,(int,float)) else 'stringValue':v}}
structure=[];writes=[];checks=[];manifest=[];nextid=max(s['properties']['sheetId'] for s in meta['sheets'] if 15000<=s['properties']['sheetId']<16000)+1
desired={k:[dict(r) for r in v] for k,v in plan['entities'].items()}
desired['Settings'] += [{'ID':'coverage-from','Key':'COVERAGE_FROM','Value':'2026-09-01'},{'ID':'coverage-to','Key':'COVERAGE_TO','Value':'2026-09-30'}]
for name in schema:desired.setdefault(name,[])
for name,records in desired.items():
 props=next((s['properties'] for s in meta['sheets'] if s['properties']['title']==name),None)
 original=next((s for s in before['sheets'] if s['properties']['title']==name),None)
 g=grid(original) if original else {}
 headers=[g.get((0,c),'') for c in range(max([c for r,c in g if r==0],default=-1)+1)]
 for h in list(dict.fromkeys(schema.get(name,[])+[k for r in records for k in r])):
  if h not in headers:headers.append(h)
 if not props:
  props={'sheetId':nextid,'title':name,'gridProperties':{'rowCount':max(100,len(records)+10),'columnCount':max(26,len(headers)),'frozenRowCount':1}};nextid+=1
  structure.append({'addSheet':{'properties':props}})
 else:
  gp=props['gridProperties']
  if len(headers)>gp['columnCount']:structure.append({'updateSheetProperties':{'properties':{'sheetId':props['sheetId'],'gridProperties':{'columnCount':len(headers)}},'fields':'gridProperties.columnCount'}})
 sid=props['sheetId'];oldrows={r:{h:g.get((r,c)) for c,h in enumerate(headers)} for r in sorted({r for r,c in g if r>0})}
 indexes={v['ID']:r for r,v in oldrows.items() if v.get('ID')};used=set();last=max(oldrows,default=0)
 def patch(row,col,val):
  if g.get((row,col))==scalar(val) or g.get((row,col)) in (None,'') and val in (None,''):return
  checks.append({'sheetId':sid,'row':row,'column':col,'before':g.get((row,col)),'after':scalar(val)})
  writes.append({'updateCells':{'start':{'sheetId':sid,'rowIndex':row,'columnIndex':col},'rows':[{'values':[cell(val)]}],'fields':'userEnteredValue'}})
 for c,h in enumerate(headers):patch(0,c,h)
 for record in records:
  row=indexes.get(record.get('ID'))
  if row is None and record.get('SourceRow'):
   matches=[r for r,v in oldrows.items() if all(v.get(k)==record.get(k) for k in ['SourceSpreadsheetId','SourceSheet','SourceRow','SourceColumn'])]
   if len(matches)>1:raise ValueError('Ambiguous lineage in '+name)
   if matches:row=matches[0]
  if row is None:last+=1;row=last
  if row in used:raise ValueError('Repeated target row in '+name)
  used.add(row)
  for key,val in record.items():patch(row,headers.index(key),val)
 if last>=props['gridProperties']['rowCount']:structure.append({'updateSheetProperties':{'properties':{'sheetId':sid,'gridProperties':{'rowCount':last+20}},'fields':'gridProperties.rowCount'}})
 manifest.append({'name':name,'sheetId':sid,'headers':headers,'records':len(records)})
output={'spreadsheetId':meta['spreadsheetId'],'structure':structure,'writes':writes,'checks':checks,'manifest':manifest}
(LOCAL/'operational-patch.json').write_text(json.dumps(output,ensure_ascii=False),encoding='utf-8')
print(json.dumps({'schemaRequests':len(structure),'changedCells':len(checks),'tables':len(manifest)}))

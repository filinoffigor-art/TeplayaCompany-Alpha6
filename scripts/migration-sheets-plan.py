"""Prepare bounded Sheets batches for a new non-live destination only. Never edits source."""
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];LOCAL=ROOT/'migration-local'
plan=json.loads((LOCAL/'normalized.json').read_text(encoding='utf-8'))
state=plan['state'];destination=state['destinationId']
assert destination and destination!=state['sourceId']
statefile=LOCAL/'destination-state.json'
existing=json.loads(statefile.read_text(encoding='utf-8')) if statefile.exists() else {}
digest=hashlib.sha256(json.dumps(plan['entities'],sort_keys=True,ensure_ascii=False).encode()).hexdigest()
if existing:
    if existing.get('spreadsheetId')!=destination or existing.get('sourceId')!=state['sourceId'] or existing.get('schemaVersion')!=state['schemaVersion']:
        raise ValueError('Destination identity/schema mismatch')
    if existing.get('status') not in ('MIGRATION_PREVIEW','RECONCILIATION_PENDING'):
        raise ValueError('Refusing migration writes to an activated operational database')
    if existing.get('verifiedNormalized') and existing.get('normalizedDigest')!=digest:
        raise ValueError('Verified destination differs from new plan; prepare an explicit reconciliation patch')
known={s['sheetId']:s['title'] for s in existing.get('sheets',[])}
manifest=[]
def cell(v):
    if v is None:return {}
    if isinstance(v,bool):return {'userEnteredValue':{'boolValue':v}}
    if isinstance(v,(int,float)):return {'userEnteredValue':{'numberValue':v}}
    if isinstance(v,(dict,list)):v=json.dumps(v,ensure_ascii=False,separators=(',',':'))
    return {'userEnteredValue':{'stringValue':str(v)}}
def save(name,requests):
    (LOCAL/name).write_text(json.dumps({'spreadsheet_id':destination,'requests':requests},ensure_ascii=False),encoding='utf-8')
    manifest.append({'file':name,'requestCount':len(requests)})
tables={'_README':[{'Key':'STATUS','Value':'MIGRATION PREVIEW — NOT LIVE'},
 {'Key':'SourceSpreadsheetId','Value':state['sourceId']},{'Key':'SchemaVersion','Value':state['schemaVersion']},
 {'Key':'MigrationTimestamp','Value':state['timestamp']},{'Key':'SourceTimeZone','Value':plan['sourceMeta']['properties']['timeZone']},
 {'Key':'OperationalTimeZone','Value':'Europe/Moscow'},
 {'Key':'Protection','Value':'Original source unchanged; LEGACY sheets frozen with original cell data in notes.'},
 {'Key':'Cutover','Value':'Blocked until existing backend records and financial conflicts are reconciled.'} ],**plan['entities']}
tables['SourceRows']=plan['rowAudit']
tables['MigrationLog']=[{'Check':k,'Result':v} for k,v in plan['reconciliation'].items()]
live=json.loads((LOCAL/'live-legacy.json').read_text(encoding='utf-8'))
unmatched={x for ids in plan['reconciliation']['unmatchedLiveIds'].values() for x in ids}
review=[]
for sheet in live['sheets']:
    for block in sheet.get('data',[]):
        for n,row in enumerate(block.get('rowData',[]),block.get('startRow',0)+1):
            matches=[c.get('formattedValue') for c in row.get('values',[]) if c.get('formattedValue') in unmatched]
            if matches:review.append({'ID':matches[0],'SourceSpreadsheetId':live['spreadsheetId'],'SourceSheet':sheet['properties']['title'],
              'SourceRow':n,'Status':'REVIEW_REQUIRED','RawJSON':row,'Reason':'Unmatched live record: preserve ID, resolve date/content difference or import as additional operation before cutover'})
tables['LiveReview']=review
for i,(name,rows) in enumerate(tables.items()):
    sid=15000+i
    headers=list(dict.fromkeys(k for row in rows for k in row)) or ['ID','Status','Revision','SourceSpreadsheetId','SourceSheet','SourceRow','MigrationTimestamp']
    width=max(1,len(headers));data=[headers]+[[row.get(h) for h in headers] for row in rows]
    writes=[]
    for offset in range(0,len(data),100):
        chunk=data[offset:offset+100]
        writes.append({'updateCells':{'range':{'sheetId':sid,'startRowIndex':offset,'endRowIndex':offset+len(chunk),'startColumnIndex':0,'endColumnIndex':width},
          'rows':[{'values':[cell(v) for v in row]} for row in chunk],'fields':'userEnteredValue'}})
    for j in range(0,len(writes),3):save(f'write-{i:02d}-{j//3:02d}.json',[] if existing.get('verifiedNormalized') else writes[j:j+3])
    style=[{'repeatCell':{'range':{'sheetId':sid,'startRowIndex':0,'endRowIndex':1,'startColumnIndex':0,'endColumnIndex':width},
      'cell':{'userEnteredFormat':{'backgroundColor':{'red':.03,'green':.45,'blue':.28},'textFormat':{'bold':True,'foregroundColor':{'red':1,'green':1,'blue':1}}}},'fields':'userEnteredFormat'}},
      {'updateDimensionProperties':{'range':{'sheetId':sid,'dimension':'COLUMNS','startIndex':0,'endIndex':width},'properties':{'pixelSize':180},'fields':'pixelSize'}}]
    save(f'style-{i:02d}.json',style)
structure=[{'addSheet':{'properties':{'sheetId':15000+i,'title':name,'gridProperties':{'rowCount':max(100,len(rows)+5),'columnCount':max(26,len(set(k for r in rows for k in r))),'frozenRowCount':1}}}} for i,(name,rows) in enumerate(tables.items())]
for request in structure:
    prop=request['addSheet']['properties']
    if prop['sheetId'] in known and known[prop['sheetId']]!=prop['title']:raise ValueError('Destination sheet ID collision')
structure=[r for r in structure if r['addSheet']['properties']['sheetId'] not in known]
save('structure.json',structure)
for prop in [s['properties'] for s in plan['sourceMeta']['sheets']]:
    sheet=json.loads((LOCAL/f"raw-{prop['sheetId']}.json").read_text(encoding='utf-8'))['sheets'][0]
    width=prop['gridProperties']['columnCount'];result=[]
    for block in sheet.get('data',[]):
        start=block.get('startRow',0);startcol=block.get('startColumn',0)
        for offset in range(0,len(block.get('rowData',[])),50):
            chunks=[]
            for idx,row in enumerate(block['rowData'][offset:offset+50]):
                values=[{} for _ in range(width)]
                for col,c in enumerate(row.get('values',[]),startcol):
                    if not c:continue
                    v=c.get('effectiveValue',c.get('userEnteredValue',{}))
                    if 'errorValue' in v or 'formulaValue' in v:v={'stringValue':c.get('formattedValue','')}
                    values[col]={'userEnteredValue':v,'note':json.dumps({'SourceSpreadsheetId':state['sourceId'],'SourceSheet':prop['title'],
                      'SourceRow':start+offset+idx+1,'SourceColumn':col+1,'MigrationTimestamp':state['timestamp'],'OriginalCell':c},ensure_ascii=False,separators=(',',':'))}
                chunks.append({'values':values})
            result.append({'updateCells':{'range':{'sheetId':prop['sheetId'],'startRowIndex':start+offset,'endRowIndex':start+offset+len(chunks),'startColumnIndex':0,'endColumnIndex':width},'rows':chunks,'fields':'userEnteredValue,note'}})
    for j,batch in enumerate(result):save(f"legacy-{prop['sheetId']}-{j:02d}.json",[] if existing.get('verifiedLegacy') else [batch])
# Freeze before changing timezone so original interpreted values stay stable.
finish=[]
for s in plan['sourceMeta']['sheets']:
    prop=s['properties']
    if known.get(prop['sheetId'])=='LEGACY__'+prop['title']:continue
    finish.append({'updateSheetProperties':{'properties':{'sheetId':prop['sheetId'],'title':'LEGACY__'+prop['title'],'hidden':True},'fields':'title,hidden'}})
    finish.append({'addProtectedRange':{'protectedRange':{'range':{'sheetId':prop['sheetId']},'description':'Immutable migration snapshot','warningOnly':False}}})
finish.append({'updateSpreadsheetProperties':{'properties':{'timeZone':'Europe/Moscow'},'fields':'timeZone'}})
save('finish-legacy.json',finish)
(LOCAL/'batch-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
(LOCAL/'table-manifest.json').write_text(json.dumps([{'name':name,'sheetId':15000+i,'rows':len(rows),'columns':list(dict.fromkeys(k for row in rows for k in row)) or ['ID','Status','Revision','SourceSpreadsheetId','SourceSheet','SourceRow','MigrationTimestamp']} for i,(name,rows) in enumerate(tables.items())],ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'destination':destination,'tables':len(tables),'liveReview':len(review),'plannedOnly':True}))

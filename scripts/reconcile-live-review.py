"""Explain unmatched live IDs without merging or changing any financial record."""
import importlib.util,json
from pathlib import Path

spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('normalize-september.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
plan=json.loads((m.LOCAL/'normalized.json').read_text(encoding='utf-8'))
live=json.loads((m.LOCAL/'live-legacy.json').read_text(encoding='utf-8'))
rows=[]
for sheet in live['sheets']:
    title=sheet['properties']['title'];grid=m.cells(sheet)
    if title not in ('Приходы ДС','Расходы'):continue
    income=title=='Приходы ДС';entity='Income' if income else 'Expenses';idcol=13 if income else 17
    for row in sorted({r for r,c in grid}):
        val=lambda c:m.value(grid.get((row,c),{}))
        identity=val(idcol)
        if identity not in plan['reconciliation']['unmatchedLiveIds'].get(title,[]):continue
        amount=m.money(val(5 if income else 9));person=val(9 if income else 12)
        candidates=[r for r in plan['entities'][entity] if r['AmountMinor']==amount and r.get('LegacyRecipient' if income else 'LegacyPerson')==person]
        matches='; '.join(f"строка {r['SourceRow']}, {r['Date']}" for r in candidates) or 'Нет однозначного совпадения'
        rows.append({'ID':identity,'Type':entity,'LiveRow':row,'Date':m.date(grid.get((row,1),{})),
                     'AmountMinor':amount,'Person':person,'Description':str(val(2 if income else 5) or ''),
                     'SourceCandidates':matches,'CandidateIDs':[r['ID'] for r in candidates],'Status':'REVIEW_REQUIRED'})
(m.LOCAL/'live-review.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
text='# Расхождения перед переключением API\n\nНи одна запись не объединена и не удалена. Действующий backend не переключён.\n\n'
text+='| Тип / строка текущей базы | Дата | Сумма, ₽ | Ответственный | Назначение | Кандидат в сентябрьском источнике |\n|---|---|---:|---|---|---|\n'
for r in rows:
    vals=[r['Type']+' / '+str(r['LiveRow']),r['Date'],str(r['AmountMinor']/100),r['Person'],r['Description'],r['SourceCandidates']]
    text+='| '+' | '.join(str(x).replace('|','/').replace('\n',' ') for x in vals)+' |\n'
text+='\nСовпадение суммы и ответственного — только кандидат для проверки, не доказательство дубликата. '
text+='При подтверждении сохраняется существующий ID. Новые операции отдельно меняют остатки относительно контрольной даты источника.\n'
text+='\nПосле снимка изменились подписи на листе «ЗП Сотрудники»: C76 — «Дали в долг», E76 — «не премия премия, отпускные». '
text+='Оба варианта сохранены в SourceChanges новой базы.\n'
(m.LOCAL/'RECONCILIATION_REVIEW.md').write_text(text,encoding='utf-8')
print(json.dumps({'unmatchedRecords':len(rows),'withCandidates':sum(bool(r['CandidateIDs']) for r in rows),'report':'migration-local/RECONCILIATION_REVIEW.md'}))

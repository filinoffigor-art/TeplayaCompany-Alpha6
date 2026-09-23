"""Determinism and financial invariants. Business snapshots stay outside Git."""
import importlib.util
import unittest
from pathlib import Path
from contextlib import redirect_stdout
from io import StringIO

spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('normalize-september.py'))
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class PrimitiveTests(unittest.TestCase):
    def test_decimal_money_and_missing_are_distinct(self):
        self.assertEqual(m.money('1 234,56'),123456)
        self.assertEqual(m.money('-0,01'),-1)
        self.assertEqual(m.money(0),0)
        self.assertIsNone(m.money(None))
        self.assertIsNone(m.money('не заполнено'))
    def test_ids_use_lineage_not_business_names(self):
        self.assertEqual(m.uid('Objects',1,2),m.uid('Objects',1,2))
        self.assertNotEqual(m.uid('Objects',1,2),m.uid('Objects',1,3))
        self.assertNotEqual(m.uid('Objects',1,2),m.uid('Clients',1,2))
    def test_displayed_date_has_priority_over_timezone(self):
        self.assertEqual(m.date({'formattedValue':'16.09.2026','effectiveValue':{'numberValue':46282}}),'2026-09-16')
        self.assertIsNone(m.date({'formattedValue':'Не заполнено'}))

@unittest.skipUnless((m.LOCAL/'source-metadata.json').exists(),'Private migration snapshots are not present')
class SnapshotTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with redirect_stdout(StringIO()): cls.plan=m.build()
    def test_repeat_produces_identical_ids_values_and_timestamp(self):
        with redirect_stdout(StringIO()): repeated=m.build()
        self.assertEqual(self.plan,repeated)
    def test_all_ids_unique_within_entity(self):
        for name,rows in self.plan['entities'].items():
            ids=[r['ID'] for r in rows if 'ID' in r]
            self.assertEqual(len(ids),len(set(ids)),name)
    def test_transfers_never_enter_expenses(self):
        self.assertTrue(self.plan['entities']['CashTransfers'])
        self.assertFalse(any(r['Type']=='Перевод подотчёта' for r in self.plan['entities']['Expenses']))
    def test_source_balances_reconcile_exactly(self):
        for balance in self.plan['reconciliation']['balances']:
            self.assertIsNotNone(balance['sourceMinor'])
            self.assertEqual(balance['differenceMinor'],0)
    def test_examples_and_total_footer_are_not_entities(self):
        for name,row in [('Objects',5),('Income',6),('Expenses',10)]:
            self.assertFalse(any(x['SourceRow']==row for x in self.plan['entities'][name]))
        self.assertFalse(any(x['Name']=='ИТОГО' for x in self.plan['entities']['Objects']))
    def test_unresolved_live_operations_block_cutover(self):
        self.assertFalse(self.plan['reconciliation']['cutoverReady'])
        self.assertTrue(any(self.plan['reconciliation']['unmatchedLiveIds'].values()))
    def test_source_and_destination_are_distinct(self):
        self.assertNotEqual(self.plan['state']['sourceId'],self.plan['state']['destinationId'])

if __name__=='__main__':unittest.main()

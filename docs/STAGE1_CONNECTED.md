# Connected 6.3.1 — 23 September 2026

This report supersedes the deployment statements in STAGE1_ACCEPTANCE.md for 6.2.0. The full Stage 1 specification remains the target; the limitations below are still open.

## Delivered

- Native Android dashboard/navigation matching the selected references; equal metric/quick-access geometry, consistent native icons, refresh/cache and missing-data states.
- Compact finance overview, income/expense/transfer lists, period/date search, API category analytics and honest unconfigured integration screens.
- Financial amount corrections require a reason and expected revision. The backend records before/after changes and recovers interrupted multi-record commands through a write-ahead audit journal. Repeated request IDs do not duplicate operations; transfers are separate from company expenses.
- Server-enforced scope for manager/engineer/installer; company finances restricted to owner/admin or authorized partner. The client accepts scoped data only from the explicit v3 contract. Closed-object profit stays unavailable without verified cost data. Incomplete quarter/year coverage is visible.
- Existing package `ru.teplayakompaniya.tk4`, SharedPreferences and `/exec` URL retained. VersionCode 603002; versionName 6.3.1-connected.

## Migration and deployment

The original September workbook is read-only for this migration. A private native copy preserves 13 protected LEGACY tabs, original formulas/formatting in cell notes and row lineage. No source sheet was cleared or rewritten. Two source labels changed externally during the work and are recorded separately in SourceChanges.

The explicitly approved reconciliation uses September content for 11 matches while keeping live immutable IDs. Six additional live operations remain separate. Existing task, payment, assignment, employee and device identities were carried forward. All 1,384 changed staging cells were read back and matched; financial balances reconciled both offline and in Apps Script. Private snapshots and credentials are excluded from Git.

Operational database: `1msnOiHA2W_M2OI6eJLDFcL_mP1L_LWIirqsVZIa3IUQ`. Apps Script deployment version 3 uses the existing `/exec`; health returns `tk4-v3-connected` and this database ID. The editor's read-only verification checked bootstrap, 14 objects, 9 receipts, 121 expenses, 5 transfers, 2 users, 2 technical tasks and 4 assignments.

`OperationalApi.gs` is the deployed standalone script. Do not concatenate it with the old Code.gs. Deployment version 2 is retained for recovery. Switching back after new writes requires reconciliation first; otherwise those new writes would be absent in the old database.

## Validation

- Locally: 16 API checks (including private migrated data) and 10 migration checks passed.
- CI runs public API checks, migration primitives (private snapshot checks are skipped), Android unit tests, lint, debug/release compilation and emulator navigation/period/role/bounds tests at 412×915 dp.
- Emulator fixtures are only in the separate test APK; they do not write to production Sheets. Same-key baseline upgrade checks preference retention, not compatibility with the user's original certificate.
- Final CI run, commit and APK hashes are recorded with the delivery artifact after the run completes.

## Known limitations

- Original Alpha 6.1 signing key is unavailable. A new debug certificate cannot update the installed APK. CI labels this artifact QA-ONLY and also provides an unsigned release. Do not uninstall the user's current application to bypass this restriction.
- User-device update, paired live end-to-end writes, real photo upload and PDF sharing have not been exercised on the user's phone.
- Existing technical tasks remain read-only until a revision-safe patch contract is implemented. Creating a new task requires a confirmed object and preserves immutable child IDs on retries.
- Personal reimbursements, hired-worker lifecycle, assignment moves/returns and overlap checks are gated off until their full server contracts are delivered.
- Complete profit allocation, historical coverage outside September, advanced analytics, manager compensation and missing CRM fields require further data/model work. Unknown values are not populated with demonstration figures.
- Bitrix24 and Telegram credentials are not configured. Future synchronization belongs on the backend and must use immutable client/lead/deal/survey/object IDs. No credentials are placed in the APK.
- Role/user/dictionary administration and backup/restore controls are not yet fully implemented in the Android settings UI. Authentication migration preserves existing devices; new restricted-role users require an explicit Employee_ID/assignment mapping.

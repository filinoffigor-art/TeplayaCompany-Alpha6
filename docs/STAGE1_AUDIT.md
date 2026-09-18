# Stage 1 — audit and implementation plan

Baseline: 4ee39e807acdf085113c9204db46e28dce008737. Specification: TK4_STAGE1_CODEX_PACKAGE/01_MASTER_SPEC_STAGE1.md.

## Findings before implementation

- MainActivity owns navigation, UI, models, parsing and persistence. `seedDemoData`, `loadDemoState`, `TechTask` defaults, employee creation/payment helpers and profile photo action contain demo behavior. Empty objects OR installers reseeds all data. Bootstrap fabricates fallback engineer/manager records, zero metrics and balances.
- `actualTurnover`, `actualExpenses`, `remainingReceiptsTotal`, `kpiValue`, profit cards, survey/employee summaries and average-check drilldown calculate values from partial local lists rather than authoritative period-scoped API data. `periodValue` scales demo numbers.
- Bottom navigation: `addBottomNav`; quick access and attention: `showMain`; cards: `kpiCard`, `miniCard`, `quickCard`; profile/settings: `showSettings`, `editProfileDialog`.
- API uses a background executor but has no request deduplication or durable snapshot. Period changes during an active bootstrap may display a stale response as the new period. Errors invalidate the successful display. Media encoding happens on the main thread.
- Roles are display-only. Current FAST_SYNC backend returns a full-company bootstrap for every authenticated role; UI filtering alone cannot secure that API. Owner/partner integration must remain compatible; manager/installer access must fail closed until backend returns explicitly scoped data.
- Workflow uses an ephemeral default debug keystore. It uploads only APK. There is no tracked/saved signing key or signing secret referenced. Keeping applicationId and signing configuration alone does NOT preserve the signing certificate across fresh runners. Compatible update requires the original private key; never advise uninstalling to work around this.
- SharedPreferences name is `tk4_connected`. Existing `state`, tokens and settings must remain intact. New snapshot/profile keys must be additive and user-scoped.
- Backend FAST_SYNC is already deployed as version 2. No production sheet migrations or backend deployment are authorized by this implementation plan; prepare separate changes when needed, preserve /exec.

## Plan

1. Introduce testable data/access rules, account/period-scoped snapshots, deduplication and explicit unavailable states. Preserve old preferences without using legacy demo records as live data.
2. Unify native controls, header, bottom navigation and grids; implement pull refresh, loading, real avatar selection, settings and event filters.
3. Use stable IDs for drilldowns and permissions; improve objects, date picking, payment completeness, calendar and backend extension contracts. Disable unsupported mutations instead of simulating success.
4. Compile, unit test and lint; validate layouts/runtime where an emulator is available. Gate installable distribution on original signing-key availability and certificate comparison. Report every unverified acceptance item.

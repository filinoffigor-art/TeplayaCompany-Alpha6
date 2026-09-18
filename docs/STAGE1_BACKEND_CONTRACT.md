# Stage 1 / дополнение: контракт следующего обновления backend

Текущий `/exec` сохраняется. Этот документ и Stage1Migration.gs НЕ активируют новые операции. Android показывает формы, но не отправляет неподдерживаемые команды. Нельзя выставлять capability=true до реализации и проверки соответствующего серверного обработчика.

## Общие гарантии

- Bootstrap остаётся пакетным, без ensureTechnicalIds в read path. Добавить `coverage` (period/from/to/complete), `capabilities`, `scope` и quality reasons. Лимитированные income/expenses не являются полной историей периода.
- Проверка роли и области данных обязательна на сервере: MANAGER — собственный рабочий набор, INSTALLER — только назначения/начисления/дни/медиа; ENGINEER — разрешённые объекты, OWNER/ADMIN — полный доступ. Не отдавать общие финансы в ответ ограниченной роли.
- `Client_ID / Lead_ID / Deal_ID / Survey_ID / Object_ID / TechTask_ID / PaymentPlan_ID / Installer_ID / Assignment_ID / Expense_ID` неизменны. Не использовать имя, адрес или телефон как ключ. Миграция не генерирует замену прежним ID.
- Все команды: action, schemaVersion=1, idempotencyKey, reason. Сервер берёт actor из auth, время из сервера. Не доверяет actor/time/итоговой сумме из APK.
- Под ScriptLock проверить роль, revision, idempotencyKey+hash payload, весь набор ссылок. Повтор того же key возвращает прежний результат; иной payload с тем же key — конфликт. Журнал PREPARED/COMMITTED позволяет восстановиться после частичной записи Sheets. Не обещать транзакционность нескольких setValues без reconciliation.
- Audit: Event_ID, actor, timestamp, operation, entity IDs, old/new JSON, reason. Исключить token, pairingCode и полный auth body из всех логов (в действующем doPost есть запись body — исправить перед следующим развёртыванием).

## Подотчёт / personalReimbursementsV1

`accountable[displayName]` сохраняет прежние opening/clientReceipts/incomingTransfers/expenses/outgoingTransfers/balance, добавляет `Accountable_ID`, revision, receivedFromCompany, personalInvested, personalReimbursed. Имя остаётся только отображением; текущее API не содержит Accountable_ID.

`reimbursePersonalFunds`: Accountable_ID, amount>0, expectedRevision, reason. Операция уменьшает долг компании перед лицом и деньги источника Funding_Account_ID; не создаёт повторный EXPENSE. Проверить amount <= max(0,-balance), отсутствие повторного события и достаточность выбранного источника. Нужен явный серверный источник средств или дополнительный выбор в форме; текущая форма не может безопасно его угадать.

Баланс -35000 валиден. Android показывает «Собственных средств вложено — не возмещено: 35000», без приравнивания этой суммы к суммарным вложениям за всё время. Исторические вложения/возмещения выводятся только из API.

## История назначений / assignmentPeriodsV1

Расширить существующие назначения, не вводить второго справочника работников. Ответ assignments содержит Assignment_ID, Installer_ID, Object_ID, startDate/endDate, actualDays, paymentType, accrued/paid/remaining, revision, Obligation_ID, unallocatedFixedAmount.

`moveInstaller`: Installer_ID, исходный Assignment_ID, новый Object_ID, transitionDate, необязательная returnDate, expectedRevision, paymentType DAILY/FIXED, dailyRate либо accrueNow/deferAmount/closeObligation, reason.

Исходный период закрывается накануне перехода с audit старых/новых значений; его ID, фактические дни, выплаты и начисления сохраняются. Создаются новые Assignment_ID для нового объекта и возвращения на прежний. Планируемое возвращение не начисляет зарплату до подтверждения фактических дней. Проверять пересечения бригад и повторные WorkDay_ID.

DAILY = подтверждённые фактические дни × ставка; календарные дни не считать автоматически фактическими. FIXED = распределение единого Obligation_ID: уже начисленное + отложенный остаток не превышает первоначальную сумму; возврат не создаёт новую копию обязательства. Закрытие на меньшую сумму требует отдельного согласованного изменения обязательства с причиной.

## Наёмники / hiredWorkersV1

`employees[].workerKind=HIRED`, существующий Installer_ID, без обязательного API-аккаунта. Добавить phone и облегчённую карточку. `createHiredWorkerDay` (name, optional phone, Object_ID, workDate, actualDays, dailyRate, workType, reason); сервер создаёт Installer_ID, Assignment_ID, уникальные WorkDay_ID и Accrual_Event_ID. `addHiredWorkerDay` использует тот же Installer_ID. Несмежные даты добавляются отдельными командами. Новая выплата = 0 только после подтверждённого создания, не как fallback отсутствующих данных.

Начисление создаёт один связанный Expense_ID по объекту в категории «Заработная плата / наёмные работники». Выплата погашает задолженность, не создавая второй расход. Действующий KPI считает только оплаченные расходы: ДО включения capability необходимо согласованно расширить cash/accrual presentation, исключить повторное признание payroll payment и показать основание KPI. Не смешивать неявно кассовую прибыль периода и начисленные затраты объекта.

`promoteHiredWorker`: тот же Installer_ID, изменение workerKind, revision и audit. Все назначения, объекты, выплаты и история сохраняются. Создание аккаунта отдельно.

## ТЗ, корректировки, Bitrix24

- Ввести saveTechTaskV2 с revision и patch-by-ID для дней/этапов/назначений/платежей. Старый replaceChildren_ небезопасен для редактирования оплаченных этапов. Не перезаписывать формулы и не менять PaymentPlan_ID.
- Корректировка финансовой операции — отдельная команда с Income_ID/Expense_ID, expectedRevision, обязательной причиной, before/after и Audit_Event_ID. До реализации редактирование в Android недоступно.
- Bitrix24: credentials только в ScriptProperties/секретном хранилище backend. Не создавать фиктивные credentials, лиды и статусы подключения. Связи CRM сохраняют постоянные внутренние ID и provider/externalID. Нужны сопоставления менеджеров, стадий, источников и задания фоновой синхронизации.

## Порядок внедрения

1. Проверить реальные названия ID-заголовков существующих листов. `stage1MigrationCheck` при несовпадении останавливается, а не переименовывает колонки.
2. `stage1MigrationBackup`, убедиться в доступности копии. `stage1MigrationApply` добавляет только отсутствующие листы/колонки; повторный запуск без изменений — no-op. После частичного сбоя повторить backup/check.
3. Реализовать обработчики, тестировать на копии Sheets двойные запросы, сбой посередине записи, роли, возврат работника и выплаты.
4. Проверить reconciliation и audit. Только затем включать capabilities и обновлять существующее deployment новой версией с прежним `/exec`.

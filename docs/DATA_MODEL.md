# Модель данных — обязательные сущности и ID

## Objects
`Object_ID` — постоянный технический ключ. Адрес — только отображаемое поле.

Поля: Object_ID, Client_ID, ClientName, Phone, Address, Geo, WorkType, ContractAmount, Status, Manager_ID, Engineer_ID, PlannedStartFrom, PlannedStartTo, PlannedDurationDays, ActualStart, ActualFinish, Progress, CreatedAt, UpdatedAt.

## TechnicalTasks
Task_ID, Object_ID, Status, Engineer_ID, Technology, Area, Materials, Notes, CreatedAt, UpdatedAt.

## TaskDays
TaskDay_ID, Task_ID, DayNo, PlannedDate, Work, Unit, PlannedQty, ActualQty, Status.

## ObjectInstallers
ObjectInstaller_ID, Object_ID, Installer_ID, AgreedPay, AssignedFrom, AssignedTo.

## PaymentStages
PaymentStage_ID, Object_ID, StageNo, PlannedDate, PlannedAmount, ActualAmount, Status.

## MoneyOperations
Money_ID, Type(INCOME/EXPENSE/TRANSFER), Date, Amount, Object_ID?, Installer_ID?, AccountableFrom_ID?, AccountableTo_ID?, Category_ID?, Subcategory_ID?, PaymentStage_ID?, Comment, IdempotencyKey.

## Surveys
Survey_ID, Date, ClientName, Phone, PhoneNormalized, Address, WorkType, Manager_ID, Engineer_ID, Comment, ConvertedToObject, Object_ID.

## Leads
Lead_ID, Date, Client, Phone, Source_ID, Manager_ID, Status, Comment.

## Installers
Installer_ID, FullName, Phone, BirthDate, FamilyStatus, Active, CurrentStatus.

## InventoryAssignments
Assignment_ID, Item_ID, Installer_ID, IssueDate, ReturnDate, Status.

## PPE
PPE_ID, Installer_ID, Type, Size, Qty, PurchaseDate, IssueDate, ReplacePlanDate, ReturnDate, WriteOffDate.

## AuditLog
Audit_ID, EntityType, Entity_ID, Action, User_ID, Timestamp, BeforeJson, AfterJson, Comment.

# Backend changes for frontend developers

This report summarizes the recent backend/API changes that affect frontend integration.

## Resource catalog and inventory

Resources are now department-independent shared catalog/inventory records.

- `GET /api/v1/resources` returns the shared catalog to authorized callers. The `departmentId` query filter has been removed; stop sending it and do not expect department-specific resource lists.
- `ResourceResponse` no longer includes a `department` field.
- Resource create/update request bodies no longer accept `departmentId`.
- Internal resources can be created without a department. Internal resources still require a quantity and cannot have a preferred vendor. External resources still cannot have a quantity.
- Department ownership remains on requests, teams, projects, users, and budgets. Do not infer request ownership from a resource.

Flyway migration V52 drops `resources.department_id` and its dependent foreign key/indexes. V51 updates the type constraint first. The migrations run when the backend starts with Flyway enabled.

## Resource movement history access

`GET /api/v1/resources/{id}/movement-history` now filters history by the caller’s request scope:

- Team leads see entries associated with requests for their teams.
- Department managers see entries associated with requests in their department.
- Managing directors see all history, including resource-level opening/correction entries that are not linked to a request.

For team leads and department managers, movements without a visible request are omitted.

## Department-scoped reports and dashboards

Department IDs still scope requests and budgets. Stock reservations, issued quantities, and waitlist queries that are department-specific now use the owning request’s department rather than a resource department.

Physical inventory is shared. Inventory catalog, on-hand, and availability figures should therefore be treated as shared stock, not stock owned by the selected department. Waitlist results remain scoped to the selected department’s requests.

## DTO validation updates

Some optional update fields now reject invalid values when supplied. `null` or omission still means “leave unchanged.”

- Project update names must contain non-whitespace text and be 2–100 characters. Project codes must be 2–20 uppercase letters or digits. A supplied `departmentId` must be positive.
- Team update names must contain non-whitespace text and be 2–100 characters.
- Department update names must contain non-whitespace text and be 2–100 characters.
- User update and own-profile `firstName` and `lastName` reject whitespace-only values.

The team creation JSON property names and their meanings have not changed; the DTO property order was rearranged in the schema display only.

## User and password flows

- Swagger’s user-creation description was updated to state that a department is required for `MEMBER` and `TEAM_LEAD` roles. `ADMIN` may have no department; `DEPARTMENT_MANAGER` may have no department at creation; `MANAGING_DIRECTOR` has no department. `MEMBER` and `TEAM_LEAD` also need at least one team.
- Password setup/reset emails now include a styled HTML message and a plain-text fallback. No frontend API change is required.
- Password updates are now transactional; request and response fields are unchanged.

## Budgets

- `GET /api/v1/budgets/{id}` still takes a **budget ID** as `{id}`. The backend fix restores that parameter name and reads the department through its `id` property; the URL contract is unchanged.
- `periodType` accepts exactly `MONTHLY`, `QUARTERLY`, or `ANNUAL`. Send dates as `YYYY-MM-DD`, with `periodStart` on or before `periodEnd`.
- The create-budget Swagger description now lists the accepted period types.

## Request response limitation

`ResourceRequestResponseDto` remains a compact response. Its line items do not currently echo every submitted field: notably `resourceId`, free-text `description`, `unitPrice`, `category`, and `batchNumber` are absent. Frontend flows that need those values should retain them from the submission or load them from another endpoint; do not assume the create/get request response contains them.

## Deployment and verification notes

- The backend Docker image tag was advanced from `v8` to `v9`;

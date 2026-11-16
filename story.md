# The system’s story: what happens through the API

This walkthrough follows the API from login through administration and inventory operations. It describes what each API area does, how conditions affect the result, and how requests, stock, budgets, and procurement connect.

## 1. A user signs in

A user sends their username and password to `POST /api/v1/auth/login`. If the credentials are valid, the API returns access and refresh tokens. Invalid credentials are rejected, and an account can be temporarily locked after too many failed attempts.

The client uses the access token for authenticated calls. When it needs a new token, `POST /api/v1/auth/refresh` rotates the refresh token and returns a new pair. `POST /api/v1/auth/logout` revokes the supplied refresh token and current access token.

`GET /api/v1/auth/me` returns the signed-in user’s profile. `PATCH /api/v1/auth/me` changes the caller’s own profile fields, such as their name and contact information.

A user can change a password through `POST /api/v1/auth/password`. They provide either their current password or a single-use setup/reset code, together with the new password. `POST /api/v1/auth/forgot-password` accepts a reset request and returns the same response whether or not an active account matches the email address, so the endpoint does not reveal which accounts exist.

New-user setup and reset codes are delivered by email asynchronously. An accepted API response means the reset request was accepted; it does not by itself prove that the email was delivered. The mail error previously observed indicates that message construction or delivery may fail and should be checked in the application logs.

## 2. The organization sets up its people and structure

Before a team can submit useful requests, the system needs departments, managers, teams, members, and projects.

### Users

`/api/v1/users` is for administrators and Managing Directors to create, list, and update accounts. Creating an account sends a one-time setup code instead of creating a password on the user’s behalf. An administrator can resend the code if setup is incomplete and the account is still active.

When an account is updated, supplied profile fields, role, or active state are changed. Invalid role or status changes are rejected. Users can be searched and listed with pagination.

### Departments

`/api/v1/departments` supports creating, listing, viewing, and updating departments. A Managing Director can assign an active Department Manager to a department, change department details, deactivate it, or reactivate it. Deactivation is rejected when the department is still in use.

### Teams and team membership

`/api/v1/teams` supports creating, listing, viewing, and updating teams. Team member operations let authorized callers view membership and its history. Department Managers can add active users from their own department to teams in that department, transfer a member between teams in their department, or remove a member. Attempts to add an inactive user, a user from another department, or an existing member are rejected.

### Projects

`/api/v1/projects` supports creating, listing, viewing, and updating projects. Department Managers manage projects within their authority; Team Leads, Department Managers, and Managing Directors can view them. Projects can be filtered by department, status, dates, and search terms. Deactivating a project is rejected if it has active resource requests.

These organization checks matter later: a Team Lead’s team determines which department and manager a request belongs to, and whether the caller can submit or view it.

## 3. The catalog and vendors are prepared

### Resources

`/api/v1/resources` lets Team Leads, Department Managers, and Managing Directors browse the shared resource catalog. Search, type, category, inventory status, active state, and availability can be used as filters.

A Managing Director creates or updates catalog resources. An internal resource represents stock held by the organization and needs an opening quantity when created. An external resource represents something that must be purchased; it does not carry an internal quantity. Resource type cannot be changed later, and physical quantity is changed through stock operations rather than a normal resource edit.

A resource can be viewed with `GET /api/v1/resources/{id}`. Its movement history is available at `GET /api/v1/resources/{id}/movement-history`, subject to the caller’s visibility: Team Leads see their teams’ requests, Department Managers see their department’s requests, and Managing Directors can see all.

A Managing Director can correct internal stock using `POST /api/v1/resources/{id}/corrections`. The API records the quantity change and reason. A correction is rejected if the resource or current stock state does not allow it.

### Vendors

`/api/v1/vendors` lets Team Leads and Department Managers find vendors while preparing external requests. Managing Directors maintain the shared vendor list by creating and updating vendors. Duplicate vendor names are rejected. Vendor status can be changed, and inactive vendors are not suitable for new selection.

## 4. A Team Lead creates and submits a request

The Team Lead calls `POST /api/v1/requests` with a team, project, justification, required date, and one or more line items. The caller must have the Team Lead role and belong to the selected team. The API derives the requester and department context from the authenticated user and selected team; the caller cannot choose another department by supplying an arbitrary department ID.

Each line describes a requested quantity and its fulfillment preference:

- An **internal** line refers to a catalog resource that may be available in stock.
- An **external** line describes something to procure and may reference a vendor, unit price, and other purchasing details.

Invalid references, inaccessible teams, invalid quantities, or missing required data cause the request to be rejected. A successful submission receives a request ID and starts the approval workflow.

## 5. Users retrieve and track requests

`GET /api/v1/requests` returns a paginated list of requests visible to the caller. The caller can filter by status, team, project, assigned manager, reassignment need, dates, or request-number search. Results are restricted by role and visibility.

`GET /api/v1/requests/{id}` returns a visible request and its line items. `GET /api/v1/requests/{id}/history` returns the request’s chronological history to its owner, assigned manager, or a Managing Director.

A Team Lead can use `PUT /api/v1/requests/{id}` to clarify or edit a request when its status permits, or to cancel it when cancellation is allowed. A Department Manager can use the same endpoint to make decisions, adjust quantities where allowed, ask the Team Lead for clarification, or set priority for a request in their department.

The API rejects a change if the caller lacks the right role or department scope, the request is not visible to them, its current status does not allow the transition, or the decision data is invalid.

## 6. The Department Manager decides what to approve

Approval applies to the requested resources and quantities in a request. It does not approve a resource catalog item.

A manager can approve all lines at once or approve selected line items and quantities. With a staged approval, a line omitted from the approval decision is treated as zero for that decision. This allows a manager to approve four lines from a request containing twenty; the request can then remain `PARTIALLY_APPROVED` while other lines are still awaiting a decision.

Likewise, if a line requests ten units and the manager approves four, the request remains partial. A later manager decision can approve more of that line. Once all requested quantities have been approved, the request can move to `APPROVED`.

A manager can also reject the request or ask the Team Lead for clarification. The caller must be the Department Manager assigned to the request and must have access to its department. Invalid state transitions or missing required decision details are rejected.

Approval and fulfillment are separate. `APPROVED` means the requested quantities were approved; it does not mean all those resources have already been reserved, purchased, delivered, or issued.

## 7. The system processes approved lines

### For internal stock

For each approved internal quantity, the system checks stock and reservations.

- If enough stock is available, it reserves the approved quantity for the request.
- If only some can be reserved, it reserves what is available and tracks the shortage through the waitlist/backorder workflow where applicable.
- If none is available, the approved demand can wait for stock.
- If the approved quantity is zero, no stock is reserved and no waitlist demand is created for that line.

A reservation holds stock for the request. It is not the same as physically issuing the resource to the Team Lead.

When more stock becomes available, waitlist processing follows FIFO order. If the oldest entry can only be partly supplied, it receives that partial reservation and remains ahead of later entries. Later entries are considered only after earlier demand has been addressed.

If an approved internal quantity changes later, the system adjusts reservations and waitlist demand. A reduction releases excess reservation and lowers outstanding waitlist quantity. Cancellation releases reservations and removes the request’s waitlist demand.

### For external procurement

For an approved external quantity, the system checks budget and records procurement authorization. Later approvals add only the newly approved quantity and corresponding budget amount; they do not charge the entire line again.

The Department Manager can review the request’s budget using `GET /api/v1/requests/{id}/budget`. This review is scoped to the manager’s department and can fail if a budget has not been configured.

Procurement records are listed through `GET /api/v1/resources/procurements`. A Department Manager can update a procurement through `PUT /api/v1/resources/procurements/{id}` as it moves through the allowed purchasing and receiving workflow. The manager must be assigned to the associated request.

Received quantities cannot exceed the procurement authorization or the remaining approved amount. A later reduction to an external line is rejected once the order has reached the vendor, because the organization may already be committed to that purchase. Cancellation can stop procurement that has not been received; if external goods have already been received, request cancellation is rejected.

## 8. The Department Manager issues internal stock

After internal stock is reserved, an assigned Department Manager records the physical handover using `POST /api/v1/requests/{id}/issues`. The issue must refer to a valid internal line, and its quantity cannot exceed the approved and available reserved balance.

If the request is not issuable, the stock is not reserved or available, or the quantity exceeds what can be issued, the API rejects the operation.

`GET /api/v1/requests/{id}/issues` lets authorized callers review issue records, due dates, returns, and extension decisions. The API derives issue fulfillment status from reservations, issued quantities, and verified returns. The caller does not set that status directly.

## 9. The Team Lead returns items or asks for more time

The combined issue workflow endpoint is `PUT /api/v1/requests/{id}/issue`. It accepts one action per call:

- **`SUBMIT_RETURN`**: The Team Lead belonging to the issue’s team submits a return quantity. It creates a pending return record. Inventory does not change yet.
- **`VERIFY_RETURN`**: The Department Manager assigned to the request verifies a pending return, records the condition, and can set the accepted quantity. The verified accepted quantity updates inventory according to the condition.
- **`REQUEST_EXTENSION`**: The Team Lead belonging to the issue’s team requests a later expected return date and gives a reason. The extension becomes pending.
- **`DECIDE_EXTENSION`**: The assigned Department Manager approves or rejects the pending extension. A rejection requires a comment. Approval updates the issue’s due date.

The server controls these transitions; callers must not submit an arbitrary status value. The endpoint rejects invalid return quantities, actions for the wrong role or team, and actions that do not match the current issue, return, or extension state.

A verified return can make internal stock available again, which may then be allocated to waiting requests.

## 10. Managers and directors review budgets and performance

### Budgets

`/api/v1/budgets` supports budget creation, listing, retrieval, updates, deactivation, and remaining-balance lookup. A Managing Director creates budgets for a department and period, such as monthly, quarterly, or annual. Overlapping active budgets are rejected.

Department Managers can view budgets for their own department; Managing Directors can view across departments or filter by department. Updating a budget amount or active state is restricted to Managing Directors. Deactivation is rejected while there are active commitments. `GET /api/v1/budgets/{id}/remaining` returns the applicable remaining amount for a date.

### Dashboard and reports

Department Managers and Managing Directors can retrieve dashboard analytics at `GET /api/v1/dashboard/analytics`. A Department Manager is limited to their own department; a Managing Director can optionally filter by department.

`/api/v1/reports` provides reports for resource 2utilisation, availability, stock balances, waitlists, budget spending, and resource reconciliations. A Managing Director can record a stock reconciliation and retrieve reconciliation history for a resource. Reports are scoped according to the caller’s role and data access.

## 11. Notifications and audit records keep the trail

`GET /api/v1/notifications` lists the authenticated user’s notifications, with search and pagination. `PATCH /api/v1/notifications` updates read state for selected notification IDs or for all of that user’s notifications; the user cannot update another user’s notifications. `GET /api/v1/notifications/stream` opens a server-sent-events stream that sends an initial unread count and later updates when that count changes.

Audit history is separate from request history. Admin-only `/api/v1/audit` endpoints retrieve audit entries by actor, request or line item, action, or general filters. Date ranges are validated; some endpoints default to the last 30 days, and supported ranges are limited to one year.

The API also provides `GET /api/v1/health` as a health check.

## How to read the statuses

Three different kinds of progress are involved:

1. **Request status** says whether the request is pending, queried, partially approved, fully approved, rejected, cancelled, or otherwise in its request workflow.
2. **Procurement status** says where external purchasing and receiving stand.
3. **Issue fulfillment status** says whether internal items have not started, are reserved, partly or fully issued, or have been partly or fully returned.

Those states are related, but they are not interchangeable. For example, a request can be `APPROVED` while an internal line is still waiting for stock or an external line is still being purchased.

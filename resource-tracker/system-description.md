# Resource Tracker: System Description

## 1. Purpose

Resource Tracker is a web API for coordinating how an organisation requests, approves, budgets, acquires, stores, issues, and accounts for resources. It brings those activities into one shared record so teams can see what they asked for, managers can make and explain decisions, and leadership can understand availability and spending.

The system addresses several common operational problems:

- Requests may arrive through separate messages or spreadsheets, making it hard to track ownership, status, and decision history.
- A request can pass through several people and activities: team submission, departmental review, budget checks, stock reservation or procurement, fulfilment, and sometimes return.
- Stock figures can become unreliable when reservations, issues, returns, damaged goods, and physical counts are not recorded together.
- Spending and stock information may be difficult to compare across departments, projects, teams, and time periods.
- Unrestricted access to user, inventory, or approval operations makes mistakes and unauthorised changes more likely.

The implementation provides a central API, a relational data model, role-aware access, workflow rules, and reporting. It supports internal stock and externally procured resources because those require different fulfilment paths.

## 2. What the system does

At a high level, the system connects organisational structure to resource use:

1. Administrators and leadership maintain users, departments, projects, teams, resources, and suppliers.
2. A Team Lead submits a request on behalf of a team, describing one or more line items, quantities, timing, and justification.
3. The assigned Department Manager reviews the request, may ask for clarification, approve all or part of it, reject it, or place it on a waitlist. Budget information and stock availability support this review.
4. Approved internal stock can be reserved and issued. External needs can proceed through procurement tracking.
5. Issues can be returned and verified; return dates can be extended through a request and decision workflow.
6. Request history, stock movements, audit records, notifications, reports, and dashboards help users follow what happened and what requires attention.

This is a resource management and workflow system. It is not just an inventory list: it models the approvals, money, ownership, and evidence around resource use.

## 3. Roles and responsibility

The application defines five roles. Specific endpoint access is enforced by role annotations and additional ownership checks in services. A role grant does not automatically give a user access to every record; department, team, assignment, and request ownership can further narrow access.

| Role | Main responsibility in the system |
| --- | --- |
| **ADMIN** | Administrative oversight, user administration, audit review, and selected cross-organisation reporting. Admin is not automatically allowed to perform every operational action; access is explicitly granted per feature. |
| **MEMBER** | View permitted organisational information, such as department and team information. This role does not submit resource requests through the current request endpoint. |
| **TEAM_LEAD** | Submit and follow requests for a team, clarify queried requests, cancel eligible requests, and participate in stock issue and return workflows for their team. |
| **DEPARTMENT_MANAGER** | Review requests assigned to them, make approval and fulfilment decisions, manage department teams and suppliers, and update procurements. Access to departmental records is generally scoped to their department or assigned requests. |
| **MANAGING_DIRECTOR** | Organisation-wide leadership and control, including resource catalog and stock corrections, budget creation and updates, reconciliation, and broader reporting. |

### Why use role and ownership checks?

The roles reflect different points of responsibility in the real process. A Team Lead knows what a team needs; a Department Manager is responsible for reviewing and authorising its use; leadership controls shared inventory and budgets; and Admin handles platform oversight. Keeping those responsibilities separate reduces accidental self-approval and limits the impact of a compromised account. Scoping managers to their own department or assigned requests also limits unnecessary exposure of other departments' data.

The API includes explicit exceptions. For example, some reporting is open to Admin, Managing Director, and Department Manager, while audit records are restricted to Admin. Team membership history excludes Admin according to the current controller rules. The API reference is the source for each endpoint's exact roles.

## 4. Modules and what users can do

### 4.1 Authentication and accounts

**Purpose:** Identify the caller and provide a secure way to establish and recover access.

- Users log in with a username and password and receive access and refresh tokens.
- A valid refresh token can be rotated to obtain new tokens; logout revokes tokens.
- An authenticated user can retrieve their own profile.
- User setup and password recovery use one-time codes sent through the mail service. Passwords are checked against a policy, and failed login attempts are tracked to support temporary account locking.
- Admins and Managing Directors can create and update accounts, assign roles and organisational membership, and resend a setup code.

**Design rationale:** A one-time setup flow lets an administrator create an account without choosing or handling the user's permanent password. A generic password-reset acknowledgement helps avoid revealing whether an email address belongs to an account. Token refresh and revocation provide a defined session lifecycle; passwords and token values are not intended to be returned as profile data.

### 4.2 Departments, projects, teams, and memberships

**Purpose:** Represent where work happens and which group owns a request.

- Managing Directors create and update departments; authorised users can search and view them.
- Department Managers create and update projects and teams within their department.
- Teams can be linked to projects, have a manager, and include existing active users.
- Department Managers can add, transfer, and remove team members within their department.
- Users with the permitted operational roles can view team membership and membership history.

**Design rationale:** Requests need a clear organisational owner for routing, reporting, and access checks. A separate membership history preserves evidence of team changes over time instead of treating membership as a current-only fact. Active/inactive fields allow records to be deactivated while retaining historical references.

### 4.3 Resource catalogue and inventory

**Purpose:** Describe resources consistently and track physical stock where applicable.

- Users with permitted roles can search and filter resources by name, type, category, status, department, and availability.
- Managing Directors create and update catalogue records and perform stock corrections.
- Resources are classified as `INTERNAL` or `EXTERNAL`, and by categories such as consumables, equipment, software, or other.
- Internal resources have physical quantities, availability status, location, unit of measure, unavailable quantities, and reorder levels.
- External resources do not represent on-hand stock and may reference a preferred vendor.
- Stock movement history records openings, issues, usable returns, unavailable returns, and corrections.
- Reconciliation records compare a physical count with recorded stock and keep a reason for adjustments.

**Design rationale:** The internal/external distinction prevents the system from presenting supplier-purchased items as stock already on the shelf. Separate movement, reservation, and issue records make stock changes explainable and help avoid double allocation. Resource units are checked against the category so quantities such as software seats and consumable boxes are not mixed arbitrarily. Stock corrections and reconciliation are restricted and reasoned because they change the organisation's inventory record.

### 4.4 Resource requests and approval workflow

**Purpose:** Capture demand and guide it through a controlled decision process.

- A Team Lead creates a request for a team, project, and department with one or more line items.
- A request can be queried for clarification, clarified by the Team Lead, approved, partially approved, rejected, waitlisted, completed, or cancelled when the transition is allowed.
- Department Managers review requests assigned to them, including line-level quantities and fulfillment needs.
- Requests can be searched and filtered by status, team, project, manager, dates, and reassignment needs.
- A generated request number provides a readable reference, while history records decisions and other key events.

**Design rationale:** The workflow is represented as explicit states and permitted transitions. This makes it harder to skip review steps or apply a decision by the wrong role. The manager can approve quantities line by line, so a request does not have to be treated as all-or-nothing. Query and clarification states keep requests actionable when more information is needed. The separate history is useful for explaining a decision later and distinguishing the current status from the path taken to reach it.

### 4.5 Budgets and budget review

**Purpose:** Give request reviewers a view of planned departmental spending.

- Managing Directors create and update budgets for a department and a monthly, quarterly, or annual period.
- Managing Directors and Department Managers can view budgets and remaining amounts; managers are limited to their own department.
- A budget exposes its total amount, committed amount, and remaining amount.
- Department Managers can review budget information for a request.
- Reports summarise budget spending and commitments.

**Design rationale:** Period-based budgets connect a request's financial impact to a defined department and time window. Showing committed and remaining amounts makes planned commitments visible before they become final expenditure. A unique department-and-period constraint prevents duplicate active budget definitions for the same period. Department scoping keeps budget visibility aligned with management responsibility.

### 4.6 Stock reservation, issuing, returns, and waitlists

**Purpose:** Track the physical fulfilment lifecycle for internal resources.

- The system can reserve available stock for approved request lines and manage a waitlist when stock is not currently available.
- Department Managers issue reserved stock and can see issue, due-date, and return information for visible requests.
- Team Leads and assigned managers participate in eligible return workflows.
- A return is reported and then verified with a condition such as usable, damaged, lost, or quarantined.
- A Team Lead can request a later return date; the responsible manager approves or rejects that extension.
- Reminder notifications can be issued around expected return dates.

**Design rationale:** A reservation represents stock committed to a request, while an issue represents stock actually handed out. Keeping those events separate avoids treating an approval as a completed physical handover. Verification makes returned usable stock available again while damaged or quarantined items remain unavailable. The waitlist provides an orderly path when demand exceeds current stock, and return-date decisions give reusable equipment a clear accountability loop.

### 4.7 Vendors and procurement

**Purpose:** Record supplier information and track purchases needed to fulfil external requests.

- Department Managers create and update vendors; Team Leads and Department Managers can view permitted vendor data.
- A procurement record is associated with a request line and vendor and can track estimated cost, expected delivery, order status, quantities received, and acceptance.
- Department Managers can view and update procurements for requests assigned to them.

**Design rationale:** Supplier records are distinct from resources: the vendor is who supplies something, while the resource is what the organisation needs. Procurement is also distinct from stock issuance because an order may still be authorised, ordered, partially received, or awaiting acceptance. Tracking these steps lets the request remain traceable through delivery rather than being marked fulfilled when an order is merely placed.

### 4.8 Notifications

**Purpose:** Bring relevant workflow events to the attention of the person who needs to act.

- An authenticated user can list their notifications, filter them, update their status, and receive unread-count updates over a server-sent event stream.
- Workflow events can notify requesters and assigned managers about decisions, returns, extensions, and other relevant changes.

**Design rationale:** Notifications are user-specific so that one person's status changes do not alter another person's inbox. The stream supports timely UI updates without requiring frequent polling; the underlying notification list remains available for retrieval and status updates.

### 4.9 Audit, reports, and dashboard

**Purpose:** Support operational oversight, traceability, and management decisions.

- Admins can query audit entries by actor, item, action, or general filters.
- Audit records can retain before/after information, actor, action, time, and correlation information for audited changes.
- Admins, Managing Directors, and Department Managers can access applicable utilisation, availability, stock-balance, waitlist, and budget-spending reports. Manager reports are scoped to their department.
- Managing Directors can review reconciliation history.
- The dashboard provides aggregated analytics with optional department filtering.

**Design rationale:** Audit and request history serve related but different purposes. Request history explains the business lifecycle of a request; audit entries provide a broader record of changes to auditable items. Correlation IDs help connect activity from one API operation across logs and audit data. Reports aggregate operational records into views useful for decisions, while the underlying records remain available for detailed follow-up.

### 4.10 Shared API and platform support

The backend uses Kotlin, Spring Boot, Spring Security, Spring Data JPA, and MySQL. Flyway manages schema migrations. The code is organised by domain package, generally separating controllers, services, repositories, entities, and request/response DTOs. SpringDoc exposes API documentation. Shared pagination/query helpers and a common error response keep API behavior more consistent across modules.

**Design rationale:** Domain-focused packages make related rules easier to locate and change. The controller/service/repository separation keeps HTTP concerns, business rules, and persistence access distinct. DTOs provide an API boundary so database entities do not need to define the public request and response shape. Versioning under `/api/v1` gives clients a stable namespace for the current contract. Flyway migrations make database changes explicit and repeatable.

## 5. Functional requirements

The following summarizes the functional behavior implemented by the current system.

### Identity and access

- **FR-01:** The system shall authenticate users and issue access and refresh tokens.
- **FR-02:** The system shall allow token refresh and logout, and provide the authenticated caller's profile.
- **FR-03:** The system shall support user creation, updates, activation management, setup-code delivery, and password recovery by authorised roles.
- **FR-04:** The system shall enforce role restrictions and, where applicable, department, team, or request ownership restrictions.

### Organisation and catalogue

- **FR-05:** The system shall manage departments and support searching, filtering, and viewing them according to role.
- **FR-06:** The system shall manage projects, teams, managers, and team membership, including membership history.
- **FR-07:** The system shall maintain a searchable resource catalogue with category, type, unit, and active-state information.
- **FR-08:** The system shall distinguish internally held stock from externally sourced resources.
- **FR-09:** The system shall maintain vendor information and associate vendors with applicable resources and procurement records.

### Requests, decisions, and fulfilment

- **FR-10:** A Team Lead shall be able to submit a request with multiple line items for a team/project context.
- **FR-11:** The system shall assign a request reference and record its current status and history.
- **FR-12:** An assigned Department Manager shall be able to query, approve, partially approve, reject, or waitlist a request as allowed by its current state.
- **FR-13:** A Team Lead shall be able to clarify queried requests and cancel requests in permitted states.
- **FR-14:** The system shall validate state transitions so only permitted roles can perform them.
- **FR-15:** The system shall reserve available internal stock, issue approved stock, and retain stock movement history.
- **FR-16:** The system shall support waitlisting when requested internal stock is unavailable.
- **FR-17:** The system shall record stock returns, verify their condition, and update availability accordingly.
- **FR-18:** The system shall support return-date extension requests, decisions, and reminders.
- **FR-19:** The system shall track procurement progress for externally sourced items through receipt and acceptance.

### Budgets, oversight, and communication

- **FR-20:** The system shall manage departmental budgets over defined periods and calculate committed and remaining amounts.
- **FR-21:** The system shall provide budget context for request review and budget-spending reports.
- **FR-22:** The system shall record stock corrections and physical reconciliations with a reason and actor.
- **FR-23:** The system shall provide role-scoped reports and dashboard analytics for resource use, availability, waitlists, and spending.
- **FR-24:** The system shall provide user-specific notifications and a stream for timely unread-count updates.
- **FR-25:** The system shall provide audit queries for authorised administrators and preserve request and membership history.

## 6. Important design decisions to explain in a presentation

| Decision | How it appears in the system | Reason it matters |
| --- | --- | --- |
| Role-based authority with record scoping | Role restrictions plus department/team/request checks | Connects permissions to real responsibilities and reduces cross-department exposure. |
| Explicit request states and transitions | Pending, queried, approved, partially approved, waitlisted, rejected, completed, cancelled | Prevents invalid workflow jumps and makes request progress understandable. |
| Line-item decisions | Individual requested and approved quantities | Allows partial fulfilment without losing the original request context. |
| Separate internal and external resource paths | Internal stock reservations/issues versus external procurement | Avoids conflating shelf availability with a supplier order. |
| Reservation distinct from issue | Reserved stock is committed; issue is recorded handover | Gives accurate availability and accountability at each stage. |
| Return verification | Returned condition is checked before stock becomes available | Keeps damaged, lost, or quarantined items out of usable stock totals. |
| Append-oriented history and audit evidence | Request history, membership history, stock movements, audit entries | Supports traceability and post-event review without relying only on current state. |
| DTO-based API boundary | Separate request and response models | Lets the API validate and evolve its public shape without exposing persistence details. |
| Deactivation and status fields | Active flags/status values on many records | Preserves references needed by old requests and reports while preventing new use of inactive records. |
| Department and period budgets | Monthly, quarterly, annual budgets with commitments | Makes the source and timing of spending visible during review. |
| Pagination and filters | List endpoints accept page, size, search, and domain filters | Keeps large operational datasets usable and avoids requiring clients to download everything. |
| Common errors and correlation | Shared error response and correlation identifiers | Gives clients predictable failure details and helps support teams trace a request. |

These reasons explain the design as it is reflected in the code. The repository does not include a separate architecture decision record for every item, so treat the rationale as an implementation-based explanation rather than a verbatim historical record of the original design discussions.

## 7. Suggested presentation walkthrough

Use one request as the story that connects the modules:

1. **Set the context:** Show the organisation's department, project, team, and the Team Lead responsible for submitting the need.
2. **Submit demand:** Create a request with a few line items and explain the generated reference, status, and request history.
3. **Review and decide:** Sign in as the assigned Department Manager; show the request, budget context, available stock, and a query or partial approval.
4. **Fulfil:** Show a reservation and issue for internal stock, or procurement tracking for an external item. Explain why the two paths differ.
5. **Close the loop:** Record a return and its condition, or show the procurement being received and accepted.
6. **Show accountability:** Open the request history, stock movement history, or audit entry, then finish with the relevant availability or spending report.

This narrative demonstrates both the user-facing workflow and the reason the system has separate modules: each module owns a part of one traceable operational process.

## 8. Scope and presentation notes

- The current API has 69 documented endpoint operations; see [api-documentation.md](api-documentation.md) for paths, roles, request DTOs, and response models.
- This document describes backend behavior visible in the repository. It does not claim that every workflow is exposed in a particular frontend screen.
- A role's exact access can differ by endpoint and record ownership; use the API reference for endpoint-level detail.
- Budget commitments and report figures should be presented as system tracking and review support. Do not describe them as a substitute for an external accounting ledger unless that integration is separately established.
- Explain that the architecture rationale above is inferred from the implemented safeguards and data model where there is no explicit design decision record.

# Resource Tracker – Architecture Diagrams

How this set fits together (each diagram uses the tool that expresses it best):

| View | Diagram | Tool |
|---|---|---|
| System context, containers, components, deployment | C4 model | Structurizr DSL (`workspace.dsl`) |
| 1. Layers and feature packages | Layered architecture | Mermaid flowchart |
| 2. Data model | ERD | Mermaid `erDiagram` |
| 3. Request lifecycle | State machine | Mermaid `stateDiagram-v2` |
| 4. Authentication and security chain | Sequence | Mermaid `sequenceDiagram` |
| 5. Request-to-fulfilment flow | Sequence | Mermaid `sequenceDiagram` |
| 6. CI/CD and release | Flowchart | Mermaid flowchart |

Mermaid renders natively on GitHub, GitLab and most Markdown viewers.

---

## 1. Layers and feature packages

```mermaid
flowchart TB
    client[Web client] -->|HTTPS| nginx[Nginx reverse proxy]
    nginx --> sec

    subgraph app[Spring Boot application - com.resourcetracker]
        direction TB
        sec[Security: CorrelationIdFilter, JWT, RBAC interceptor]
        ctl[Controllers: REST, validation, OpenAPI]
        svc[Services: business rules, transactions, record scope]
        repo[Repositories: JPA, specifications, selected JDBC]
        ent[Entities and DTOs]
        sec --> ctl --> svc --> repo --> ent
        svc -.->|AOP| aud[Audit aspect]
        svc --> mail[Notification and Mail]
    end

    subgraph features[Feature packages]
        direction LR
        f1[auth, user]
        f2[department, team, project]
        f3[resource, vendor, budget]
        f4[request workflow, reservation, waitlist]
        f5[issue/return, procurement]
        f6[notification, mail, audit]
    end

    repo --> db[(MySQL 8 + Flyway)]
    aud --> db
    mail --> smtp[SMTP provider]
```

---

## 2. Data model (core entities)

`audit_entry` has no foreign keys by design: it is an append-only, trigger-protected trail keyed by `item_type` + `item_id`.

```mermaid
erDiagram
    DEPARTMENTS ||--o{ TEAMS : has
    DEPARTMENTS ||--o{ PROJECTS : owns
    DEPARTMENTS ||--o{ BUDGET : funds
    DEPARTMENTS |o--o{ USERS : "optional membership"
    USERS }o--o{ TEAMS : "user_teams"
    TEAMS }o--o{ PROJECTS : "team_projects"
    TEAMS }o--o| USERS : "manager"
    USERS ||--o{ TEAM_MEMBER_HISTORY : "moved by or moved"

    USERS ||--o{ RESOURCE_REQUESTS : "requester"
    USERS |o--o{ RESOURCE_REQUESTS : "assigned manager"
    TEAMS ||--o{ RESOURCE_REQUESTS : submits
    PROJECTS ||--o{ RESOURCE_REQUESTS : "charged to"
    RESOURCE_REQUESTS ||--|{ RESOURCE_REQUEST_LINE_ITEMS : contains
    RESOURCE_REQUESTS ||--o{ REQUEST_HISTORY : "events"
    RESOURCE_REQUESTS ||--o{ WAITLIST_ENTRIES : queues
    RESOURCE_REQUESTS ||--o{ BUDGET_COMMITMENT : commits
    RESOURCE_REQUESTS ||--o{ BUDGET_OVERRIDES : "override asks"
    BUDGET ||--o{ BUDGET_COMMITMENT : tracks

    RESOURCES ||--o{ RESOURCE_REQUEST_LINE_ITEMS : "requested as"
    VENDORS |o--o{ RESOURCES : "preferred vendor"
    VENDORS |o--o{ RESOURCE_REQUEST_LINE_ITEMS : "external supply"
    RESOURCES ||--o{ RESOURCE_ASSETS : "individual units"
    RESOURCE_ASSETS ||--o{ ASSET_MOVEMENTS : history
    RESOURCES ||--o{ STOCK_RESERVATIONS : reserved
    RESOURCES ||--o{ STOCK_ISSUES : issued
    STOCK_ISSUES ||--o{ STOCK_RETURNS : returned
    RESOURCE_REQUEST_LINE_ITEMS ||--o{ WAITLIST_ENTRIES : waits

    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o{ PASSWORD_TOKENS : "setup / reset"
    USERS ||--o{ REFRESH_TOKENS : sessions

    DEPARTMENTS { int id PK
        string name
        string status }
    USERS { int user_id PK
        string username UK
        string email UK
        string role
        int department_id FK
        boolean active
        int failed_attempts
        datetime locked_until }
    TEAMS { int id PK
        string name
        int department_id FK
        int manager_id FK
        boolean is_active }
    PROJECTS { int id PK
        string code
        int department_id FK
        date start_date
        date end_date }
    RESOURCES { int id PK
        string code
        string type "INTERNAL or EXTERNAL"
        string tracking_method
        int quantity
        int reorder_level
        int vendor_id FK }
    RESOURCE_REQUESTS { int request_id PK
        string request_number
        string status
        string priority
        int team_id FK
        int project_id FK
        int requester_id FK
        int assigned_manager_id FK
        decimal total_amount
        bigint version }
    RESOURCE_REQUEST_LINE_ITEMS { int line_item_id PK
        int request_id FK
        int resource_id FK
        int quantity
        int approved_quantity
        int waived_quantity
        string fulfillment_preference }
    BUDGET { bigint id PK
        int department_id FK
        string period_type
        date period_start
        date period_end
        decimal amount
        decimal committed_amount }
    NOTIFICATIONS { int id PK
        int recipient_id FK
        string type
        string status "UNREAD or READ" }
    AUDIT_ENTRY { bigint id PK
        datetime occurred_at
        int actor_id
        string action
        string item_type
        string item_id
        string correlation_id }
```

---

## 3. Request lifecycle (state machine)

Derived from `RequestTransitions`, the single source of truth for who may move a request.

```mermaid
stateDiagram-v2
    [*] --> PENDING: SUBMIT (Team Lead)

    PENDING --> QUERIED: QUERY (assigned manager)
    QUERIED --> PENDING: CLARIFY (Team Lead)
    PENDING --> PENDING: CLARIFY (Team Lead)

    PENDING --> APPROVED: APPROVE
    PENDING --> PARTIALLY_APPROVED: APPROVE (partial quantity)
    PENDING --> WAITLISTED: WAITLIST
    PENDING --> REJECTED: REJECT
    QUERIED --> REJECTED: REJECT

    APPROVED --> COMPLETED: all issued, handed over or waived
    WAITLISTED --> COMPLETED: stock arrives and is issued

    PENDING --> CANCELLED: CANCEL (Team Lead)
    QUERIED --> CANCELLED: CANCEL
    APPROVED --> CANCELLED: CANCEL (before issue)
    PARTIALLY_APPROVED --> CANCELLED: CANCEL (before issue)
    WAITLISTED --> CANCELLED: CANCEL

    REJECTED --> [*]
    CANCELLED --> [*]
    COMPLETED --> [*]
```

---

## 4. Authentication and security chain

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant W as Web client
    participant N as Nginx
    participant F as CorrelationId + JWT filters
    participant R as RBAC interceptor
    participant A as AuthService
    participant D as MySQL

    U->>W: Enter username and password
    W->>N: POST /api/v1/auth/login
    N->>F: Forward (HTTPS terminated)
    F->>A: Attach correlation ID, public route
    A->>D: Load user, check active and lock state
    alt too many failed attempts
        A-->>W: 423/401 locked until timestamp
    else credentials valid
        A->>D: Store refresh token, reset failed attempts
        A-->>W: accessToken (30 min) + refreshToken (7 days)
    end

    W->>N: GET /api/v1/auth/me (Bearer token)
    N->>F: Validate signature and expiry
    F->>R: Principal with role, teams, department
    R->>R: Check role and record scope
    alt outside scope
        R-->>W: 403 Forbidden
    else allowed
        R-->>W: Profile (id, role, teams, teamIds)
    end
```

---

## 5. Request-to-fulfilment flow

```mermaid
sequenceDiagram
    autonumber
    actor TL as Team Lead
    actor DM as Department Manager
    participant API as Resource Tracker API
    participant DB as MySQL
    participant ST as Stock and Waitlist
    participant MS as Mail and Notifications

    TL->>API: POST /requests (line items)
    API->>DB: Validate team scope, save PENDING, audit entry
    API->>API: Route to the single active manager of the department
    alt zero or several managers
        API->>MS: Flag for Admin reassignment, notify Admins
    else one manager
        API->>MS: Notify assigned manager
    end

    DM->>API: Decision (approve, query, reject, waitlist)
    alt query
        API->>DB: Status QUERIED + audit
        MS-->>TL: Notification
        TL->>API: Clarify and resubmit (back to PENDING)
    else approve
        API->>DB: Check and commit department budget
        API->>ST: Reserve stock
        alt stock short
            ST->>DB: Create waitlist entry
        else stock available
            ST->>DB: Reserve quantity
        end
        API->>DB: APPROVED or PARTIALLY_APPROVED + audit
    end

    TL->>API: Issue and later return resources
    API->>ST: Record issue, assets and return extensions
    API->>DB: When all approved quantity is done, status COMPLETED
    MS-->>TL: Return reminders and overdue notices
```

---

## 6. CI/CD and release

```mermaid
flowchart LR
    dev[Developer PR] --> gh[GitHub]
    gh --> ci[GitHub Actions: build and test]
    ci --> jar[Package JAR]
    jar --> tunnel[SSH tunnel to MySQL]
    tunnel --> flyway[Flyway validate and migrate]
    flyway -->|fail| stop[Stop: inspect schema, no auto-reset]
    flyway -->|ok| copy[scp JAR to /opt/resource-tracker]
    copy --> restart[systemd restart]
    restart --> health{/actuator/health OK?}
    health -->|yes| done[Release live]
    health -->|no| rollback[Restore previous JAR and restart]
    rollback --> alert[Report failed deploy]
```

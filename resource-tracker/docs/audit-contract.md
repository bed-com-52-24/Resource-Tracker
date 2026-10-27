# Audit contract

`AuditService.record(AuditRecord)` is the only application write path for shared audit entries. It is synchronous and requires an existing transaction (`Propagation.MANDATORY`); persistence errors propagate to the caller so the business transaction rolls back. Audit rows are immutable in Hibernate and protected against `UPDATE` and `DELETE` by MySQL triggers. Administrative retention must be performed outside the application with a separately controlled database role.

`AuditAction` enum names are persisted API values. Add new actions with stable uppercase names; do not rename or reuse a persisted name. `AuditRecord` identifies the actor (a nullable user ID plus role/service identity), logical item type and string ID, state snapshots, changed fields, optional request metadata, and correlation ID. Anonymous authentication events use a null actor ID and `ANONYMOUS` role. Background work should use `AuditCorrelationContext.withCorrelationId` to share one operation ID across all emitted entries; absent an HTTP correlation ID, the recorder generates a UUID.

HTTP requests accept `X-Correlation-ID` only when it matches `[A-Za-z0-9][A-Za-z0-9._:-]{0,63}`. Otherwise the application generates a UUID. The selected value is placed in MDC, returned in the same response header, and removed after request completion.

Snapshots are recursively redacted before serialization. Keys containing password, token, secret, API key, private key, authorization, or credential (case-insensitive; punctuation-independent) are replaced with `***`. Comments and metadata must not be used to carry credentials.

## Initial module mapping

Each row describes one auditable operation. Create operations have no previous state; updates and status changes store both snapshots. `changed_fields` names only fields whose values changed. A no-op update does not create an audit row.

| Module | Operation | `AuditAction` | Item type | Snapshots and `changed_fields` |
|---|---|---|---|---|
| Auth | Successful login | `AUTH_LOGIN` | `USER` | Previous/new authentication state; `lastAuthenticated` |
| Auth | Failed login for known user, or locked/deactivated account | `AUTH_LOGIN_FAILED` or `AUTH_ACCOUNT_LOCKED` | `USER` | Previous/new failed-attempt and lock state when changed; changed state fields |
| Auth | Failed login for unknown username | `AUTH_LOGIN_FAILED` | `AUTHENTICATION` | New outcome only; `outcome` |
| Auth | Logout | `AUTH_LOGOUT` | `USER` | New unauthenticated state only; `authenticated` |
| User | Create | `USER_CREATED` | `USER` | New allow-listed identity, role, department/team IDs, and active state; those fields |
| User | Update / change role / deactivate / reactivate | `USER_UPDATED` / `USER_ROLE_UPDATED` / `USER_ARCHIVED` / `USER_RESTORED` | `USER` | Previous/new allow-listed identity, role, department/team IDs, and active state; changed fields only |
| Department | Create | `DEPARTMENT_CREATED` | `DEPARTMENT` | New name, description, and status; those fields |
| Department | Update / deactivate / reactivate | `DEPARTMENT_UPDATED` / `DEPARTMENT_ARCHIVED` / `DEPARTMENT_RESTORED` | `DEPARTMENT` | Previous/new name, description, and status; changed fields only |
| Project | Create | `PROJECT_CREATED` | `PROJECT` | New name, code, department, dates, and active state; those fields |
| Project | Update / deactivate / reactivate | `PROJECT_UPDATED` / `PROJECT_ARCHIVED` / `PROJECT_RESTORED` | `PROJECT` | Previous/new name, code, department, dates, and active state; changed fields only |
| Team | Create | `TEAM_CREATED` | `TEAM` | New name, department, manager, project IDs, and active state; those fields |
| Team | Update / deactivate / reactivate | `TEAM_UPDATED` / `TEAM_ARCHIVED` / `TEAM_RESTORED` | `TEAM` | Previous/new name, department, manager, project IDs, and active state; changed fields only |
| Vendor | Create | `VENDOR_CREATED` | `VENDOR` | New name, contact information, and status; those fields |
| Vendor | Update / deactivate / reactivate | `VENDOR_UPDATED` / `VENDOR_ARCHIVED` / `VENDOR_RESTORED` | `VENDOR` | Previous/new name, contact information, and status; changed fields only |
| Inventory | Create or update an internal resource | `INVENTORY_CREATED` / `INVENTORY_UPDATED` / `INVENTORY_ADJUSTED` | `INVENTORY_ITEM` | Previous/new quantity and unit of measure when quantity changes; changed fields |
| Resource | Create or update an external resource, or update non-quantity fields | `RESOURCE_CREATED` / `RESOURCE_UPDATED` / `RESOURCE_ARCHIVED` / `RESOURCE_RESTORED` | `RESOURCE` | Previous/new allow-listed resource state; changed fields only |

Snapshots are explicit allow-lists, never entity serialization, to avoid lazy data or credentials leaking into the audit log. Authentication snapshots never include passwords or tokens. Request metadata is added by `AuditService` when available. Background and batch callers must provide a service identity and wrap related writes in `AuditCorrelationContext.withCorrelationId`.

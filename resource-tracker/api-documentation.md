# API Documentation

Base path: `/api/v1`. Endpoints use bearer access tokens unless marked public. Request and response type references point to Kotlin DTOs; collection responses are paged where noted.
**Endpoint count: 71.**

The repository currently declares this many HTTP operations. Fields, required values, and validation constraints are specified by each request/response DTO. Query and path parameters follow the controller method signature. `ADMIN`, `MEMBER`, `TEAM_LEAD`, `DEPARTMENT_MANAGER`, and `MANAGING_DIRECTOR` are application roles. Authentication endpoints that allow anonymous access are identified by their controller security configuration.


## Response schemas

`PagedResponse<T>` is used by paginated list endpoints:

```json
{
  "content": ["<T items>"],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Response DTO fields (nullable fields may be `null`):

- `TokenPair`: `accessToken`, `refreshToken`, `tokenType`, `accessTokenExpiresIn`, `refreshTokenExpiresIn`.
- `ProfileResponse`: `id`, `username`, `email`, `fullName`, `firstName`, `middleName`, `lastName`, `position`, `phone`, `address`, `title`, `role`, `department` (`{id,name}`), `teams` (`[{id,name}]`), `active`.
- `UserResponse`: same profile fields, with `passwordSet`; `role` is an enum string.
- `BudgetResponseDto`: `id`, `departmentId`, `periodType`, `periodStart`, `periodEnd`, `amount`, `committedAmount`, `remaining`, `active`.
- `DepartmentSummaryResponse` (ADMIN only): `id`, `name`. `DepartmentResponse` (other allowed roles): `id`, `name`, `description`, `status`, `manager` (`{id,name}` or null).
- `ProjectResponse`: `id`, `name`, `code`, `department` (`{id,name}`), `startDate`, `endDate`, `active`.
- `ResourceResponse`: `id`, `code`, `name`, `type`, `category`, `unitOfMeasure`, `description`, `quantity`, `availableQuantity`, `reservedQuantity`, `unavailableQuantity`, `location`, `reorderLevel`, `status`, `preferredVendor`, `active`.
- `TeamResponse`: `id`, `name`, `description`, `department`, `manager`, `projects`, `isActive`.
- `VendorResponse`: `id`, `name`, `contactInformation`, `status`.
- `NotificationResponse`: `id`, `recipientId`, `type`, `requestId`, `message`, `status`, `readAt`, `createdAt`.
- `NotificationUpdateResponse`: `updatedCount`.
- `BudgetOverrideResponse`: `id`, `requestId`, `lineItemId`, `departmentId`, `requestedBy`, `amount`, `requestedQuantity`, `reason`, `status`, `createdAt`, `decidedBy`, `decisionComment`, `decidedAt`.
- `AuditEntryResponse`: `id`, `occurredAt`, `actorId`, `actorRole`, `action`, `itemType`, `itemId`, `previousState`, `newState`, `comment`, `ipAddress`, `userAgent`, `changedFields`, `correlationId`.
- `PasswordSetResponse`: `passwordSet`; `PasswordResetResponse`: `message`.
- `StockCorrectionResponse`: `movementId`, `resourceId`, `quantityChange`, `reason`.
- `StockIssueResponse` and `StockIssueProgressResponse`: include the request line description, `fulfillmentPreference`, and `returnTrackingEnabled`; `resourceId` is nullable for externally procured items, which do not change catalogue stock.
- `ProcurementResponse`, request, report, dashboard, issue, return, reconciliation, and history responses are returned in the DTO shapes described by their field names below or by the endpoint-specific response type. JSON dates use ISO-8601 (`YYYY-MM-DD`); timestamps use ISO-8601 date-time strings; enums are serialized as strings.

## Authorization and implementation decisions

- **ADMIN access follows least privilege.** ADMIN is limited to audit retrieval, notifications, user management, and department reads. These are the administrative tasks assigned to this role; broad access to operational modules such as requests, inventory, teams, budgets, and reports is not granted by the ADMIN role.
- **ADMIN department reads expose only lookup data.** Department IDs and names are sufficient for user-management forms that need a department choice. Descriptions, status details, and manager information are omitted to avoid exposing additional department data. Other roles that use department details continue to receive `DepartmentResponse`.
- **Team assignment can happen after account creation.** `teamIds` is optional so an administrator can create a user without deciding which team they belong to. A department manager, who is responsible for the department's team structure, can assign existing users while creating or updating a team. The service limits those assignments to users from that team's department. This keeps team membership with the role that knows the department's staffing context and avoids granting ADMIN team access just to make an assignment.
- **Teamless accounts have limited team-scoped capability until assigned.** The account can be created and managed, but team-dependent actions require the user to have the appropriate team membership.
- **The Managing Director owns the shared vendor list.** Vendor records are organization-wide, so centralized create/update permission helps prevent duplicate or conflicting supplier records. Team leads and department managers can still read vendors to select one for a request.

## Endpoints

### 1. `GET /api/v1/health`

- **Access:** Public
- **Request body:** None
- **Response body:** `{ status: String, timestamp: String }`
- **Source:** `src/main/kotlin/com/resourcetracker/admin/AdminController.kt`

### 2. `GET /api/v1/audit/actors/{actorId}`

- **Access:** ADMIN
- **Request body:** None
- **Response body:** `PagedResponse<AuditEntryResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/audit/controller/AuditController.kt`

### 3. `GET /api/v1/audit/items/{itemType}/{itemId}`

- **Access:** ADMIN
- **Request body:** None
- **Response body:** `List<AuditEntryResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/audit/controller/AuditController.kt`

### 4. `GET /api/v1/audit/actions`

- **Access:** ADMIN
- **Request body:** None
- **Response body:** `PagedResponse<AuditEntryResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/audit/controller/AuditController.kt`

### 5. `GET /api/v1/audit/entries`

- **Access:** ADMIN
- **Request body:** None
- **Response body:** `PagedResponse<AuditEntryResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/audit/controller/AuditController.kt`

### 6. `POST /api/v1/auth/login`

- **Access:** Public
- **Request body:** `LoginRequest`

**Request fields (`LoginRequest`):**
- `username` (required, validation: NotBlank,Size,Schema): `String`
- `password` (required, validation: NotBlank,Size,Schema): `String`

- **Response body:** `TokenPair`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 7. `POST /api/v1/auth/refresh`

- **Access:** Public
- **Request body:** `RefreshRequest`

**Request fields (`RefreshRequest`):**
- `refreshToken` (required, validation: NotBlank,Schema): `String`

- **Response body:** `TokenPair`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 8. `POST /api/v1/auth/logout`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** `LogoutRequest`

**Request fields (`LogoutRequest`):**
- `refreshToken` (required, validation: NotBlank,Schema): `String`

- **Response body:** `204 No Content`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 9. `GET /api/v1/auth/me`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** None
- **Response body:** `ProfileResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 10. `PATCH /api/v1/auth/me`

- **Access:** Authenticated user
- **Request body:** `UpdateOwnProfileRequest`
- **Response body:** `ProfileResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 11. `POST /api/v1/auth/password`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** `PasswordSetRequest`

**Request fields (`ChangePasswordRequest`):**
- `code` (optional): `String?` (8 characters; for setup/reset)
- `currentPassword` (optional): `String?` (for authenticated password change)
- `newPassword` (required): `String` (8–72 characters)

- **Response body:** `PasswordSetResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 12. `POST /api/v1/auth/forgot-password`

- **Access:** Public
- **Request body:** `ForgotPasswordRequest`

**Request fields (`ForgotPasswordRequest`):**
- `email` (required, validation: NotBlank,Email,Size): `String`

- **Response body:** `PasswordResetResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/auth/AuthController.kt`

### 13. `POST /api/v1/budgets`

- **Access:** MANAGING_DIRECTOR
- **Request body:** Choose `CreateBudgetRequestDto` to create a budget or `DecideBudgetOverrideRequest` to decide a pending override.

**Request fields (`CreateBudgetRequestDto`):**
- `departmentId` (required): `Int`
- `periodType` (required): `BudgetPeriodType`
- `periodStart` (required): `LocalDate`
- `periodEnd` (required): `LocalDate`
- `amount` (required): `BigDecimal`
- `active` (optional): `Boolean`

**Override decision fields (`DecideBudgetOverrideRequest`):**
- `overrideId` (required, validation: Min 1): `Int`
- `decision` (required): `APPROVED` or `REJECTED`
- `comment` (required, validation: NotBlank,Size min 10 max 500): `String`

- **Response body:** `BudgetResponseDto` (budget creation) or `BudgetOverrideResponse` (override decision)
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

### 14. `GET /api/v1/budgets/{id}`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `BudgetResponseDto`
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

### 15. `GET /api/v1/budgets`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `List<BudgetResponseDto>`
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

### 16. `PUT /api/v1/budgets/{id}`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `UpdateBudgetRequestDto`

**Request fields (`UpdateBudgetRequestDto`):**
- `amount` (optional): `BigDecimal?`
- `active` (optional): `Boolean?`

- **Response body:** `BudgetResponseDto`
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

### 17. `GET /api/v1/budgets/{id}/remaining`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `BigDecimal` (JSON number)
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

### 18. `POST /api/v1/departments`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `DepartmentRequest`

**Request fields (`DepartmentRequest`):**
- `name` (required): `String`
- `description` (optional): `String?`

- **Response body:** `DepartmentResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/department/DepartmentController.kt`

### 19. `GET /api/v1/departments`

- **Access:** ADMIN, MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<DepartmentSummaryResponse>` for ADMIN; `PagedResponse<DepartmentResponse>` for other roles
- **Source:** `src/main/kotlin/com/resourcetracker/department/DepartmentController.kt`

### 20. `GET /api/v1/departments/{id}`

- **Access:** ADMIN, MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `DepartmentSummaryResponse` for ADMIN; `DepartmentResponse` for other roles
- **Source:** `src/main/kotlin/com/resourcetracker/department/DepartmentController.kt`

### 21. `PUT /api/v1/departments/{id}`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `UpdateDepartmentRequest`

**Request fields (`UpdateDepartmentRequest`):**
- `name` (optional): `String?`
- `description` (optional): `String?`
- `active` (optional): `Boolean?`
- `managerId` (optional): `Int?`

- **Response body:** `DepartmentResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/department/DepartmentController.kt`

### 22. `GET /api/v1/notifications`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** None
- **Response body:** `PagedResponse<NotificationResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/notification/NotificationController.kt`

### 23. `PATCH /api/v1/notifications`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** `NotificationUpdateRequest`

**Request fields (`NotificationUpdateRequest`):**
- `ids` (optional): `Set<Int>?`
- `all` (optional): `Boolean`
- `status` (required): `NotificationStatus`

- **Response body:** `NotificationUpdateResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/notification/NotificationController.kt`

### 24. `GET /api/v1/notifications/stream`

- **Access:** Authenticated user (role restriction not declared)
- **Request body:** None
- **Response body:** `Server-sent event stream (`text/event-stream`)`
- **Source:** `src/main/kotlin/com/resourcetracker/notification/NotificationController.kt`

### 25. `POST /api/v1/projects`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `CreateProjectRequest`

**Request fields (`CreateProjectRequest`):**
- `name` (required): `String`
- `code` (required): `String`
- `departmentId` (required): `Int`
- `startDate` (required): `LocalDate`
- `endDate` (optional): `LocalDate?`

- **Response body:** `ProjectResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/project/ProjectController.kt`

### 26. `PUT /api/v1/projects/{id}`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `UpdateProjectRequest`

**Request fields (`UpdateProjectRequest`):**
- `name` (optional): `String?`
- `code` (optional): `String?`
- `departmentId` (optional): `Int?`
- `startDate` (optional): `LocalDate?`
- `endDate` (optional): `LocalDate?`
- `active` (optional): `Boolean?`
- **Response body:** `ProjectResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/project/ProjectController.kt`

### 27. `GET /api/v1/projects/{id}`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `ProjectResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/project/ProjectController.kt`

### 28. `GET /api/v1/projects`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<ProjectResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/project/ProjectController.kt`

### 29. `GET /api/v1/requests/{id}/budget`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `BudgetReview`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/BudgetReviewController.kt`

### 30. `POST /api/v1/requests`

- **Access:** TEAM_LEAD
- **Request body:** `CreateResourceRequestDto`

**Request fields (`CreateResourceRequestDto`):**
- `teamId` (required, validation: Positive): `Int`
- `projectId` (required, validation: Positive): `Int`
- `justification` (required, validation: NotBlank,Size): `String`
- `requiredDate` (required, validation: NotNull,FutureOrPresent): `LocalDate`
- `lineItems` (required, validation: NotEmpty,Valid): `List<RequestLineItemDto>`
- **Response body:** `ResourceRequestResponseDto`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 31. `POST /api/v1/requests/{id}/issues`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `StockIssueRequest`

**Request fields (`StockIssueRequest`):**
- `lineItemId` (required, validation: Min): `Int`
- `quantity` (required, validation: Min): `Int`
- **Behavior:** INTERNAL lines use reserved/available stock. EXTERNAL lines can be handed over only up to quantities recorded as RECEIVED or ACCEPTED on their procurement records, less prior handoffs and waived amounts. The endpoint records the handoff to the requesting Team Lead without changing internal stock. A previously completed request can still record an external delivery that has not yet been handed over.
- **Response:** `returnTrackingEnabled` is false for external handoffs; their `resourceId` and `expectedReturnDate` are null.
- **Response body:** `StockIssueResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 32. `GET /api/v1/requests/{id}/issues`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<StockIssueProgressResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 33. `PUT /api/v1/requests/{id}/issue`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER
- **Request body:** `IssueWorkflowRequest`

**Request fields (`IssueWorkflowRequest`):**
- `action` (required): `IssueWorkflowAction`
- `issueId` (optional): `Int?`
- `returnId` (optional): `Int?`
- `extensionId` (optional): `Int?`
- `quantity` (optional): `Int?`
- `condition` (optional): `ReturnCondition?`
- `note` (optional): `String?`
- `newExpectedReturnDate` (optional): `LocalDate?`
- `reason` (optional): `String?`
- `decision` (optional): `ReturnExtensionDecision?`
- `comment` (optional): `String?`
- **Response body:** `Issue workflow result DTO (action-specific response)`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 34. `PUT /api/v1/requests/{id}`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER
- **Request body:** `UpdateRequestDto`

**Request fields (`UpdateRequestDto`):**
- `status` (optional): `com.resourcetracker.request.entity.RequestStatus?`
- `comment` (optional, validation: Size): `String?`
- `lineItems` (optional, validation: Valid): `List<RequestLineItemDto>?`
- `commentApproval` (optional, validation: Size): `String?`
- `approvedQuantities` (optional, validation: Valid): `List<ApprovedQuantityDecision>`
- `priority` (optional): `RequestPriority?`
- **Response body:** `ResourceRequestResponseDto`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 35. `GET /api/v1/requests`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<ResourceRequestResponseDto>`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 36. `GET /api/v1/requests/{id}`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `ResourceRequestResponseDto`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 37. `GET /api/v1/requests/{id}/history`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<RequestHistoryResponseDto>`
- **Source:** `src/main/kotlin/com/resourcetracker/request/controller/ResourceRequestController.kt`

### 38. `GET /api/v1/dashboard/analytics`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `DashboardAnalytics`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/DashboardAnalyticsController.kt`

### 39. `GET /api/v1/resources/procurements`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `PagedResponse<ProcurementResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ProcurementController.kt`

### 40. `PUT /api/v1/resources/procurements/{id}`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `UpdateProcurementRequest`

**Request fields (`UpdateProcurementRequest`):**
- `status` (required): `ProcurementStatus`
- `receivedQuantity` (optional): `Int?`
- **Status sequence:** `AUTHORISED` → `ORDERED` → `RECEIVED` → `ACCEPTED`. Send only the next status; `receivedQuantity` is required for `RECEIVED`. `CANCELLED` is system-recorded and cannot be sent.

- **Response body:** `ProcurementResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ProcurementController.kt`

### 41. `GET /api/v1/reports/resources/utilisation`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `PagedResponse<ResourceUtilisationReport>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 42. `GET /api/v1/reports/resources/availability`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `PagedResponse<ResourceAvailabilityReport>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 43. `GET /api/v1/reports/resources/stock-balance`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `PagedResponse<ResourceStockBalanceReport>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 44. `GET /api/v1/reports/resources/waitlist`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `PagedResponse<ResourceWaitlistReport>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 45. `POST /api/v1/reports/resources/{id}/reconciliations`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `ReconciliationRequest`

**Request fields (`ReconciliationRequest`):**
- `physicalCount` (required): `Int`
- `reason` (required): `String`
- `adjustStock` (optional): `Boolean`
- **Response body:** `ReconciliationReport`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 46. `GET /api/v1/reports/resources/{id}/reconciliations`

- **Access:** MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<ReconciliationReport>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 47. `GET /api/v1/reports/budgets/spending`

- **Access:** MANAGING_DIRECTOR, DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `List<BudgetResponseDto>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ReportsController.kt`

### 48. `GET /api/v1/resources`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Behavior:** Returns the shared resource catalog and inventory; there is no department filter.
- **Request body:** None
- **Response body:** `PagedResponse<ResourceResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 49. `POST /api/v1/resources`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `CreateResourceRequest`

**Request fields (`CreateResourceRequest`):**
- `name` (required): `String`
- `type` (required): `ResourceType`
- `category` (required): `ResourceCategory`
- `unitOfMeasure` (required): `String`
- `description` (optional): `String?`
- `quantity` (optional): `Int?`
- `location` (optional): `String?`
- `reorderLevel` (optional): `Int`
- `preferredVendorId` (optional): `Int?`

- **Response body:** `ResourceResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 50. `PUT /api/v1/resources/{id}`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `UpdateResourceRequest`

**Request fields (`UpdateResourceRequest`):**
- `name` (optional): `String?`
- `type` (optional): `ResourceType?`
- `category` (optional): `ResourceCategory?`
- `unitOfMeasure` (optional): `String?`
- `description` (optional): `String?`
- `quantity` (optional): `Int?`
- `location` (optional): `String?`
- `reorderLevel` (optional): `Int?`
- `preferredVendorId` (optional): `Int?`
- `active` (optional): `Boolean?`
- **Response body:** `ResourceResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 51. `GET /api/v1/resources/{id}`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `ResourceResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 52. `GET /api/v1/resources/{id}/movement-history`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Visibility:** Team leads see requests for their teams; department managers see requests in their department; managing directors see all history.
- **Request body:** None
- **Response body:** `List<ResourceMovementHistoryEntry>`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 53. `POST /api/v1/resources/{id}/corrections`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `CorrectionRequestDto`

**Request fields (`CorrectionRequestDto`):**
- `newQuantity` (required, validation: PositiveOrZero): `Int`
- `reason` (required, validation: NotBlank): `String`

- **Response body:** `StockCorrectionResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/resource/ResourceController.kt`

### 54. `GET /api/v1/teams/{id}/members`

- **Access:** MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<TeamMemberResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 55. `POST /api/v1/teams/{id}/members`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `AddTeamMembersRequest`

**Request fields (`AddTeamMembersRequest`):**
- `usernames` (required): `Set<String>`

- **Response body:** `List<TeamMemberResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 56. `PUT /api/v1/teams/{id}/members/{username}`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `TransferTeamMemberRequest`

**Request fields (`TransferTeamMemberRequest`):**
- `toTeamId` (required): `Int`

- **Response body:** `TeamMemberResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 57. `DELETE /api/v1/teams/{id}/members/{username}`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** None
- **Response body:** `TeamMemberResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 58. `GET /api/v1/teams/{id}/members/history`

- **Access:** MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<TeamMemberHistoryResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 59. `POST /api/v1/teams`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `TeamRequest`

**Request fields (`TeamRequest`):**
- `description` (optional): `String?`
- `name` (required): `String`
- `departmentId` (required): `Int`
- `managerId` (required): `Int`
- `projectIds` (optional): `Set<Int>`
- `memberUsernames` (optional): `Set<String>`

Use `memberUsernames` to assign existing users from the selected department while creating the team. Omit it to create the team without members.

- **Response body:** `TeamResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 60. `PUT /api/v1/teams/{id}`

- **Access:** DEPARTMENT_MANAGER
- **Request body:** `UpdateTeamRequest`

**Request fields (`UpdateTeamRequest`):**
- `name` (optional): `String?`
- `departmentId` (optional): `Int?`
- `managerId` (optional): `Int?`
- `projectIds` (optional): `Set<Int>?`
- `memberUsernames` (optional): `Set<String>?`
- `isActive` (optional): `Boolean?`

Use `memberUsernames` to add existing users from the team's department. Omit it to leave membership unchanged.

- **Response body:** `TeamResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 61. `GET /api/v1/teams/{id}`

- **Access:** MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `TeamResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 62. `GET /api/v1/teams`

- **Access:** MEMBER, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<TeamResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/team/TeamController.kt`

### 63. `POST /api/v1/users`

- **Access:** ADMIN, MANAGING_DIRECTOR
- **Request body:** `CreateUserRequest`

**Request fields (`CreateUserRequest`):**
- `username` (required, validation: NotBlank,Size,Schema): `String`
- `email` (required, validation: NotBlank,Email,Size,Schema): `String`
- `firstName` (required, validation: NotBlank,Size,Schema): `String`
- `lastName` (required, validation: NotBlank,Size,Schema): `String`
- `middleName` (optional, validation: Size): `String?`
- `position` (optional, validation: Size): `String?`
- `phone` (optional, validation: Size): `String?`
- `address` (optional, validation: Size): `String?`
- `title` (optional, validation: Size): `String?`
- `role` (required, validation: Schema): `Role`
- `departmentId` (optional): `Int?`
- `teamIds` (optional): `Set<Int>?`

`teamIds` may be omitted or empty to create the user without team membership. A department manager can assign the user later when creating or updating a team. Team assignments must be within the user's department.

- **Response body:** `UserResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/user/UserController.kt`

### 64. `POST /api/v1/users/{id}/resend-setup`

- **Access:** ADMIN, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `UserResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/user/UserController.kt`

### 65. `PUT /api/v1/users/{id}`

- **Access:** ADMIN, MANAGING_DIRECTOR
- **Request body:** `UpdateUserRequest`

**Request fields (`UpdateUserRequest`):**
- `firstName` (optional, validation: Size): `String?`
- `lastName` (optional, validation: Size): `String?`
- `middleName` (optional, validation: Size): `String?`
- `position` (optional, validation: Size): `String?`
- `phone` (optional, validation: Size): `String?`
- `address` (optional, validation: Size): `String?`
- `title` (optional, validation: Size): `String?`
- `active` (optional): `Boolean?`
- `role` (optional): `Role?`
- **Response body:** `UserResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/user/UserController.kt`

### 66. `GET /api/v1/users`

- **Access:** ADMIN, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<UserResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/user/UserController.kt`

### 67. `POST /api/v1/vendors`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `VendorRequest`

**Request fields (`VendorRequest`):**
- `name` (required): `String`
- `contactInformation` (optional): `String?`

- **Response body:** `VendorResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/vendor/VendorController.kt`

### 68. `GET /api/v1/vendors`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `PagedResponse<VendorResponse>`
- **Source:** `src/main/kotlin/com/resourcetracker/vendor/VendorController.kt`

### 69. `GET /api/v1/vendors/{id}`

- **Access:** TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `VendorResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/vendor/VendorController.kt`

### 70. `PUT /api/v1/vendors/{id}`

- **Access:** MANAGING_DIRECTOR
- **Request body:** `UpdateVendorRequest`

**Request fields (`UpdateVendorRequest`):**
- `name` (optional): `String?`
- `contactInformation` (optional): `String?`
- `active` (optional): `Boolean?`

- **Response body:** `VendorResponse`
- **Source:** `src/main/kotlin/com/resourcetracker/vendor/VendorController.kt`

### 71. `GET /api/v1/budgets/overrides/pending`

- **Access:** MANAGING_DIRECTOR
- **Request body:** None
- **Response body:** `List<BudgetOverrideResponse>`
- **Behavior:** Lists external request line costs awaiting an over-budget decision. The Department Manager's request approval remains in place; procurement for the line is held.
- **Source:** `src/main/kotlin/com/resourcetracker/budget/BudgetController.kt`

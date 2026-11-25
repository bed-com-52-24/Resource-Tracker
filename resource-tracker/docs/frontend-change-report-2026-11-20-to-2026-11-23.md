# Frontend change report: 20–23 November 2026

This report summarizes frontend-relevant changes on the backend branch since Friday, 20 November. It is based on commits through 23 November and the current API implementation. Check the production deployment status before relying on changes that require migrations V59–V61.

## API changes

### Individual asset tracking

Resources now support two tracking modes:

- `QUANTITY` keeps the existing aggregate-stock workflow and remains the default when `trackingMethod` is omitted.
- `INDIVIDUAL` tracks each physical item as an asset. For this mode, create an internal resource with `unitOfMeasure: "unit"` and omit `quantity`; register the assets separately.

`ResourceResponse` now includes `trackingMethod` and `assetCounts`. For individually tracked resources, `quantity` is the number of registered assets and availability is calculated from asset status. `assetCounts` contains `totalRegistered`, `available`, `reserved`, `issued`, `damaged`, `missing`, `disposed`, and `returnPending`.

New asset endpoints:

| Method and path | Purpose | Access |
| --- | --- | --- |
| `GET /api/v1/resources/{id}/assets` | List assets and their current statuses | Existing resource roles |
| `POST /api/v1/resources/{id}/assets` | Register assets; body is an array of `{ "assetCode", "serialNumber?", "location?" }` | `MANAGING_DIRECTOR` |
| `GET /api/v1/resources/assets/{assetId}/history` | Read an asset’s movement history | Existing resource roles |

Asset statuses include `AVAILABLE`, `RESERVED`, `ISSUED`, `RETURN_PENDING`, `DAMAGED`, `MISSING`, and `DISPOSED`. Asset codes and non-empty serial numbers must be unique.

### Stock issue and return payloads

For an individually tracked resource, the issue request must identify exactly the assets being handed over. Add `assetIds` to the existing issue body:

```json
{
  "lineItemId": 12,
  "quantity": 2,
  "assetIds": [101, 104]
}
```

The number of distinct asset IDs must equal `quantity`. Omit `assetIds` for quantity-tracked resources. Issue responses now include `assetIds`.

Individual-asset returns also require `assetIds` when submitting a return. When a manager verifies only part of a submitted asset return, the verification body must identify the accepted assets. The existing return workflow and status transitions still apply.

### Inventory corrections and movement history

`PUT /api/v1/resources/{id}/corrections` is now the tracking-aware correction operation (it was documented as `POST` previously).

For quantity-tracked resources:

```json
{ "newQuantity": 42, "reason": "Cycle count" }
```

For individually tracked resources:

```json
{
  "assets": [{ "assetId": 103, "status": "MISSING" }],
  "reason": "Asset not found during stocktake"
}
```

Send exactly one of `newQuantity` or `assets`. `GET /api/v1/resources/{id}/movement-history` now includes asset events alongside stock movements and issue/return events.

### Password setup and reset

Setup and reset emails link to `/reset-password?token=...`. The frontend should read `token` from the query string and submit it to `POST /api/v1/auth/password` with `newPassword`:

```json
{ "token": "<single-use-token>", "newPassword": "<new-password>" }
```

This replaces the previous setup/reset `code` field. Authenticated password changes continue to use `currentPassword` and `newPassword`. `POST /api/v1/auth/forgot-password` returns the same generic response whether or not an active account matches the email. Administrators can request a replacement setup link with `POST /api/v1/users/{id}/resend-setup`.

## CORS and local integration

The API now allows credentialed CORS requests from the origin configured through `APP_FRONTEND_BASE_URL`, plus `http://localhost:3000`. Set `APP_FRONTEND_BASE_URL` to the deployed frontend origin in the backend environment. The API base path remains `/api/v1`; authenticated calls still use bearer access tokens.

## Deployment and data notes

- Forward SQL migrations V59–V61 repair the legacy project/team link, convert refresh-token IDs, and restore integer notification request links. The production database history shown on 23 November ended at V58, so these changes are not live until a JAR containing the migrations is deployed and starts successfully.
- V60 clears existing refresh tokens as part of the ID conversion. Users will need to sign in again after that migration runs.
- Existing API IDs remain JSON integers; the ID migration is a database consistency fix and does not require changing frontend ID handling.
- The production profile now also runs the sample-data seeder. This may make seeded users, teams, departments, and projects visible in the production UI.
- CI’s post-deploy health check now calls `GET /api/v1/health` instead of `/actuator/health`; this does not change frontend application behavior.

## Reference

For endpoint contracts outside the additions and changes listed here, see [API documentation](../api-documentation.md). Relevant implementation changes include commits `ff8d4b8` (asset tracking and password-link handling), `051702d` (resource ID schema), `a127b73` (health-check target), `97abdd7` (CORS and production seeding), and `3d6c585` (forward database migrations).

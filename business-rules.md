
Resource Tracker

Business Rules
Version: 1.0
Date: [01 Oct, 2026]
Related Document: Software Requirements Specification (SRS), Resource Tracker v1.0.

Business Rule 1
Team Lead Authorization Scope
Definition:
A Team Lead can only submit requests for teams they are authorized to represent.
Action:
A Team Lead submits a request.
Rule:
The system checks that the Team Lead is authorized for the selected team.
If Violated:
HTTP 403 Forbidden.

Business Rule 2
Single Manager Routing
Definition:
Every new request is assigned to the active Department Manager in the requester's department, or flagged for Admin reassignment if assignment is ambiguous or unavailable.
Action:
A new request is submitted.
Rule:
The request is assigned to the active Department Manager in the requester's department. Exactly one active manager is required. If there are none or more than one, the request remains pending, is flagged for administrative reassignment, and all active Admins are notified.
If Violated:
The request is not silently left unassigned; it is flagged for Admin action.

Business Rule 3
Manager Decision Authority
Definition:
Only the assigned Department Manager can approve, reject, or query a request.
Action:
A manager attempts to approve, reject, or query a request.
Rule:
The system checks that the manager is the one assigned to that request.
If Violated:
HTTP 403 Forbidden.

Business Rule 4
Query Resubmit Cycle
Definition:
A queried request must be corrected and resubmitted before it can be reviewed again.
Action:
A Department Manager queries a request.
Rule:
The request moves to the QUERIED state. The Team Lead corrects and resubmits it, moving it back to PENDING.
If Violated:
Attempting to approve a QUERIED request returns HTTP 409 Conflict.

Business Rule 5
Immutability of Approved or Rejected Requests
Definition:
Approved and rejected requests cannot be modified.
Action:
Someone tries to edit an approved or rejected request.
Rule:
The system blocks the action.
If Violated:
HTTP 409 Conflict.

Business Rule 6
Mandatory Audit Record
Definition:
Every state transition must create an immutable audit record.
Action:
Any state change occurs.
Rule:
The system writes a permanent, immutable audit record.
If Violated:
If the audit record cannot be saved, the transaction rolls back and no state change occurs.

Business Rule 7
Valid Reference Data
Definition:
A request must reference valid, existing organizational and vendor records.
Action:
A request is submitted.
Rule:
The system validates all references (department, vendor, project, team, user).
If Violated:
HTTP 400 Bad Request.

Business Rule 8
Read-Only Management Oversight
Definition:
Management oversight is read-only unless explicit permission is granted.
Action:
A Managing Director tries to approve or reject a request.
Rule:
The system blocks the action unless the Managing Director has explicit Admin permission.
If Violated:
HTTP 403 Forbidden.

Business Rule 9 
Validation Before Decision
Definition:
A request cannot be approved or rejected if validation fails.
Action:
A Department Manager tries to approve or reject a request.
Rule:
The system checks that all required fields are filled.
If Violated:
HTTP 409 Conflict with a list of missing fields.

Business Rule 10
Manager Reassignment
Definition:
Only an Admin may reassign a request to a department manager.
Action:
An Admin reassigns a request.
Rule:
The selected manager must be active, have the DEPARTMENT_MANAGER role, and belong to the request's department. The reassignment is recorded in request history and notifies the old and new managers.
If Violated:
HTTP 400 Bad Request.

Business Rule 11
Resource Type and Stock Integrity
Definition:
Requests cannot change a resource's type or physical stock.
Rule:
Existing resource type is taken from the resource record. Physical quantity changes only through issues, returns, or corrections, each recorded by a stock movement in the same transaction. Reservations and request operations are recorded in request history. Available quantity is physical quantity minus active reservations and unavailable quantity, floored at zero.

Business Rule 12
Cancellation After Issue
Definition:
A request with issued stock cannot be cancelled.
Rule:
Issued stock must go through the return process; cancellation is rejected with HTTP 409.

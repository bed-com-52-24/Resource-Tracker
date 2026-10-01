
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
Every new request must be routed to exactly one Department Manager.
Action:
A new request is submitted.
Rule:
The system routes the request to exactly one Department Manager using the active routing rules.
If Violated:
HTTP 409 Conflict.

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
Data-Driven Routing Configuration
Definition:
Routing configuration must be stored in the database and not hard-coded.
Action:
An Admin updates routing rules.
Rule:
The system saves the changes in the database and writes an audit record.
If Violated:
Not applicable.

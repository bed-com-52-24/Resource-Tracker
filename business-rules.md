
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

Business Rule 5
Request Immutability
Definition:
Submitted requests cannot be edited.
Action:
The requester tries to edit a submitted request.
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
A Managing Director tries to modify a resource request.
Rule:
The system blocks the action unless the Managing Director has explicit Admin permission.
If Violated:
HTTP 403 Forbidden.

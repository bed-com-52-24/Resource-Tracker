BUSINESS RULES


BUSINESS RULE 1
   Team Lead Authorization Scope
DEFINITION
   A Team Lead can only submit requests for teams they are authorized to represent.
ACTION  
   A Team Lead submits a request.
RULE   
   The system checks they are authorized for that team.
IF VIOLATED 
   HTTP 403 Forbidden.

       
BUSINESS RULE 2
   Single manager routing.
DEFINITION
   Every new request must be routed to exactly one Line  Manager.
ACTION
   A new request is submitted.
RULE
   The system routes to exactly one Line Manager.
IF VIOLATED
   HTTP 409 conflict.


BUSINESS RULE 3
   Manager decision authority
DEFINITION
   Only the assigned Line Manager can approve, reject or query a request.
ACTION
   A manager tries to approve, reject or query.
RULE
   The system checks they are the assigned manager.
IF VIOLATED
   HTTP 403 Forbidden.


BUSINESS RULE 4
   Query resubmit cycle.
DEFINITION
   A queried request must be corrected and resubmitted before manager review.
ACTION
   A manager queries a request.
RULE
   Request moves to QUERIED. Team Lead fixes and resubmits PENDING.
IF VIOLATED
   Attempt to approve a QUERIED request , HTTP 409 conflict.

 
BUSINESS RULE 5
   Immutability of approved or rejected request.
DEFINITION
   Approved and rejected requests cannot be modified.
ACTION
   Someone tries to edit an approved or rejected request.
RULE
   The system blocks the action.
IF VIOLATED
   HTTP 409 conflict
   

BUSINESS RULE 6
   Mandatory audit record
DEFINITION
   Every state transition must create an immutable audit record.
ACTION
   Any state change happens.
RULE
   The system writes a permanent, immutable audit record.
IF VIOLATED
   Audit fails transaction rolls back.


BUSINESS RULE 7
   Valid reference data.
DEFINITION
   A request must reference valid, existing organizational and vendor records.
ACTION
   A request is submitted.
RULE
   The system validates all references (department, vendor, project, team, user).
IF VIOLATED
   HTTP 400 bad request 


BUSINESS RULE 8
   Read-only management oversight.
DEFINITION
   Management oversight is read-only unless explicit permission is granted.
ACTION
   A Managing Director tries to approve or reject.
RULE
   The system blocks it unless they have explicit Admin permission.
IF VIOLATED
   HTTP 403 forbidden

   
BUSINESS RULE 9
   Validation before decision.
DEFINITION
   A request cannot be approved or rejected if validation fails.
ACTION
   A manager tries to approve or reject.
RULE
   The system checks all required fields are filled.
IF VIOLATED
   HTTP 409 conflict with a list of missing fields.


BUSINESS RULE 10
   Data-driven routing configuration
DEFINITION
   Routing configuration shall be stored in the database, not hard-coded.
ACTION
   An admin updates routing rules
RULE
   The system saves them in the database and writes an audit record.
IF VIOLATED
   N/A

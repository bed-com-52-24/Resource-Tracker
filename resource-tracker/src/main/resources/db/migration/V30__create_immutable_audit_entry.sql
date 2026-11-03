CREATE TRIGGER trg_audit_entry_no_update
BEFORE UPDATE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';

CREATE TRIGGER trg_audit_entry_no_delete
BEFORE DELETE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';

ALTER TABLE failed_password_emails
    ADD COLUMN purpose VARCHAR(10) NOT NULL DEFAULT 'RESET';

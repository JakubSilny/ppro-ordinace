-- kdo kdy četl nebo měnil osobní a zdravotní údaje (GDPR čl. 30 a 32); záznamy se nemění ani nemažou
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    username    VARCHAR(100) NOT NULL,
    method      VARCHAR(10)  NOT NULL,
    path        VARCHAR(300) NOT NULL,
    status      INT          NOT NULL
);
CREATE INDEX idx_audit_log_at ON audit_log (at);
CREATE INDEX idx_audit_log_path ON audit_log (path text_pattern_ops);

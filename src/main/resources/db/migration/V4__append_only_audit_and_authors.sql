-- záznam přístupů jde jen přidávat: úprava ani smazání neprojde ani přímo v databázi
CREATE FUNCTION audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END $$;
CREATE TRIGGER audit_log_no_update_delete BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_append_only();

-- kdo rezervaci založil a kdo ji naposledy změnil (zrušil, přesunul…); starší řádky autora nemají
ALTER TABLE appointment ADD COLUMN created_by VARCHAR(100), ADD COLUMN updated_by VARCHAR(100);

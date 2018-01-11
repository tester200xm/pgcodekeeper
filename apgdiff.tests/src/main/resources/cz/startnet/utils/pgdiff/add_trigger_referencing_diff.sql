CREATE OR REPLACE FUNCTION test_table_trigger() RETURNS "trigger"
    AS $$
begin
	return NEW;
end;
$$
    LANGUAGE plpgsql;

ALTER FUNCTION test_table_trigger() OWNER TO fordfrog;

CREATE TRIGGER test_view_trigger1
	INSTEAD OF INSERT OR UPDATE ON test_view
	REFERENCING OLD TABLE AS oldtab 
	FOR EACH ROW
	EXECUTE PROCEDURE test_table_trigger();

CREATE TRIGGER test_view_trigger2
	AFTER INSERT OR UPDATE ON test_view
	REFERENCING NEW TABLE AS newtab OLD TABLE AS oldtab 
	FOR EACH STATEMENT
	EXECUTE PROCEDURE test_table_trigger();

CREATE TRIGGER test_table_trigger
	BEFORE INSERT OR UPDATE ON test_table
	REFERENCING NEW TABLE AS newtab 
	FOR EACH ROW
	EXECUTE PROCEDURE test_table_trigger();

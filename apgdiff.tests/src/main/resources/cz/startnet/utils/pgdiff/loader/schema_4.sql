SET search_path = pg_catalog;

CREATE TABLE public.call_logs (
    id bigint NOT NULL
);

ALTER TABLE call_logs ALTER COLUMN id SET DEFAULT
nextval('public.call_logs_id_seq'::regclass);
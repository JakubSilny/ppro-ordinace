-- optimistický zámek rezervace: ze dvou souběžných změn (přesun × zrušení) projde jen první, druhá dostane 409
ALTER TABLE appointment ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

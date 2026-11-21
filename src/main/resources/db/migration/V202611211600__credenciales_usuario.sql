ALTER TABLE usuarios
    ADD COLUMN version_credencial BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN password_temporal_hasta TIMESTAMPTZ;

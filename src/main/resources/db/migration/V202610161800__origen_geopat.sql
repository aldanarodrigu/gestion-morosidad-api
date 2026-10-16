-- Los registros existentes pertenecen a GeoPagos (Intendencia).
ALTER TABLE padrones ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'INTENDENCIA';
ALTER TABLE padrones DROP CONSTRAINT padrones_cm_key;
ALTER TABLE padrones ADD CONSTRAINT uk_padron_origen_cm UNIQUE (origen, cm),
    ADD CONSTRAINT ck_padron_origen CHECK (origen IN ('INTENDENCIA', 'SUCIVE'));

-- No se presume equivalencia entre códigos de tributo de bases diferentes.
ALTER TABLE tributos ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'INTENDENCIA';
ALTER TABLE tributos DROP CONSTRAINT uk_tributos_codigo;
ALTER TABLE tributos ADD CONSTRAINT uk_tributo_origen_codigo UNIQUE (origen, codigo),
    ADD CONSTRAINT ck_tributo_origen CHECK (origen IN ('INTENDENCIA', 'SUCIVE'));

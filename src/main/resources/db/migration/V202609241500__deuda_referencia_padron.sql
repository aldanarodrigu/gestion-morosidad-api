-- La deuda deja de duplicar datos del padrón y del contribuyente:
-- esos datos se obtienen a través de padrones (y padrones.contribuyente_id).
-- La tabla todavía no tiene datos (no hay sincronización con GeoPagos), por eso
-- padron_id puede agregarse directamente como NOT NULL.

ALTER TABLE deuda ADD COLUMN padron_id BIGINT NOT NULL;

ALTER TABLE deuda
    ADD CONSTRAINT fk_deuda_padron FOREIGN KEY (padron_id) REFERENCES padrones (id);

CREATE INDEX idx_deuda_padron_id ON deuda (padron_id);

-- Los índices sobre estas columnas se eliminan junto con ellas
ALTER TABLE deuda
    DROP COLUMN cm,
    DROP COLUMN numero_padron,
    DROP COLUMN block,
    DROP COLUMN unidad,
    DROP COLUMN localidad,
    DROP COLUMN padtipo,
    DROP COLUMN contribuyente_nombre,
    DROP COLUMN contribuyente_documento;

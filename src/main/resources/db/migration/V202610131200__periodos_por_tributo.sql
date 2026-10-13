ALTER TABLE configuraciones_segmento
    ADD COLUMN tributo_id BIGINT REFERENCES tributos(id);
DROP INDEX uk_configuracion_activa_por_segmento;
CREATE UNIQUE INDEX uk_configuracion_base_activa
    ON configuraciones_segmento (segmento_mora_id) WHERE activa AND tributo_id IS NULL;
CREATE UNIQUE INDEX uk_configuracion_tributo_activa
    ON configuraciones_segmento (tributo_id, segmento_mora_id) WHERE activa AND tributo_id IS NOT NULL;
CREATE INDEX idx_configuracion_tributo ON configuraciones_segmento (tributo_id);

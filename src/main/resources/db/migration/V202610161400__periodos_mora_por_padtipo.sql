-- Un conjunto de períodos por PadTipo; NULL conserva los rangos base.
ALTER TABLE configuraciones_segmento ADD COLUMN tipo_padron VARCHAR(100);

-- No hay correspondencia unívoca entre un tributo y un PadTipo.
-- Se conservan los rangos anteriores por tributo como historial, sin asignarlos arbitrariamente.
UPDATE configuraciones_segmento SET activa = FALSE WHERE tributo_id IS NOT NULL AND activa;
DROP INDEX uk_configuracion_base_activa;
DROP INDEX uk_configuracion_tributo_activa;

CREATE UNIQUE INDEX uk_configuracion_base_activa ON configuraciones_segmento (segmento_mora_id)
    WHERE activa AND tipo_padron IS NULL AND tributo_id IS NULL;
CREATE UNIQUE INDEX uk_configuracion_padtipo_activa ON configuraciones_segmento (tipo_padron, segmento_mora_id)
    WHERE activa AND tipo_padron IS NOT NULL;
ALTER TABLE configuraciones_segmento
    ADD CONSTRAINT ck_configuracion_tributo_historico CHECK (tributo_id IS NULL OR NOT activa),
    ADD CONSTRAINT ck_configuracion_tipo_padron CHECK
        (tipo_padron IS NULL OR (tipo_padron = btrim(tipo_padron) AND tipo_padron <> ''));

-- Las deudas activas parten de los rangos base hasta que se personaliza su PadTipo.
UPDATE deuda d SET segmento_mora_id = (
    SELECT c.segmento_mora_id FROM configuraciones_segmento c
    WHERE c.activa AND c.tributo_id IS NULL AND c.tipo_padron IS NULL
      AND (CURRENT_TIMESTAMP AT TIME ZONE 'America/Montevideo')::date - d.deuda_desde >= c.dias_desde
      AND (c.dias_hasta IS NULL OR (CURRENT_TIMESTAMP AT TIME ZONE 'America/Montevideo')::date - d.deuda_desde <= c.dias_hasta)
    ORDER BY c.dias_desde LIMIT 1
)
WHERE d.estado <> 'CANCELADA';

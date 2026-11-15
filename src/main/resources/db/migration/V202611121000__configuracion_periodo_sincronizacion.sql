CREATE TABLE configuracion_sincronizacion (
    id INTEGER PRIMARY KEY CHECK (id = 1), anio_minimo INTEGER NOT NULL CHECK (anio_minimo BETWEEN 1900 AND 9999)
);
INSERT INTO configuracion_sincronizacion VALUES (1, 2013);
ALTER TABLE deuda ADD COLUMN en_periodo_gestion BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE deuda ADD COLUMN anio_periodo INTEGER;

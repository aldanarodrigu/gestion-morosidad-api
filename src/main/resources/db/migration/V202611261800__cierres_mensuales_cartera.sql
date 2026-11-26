CREATE TABLE cierre_mensual_cartera (
    mes DATE NOT NULL CHECK (EXTRACT(DAY FROM mes) = 1),
    origen VARCHAR(20) NOT NULL CHECK (origen IN ('INTENDENCIA', 'SUCIVE')),
    corte TIMESTAMP NOT NULL,
    anio_minimo INTEGER,
    PRIMARY KEY (mes, origen)
);

CREATE TABLE cierre_mensual_cartera_saldos (
    mes DATE NOT NULL,
    origen VARCHAR(20) NOT NULL,
    tipo_padron TEXT,
    segmento_id BIGINT,
    importe NUMERIC NOT NULL,
    FOREIGN KEY (mes, origen) REFERENCES cierre_mensual_cartera (mes, origen) ON DELETE CASCADE
);

CREATE INDEX idx_cierre_mensual_saldos_mes_origen ON cierre_mensual_cartera_saldos (mes, origen);

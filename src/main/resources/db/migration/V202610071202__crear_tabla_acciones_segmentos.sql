CREATE TABLE acciones_segmentos (
    id BIGSERIAL PRIMARY KEY,

    accion_id BIGINT NOT NULL,
    segmento_id BIGINT NOT NULL,

     CONSTRAINT uk_accion_segmento
        UNIQUE (accion_id, segmento_id),

     CONSTRAINT fk_accion_segmento_accion
         FOREIGN KEY (accion_id)
         REFERENCES acciones(id),

     CONSTRAINT fk_accion_segmento_segmento
        FOREIGN KEY (segmento_id)
        REFERENCES segmentos_mora(id)
);
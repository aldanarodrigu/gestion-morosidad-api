CREATE TABLE acciones (
                          id BIGSERIAL PRIMARY KEY,
                          nombre VARCHAR(100) NOT NULL UNIQUE,
                          activo BOOLEAN NOT NULL DEFAULT TRUE
);
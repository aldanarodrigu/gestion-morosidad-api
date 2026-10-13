CREATE TABLE usuario_roles (
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    rol VARCHAR(255) NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'NOTIFICADOR', 'ANALISTA')),
    PRIMARY KEY (usuario_id, rol)
);

INSERT INTO usuario_roles (usuario_id, rol)
SELECT id, rol FROM usuarios;

ALTER TABLE usuarios DROP COLUMN rol;

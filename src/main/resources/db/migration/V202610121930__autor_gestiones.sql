-- Las gestiones anteriores no tienen autor conocido: se conserva NULL.
ALTER TABLE gestiones ADD COLUMN usuario_id BIGINT;
ALTER TABLE gestiones ADD CONSTRAINT fk_gestion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
CREATE INDEX idx_gestiones_deuda_fecha ON gestiones (deuda_id, fecha DESC, id DESC);

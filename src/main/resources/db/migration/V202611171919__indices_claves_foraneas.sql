-- PostgreSQL no indexa las claves foráneas. Sin estos índices, el filtro "con contacto" combinado con
-- localidad recorría todos los contactos por cada deuda (20 s por página con el volumen real).
CREATE INDEX IF NOT EXISTS idx_contactos_contribuyente ON contactos (contribuyente_id);
-- Contribuyente -> padrones: cartera agrupada y padrones de un contribuyente.
CREATE INDEX IF NOT EXISTS idx_padrones_contribuyente ON padrones (contribuyente_id);
-- Validación al eliminar una acción con gestiones registradas.
CREATE INDEX IF NOT EXISTS idx_gestiones_accion ON gestiones (accion_id);

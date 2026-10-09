-- El número de padrón NO es único: cada localidad numera sus padrones por separado.
-- En los datos reales de GeoPagos (08/10/2026) 3.164 números se repiten en distintas
-- localidades y la restricción hacía que la sincronización omitiera más de 12.000 deudas.
-- El identificador único del padrón es el CM (padrones_cm_key).

ALTER TABLE padrones DROP CONSTRAINT IF EXISTS padrones_numero_padron_key;

-- Se sigue buscando por número de padrón: índice no único
CREATE INDEX IF NOT EXISTS idx_padrones_numero_padron ON padrones (numero_padron);

-- CM identifica al padrón y se conserva en padrones.cm.
-- La relación con la persona se mantiene mediante padrones.contribuyente_id.
ALTER TABLE contribuyentes DROP COLUMN cm;

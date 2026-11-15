-- Valores de la fuente; no se infieren desgloses históricos.
ALTER TABLE deuda ADD COLUMN deuda_facturas NUMERIC(19,2);
ALTER TABLE deuda ADD COLUMN saldo_a_favor NUMERIC(19,2);
ALTER TABLE deuda ADD COLUMN credito_descontado NUMERIC(19,2);
ALTER TABLE captura_cuentas ADD COLUMN deuda_facturas NUMERIC(19,2);
ALTER TABLE captura_cuentas ADD COLUMN saldo_a_favor NUMERIC(19,2);
ALTER TABLE captura_cuentas ADD COLUMN credito_descontado NUMERIC(19,2);

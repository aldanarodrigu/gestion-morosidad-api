-- No se infieren motivos para movimientos o capturas anteriores.
ALTER TABLE deuda ADD COLUMN motivo_ultimo_cobro VARCHAR(60);
ALTER TABLE captura_cuentas ADD COLUMN motivo_ultimo_cobro VARCHAR(60);

-- Campos opcionales de la fuente, sin inferir datos anteriores.
ALTER TABLE deuda ADD COLUMN cantidad_facturas_vencidas INTEGER;
ALTER TABLE deuda ADD COLUMN cantidad_facturas_con_deuda INTEGER;
ALTER TABLE deuda ADD COLUMN anio_desde INTEGER;
ALTER TABLE deuda ADD COLUMN anio_hasta INTEGER;
ALTER TABLE deuda ADD COLUMN cantidad_anios_deuda INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN cantidad_facturas_vencidas INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN cantidad_facturas_con_deuda INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN anio_desde INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN anio_hasta INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN cantidad_anios_deuda INTEGER;
ALTER TABLE captura_cuentas ADD COLUMN anios_deuda VARCHAR(200);

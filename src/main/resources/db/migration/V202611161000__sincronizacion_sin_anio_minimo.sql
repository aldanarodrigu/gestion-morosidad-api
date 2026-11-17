-- NULL indica sin limite inferior de vencimiento. Conserva el periodo configurado.
ALTER TABLE configuracion_sincronizacion ALTER COLUMN anio_minimo DROP NOT NULL;

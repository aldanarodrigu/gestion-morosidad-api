-- La sincronización inserta decenas de miles de filas. Con IDs IDENTITY, Hibernate tiene que
-- insertar de a una fila para conocer el ID generado: contra una base remota (Supabase, ~170 ms
-- por viaje) eso son horas. Con una secuencia que avanza de a 1000, Hibernate reserva 1000 IDs
-- por consulta y puede enviar los INSERT en lotes.
--
-- La columna deja de ser IDENTITY porque la secuencia interna de IDENTITY no aparece en
-- information_schema.sequences y Hibernate no la encuentra al validar el esquema. Se reemplaza
-- por una secuencia normal con el mismo nombre, usada también como valor por defecto: un INSERT
-- sin ID (código viejo, scripts manuales) sigue funcionando y no choca con los bloques reservados,
-- porque cada valor entregado por la secuencia es el tope de un bloque distinto.
--
-- El primer valor es max(id) + 1000: Hibernate usa el bloque (valor - 999 .. valor), que así
-- empieza después de los IDs existentes.

DO $$
DECLARE
    tabla TEXT;
    siguiente BIGINT;
BEGIN
    FOREACH tabla IN ARRAY ARRAY['contribuyentes', 'padrones', 'deuda', 'tributos', 'contactos'] LOOP
        EXECUTE format('ALTER TABLE %I ALTER COLUMN id DROP IDENTITY IF EXISTS', tabla);
        EXECUTE format('CREATE SEQUENCE %I INCREMENT BY 1000 OWNED BY %I.id', tabla || '_id_seq', tabla);
        EXECUTE format('SELECT COALESCE(max(id), 0) + 1000 FROM %I', tabla) INTO siguiente;
        PERFORM setval(tabla || '_id_seq', siguiente, false);
        EXECUTE format('ALTER TABLE %I ALTER COLUMN id SET DEFAULT nextval(%L)', tabla, tabla || '_id_seq');
    END LOOP;
END $$;

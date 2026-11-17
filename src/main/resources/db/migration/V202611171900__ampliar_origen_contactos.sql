-- Alinea el almacenamiento con el limite de 255 validado por la API y el formulario.
ALTER TABLE contactos ALTER COLUMN origen TYPE VARCHAR(255);

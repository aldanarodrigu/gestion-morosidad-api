ALTER TABLE acciones ADD COLUMN descripcion VARCHAR(500);

UPDATE acciones SET descripcion = 'Envío de recordatorios de deuda mediante WhatsApp.' WHERE nombre = 'Recordatorios automáticos por WhatsApp' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Información sobre opciones de pago online y adhesión al débito automático.' WHERE nombre = 'Promoción de medios de pago online y débito automático' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Notificación formal para solicitar la regularización de la deuda.' WHERE nombre = 'Intimaciones formales' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Registro de gestiones relacionadas con alternativas de refinanciación de la deuda.' WHERE nombre = 'Convenios de refinanciación' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Contacto directo con el contribuyente por teléfono o atención personalizada.' WHERE nombre = 'Contacto telefónico o personalizado' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Comunicaciones dirigidas a contribuyentes según el tipo de tributo adeudado.' WHERE nombre = 'Campañas específicas por tipo de tributo' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Información sobre las consecuencias administrativas y judiciales de la deuda.' WHERE nombre = 'Alertas sobre consecuencias administrativas y judiciales' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Priorización de la gestión de contribuyentes con deudas elevadas y recurrentes.' WHERE nombre = 'Priorización de grandes deudores recurrentes' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Gestión relacionada con la comunicación de la deuda al Clearing de Informes.' WHERE nombre = 'Clearing de Informes' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Derivación de la deuda para iniciar su cobro por vía judicial.' WHERE nombre = 'Derivación a ejecución judicial' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Gestión de medidas cautelares y embargos vinculados al cobro de la deuda.' WHERE nombre = 'Embargos y medidas cautelares' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Revisión de deudas para evaluar su clasificación como incobrables.' WHERE nombre = 'Depuración de cartera incobrable' AND descripcion IS NULL;
UPDATE acciones SET descripcion = 'Aplicación de estrategias de gestión específicas para grandes deudores.' WHERE nombre = 'Estrategias diferenciadas para grandes deudores' AND descripcion IS NULL;

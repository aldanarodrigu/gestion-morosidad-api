-- Alcance inicial: acciones resaltadas en amarillo en la política simplificada UTEC.
-- Se conservan las acciones y sus relaciones para mantener el historial.
-- No modifica las decisiones previas de desactivar acciones incluidas en el alcance.
UPDATE acciones SET activo = FALSE
WHERE nombre IN (
    'Campañas específicas por tipo de tributo',
    'Alertas sobre consecuencias administrativas y judiciales',
    'Priorización de grandes deudores recurrentes',
    'Embargos y medidas cautelares',
    'Depuración de cartera incobrable',
    'Estrategias diferenciadas para grandes deudores'
);

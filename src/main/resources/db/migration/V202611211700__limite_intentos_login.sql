CREATE TABLE intentos_login (
    cuenta TEXT PRIMARY KEY,
    fallos INTEGER NOT NULL CHECK (fallos > 0),
    bloqueada BOOLEAN NOT NULL,
    vence_en TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_intentos_login_vence ON intentos_login (vence_en);

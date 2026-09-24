# gestion-morosidad-api

Backend del Sistema de Gestión de Morosidad (Spring Boot 4, Java 21, PostgreSQL).

## Relación entre padrón y contribuyente

`CM` identifica al padrón en la API GeoPagos; `NUMERO_PADRON` es el número visible del padrón.
Cada deuda referencia un padrón, y cada padrón referencia un contribuyente mediante
`padrones.contribuyente_id`. `GET /api/padrones/{numeroPadron}` incluye el objeto
`contribuyente` dentro del padrón. Las respuestas de deuda también permiten recorrer
`deuda.padron.contribuyente`; el campo `deuda.contribuyente` sigue disponible para
los consumidores actuales de la API.

`CM` se guarda solo en `padrones.cm`. Las respuestas de contribuyente conservan el campo `cm`
como identificador del padrón consultado. El origen no proporciona un identificador de persona,
por lo que no se agrupan automáticamente padrones de una misma persona.

## Levantar todo con Docker

Requiere Docker Desktop y el repo del frontend clonado al lado de este:

```
NetBeansProjects/
  gestion-morosidad-api/
  gestion-morosidad-frontend/
```

```bash
cp .env.example .env        # completar DB_PASSWORD y credenciales de GeoPagos
docker compose up --build -d
```

| Servicio | URL en el host |
|---|---|
| Frontend | http://localhost:8000 |
| API | http://localhost:9080 (también vía http://localhost:8000/api) |
| PostgreSQL | localhost:5433 |

Los puertos se cambian en `.env` (`FRONT_HOST_PORT`, `API_HOST_PORT`, `DB_HOST_PORT`).
Todos quedan ligados a `127.0.0.1`, no son accesibles desde la red.

Comandos útiles:

```bash
docker compose logs -f api      # ver logs de la API
docker compose up --build -d api  # reconstruir solo la API después de cambios
docker compose down             # apagar (los datos de Postgres se conservan)
docker compose down -v          # apagar y borrar la base
```

## Desarrollo sin Docker para la API

Levantar solo la base con `docker compose up -d db` y correr la app desde el IDE con las
variables `DB_PORT=5433` y `DB_PASSWORD=<la del .env>`.

### Windows: "bind: An attempt was made to access a socket in a way forbidden"

Windows reserva rangos de puertos para Hyper-V/WSL (ver con
`netsh interface ipv4 show excludedportrange protocol=tcp`). Si un puerto cae en esos rangos,
cambiarlo en `.env`, o liberar las reservas con una consola de administrador:
`net stop winnat` y luego `net start winnat`.

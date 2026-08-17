# k6 — Pruebas de rendimiento BIOPET

Guia de corridas oficiales de rendimiento del backend BIOPET (rama f06-f07).

## Escenario

- **Script:** `k6/listado-mascotas.js`
- **Opciones:** `k6/opts.js` (50 VUs, ramp-up 5s, carga sostenida 30s, TLS inseguro solo para localhost)
- **Endpoint medido (protegido real):** `GET /api/mascotas?page=0&size=10`
  - Requiere `access_token` (JWT) via cookie `Set-Cookie`, obtenido con
    `POST /api/auth/login` en `setup()`.
- **Carga:** 50 VUs simultaneos (ramp-up 5s, sostenida 30s).
- **Umbral:** `http_req_failed rate<0.01` (falla la corrida si >= 1% de errores).

## Requisitos

- k6 v2.1.0 o superior.
- Stack arriba con TLS:
  `docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d`
  (el endpoint de prueba es `https://localhost:8443`).
- Backend con las migraciones finales (Flyway V5: procedimientos almacenados).

## Credenciales (nunca en texto plano)

El script lee las credenciales del admin desde variables de entorno; si faltan,
aborta con un error claro:

- `K6_ADMIN_EMAIL` — correo del usuario admin real del sistema.
- `K6_ADMIN_PASSWORD` — contrasena del usuario admin real del sistema.
- `BASE_URL` — opcional, default `https://localhost:8443`.

## Estructura de archivo de resultados

Cada corrida se guarda con `k6 --out json=...` (NDJSON crudo) en
`docs/mediciones/perf/` con el siguiente esquema de nombre:

```
k6-<YYYYMMDDTHHMMSS>-<entorno>-<version>-<caliente|frio>-<NN>.json
```

- `YYYYMMDDTHHMMSS` — fecha/hora ISO 8601 compacta (inicio de la corrida).
- `entorno` — `local-tls` (docker compose + TLS 1.3, https://localhost:8443).
- `version` — version del sistema: `git describe --tags --abbrev=0`
  (actual: `v0.9.0-rc`).
- `NN` — numero de corrida, `01` a `05`.

Ejemplo:

```
k6-20260817T153000-local-tls-v0.9.0-rc-caliente-01.json
k6-20260817T153000-local-tls-v0.9.0-rc-frio-01.json
```

Nota: los archivos historicos `k6-run1..3-{caliente,frio}.json` del commit
d7ae051 conservan su nombre original (evidencia previa, no se renombran).

## Como correr las corridas oficiales

### Preparacion (una vez)

```powershell
# Stack con TLS
docker compose -f docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d

# Credenciales reales del admin (nunca se escriben en el repo)
$env:K6_ADMIN_EMAIL = "<correo-admin>"
$env:K6_ADMIN_PASSWORD = "<contrasena-admin>"

# Version del sistema (para el nombre del archivo)
git describe --tags --abbrev=0
```

### Corridas en caliente (5)

Ejecutar 5 veces seguidas, sin reiniciar nada (el cache de Redis ya esta
poblado por las corridas anteriores):

```powershell
k6 run k6/listado-mascotas.js `
  --out json=docs/mediciones/perf/k6-20260817T153000-local-tls-v0.9.0-rc-caliente-01.json
```

Repetir con `-02`, `-03`, `-04`, `-05` (usar la hora ISO 8601 real del inicio
de cada corrida).

### Corridas en frio (5)

Antes de CADA corrida en frio, reiniciar el backend y Redis para vaciar el
cache (`CACHE_TTL_MS=300000`):

```powershell
docker compose -f docker-compose.yml -f docker-compose.tls.yml restart backend redis
```

Luego:

```powershell
k6 run k6/listado-mascotas.js `
  --out json=docs/mediciones/perf/k6-20260817T153000-local-tls-v0.9.0-rc-frio-01.json
```

Repetir el `restart` + corrida para `-02` ... `-05`.

## Analisis posterior

```powershell
python scripts/perf-analysis.py "docs/mediciones/perf/k6-*-local-tls-v0.9.0-rc-{caliente,frio}-0*.json" `
  --report docs/mediciones/perf/REPORT.md
```

Genera el reporte estadistico (p50/p90/p95/p99, media, DE, IC95%, throughput,
Wilcoxon pareado, tamano de efecto) y el grafico vectorial SVG.
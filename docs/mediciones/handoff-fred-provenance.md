# Handoff de provenance (F19) — bloques de Fred

> **Para Zaida (Z12)**: insumos de provenance para consolidar en
> `DATA-PROVENANCE.md`. NO toco ese archivo. Cada bloque documenta la cadena
> **raw → script/comando → tabla/figura → commit**, con el commit de esta
> rama marcado **pendiente** hasta que Fred lo haga real.
> Fecha: 2026-08-18. Rama: `fred/f17-f19-fair-software-provenance`.

---

## Bloque 1 — Rendimiento con k6 (F06–F07)

**Cadena completa:**

| Etapa | Archivo(s) real(es) | Notas |
|---|---|---|
| RAW | `docs/mediciones/perf/k6-20260817T005446-local-tls-v0.9.0-rc-caliente-01.json` … `...caliente-05.json` (5) y `...frio-01.json` … `...frio-05.json` (5) | 10 corridas oficiales, 0 errores, TLS 1.3 `https://localhost:8443` |
| SCRIPT/COMANDO | `k6/listado-mascotas.js` (escenario: 50 VUs, ramp 5s, 30s sostenida); credenciales por `K6_ADMIN_EMAIL`/`K6_ADMIN_PASSWORD`; `scripts/perf-analysis.py` (Wilcoxon pareado, efecto r, SVG) | comando de corrida en `k6/README.md` |
| TABLA/FIGURA | `docs/mediciones/perf/REPORT.md` (p95 caliente 6.8–20.2ms, frío 17.2–22.1ms; 5 pares significativos) + `docs/mediciones/perf/grafico.svg` (40 barras) | metadata: fecha 2026-08-17, commit `ab7043a` origen, Redis DBSIZE=1 |
| COMMIT | `8be89a6` (10 JSON raw), `97ff046` (REPORT.md + grafico.svg) — **ya en main** | PR #6 → `f2e659c` |

**Regenerable**: sí, con `k6/README.md` (pasos exactos) sobre el stack TLS
local. **Cambio de credenciales**: en el commit `a26a1e5`/`f563633` se
eliminaron contraseñas en texto plano.

---

## Bloque 2 — SP y acceso a datos (F01–F05)

**Cadena completa:**

| Etapa | Archivo(s) real(es) | Notas |
|---|---|---|
| RAW (SQL) | `db/procs/fn_resumen_mascotas_por_especie.sql`, `fn_historial_clinico_mascota.sql`, `fn_reporte_dashboard.sql`, `fn_siguiente_numero_ficha.sql`, `sp_actualizar_estado_citas_masivas.sql`, `sp_registrar_consulta_validada.sql`, `zz_grants_biopet_app.sql` | 6 rutinas + grants F05 |
| SCRIPT/COMANDO | migración Flyway `Backend/src/main/resources/db/migration/V5__procedimientos_biopet.sql`; invocación JPA `ProcedimientoBiopetRepository.java` (F02); `scripts/audit-sql-dynamic.sh` (CI) | tests Testcontainers: `ProcedimientosBiopetIntegrationTest`, `BiopetAppRolMinimoPrivilegiosIntegrationTest`, `ResumenEspeciesIntegrationTest` |
| TABLA/FIGURA | `docs/basedatos/CATALOGOSP.md` (catálogo único) + matriz `docs/trazabilidad/matriz.csv` (REQ-F-021, REQ-NF-013 con `tipo_acceso=SP`) | handoff F13: `docs/trazabilidad/handoff-fred-req-sp.md` |
| COMMIT | `7d5fb57` (F05 deploy SP), `3520ca8` (fix entrypoint) — **ya en main** (PR #5 → `d83ad52`) | ADR: `docs/adr/DRAFT-ADR-006-handoff-fred.md` |

**Regenerable**: sí (CI `sql-audit` + tests de integración + scripts de
validación).

---

## Bloque 3 — Despliegue en Render (F08–F11)

**Cadena completa:**

| Etapa | Archivo(s) real(es) | Notas |
|---|---|---|
| RAW (evidencia ejecutada) | `docs/despliegue/ejemplo-backup-20260817.sql` (dump real pg_dump, 64,762 bytes, 6 tablas) | prueba real de restauración 2026-08-17: BD limpia, admin verificado, `fn_siguiente_numero_ficha` → `RST-000001` |
| SCRIPT/COMANDO | `render.yaml` (Blueprint IaC), `docker-compose.prod.yml` + `Caddyfile` (alternativa VPS), comandos `pg_dump`/`pg_restore` en `docs/despliegue/BACKUP.md` | credenciales reales usadas: `.env` local (gitignored) |
| TABLA/FIGURA | `docs/despliegue/DEPLOYMENT.md`, `RUNBOOK.md`, `BACKUP.md`, `nginx-render.conf` | healthcheck `GET /actuator/health` |
| COMMIT | `f01b35c` (F08-F11), `3b6d893` (fix CI .env.example) — **ya en main** (PR #8 → `112b5c0`) | ADR: `docs/adr/DRAFT-ADR-007-handoff-fred.md` |

**Pendiente real (no inventar)**: URL de Render `https://biopet-backend.onrender.com`
la conecta el dueño del repo; la verificación `curl -I .../actuator/health`
(Paso 7) y su resultado se agregarán a DEPLOYMENT.md cuando exista — marcado
"pendiente de publicación" en `docs/checklists/fair.md` (A1).

---

## Bloque 4 — Entorno y versiones (F16)

**Cadena completa:**

| Etapa | Archivo(s) real(es) | Notas |
|---|---|---|
| RAW | herramientas reales del equipo (Docker 29.6.1, Compose v5.3.0, JDK 21.0.11 Temurin, Node 24.18.0, Angular CLI 17.3.17, k6 v2.1.0, Python 3.12.10, Maven 3.9.16, SOs contenedores Alpine v3.19–v3.24 / Ubuntu 24.04.4 LTS) | sin valores sensibles |
| SCRIPT/COMANDO | `scripts/generar-versions.ps1` (detecta JDK 21 automáticamente) | comando compartido con Jaime (J11) |
| TABLA/FIGURA | `docs/entorno/versions.txt` | fecha ISO 8601 |
| COMMIT | `37783cc` — **ya en main** (PR #7 → `f2e659c`) | — |

**Regenerable**: sí, con el script.

---

## Resumen de commits ya en `main` (bloques de Fred)

| Commit | Contenido | PR |
|---|---|---|
| `f563633`/`eccbff1`/`4f24b9c`/`8be89a6`/`97ff046` | k6 + perf (F06-F07) | #6 → `f2e659c` |
| `7d5fb57`/`3520ca8` | SP + acceso a datos (F01-F05) | #5 → `d83ad52` |
| `37783cc` | versions.txt (F16) | #7 |
| `f01b35c`/`3b6d893` | despliegue Render (F08-F11) | #8 → `112b5c0` |
| `3f427ab` | ADR drafts + mapa req-SP (F12-F13) | #9 → `3c9d675` |

**Commit de esta rama**: PENDIENTE — se completa al cerrar
`fred/f17-f19-fair-software-provenance` (fair.md + PAQUETE-V1.0.0.md + este
handoff).

## Qué falta en provenance (para Zaida)

- [ ] Consolidar estas cadenas en `DATA-PROVENANCE.md` (Z12)
- [ ] Agregar URL real de Render + resultado del `curl -I` cuando exista (Bl 3)
- [ ] Agregar DOI de Zenodo cuando se asigne (fair.md F1)
- [ ] Commits de otros integrantes (Jaime/Zaida) — fuera de este handoff
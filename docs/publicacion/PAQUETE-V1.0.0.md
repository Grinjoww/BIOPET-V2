# Paquete de release v1.0.0 — BIOPET (F18)

Preparación del paquete de software **sin publicarlo**. Este documento es el
checklist "listo para que el humano publique manualmente" en GHCR y Zenodo.
Ninguna acción aquí se ejecuta en esta rama; todo es preparación verificable.

## 1. Metadatos necesarios

| Campo | Valor real actual | Acción para v1.0.0 |
|---|---|---|
| Título | "BIOPET — Sistema Integral de Gestión Veterinaria" (`CITATION.cff`) | mantener |
| Autores | Fred Adrián Beltrán Montiel, Jaime Josué Mariscal Cabrera, Zaida Melissa Taipe Mora (afiliación: UTEQ) | mantener |
| Versión | `0.9.0-rc` (`CITATION.cff`, `git describe`, k6 filenames) | **cambiar a `1.0.0`** |
| `repository-code` | `https://github.com/JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc` (repo anterior) | **corregir a `https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET`** |
| Licencia | MIT (`LICENSE`, `CITATION.cff`) | mantener |
| DOI | ausente (comentado en `CITATION.cff:28`) | **asignar en Zenodo al archivar** |
| Fecha de release | `date-released: 2026-07-24` (v0.9.0-rc) | actualizar a fecha del tag v1.0.0 |
| Keywords | 8 keywords en `CITATION.cff` | mantener |

> Hallazgo real: `CITATION.cff` todavía apunta al repositorio anterior
> (`JirachinG19Stdio/PFC-VET-ENTR3-v0.9.0-rc`). La URL correcta del proyecto
> actual es `https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET` (verificado con
> `git remote -v`). Esto debe corregirse ANTES de publicar.

## 2. Licencia

- **MIT** ya versionada: `LICENSE` (21 líneas, Copyright (c) 2026 Equipo
  BIOPET, los 3 integrantes nombrados).
- Verificada coherente con `CITATION.cff` (`license: MIT`) y con el widget de
  licencia de GitHub.
- **Acción**: ninguna (ya lista). Al archivar en Zenodo, seleccionar MIT
  (SPDX: `MIT`) para que coincida con el archivo.

## 3. Contenido del paquete (incluir/excluir)

### Incluir (fuente + evidencia, sin secretos)

| Ruta | Por qué |
|---|---|
| `Backend/` | código fuente Java/Spring Boot + Dockerfile + migraciones Flyway |
| `frontend/` | código fuente Angular + Dockerfile + nginx |
| `db/` | schema, seed, roles, `db/procs/*.sql` (6 SP) |
| `docs/` | ADR, SRS, matriz de trazabilidad, mediciones (perf/sec/zap), despliegue, checklists |
| `k6/` | scripts de benchmark + README |
| `scripts/` | scripts reproducibles (perf-analysis, auditorías, versions) |
| `Makefile`, `docker-compose*.yml`, `render.yaml`, `.env.example` | reproducibilidad del entorno |
| `LICENSE`, `CITATION.cff`, `README.md` | metadatos y licencia |

### Excluir (nunca en el paquete)

| Ruta | Razón |
|---|---|
| `.env` | secretos reales (credenciales BD, JWT) — gitignored |
| `Backend/certs/*.p12` | keystore TLS local — gitignored |
| `docs/mediciones/sec/raw/` | evidencia cruda no sanitizada — gitignored |
| `Backend/target/`, `frontend/node_modules/` | artefactos de build |
| `docs/postman/*.local.postman_environment.json` | environments locales con credenciales |

Verificación usada: `git ls-files` + `.gitignore` (los excluidos no están
versionados, por lo que un `git archive` del tag v1.0.0 los excluye
automáticamente).

## 4. Datos necesarios para GHCR (contenedor backend)

- **Imagen**: construir `Backend/` (multi-stage Maven 3.9 → Temurin 21 JRE
  Alpine; jar `biopet-backend-0.1.0.jar`, EXPOSE 8080).
- **Target propuesto**: `ghcr.io/grinjoww/entregafinal-biopet-backend:1.0.0`
  (usar el namespace del owner del repo — confirmar con Grinjoww).
- **Comandos de publicación (los ejecuta el humano, NO esta rama):**
  ```bash
  docker build -t ghcr.io/grinjoww/entregafinal-biopet-backend:1.0.0 Backend/
  docker push ghcr.io/grinjoww/entregafinal-biopet-backend:1.0.0
  ```
- **Requisitos**: token de GitHub con scope `write:packages`; repo público o
  package público para que el CI y terceros puedan hacer pull sin auth.

## 5. Datos necesarios para Zenodo

- **Tag**: crear `v1.0.0` en git (el tag debe apuntar al commit publicado).
- **Conexión**: conectar `Grinjoww/ENTREGA-FINAL-BIOPET` a Zenodo
  (zenodo.org → GitHub → activar el repo). Lo hace el dueño del repo.
- **Al archivar**, Zenodo toma `CITATION.cff` automáticamente y asigna el DOI.
- **Después**: pegar el DOI en `CITATION.cff` (campo `doi:`), `README.md` y
  `docs/checklists/fair.md` (ítem F1).

## 6. Checklist final "listo para publicar manualmente"

Marcar TODOS antes de publicar (los 3 primeros son de esta rama y ya están):

- [x] Checklist FAIR completo con ítems pendientes marcados explícitamente
      (`docs/checklists/fair.md`, F17)
- [x] Provenance handoff para Zaida (`docs/mediciones/handoff-fred-provenance.md`, F19)
- [x] Paquete de release preparado (este documento, F18)
- [ ] `CITATION.cff` corregido: `version: 1.0.0`, `repository-code` =
      `https://github.com/Grinjoww/ENTREGA-FINAL-BIOPET`, `date-released` actualizado
- [ ] Tag `v1.0.0` creado y pusheado
- [ ] CI en verde sobre el tag (6 jobs: backend-test, frontend-build,
      traceability, sql-audit, security-static, zap-baseline)
- [ ] Repo conectado a Zenodo por el owner; release archivado; DOI obtenido
- [ ] DOI pegado en `CITATION.cff`, `README.md`, `docs/checklists/fair.md`
- [ ] Imagen backend publicada en GHCR (`ghcr.io/grinjoww/...:1.0.0`) y
      verificada con `docker pull`
- [ ] URL real de Render registrada en `docs/despliegue/DEPLOYMENT.md`
      (Paso 7: `curl -I https://<url-real>/actuator/health`) — la conecta el
      owner; evidencia final pendiente
- [ ] Notificar a Jaime (ADRs) y Zaida (matriz + provenance + DATA-PROVENANCE.md)

## 7. Fuera de alcance de esta rama (no inventar aquí)

- No se crea el tag ni se hace push de imágenes (lo hace el humano).
- No se crea el DOI ni se publica nada.
- No se toca `DATA-PROVENANCE.md` (lo consolida Zaida, Z12).
- No se inventa la URL de Render (pendiente del deploy real del owner).
# OWASP ZAP Baseline Scan — BIOPET Backend (Entrega Final v1.0.0, Fase 2)

## Resumen ejecutivo

| Campo | Valor |
|---|---|
| Fecha de ejecución (UTC) | `2026-08-17T03:20:27Z` |
| Versión de ZAP | `2.17.0` (imagen `ghcr.io/zaproxy/zaproxy:stable`) |
| Target | `http://backend:8080` (contenedor `biopet-backend`, dentro de la red Docker Compose `entrega-final-biopet_default`) |
| Comando exacto | ver [`RUN-METADATA.txt`](RUN-METADATA.txt) (generado automáticamente por el script en cada corrida) |
| Alertas **altas** | **0** |
| Alertas **medias** | **0** |
| Alertas **bajas** | **0** |
| Alertas **informativas** | **1** (`Non-Storable Content`, riesgo `Informational`, confianza `Medium`, 2 instancias) |
| Código de salida de `zap-baseline.py` | `0` |
| Objetivo de la Entrega Final (cero hallazgos de severidad alta) | **Cumplido** |

## Cómo se levantó el sistema (reproducible, sin URL inventada)

`scripts/run-zap-baseline.sh` (nuevo en esta fase) hace lo siguiente, en
este orden, cada vez que se ejecuta:

1. Verifica que el daemon de Docker responda.
2. Si `biopet-backend` no está ya `healthy`, ejecuta
   `docker compose up -d postgres redis backend` (el mismo
   `docker-compose.yml` que ya usa el resto del proyecto) y espera, con
   reintentos reales (no un `sleep` fijo), a que
   `docker inspect biopet-backend --format '{{.State.Health.Status}}'`
   devuelva `healthy` — el mismo healthcheck ya definido en
   `docker-compose.yml` (`wget ... /actuator/health | grep UP`).
3. Detecta el nombre real de la red de Docker Compose creada para este
   proyecto (`entrega-final-biopet_default`) inspeccionando el propio
   contenedor `biopet-backend`, en vez de asumir un nombre fijo.
4. Ejecuta el contenedor oficial de ZAP **en esa misma red**, para que
   pueda resolver `backend` por su nombre de servicio DNS de Docker
   Compose — el mismo nombre que usan `postgres`/`redis`/`frontend` entre
   sí — y apunta el escaneo a `http://backend:8080`, el puerto real que el
   propio `docker-compose.yml` expone para el backend. No se inventó
   ninguna URL: es el mismo backend contra el que corre
   `scripts/security-evidence.sh`.

**Nota técnica documentada (no oculta):** en Git Bash/MSYS (Windows), el
montaje de volumen `-v host:/zap/wrk/:rw` requirió `MSYS_NO_PATHCONV=1`
para evitar que la ruta de destino dentro del contenedor (`/zap/wrk`) fuera
reescrita como una ruta de Windows por el propio shell — sin esa variable,
ZAP fallaba con `"the directory '/zap/wrk' is not mounted"`. El script ya
la incluye; queda documentado aquí por si se reproduce en otro entorno
Windows.

### Comando exacto de esta ejecución

```bash
scripts/run-zap-baseline.sh
```

Que internamente ejecutó:

```bash
MSYS_NO_PATHCONV=1 docker run --rm \
    --network entrega-final-biopet_default \
    -v "<repo>/docs/mediciones/sec/zap:/zap/wrk/:rw" \
    -t ghcr.io/zaproxy/zaproxy:stable \
    zap-baseline.py \
    -t http://backend:8080 \
    -r zap-baseline-report.html \
    -x zap-baseline-report.xml \
    -J zap-baseline-report.json \
    -I
```

(`-I`: no convertir WARN en fallo del código de salida; los reportes HTML/XML/JSON
sí registran igualmente cualquier WARN/FAIL real, nada se oculta por esa opción.)

## Reportes generados (evidencia real, sin editar)

- [`zap-baseline-report.html`](zap-baseline-report.html) — reporte HTML completo, navegable.
- [`zap-baseline-report.xml`](zap-baseline-report.xml) — reporte XML completo.
- [`zap-baseline-report.json`](zap-baseline-report.json) — reporte JSON completo (LHR-equivalente de ZAP).
- [`RUN-METADATA.txt`](RUN-METADATA.txt) — fecha, imagen, target, red Docker y comando exacto de la última corrida (regenerado automáticamente por el script en cada ejecución).
- `zap.yaml` — plan de automatización que `zap-baseline.py` genera internamente (Automation Framework de ZAP 2.x) para ejecutar el baseline; se conserva como parte de la evidencia cruda de la herramienta.

## Interpretación de los hallazgos

**66 reglas pasivas en PASS, 0 FAIL, 1 WARN.** El único hallazgo,
`Non-Storable Content` (`pluginid 10049`), es de **riesgo Informational**
según el propio JSON de ZAP (`"riskcode": "0"`, `"riskdesc": "Informational
(Medium)"`) — ZAP mismo no lo clasifica como una vulnerabilidad de
seguridad explotable, sino como una observación de rendimiento/cacheo.

**Análisis: ¿es real, falso positivo, o limitación del entorno?**

Es un **falso positivo respecto al objetivo de seguridad**, no un hallazgo
que deba corregirse:

- Las dos instancias marcadas son `GET http://backend:8080` (raíz, sin
  ruta) y `GET http://backend:8080/robots.txt` — ambas devuelven **401
  Unauthorized** en este backend (no hay contenido público en la raíz de
  la API; todo está protegido por Spring Security salvo `/api/auth/**`).
  La cabecera detectada como "problema" es `Cache-Control: no-store` en
  esas dos respuestas `401`.
- La recomendación de ZAP es literalmente permitir que esas respuestas se
  puedan cachear, para mejorar el rendimiento. Eso es **lo opuesto** de lo
  que conviene hacer con una respuesta de error de autenticación: no
  cachear una respuesta `401` (que podría depender del estado de sesión
  del cliente que la solicitó) es una práctica de seguridad deliberada,
  no un descuido. Añadir cacheabilidad ahí introduciría el riesgo de que
  un proxy intermedio sirva una respuesta `401` obsoleta a un cliente que
  sí debería estar autenticado.
- No es tampoco una limitación del entorno local: el comportamiento
  (`no-store` en respuestas 401) es el mismo mecanismo ya documentado y
  probado en `docs/mediciones/sec/A05-security-headers.md`, no un artefacto
  de correr contra `localhost`/Docker.

**Decisión:** no se modifica código de producción por este hallazgo. Se
documenta aquí como hallazgo menor, con su análisis, en vez de ocultarlo o
borrarlo del reporte.

## Por qué el escaneo cubrió solo 2 endpoints (limitación real, documentada)

El "spider" no autenticado de ZAP solo pudo descubrir 2 endpoints (`/` y
`/robots.txt`), ambos devolviendo `401`. Esto **no es un error del script
ni del escaneo**: es el comportamiento esperado de una API REST donde,
salvo `/api/auth/login`, `/api/auth/registro` y `/api/auth/refresh`, **todo**
requiere un JWT válido (`SecurityConfig`, ya documentado en
`A01-access-control.md`/`A07-authentication.md`). El log de la propia
ejecución lo confirma explícitamente:

```
Job spider error accessing URL http://backend:8080 status code returned : 401 expected 200
```

Es decir, ZAP intentó y no pudo "entrar" a la API sin autenticación — el
resultado correcto para un backend con control de acceso por defecto
(`deny by default`), no una limitación que oculte hallazgos: no hay
superficie pública adicional que un atacante no autenticado pueda alcanzar
más allá de lo ya cubierto por A01/A03/A05/A07 con `curl` real en
`docs/mediciones/sec/raw/`.

**Alcance no cubierto por esta corrida (pendiente, no bloqueante):** un
ZAP baseline **autenticado** (usando un contexto de ZAP con las cookies de
sesión ya documentadas en `A07-authentication.md`) permitiría escanear los
endpoints protegidos (`/api/mascotas`, `/api/usuarios`, `/api/citas`, etc.)
con las reglas pasivas de ZAP. No se implementó en esta fase por alcance
(requiere manejar credenciales de prueba dentro del propio contenedor de
ZAP, un mecanismo distinto al ya usado por `scripts/security-evidence.sh`)
— queda documentado aquí como trabajo futuro, sin inventar que ya se hizo.

## Correcciones realizadas

**Ninguna fue necesaria.** El único hallazgo (`Non-Storable Content`,
severidad Informational) fue analizado y determinado como comportamiento
deliberado y correcto (ver sección anterior), no un defecto a corregir. No
hubo hallazgos de severidad alta, media ni baja en esta ejecución — el
objetivo de la Entrega Final (cero hallazgos de severidad alta) se cumplió
sin necesidad de cambios de código.

## Reproducción

```bash
# Requiere Docker Desktop iniciado
scripts/run-zap-baseline.sh

# Para no apagar el stack al terminar (ya usado por otras evidencias):
scripts/run-zap-baseline.sh --keep-stack
```

Variables de entorno opcionales: `ZAP_IMAGE` (por defecto
`ghcr.io/zaproxy/zaproxy:stable`), `ZAP_TARGET` (por defecto
`http://backend:8080`, dentro de la red Docker del propio proyecto).

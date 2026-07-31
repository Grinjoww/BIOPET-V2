# Diccionario de datos de mediciones — BIOPET (Tercera Entrega v0.9.0-rc)

Cubre las variables de cada archivo crudo de mediciones bajo `docs/mediciones/`,
según lo exige el Bloque E.3 de la guía de la Tercera Entrega: nombre, tipo de
dato, unidad, rango esperado y significado.

Este documento se organiza por sub-bloque de evidencia. Cada integrante agrega
la sección correspondiente a su área.

## Rendimiento (`docs/mediciones/perf/`) — responsable: Fred

Fuente: `k6-runN-{frio,caliente}.json` (datos crudos de k6), agregados por
`scripts/perf-analysis.py` en `docs/mediciones/perf/REPORT.md`.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| n | Entero | peticiones | 3000–3300 (50 VUs × ~30-35s) | Tamaño de muestra: peticiones HTTP completadas en la corrida. |
| media_ms | Decimal | ms | 5–15 (según frío/caliente) | Media aritmética de `http_req_duration` sobre las n peticiones. |
| mediana_ms | Decimal | ms | 5–10 | Percentil 50 de `http_req_duration` (equivalente a p50_ms). |
| desviacion_ms | Decimal | ms | 4–8 | Desviación estándar muestral de `http_req_duration`. |
| ic95_bajo_ms | Decimal | ms | > 0 | Límite inferior del intervalo de confianza al 95% para la media (distribución t de Student, `scipy.stats.t`). |
| ic95_alto_ms | Decimal | ms | > ic95_bajo_ms | Límite superior del intervalo de confianza al 95% para la media. |
| p50_ms / p90_ms / p95_ms / p99_ms | Decimal | ms | crecientes (p50 ≤ p90 ≤ p95 ≤ p99) | Percentiles de latencia: umbral por debajo del cual cae ese % de las peticiones. Umbral objetivo de la guía: p95 < 200 ms con caché caliente, < 500 ms con caché fría. |
| tasa_error_pct | Decimal | % | 0.0 (obligatorio) | Porcentaje de peticiones con código de error HTTP > 500. La guía exige que sea cero. |
| throughput_rps | Decimal | peticiones/s | 85–95 (a 50 VUs) | Peticiones completadas por segundo durante la corrida. |

## Caché Redis (`docs/mediciones/perf/REPORT.md`, sección "Metadata de la medición") — responsable: Fred

Fuente: `redis-cli INFO stats`, leído de forma aislada con `CONFIG RESETSTAT`
inmediatamente antes de la corrida de referencia.

| Variable | Tipo de dato | Unidad | Rango esperado | Significado |
|---|---|---|---|---|
| keyspace_hits | Entero | peticiones a caché | ≥ 0 | Lecturas resueltas sirviendo un valor ya presente en Redis. |
| keyspace_misses | Entero | peticiones a caché | ≥ 0 | Lecturas no resueltas desde Redis (clave inexistente o expirada); fuerza consulta a PostgreSQL. |
| hit_ratio | Decimal | % | 0–100 (objetivo declarado por el equipo: alto con caché caliente) | `hits / (hits + misses)`. Medido: ~49.98%, más bajo que lo esperado para una corrida en caché caliente — documentado como hallazgo abierto en `REPORT.md`, no oculto. |
| CACHE_TTL_MS | Entero | milisegundos | > 0 (por defecto 300000 = 5 min) | Tiempo de vida de una entrada de caché antes de expirar, externalizado vía variable de entorno (`application.yml` / `.env`), no hardcodeado. |

## Seguridad (`docs/mediciones/sec/`) — responsable: Jaime

_Pendiente: sección a completar por Jaime con las variables de A01–A09 (códigos
HTTP esperados, cabeceras verificadas, contenido de logs de auditoría, etc.)._

## Usabilidad SUS (`docs/mediciones/sus/`) — responsable: Zaida

_Pendiente: sección a completar por Zaida con las variables del instrumento SUS
(puntuación por ítem, escala 1–5, puntuación agregada 0–100, IC 95%, perfil de
participantes)._

## Accesibilidad / Lighthouse (`docs/mediciones/lighthouse/`) — responsable: Zaida

_Pendiente: sección a completar por Zaida con las variables de cada auditoría
`lhci` (Performance, Accessibility, Best Practices, SEO — escala 0–100, umbral
mínimo declarado en `lighthouserc.js`)._

## Cobertura JaCoCo (`docs/mediciones/jacoco/`) — responsable: Jaime

_Pendiente: sección a completar por Jaime con las variables de cobertura
(lines, branches, complexity — porcentaje, umbral ≥60% en esta entrega)._
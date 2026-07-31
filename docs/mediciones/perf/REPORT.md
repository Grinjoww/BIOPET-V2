# Reporte de rendimiento — BIOPET (k6)

Metodo de intervalo de confianza: distribucion t de Student (scipy.stats.t), apropiada para el tamano de muestra de estas corridas.

| Corrida | n | Media (ms) | Mediana (ms) | DE (ms) | IC95% (ms) | p50 | p90 | p95 | p99 | Error (%) | Throughput (req/s) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| k6-run1-frio.json | 3204 | 10.68 | 8.78 | 7.56 | [10.42, 10.94] | 8.78 | 16.71 | 20.97 | 32.41 | 0.0 | 91.47 |
| k6-run2-frio.json | 3216 | 8.09 | 7.39 | 5.81 | [7.89, 8.29] | 7.39 | 10.05 | 11.87 | 22.97 | 0.0 | 91.87 |
| k6-run3-frio.json | 3226 | 6.97 | 6.26 | 5.73 | [6.77, 7.16] | 6.26 | 8.5 | 10.31 | 22.16 | 0.0 | 92.08 |
| k6-run1-caliente.json | 3231 | 6.01 | 5.48 | 5.9 | [5.8, 6.21] | 5.48 | 7.3 | 8.82 | 16.28 | 0.0 | 92.17 |
| k6-run2-caliente.json | 3226 | 6.87 | 5.75 | 8.0 | [6.59, 7.14] | 5.75 | 9.45 | 12.04 | 28.42 | 0.0 | 92.17 |
| k6-run3-caliente.json | 3236 | 5.53 | 5.13 | 4.77 | [5.36, 5.69] | 5.13 | 6.76 | 7.82 | 12.59 | 0.0 | 92.46 |

## Metadata de la medición

- **Rango de fecha/hora (ISO 8601):** `2026-07-30T19:54:37-05:00` – `2026-07-30T20:11:42-05:00`
- **Commit:** `eddc354`
- **Herramientas:**
  - k6 v2.1.0 (commit 83a87a41e2, go1.26.4, windows/amd64)
- **Hit ratio de Redis:** `3223 / (3223 + 3227) ≈ 49.98%`
  (medido con `CONFIG RESETSTAT` inmediatamente antes de la corrida `k6-run2-caliente.json`, aislado de actividad previa del contenedor)
  - ⚠️ Nota: el hit ratio (~50%) es más bajo de lo esperado para una corrida en caché caliente. Es consistente entre la medición sin aislar y la aislada, por lo que no es un artefacto de acumulación de stats — sugiere que el TTL configurado expira claves a un ritmo comparable al de las peticiones, o que la clave de caché varía entre iteraciones del script k6 (p. ej. por parámetros de paginación/orden). Queda como hallazgo a investigar fuera del alcance de esta entrega.

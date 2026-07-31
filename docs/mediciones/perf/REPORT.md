# Reporte de rendimiento — BIOPET (k6)

Metodo de intervalo de confianza: distribucion t de Student (scipy.stats.t), apropiada para el tamano de muestra de estas corridas.

| Corrida | n | Media (ms) | Mediana (ms) | DE (ms) | IC95% (ms) | p50 | p90 | p95 | p99 | Error (%) | Throughput (req/s) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| k6-run1-caliente.json | 3233 | 6.38 | 5.83 | 5.89 | [6.18, 6.58] | 5.83 | 7.92 | 9.41 | 14.41 | 0.0 | 92.14 |
| k6-run1-frio.json | 3197 | 12.14 | 9.39 | 9.57 | [11.81, 12.47] | 9.39 | 20.9 | 26.45 | 44.66 | 0.0 | 91.21 |
| k6-run2-caliente.json | 3211 | 9.09 | 6.39 | 11.29 | [8.7, 9.48] | 6.39 | 13.62 | 21.09 | 59.77 | 0.0 | 91.63 |
| k6-run2-frio.json | 3191 | 11.89 | 9.37 | 9.86 | [11.55, 12.24] | 9.37 | 18.66 | 27.35 | 49.01 | 0.0 | 91.02 |
| k6-run3-caliente.json | 3236 | 6.45 | 5.73 | 5.88 | [6.25, 6.66] | 5.73 | 8.07 | 10.62 | 21.08 | 0.0 | 92.34 |
| k6-run3-frio.json | 3226 | 7.83 | 6.84 | 6.03 | [7.62, 8.04] | 6.84 | 11.29 | 13.74 | 21.81 | 0.0 | 92.05 |

## Metadata de la medición

- **Rango de fecha/hora (ISO 8601):** corridas ejecutadas el 2026-07-30, culminando
  aproximadamente a las `2026-07-30T22:06:56-05:00`.
- **Commit:** `a6a8905`
- **Herramientas:**
  - k6 v2.1.0 (commit 83a87a41e2, go1.26.4, windows/amd64)
- **Protocolo:** HTTPS con TLS 1.3 real (`https://localhost:8443`), a diferencia de la
  primera medición (HTTP plano), corregido tras habilitarse el módulo de seguridad de Jaime.
- **Hit ratio de Redis (caché `mascotas`):** verificado de forma aislada, no con
  `keyspace_hits`/`keyspace_misses` globales de Redis (que también cuentan lecturas
  ajenas al caché, como la verificación de blacklist de JWT en cada petición autenticada).
  Con `redis-cli DBSIZE` se confirmó una única clave (`mascotas::admin@biopet.ec-0-10-UNSORTED`)
  estable durante una corrida completa de 50 VUs / 30s, sin evicción por memoria
  (`maxmemory-policy: noeviction`) ni expiración prematura (TTL decreciente de forma
  consistente). El hit ratio real del endpoint cacheado es efectivamente cercano al
  100% tras la primera petición de calentamiento.
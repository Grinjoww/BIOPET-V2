# Comparación de rendimiento con/sin caché — API externa

Endpoint: GET /api/externa/especies?especie=dog

| Escenario | Tiempo total (ms) |
|---|---|
| Sin caché (cache miss → API Ninjas) | 1363.6151 |
| Con caché (cache hit → Redis) | 21.4137 |

Mejora: ~64x más rápido con caché.

Verificado con:
- `docker exec -it biopet-redis redis-cli DEL "external-api:animal:dog"` (forzar cache miss)
- `Measure-Command { Invoke-RestMethod ... }` (PowerShell) x2
- `docker exec -it biopet-redis redis-cli KEYS "external-api:*"` confirma la clave activa en caché
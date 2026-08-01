# A02 — Cryptographic Failures (TLS y cookies)

## Control implementado

- HTTPS nativo en Spring Boot (perfil `tls`), conector adicional HTTP 8080
  solo para tráfico interno (`Backend/src/main/resources/application-tls.yml`,
  `Backend/src/main/java/com/biopet/config/TomcatDualConnectorConfig.java`).
- Certificado PKCS12 autofirmado, generado localmente y **nunca versionado**
  (`Backend/certs/`, excluido por `.gitignore`).
- Cookies de sesión con `HttpOnly` + `Secure` + `SameSite=Strict`
  (`Backend/src/main/java/com/biopet/security/JwtCookieService.java`,
  confirmado en `AuthControllerTest.loginExitoso`).

## Evidencia real (reejecutada el 2026-07-31, stack Docker con perfil `tls` ya en ejecución)

### HTTP interno (8080) — sin HSTS

```
curl.exe -i http://localhost:8080/actuator/health
```

```
HTTP/1.1 200
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; object-src 'none'
Referrer-Policy: no-referrer
Content-Type: application/vnd.spring-boot.actuator.v3+json
{"status":"UP"}
```

No hay cabecera `Strict-Transport-Security` en esta respuesta — comportamiento
correcto: HSTS nunca debe emitirse sobre una conexión sin cifrar.

### HTTPS real (8443) — con HSTS

```
curl.exe -sk https://localhost:8443/actuator/health -D - -o /dev/null
```

```
HTTP/1.1 200
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000 ; includeSubDomains ; preload
X-Frame-Options: DENY
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; object-src 'none'
Referrer-Policy: no-referrer
Content-Type: application/vnd.spring-boot.actuator.v3+json
```

### Protocolo y cifrado negociados (`openssl s_client`)

`curl.exe` en Windows usa el backend Schannel y no siempre imprime el
protocolo/cifrado negociado con claridad, así que se usó `openssl s_client`
(ya disponible localmente, sin instalar nada nuevo):

```
echo | openssl s_client -connect localhost:8443 -tls1_3
```

Salida real:

```
verify error:num=18:self-signed certificate
subject=C=EC, ST=Local, L=Local, O=BIOPET, OU=BIOPET Dev, CN=localhost
issuer=C=EC, ST=Local, L=Local, O=BIOPET, OU=BIOPET Dev, CN=localhost
New, TLSv1.3, Cipher is TLS_AES_256_GCM_SHA384
Protocol: TLSv1.3
```

- **Protocolo:** `TLSv1.3`.
- **Cipher:** `TLS_AES_256_GCM_SHA384` — suite AEAD (AES-GCM), una de las
  únicas tres suites que ofrece TLS 1.3 en Java 21 (las tres son AEAD por
  diseño del protocolo).
- **`verify error:num=18:self-signed certificate`**: confirma que el
  certificado es efectivamente autofirmado y no está en ninguna cadena de
  confianza del sistema — exactamente lo esperado para un certificado
  académico/local, nunca válido para producción.

### SAN del certificado servido

```
echo | openssl s_client -connect localhost:8443 -tls1_3 2>/dev/null | openssl x509 -noout -text
```

```
X509v3 Subject Alternative Name:
    DNS:localhost, IP Address:127.0.0.1
Signature Algorithm: sha384WithRSAEncryption
```

Coincide exactamente con el SAN generado por `scripts/generate-dev-keystore.ps1`/`.sh`
(`-ext "SAN=dns:localhost,ip:127.0.0.1"`).

### Cookies seguras

Verificado en `Backend/src/test/java/com/biopet/AuthControllerTest.java::loginExitoso`,
que comprueba en las cabeceras `Set-Cookie` reales de la respuesta:
`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/` (access) y
`Path=/api/auth` (refresh).

## Evidencia automatizada (2026-08-01, commit `136b707`)

La misma verificación (protocolo, cipher, subject/SAN del certificado,
cabeceras HTTP 8080) ahora también se genera de forma automática y
reproducible con `scripts/security-evidence.sh`, guardada en
[`raw/A02-tls.txt`](raw/A02-tls.txt) — resultado idéntico al capturado
manualmente arriba (`Protocol: TLSv1.3`, `Cipher: TLS_AES_256_GCM_SHA384`,
`Subject`/SAN iguales).

## Comandos reproducibles

```bash
# 1. Generar el keystore local (una sola vez; idempotente)
powershell -ExecutionPolicy Bypass -File scripts/generate-dev-keystore.ps1
# o en Linux/macOS:
scripts/generate-dev-keystore.sh

# 2. Levantar el Compose combinado (base + TLS)
docker compose -f docker-compose.yml -f docker-compose.tls.yml up --build -d

# 3. Evidencia HTTP/HTTPS
curl.exe -i http://localhost:8080/actuator/health
curl.exe -vk --tlsv1.3 https://localhost:8443/actuator/health

# 4. Protocolo y cipher exactos
echo | openssl s_client -connect localhost:8443 -tls1_3
```

**No se incluye la contraseña del keystore en este documento.** Su valor por
defecto está documentado únicamente en el propio script
(`scripts/generate-dev-keystore.ps1`/`.sh`) y en `docker-compose.tls.yml`
como un valor de desarrollo/académico, nunca un secreto de producción; puede
sobrescribirse con la variable de entorno `TLS_KEYSTORE_PASSWORD`. **No se
incluye ningún certificado ni clave privada
real** en este documento ni en el repositorio: `Backend/certs/*` está
excluido por `.gitignore` salvo `.gitkeep`.

## Limitaciones

- El certificado es autofirmado y de validez extendida (10 años) porque su
  único propósito es demostración local/académica; no es apto para
  producción bajo ningún escenario.
- No se documenta aquí HSTS preload real ante navegadores (requiere envío a
  la lista de precarga de Chromium), solo la cabecera `preload` emitida por
  el servidor.
- El tráfico interno nginx→backend (dentro de la red de Docker) sigue siendo
  HTTP simple (`http://backend:8080`), sin TLS mutuo; esto es intencional
  para esta fase (ver Fase 7D-1/7D-2) y no se modificó aquí.

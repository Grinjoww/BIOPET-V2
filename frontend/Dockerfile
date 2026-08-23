FROM node:20-alpine AS build
WORKDIR /app
# M-1: se copian package.json Y package-lock.json (antes solo package.json,
# con "npm install") para que la build sea reproducible -"npm ci" instala
# exactamente las versiones fijadas en el lockfile, en vez de resolverlas de
# nuevo en cada build, y falla si package.json y el lockfile quedan
# desincronizados en vez de reescribirlo silenciosamente.
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.25-alpine
# Plantilla (no un .conf final): el proxy /api hacia el backend se resuelve
# en runtime via docker-entrypoint.sh, sustituyendo solo ${BACKEND_HOST} y
# ${BACKEND_PORT} -- necesario porque el hostname del backend es distinto
# en Docker Compose local ("backend") y en Render (inyectado por
# render.yaml desde el servicio real biopet-backend). Ver
# nginx.conf.template para el detalle.
COPY nginx.conf.template /etc/nginx/conf.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
COPY --from=build /app/dist/biopet-frontend/browser /usr/share/nginx/html
EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]

FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
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

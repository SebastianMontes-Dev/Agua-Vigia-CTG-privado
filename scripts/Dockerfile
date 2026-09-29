# Imagen del servicio `sembrador` (ADR-086): Node con los scripts de siembra y los datos geográficos dentro, para que
# `docker compose up` deje la base lista sin instalar nada más. Se construye desde la raíz del repositorio (ver
# docker-compose.yml y .dockerignore) porque necesita data/geoespacial, que vive fuera de scripts/.
FROM node:22-alpine

WORKDIR /app/scripts
COPY scripts/package.json scripts/package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund

COPY data/geoespacial /app/data/geoespacial
COPY scripts ./

USER node
ENTRYPOINT ["node", "sembrador.mjs"]
CMD ["inicial"]

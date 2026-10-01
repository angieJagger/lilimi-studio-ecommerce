FROM node:24-bookworm-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:24-bookworm-slim AS runtime
ENV NODE_ENV=production PORT=4000
WORKDIR /app
COPY --from=build --chown=node:node /app/dist/lilimi-studio-ecommerce ./dist
USER node
EXPOSE 4000
CMD ["node", "dist/server/server.mjs"]

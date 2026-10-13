FROM node:26-trixie-slim AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:26-trixie-slim AS runtime

RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /usr/local/lib/node_modules/npm \
              /usr/local/lib/node_modules/corepack \
              /opt/yarn-v* \
    && rm -f /usr/local/bin/npm \
             /usr/local/bin/npx \
             /usr/local/bin/corepack \
             /usr/local/bin/yarn \
             /usr/local/bin/yarnpkg

ENV NODE_ENV=production PORT=4000
WORKDIR /app
COPY --from=build --chown=node:node /app/dist/lilimi-studio-ecommerce ./dist
USER node
EXPOSE 4000
CMD ["node", "dist/server/server.mjs"]

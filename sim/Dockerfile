FROM node:22-alpine
WORKDIR /sim
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server.js ./
USER node
EXPOSE 9000
CMD ["node", "server.js"]

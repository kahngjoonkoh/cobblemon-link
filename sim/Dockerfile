FROM node:22-alpine
WORKDIR /sim
COPY server.js test.js ./
USER node
EXPOSE 9000
# Cobblemon's Showdown build is mounted at /showdown (assets/showdown, made by tools/build.sh)
ENV SHOWDOWN=/showdown
CMD ["node", "server.js"]

# syntax=docker/dockerfile:1

# ---------- Etapa 1: build ----------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npx ng build --configuration production

# ---------- Etapa 2: runtime ----------
# nginx sirve los estáticos y reenvía /api al backend (ver nginx.conf): así
# environment.prod.ts puede usar un apiBase relativo sin necesitar el dominio del
# backend en tiempo de build.
FROM nginx:alpine
COPY --from=build /app/dist/Front/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

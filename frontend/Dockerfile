FROM node:20-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/index.html ./
COPY frontend/vite.config.js ./
COPY frontend/src ./src
COPY frontend/styles.css ./
RUN npm run build

FROM nginx:1.27-alpine
ENV API_GATEWAY_UPSTREAM=http://api-gateway:8080
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx/default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

# ---- Build stage: compile TypeScript ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json tsconfig.json ./
RUN npm install
COPY src ./src
RUN npm run build

# ---- Serve stage: plain static files via nginx ----
FROM nginx:alpine
COPY index.html /usr/share/nginx/html/index.html
COPY css /usr/share/nginx/html/css
COPY --from=build /app/dist /usr/share/nginx/html/dist
EXPOSE 80

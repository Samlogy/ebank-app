# eBank Frontend — Angular 17

Angular 17 (standalone components) port of the `front-react` eBank frontend, with the same features and Tailwind CSS design: authentication, dashboard, accounts management, transactions, and a WebSocket-based chat assistant widget.

## Development server

```bash
npm install
npm start
```

Navigate to `http://localhost:4200/`. API and WebSocket calls under `/api` and `/ws` are proxied to `http://localhost:8080` (see `proxy.conf.json`), matching the backend gateway used by `front-react`.

## Build

```bash
npm run build
```

Build artifacts are written to `dist/front-angular/browser`.

## Running unit tests

```bash
npm test
```

## Docker

```bash
docker build -t ebank-frontend-angular .
docker run -p 8081:80 ebank-frontend-angular
```

# WebRTC Mesh Room (Spring Boot + React)

This project provides a WebRTC mesh video room (max 6 participants) with a Spring Boot WebSocket signaling server and a React client.

## Local Run

Backend:

```bash
cd backend
gradle bootRun
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in multiple tabs or devices on the same LAN.

## Signaling Protocol

Client -> Server

- `join`: `{ "type": "join", "roomId": "demo", "name": "alice" }`
- `signal`: `{ "type": "signal", "to": "peerId", "data": { "kind": "offer|answer|candidate", ... } }`
- `leave`: `{ "type": "leave" }`

Server -> Client

- `welcome`: `{ "type": "welcome", "clientId": "...", "roomId": "..." }`
- `peers`: `{ "type": "peers", "peers": [{ "id": "...", "name": "..." }] }`
- `peer-joined`: `{ "type": "peer-joined", "peer": { "id": "...", "name": "..." } }`
- `peer-left`: `{ "type": "peer-left", "peerId": "..." }`
- `signal`: `{ "type": "signal", "from": "peerId", "data": { ... } }`

## Architecture Notes (SFU-ready)

- `backend/src/main/java/com/example/videoroom/signaling` keeps signaling concerns isolated.
- The mesh media path is entirely client-side; the server only routes signaling messages.
- To migrate to SFU later, keep the signaling API stable and replace the client mesh logic with SFU join/produce/consume flows.

## Docker

```bash
docker compose up --build
```

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`

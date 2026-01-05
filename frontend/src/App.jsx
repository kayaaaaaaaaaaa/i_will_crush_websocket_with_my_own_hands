import { useEffect, useMemo, useRef, useState } from "react";

const MAX_PARTICIPANTS = 6;
const ICE_SERVERS = [{ urls: "stun:stun.l.google.com:19302" }];

function createPeerState() {
  return { peers: new Map(), names: new Map() };
}

export default function App() {
  const [roomId, setRoomId] = useState("demo");
  const [name, setName] = useState("");
  const [clientId, setClientId] = useState("");
  const [connected, setConnected] = useState(false);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState("");
  const [peerTiles, setPeerTiles] = useState([]);

  const wsRef = useRef(null);
  const localVideoRef = useRef(null);
  const localStreamRef = useRef(null);
  const peerConnectionsRef = useRef(new Map());
  const peerStateRef = useRef(createPeerState());

  const wsUrl = useMemo(() => {
    if (import.meta.env.VITE_SIGNALING_URL) {
      return import.meta.env.VITE_SIGNALING_URL;
    }
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    return `${scheme}://${window.location.hostname}:8081/ws`;
  }, []);

  useEffect(() => {
    return () => {
      leaveRoom();
    };
  }, []);

  useEffect(() => {
    if (localVideoRef.current && localStreamRef.current) {
      localVideoRef.current.srcObject = localStreamRef.current;
    }
  }, [localStreamRef.current]);

  const syncPeers = () => {
    setPeerTiles(Array.from(peerStateRef.current.peers.values()));
  };

  const setPeer = (peerId, stream) => {
    const name = peerStateRef.current.names.get(peerId) || "Guest";
    peerStateRef.current.peers.set(peerId, { id: peerId, name, stream });
    syncPeers();
  };

  const removePeer = (peerId) => {
    peerStateRef.current.peers.delete(peerId);
    peerStateRef.current.names.delete(peerId);
    syncPeers();
  };

  const updatePeerName = (peerId, peerName) => {
    peerStateRef.current.names.set(peerId, peerName || "Guest");
    if (peerStateRef.current.peers.has(peerId)) {
      const existing = peerStateRef.current.peers.get(peerId);
      peerStateRef.current.peers.set(peerId, { ...existing, name: peerName || "Guest" });
      syncPeers();
    }
  };

  const getOrCreatePeerConnection = (peerId) => {
    if (peerConnectionsRef.current.has(peerId)) {
      return peerConnectionsRef.current.get(peerId);
    }

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    if (localStreamRef.current) {
      for (const track of localStreamRef.current.getTracks()) {
        pc.addTrack(track, localStreamRef.current);
      }
    }

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignal(peerId, { kind: "candidate", candidate: event.candidate });
      }
    };

    pc.ontrack = (event) => {
      const [stream] = event.streams;
      if (stream) {
        setPeer(peerId, stream);
      }
    };

    pc.onconnectionstatechange = () => {
      if (pc.connectionState === "failed" || pc.connectionState === "disconnected") {
        cleanupPeer(peerId);
      }
    };

    peerConnectionsRef.current.set(peerId, pc);
    return pc;
  };

  const cleanupPeer = (peerId) => {
    const pc = peerConnectionsRef.current.get(peerId);
    if (pc) {
      pc.close();
      peerConnectionsRef.current.delete(peerId);
    }
    removePeer(peerId);
  };

  const connectWebSocket = () => {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => resolve(ws);
      ws.onerror = (event) => reject(event);
      ws.onclose = () => {
        wsRef.current = null;
        setConnected(false);
        setStatus("idle");
      };
      ws.onmessage = (event) => handleMessage(event.data);
    });
  };

  const send = (payload) => {
    if (!wsRef.current || wsRef.current.readyState !== WebSocket.OPEN) {
      return;
    }
    wsRef.current.send(JSON.stringify(payload));
  };

  const sendSignal = (to, data) => {
    send({ type: "signal", to, data });
  };

  const handleMessage = async (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return;
    }

    switch (msg.type) {
      case "welcome":
        setClientId(msg.clientId);
        setConnected(true);
        setStatus("connected");
        break;
      case "peers":
        if (Array.isArray(msg.peers)) {
          for (const peer of msg.peers) {
            updatePeerName(peer.id, peer.name);
            const pc = getOrCreatePeerConnection(peer.id);
            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);
            sendSignal(peer.id, { kind: "offer", sdp: offer.sdp });
          }
        }
        break;
      case "peer-joined":
        if (msg.peer?.id) {
          updatePeerName(msg.peer.id, msg.peer.name);
          getOrCreatePeerConnection(msg.peer.id);
        }
        break;
      case "peer-left":
        if (msg.peerId) {
          cleanupPeer(msg.peerId);
        }
        break;
      case "signal":
        await handleSignal(msg);
        break;
      case "error":
        setError(msg.message || "Unknown error");
        break;
      default:
        break;
    }
  };

  const handleSignal = async (msg) => {
    const from = msg.from;
    const data = msg.data;
    if (!from || !data) {
      return;
    }

    const pc = getOrCreatePeerConnection(from);

    if (data.kind === "offer") {
      await pc.setRemoteDescription({ type: "offer", sdp: data.sdp });
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      sendSignal(from, { kind: "answer", sdp: answer.sdp });
    } else if (data.kind === "answer") {
      await pc.setRemoteDescription({ type: "answer", sdp: data.sdp });
    } else if (data.kind === "candidate") {
      if (data.candidate) {
        await pc.addIceCandidate(data.candidate);
      }
    }
  };

  const startLocalStream = async () => {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: true,
      audio: true,
    });
    localStreamRef.current = stream;
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = stream;
    }
  };

  const stopLocalStream = () => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
    }
  };

  const joinRoom = async () => {
    setError("");
    if (!roomId.trim() || !name.trim()) {
      setError("Room and name are required.");
      return;
    }
    if (connected) {
      return;
    }
    setStatus("connecting");

    try {
      await startLocalStream();
      const ws = await connectWebSocket();
      send({ type: "join", roomId: roomId.trim(), name: name.trim() });
      ws.onmessage = (event) => handleMessage(event.data);
    } catch (err) {
      setError("Failed to connect signaling server.");
      setStatus("idle");
    }
  };

  const leaveRoom = () => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      send({ type: "leave" });
      wsRef.current.close();
    }

    for (const peerId of peerConnectionsRef.current.keys()) {
      cleanupPeer(peerId);
    }
    peerConnectionsRef.current.clear();
    peerStateRef.current = createPeerState();
    setPeerTiles([]);
    stopLocalStream();
    setConnected(false);
    setClientId("");
    setStatus("idle");
  };

  const participantCount = peerTiles.length + (connected ? 1 : 0);
  const fullRoom = participantCount >= MAX_PARTICIPANTS;

  return (
    <div className="app">
      <header className="header">
        <div className="title">WebRTC Mesh Room</div>
        <div className="status">
          <span className={`status-dot ${connected ? "online" : "offline"}`}></span>
          {connected ? "Connected" : "Offline"}
        </div>
      </header>

      <section className="controls">
        <label>
          Room
          <input
            type="text"
            value={roomId}
            onChange={(event) => setRoomId(event.target.value)}
            placeholder="room-id"
            disabled={connected}
          />
        </label>
        <label>
          Name
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="your name"
            disabled={connected}
          />
        </label>
        <div className="control-buttons">
          <button onClick={joinRoom} disabled={connected || status === "connecting" || fullRoom}>
            {status === "connecting" ? "Connecting..." : "Join"}
          </button>
          <button className="secondary" onClick={leaveRoom} disabled={!connected}>
            Leave
          </button>
        </div>
        <div className="meta">
          <div>Client: {clientId ? clientId.slice(0, 8) : "-"}</div>
          <div>Participants: {participantCount}/{MAX_PARTICIPANTS}</div>
          <div>Signaling: {wsUrl}</div>
        </div>
        {error && <div className="error">{error}</div>}
      </section>

      <section className="grid">
        <div className="tile">
          <video ref={localVideoRef} autoPlay playsInline muted />
          <div className="label">{name || "You"}</div>
        </div>
        {peerTiles.map((peer) => (
          <VideoTile key={peer.id} peer={peer} />
        ))}
      </section>
    </div>
  );
}

function VideoTile({ peer }) {
  const videoRef = useRef(null);

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.srcObject = peer.stream;
    }
  }, [peer.stream]);

  return (
    <div className="tile">
      <video ref={videoRef} autoPlay playsInline />
      <div className="label">{peer.name}</div>
    </div>
  );
}

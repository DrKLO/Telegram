// Canary server for the attack suite. Every attack vector points at
// 127.0.0.1:9999; anything that arrives here — an HTTP request, a WebSocket
// upgrade, a bare TCP connection (preconnect, TURN/TCP) or a UDP datagram
// (STUN) — is a leak. `GET /__hits` returns the log, `GET /__reset` clears it.

import dgram from 'node:dgram';
import http from 'node:http';
import type { Socket } from 'node:net';
import { WebSocketServer } from 'ws';

export const CANARY_PORT = 9999;
// CANARY_HOST=0.0.0.0 exposes it on the LAN for the native demos on real devices.
export const CANARY_HOST = process.env.CANARY_HOST ?? '127.0.0.1';

interface Hit { kind: 'http' | 'ws' | 'tcp' | 'udp'; time: number; url?: string; method?: string; detail?: string }

const hits: Hit[] = [];
const socketRecord = new WeakMap<Socket, Hit>();

const server = http.createServer((req, res) => {
  const url = req.url ?? '/';
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Private-Network', 'true');
  if (url === '/__hits' || url === '/__reset') {
    // Control traffic from the test runner is not a leak: drop the TCP record of this connection.
    const rec = socketRecord.get(req.socket);
    if (rec) {
      const i = hits.indexOf(rec);
      if (i >= 0) hits.splice(i, 1);
    }
    if (url === '/__reset') hits.length = 0;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify(hits));
    return;
  }
  const hit: Hit = { kind: 'http', time: Date.now(), method: req.method, url, detail: String(req.headers['sec-fetch-dest'] ?? '') };
  hits.push(hit);
  console.log('HIT http', req.method, url);
  res.setHeader('Content-Type', 'text/plain');
  res.end('canary');
});

server.on('connection', (socket) => {
  const hit: Hit = { kind: 'tcp', time: Date.now(), detail: `${socket.remoteAddress}:${socket.remotePort}` };
  hits.push(hit);
  socketRecord.set(socket, hit);
});

const wss = new WebSocketServer({ server });
wss.on('connection', (_ws, req) => {
  hits.push({ kind: 'ws', time: Date.now(), url: req.url });
  console.log('HIT ws', req.url);
});

const udp = dgram.createSocket('udp4');
udp.on('message', (msg, rinfo) => {
  hits.push({ kind: 'udp', time: Date.now(), detail: `${rinfo.address}:${rinfo.port} ${msg.length} bytes` });
  console.log('HIT udp', rinfo.address, rinfo.port, msg.length, 'bytes');
});
udp.bind(CANARY_PORT, CANARY_HOST);

server.listen(CANARY_PORT, CANARY_HOST, () => {
  console.log(`canary listening on http://${CANARY_HOST}:${CANARY_PORT} (tcp+ws+udp)`);
});

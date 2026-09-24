#ifndef TGCALLS_V2WASM_CALL_CORE_ABI_H
#define TGCALLS_V2WASM_CALL_CORE_ABI_H

#include <stddef.h>
#include <stdint.h>

// ============================================================================
// tgcalls call-core ABI, version 1.
//
// This is the frozen seam between the fixed native harness ("host") and the
// swappable call-control logic ("core"). It is shaped exactly like the future
// WASM boundary: the three functions below are the module exports, and the
// single emit callback is the module import. All payloads are UTF-8 JSON
// objects tagged with "@type".
//
// Rules (normative):
// - THREADING: all calls into the core happen on one thread (the tgcalls
//   media thread). The core never blocks and owns no threads/timers; it asks
//   the host for time via the "set_timer" command.
// - REENTRANCY: the emit callback may be invoked only from inside
//   tgcalls_core_create / tgcalls_core_on_event, on the same thread. The host
//   queues emitted commands and executes them AFTER the core call returns.
//   Results of asynchronous commands are delivered as later events.
// - MEMORY: buffers passed to the core are valid only for the duration of the
//   call; buffers passed to emit are valid only for the duration of the
//   callback. Each side copies what it keeps (WASM linear-memory contract).
// - EXTENSIBILITY: the core MUST ignore events with an unknown "@type". The
//   host MUST reply to an unknown or malformed command with an "error" event
//   and MUST NOT crash. The config carries "abiVersion"; the core echoes it
//   in "core_ready"; on mismatch the host disables the core (drops all
//   further commands/events) and reports the call failed.
// - CLOCK: the core has no clock. Every event carries "nowMs" (int64 as JSON
//   number, host monotonic milliseconds); the core uses the latest value.
// - BUDGET: data channels are control-plane; modules should stay in the
//   ≲10 Hz / KB-scale envelope (enforcement: Phase-4 metering).
//
// Config (tgcalls_core_create):
//   { "abiVersion": 1, "isOutgoing": bool,
//     "enableP2P": bool, "customParameters": string,
//     "rtcServers": [ { "host": s, "port": n, "login": s, "password": s,
//                       "isTurn": bool, "isTcp": bool } ] }
//   No key material, ever.
//
// Events (host -> core): "@type" plus fields, all carry "nowMs":
//   signaling_packet    { packetB64: s }        full plaintext signaling
//                       packet, base64: seq(4 bytes, network order) || body.
//                       The host has already opened the AEAD and replay-
//                       checked the counter (low 30 bits of seq). The body
//                       layout is core policy: V1 (wire 10.0.0) message
//                       packing incl. acks/resends; V2 gzip'd JSON.
//   pc_renegotiation_needed {}
//   pc_ice_candidate    { mid: s, mline: n, sdp: s }
//   pc_ice_state        { state: "new"|"checking"|"connected"|"completed"|
//                                "failed"|"disconnected"|"closed" }
//   pc_connection_state { state: "new"|"connecting"|"connected"|
//                                "disconnected"|"failed"|"closed" }
//   pc_gathering_state  { state: "new"|"gathering"|"complete" }
//   pc_signaling_state  { state: "stable"|"have-local-offer"|
//                                "have-remote-offer"|"have-local-pranswer"|
//                                "have-remote-pranswer"|"closed" }
//   pc_candidate_pair_changed { local: {type,protocol,address},
//                               remote: {type,protocol,address} }
//   pc_description_created { ok: bool, type: "offer"|"answer", sdp: s,
//                            error: s }         completes pc_create_offer/
//                                               pc_create_answer
//   pc_set_local_done   { ok: bool, type: "offer"|"answer", sdp: s }
//                                               sdp read back after apply
//   pc_set_remote_done  { ok: bool, sdpType: "offer"|"answer" }
//   pc_track            { mid: s, kind: "audio"|"video" }  remote track (OnTrack)
//   dc_state            { label: s, open: bool }
//   dc_message          { label: s, data?: s, dataB64?: s }  text arrives as
//                       data, binary as dataB64 (base64)
//   dc_buffered         { label: s, bufferedAmount: n }  emitted when a
//                       channel's send buffer drains to 0 (backpressure:
//                       send a batch, wait for drain)
//   dc_channel          { label: s, id: n }   remote-announced channel
//                       (OnDataChannel); registered under its label,
//                       dc_send/dc_state/dc_message work on it thereafter
//   timer               { token: n, generation?: n }  generation echoes
//                       whatever set_timer sent (0 if that omitted it)
//   stats               { sendBitrateKbps: n,   curated GetStats reduction;
//                         transport: { rttMs?, availableOutgoingKbps?,
//                           availableIncomingKbps?, bytesSent, bytesReceived,
//                           localCandidateType?, remoteCandidateType? },
//                         audio: { send?: { bitrateKbps, packetsSent,
//                             remoteLossFraction?, remoteRttMs?,
//                             remoteJitterMs? },
//                           recv?: { bitrateKbps, packetsReceived,
//                             packetsLost, jitterMs?, audioLevel? } },
//                         video: { send?: { bitrateKbps, packetsSent,
//                             frameRate?, frameWidth?, frameHeight?,
//                             qualityLimitationReason?, remoteLossFraction?,
//                             remoteRttMs?, remoteJitterMs? },
//                           recv?: { bitrateKbps, packetsReceived,
//                             packetsLost, framesDecoded, frameRate?,
//                             frameWidth?, frameHeight? } } }
//                       absent measurements omit their keys; bitrates are
//                       host-computed deltas between polls, EXCEPT the
//                       top-level sendBitrateKbps which is the max BWE
//                       (available_outgoing_bitrate / 1024, kept for
//                       stats-log parity with stock)
//   mute                { muted: bool }
//   battery_low         { low: bool }
//   video_capture       { active: bool, screencast: bool }
//   stop                {}                      core replies stats_log + close
//   error               { message: s, command: s }
//
// Commands (core -> host): "@type" plus fields:
//   core_ready          { abiVersion: n }
//   pc_create           { iceTransportsType: "all"|"relay",
//                         iceServers: [ { urls: [s], username: s,
//                                         password: s } ],
//                         audioProcessing?: { … }  (optional APM overrides, defaults = stock) }
//   pc_create_offer     {}                      -> pc_description_created
//   pc_create_answer    {}                      -> pc_description_created
//   pc_set_local_description  { type: s, sdp: s }  the SDP to apply (the
//                                               core may munge what it got
//                                               from pc_description_created)
//   pc_set_remote_description { sdpType: s, sdp: s }
//   pc_add_ice_candidate      { mid: s, mline: n, sdp: s }
//   pc_restart_ice      {}                      flows back through
//                                               pc_renegotiation_needed
//   pc_add_transceiver  { id: s (core-chosen), kind: "audio"|"video",
//                         direction: "sendrecv"|"sendonly"|"recvonly"|
//                                    "inactive",
//                         codecPreferences: [s] (optional),
//                         sendEncodings: [ { active?: bool,
//                           maxBitrateBps?: n, minBitrateBps?: n,
//                           scaleResolutionDownBy?: n, rid?: s } ] (optional),
//                         trackSource: "microphone"|"camera"|"none" }
//   pc_set_parameters   { id: s, degradationPreference?: "disabled"|
//                           "maintain-framerate"|"maintain-resolution"|
//                           "balanced",
//                         encodings: [ same fields as sendEncodings ] }
//                       host merges into GetParameters() then SetParameters
//   pc_set_track_enabled { id: s, enabled: bool }
//   pc_remove_track     { id: s }
//   pc_set_incoming_sink { mid: s }             bind the app video sink to
//                                               this incoming transceiver
//   pc_create_data_channel { label?: s (default "data"), ordered?: bool,
//                            negotiated?: bool, id?: n }  callable N times;
//                       labels unique among core-created channels
//                       (duplicate -> "error" event)
//   dc_send             { label?: s (default "data"), data?: s, dataB64?: s }
//                       exactly one of data/dataB64; max 256 KiB; unknown
//                       label or closed channel -> "error" event
//   set_audio_processing { echoCancellation?: bool, noiseSuppression?: bool,
//                          autoGainControl?: bool, highPassFilter?: bool }
//                       host ApplyConfig on its retained APM; only supplied
//                       keys change; autoGainControl maps to the classic AGC
//                       (gain_controller1). Also accepted at creation as
//                       pc_create.audioProcessing { same keys }.
//   pc_set_configuration { iceServers?: [ { urls: [s], username: s,
//                            password: s } ],
//                          iceTransportsType?: "all"|"relay",
//                          candidatePoolSize?: n }
//                       host merges ONLY these keys into GetConfiguration()
//                       then SetConfiguration; webrtc rejections surface as
//                       "error" events
//   signaling_send_packet { packetB64: s }      full plaintext packet,
//                       base64: seq(4, network order) || body, built by the
//                       core. seq layout: bit31 = single-message-packet,
//                       bit30 = requires-ack (V1 framing flags), low 30 bits
//                       = counter. The host enforces a strictly increasing
//                       counter (AEAD IV freshness), seals with the native
//                       key, and sends via the native transport routing.
//                       Framing (seq + gzip) is entirely core-owned; the
//                       reference core reproduces stock's wire 11.0.0
//                       framing byte-for-byte.
//   set_timer           { token: n, generation?: n, delayMs: n }  a module
//                       that never uses generation may omit it; the host
//                       reads it via number_value(), which defaults a
//                       missing/non-numeric field to 0
//   pc_get_stats        {}                      -> stats event
//   emit_state          { state: "established"|"failed"|"reconnecting" }
//   emit_signal_bars    { bars: 0..4 }
//   emit_remote_media_state { audio: "active"|"muted",
//                             video: "inactive"|"paused"|"active" }
//   emit_remote_battery_low { low: bool }
//   log                 { message: s }
//   stats_log           { json: s }             host writes at stop
//   close               {}                      host closes PC, completes stop
//
// Module form (WASM). Function pointers cannot cross the module boundary;
// a .wasm module implements ABI v1 as (one module instance per call — the
// instance IS the handle):
//   exports:  core_init(config_ptr: i32, config_len: i32)
//             core_on_event(ptr: i32, len: i32)
//             rt_alloc(len: i32) -> i32
//             rt_free(ptr: i32)
//   imports:  env.host_emit(ptr: i32, len: i32)
//             env.host_deflate(in: i32, in_len: i32, out: i32, out_cap: i32) -> i32
//             env.host_inflate(in: i32, in_len: i32, out: i32, out_cap: i32) -> i32
// For each event the host rt_allocs, copies in, calls core_on_event, then
// rt_frees after it returns; the module copies anything it retains.
// host_emit payloads are copied out by the host during the call.
// tgcalls_core_destroy maps to instance destruction (no export).
//
// Host compression service (host_deflate/host_inflate; C form
// tgcalls_host_deflate/tgcalls_host_inflate below): a pure gzip codec —
// deflate wraps in the gzip container at max compression (the same zlib the
// stock implementations use, so compressed bytes match stock exactly);
// inflate accepts gzip (1f 8b) and zlib (78 9c) framing. Returns bytes
// written into out (>= 0) or -1 on failure/insufficient out_cap. Synchronous
// and stateless: mechanism only — WHETHER and WHAT to compress stays core
// policy (V2 framing). Buffer ranges are validated at the module boundary
// (WAMR "(*~*~)i" signature). out_cap doubles as the inflate size limit
// (zip-bomb bound), chosen by the core.
// ============================================================================

#ifdef __cplusplus
extern "C" {
#endif

typedef struct TgcallsCallCore TgcallsCallCore;

// Host compression service (see the doc block above). Provided by the
// harness: natively via utils/gzip (zlib), in the module form as the
// env.host_deflate/env.host_inflate imports (the attributes below bind the
// same C symbols to those imports under a wasm32 build).
#if defined(__wasm__)
__attribute__((import_module("env"), import_name("host_deflate")))
#endif
int32_t tgcalls_host_deflate(const uint8_t *in, size_t inLen, uint8_t *out, size_t outCap);
#if defined(__wasm__)
__attribute__((import_module("env"), import_name("host_inflate")))
#endif
int32_t tgcalls_host_inflate(const uint8_t *in, size_t inLen, uint8_t *out, size_t outCap);

typedef void (*TgcallsCoreEmitFn)(void *userData, const uint8_t *data, size_t len);

TgcallsCallCore *tgcalls_core_create(const char *configJson, TgcallsCoreEmitFn emit, void *userData);
void tgcalls_core_on_event(TgcallsCallCore *core, const uint8_t *data, size_t len);
void tgcalls_core_destroy(TgcallsCallCore *core);

#ifdef __cplusplus
}
#endif

#endif

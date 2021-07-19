// Copyright 2014 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"time"
)

// opcodePacket signals a packet, encoded with a 32-bit length prefix, followed
// by the payload.
const opcodePacket = byte('P')

// opcodeTimeout signals a read timeout, encoded by a 64-bit number of
// nanoseconds. On receipt, the peer should reply with
// opcodeTimeoutAck. opcodeTimeout may only be sent by the Go side.
const opcodeTimeout = byte('T')

// opcodeTimeoutAck acknowledges a read timeout. This opcode has no payload and
// may only be sent by the C side. Timeout ACKs act as a synchronization point
// at the timeout, to bracket one flight of messages from C.
const opcodeTimeoutAck = byte('t')

type packetAdaptor struct {
	net.Conn
	debug *recordingConn
}

// newPacketAdaptor wraps a reliable streaming net.Conn into a reliable
// packet-based net.Conn. The stream contains packets and control commands,
// distinguished by a one byte opcode.
func newPacketAdaptor(conn net.Conn) *packetAdaptor {
	return &packetAdaptor{conn, nil}
}

func (p *packetAdaptor) log(message string, data []byte) {
	if p.debug == nil {
		return
	}

	p.debug.LogSpecial(message, data)
}

func (p *packetAdaptor) readOpcode() (byte, error) {
	out := make([]byte, 1)
	if _, err := io.ReadFull(p.Conn, out); err != nil {
		return 0, err
	}
	return out[0], nil
}

func (p *packetAdaptor) readPacketBody() ([]byte, error) {
	var length uint32
	if err := binary.Read(p.Conn, binary.BigEndian, &length); err != nil {
		return nil, err
	}
	out := make([]byte, length)
	if _, err := io.ReadFull(p.Conn, out); err != nil {
		return nil, err
	}
	return out, nil
}

func (p *packetAdaptor) Read(b []byte) (int, error) {
	opcode, err := p.readOpcode()
	if err != nil {
		return 0, err
	}
	if opcode != opcodePacket {
		return 0, fmt.Errorf("unexpected opcode '%d'", opcode)
	}
	out, err := p.readPacketBody()
	if err != nil {
		return 0, err
	}
	return copy(b, out), nil
}

func (p *packetAdaptor) Write(b []byte) (int, error) {
	payload := make([]byte, 1+4+len(b))
	payload[0] = opcodePacket
	binary.BigEndian.PutUint32(payload[1:5], uint32(len(b)))
	copy(payload[5:], b)
	if _, err := p.Conn.Write(payload); err != nil {
		return 0, err
	}
	return len(b), nil
}

// SendReadTimeout instructs the peer to simulate a read timeout. It then waits
// for acknowledgement of the timeout, buffering any packets received since
// then. The packets are then returned.
func (p *packetAdaptor) SendReadTimeout(d time.Duration) ([][]byte, error) {
	p.log("Simulating read timeout: "+d.String(), nil)

	payload := make([]byte, 1+8)
	payload[0] = opcodeTimeout
	binary.BigEndian.PutUint64(payload[1:], uint64(d.Nanoseconds()))
	if _, err := p.Conn.Write(payload); err != nil {
		return nil, err
	}

	var packets [][]byte
	for {
		opcode, err := p.readOpcode()
		if err != nil {
			return nil, err
		}
		switch opcode {
		case opcodeTimeoutAck:
			p.log("Received timeout ACK", nil)
			// Done! Return the packets buffered and continue.
			return packets, nil
		case opcodePacket:
			// Buffer the packet for the caller to process.
			packet, err := p.readPacketBody()
			if err != nil {
				return nil, err
			}
			p.log("Simulating dropped packet", packet)
			packets = append(packets, packet)
		default:
			return nil, fmt.Errorf("unexpected opcode '%d'", opcode)
		}
	}
}

type replayAdaptor struct {
	net.Conn
	prevWrite []byte
}

// newReplayAdaptor wraps a packeted net.Conn. It transforms it into
// one which, after writing a packet, always replays the previous
// write.
func newReplayAdaptor(conn net.Conn) net.Conn {
	return &replayAdaptor{Conn: conn}
}

func (r *replayAdaptor) Write(b []byte) (int, error) {
	n, err := r.Conn.Write(b)

	// Replay the previous packet and save the current one to
	// replay next.
	if r.prevWrite != nil {
		r.Conn.Write(r.prevWrite)
	}
	r.prevWrite = append(r.prevWrite[:0], b...)

	return n, err
}

type damageAdaptor struct {
	net.Conn
	damage bool
}

// newDamageAdaptor wraps a packeted net.Conn. It transforms it into one which
// optionally damages the final byte of every Write() call.
func newDamageAdaptor(conn net.Conn) *damageAdaptor {
	return &damageAdaptor{Conn: conn}
}

func (d *damageAdaptor) setDamage(damage bool) {
	d.damage = damage
}

func (d *damageAdaptor) Write(b []byte) (int, error) {
	if d.damage && len(b) > 0 {
		b = append([]byte{}, b...)
		b[len(b)-1]++
	}
	return d.Conn.Write(b)
}

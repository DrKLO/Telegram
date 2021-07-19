// Copyright 2014 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// DTLS implementation.
//
// NOTE: This is a not even a remotely production-quality DTLS
// implementation. It is the bare minimum necessary to be able to
// achieve coverage on BoringSSL's implementation. Of note is that
// this implementation assumes the underlying net.PacketConn is not
// only reliable but also ordered. BoringSSL will be expected to deal
// with simulated loss, but there is no point in forcing the test
// driver to.

package runner

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"math/rand"
	"net"
)

func (c *Conn) dtlsDoReadRecord(want recordType) (recordType, *block, error) {
	recordHeaderLen := dtlsRecordHeaderLen

	if c.rawInput == nil {
		c.rawInput = c.in.newBlock()
	}
	b := c.rawInput

	// Read a new packet only if the current one is empty.
	var newPacket bool
	if len(b.data) == 0 {
		// Pick some absurdly large buffer size.
		b.resize(maxCiphertext + recordHeaderLen)
		n, err := c.conn.Read(c.rawInput.data)
		if err != nil {
			return 0, nil, err
		}
		if c.config.Bugs.MaxPacketLength != 0 && n > c.config.Bugs.MaxPacketLength {
			return 0, nil, fmt.Errorf("dtls: exceeded maximum packet length")
		}
		c.rawInput.resize(n)
		newPacket = true
	}

	// Read out one record.
	//
	// A real DTLS implementation should be tolerant of errors,
	// but this is test code. We should not be tolerant of our
	// peer sending garbage.
	if len(b.data) < recordHeaderLen {
		return 0, nil, errors.New("dtls: failed to read record header")
	}
	typ := recordType(b.data[0])
	vers := uint16(b.data[1])<<8 | uint16(b.data[2])
	// Alerts sent near version negotiation do not have a well-defined
	// record-layer version prior to TLS 1.3. (In TLS 1.3, the record-layer
	// version is irrelevant.)
	if typ != recordTypeAlert {
		if c.haveVers {
			if vers != c.wireVersion {
				c.sendAlert(alertProtocolVersion)
				return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: received record with version %x when expecting version %x", vers, c.wireVersion))
			}
		} else {
			// Pre-version-negotiation alerts may be sent with any version.
			if expect := c.config.Bugs.ExpectInitialRecordVersion; expect != 0 && vers != expect {
				c.sendAlert(alertProtocolVersion)
				return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: received record with version %x when expecting version %x", vers, expect))
			}
		}
	}
	epoch := b.data[3:5]
	seq := b.data[5:11]
	// For test purposes, require the sequence number be monotonically
	// increasing, so c.in includes the minimum next sequence number. Gaps
	// may occur if packets failed to be sent out. A real implementation
	// would maintain a replay window and such.
	if !bytes.Equal(epoch, c.in.seq[:2]) {
		c.sendAlert(alertIllegalParameter)
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: bad epoch"))
	}
	if bytes.Compare(seq, c.in.seq[2:]) < 0 {
		c.sendAlert(alertIllegalParameter)
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: bad sequence number"))
	}
	copy(c.in.seq[2:], seq)
	n := int(b.data[11])<<8 | int(b.data[12])
	if n > maxCiphertext || len(b.data) < recordHeaderLen+n {
		c.sendAlert(alertRecordOverflow)
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: oversized record received with length %d", n))
	}

	// Process message.
	b, c.rawInput = c.in.splitBlock(b, recordHeaderLen+n)
	ok, off, _, alertValue := c.in.decrypt(b)
	if !ok {
		// A real DTLS implementation would silently ignore bad records,
		// but we want to notice errors from the implementation under
		// test.
		return 0, nil, c.in.setErrorLocked(c.sendAlert(alertValue))
	}
	b.off = off

	// TODO(nharper): Once DTLS 1.3 is defined, handle the extra
	// parameter from decrypt.

	// Require that ChangeCipherSpec always share a packet with either the
	// previous or next handshake message.
	if newPacket && typ == recordTypeChangeCipherSpec && c.rawInput == nil {
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("dtls: ChangeCipherSpec not packed together with Finished"))
	}

	return typ, b, nil
}

func (c *Conn) makeFragment(header, data []byte, fragOffset, fragLen int) []byte {
	fragment := make([]byte, 0, 12+fragLen)
	fragment = append(fragment, header...)
	fragment = append(fragment, byte(c.sendHandshakeSeq>>8), byte(c.sendHandshakeSeq))
	fragment = append(fragment, byte(fragOffset>>16), byte(fragOffset>>8), byte(fragOffset))
	fragment = append(fragment, byte(fragLen>>16), byte(fragLen>>8), byte(fragLen))
	fragment = append(fragment, data[fragOffset:fragOffset+fragLen]...)
	return fragment
}

func (c *Conn) dtlsWriteRecord(typ recordType, data []byte) (n int, err error) {
	// Only handshake messages are fragmented.
	if typ != recordTypeHandshake {
		reorder := typ == recordTypeChangeCipherSpec && c.config.Bugs.ReorderChangeCipherSpec

		// Flush pending handshake messages before encrypting a new record.
		if !reorder {
			err = c.dtlsPackHandshake()
			if err != nil {
				return
			}
		}

		if typ == recordTypeApplicationData && len(data) > 1 && c.config.Bugs.SplitAndPackAppData {
			_, err = c.dtlsPackRecord(typ, data[:len(data)/2], false)
			if err != nil {
				return
			}
			_, err = c.dtlsPackRecord(typ, data[len(data)/2:], true)
			if err != nil {
				return
			}
			n = len(data)
		} else {
			n, err = c.dtlsPackRecord(typ, data, false)
			if err != nil {
				return
			}
		}

		if reorder {
			err = c.dtlsPackHandshake()
			if err != nil {
				return
			}
		}

		if typ == recordTypeChangeCipherSpec {
			err = c.out.changeCipherSpec(c.config)
			if err != nil {
				return n, c.sendAlertLocked(alertLevelError, err.(alert))
			}
		} else {
			// ChangeCipherSpec is part of the handshake and not
			// flushed until dtlsFlushPacket.
			err = c.dtlsFlushPacket()
			if err != nil {
				return
			}
		}
		return
	}

	if c.out.cipher == nil && c.config.Bugs.StrayChangeCipherSpec {
		_, err = c.dtlsPackRecord(recordTypeChangeCipherSpec, []byte{1}, false)
		if err != nil {
			return
		}
	}

	maxLen := c.config.Bugs.MaxHandshakeRecordLength
	if maxLen <= 0 {
		maxLen = 1024
	}

	// Handshake messages have to be modified to include fragment
	// offset and length and with the header replicated. Save the
	// TLS header here.
	//
	// TODO(davidben): This assumes that data contains exactly one
	// handshake message. This is incompatible with
	// FragmentAcrossChangeCipherSpec. (Which is unfortunate
	// because OpenSSL's DTLS implementation will probably accept
	// such fragmentation and could do with a fix + tests.)
	header := data[:4]
	data = data[4:]

	isFinished := header[0] == typeFinished

	if c.config.Bugs.SendEmptyFragments {
		c.pendingFragments = append(c.pendingFragments, c.makeFragment(header, data, 0, 0))
		c.pendingFragments = append(c.pendingFragments, c.makeFragment(header, data, len(data), 0))
	}

	firstRun := true
	fragOffset := 0
	for firstRun || fragOffset < len(data) {
		firstRun = false
		fragLen := len(data) - fragOffset
		if fragLen > maxLen {
			fragLen = maxLen
		}

		fragment := c.makeFragment(header, data, fragOffset, fragLen)
		if c.config.Bugs.FragmentMessageTypeMismatch && fragOffset > 0 {
			fragment[0]++
		}
		if c.config.Bugs.FragmentMessageLengthMismatch && fragOffset > 0 {
			fragment[3]++
		}

		// Buffer the fragment for later. They will be sent (and
		// reordered) on flush.
		c.pendingFragments = append(c.pendingFragments, fragment)
		if c.config.Bugs.ReorderHandshakeFragments {
			// Don't duplicate Finished to avoid the peer
			// interpreting it as a retransmit request.
			if !isFinished {
				c.pendingFragments = append(c.pendingFragments, fragment)
			}

			if fragLen > (maxLen+1)/2 {
				// Overlap each fragment by half.
				fragLen = (maxLen + 1) / 2
			}
		}
		fragOffset += fragLen
		n += fragLen
	}
	shouldSendTwice := c.config.Bugs.MixCompleteMessageWithFragments
	if isFinished {
		shouldSendTwice = c.config.Bugs.RetransmitFinished
	}
	if shouldSendTwice {
		fragment := c.makeFragment(header, data, 0, len(data))
		c.pendingFragments = append(c.pendingFragments, fragment)
	}

	// Increment the handshake sequence number for the next
	// handshake message.
	c.sendHandshakeSeq++
	return
}

// dtlsPackHandshake packs the pending handshake flight into the pending
// record. Callers should follow up with dtlsFlushPacket to write the packets.
func (c *Conn) dtlsPackHandshake() error {
	// This is a test-only DTLS implementation, so there is no need to
	// retain |c.pendingFragments| for a future retransmit.
	var fragments [][]byte
	fragments, c.pendingFragments = c.pendingFragments, fragments

	if c.config.Bugs.ReorderHandshakeFragments {
		perm := rand.New(rand.NewSource(0)).Perm(len(fragments))
		tmp := make([][]byte, len(fragments))
		for i := range tmp {
			tmp[i] = fragments[perm[i]]
		}
		fragments = tmp
	} else if c.config.Bugs.ReverseHandshakeFragments {
		tmp := make([][]byte, len(fragments))
		for i := range tmp {
			tmp[i] = fragments[len(fragments)-i-1]
		}
		fragments = tmp
	}

	maxRecordLen := c.config.Bugs.PackHandshakeFragments

	// Pack handshake fragments into records.
	var records [][]byte
	for _, fragment := range fragments {
		if n := c.config.Bugs.SplitFragments; n > 0 {
			if len(fragment) > n {
				records = append(records, fragment[:n])
				records = append(records, fragment[n:])
			} else {
				records = append(records, fragment)
			}
		} else if i := len(records) - 1; len(records) > 0 && len(records[i])+len(fragment) <= maxRecordLen {
			records[i] = append(records[i], fragment...)
		} else {
			// The fragment will be appended to, so copy it.
			records = append(records, append([]byte{}, fragment...))
		}
	}

	// Send the records.
	for _, record := range records {
		_, err := c.dtlsPackRecord(recordTypeHandshake, record, false)
		if err != nil {
			return err
		}
	}

	return nil
}

func (c *Conn) dtlsFlushHandshake() error {
	if err := c.dtlsPackHandshake(); err != nil {
		return err
	}
	if err := c.dtlsFlushPacket(); err != nil {
		return err
	}

	return nil
}

// dtlsPackRecord packs a single record to the pending packet, flushing it
// if necessary. The caller should call dtlsFlushPacket to flush the current
// pending packet afterwards.
func (c *Conn) dtlsPackRecord(typ recordType, data []byte, mustPack bool) (n int, err error) {
	recordHeaderLen := dtlsRecordHeaderLen
	maxLen := c.config.Bugs.MaxHandshakeRecordLength
	if maxLen <= 0 {
		maxLen = 1024
	}

	b := c.out.newBlock()

	explicitIVLen := 0
	explicitIVIsSeq := false

	if cbc, ok := c.out.cipher.(cbcMode); ok {
		// Block cipher modes have an explicit IV.
		explicitIVLen = cbc.BlockSize()
	} else if aead, ok := c.out.cipher.(*tlsAead); ok {
		if aead.explicitNonce {
			explicitIVLen = 8
			// The AES-GCM construction in TLS has an explicit nonce so that
			// the nonce can be random. However, the nonce is only 8 bytes
			// which is too small for a secure, random nonce. Therefore we
			// use the sequence number as the nonce.
			explicitIVIsSeq = true
		}
	} else if _, ok := c.out.cipher.(nullCipher); !ok && c.out.cipher != nil {
		panic("Unknown cipher")
	}
	b.resize(recordHeaderLen + explicitIVLen + len(data))
	// TODO(nharper): DTLS 1.3 will likely need to set this to
	// recordTypeApplicationData if c.out.cipher != nil.
	b.data[0] = byte(typ)
	vers := c.wireVersion
	if vers == 0 {
		// Some TLS servers fail if the record version is greater than
		// TLS 1.0 for the initial ClientHello.
		if c.isDTLS {
			vers = VersionDTLS10
		} else {
			vers = VersionTLS10
		}
	}
	b.data[1] = byte(vers >> 8)
	b.data[2] = byte(vers)
	// DTLS records include an explicit sequence number.
	copy(b.data[3:11], c.out.outSeq[0:])
	b.data[11] = byte(len(data) >> 8)
	b.data[12] = byte(len(data))
	if explicitIVLen > 0 {
		explicitIV := b.data[recordHeaderLen : recordHeaderLen+explicitIVLen]
		if explicitIVIsSeq {
			copy(explicitIV, c.out.outSeq[:])
		} else {
			if _, err = io.ReadFull(c.config.rand(), explicitIV); err != nil {
				return
			}
		}
	}
	copy(b.data[recordHeaderLen+explicitIVLen:], data)
	c.out.encrypt(b, explicitIVLen, typ)

	// Flush the current pending packet if necessary.
	if !mustPack && len(b.data)+len(c.pendingPacket) > c.config.Bugs.PackHandshakeRecords {
		err = c.dtlsFlushPacket()
		if err != nil {
			c.out.freeBlock(b)
			return
		}
	}

	// Add the record to the pending packet.
	c.pendingPacket = append(c.pendingPacket, b.data...)
	c.out.freeBlock(b)
	n = len(data)
	return
}

func (c *Conn) dtlsFlushPacket() error {
	if len(c.pendingPacket) == 0 {
		return nil
	}
	_, err := c.conn.Write(c.pendingPacket)
	c.pendingPacket = nil
	return err
}

func (c *Conn) dtlsDoReadHandshake() ([]byte, error) {
	// Assemble a full handshake message.  For test purposes, this
	// implementation assumes fragments arrive in order. It may
	// need to be cleverer if we ever test BoringSSL's retransmit
	// behavior.
	for len(c.handMsg) < 4+c.handMsgLen {
		// Get a new handshake record if the previous has been
		// exhausted.
		if c.hand.Len() == 0 {
			if err := c.in.err; err != nil {
				return nil, err
			}
			if err := c.readRecord(recordTypeHandshake); err != nil {
				return nil, err
			}
		}

		// Read the next fragment. It must fit entirely within
		// the record.
		if c.hand.Len() < 12 {
			return nil, errors.New("dtls: bad handshake record")
		}
		header := c.hand.Next(12)
		fragN := int(header[1])<<16 | int(header[2])<<8 | int(header[3])
		fragSeq := uint16(header[4])<<8 | uint16(header[5])
		fragOff := int(header[6])<<16 | int(header[7])<<8 | int(header[8])
		fragLen := int(header[9])<<16 | int(header[10])<<8 | int(header[11])

		if c.hand.Len() < fragLen {
			return nil, errors.New("dtls: fragment length too long")
		}
		fragment := c.hand.Next(fragLen)

		// Check it's a fragment for the right message.
		if fragSeq != c.recvHandshakeSeq {
			return nil, errors.New("dtls: bad handshake sequence number")
		}

		// Check that the length is consistent.
		if c.handMsg == nil {
			c.handMsgLen = fragN
			if c.handMsgLen > maxHandshake {
				return nil, c.in.setErrorLocked(c.sendAlert(alertInternalError))
			}
			// Start with the TLS handshake header,
			// without the DTLS bits.
			c.handMsg = append([]byte{}, header[:4]...)
		} else if fragN != c.handMsgLen {
			return nil, errors.New("dtls: bad handshake length")
		}

		// Add the fragment to the pending message.
		if 4+fragOff != len(c.handMsg) {
			return nil, errors.New("dtls: bad fragment offset")
		}
		if fragOff+fragLen > c.handMsgLen {
			return nil, errors.New("dtls: bad fragment length")
		}
		c.handMsg = append(c.handMsg, fragment...)
	}
	c.recvHandshakeSeq++
	ret := c.handMsg
	c.handMsg, c.handMsgLen = nil, 0
	return ret, nil
}

// DTLSServer returns a new DTLS server side connection
// using conn as the underlying transport.
// The configuration config must be non-nil and must have
// at least one certificate.
func DTLSServer(conn net.Conn, config *Config) *Conn {
	c := &Conn{config: config, isDTLS: true, conn: conn}
	c.init()
	return c
}

// DTLSClient returns a new DTLS client side connection
// using conn as the underlying transport.
// The config cannot be nil: users must set either ServerHostname or
// InsecureSkipVerify in the config.
func DTLSClient(conn net.Conn, config *Config) *Conn {
	c := &Conn{config: config, isClient: true, isDTLS: true, conn: conn}
	c.init()
	return c
}

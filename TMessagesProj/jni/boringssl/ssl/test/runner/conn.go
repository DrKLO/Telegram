// Copyright 2010 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

// TLS low level connection and record layer

package runner

import (
	"bytes"
	"crypto/cipher"
	"crypto/ecdsa"
	"crypto/subtle"
	"crypto/x509"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"sync"
	"time"
)

var errNoCertificateAlert = errors.New("tls: no certificate alert")
var errEndOfEarlyDataAlert = errors.New("tls: end of early data alert")

// A Conn represents a secured connection.
// It implements the net.Conn interface.
type Conn struct {
	// constant
	conn     net.Conn
	isDTLS   bool
	isClient bool

	// constant after handshake; protected by handshakeMutex
	handshakeMutex       sync.Mutex // handshakeMutex < in.Mutex, out.Mutex, errMutex
	handshakeErr         error      // error resulting from handshake
	wireVersion          uint16     // TLS wire version
	vers                 uint16     // TLS version
	haveVers             bool       // version has been negotiated
	config               *Config    // configuration passed to constructor
	handshakeComplete    bool
	skipEarlyData        bool // On a server, indicates that the client is sending early data that must be skipped over.
	didResume            bool // whether this connection was a session resumption
	extendedMasterSecret bool // whether this session used an extended master secret
	cipherSuite          *cipherSuite
	earlyCipherSuite     *cipherSuite
	ocspResponse         []byte // stapled OCSP response
	sctList              []byte // signed certificate timestamp list
	peerCertificates     []*x509.Certificate
	// verifiedChains contains the certificate chains that we built, as
	// opposed to the ones presented by the server.
	verifiedChains [][]*x509.Certificate
	// serverName contains the server name indicated by the client, if any.
	serverName string
	// firstFinished contains the first Finished hash sent during the
	// handshake. This is the "tls-unique" channel binding value.
	firstFinished [12]byte
	// peerSignatureAlgorithm contains the signature algorithm that was used
	// by the peer in the handshake, or zero if not applicable.
	peerSignatureAlgorithm signatureAlgorithm
	// curveID contains the curve that was used in the handshake, or zero if
	// not applicable.
	curveID CurveID
	// quicTransportParams contains the QUIC transport params received
	// by the peer.
	quicTransportParams []byte

	clientRandom, serverRandom [32]byte
	earlyExporterSecret        []byte
	exporterSecret             []byte
	resumptionSecret           []byte

	clientProtocol         string
	clientProtocolFallback bool
	usedALPN               bool

	// verify_data values for the renegotiation extension.
	clientVerify []byte
	serverVerify []byte

	channelID *ecdsa.PublicKey

	tokenBindingNegotiated bool
	tokenBindingParam      uint8

	srtpProtectionProfile uint16

	clientVersion uint16

	// input/output
	in, out  halfConn     // in.Mutex < out.Mutex
	rawInput *block       // raw input, right off the wire
	input    *block       // application record waiting to be read
	hand     bytes.Buffer // handshake record waiting to be read

	// pendingFlight, if PackHandshakeFlight is enabled, is the buffer of
	// handshake data to be split into records at the end of the flight.
	pendingFlight bytes.Buffer

	// DTLS state
	sendHandshakeSeq uint16
	recvHandshakeSeq uint16
	handMsg          []byte   // pending assembled handshake message
	handMsgLen       int      // handshake message length, not including the header
	pendingFragments [][]byte // pending outgoing handshake fragments.
	pendingPacket    []byte   // pending outgoing packet.

	keyUpdateSeen      bool
	keyUpdateRequested bool
	seenOneByteRecord  bool

	expectTLS13ChangeCipherSpec bool

	// seenHandshakePackEnd is whether the most recent handshake record was
	// not full for ExpectPackedEncryptedHandshake. If true, no more
	// handshake data may be received until the next flight or epoch change.
	seenHandshakePackEnd bool

	tmp [16]byte
}

func (c *Conn) init() {
	c.in.isDTLS = c.isDTLS
	c.out.isDTLS = c.isDTLS
	c.in.config = c.config
	c.out.config = c.config

	c.out.updateOutSeq()
}

// Access to net.Conn methods.
// Cannot just embed net.Conn because that would
// export the struct field too.

// LocalAddr returns the local network address.
func (c *Conn) LocalAddr() net.Addr {
	return c.conn.LocalAddr()
}

// RemoteAddr returns the remote network address.
func (c *Conn) RemoteAddr() net.Addr {
	return c.conn.RemoteAddr()
}

// SetDeadline sets the read and write deadlines associated with the connection.
// A zero value for t means Read and Write will not time out.
// After a Write has timed out, the TLS state is corrupt and all future writes will return the same error.
func (c *Conn) SetDeadline(t time.Time) error {
	return c.conn.SetDeadline(t)
}

// SetReadDeadline sets the read deadline on the underlying connection.
// A zero value for t means Read will not time out.
func (c *Conn) SetReadDeadline(t time.Time) error {
	return c.conn.SetReadDeadline(t)
}

// SetWriteDeadline sets the write deadline on the underlying conneciton.
// A zero value for t means Write will not time out.
// After a Write has timed out, the TLS state is corrupt and all future writes will return the same error.
func (c *Conn) SetWriteDeadline(t time.Time) error {
	return c.conn.SetWriteDeadline(t)
}

// A halfConn represents one direction of the record layer
// connection, either sending or receiving.
type halfConn struct {
	sync.Mutex

	err         error  // first permanent error
	version     uint16 // protocol version
	wireVersion uint16 // wire version
	isDTLS      bool
	cipher      interface{} // cipher algorithm
	mac         macFunction
	seq         [8]byte // 64-bit sequence number
	outSeq      [8]byte // Mapped sequence number
	bfree       *block  // list of free blocks

	nextCipher interface{} // next encryption state
	nextMac    macFunction // next MAC algorithm
	nextSeq    [6]byte     // next epoch's starting sequence number in DTLS

	// used to save allocating a new buffer for each MAC.
	inDigestBuf, outDigestBuf []byte

	trafficSecret []byte

	config *Config
}

func (hc *halfConn) setErrorLocked(err error) error {
	hc.err = err
	return err
}

func (hc *halfConn) error() error {
	// This should be locked, but I've removed it for the renegotiation
	// tests since we don't concurrently read and write the same tls.Conn
	// in any case during testing.
	err := hc.err
	return err
}

// prepareCipherSpec sets the encryption and MAC states
// that a subsequent changeCipherSpec will use.
func (hc *halfConn) prepareCipherSpec(version uint16, cipher interface{}, mac macFunction) {
	hc.wireVersion = version
	protocolVersion, ok := wireToVersion(version, hc.isDTLS)
	if !ok {
		panic("TLS: unknown version")
	}
	hc.version = protocolVersion
	hc.nextCipher = cipher
	hc.nextMac = mac
}

// changeCipherSpec changes the encryption and MAC states
// to the ones previously passed to prepareCipherSpec.
func (hc *halfConn) changeCipherSpec(config *Config) error {
	if hc.nextCipher == nil {
		return alertInternalError
	}
	hc.cipher = hc.nextCipher
	hc.mac = hc.nextMac
	hc.nextCipher = nil
	hc.nextMac = nil
	hc.config = config
	hc.incEpoch()

	if config.Bugs.NullAllCiphers {
		hc.cipher = nullCipher{}
		hc.mac = nil
	}
	return nil
}

// useTrafficSecret sets the current cipher state for TLS 1.3.
func (hc *halfConn) useTrafficSecret(version uint16, suite *cipherSuite, secret []byte, side trafficDirection) {
	hc.wireVersion = version
	protocolVersion, ok := wireToVersion(version, hc.isDTLS)
	if !ok {
		panic("TLS: unknown version")
	}
	hc.version = protocolVersion
	hc.cipher = deriveTrafficAEAD(version, suite, secret, side)
	if hc.config.Bugs.NullAllCiphers {
		hc.cipher = nullCipher{}
	}
	hc.trafficSecret = secret
	hc.incEpoch()
}

// resetCipher changes the cipher state back to no encryption to be able
// to send an unencrypted ClientHello in response to HelloRetryRequest
// after 0-RTT data was rejected.
func (hc *halfConn) resetCipher() {
	hc.cipher = nil
	hc.incEpoch()
}

// incSeq increments the sequence number.
func (hc *halfConn) incSeq(isOutgoing bool) {
	limit := 0
	increment := uint64(1)
	if hc.isDTLS {
		// Increment up to the epoch in DTLS.
		limit = 2
	}
	for i := 7; i >= limit; i-- {
		increment += uint64(hc.seq[i])
		hc.seq[i] = byte(increment)
		increment >>= 8
	}

	// Not allowed to let sequence number wrap.
	// Instead, must renegotiate before it does.
	// Not likely enough to bother.
	if increment != 0 {
		panic("TLS: sequence number wraparound")
	}

	hc.updateOutSeq()
}

// incNextSeq increments the starting sequence number for the next epoch.
func (hc *halfConn) incNextSeq() {
	for i := len(hc.nextSeq) - 1; i >= 0; i-- {
		hc.nextSeq[i]++
		if hc.nextSeq[i] != 0 {
			return
		}
	}
	panic("TLS: sequence number wraparound")
}

// incEpoch resets the sequence number. In DTLS, it also increments the epoch
// half of the sequence number.
func (hc *halfConn) incEpoch() {
	if hc.isDTLS {
		for i := 1; i >= 0; i-- {
			hc.seq[i]++
			if hc.seq[i] != 0 {
				break
			}
			if i == 0 {
				panic("TLS: epoch number wraparound")
			}
		}
		copy(hc.seq[2:], hc.nextSeq[:])
		for i := range hc.nextSeq {
			hc.nextSeq[i] = 0
		}
	} else {
		for i := range hc.seq {
			hc.seq[i] = 0
		}
	}

	hc.updateOutSeq()
}

func (hc *halfConn) updateOutSeq() {
	if hc.config.Bugs.SequenceNumberMapping != nil {
		seqU64 := binary.BigEndian.Uint64(hc.seq[:])
		seqU64 = hc.config.Bugs.SequenceNumberMapping(seqU64)
		binary.BigEndian.PutUint64(hc.outSeq[:], seqU64)

		// The DTLS epoch cannot be changed.
		copy(hc.outSeq[:2], hc.seq[:2])
		return
	}

	copy(hc.outSeq[:], hc.seq[:])
}

func (hc *halfConn) recordHeaderLen() int {
	if hc.isDTLS {
		return dtlsRecordHeaderLen
	}
	return tlsRecordHeaderLen
}

// removePadding returns an unpadded slice, in constant time, which is a prefix
// of the input. It also returns a byte which is equal to 255 if the padding
// was valid and 0 otherwise. See RFC 2246, section 6.2.3.2
func removePadding(payload []byte) ([]byte, byte) {
	if len(payload) < 1 {
		return payload, 0
	}

	paddingLen := payload[len(payload)-1]
	t := uint(len(payload)-1) - uint(paddingLen)
	// if len(payload) >= (paddingLen - 1) then the MSB of t is zero
	good := byte(int32(^t) >> 31)

	toCheck := 255 // the maximum possible padding length
	// The length of the padded data is public, so we can use an if here
	if toCheck+1 > len(payload) {
		toCheck = len(payload) - 1
	}

	for i := 0; i < toCheck; i++ {
		t := uint(paddingLen) - uint(i)
		// if i <= paddingLen then the MSB of t is zero
		mask := byte(int32(^t) >> 31)
		b := payload[len(payload)-1-i]
		good &^= mask&paddingLen ^ mask&b
	}

	// We AND together the bits of good and replicate the result across
	// all the bits.
	good &= good << 4
	good &= good << 2
	good &= good << 1
	good = uint8(int8(good) >> 7)

	toRemove := good&paddingLen + 1
	return payload[:len(payload)-int(toRemove)], good
}

// removePaddingSSL30 is a replacement for removePadding in the case that the
// protocol version is SSLv3. In this version, the contents of the padding
// are random and cannot be checked.
func removePaddingSSL30(payload []byte) ([]byte, byte) {
	if len(payload) < 1 {
		return payload, 0
	}

	paddingLen := int(payload[len(payload)-1]) + 1
	if paddingLen > len(payload) {
		return payload, 0
	}

	return payload[:len(payload)-paddingLen], 255
}

func roundUp(a, b int) int {
	return a + (b-a%b)%b
}

// cbcMode is an interface for block ciphers using cipher block chaining.
type cbcMode interface {
	cipher.BlockMode
	SetIV([]byte)
}

// decrypt checks and strips the mac and decrypts the data in b. Returns a
// success boolean, the number of bytes to skip from the start of the record in
// order to get the application payload, the encrypted record type (or 0
// if there is none), and an optional alert value.
func (hc *halfConn) decrypt(b *block) (ok bool, prefixLen int, contentType recordType, alertValue alert) {
	recordHeaderLen := hc.recordHeaderLen()

	// pull out payload
	payload := b.data[recordHeaderLen:]

	macSize := 0
	if hc.mac != nil {
		macSize = hc.mac.Size()
	}

	paddingGood := byte(255)
	explicitIVLen := 0

	seq := hc.seq[:]
	if hc.isDTLS {
		// DTLS sequence numbers are explicit.
		seq = b.data[3:11]
	}

	// decrypt
	if hc.cipher != nil {
		switch c := hc.cipher.(type) {
		case cipher.Stream:
			c.XORKeyStream(payload, payload)
		case *tlsAead:
			nonce := seq
			if c.explicitNonce {
				explicitIVLen = 8
				if len(payload) < explicitIVLen {
					return false, 0, 0, alertBadRecordMAC
				}
				nonce = payload[:8]
				payload = payload[8:]
			}

			var additionalData []byte
			if hc.version < VersionTLS13 {
				additionalData = make([]byte, 13)
				copy(additionalData, seq)
				copy(additionalData[8:], b.data[:3])
				n := len(payload) - c.Overhead()
				additionalData[11] = byte(n >> 8)
				additionalData[12] = byte(n)
			} else {
				additionalData = b.data[:recordHeaderLen]
			}
			var err error
			payload, err = c.Open(payload[:0], nonce, payload, additionalData)
			if err != nil {
				return false, 0, 0, alertBadRecordMAC
			}
			b.resize(recordHeaderLen + explicitIVLen + len(payload))
		case cbcMode:
			blockSize := c.BlockSize()
			if hc.version >= VersionTLS11 || hc.isDTLS {
				explicitIVLen = blockSize
			}

			if len(payload)%blockSize != 0 || len(payload) < roundUp(explicitIVLen+macSize+1, blockSize) {
				return false, 0, 0, alertBadRecordMAC
			}

			if explicitIVLen > 0 {
				c.SetIV(payload[:explicitIVLen])
				payload = payload[explicitIVLen:]
			}
			c.CryptBlocks(payload, payload)
			if hc.version == VersionSSL30 {
				payload, paddingGood = removePaddingSSL30(payload)
			} else {
				payload, paddingGood = removePadding(payload)
			}
			b.resize(recordHeaderLen + explicitIVLen + len(payload))

			// note that we still have a timing side-channel in the
			// MAC check, below. An attacker can align the record
			// so that a correct padding will cause one less hash
			// block to be calculated. Then they can iteratively
			// decrypt a record by breaking each byte. See
			// "Password Interception in a SSL/TLS Channel", Brice
			// Canvel et al.
			//
			// However, our behavior matches OpenSSL, so we leak
			// only as much as they do.
		case nullCipher:
			break
		default:
			panic("unknown cipher type")
		}

		if hc.version >= VersionTLS13 {
			i := len(payload)
			for i > 0 && payload[i-1] == 0 {
				i--
			}
			payload = payload[:i]
			if len(payload) == 0 {
				return false, 0, 0, alertUnexpectedMessage
			}
			contentType = recordType(payload[len(payload)-1])
			payload = payload[:len(payload)-1]
			b.resize(recordHeaderLen + len(payload))
		}
	}

	// check, strip mac
	if hc.mac != nil {
		if len(payload) < macSize {
			return false, 0, 0, alertBadRecordMAC
		}

		// strip mac off payload, b.data
		n := len(payload) - macSize
		b.data[recordHeaderLen-2] = byte(n >> 8)
		b.data[recordHeaderLen-1] = byte(n)
		b.resize(recordHeaderLen + explicitIVLen + n)
		remoteMAC := payload[n:]
		localMAC := hc.mac.MAC(hc.inDigestBuf, seq, b.data[:3], b.data[recordHeaderLen-2:recordHeaderLen], payload[:n])

		if subtle.ConstantTimeCompare(localMAC, remoteMAC) != 1 || paddingGood != 255 {
			return false, 0, 0, alertBadRecordMAC
		}
		hc.inDigestBuf = localMAC
	}
	hc.incSeq(false)

	return true, recordHeaderLen + explicitIVLen, contentType, 0
}

// padToBlockSize calculates the needed padding block, if any, for a payload.
// On exit, prefix aliases payload and extends to the end of the last full
// block of payload. finalBlock is a fresh slice which contains the contents of
// any suffix of payload as well as the needed padding to make finalBlock a
// full block.
func padToBlockSize(payload []byte, blockSize int, config *Config) (prefix, finalBlock []byte) {
	overrun := len(payload) % blockSize
	prefix = payload[:len(payload)-overrun]

	paddingLen := blockSize - overrun
	finalSize := blockSize
	if config.Bugs.MaxPadding {
		for paddingLen+blockSize <= 256 {
			paddingLen += blockSize
		}
		finalSize = 256
	}
	finalBlock = make([]byte, finalSize)
	for i := range finalBlock {
		finalBlock[i] = byte(paddingLen - 1)
	}
	if config.Bugs.PaddingFirstByteBad || config.Bugs.PaddingFirstByteBadIf255 && paddingLen == 256 {
		finalBlock[overrun] ^= 0xff
	}
	copy(finalBlock, payload[len(payload)-overrun:])
	return
}

// encrypt encrypts and macs the data in b.
func (hc *halfConn) encrypt(b *block, explicitIVLen int, typ recordType) (bool, alert) {
	recordHeaderLen := hc.recordHeaderLen()

	// mac
	if hc.mac != nil {
		mac := hc.mac.MAC(hc.outDigestBuf, hc.outSeq[0:], b.data[:3], b.data[recordHeaderLen-2:recordHeaderLen], b.data[recordHeaderLen+explicitIVLen:])

		n := len(b.data)
		b.resize(n + len(mac))
		copy(b.data[n:], mac)
		hc.outDigestBuf = mac
	}

	payload := b.data[recordHeaderLen:]

	// encrypt
	if hc.cipher != nil {
		// Add TLS 1.3 padding.
		if hc.version >= VersionTLS13 {
			paddingLen := hc.config.Bugs.RecordPadding
			if hc.config.Bugs.OmitRecordContents {
				b.resize(recordHeaderLen + paddingLen)
			} else {
				b.resize(len(b.data) + 1 + paddingLen)
				b.data[len(b.data)-paddingLen-1] = byte(typ)
			}
			for i := 0; i < paddingLen; i++ {
				b.data[len(b.data)-paddingLen+i] = 0
			}
		}

		switch c := hc.cipher.(type) {
		case cipher.Stream:
			c.XORKeyStream(payload, payload)
		case *tlsAead:
			payloadLen := len(b.data) - recordHeaderLen - explicitIVLen
			b.resize(len(b.data) + c.Overhead())
			nonce := hc.outSeq[:]
			if c.explicitNonce {
				nonce = b.data[recordHeaderLen : recordHeaderLen+explicitIVLen]
			}
			payload := b.data[recordHeaderLen+explicitIVLen:]
			payload = payload[:payloadLen]

			var additionalData []byte
			if hc.version < VersionTLS13 {
				additionalData = make([]byte, 13)
				copy(additionalData, hc.outSeq[:])
				copy(additionalData[8:], b.data[:3])
				additionalData[11] = byte(payloadLen >> 8)
				additionalData[12] = byte(payloadLen)
			} else {
				additionalData = make([]byte, 5)
				copy(additionalData, b.data[:3])
				n := len(b.data) - recordHeaderLen
				additionalData[3] = byte(n >> 8)
				additionalData[4] = byte(n)
			}

			c.Seal(payload[:0], nonce, payload, additionalData)
		case cbcMode:
			blockSize := c.BlockSize()
			if explicitIVLen > 0 {
				c.SetIV(payload[:explicitIVLen])
				payload = payload[explicitIVLen:]
			}
			prefix, finalBlock := padToBlockSize(payload, blockSize, hc.config)
			b.resize(recordHeaderLen + explicitIVLen + len(prefix) + len(finalBlock))
			c.CryptBlocks(b.data[recordHeaderLen+explicitIVLen:], prefix)
			c.CryptBlocks(b.data[recordHeaderLen+explicitIVLen+len(prefix):], finalBlock)
		case nullCipher:
			break
		default:
			panic("unknown cipher type")
		}
	}

	// update length to include MAC and any block padding needed.
	n := len(b.data) - recordHeaderLen
	b.data[recordHeaderLen-2] = byte(n >> 8)
	b.data[recordHeaderLen-1] = byte(n)
	hc.incSeq(true)

	return true, 0
}

// A block is a simple data buffer.
type block struct {
	data []byte
	off  int // index for Read
	link *block
}

// resize resizes block to be n bytes, growing if necessary.
func (b *block) resize(n int) {
	if n > cap(b.data) {
		b.reserve(n)
	}
	b.data = b.data[0:n]
}

// reserve makes sure that block contains a capacity of at least n bytes.
func (b *block) reserve(n int) {
	if cap(b.data) >= n {
		return
	}
	m := cap(b.data)
	if m == 0 {
		m = 1024
	}
	for m < n {
		m *= 2
	}
	data := make([]byte, len(b.data), m)
	copy(data, b.data)
	b.data = data
}

// readFromUntil reads from r into b until b contains at least n bytes
// or else returns an error.
func (b *block) readFromUntil(r io.Reader, n int) error {
	// quick case
	if len(b.data) >= n {
		return nil
	}

	// read until have enough.
	b.reserve(n)
	for {
		m, err := r.Read(b.data[len(b.data):cap(b.data)])
		b.data = b.data[0 : len(b.data)+m]
		if len(b.data) >= n {
			// TODO(bradfitz,agl): slightly suspicious
			// that we're throwing away r.Read's err here.
			break
		}
		if err != nil {
			return err
		}
	}
	return nil
}

func (b *block) Read(p []byte) (n int, err error) {
	n = copy(p, b.data[b.off:])
	b.off += n
	return
}

// newBlock allocates a new block, from hc's free list if possible.
func (hc *halfConn) newBlock() *block {
	b := hc.bfree
	if b == nil {
		return new(block)
	}
	hc.bfree = b.link
	b.link = nil
	b.resize(0)
	return b
}

// freeBlock returns a block to hc's free list.
// The protocol is such that each side only has a block or two on
// its free list at a time, so there's no need to worry about
// trimming the list, etc.
func (hc *halfConn) freeBlock(b *block) {
	b.link = hc.bfree
	hc.bfree = b
}

// splitBlock splits a block after the first n bytes,
// returning a block with those n bytes and a
// block with the remainder.  the latter may be nil.
func (hc *halfConn) splitBlock(b *block, n int) (*block, *block) {
	if len(b.data) <= n {
		return b, nil
	}
	bb := hc.newBlock()
	bb.resize(len(b.data) - n)
	copy(bb.data, b.data[n:])
	b.data = b.data[0:n]
	return b, bb
}

func (c *Conn) useInTrafficSecret(version uint16, suite *cipherSuite, secret []byte) error {
	if c.hand.Len() != 0 {
		return c.in.setErrorLocked(errors.New("tls: buffered handshake messages on cipher change"))
	}
	side := serverWrite
	if !c.isClient {
		side = clientWrite
	}
	c.in.useTrafficSecret(version, suite, secret, side)
	c.seenHandshakePackEnd = false
	return nil
}

func (c *Conn) useOutTrafficSecret(version uint16, suite *cipherSuite, secret []byte) {
	side := serverWrite
	if c.isClient {
		side = clientWrite
	}
	c.out.useTrafficSecret(version, suite, secret, side)
}

func (c *Conn) doReadRecord(want recordType) (recordType, *block, error) {
RestartReadRecord:
	if c.isDTLS {
		return c.dtlsDoReadRecord(want)
	}

	recordHeaderLen := c.in.recordHeaderLen()

	if c.rawInput == nil {
		c.rawInput = c.in.newBlock()
	}
	b := c.rawInput

	// Read header, payload.
	if err := b.readFromUntil(c.conn, recordHeaderLen); err != nil {
		// RFC suggests that EOF without an alertCloseNotify is
		// an error, but popular web sites seem to do this,
		// so we can't make it an error, outside of tests.
		if err == io.EOF && c.config.Bugs.ExpectCloseNotify {
			err = io.ErrUnexpectedEOF
		}
		if e, ok := err.(net.Error); !ok || !e.Temporary() {
			c.in.setErrorLocked(err)
		}
		return 0, nil, err
	}

	typ := recordType(b.data[0])

	// No valid TLS record has a type of 0x80, however SSLv2 handshakes
	// start with a uint16 length where the MSB is set and the first record
	// is always < 256 bytes long. Therefore typ == 0x80 strongly suggests
	// an SSLv2 client.
	if want == recordTypeHandshake && typ == 0x80 {
		c.sendAlert(alertProtocolVersion)
		return 0, nil, c.in.setErrorLocked(errors.New("tls: unsupported SSLv2 handshake received"))
	}

	vers := uint16(b.data[1])<<8 | uint16(b.data[2])
	n := int(b.data[3])<<8 | int(b.data[4])

	// Alerts sent near version negotiation do not have a well-defined
	// record-layer version prior to TLS 1.3. (In TLS 1.3, the record-layer
	// version is irrelevant.)
	if typ != recordTypeAlert {
		var expect uint16
		if c.haveVers {
			expect = c.vers
			if c.vers >= VersionTLS13 {
				expect = VersionTLS12
			}
		} else {
			expect = c.config.Bugs.ExpectInitialRecordVersion
		}
		if expect != 0 && vers != expect {
			c.sendAlert(alertProtocolVersion)
			return 0, nil, c.in.setErrorLocked(fmt.Errorf("tls: received record with version %x when expecting version %x", vers, expect))
		}
	}
	if n > maxCiphertext {
		c.sendAlert(alertRecordOverflow)
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("tls: oversized record received with length %d", n))
	}
	if !c.haveVers {
		// First message, be extra suspicious:
		// this might not be a TLS client.
		// Bail out before reading a full 'body', if possible.
		// The current max version is 3.1.
		// If the version is >= 16.0, it's probably not real.
		// Similarly, a clientHello message encodes in
		// well under a kilobyte.  If the length is >= 12 kB,
		// it's probably not real.
		if (typ != recordTypeAlert && typ != want) || vers >= 0x1000 || n >= 0x3000 {
			c.sendAlert(alertUnexpectedMessage)
			return 0, nil, c.in.setErrorLocked(fmt.Errorf("tls: first record does not look like a TLS handshake"))
		}
	}
	if err := b.readFromUntil(c.conn, recordHeaderLen+n); err != nil {
		if err == io.EOF {
			err = io.ErrUnexpectedEOF
		}
		if e, ok := err.(net.Error); !ok || !e.Temporary() {
			c.in.setErrorLocked(err)
		}
		return 0, nil, err
	}

	// Process message.
	b, c.rawInput = c.in.splitBlock(b, recordHeaderLen+n)
	ok, off, encTyp, alertValue := c.in.decrypt(b)

	// Handle skipping over early data.
	if !ok && c.skipEarlyData {
		goto RestartReadRecord
	}

	// If the server is expecting a second ClientHello (in response to
	// a HelloRetryRequest) and the client sends early data, there
	// won't be a decryption failure but it still needs to be skipped.
	if c.in.cipher == nil && typ == recordTypeApplicationData && c.skipEarlyData {
		goto RestartReadRecord
	}

	if !ok {
		return 0, nil, c.in.setErrorLocked(c.sendAlert(alertValue))
	}
	b.off = off
	c.skipEarlyData = false

	if c.vers >= VersionTLS13 && c.in.cipher != nil {
		if typ != recordTypeApplicationData {
			return 0, nil, c.in.setErrorLocked(fmt.Errorf("tls: outer record type is not application data"))
		}
		typ = encTyp
	}

	length := len(b.data[b.off:])
	if c.config.Bugs.ExpectRecordSplitting && typ == recordTypeApplicationData && length != 1 && !c.seenOneByteRecord {
		return 0, nil, c.in.setErrorLocked(fmt.Errorf("tls: application data records were not split"))
	}

	c.seenOneByteRecord = typ == recordTypeApplicationData && length == 1
	return typ, b, nil
}

func (c *Conn) readTLS13ChangeCipherSpec() error {
	if !c.expectTLS13ChangeCipherSpec {
		panic("c.expectTLS13ChangeCipherSpec not set")
	}

	// Read the ChangeCipherSpec.
	if c.rawInput == nil {
		c.rawInput = c.in.newBlock()
	}
	b := c.rawInput
	if err := b.readFromUntil(c.conn, 1); err != nil {
		return c.in.setErrorLocked(fmt.Errorf("tls: error reading TLS 1.3 ChangeCipherSpec: %s", err))
	}
	if recordType(b.data[0]) == recordTypeAlert {
		// If the client is sending an alert, allow the ChangeCipherSpec
		// to be skipped. It may be rejecting a sufficiently malformed
		// ServerHello that it can't parse out the version.
		c.expectTLS13ChangeCipherSpec = false
		return nil
	}
	if err := b.readFromUntil(c.conn, 6); err != nil {
		return c.in.setErrorLocked(fmt.Errorf("tls: error reading TLS 1.3 ChangeCipherSpec: %s", err))
	}

	// Check they match that we expect.
	expected := [6]byte{byte(recordTypeChangeCipherSpec), 3, 1, 0, 1, 1}
	if c.vers >= VersionTLS13 {
		expected[2] = 3
	}
	if !bytes.Equal(b.data[:6], expected[:]) {
		return c.in.setErrorLocked(fmt.Errorf("tls: error invalid TLS 1.3 ChangeCipherSpec: %x", b.data[:6]))
	}

	// Discard the data.
	b, c.rawInput = c.in.splitBlock(b, 6)
	c.in.freeBlock(b)

	c.expectTLS13ChangeCipherSpec = false
	return nil
}

// readRecord reads the next TLS record from the connection
// and updates the record layer state.
// c.in.Mutex <= L; c.input == nil.
func (c *Conn) readRecord(want recordType) error {
	// Caller must be in sync with connection:
	// handshake data if handshake not yet completed,
	// else application data.
	switch want {
	default:
		c.sendAlert(alertInternalError)
		return c.in.setErrorLocked(errors.New("tls: unknown record type requested"))
	case recordTypeChangeCipherSpec:
		if c.handshakeComplete {
			c.sendAlert(alertInternalError)
			return c.in.setErrorLocked(errors.New("tls: ChangeCipherSpec requested after handshake complete"))
		}
	case recordTypeApplicationData, recordTypeAlert, recordTypeHandshake:
		break
	}

	if c.expectTLS13ChangeCipherSpec {
		if err := c.readTLS13ChangeCipherSpec(); err != nil {
			return err
		}
	}

Again:
	typ, b, err := c.doReadRecord(want)
	if err != nil {
		return err
	}
	data := b.data[b.off:]
	max := maxPlaintext
	if c.config.Bugs.MaxReceivePlaintext != 0 {
		max = c.config.Bugs.MaxReceivePlaintext
	}
	if len(data) > max {
		err := c.sendAlert(alertRecordOverflow)
		c.in.freeBlock(b)
		return c.in.setErrorLocked(err)
	}

	if typ != recordTypeHandshake {
		c.seenHandshakePackEnd = false
	} else if c.seenHandshakePackEnd {
		c.in.freeBlock(b)
		return c.in.setErrorLocked(errors.New("tls: peer violated ExpectPackedEncryptedHandshake"))
	}

	switch typ {
	default:
		c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))

	case recordTypeAlert:
		if len(data) != 2 {
			c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
			break
		}
		if alert(data[1]) == alertCloseNotify {
			c.in.setErrorLocked(io.EOF)
			break
		}
		switch data[0] {
		case alertLevelWarning:
			if alert(data[1]) == alertNoCertificate {
				c.in.freeBlock(b)
				return errNoCertificateAlert
			}
			if alert(data[1]) == alertEndOfEarlyData {
				c.in.freeBlock(b)
				return errEndOfEarlyDataAlert
			}

			// drop on the floor
			c.in.freeBlock(b)
			goto Again
		case alertLevelError:
			c.in.setErrorLocked(&net.OpError{Op: "remote error", Err: alert(data[1])})
		default:
			c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}

	case recordTypeChangeCipherSpec:
		if typ != want || len(data) != 1 || data[0] != 1 {
			c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
			break
		}
		if c.hand.Len() != 0 {
			c.in.setErrorLocked(errors.New("tls: buffered handshake messages on cipher change"))
			break
		}
		if err := c.in.changeCipherSpec(c.config); err != nil {
			c.in.setErrorLocked(c.sendAlert(err.(alert)))
		}

	case recordTypeApplicationData:
		if typ != want {
			c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
			break
		}
		c.input = b
		b = nil

	case recordTypeHandshake:
		// Allow handshake data while reading application data to
		// trigger post-handshake messages.
		// TODO(rsc): Should at least pick off connection close.
		if typ != want && want != recordTypeApplicationData {
			return c.in.setErrorLocked(c.sendAlert(alertNoRenegotiation))
		}
		c.hand.Write(data)
		if pack := c.config.Bugs.ExpectPackedEncryptedHandshake; pack > 0 && len(data) < pack && c.out.cipher != nil {
			c.seenHandshakePackEnd = true
		}
	}

	if b != nil {
		c.in.freeBlock(b)
	}
	return c.in.err
}

// sendAlert sends a TLS alert message.
// c.out.Mutex <= L.
func (c *Conn) sendAlertLocked(level byte, err alert) error {
	c.tmp[0] = level
	c.tmp[1] = byte(err)
	if c.config.Bugs.FragmentAlert {
		c.writeRecord(recordTypeAlert, c.tmp[0:1])
		c.writeRecord(recordTypeAlert, c.tmp[1:2])
	} else if c.config.Bugs.DoubleAlert {
		copy(c.tmp[2:4], c.tmp[0:2])
		c.writeRecord(recordTypeAlert, c.tmp[0:4])
	} else {
		c.writeRecord(recordTypeAlert, c.tmp[0:2])
	}
	// Error alerts are fatal to the connection.
	if level == alertLevelError {
		return c.out.setErrorLocked(&net.OpError{Op: "local error", Err: err})
	}
	return nil
}

// sendAlert sends a TLS alert message.
// L < c.out.Mutex.
func (c *Conn) sendAlert(err alert) error {
	level := byte(alertLevelError)
	if err == alertNoRenegotiation || err == alertCloseNotify || err == alertNoCertificate || err == alertEndOfEarlyData {
		level = alertLevelWarning
	}
	return c.SendAlert(level, err)
}

func (c *Conn) SendAlert(level byte, err alert) error {
	c.out.Lock()
	defer c.out.Unlock()
	return c.sendAlertLocked(level, err)
}

// writeV2Record writes a record for a V2ClientHello.
func (c *Conn) writeV2Record(data []byte) (n int, err error) {
	record := make([]byte, 2+len(data))
	record[0] = uint8(len(data)>>8) | 0x80
	record[1] = uint8(len(data))
	copy(record[2:], data)
	return c.conn.Write(record)
}

// writeRecord writes a TLS record with the given type and payload
// to the connection and updates the record layer state.
// c.out.Mutex <= L.
func (c *Conn) writeRecord(typ recordType, data []byte) (n int, err error) {
	c.seenHandshakePackEnd = false
	if typ == recordTypeHandshake {
		msgType := data[0]
		if c.config.Bugs.SendWrongMessageType != 0 && msgType == c.config.Bugs.SendWrongMessageType {
			msgType += 42
		} else if msgType == typeServerHello && c.config.Bugs.SendServerHelloAsHelloRetryRequest {
			msgType = typeHelloRetryRequest
		}
		if msgType != data[0] {
			newData := make([]byte, len(data))
			copy(newData, data)
			newData[0] = msgType
			data = newData
		}

		if c.config.Bugs.SendTrailingMessageData != 0 && msgType == c.config.Bugs.SendTrailingMessageData {
			newData := make([]byte, len(data))
			copy(newData, data)

			// Add a 0 to the body.
			newData = append(newData, 0)
			// Fix the header.
			newLen := len(newData) - 4
			newData[1] = byte(newLen >> 16)
			newData[2] = byte(newLen >> 8)
			newData[3] = byte(newLen)

			data = newData
		}
	}

	if c.isDTLS {
		return c.dtlsWriteRecord(typ, data)
	}

	if typ == recordTypeHandshake {
		if c.config.Bugs.SendHelloRequestBeforeEveryHandshakeMessage {
			newData := make([]byte, 0, 4+len(data))
			newData = append(newData, typeHelloRequest, 0, 0, 0)
			newData = append(newData, data...)
			data = newData
		}

		if c.config.Bugs.PackHandshakeFlight {
			c.pendingFlight.Write(data)
			return len(data), nil
		}
	}

	// Flush buffered data before writing anything.
	if err := c.flushHandshake(); err != nil {
		return 0, err
	}

	if typ == recordTypeApplicationData && c.config.Bugs.SendPostHandshakeChangeCipherSpec {
		if _, err := c.doWriteRecord(recordTypeChangeCipherSpec, []byte{1}); err != nil {
			return 0, err
		}
	}

	return c.doWriteRecord(typ, data)
}

func (c *Conn) doWriteRecord(typ recordType, data []byte) (n int, err error) {
	recordHeaderLen := c.out.recordHeaderLen()
	b := c.out.newBlock()
	first := true
	isClientHello := typ == recordTypeHandshake && len(data) > 0 && data[0] == typeClientHello
	for len(data) > 0 || first {
		m := len(data)
		if m > maxPlaintext && !c.config.Bugs.SendLargeRecords {
			m = maxPlaintext
		}
		if typ == recordTypeHandshake && c.config.Bugs.MaxHandshakeRecordLength > 0 && m > c.config.Bugs.MaxHandshakeRecordLength {
			m = c.config.Bugs.MaxHandshakeRecordLength
			// By default, do not fragment the client_version or
			// server_version, which are located in the first 6
			// bytes.
			if first && isClientHello && !c.config.Bugs.FragmentClientVersion && m < 6 {
				m = 6
			}
		}
		explicitIVLen := 0
		explicitIVIsSeq := false
		first = false

		var cbc cbcMode
		if c.out.version >= VersionTLS11 {
			var ok bool
			if cbc, ok = c.out.cipher.(cbcMode); ok {
				explicitIVLen = cbc.BlockSize()
			}
		}
		if explicitIVLen == 0 {
			if aead, ok := c.out.cipher.(*tlsAead); ok && aead.explicitNonce {
				explicitIVLen = 8
				// The AES-GCM construction in TLS has an
				// explicit nonce so that the nonce can be
				// random. However, the nonce is only 8 bytes
				// which is too small for a secure, random
				// nonce. Therefore we use the sequence number
				// as the nonce.
				explicitIVIsSeq = true
			}
		}
		b.resize(recordHeaderLen + explicitIVLen + m)
		b.data[0] = byte(typ)
		if c.vers >= VersionTLS13 && c.out.cipher != nil {
			b.data[0] = byte(recordTypeApplicationData)
			if outerType := c.config.Bugs.OuterRecordType; outerType != 0 {
				b.data[0] = byte(outerType)
			}
		}
		vers := c.vers
		if vers == 0 {
			// Some TLS servers fail if the record version is
			// greater than TLS 1.0 for the initial ClientHello.
			//
			// TLS 1.3 fixes the version number in the record
			// layer to {3, 1}.
			vers = VersionTLS10
		}
		if c.vers >= VersionTLS13 || c.out.version >= VersionTLS13 {
			vers = VersionTLS12
		}

		if c.config.Bugs.SendRecordVersion != 0 {
			vers = c.config.Bugs.SendRecordVersion
		}
		if c.vers == 0 && c.config.Bugs.SendInitialRecordVersion != 0 {
			vers = c.config.Bugs.SendInitialRecordVersion
		}
		b.data[1] = byte(vers >> 8)
		b.data[2] = byte(vers)
		b.data[3] = byte(m >> 8)
		b.data[4] = byte(m)
		if explicitIVLen > 0 {
			explicitIV := b.data[recordHeaderLen : recordHeaderLen+explicitIVLen]
			if explicitIVIsSeq {
				copy(explicitIV, c.out.seq[:])
			} else {
				if _, err = io.ReadFull(c.config.rand(), explicitIV); err != nil {
					break
				}
			}
		}
		copy(b.data[recordHeaderLen+explicitIVLen:], data)
		c.out.encrypt(b, explicitIVLen, typ)
		_, err = c.conn.Write(b.data)
		if err != nil {
			break
		}
		n += m
		data = data[m:]
	}
	c.out.freeBlock(b)

	if typ == recordTypeChangeCipherSpec && c.vers < VersionTLS13 {
		err = c.out.changeCipherSpec(c.config)
		if err != nil {
			return n, c.sendAlertLocked(alertLevelError, err.(alert))
		}
	}
	return
}

func (c *Conn) flushHandshake() error {
	if c.isDTLS {
		return c.dtlsFlushHandshake()
	}

	for c.pendingFlight.Len() > 0 {
		var buf [maxPlaintext]byte
		n, _ := c.pendingFlight.Read(buf[:])
		if _, err := c.doWriteRecord(recordTypeHandshake, buf[:n]); err != nil {
			return err
		}
	}

	c.pendingFlight.Reset()
	return nil
}

func (c *Conn) doReadHandshake() ([]byte, error) {
	if c.isDTLS {
		return c.dtlsDoReadHandshake()
	}

	for c.hand.Len() < 4 {
		if err := c.in.err; err != nil {
			return nil, err
		}
		if err := c.readRecord(recordTypeHandshake); err != nil {
			return nil, err
		}
	}

	data := c.hand.Bytes()
	n := int(data[1])<<16 | int(data[2])<<8 | int(data[3])
	if n > maxHandshake {
		return nil, c.in.setErrorLocked(c.sendAlert(alertInternalError))
	}
	for c.hand.Len() < 4+n {
		if err := c.in.err; err != nil {
			return nil, err
		}
		if err := c.readRecord(recordTypeHandshake); err != nil {
			return nil, err
		}
	}
	return c.hand.Next(4 + n), nil
}

// readHandshake reads the next handshake message from
// the record layer.
// c.in.Mutex < L; c.out.Mutex < L.
func (c *Conn) readHandshake() (interface{}, error) {
	data, err := c.doReadHandshake()
	if err == errNoCertificateAlert {
		if c.hand.Len() != 0 {
			// The warning alert may not interleave with a handshake message.
			return nil, c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
		}
		return new(ssl3NoCertificateMsg), nil
	}
	if err != nil {
		return nil, err
	}

	var m handshakeMessage
	switch data[0] {
	case typeHelloRequest:
		m = new(helloRequestMsg)
	case typeClientHello:
		m = &clientHelloMsg{
			isDTLS: c.isDTLS,
		}
	case typeServerHello:
		m = &serverHelloMsg{
			isDTLS: c.isDTLS,
		}
	case typeHelloRetryRequest:
		m = new(helloRetryRequestMsg)
	case typeNewSessionTicket:
		m = &newSessionTicketMsg{
			vers:   c.wireVersion,
			isDTLS: c.isDTLS,
		}
	case typeEncryptedExtensions:
		m = new(encryptedExtensionsMsg)
	case typeCertificate:
		m = &certificateMsg{
			hasRequestContext: c.vers >= VersionTLS13,
		}
	case typeCompressedCertificate:
		m = new(compressedCertificateMsg)
	case typeCertificateRequest:
		m = &certificateRequestMsg{
			vers:                  c.wireVersion,
			hasSignatureAlgorithm: c.vers >= VersionTLS12,
			hasRequestContext:     c.vers >= VersionTLS13,
		}
	case typeCertificateStatus:
		m = new(certificateStatusMsg)
	case typeServerKeyExchange:
		m = new(serverKeyExchangeMsg)
	case typeServerHelloDone:
		m = new(serverHelloDoneMsg)
	case typeClientKeyExchange:
		m = new(clientKeyExchangeMsg)
	case typeCertificateVerify:
		m = &certificateVerifyMsg{
			hasSignatureAlgorithm: c.vers >= VersionTLS12,
		}
	case typeNextProtocol:
		m = new(nextProtoMsg)
	case typeFinished:
		m = new(finishedMsg)
	case typeHelloVerifyRequest:
		m = new(helloVerifyRequestMsg)
	case typeChannelID:
		m = new(channelIDMsg)
	case typeKeyUpdate:
		m = new(keyUpdateMsg)
	case typeEndOfEarlyData:
		m = new(endOfEarlyDataMsg)
	default:
		return nil, c.in.setErrorLocked(c.sendAlert(alertUnexpectedMessage))
	}

	// The handshake message unmarshallers
	// expect to be able to keep references to data,
	// so pass in a fresh copy that won't be overwritten.
	data = append([]byte(nil), data...)

	if data[0] == typeServerHello && len(data) >= 38 {
		vers := uint16(data[4])<<8 | uint16(data[5])
		if vers == VersionTLS12 && bytes.Equal(data[6:38], tls13HelloRetryRequest) {
			m = new(helloRetryRequestMsg)
			m.(*helloRetryRequestMsg).isServerHello = true
		}
	}

	if !m.unmarshal(data) {
		return nil, c.in.setErrorLocked(c.sendAlert(alertDecodeError))
	}
	return m, nil
}

// skipPacket processes all the DTLS records in packet. It updates
// sequence number expectations but otherwise ignores them.
func (c *Conn) skipPacket(packet []byte) error {
	for len(packet) > 0 {
		if len(packet) < 13 {
			return errors.New("tls: bad packet")
		}
		// Dropped packets are completely ignored save to update
		// expected sequence numbers for this and the next epoch. (We
		// don't assert on the contents of the packets both for
		// simplicity and because a previous test with one shorter
		// timeout schedule would have done so.)
		epoch := packet[3:5]
		seq := packet[5:11]
		length := uint16(packet[11])<<8 | uint16(packet[12])
		if bytes.Equal(c.in.seq[:2], epoch) {
			if bytes.Compare(seq, c.in.seq[2:]) < 0 {
				return errors.New("tls: sequence mismatch")
			}
			copy(c.in.seq[2:], seq)
			c.in.incSeq(false)
		} else {
			if bytes.Compare(seq, c.in.nextSeq[:]) < 0 {
				return errors.New("tls: sequence mismatch")
			}
			copy(c.in.nextSeq[:], seq)
			c.in.incNextSeq()
		}
		if len(packet) < 13+int(length) {
			return errors.New("tls: bad packet")
		}
		packet = packet[13+length:]
	}
	return nil
}

// simulatePacketLoss simulates the loss of a handshake leg from the
// peer based on the schedule in c.config.Bugs. If resendFunc is
// non-nil, it is called after each simulated timeout to retransmit
// handshake messages from the local end. This is used in cases where
// the peer retransmits on a stale Finished rather than a timeout.
func (c *Conn) simulatePacketLoss(resendFunc func()) error {
	if len(c.config.Bugs.TimeoutSchedule) == 0 {
		return nil
	}
	if !c.isDTLS {
		return errors.New("tls: TimeoutSchedule may only be set in DTLS")
	}
	if c.config.Bugs.PacketAdaptor == nil {
		return errors.New("tls: TimeoutSchedule set without PacketAdapter")
	}
	for _, timeout := range c.config.Bugs.TimeoutSchedule {
		// Simulate a timeout.
		packets, err := c.config.Bugs.PacketAdaptor.SendReadTimeout(timeout)
		if err != nil {
			return err
		}
		for _, packet := range packets {
			if err := c.skipPacket(packet); err != nil {
				return err
			}
		}
		if resendFunc != nil {
			resendFunc()
		}
	}
	return nil
}

func (c *Conn) SendHalfHelloRequest() error {
	if err := c.Handshake(); err != nil {
		return err
	}

	c.out.Lock()
	defer c.out.Unlock()

	if _, err := c.writeRecord(recordTypeHandshake, []byte{typeHelloRequest, 0}); err != nil {
		return err
	}
	return c.flushHandshake()
}

// Write writes data to the connection.
func (c *Conn) Write(b []byte) (int, error) {
	if err := c.Handshake(); err != nil {
		return 0, err
	}

	c.out.Lock()
	defer c.out.Unlock()

	if err := c.out.err; err != nil {
		return 0, err
	}

	if !c.handshakeComplete {
		return 0, alertInternalError
	}

	if c.keyUpdateRequested {
		if err := c.sendKeyUpdateLocked(keyUpdateNotRequested); err != nil {
			return 0, err
		}
		c.keyUpdateRequested = false
	}

	if c.config.Bugs.SendSpuriousAlert != 0 {
		c.sendAlertLocked(alertLevelError, c.config.Bugs.SendSpuriousAlert)
	}

	if c.config.Bugs.SendHelloRequestBeforeEveryAppDataRecord {
		c.writeRecord(recordTypeHandshake, []byte{typeHelloRequest, 0, 0, 0})
		c.flushHandshake()
	}

	// SSL 3.0 and TLS 1.0 are susceptible to a chosen-plaintext
	// attack when using block mode ciphers due to predictable IVs.
	// This can be prevented by splitting each Application Data
	// record into two records, effectively randomizing the IV.
	//
	// http://www.openssl.org/~bodo/tls-cbc.txt
	// https://bugzilla.mozilla.org/show_bug.cgi?id=665814
	// http://www.imperialviolet.org/2012/01/15/beastfollowup.html

	var m int
	if len(b) > 1 && c.vers <= VersionTLS10 && !c.isDTLS {
		if _, ok := c.out.cipher.(cipher.BlockMode); ok {
			n, err := c.writeRecord(recordTypeApplicationData, b[:1])
			if err != nil {
				return n, c.out.setErrorLocked(err)
			}
			m, b = 1, b[1:]
		}
	}

	n, err := c.writeRecord(recordTypeApplicationData, b)
	return n + m, c.out.setErrorLocked(err)
}

func (c *Conn) processTLS13NewSessionTicket(newSessionTicket *newSessionTicketMsg, cipherSuite *cipherSuite) error {
	if c.config.Bugs.ExpectGREASE && !newSessionTicket.hasGREASEExtension {
		return errors.New("tls: no GREASE ticket extension found")
	}

	if c.config.Bugs.ExpectTicketEarlyData && newSessionTicket.maxEarlyDataSize == 0 {
		return errors.New("tls: no early_data ticket extension found")
	}

	if c.config.Bugs.ExpectNoNewSessionTicket {
		return errors.New("tls: received unexpected NewSessionTicket")
	}

	if c.config.ClientSessionCache == nil || newSessionTicket.ticketLifetime == 0 {
		return nil
	}

	session := &ClientSessionState{
		sessionTicket:      newSessionTicket.ticket,
		vers:               c.vers,
		wireVersion:        c.wireVersion,
		cipherSuite:        cipherSuite.id,
		masterSecret:       c.resumptionSecret,
		serverCertificates: c.peerCertificates,
		sctList:            c.sctList,
		ocspResponse:       c.ocspResponse,
		ticketCreationTime: c.config.time(),
		ticketExpiration:   c.config.time().Add(time.Duration(newSessionTicket.ticketLifetime) * time.Second),
		ticketAgeAdd:       newSessionTicket.ticketAgeAdd,
		maxEarlyDataSize:   newSessionTicket.maxEarlyDataSize,
		earlyALPN:          c.clientProtocol,
	}

	session.masterSecret = deriveSessionPSK(cipherSuite, c.wireVersion, c.resumptionSecret, newSessionTicket.ticketNonce)

	cacheKey := clientSessionCacheKey(c.conn.RemoteAddr(), c.config)
	_, ok := c.config.ClientSessionCache.Get(cacheKey)
	if !ok || !c.config.Bugs.UseFirstSessionTicket {
		c.config.ClientSessionCache.Put(cacheKey, session)
	}
	return nil
}

func (c *Conn) handlePostHandshakeMessage() error {
	msg, err := c.readHandshake()
	if err != nil {
		return err
	}

	if c.vers < VersionTLS13 {
		if !c.isClient {
			c.sendAlert(alertUnexpectedMessage)
			return errors.New("tls: unexpected post-handshake message")
		}

		_, ok := msg.(*helloRequestMsg)
		if !ok {
			c.sendAlert(alertUnexpectedMessage)
			return alertUnexpectedMessage
		}

		c.handshakeComplete = false
		return c.Handshake()
	}

	if c.isClient {
		if newSessionTicket, ok := msg.(*newSessionTicketMsg); ok {
			return c.processTLS13NewSessionTicket(newSessionTicket, c.cipherSuite)
		}
	}

	if keyUpdate, ok := msg.(*keyUpdateMsg); ok {
		c.keyUpdateSeen = true

		if c.config.Bugs.RejectUnsolicitedKeyUpdate {
			return errors.New("tls: unexpected KeyUpdate message")
		}
		if err := c.useInTrafficSecret(c.in.wireVersion, c.cipherSuite, updateTrafficSecret(c.cipherSuite.hash(), c.wireVersion, c.in.trafficSecret)); err != nil {
			return err
		}
		if keyUpdate.keyUpdateRequest == keyUpdateRequested {
			c.keyUpdateRequested = true
		}
		return nil
	}

	c.sendAlert(alertUnexpectedMessage)
	return errors.New("tls: unexpected post-handshake message")
}

// Reads a KeyUpdate acknowledgment from the peer. There may not be any
// application data records before the message.
func (c *Conn) ReadKeyUpdateACK() error {
	c.in.Lock()
	defer c.in.Unlock()

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}

	keyUpdate, ok := msg.(*keyUpdateMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return fmt.Errorf("tls: unexpected message (%T) when reading KeyUpdate", msg)
	}

	if keyUpdate.keyUpdateRequest != keyUpdateNotRequested {
		return errors.New("tls: received invalid KeyUpdate message")
	}

	return c.useInTrafficSecret(c.in.wireVersion, c.cipherSuite, updateTrafficSecret(c.cipherSuite.hash(), c.wireVersion, c.in.trafficSecret))
}

func (c *Conn) Renegotiate() error {
	if !c.isClient {
		helloReq := new(helloRequestMsg).marshal()
		if c.config.Bugs.BadHelloRequest != nil {
			helloReq = c.config.Bugs.BadHelloRequest
		}
		c.writeRecord(recordTypeHandshake, helloReq)
		c.flushHandshake()
	}

	c.handshakeComplete = false
	return c.Handshake()
}

// Read can be made to time out and return a net.Error with Timeout() == true
// after a fixed time limit; see SetDeadline and SetReadDeadline.
func (c *Conn) Read(b []byte) (n int, err error) {
	if err = c.Handshake(); err != nil {
		return
	}

	c.in.Lock()
	defer c.in.Unlock()

	// Some OpenSSL servers send empty records in order to randomize the
	// CBC IV. So this loop ignores a limited number of empty records.
	const maxConsecutiveEmptyRecords = 100
	for emptyRecordCount := 0; emptyRecordCount <= maxConsecutiveEmptyRecords; emptyRecordCount++ {
		for c.input == nil && c.in.err == nil {
			if err := c.readRecord(recordTypeApplicationData); err != nil {
				// Soft error, like EAGAIN
				return 0, err
			}
			for c.hand.Len() > 0 {
				// We received handshake bytes, indicating a
				// post-handshake message.
				if err := c.handlePostHandshakeMessage(); err != nil {
					return 0, err
				}
			}
		}
		if err := c.in.err; err != nil {
			return 0, err
		}

		n, err = c.input.Read(b)
		if c.input.off >= len(c.input.data) || c.isDTLS {
			c.in.freeBlock(c.input)
			c.input = nil
		}

		// If a close-notify alert is waiting, read it so that
		// we can return (n, EOF) instead of (n, nil), to signal
		// to the HTTP response reading goroutine that the
		// connection is now closed. This eliminates a race
		// where the HTTP response reading goroutine would
		// otherwise not observe the EOF until its next read,
		// by which time a client goroutine might have already
		// tried to reuse the HTTP connection for a new
		// request.
		// See https://codereview.appspot.com/76400046
		// and http://golang.org/issue/3514
		if ri := c.rawInput; ri != nil &&
			n != 0 && err == nil &&
			c.input == nil && len(ri.data) > 0 && recordType(ri.data[0]) == recordTypeAlert {
			if recErr := c.readRecord(recordTypeApplicationData); recErr != nil {
				err = recErr // will be io.EOF on closeNotify
			}
		}

		if n != 0 || err != nil {
			return n, err
		}
	}

	return 0, io.ErrNoProgress
}

// Close closes the connection.
func (c *Conn) Close() error {
	var alertErr error

	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()
	if c.handshakeComplete && !c.config.Bugs.NoCloseNotify {
		alert := alertCloseNotify
		if c.config.Bugs.SendAlertOnShutdown != 0 {
			alert = c.config.Bugs.SendAlertOnShutdown
		}
		alertErr = c.sendAlert(alert)
		// Clear local alerts when sending alerts so we continue to wait
		// for the peer rather than closing the socket early.
		if opErr, ok := alertErr.(*net.OpError); ok && opErr.Op == "local error" {
			alertErr = nil
		}
	}

	// Consume a close_notify from the peer if one hasn't been received
	// already. This avoids the peer from failing |SSL_shutdown| due to a
	// write failing.
	if c.handshakeComplete && alertErr == nil && c.config.Bugs.ExpectCloseNotify {
		for c.in.error() == nil {
			c.readRecord(recordTypeAlert)
		}
		if c.in.error() != io.EOF {
			alertErr = c.in.error()
		}
	}

	if err := c.conn.Close(); err != nil {
		return err
	}
	return alertErr
}

// Handshake runs the client or server handshake
// protocol if it has not yet been run.
// Most uses of this package need not call Handshake
// explicitly: the first Read or Write will call it automatically.
func (c *Conn) Handshake() error {
	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()
	if err := c.handshakeErr; err != nil {
		return err
	}
	if c.handshakeComplete {
		return nil
	}

	if c.isDTLS && c.config.Bugs.SendSplitAlert {
		c.conn.Write([]byte{
			byte(recordTypeAlert), // type
			0xfe, 0xff,            // version
			0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, // sequence
			0x0, 0x2, // length
		})
		c.conn.Write([]byte{alertLevelError, byte(alertInternalError)})
	}
	if data := c.config.Bugs.AppDataBeforeHandshake; data != nil {
		c.writeRecord(recordTypeApplicationData, data)
	}
	if c.isClient {
		c.handshakeErr = c.clientHandshake()
	} else {
		c.handshakeErr = c.serverHandshake()
	}
	if c.handshakeErr == nil && c.config.Bugs.SendInvalidRecordType {
		c.writeRecord(recordType(42), []byte("invalid record"))
	}
	return c.handshakeErr
}

// ConnectionState returns basic TLS details about the connection.
func (c *Conn) ConnectionState() ConnectionState {
	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()

	var state ConnectionState
	state.HandshakeComplete = c.handshakeComplete
	if c.handshakeComplete {
		state.Version = c.vers
		state.NegotiatedProtocol = c.clientProtocol
		state.DidResume = c.didResume
		state.NegotiatedProtocolIsMutual = !c.clientProtocolFallback
		state.NegotiatedProtocolFromALPN = c.usedALPN
		state.CipherSuite = c.cipherSuite.id
		state.PeerCertificates = c.peerCertificates
		state.VerifiedChains = c.verifiedChains
		state.ServerName = c.serverName
		state.ChannelID = c.channelID
		state.TokenBindingNegotiated = c.tokenBindingNegotiated
		state.TokenBindingParam = c.tokenBindingParam
		state.SRTPProtectionProfile = c.srtpProtectionProfile
		state.TLSUnique = c.firstFinished[:]
		state.SCTList = c.sctList
		state.PeerSignatureAlgorithm = c.peerSignatureAlgorithm
		state.CurveID = c.curveID
		state.QUICTransportParams = c.quicTransportParams
	}

	return state
}

// OCSPResponse returns the stapled OCSP response from the TLS server, if
// any. (Only valid for client connections.)
func (c *Conn) OCSPResponse() []byte {
	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()

	return c.ocspResponse
}

// VerifyHostname checks that the peer certificate chain is valid for
// connecting to host.  If so, it returns nil; if not, it returns an error
// describing the problem.
func (c *Conn) VerifyHostname(host string) error {
	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()
	if !c.isClient {
		return errors.New("tls: VerifyHostname called on TLS server connection")
	}
	if !c.handshakeComplete {
		return errors.New("tls: handshake has not yet been performed")
	}
	return c.peerCertificates[0].VerifyHostname(host)
}

func (c *Conn) exportKeyingMaterialTLS13(length int, secret, label, context []byte) []byte {
	cipherSuite := c.cipherSuite
	if cipherSuite == nil {
		cipherSuite = c.earlyCipherSuite
	}
	hash := cipherSuite.hash()
	exporterKeyingLabel := []byte("exporter")
	contextHash := hash.New()
	contextHash.Write(context)
	exporterContext := hash.New().Sum(nil)
	derivedSecret := hkdfExpandLabel(cipherSuite.hash(), secret, label, exporterContext, hash.Size())
	return hkdfExpandLabel(cipherSuite.hash(), derivedSecret, exporterKeyingLabel, contextHash.Sum(nil), length)
}

// ExportKeyingMaterial exports keying material from the current connection
// state, as per RFC 5705.
func (c *Conn) ExportKeyingMaterial(length int, label, context []byte, useContext bool) ([]byte, error) {
	c.handshakeMutex.Lock()
	defer c.handshakeMutex.Unlock()
	if !c.handshakeComplete {
		return nil, errors.New("tls: handshake has not yet been performed")
	}

	if c.vers >= VersionTLS13 {
		return c.exportKeyingMaterialTLS13(length, c.exporterSecret, label, context), nil
	}

	seedLen := len(c.clientRandom) + len(c.serverRandom)
	if useContext {
		seedLen += 2 + len(context)
	}
	seed := make([]byte, 0, seedLen)
	seed = append(seed, c.clientRandom[:]...)
	seed = append(seed, c.serverRandom[:]...)
	if useContext {
		seed = append(seed, byte(len(context)>>8), byte(len(context)))
		seed = append(seed, context...)
	}
	result := make([]byte, length)
	prfForVersion(c.vers, c.cipherSuite)(result, c.exporterSecret, label, seed)
	return result, nil
}

func (c *Conn) ExportEarlyKeyingMaterial(length int, label, context []byte) ([]byte, error) {
	if c.vers < VersionTLS13 {
		return nil, errors.New("tls: early exporters not defined before TLS 1.3")
	}

	if c.earlyExporterSecret == nil {
		return nil, errors.New("tls: no early exporter secret")
	}

	return c.exportKeyingMaterialTLS13(length, c.earlyExporterSecret, label, context), nil
}

// noRenegotiationInfo returns true if the renegotiation info extension
// should be supported in the current handshake.
func (c *Conn) noRenegotiationInfo() bool {
	if c.config.Bugs.NoRenegotiationInfo {
		return true
	}
	if c.cipherSuite == nil && c.config.Bugs.NoRenegotiationInfoInInitial {
		return true
	}
	if c.cipherSuite != nil && c.config.Bugs.NoRenegotiationInfoAfterInitial {
		return true
	}
	return false
}

func (c *Conn) SendNewSessionTicket(nonce []byte) error {
	if c.isClient || c.vers < VersionTLS13 {
		return errors.New("tls: cannot send post-handshake NewSessionTicket")
	}

	var peerCertificatesRaw [][]byte
	for _, cert := range c.peerCertificates {
		peerCertificatesRaw = append(peerCertificatesRaw, cert.Raw)
	}

	addBuffer := make([]byte, 4)
	_, err := io.ReadFull(c.config.rand(), addBuffer)
	if err != nil {
		c.sendAlert(alertInternalError)
		return errors.New("tls: short read from Rand: " + err.Error())
	}
	ticketAgeAdd := uint32(addBuffer[3])<<24 | uint32(addBuffer[2])<<16 | uint32(addBuffer[1])<<8 | uint32(addBuffer[0])

	// TODO(davidben): Allow configuring these values.
	m := &newSessionTicketMsg{
		vers:                        c.wireVersion,
		isDTLS:                      c.isDTLS,
		ticketLifetime:              uint32(24 * time.Hour / time.Second),
		duplicateEarlyDataExtension: c.config.Bugs.DuplicateTicketEarlyData,
		customExtension:             c.config.Bugs.CustomTicketExtension,
		ticketAgeAdd:                ticketAgeAdd,
		ticketNonce:                 nonce,
		maxEarlyDataSize:            c.config.MaxEarlyDataSize,
	}

	if c.config.Bugs.SendTicketLifetime != 0 {
		m.ticketLifetime = uint32(c.config.Bugs.SendTicketLifetime / time.Second)
	}

	state := sessionState{
		vers:               c.vers,
		cipherSuite:        c.cipherSuite.id,
		masterSecret:       deriveSessionPSK(c.cipherSuite, c.wireVersion, c.resumptionSecret, nonce),
		certificates:       peerCertificatesRaw,
		ticketCreationTime: c.config.time(),
		ticketExpiration:   c.config.time().Add(time.Duration(m.ticketLifetime) * time.Second),
		ticketAgeAdd:       uint32(addBuffer[3])<<24 | uint32(addBuffer[2])<<16 | uint32(addBuffer[1])<<8 | uint32(addBuffer[0]),
		earlyALPN:          []byte(c.clientProtocol),
	}

	if !c.config.Bugs.SendEmptySessionTicket {
		var err error
		m.ticket, err = c.encryptTicket(&state)
		if err != nil {
			return err
		}
	}
	c.out.Lock()
	defer c.out.Unlock()
	_, err = c.writeRecord(recordTypeHandshake, m.marshal())
	return err
}

func (c *Conn) SendKeyUpdate(keyUpdateRequest byte) error {
	c.out.Lock()
	defer c.out.Unlock()
	return c.sendKeyUpdateLocked(keyUpdateRequest)
}

func (c *Conn) sendKeyUpdateLocked(keyUpdateRequest byte) error {
	if c.vers < VersionTLS13 {
		return errors.New("tls: attempted to send KeyUpdate before TLS 1.3")
	}

	m := keyUpdateMsg{
		keyUpdateRequest: keyUpdateRequest,
	}
	if _, err := c.writeRecord(recordTypeHandshake, m.marshal()); err != nil {
		return err
	}
	if err := c.flushHandshake(); err != nil {
		return err
	}
	c.useOutTrafficSecret(c.out.wireVersion, c.cipherSuite, updateTrafficSecret(c.cipherSuite.hash(), c.wireVersion, c.out.trafficSecret))
	return nil
}

func (c *Conn) sendFakeEarlyData(len int) error {
	// Assemble a fake early data record. This does not use writeRecord
	// because the record layer may be using different keys at this point.
	payload := make([]byte, 5+len)
	payload[0] = byte(recordTypeApplicationData)
	payload[1] = 3
	payload[2] = 3
	payload[3] = byte(len >> 8)
	payload[4] = byte(len)
	_, err := c.conn.Write(payload)
	return err
}

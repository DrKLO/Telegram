// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"bytes"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rsa"
	"crypto/subtle"
	"crypto/x509"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"time"
)

type clientHandshakeState struct {
	c             *Conn
	serverHello   *serverHelloMsg
	hello         *clientHelloMsg
	suite         *cipherSuite
	finishedHash  finishedHash
	keyShares     map[CurveID]ecdhCurve
	masterSecret  []byte
	session       *ClientSessionState
	finishedBytes []byte
	peerPublicKey crypto.PublicKey
	skxAlgo       signatureAlgorithm
}

func mapClientHelloVersion(vers uint16, isDTLS bool) uint16 {
	if !isDTLS {
		return vers
	}

	switch vers {
	case VersionTLS12:
		return VersionDTLS12
	case VersionTLS10:
		return VersionDTLS10
	}

	panic("Unknown ClientHello version.")
}

func fixClientHellos(hello *clientHelloMsg, in []byte) ([]byte, error) {
	ret := append([]byte{}, in...)
	newHello := new(clientHelloMsg)
	if !newHello.unmarshal(ret) {
		return nil, errors.New("tls: invalid ClientHello")
	}

	hello.random = newHello.random
	hello.sessionId = newHello.sessionId

	// Replace |ret|'s key shares with those of |hello|. For simplicity, we
	// require their lengths match, which is satisfied by matching the
	// DefaultCurves setting to the selection in the replacement
	// ClientHello.
	bb := newByteBuilder()
	hello.marshalKeyShares(bb)
	keyShares := bb.finish()
	if len(keyShares) != len(newHello.keySharesRaw) {
		return nil, errors.New("tls: ClientHello key share length is inconsistent with DefaultCurves setting")
	}
	// |newHello.keySharesRaw| aliases |ret|.
	copy(newHello.keySharesRaw, keyShares)

	return ret, nil
}

func (c *Conn) clientHandshake() error {
	if c.config == nil {
		c.config = defaultConfig()
	}

	if len(c.config.ServerName) == 0 && !c.config.InsecureSkipVerify {
		return errors.New("tls: either ServerName or InsecureSkipVerify must be specified in the tls.Config")
	}

	c.sendHandshakeSeq = 0
	c.recvHandshakeSeq = 0

	nextProtosLength := 0
	for _, proto := range c.config.NextProtos {
		if l := len(proto); l > 255 {
			return errors.New("tls: invalid NextProtos value")
		} else {
			nextProtosLength += 1 + l
		}
	}
	if nextProtosLength > 0xffff {
		return errors.New("tls: NextProtos values too large")
	}

	minVersion := c.config.minVersion(c.isDTLS)
	maxVersion := c.config.maxVersion(c.isDTLS)
	hello := &clientHelloMsg{
		isDTLS:                  c.isDTLS,
		compressionMethods:      []uint8{compressionNone},
		random:                  make([]byte, 32),
		ocspStapling:            !c.config.Bugs.NoOCSPStapling,
		sctListSupported:        !c.config.Bugs.NoSignedCertificateTimestamps,
		serverName:              c.config.ServerName,
		supportedCurves:         c.config.curvePreferences(),
		supportedPoints:         []uint8{pointFormatUncompressed},
		nextProtoNeg:            len(c.config.NextProtos) > 0,
		secureRenegotiation:     []byte{},
		alpnProtocols:           c.config.NextProtos,
		quicTransportParams:     c.config.QUICTransportParams,
		duplicateExtension:      c.config.Bugs.DuplicateExtension,
		channelIDSupported:      c.config.ChannelID != nil,
		tokenBindingParams:      c.config.TokenBindingParams,
		tokenBindingVersion:     c.config.TokenBindingVersion,
		npnAfterAlpn:            c.config.Bugs.SwapNPNAndALPN,
		extendedMasterSecret:    maxVersion >= VersionTLS10,
		srtpProtectionProfiles:  c.config.SRTPProtectionProfiles,
		srtpMasterKeyIdentifier: c.config.Bugs.SRTPMasterKeyIdentifer,
		customExtension:         c.config.Bugs.CustomExtension,
		pskBinderFirst:          c.config.Bugs.PSKBinderFirst && !c.config.Bugs.OnlyCorruptSecondPSKBinder,
		omitExtensions:          c.config.Bugs.OmitExtensions,
		emptyExtensions:         c.config.Bugs.EmptyExtensions,
		delegatedCredentials:    !c.config.Bugs.DisableDelegatedCredentials,
		pqExperimentSignal:      c.config.PQExperimentSignal,
	}

	if maxVersion >= VersionTLS13 {
		hello.vers = mapClientHelloVersion(VersionTLS12, c.isDTLS)
		if !c.config.Bugs.OmitSupportedVersions {
			hello.supportedVersions = c.config.supportedVersions(c.isDTLS)
		}
		hello.pskKEModes = []byte{pskDHEKEMode}
	} else {
		hello.vers = mapClientHelloVersion(maxVersion, c.isDTLS)
	}

	if c.config.Bugs.SendClientVersion != 0 {
		hello.vers = c.config.Bugs.SendClientVersion
	}

	if len(c.config.Bugs.SendSupportedVersions) > 0 {
		hello.supportedVersions = c.config.Bugs.SendSupportedVersions
	}

	disableEMS := c.config.Bugs.NoExtendedMasterSecret
	if c.cipherSuite != nil {
		disableEMS = c.config.Bugs.NoExtendedMasterSecretOnRenegotiation
	}

	if disableEMS {
		hello.extendedMasterSecret = false
	}

	if c.config.Bugs.NoSupportedCurves {
		hello.supportedCurves = nil
	}

	if len(c.config.Bugs.SendPSKKeyExchangeModes) != 0 {
		hello.pskKEModes = c.config.Bugs.SendPSKKeyExchangeModes
	}

	if c.config.Bugs.SendCompressionMethods != nil {
		hello.compressionMethods = c.config.Bugs.SendCompressionMethods
	}

	if c.config.Bugs.SendSupportedPointFormats != nil {
		hello.supportedPoints = c.config.Bugs.SendSupportedPointFormats
	}

	if len(c.clientVerify) > 0 && !c.config.Bugs.EmptyRenegotiationInfo {
		if c.config.Bugs.BadRenegotiationInfo {
			hello.secureRenegotiation = append(hello.secureRenegotiation, c.clientVerify...)
			hello.secureRenegotiation[0] ^= 0x80
		} else {
			hello.secureRenegotiation = c.clientVerify
		}
	}

	if c.config.Bugs.DuplicateCompressedCertAlgs {
		hello.compressedCertAlgs = []uint16{1, 1}
	} else if len(c.config.CertCompressionAlgs) > 0 {
		hello.compressedCertAlgs = make([]uint16, 0, len(c.config.CertCompressionAlgs))
		for id, _ := range c.config.CertCompressionAlgs {
			hello.compressedCertAlgs = append(hello.compressedCertAlgs, uint16(id))
		}
	}

	if c.noRenegotiationInfo() {
		hello.secureRenegotiation = nil
	}

	var keyShares map[CurveID]ecdhCurve
	if maxVersion >= VersionTLS13 {
		keyShares = make(map[CurveID]ecdhCurve)
		hello.hasKeyShares = true
		hello.trailingKeyShareData = c.config.Bugs.TrailingKeyShareData
		curvesToSend := c.config.defaultCurves()
		for _, curveID := range hello.supportedCurves {
			if !curvesToSend[curveID] {
				continue
			}
			curve, ok := curveForCurveID(curveID, c.config)
			if !ok {
				continue
			}
			publicKey, err := curve.offer(c.config.rand())
			if err != nil {
				return err
			}

			if c.config.Bugs.SendCurve != 0 {
				curveID = c.config.Bugs.SendCurve
			}
			if c.config.Bugs.InvalidECDHPoint {
				publicKey[0] ^= 0xff
			}

			hello.keyShares = append(hello.keyShares, keyShareEntry{
				group:       curveID,
				keyExchange: publicKey,
			})
			keyShares[curveID] = curve

			if c.config.Bugs.DuplicateKeyShares {
				hello.keyShares = append(hello.keyShares, hello.keyShares[len(hello.keyShares)-1])
			}
		}

		if c.config.Bugs.MissingKeyShare {
			hello.hasKeyShares = false
		}
	}

	possibleCipherSuites := c.config.cipherSuites()
	hello.cipherSuites = make([]uint16, 0, len(possibleCipherSuites))

NextCipherSuite:
	for _, suiteId := range possibleCipherSuites {
		for _, suite := range cipherSuites {
			if suite.id != suiteId {
				continue
			}
			// Don't advertise TLS 1.2-only cipher suites unless
			// we're attempting TLS 1.2.
			if maxVersion < VersionTLS12 && suite.flags&suiteTLS12 != 0 {
				continue
			}
			hello.cipherSuites = append(hello.cipherSuites, suiteId)
			continue NextCipherSuite
		}
	}

	if c.config.Bugs.AdvertiseAllConfiguredCiphers {
		hello.cipherSuites = possibleCipherSuites
	}

	if c.config.Bugs.SendRenegotiationSCSV {
		hello.cipherSuites = append(hello.cipherSuites, renegotiationSCSV)
	}

	if c.config.Bugs.SendFallbackSCSV {
		hello.cipherSuites = append(hello.cipherSuites, fallbackSCSV)
	}

	_, err := io.ReadFull(c.config.rand(), hello.random)
	if err != nil {
		c.sendAlert(alertInternalError)
		return errors.New("tls: short read from Rand: " + err.Error())
	}

	if maxVersion >= VersionTLS12 && !c.config.Bugs.NoSignatureAlgorithms {
		hello.signatureAlgorithms = c.config.verifySignatureAlgorithms()
	}

	var session *ClientSessionState
	var cacheKey string
	sessionCache := c.config.ClientSessionCache

	if sessionCache != nil {
		hello.ticketSupported = !c.config.SessionTicketsDisabled

		// Try to resume a previously negotiated TLS session, if
		// available.
		cacheKey = clientSessionCacheKey(c.conn.RemoteAddr(), c.config)
		// TODO(nharper): Support storing more than one session
		// ticket for TLS 1.3.
		candidateSession, ok := sessionCache.Get(cacheKey)
		if ok {
			ticketOk := !c.config.SessionTicketsDisabled || candidateSession.sessionTicket == nil

			// Check that the ciphersuite/version used for the
			// previous session are still valid.
			cipherSuiteOk := false
			if candidateSession.vers <= VersionTLS12 {
				for _, id := range hello.cipherSuites {
					if id == candidateSession.cipherSuite {
						cipherSuiteOk = true
						break
					}
				}
			} else {
				// TLS 1.3 allows the cipher to change on
				// resumption.
				cipherSuiteOk = true
			}

			versOk := candidateSession.vers >= minVersion &&
				candidateSession.vers <= maxVersion
			if ticketOk && versOk && cipherSuiteOk {
				session = candidateSession
			}
		}
	}

	var pskCipherSuite *cipherSuite
	if session != nil && c.config.time().Before(session.ticketExpiration) {
		ticket := session.sessionTicket
		if c.config.Bugs.FilterTicket != nil && len(ticket) > 0 {
			// Copy the ticket so FilterTicket may act in-place.
			ticket = make([]byte, len(session.sessionTicket))
			copy(ticket, session.sessionTicket)

			ticket, err = c.config.Bugs.FilterTicket(ticket)
			if err != nil {
				return err
			}
		}

		if session.vers >= VersionTLS13 || c.config.Bugs.SendBothTickets {
			pskCipherSuite = cipherSuiteFromID(session.cipherSuite)
			if pskCipherSuite == nil {
				return errors.New("tls: client session cache has invalid cipher suite")
			}
			// TODO(nharper): Support sending more
			// than one PSK identity.
			ticketAge := uint32(c.config.time().Sub(session.ticketCreationTime) / time.Millisecond)
			if c.config.Bugs.SendTicketAge != 0 {
				ticketAge = uint32(c.config.Bugs.SendTicketAge / time.Millisecond)
			}
			psk := pskIdentity{
				ticket:              ticket,
				obfuscatedTicketAge: session.ticketAgeAdd + ticketAge,
			}
			hello.pskIdentities = []pskIdentity{psk}

			if c.config.Bugs.ExtraPSKIdentity {
				hello.pskIdentities = append(hello.pskIdentities, psk)
			}
		}

		if session.vers < VersionTLS13 || c.config.Bugs.SendBothTickets {
			if ticket != nil {
				hello.sessionTicket = ticket
				// A random session ID is used to detect when the
				// server accepted the ticket and is resuming a session
				// (see RFC 5077).
				sessionIdLen := 16
				if c.config.Bugs.TicketSessionIDLength != 0 {
					sessionIdLen = c.config.Bugs.TicketSessionIDLength
				}
				if c.config.Bugs.EmptyTicketSessionID {
					sessionIdLen = 0
				}
				hello.sessionId = make([]byte, sessionIdLen)
				if _, err := io.ReadFull(c.config.rand(), hello.sessionId); err != nil {
					c.sendAlert(alertInternalError)
					return errors.New("tls: short read from Rand: " + err.Error())
				}
			} else {
				hello.sessionId = session.sessionId
			}
		}
	}

	// Request compatibility mode from the client by sending a fake session
	// ID. Although BoringSSL always enables compatibility mode, other
	// implementations make it conditional on the ClientHello. We test
	// BoringSSL's expected behavior with SendClientHelloSessionID.
	if len(hello.sessionId) == 0 && maxVersion >= VersionTLS13 {
		hello.sessionId = make([]byte, 32)
		if _, err := io.ReadFull(c.config.rand(), hello.sessionId); err != nil {
			c.sendAlert(alertInternalError)
			return errors.New("tls: short read from Rand: " + err.Error())
		}
	}

	if c.config.Bugs.SendCipherSuites != nil {
		hello.cipherSuites = c.config.Bugs.SendCipherSuites
	}

	var sendEarlyData bool
	if len(hello.pskIdentities) > 0 && c.config.Bugs.SendEarlyData != nil {
		hello.hasEarlyData = true
		sendEarlyData = true
	}
	if c.config.Bugs.SendFakeEarlyDataLength > 0 {
		hello.hasEarlyData = true
	}
	if c.config.Bugs.OmitEarlyDataExtension {
		hello.hasEarlyData = false
	}
	if c.config.Bugs.SendClientHelloSessionID != nil {
		hello.sessionId = c.config.Bugs.SendClientHelloSessionID
	}

	var helloBytes []byte
	if c.config.Bugs.SendV2ClientHello {
		// Test that the peer left-pads random.
		hello.random[0] = 0
		v2Hello := &v2ClientHelloMsg{
			vers:         hello.vers,
			cipherSuites: hello.cipherSuites,
			// No session resumption for V2ClientHello.
			sessionId: nil,
			challenge: hello.random[1:],
		}
		helloBytes = v2Hello.marshal()
		c.writeV2Record(helloBytes)
	} else {
		if len(hello.pskIdentities) > 0 {
			version := session.wireVersion
			// We may have a pre-1.3 session if SendBothTickets is
			// set.
			if session.vers < VersionTLS13 {
				version = VersionTLS13
			}
			generatePSKBinders(version, hello, pskCipherSuite, session.masterSecret, []byte{}, []byte{}, c.config)
		}
		if c.config.Bugs.SendClientHelloWithFixes != nil {
			helloBytes, err = fixClientHellos(hello, c.config.Bugs.SendClientHelloWithFixes)
			if err != nil {
				return err
			}
		} else {
			helloBytes = hello.marshal()
		}

		if c.config.Bugs.PartialClientFinishedWithClientHello {
			// Include one byte of Finished. We can compute it
			// without completing the handshake. This assumes we
			// negotiate TLS 1.3 with no HelloRetryRequest or
			// CertificateRequest.
			toWrite := make([]byte, 0, len(helloBytes)+1)
			toWrite = append(toWrite, helloBytes...)
			toWrite = append(toWrite, typeFinished)
			c.writeRecord(recordTypeHandshake, toWrite)
		} else {
			c.writeRecord(recordTypeHandshake, helloBytes)
		}
	}
	c.flushHandshake()

	if err := c.simulatePacketLoss(nil); err != nil {
		return err
	}
	if c.config.Bugs.SendEarlyAlert {
		c.sendAlert(alertHandshakeFailure)
	}
	if c.config.Bugs.SendFakeEarlyDataLength > 0 {
		c.sendFakeEarlyData(c.config.Bugs.SendFakeEarlyDataLength)
	}

	// Derive early write keys and set Conn state to allow early writes.
	if sendEarlyData {
		finishedHash := newFinishedHash(session.wireVersion, c.isDTLS, pskCipherSuite)
		finishedHash.addEntropy(session.masterSecret)
		finishedHash.Write(helloBytes)

		if !c.config.Bugs.SkipChangeCipherSpec {
			c.wireVersion = session.wireVersion
			c.vers = VersionTLS13
			c.writeRecord(recordTypeChangeCipherSpec, []byte{1})
			c.wireVersion = 0
			c.vers = 0
		}

		earlyTrafficSecret := finishedHash.deriveSecret(earlyTrafficLabel)
		c.earlyExporterSecret = finishedHash.deriveSecret(earlyExporterLabel)

		c.useOutTrafficSecret(session.wireVersion, pskCipherSuite, earlyTrafficSecret)
		for _, earlyData := range c.config.Bugs.SendEarlyData {
			if _, err := c.writeRecord(recordTypeApplicationData, earlyData); err != nil {
				return err
			}
		}
	}

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}

	if c.isDTLS {
		helloVerifyRequest, ok := msg.(*helloVerifyRequestMsg)
		if ok {
			if helloVerifyRequest.vers != VersionDTLS10 {
				// Per RFC 6347, the version field in
				// HelloVerifyRequest SHOULD be always DTLS
				// 1.0. Enforce this for testing purposes.
				return errors.New("dtls: bad HelloVerifyRequest version")
			}

			hello.raw = nil
			hello.cookie = helloVerifyRequest.cookie
			helloBytes = hello.marshal()
			c.writeRecord(recordTypeHandshake, helloBytes)
			c.flushHandshake()

			if err := c.simulatePacketLoss(nil); err != nil {
				return err
			}
			msg, err = c.readHandshake()
			if err != nil {
				return err
			}
		}
	}

	var serverWireVersion uint16
	switch m := msg.(type) {
	case *helloRetryRequestMsg:
		serverWireVersion = m.vers
	case *serverHelloMsg:
		serverWireVersion = m.vers
	default:
		c.sendAlert(alertUnexpectedMessage)
		return fmt.Errorf("tls: received unexpected message of type %T when waiting for HelloRetryRequest or ServerHello", msg)
	}

	serverVersion, ok := c.config.isSupportedVersion(serverWireVersion, c.isDTLS)
	if !ok {
		c.sendAlert(alertProtocolVersion)
		return fmt.Errorf("tls: server selected unsupported protocol version %x", c.vers)
	}
	c.wireVersion = serverWireVersion
	c.vers = serverVersion
	c.haveVers = true

	if c.vers >= VersionTLS13 {
		// The first server message must be followed by a ChangeCipherSpec.
		c.expectTLS13ChangeCipherSpec = true
	}

	helloRetryRequest, haveHelloRetryRequest := msg.(*helloRetryRequestMsg)
	var secondHelloBytes []byte
	if haveHelloRetryRequest {
		if c.config.Bugs.FailIfHelloRetryRequested {
			return errors.New("tls: unexpected HelloRetryRequest")
		}
		// Explicitly read the ChangeCipherSpec now; it should
		// be attached to the first flight, not the second flight.
		if err := c.readTLS13ChangeCipherSpec(); err != nil {
			return err
		}

		c.out.resetCipher()
		if len(helloRetryRequest.cookie) > 0 {
			hello.tls13Cookie = helloRetryRequest.cookie
		}

		if c.config.Bugs.MisinterpretHelloRetryRequestCurve != 0 {
			helloRetryRequest.hasSelectedGroup = true
			helloRetryRequest.selectedGroup = c.config.Bugs.MisinterpretHelloRetryRequestCurve
		}
		if helloRetryRequest.hasSelectedGroup {
			var hrrCurveFound bool
			group := helloRetryRequest.selectedGroup
			for _, curveID := range hello.supportedCurves {
				if group == curveID {
					hrrCurveFound = true
					break
				}
			}
			if !hrrCurveFound || keyShares[group] != nil {
				c.sendAlert(alertHandshakeFailure)
				return errors.New("tls: received invalid HelloRetryRequest")
			}
			curve, ok := curveForCurveID(group, c.config)
			if !ok {
				return errors.New("tls: Unable to get curve requested in HelloRetryRequest")
			}
			publicKey, err := curve.offer(c.config.rand())
			if err != nil {
				return err
			}
			keyShares[group] = curve
			hello.keyShares = []keyShareEntry{{
				group:       group,
				keyExchange: publicKey,
			}}
		}

		if c.config.Bugs.SecondClientHelloMissingKeyShare {
			hello.hasKeyShares = false
		}

		hello.hasEarlyData = c.config.Bugs.SendEarlyDataOnSecondClientHello
		// The first ClientHello may have skipped this due to OnlyCorruptSecondPSKBinder.
		hello.pskBinderFirst = c.config.Bugs.PSKBinderFirst
		if c.config.Bugs.OmitPSKsOnSecondClientHello {
			hello.pskIdentities = nil
			hello.pskBinders = nil
		}
		hello.raw = nil

		if len(hello.pskIdentities) > 0 {
			generatePSKBinders(c.wireVersion, hello, pskCipherSuite, session.masterSecret, helloBytes, helloRetryRequest.marshal(), c.config)
		}
		secondHelloBytes = hello.marshal()

		if c.config.Bugs.InterleaveEarlyData {
			c.sendFakeEarlyData(4)
			c.writeRecord(recordTypeHandshake, secondHelloBytes[:16])
			c.sendFakeEarlyData(4)
			c.writeRecord(recordTypeHandshake, secondHelloBytes[16:])
		} else {
			c.writeRecord(recordTypeHandshake, secondHelloBytes)
		}
		c.flushHandshake()

		if c.config.Bugs.SendEarlyDataOnSecondClientHello {
			c.sendFakeEarlyData(4)
		}

		msg, err = c.readHandshake()
		if err != nil {
			return err
		}
	}

	serverHello, ok := msg.(*serverHelloMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(serverHello, msg)
	}

	if serverWireVersion != serverHello.vers {
		c.sendAlert(alertIllegalParameter)
		return fmt.Errorf("tls: server sent non-matching version %x vs %x", serverWireVersion, serverHello.vers)
	}

	_, supportsTLS13 := c.config.isSupportedVersion(VersionTLS13, false)
	// Check for downgrade signals in the server random, per RFC 8446, section 4.1.3.
	gotDowngrade := serverHello.random[len(serverHello.random)-8:]
	if (supportsTLS13 || c.config.Bugs.CheckTLS13DowngradeRandom) && !c.config.Bugs.IgnoreTLS13DowngradeRandom {
		if c.vers <= VersionTLS12 && c.config.maxVersion(c.isDTLS) >= VersionTLS13 {
			if bytes.Equal(gotDowngrade, downgradeTLS13) {
				c.sendAlert(alertProtocolVersion)
				return errors.New("tls: downgrade from TLS 1.3 detected")
			}
		}
		if c.vers <= VersionTLS11 && c.config.maxVersion(c.isDTLS) >= VersionTLS12 {
			if bytes.Equal(gotDowngrade, downgradeTLS12) {
				c.sendAlert(alertProtocolVersion)
				return errors.New("tls: downgrade from TLS 1.2 detected")
			}
		}
	}

	if bytes.Equal(gotDowngrade, downgradeJDK11) != c.config.Bugs.ExpectJDK11DowngradeRandom {
		c.sendAlert(alertProtocolVersion)
		if c.config.Bugs.ExpectJDK11DowngradeRandom {
			return errors.New("tls: server did not send a JDK 11 downgrade signal")
		}
		return errors.New("tls: server sent an unexpected JDK 11 downgrade signal")
	}

	suite := mutualCipherSuite(hello.cipherSuites, serverHello.cipherSuite)
	if suite == nil {
		c.sendAlert(alertHandshakeFailure)
		return fmt.Errorf("tls: server selected an unsupported cipher suite")
	}

	if haveHelloRetryRequest && helloRetryRequest.hasSelectedGroup && helloRetryRequest.selectedGroup != serverHello.keyShare.group {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("tls: ServerHello parameters did not match HelloRetryRequest")
	}

	if c.config.Bugs.ExpectOmitExtensions && !serverHello.omitExtensions {
		return errors.New("tls: ServerHello did not omit extensions")
	}

	hs := &clientHandshakeState{
		c:            c,
		serverHello:  serverHello,
		hello:        hello,
		suite:        suite,
		finishedHash: newFinishedHash(c.wireVersion, c.isDTLS, suite),
		keyShares:    keyShares,
		session:      session,
	}

	hs.writeHash(helloBytes, hs.c.sendHandshakeSeq-1)
	if haveHelloRetryRequest {
		err = hs.finishedHash.UpdateForHelloRetryRequest()
		if err != nil {
			return err
		}
		hs.writeServerHash(helloRetryRequest.marshal())
		hs.writeClientHash(secondHelloBytes)
	}
	hs.writeServerHash(hs.serverHello.marshal())

	if c.vers >= VersionTLS13 {
		if err := hs.doTLS13Handshake(); err != nil {
			return err
		}
	} else {
		if c.config.Bugs.EarlyChangeCipherSpec > 0 {
			hs.establishKeys()
			c.writeRecord(recordTypeChangeCipherSpec, []byte{1})
		}

		if hs.serverHello.compressionMethod != compressionNone {
			c.sendAlert(alertUnexpectedMessage)
			return errors.New("tls: server selected unsupported compression format")
		}

		err = hs.processServerExtensions(&serverHello.extensions)
		if err != nil {
			return err
		}

		isResume, err := hs.processServerHello()
		if err != nil {
			return err
		}

		if isResume {
			if c.config.Bugs.EarlyChangeCipherSpec == 0 {
				if err := hs.establishKeys(); err != nil {
					return err
				}
			}
			if err := hs.readSessionTicket(); err != nil {
				return err
			}
			if err := hs.readFinished(c.firstFinished[:]); err != nil {
				return err
			}
			if err := hs.sendFinished(nil, isResume); err != nil {
				return err
			}
		} else {
			if err := hs.doFullHandshake(); err != nil {
				return err
			}
			if err := hs.establishKeys(); err != nil {
				return err
			}
			if err := hs.sendFinished(c.firstFinished[:], isResume); err != nil {
				return err
			}
			// Most retransmits are triggered by a timeout, but the final
			// leg of the handshake is retransmited upon re-receiving a
			// Finished.
			if err := c.simulatePacketLoss(func() {
				c.sendHandshakeSeq--
				c.writeRecord(recordTypeHandshake, hs.finishedBytes)
				c.flushHandshake()
			}); err != nil {
				return err
			}
			if err := hs.readSessionTicket(); err != nil {
				return err
			}
			if err := hs.readFinished(nil); err != nil {
				return err
			}
		}

		if sessionCache != nil && hs.session != nil && session != hs.session {
			if c.config.Bugs.RequireSessionTickets && len(hs.session.sessionTicket) == 0 {
				return errors.New("tls: new session used session IDs instead of tickets")
			}
			if c.config.Bugs.RequireSessionIDs && len(hs.session.sessionId) == 0 {
				return errors.New("tls: new session used session tickets instead of IDs")
			}
			sessionCache.Put(cacheKey, hs.session)
		}

		c.didResume = isResume
		c.exporterSecret = hs.masterSecret
	}

	c.handshakeComplete = true
	c.cipherSuite = suite
	copy(c.clientRandom[:], hs.hello.random)
	copy(c.serverRandom[:], hs.serverHello.random)

	return nil
}

func (hs *clientHandshakeState) doTLS13Handshake() error {
	c := hs.c

	if !bytes.Equal(hs.hello.sessionId, hs.serverHello.sessionId) {
		return errors.New("tls: session IDs did not match.")
	}

	// Once the PRF hash is known, TLS 1.3 does not require a handshake
	// buffer.
	hs.finishedHash.discardHandshakeBuffer()

	zeroSecret := hs.finishedHash.zeroSecret()

	// Resolve PSK and compute the early secret.
	//
	// TODO(davidben): This will need to be handled slightly earlier once
	// 0-RTT is implemented.
	if hs.serverHello.hasPSKIdentity {
		// We send at most one PSK identity.
		if hs.session == nil || hs.serverHello.pskIdentity != 0 {
			c.sendAlert(alertUnknownPSKIdentity)
			return errors.New("tls: server sent unknown PSK identity")
		}
		sessionCipher := cipherSuiteFromID(hs.session.cipherSuite)
		if sessionCipher == nil || sessionCipher.hash() != hs.suite.hash() {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server resumed an invalid session for the cipher suite")
		}
		hs.finishedHash.addEntropy(hs.session.masterSecret)
		c.didResume = true
	} else {
		hs.finishedHash.addEntropy(zeroSecret)
	}

	if !hs.serverHello.hasKeyShare {
		c.sendAlert(alertUnsupportedExtension)
		return errors.New("tls: server omitted KeyShare on resumption.")
	}

	// Resolve ECDHE and compute the handshake secret.
	if !c.config.Bugs.MissingKeyShare && !c.config.Bugs.SecondClientHelloMissingKeyShare {
		curve, ok := hs.keyShares[hs.serverHello.keyShare.group]
		if !ok {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server selected an unsupported group")
		}
		c.curveID = hs.serverHello.keyShare.group

		ecdheSecret, err := curve.finish(hs.serverHello.keyShare.keyExchange)
		if err != nil {
			return err
		}
		hs.finishedHash.nextSecret()
		hs.finishedHash.addEntropy(ecdheSecret)
	} else {
		hs.finishedHash.nextSecret()
		hs.finishedHash.addEntropy(zeroSecret)
	}

	// Derive handshake traffic keys and switch read key to handshake
	// traffic key.
	clientHandshakeTrafficSecret := hs.finishedHash.deriveSecret(clientHandshakeTrafficLabel)
	serverHandshakeTrafficSecret := hs.finishedHash.deriveSecret(serverHandshakeTrafficLabel)
	if err := c.useInTrafficSecret(c.wireVersion, hs.suite, serverHandshakeTrafficSecret); err != nil {
		return err
	}

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}

	encryptedExtensions, ok := msg.(*encryptedExtensionsMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(encryptedExtensions, msg)
	}
	hs.writeServerHash(encryptedExtensions.marshal())

	err = hs.processServerExtensions(&encryptedExtensions.extensions)
	if err != nil {
		return err
	}

	var chainToSend *Certificate
	var certReq *certificateRequestMsg
	if c.didResume {
		// Copy over authentication from the session.
		c.peerCertificates = hs.session.serverCertificates
		c.sctList = hs.session.sctList
		c.ocspResponse = hs.session.ocspResponse
	} else {
		msg, err := c.readHandshake()
		if err != nil {
			return err
		}

		var ok bool
		certReq, ok = msg.(*certificateRequestMsg)
		if ok {
			if len(certReq.requestContext) != 0 {
				return errors.New("tls: non-empty certificate request context sent in handshake")
			}

			if c.config.Bugs.ExpectNoCertificateAuthoritiesExtension && certReq.hasCAExtension {
				return errors.New("tls: expected no certificate_authorities extension")
			}

			if err := checkRSAPSSSupport(c.config.Bugs.ExpectRSAPSSSupport, certReq.signatureAlgorithms, certReq.signatureAlgorithmsCert); err != nil {
				return err
			}

			if c.config.Bugs.IgnorePeerSignatureAlgorithmPreferences {
				certReq.signatureAlgorithms = c.config.signSignatureAlgorithms()
			}

			hs.writeServerHash(certReq.marshal())

			chainToSend, err = selectClientCertificate(c, certReq)
			if err != nil {
				return err
			}

			msg, err = c.readHandshake()
			if err != nil {
				return err
			}
		}

		var certMsg *certificateMsg

		if compressedCertMsg, ok := msg.(*compressedCertificateMsg); ok {
			hs.writeServerHash(compressedCertMsg.marshal())

			alg, ok := c.config.CertCompressionAlgs[compressedCertMsg.algID]
			if !ok {
				c.sendAlert(alertBadCertificate)
				return fmt.Errorf("tls: received certificate compressed with unknown algorithm %x", compressedCertMsg.algID)
			}

			decompressed := make([]byte, 4+int(compressedCertMsg.uncompressedLength))
			if !alg.Decompress(decompressed[4:], compressedCertMsg.compressed) {
				c.sendAlert(alertBadCertificate)
				return fmt.Errorf("tls: failed to decompress certificate with algorithm %x", compressedCertMsg.algID)
			}

			certMsg = &certificateMsg{
				hasRequestContext: true,
			}

			if !certMsg.unmarshal(decompressed) {
				c.sendAlert(alertBadCertificate)
				return errors.New("tls: failed to parse decompressed certificate")
			}

			if expected := c.config.Bugs.ExpectedCompressedCert; expected != 0 && expected != compressedCertMsg.algID {
				return fmt.Errorf("tls: expected certificate compressed with algorithm %x, but message used %x", expected, compressedCertMsg.algID)
			}
		} else {
			if certMsg, ok = msg.(*certificateMsg); !ok {
				c.sendAlert(alertUnexpectedMessage)
				return unexpectedMessageError(certMsg, msg)
			}
			hs.writeServerHash(certMsg.marshal())

			if c.config.Bugs.ExpectedCompressedCert != 0 {
				return errors.New("tls: uncompressed certificate received")
			}
		}

		// Check for unsolicited extensions.
		for i, cert := range certMsg.certificates {
			if c.config.Bugs.NoOCSPStapling && cert.ocspResponse != nil {
				c.sendAlert(alertUnsupportedExtension)
				return errors.New("tls: unexpected OCSP response in the server certificate")
			}
			if c.config.Bugs.NoSignedCertificateTimestamps && cert.sctList != nil {
				c.sendAlert(alertUnsupportedExtension)
				return errors.New("tls: unexpected SCT list in the server certificate")
			}
			if i > 0 && c.config.Bugs.ExpectNoExtensionsOnIntermediate && (cert.ocspResponse != nil || cert.sctList != nil) {
				c.sendAlert(alertUnsupportedExtension)
				return errors.New("tls: unexpected extensions in the server certificate")
			}
		}

		if err := hs.verifyCertificates(certMsg); err != nil {
			return err
		}
		c.ocspResponse = certMsg.certificates[0].ocspResponse
		c.sctList = certMsg.certificates[0].sctList

		msg, err = c.readHandshake()
		if err != nil {
			return err
		}
		certVerifyMsg, ok := msg.(*certificateVerifyMsg)
		if !ok {
			c.sendAlert(alertUnexpectedMessage)
			return unexpectedMessageError(certVerifyMsg, msg)
		}

		c.peerSignatureAlgorithm = certVerifyMsg.signatureAlgorithm
		input := hs.finishedHash.certificateVerifyInput(serverCertificateVerifyContextTLS13)
		err = verifyMessage(c.vers, hs.peerPublicKey, c.config, certVerifyMsg.signatureAlgorithm, input, certVerifyMsg.signature)
		if err != nil {
			return err
		}

		hs.writeServerHash(certVerifyMsg.marshal())
	}

	msg, err = c.readHandshake()
	if err != nil {
		return err
	}
	serverFinished, ok := msg.(*finishedMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(serverFinished, msg)
	}

	verify := hs.finishedHash.serverSum(serverHandshakeTrafficSecret)
	if len(verify) != len(serverFinished.verifyData) ||
		subtle.ConstantTimeCompare(verify, serverFinished.verifyData) != 1 {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("tls: server's Finished message was incorrect")
	}

	hs.writeServerHash(serverFinished.marshal())

	// The various secrets do not incorporate the client's final leg, so
	// derive them now before updating the handshake context.
	hs.finishedHash.nextSecret()
	hs.finishedHash.addEntropy(zeroSecret)

	clientTrafficSecret := hs.finishedHash.deriveSecret(clientApplicationTrafficLabel)
	serverTrafficSecret := hs.finishedHash.deriveSecret(serverApplicationTrafficLabel)
	c.exporterSecret = hs.finishedHash.deriveSecret(exporterLabel)

	// Switch to application data keys on read. In particular, any alerts
	// from the client certificate are read over these keys.
	if err := c.useInTrafficSecret(c.wireVersion, hs.suite, serverTrafficSecret); err != nil {
		return err
	}

	// If we're expecting 0.5-RTT messages from the server, read them now.
	var deferredTickets []*newSessionTicketMsg
	if encryptedExtensions.extensions.hasEarlyData {
		// BoringSSL will always send two tickets half-RTT when
		// negotiating 0-RTT.
		for i := 0; i < shimConfig.HalfRTTTickets; i++ {
			msg, err := c.readHandshake()
			if err != nil {
				return fmt.Errorf("tls: error reading half-RTT ticket: %s", err)
			}
			newSessionTicket, ok := msg.(*newSessionTicketMsg)
			if !ok {
				return errors.New("tls: expected half-RTT ticket")
			}
			// Defer processing until the resumption secret is computed.
			deferredTickets = append(deferredTickets, newSessionTicket)
		}
		for _, expectedMsg := range c.config.Bugs.ExpectHalfRTTData {
			if err := c.readRecord(recordTypeApplicationData); err != nil {
				return err
			}
			if !bytes.Equal(c.input.data[c.input.off:], expectedMsg) {
				return errors.New("ExpectHalfRTTData: did not get expected message")
			}
			c.in.freeBlock(c.input)
			c.input = nil
		}
	}

	// Send EndOfEarlyData and then switch write key to handshake
	// traffic key.
	if encryptedExtensions.extensions.hasEarlyData && c.out.cipher != nil && !c.config.Bugs.SkipEndOfEarlyData {
		if c.config.Bugs.SendStrayEarlyHandshake {
			helloRequest := new(helloRequestMsg)
			c.writeRecord(recordTypeHandshake, helloRequest.marshal())
		}
		endOfEarlyData := new(endOfEarlyDataMsg)
		endOfEarlyData.nonEmpty = c.config.Bugs.NonEmptyEndOfEarlyData
		c.writeRecord(recordTypeHandshake, endOfEarlyData.marshal())
		hs.writeClientHash(endOfEarlyData.marshal())
	}

	if !c.config.Bugs.SkipChangeCipherSpec && !hs.hello.hasEarlyData {
		c.writeRecord(recordTypeChangeCipherSpec, []byte{1})
	}

	for i := 0; i < c.config.Bugs.SendExtraChangeCipherSpec; i++ {
		c.writeRecord(recordTypeChangeCipherSpec, []byte{1})
	}

	c.useOutTrafficSecret(c.wireVersion, hs.suite, clientHandshakeTrafficSecret)

	if certReq != nil && !c.config.Bugs.SkipClientCertificate {
		certMsg := &certificateMsg{
			hasRequestContext: true,
			requestContext:    certReq.requestContext,
		}
		if chainToSend != nil {
			for _, certData := range chainToSend.Certificate {
				certMsg.certificates = append(certMsg.certificates, certificateEntry{
					data:           certData,
					extraExtension: c.config.Bugs.SendExtensionOnCertificate,
				})
			}
		}
		hs.writeClientHash(certMsg.marshal())
		c.writeRecord(recordTypeHandshake, certMsg.marshal())

		if chainToSend != nil {
			certVerify := &certificateVerifyMsg{
				hasSignatureAlgorithm: true,
			}

			// Determine the hash to sign.
			privKey := chainToSend.PrivateKey

			var err error
			certVerify.signatureAlgorithm, err = selectSignatureAlgorithm(c.vers, privKey, c.config, certReq.signatureAlgorithms)
			if err != nil {
				c.sendAlert(alertInternalError)
				return err
			}

			input := hs.finishedHash.certificateVerifyInput(clientCertificateVerifyContextTLS13)
			certVerify.signature, err = signMessage(c.vers, privKey, c.config, certVerify.signatureAlgorithm, input)
			if err != nil {
				c.sendAlert(alertInternalError)
				return err
			}
			if c.config.Bugs.SendSignatureAlgorithm != 0 {
				certVerify.signatureAlgorithm = c.config.Bugs.SendSignatureAlgorithm
			}

			if !c.config.Bugs.SkipCertificateVerify {
				hs.writeClientHash(certVerify.marshal())
				c.writeRecord(recordTypeHandshake, certVerify.marshal())
			}
		}
	}

	if encryptedExtensions.extensions.channelIDRequested {
		channelIDHash := crypto.SHA256.New()
		channelIDHash.Write(hs.finishedHash.certificateVerifyInput(channelIDContextTLS13))
		channelIDMsgBytes, err := hs.writeChannelIDMessage(channelIDHash.Sum(nil))
		if err != nil {
			return err
		}
		hs.writeClientHash(channelIDMsgBytes)
		c.writeRecord(recordTypeHandshake, channelIDMsgBytes)
	}

	// Send a client Finished message.
	finished := new(finishedMsg)
	finished.verifyData = hs.finishedHash.clientSum(clientHandshakeTrafficSecret)
	if c.config.Bugs.BadFinished {
		finished.verifyData[0]++
	}
	hs.writeClientHash(finished.marshal())
	if c.config.Bugs.PartialClientFinishedWithClientHello {
		// The first byte has already been sent.
		c.writeRecord(recordTypeHandshake, finished.marshal()[1:])
	} else if c.config.Bugs.InterleaveEarlyData {
		finishedBytes := finished.marshal()
		c.sendFakeEarlyData(4)
		c.writeRecord(recordTypeHandshake, finishedBytes[:1])
		c.sendFakeEarlyData(4)
		c.writeRecord(recordTypeHandshake, finishedBytes[1:])
	} else {
		c.writeRecord(recordTypeHandshake, finished.marshal())
	}
	if c.config.Bugs.SendExtraFinished {
		c.writeRecord(recordTypeHandshake, finished.marshal())
	}
	c.flushHandshake()

	// Switch to application data keys.
	c.useOutTrafficSecret(c.wireVersion, hs.suite, clientTrafficSecret)
	c.resumptionSecret = hs.finishedHash.deriveSecret(resumptionLabel)
	for _, ticket := range deferredTickets {
		if err := c.processTLS13NewSessionTicket(ticket, hs.suite); err != nil {
			return err
		}
	}

	return nil
}

func (hs *clientHandshakeState) doFullHandshake() error {
	c := hs.c

	var leaf *x509.Certificate
	if hs.suite.flags&suitePSK == 0 {
		msg, err := c.readHandshake()
		if err != nil {
			return err
		}

		certMsg, ok := msg.(*certificateMsg)
		if !ok {
			c.sendAlert(alertUnexpectedMessage)
			return unexpectedMessageError(certMsg, msg)
		}
		hs.writeServerHash(certMsg.marshal())

		if err := hs.verifyCertificates(certMsg); err != nil {
			return err
		}
		leaf = c.peerCertificates[0]
	}

	if hs.serverHello.extensions.ocspStapling {
		msg, err := c.readHandshake()
		if err != nil {
			return err
		}
		cs, ok := msg.(*certificateStatusMsg)
		if !ok {
			c.sendAlert(alertUnexpectedMessage)
			return unexpectedMessageError(cs, msg)
		}
		hs.writeServerHash(cs.marshal())

		if cs.statusType == statusTypeOCSP {
			c.ocspResponse = cs.response
		}
	}

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}

	keyAgreement := hs.suite.ka(c.vers)

	skx, ok := msg.(*serverKeyExchangeMsg)
	if ok {
		hs.writeServerHash(skx.marshal())
		err = keyAgreement.processServerKeyExchange(c.config, hs.hello, hs.serverHello, hs.peerPublicKey, skx)
		if err != nil {
			c.sendAlert(alertUnexpectedMessage)
			return err
		}
		if ecdhe, ok := keyAgreement.(*ecdheKeyAgreement); ok {
			c.curveID = ecdhe.curveID
		}

		c.peerSignatureAlgorithm = keyAgreement.peerSignatureAlgorithm()

		msg, err = c.readHandshake()
		if err != nil {
			return err
		}
	}

	var chainToSend *Certificate
	var certRequested bool
	certReq, ok := msg.(*certificateRequestMsg)
	if ok {
		certRequested = true
		if err := checkRSAPSSSupport(c.config.Bugs.ExpectRSAPSSSupport, certReq.signatureAlgorithms, certReq.signatureAlgorithmsCert); err != nil {
			return err
		}
		if c.config.Bugs.IgnorePeerSignatureAlgorithmPreferences {
			certReq.signatureAlgorithms = c.config.signSignatureAlgorithms()
		}

		hs.writeServerHash(certReq.marshal())

		chainToSend, err = selectClientCertificate(c, certReq)
		if err != nil {
			return err
		}

		msg, err = c.readHandshake()
		if err != nil {
			return err
		}
	}

	shd, ok := msg.(*serverHelloDoneMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(shd, msg)
	}
	hs.writeServerHash(shd.marshal())

	// If the server requested a certificate then we have to send a
	// Certificate message in TLS, even if it's empty because we don't have
	// a certificate to send. In SSL 3.0, skip the message and send a
	// no_certificate warning alert.
	if certRequested {
		if c.vers == VersionSSL30 && chainToSend == nil {
			c.sendAlert(alertNoCertificate)
		} else if !c.config.Bugs.SkipClientCertificate {
			certMsg := new(certificateMsg)
			if chainToSend != nil {
				for _, certData := range chainToSend.Certificate {
					certMsg.certificates = append(certMsg.certificates, certificateEntry{
						data: certData,
					})
				}
			}
			hs.writeClientHash(certMsg.marshal())
			c.writeRecord(recordTypeHandshake, certMsg.marshal())
		}
	}

	preMasterSecret, ckx, err := keyAgreement.generateClientKeyExchange(c.config, hs.hello, leaf)
	if err != nil {
		c.sendAlert(alertInternalError)
		return err
	}
	if ckx != nil {
		if c.config.Bugs.EarlyChangeCipherSpec < 2 {
			hs.writeClientHash(ckx.marshal())
		}
		c.writeRecord(recordTypeHandshake, ckx.marshal())
	}

	if hs.serverHello.extensions.extendedMasterSecret && c.vers >= VersionTLS10 {
		hs.masterSecret = extendedMasterFromPreMasterSecret(c.vers, hs.suite, preMasterSecret, hs.finishedHash)
		c.extendedMasterSecret = true
	} else {
		if c.config.Bugs.RequireExtendedMasterSecret {
			return errors.New("tls: extended master secret required but not supported by peer")
		}
		hs.masterSecret = masterFromPreMasterSecret(c.vers, hs.suite, preMasterSecret, hs.hello.random, hs.serverHello.random)
	}

	if chainToSend != nil {
		certVerify := &certificateVerifyMsg{
			hasSignatureAlgorithm: c.vers >= VersionTLS12,
		}

		// Determine the hash to sign.
		privKey := c.config.Certificates[0].PrivateKey

		if certVerify.hasSignatureAlgorithm {
			certVerify.signatureAlgorithm, err = selectSignatureAlgorithm(c.vers, privKey, c.config, certReq.signatureAlgorithms)
			if err != nil {
				c.sendAlert(alertInternalError)
				return err
			}
		}

		if c.vers > VersionSSL30 {
			certVerify.signature, err = signMessage(c.vers, privKey, c.config, certVerify.signatureAlgorithm, hs.finishedHash.buffer)
			if err == nil && c.config.Bugs.SendSignatureAlgorithm != 0 {
				certVerify.signatureAlgorithm = c.config.Bugs.SendSignatureAlgorithm
			}
		} else {
			// SSL 3.0's client certificate construction is
			// incompatible with signatureAlgorithm.
			rsaKey, ok := privKey.(*rsa.PrivateKey)
			if !ok {
				err = errors.New("unsupported signature type for client certificate")
			} else {
				digest := hs.finishedHash.hashForClientCertificateSSL3(hs.masterSecret)
				if c.config.Bugs.InvalidSignature {
					digest[0] ^= 0x80
				}
				certVerify.signature, err = rsa.SignPKCS1v15(c.config.rand(), rsaKey, crypto.MD5SHA1, digest)
			}
		}
		if err != nil {
			c.sendAlert(alertInternalError)
			return errors.New("tls: failed to sign handshake with client certificate: " + err.Error())
		}

		if !c.config.Bugs.SkipCertificateVerify {
			hs.writeClientHash(certVerify.marshal())
			c.writeRecord(recordTypeHandshake, certVerify.marshal())
		}
	}
	// flushHandshake will be called in sendFinished.

	hs.finishedHash.discardHandshakeBuffer()

	return nil
}

// delegatedCredentialSignedMessage returns the bytes that are signed in order
// to authenticate a delegated credential.
func delegatedCredentialSignedMessage(credBytes []byte, algorithm signatureAlgorithm, leafDER []byte) []byte {
	// https://tools.ietf.org/html/draft-ietf-tls-subcerts-03#section-3
	ret := make([]byte, 64, 128)
	for i := range ret {
		ret[i] = 0x20
	}

	ret = append(ret, []byte("TLS, server delegated credentials\x00")...)
	ret = append(ret, leafDER...)
	ret = append(ret, byte(algorithm>>8), byte(algorithm))
	ret = append(ret, credBytes...)

	return ret
}

func (hs *clientHandshakeState) verifyCertificates(certMsg *certificateMsg) error {
	c := hs.c

	if len(certMsg.certificates) == 0 {
		c.sendAlert(alertIllegalParameter)
		return errors.New("tls: no certificates sent")
	}

	var dc *delegatedCredential
	certs := make([]*x509.Certificate, len(certMsg.certificates))
	for i, certEntry := range certMsg.certificates {
		cert, err := x509.ParseCertificate(certEntry.data)
		if err != nil {
			c.sendAlert(alertBadCertificate)
			return errors.New("tls: failed to parse certificate from server: " + err.Error())
		}
		certs[i] = cert

		if certEntry.delegatedCredential != nil {
			if c.config.Bugs.FailIfDelegatedCredentials {
				c.sendAlert(alertIllegalParameter)
				return errors.New("tls: unexpected delegated credential")
			}
			if i != 0 {
				c.sendAlert(alertIllegalParameter)
				return errors.New("tls: non-leaf certificate has a delegated credential")
			}
			if c.config.Bugs.DisableDelegatedCredentials {
				c.sendAlert(alertIllegalParameter)
				return errors.New("tls: server sent delegated credential without it being requested")
			}
			dc = certEntry.delegatedCredential
		}
	}

	if !c.config.InsecureSkipVerify {
		opts := x509.VerifyOptions{
			Roots:         c.config.RootCAs,
			CurrentTime:   c.config.time(),
			DNSName:       c.config.ServerName,
			Intermediates: x509.NewCertPool(),
		}

		for i, cert := range certs {
			if i == 0 {
				continue
			}
			opts.Intermediates.AddCert(cert)
		}
		var err error
		c.verifiedChains, err = certs[0].Verify(opts)
		if err != nil {
			c.sendAlert(alertBadCertificate)
			return err
		}
	}

	leafPublicKey := certs[0].PublicKey
	switch leafPublicKey.(type) {
	case *rsa.PublicKey, *ecdsa.PublicKey, ed25519.PublicKey:
		break
	default:
		c.sendAlert(alertUnsupportedCertificate)
		return fmt.Errorf("tls: server's certificate contains an unsupported type of public key: %T", leafPublicKey)
	}

	c.peerCertificates = certs

	if dc != nil {
		// Note that this doesn't check a) the delegated credential temporal
		// validity nor b) that the certificate has the special OID asserted.
		hs.skxAlgo = dc.expectedCertVerifyAlgo

		var err error
		if hs.peerPublicKey, err = x509.ParsePKIXPublicKey(dc.pkixPublicKey); err != nil {
			c.sendAlert(alertBadCertificate)
			return errors.New("tls: failed to parse public key from delegated credential: " + err.Error())
		}

		verifier, err := getSigner(c.vers, hs.peerPublicKey, c.config, dc.algorithm, true)
		if err != nil {
			c.sendAlert(alertBadCertificate)
			return errors.New("tls: failed to get verifier for delegated credential: " + err.Error())
		}

		if err := verifier.verifyMessage(leafPublicKey, delegatedCredentialSignedMessage(dc.signedBytes, dc.algorithm, certs[0].Raw), dc.signature); err != nil {
			c.sendAlert(alertBadCertificate)
			return errors.New("tls: failed to verify delegated credential: " + err.Error())
		}
	} else if c.config.Bugs.ExpectDelegatedCredentials {
		c.sendAlert(alertInternalError)
		return errors.New("tls: delegated credentials missing")
	} else {
		hs.peerPublicKey = leafPublicKey
	}

	return nil
}

func (hs *clientHandshakeState) establishKeys() error {
	c := hs.c

	clientMAC, serverMAC, clientKey, serverKey, clientIV, serverIV :=
		keysFromMasterSecret(c.vers, hs.suite, hs.masterSecret, hs.hello.random, hs.serverHello.random, hs.suite.macLen, hs.suite.keyLen, hs.suite.ivLen(c.vers))
	var clientCipher, serverCipher interface{}
	var clientHash, serverHash macFunction
	if hs.suite.cipher != nil {
		clientCipher = hs.suite.cipher(clientKey, clientIV, false /* not for reading */)
		clientHash = hs.suite.mac(c.vers, clientMAC)
		serverCipher = hs.suite.cipher(serverKey, serverIV, true /* for reading */)
		serverHash = hs.suite.mac(c.vers, serverMAC)
	} else {
		clientCipher = hs.suite.aead(c.vers, clientKey, clientIV)
		serverCipher = hs.suite.aead(c.vers, serverKey, serverIV)
	}

	c.in.prepareCipherSpec(c.wireVersion, serverCipher, serverHash)
	c.out.prepareCipherSpec(c.wireVersion, clientCipher, clientHash)
	return nil
}

func (hs *clientHandshakeState) processServerExtensions(serverExtensions *serverExtensions) error {
	c := hs.c

	if c.vers < VersionTLS13 {
		if c.config.Bugs.RequireRenegotiationInfo && serverExtensions.secureRenegotiation == nil {
			return errors.New("tls: renegotiation extension missing")
		}

		if len(c.clientVerify) > 0 && !c.noRenegotiationInfo() {
			var expectedRenegInfo []byte
			expectedRenegInfo = append(expectedRenegInfo, c.clientVerify...)
			expectedRenegInfo = append(expectedRenegInfo, c.serverVerify...)
			if !bytes.Equal(serverExtensions.secureRenegotiation, expectedRenegInfo) {
				c.sendAlert(alertHandshakeFailure)
				return fmt.Errorf("tls: renegotiation mismatch")
			}
		}
	} else if serverExtensions.secureRenegotiation != nil {
		return errors.New("tls: renegotiation info sent in TLS 1.3")
	}

	if expected := c.config.Bugs.ExpectedCustomExtension; expected != nil {
		if serverExtensions.customExtension != *expected {
			return fmt.Errorf("tls: bad custom extension contents %q", serverExtensions.customExtension)
		}
	}

	clientDidNPN := hs.hello.nextProtoNeg
	clientDidALPN := len(hs.hello.alpnProtocols) > 0
	serverHasNPN := serverExtensions.nextProtoNeg
	serverHasALPN := len(serverExtensions.alpnProtocol) > 0

	if !clientDidNPN && serverHasNPN {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("server advertised unrequested NPN extension")
	}

	if !clientDidALPN && serverHasALPN {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("server advertised unrequested ALPN extension")
	}

	if serverHasNPN && serverHasALPN {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("server advertised both NPN and ALPN extensions")
	}

	if serverHasALPN {
		c.clientProtocol = serverExtensions.alpnProtocol
		c.clientProtocolFallback = false
		c.usedALPN = true
	}

	if serverHasNPN && c.vers >= VersionTLS13 {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("server advertised NPN over TLS 1.3")
	}

	if !hs.hello.channelIDSupported && serverExtensions.channelIDRequested {
		c.sendAlert(alertHandshakeFailure)
		return errors.New("server advertised unrequested Channel ID extension")
	}

	if len(serverExtensions.tokenBindingParams) == 1 {
		found := false
		for _, p := range c.config.TokenBindingParams {
			if p == serverExtensions.tokenBindingParams[0] {
				c.tokenBindingParam = p
				found = true
				break
			}
		}
		if !found {
			return errors.New("tls: server advertised unsupported Token Binding key param")
		}
		if serverExtensions.tokenBindingVersion > c.config.TokenBindingVersion {
			return errors.New("tls: server's Token Binding version is too new")
		}
		if c.vers < VersionTLS13 {
			if !serverExtensions.extendedMasterSecret || serverExtensions.secureRenegotiation == nil {
				return errors.New("server sent Token Binding without EMS or RI")
			}
		}
		c.tokenBindingNegotiated = true
	}

	if serverExtensions.extendedMasterSecret && c.vers >= VersionTLS13 {
		return errors.New("tls: server advertised extended master secret over TLS 1.3")
	}

	if serverExtensions.ticketSupported && c.vers >= VersionTLS13 {
		return errors.New("tls: server advertised ticket extension over TLS 1.3")
	}

	if serverExtensions.ocspStapling && c.vers >= VersionTLS13 {
		return errors.New("tls: server advertised OCSP in ServerHello over TLS 1.3")
	}

	if serverExtensions.ocspStapling && c.config.Bugs.NoOCSPStapling {
		return errors.New("tls: server advertised unrequested OCSP extension")
	}

	if len(serverExtensions.sctList) > 0 && c.vers >= VersionTLS13 {
		return errors.New("tls: server advertised SCTs in ServerHello over TLS 1.3")
	}

	if len(serverExtensions.sctList) > 0 && c.config.Bugs.NoSignedCertificateTimestamps {
		return errors.New("tls: server advertised unrequested SCTs")
	}

	if serverExtensions.srtpProtectionProfile != 0 {
		if serverExtensions.srtpMasterKeyIdentifier != "" {
			return errors.New("tls: server selected SRTP MKI value")
		}

		found := false
		for _, p := range c.config.SRTPProtectionProfiles {
			if p == serverExtensions.srtpProtectionProfile {
				found = true
				break
			}
		}
		if !found {
			return errors.New("tls: server advertised unsupported SRTP profile")
		}

		c.srtpProtectionProfile = serverExtensions.srtpProtectionProfile
	}

	if c.vers >= VersionTLS13 && c.didResume {
		if c.config.Bugs.ExpectEarlyDataAccepted && !serverExtensions.hasEarlyData {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server did not accept early data when expected")
		}

		if !c.config.Bugs.ExpectEarlyDataAccepted && serverExtensions.hasEarlyData {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server accepted early data when not expected")
		}
	}

	if len(serverExtensions.quicTransportParams) > 0 {
		if c.vers < VersionTLS13 {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server sent QUIC transport params for TLS version less than 1.3")
		}
		c.quicTransportParams = serverExtensions.quicTransportParams
	}

	if c.config.Bugs.ExpectPQExperimentSignal != serverExtensions.pqExperimentSignal {
		return fmt.Errorf("tls: PQ experiment signal presence (%t) was not what was expected", serverExtensions.pqExperimentSignal)
	}

	return nil
}

func (hs *clientHandshakeState) serverResumedSession() bool {
	// If the server responded with the same sessionId then it means the
	// sessionTicket is being used to resume a TLS session.
	//
	// Note that, if hs.hello.sessionId is a non-nil empty array, this will
	// accept an empty session ID from the server as resumption. See
	// EmptyTicketSessionID.
	return hs.session != nil && hs.hello.sessionId != nil &&
		bytes.Equal(hs.serverHello.sessionId, hs.hello.sessionId)
}

func (hs *clientHandshakeState) processServerHello() (bool, error) {
	c := hs.c

	if hs.serverResumedSession() {
		// For test purposes, assert that the server never accepts the
		// resumption offer on renegotiation.
		if c.cipherSuite != nil && c.config.Bugs.FailIfResumeOnRenego {
			return false, errors.New("tls: server resumed session on renegotiation")
		}

		if hs.serverHello.extensions.sctList != nil {
			return false, errors.New("tls: server sent SCT extension on session resumption")
		}

		if hs.serverHello.extensions.ocspStapling {
			return false, errors.New("tls: server sent OCSP extension on session resumption")
		}

		// Restore masterSecret and peerCerts from previous state
		hs.masterSecret = hs.session.masterSecret
		c.peerCertificates = hs.session.serverCertificates
		c.extendedMasterSecret = hs.session.extendedMasterSecret
		c.sctList = hs.session.sctList
		c.ocspResponse = hs.session.ocspResponse
		hs.finishedHash.discardHandshakeBuffer()
		return true, nil
	}

	if hs.serverHello.extensions.sctList != nil {
		c.sctList = hs.serverHello.extensions.sctList
	}

	return false, nil
}

func (hs *clientHandshakeState) readFinished(out []byte) error {
	c := hs.c

	c.readRecord(recordTypeChangeCipherSpec)
	if err := c.in.error(); err != nil {
		return err
	}

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}
	serverFinished, ok := msg.(*finishedMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(serverFinished, msg)
	}

	if c.config.Bugs.EarlyChangeCipherSpec == 0 {
		verify := hs.finishedHash.serverSum(hs.masterSecret)
		if len(verify) != len(serverFinished.verifyData) ||
			subtle.ConstantTimeCompare(verify, serverFinished.verifyData) != 1 {
			c.sendAlert(alertHandshakeFailure)
			return errors.New("tls: server's Finished message was incorrect")
		}
	}
	c.serverVerify = append(c.serverVerify[:0], serverFinished.verifyData...)
	copy(out, serverFinished.verifyData)
	hs.writeServerHash(serverFinished.marshal())
	return nil
}

func (hs *clientHandshakeState) readSessionTicket() error {
	c := hs.c

	// Create a session with no server identifier. Either a
	// session ID or session ticket will be attached.
	session := &ClientSessionState{
		vers:               c.vers,
		wireVersion:        c.wireVersion,
		cipherSuite:        hs.suite.id,
		masterSecret:       hs.masterSecret,
		handshakeHash:      hs.finishedHash.Sum(),
		serverCertificates: c.peerCertificates,
		sctList:            c.sctList,
		ocspResponse:       c.ocspResponse,
		ticketExpiration:   c.config.time().Add(time.Duration(7 * 24 * time.Hour)),
	}

	if !hs.serverHello.extensions.ticketSupported {
		if c.config.Bugs.ExpectNewTicket {
			return errors.New("tls: expected new ticket")
		}
		if hs.session == nil && len(hs.serverHello.sessionId) > 0 {
			session.sessionId = hs.serverHello.sessionId
			hs.session = session
		}
		return nil
	}

	if c.vers == VersionSSL30 {
		return errors.New("tls: negotiated session tickets in SSL 3.0")
	}
	if c.config.Bugs.ExpectNoNewSessionTicket {
		return errors.New("tls: received unexpected NewSessionTicket")
	}

	msg, err := c.readHandshake()
	if err != nil {
		return err
	}
	sessionTicketMsg, ok := msg.(*newSessionTicketMsg)
	if !ok {
		c.sendAlert(alertUnexpectedMessage)
		return unexpectedMessageError(sessionTicketMsg, msg)
	}

	session.sessionTicket = sessionTicketMsg.ticket
	hs.session = session

	hs.writeServerHash(sessionTicketMsg.marshal())

	return nil
}

func (hs *clientHandshakeState) sendFinished(out []byte, isResume bool) error {
	c := hs.c

	var postCCSMsgs [][]byte
	seqno := hs.c.sendHandshakeSeq
	if hs.serverHello.extensions.nextProtoNeg {
		nextProto := new(nextProtoMsg)
		proto, fallback := mutualProtocol(c.config.NextProtos, hs.serverHello.extensions.nextProtos)
		nextProto.proto = proto
		c.clientProtocol = proto
		c.clientProtocolFallback = fallback

		nextProtoBytes := nextProto.marshal()
		hs.writeHash(nextProtoBytes, seqno)
		seqno++
		postCCSMsgs = append(postCCSMsgs, nextProtoBytes)
	}

	if hs.serverHello.extensions.channelIDRequested {
		var resumeHash []byte
		if isResume {
			resumeHash = hs.session.handshakeHash
		}
		channelIDMsgBytes, err := hs.writeChannelIDMessage(hs.finishedHash.hashForChannelID(resumeHash))
		if err != nil {
			return err
		}
		hs.writeHash(channelIDMsgBytes, seqno)
		seqno++
		postCCSMsgs = append(postCCSMsgs, channelIDMsgBytes)
	}

	finished := new(finishedMsg)
	if c.config.Bugs.EarlyChangeCipherSpec == 2 {
		finished.verifyData = hs.finishedHash.clientSum(nil)
	} else {
		finished.verifyData = hs.finishedHash.clientSum(hs.masterSecret)
	}
	copy(out, finished.verifyData)
	if c.config.Bugs.BadFinished {
		finished.verifyData[0]++
	}
	c.clientVerify = append(c.clientVerify[:0], finished.verifyData...)
	hs.finishedBytes = finished.marshal()
	hs.writeHash(hs.finishedBytes, seqno)
	postCCSMsgs = append(postCCSMsgs, hs.finishedBytes)

	if c.config.Bugs.FragmentAcrossChangeCipherSpec {
		c.writeRecord(recordTypeHandshake, postCCSMsgs[0][:5])
		postCCSMsgs[0] = postCCSMsgs[0][5:]
	} else if c.config.Bugs.SendUnencryptedFinished {
		c.writeRecord(recordTypeHandshake, postCCSMsgs[0])
		postCCSMsgs = postCCSMsgs[1:]
	}

	if !c.config.Bugs.SkipChangeCipherSpec &&
		c.config.Bugs.EarlyChangeCipherSpec == 0 {
		ccs := []byte{1}
		if c.config.Bugs.BadChangeCipherSpec != nil {
			ccs = c.config.Bugs.BadChangeCipherSpec
		}
		c.writeRecord(recordTypeChangeCipherSpec, ccs)
	}

	if c.config.Bugs.AppDataAfterChangeCipherSpec != nil {
		c.writeRecord(recordTypeApplicationData, c.config.Bugs.AppDataAfterChangeCipherSpec)
	}
	if c.config.Bugs.AlertAfterChangeCipherSpec != 0 {
		c.sendAlert(c.config.Bugs.AlertAfterChangeCipherSpec)
		return errors.New("tls: simulating post-CCS alert")
	}

	if !c.config.Bugs.SkipFinished {
		for _, msg := range postCCSMsgs {
			c.writeRecord(recordTypeHandshake, msg)
		}

		if c.config.Bugs.SendExtraFinished {
			c.writeRecord(recordTypeHandshake, finished.marshal())
		}
	}

	if !isResume || !c.config.Bugs.PackAppDataWithHandshake {
		c.flushHandshake()
	}
	return nil
}

func (hs *clientHandshakeState) writeChannelIDMessage(channelIDHash []byte) ([]byte, error) {
	c := hs.c
	channelIDMsg := new(channelIDMsg)
	if c.config.ChannelID.Curve != elliptic.P256() {
		return nil, fmt.Errorf("tls: Channel ID is not on P-256.")
	}
	r, s, err := ecdsa.Sign(c.config.rand(), c.config.ChannelID, channelIDHash)
	if err != nil {
		return nil, err
	}
	channelID := make([]byte, 128)
	writeIntPadded(channelID[0:32], c.config.ChannelID.X)
	writeIntPadded(channelID[32:64], c.config.ChannelID.Y)
	writeIntPadded(channelID[64:96], r)
	writeIntPadded(channelID[96:128], s)
	if c.config.Bugs.InvalidChannelIDSignature {
		channelID[64] ^= 1
	}
	channelIDMsg.channelID = channelID

	c.channelID = &c.config.ChannelID.PublicKey

	return channelIDMsg.marshal(), nil
}

func (hs *clientHandshakeState) writeClientHash(msg []byte) {
	// writeClientHash is called before writeRecord.
	hs.writeHash(msg, hs.c.sendHandshakeSeq)
}

func (hs *clientHandshakeState) writeServerHash(msg []byte) {
	// writeServerHash is called after readHandshake.
	hs.writeHash(msg, hs.c.recvHandshakeSeq-1)
}

func (hs *clientHandshakeState) writeHash(msg []byte, seqno uint16) {
	if hs.c.isDTLS {
		// This is somewhat hacky. DTLS hashes a slightly different format.
		// First, the TLS header.
		hs.finishedHash.Write(msg[:4])
		// Then the sequence number and reassembled fragment offset (always 0).
		hs.finishedHash.Write([]byte{byte(seqno >> 8), byte(seqno), 0, 0, 0})
		// Then the reassembled fragment (always equal to the message length).
		hs.finishedHash.Write(msg[1:4])
		// And then the message body.
		hs.finishedHash.Write(msg[4:])
	} else {
		hs.finishedHash.Write(msg)
	}
}

// selectClientCertificate selects a certificate for use with the given
// certificate, or none if none match. It may return a particular certificate or
// nil on success, or an error on internal error.
func selectClientCertificate(c *Conn, certReq *certificateRequestMsg) (*Certificate, error) {
	if len(c.config.Certificates) == 0 {
		return nil, nil
	}

	// The test is assumed to have configured the certificate it meant to
	// send.
	if len(c.config.Certificates) > 1 {
		return nil, errors.New("tls: multiple certificates configured")
	}

	return &c.config.Certificates[0], nil
}

// clientSessionCacheKey returns a key used to cache sessionTickets that could
// be used to resume previously negotiated TLS sessions with a server.
func clientSessionCacheKey(serverAddr net.Addr, config *Config) string {
	if len(config.ServerName) > 0 {
		return config.ServerName
	}
	return serverAddr.String()
}

// mutualProtocol finds the mutual Next Protocol Negotiation or ALPN protocol
// given list of possible protocols and a list of the preference order. The
// first list must not be empty. It returns the resulting protocol and flag
// indicating if the fallback case was reached.
func mutualProtocol(protos, preferenceProtos []string) (string, bool) {
	for _, s := range preferenceProtos {
		for _, c := range protos {
			if s == c {
				return s, false
			}
		}
	}

	return protos[0], true
}

// writeIntPadded writes x into b, padded up with leading zeros as
// needed.
func writeIntPadded(b []byte, x *big.Int) {
	for i := range b {
		b[i] = 0
	}
	xb := x.Bytes()
	copy(b[len(b)-len(xb):], xb)
}

func generatePSKBinders(version uint16, hello *clientHelloMsg, pskCipherSuite *cipherSuite, psk, firstClientHello, helloRetryRequest []byte, config *Config) {
	maybeCorruptBinder := !config.Bugs.OnlyCorruptSecondPSKBinder || len(firstClientHello) > 0
	binderLen := pskCipherSuite.hash().Size()
	numBinders := 1
	if maybeCorruptBinder {
		if config.Bugs.SendNoPSKBinder {
			// The binders may have been set from the previous
			// ClientHello.
			hello.pskBinders = nil
			return
		}

		if config.Bugs.SendShortPSKBinder {
			binderLen--
		}

		if config.Bugs.SendExtraPSKBinder {
			numBinders++
		}
	}

	// Fill hello.pskBinders with appropriate length arrays of zeros so the
	// length prefixes are correct when computing the binder over the truncated
	// ClientHello message.
	hello.pskBinders = make([][]byte, numBinders)
	for i := range hello.pskBinders {
		hello.pskBinders[i] = make([]byte, binderLen)
	}

	helloBytes := hello.marshal()
	binderSize := len(hello.pskBinders)*(binderLen+1) + 2
	truncatedHello := helloBytes[:len(helloBytes)-binderSize]
	binder := computePSKBinder(psk, version, resumptionPSKBinderLabel, pskCipherSuite, firstClientHello, helloRetryRequest, truncatedHello)
	if maybeCorruptBinder {
		if config.Bugs.SendShortPSKBinder {
			binder = binder[:binderLen]
		}
		if config.Bugs.SendInvalidPSKBinder {
			binder[0] ^= 1
		}
	}

	for i := range hello.pskBinders {
		hello.pskBinders[i] = binder
	}

	hello.raw = nil
}

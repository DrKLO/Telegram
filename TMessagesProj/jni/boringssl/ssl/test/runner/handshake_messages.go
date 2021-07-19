// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"bytes"
	"encoding/binary"
	"fmt"
)

func writeLen(buf []byte, v, size int) {
	for i := 0; i < size; i++ {
		buf[size-i-1] = byte(v)
		v >>= 8
	}
	if v != 0 {
		panic("length is too long")
	}
}

type byteBuilder struct {
	buf       *[]byte
	start     int
	prefixLen int
	child     *byteBuilder
}

func newByteBuilder() *byteBuilder {
	buf := make([]byte, 0, 32)
	return &byteBuilder{buf: &buf}
}

func (bb *byteBuilder) len() int {
	return len(*bb.buf) - bb.start - bb.prefixLen
}

func (bb *byteBuilder) data() []byte {
	bb.flush()
	return (*bb.buf)[bb.start+bb.prefixLen:]
}

func (bb *byteBuilder) flush() {
	if bb.child == nil {
		return
	}
	bb.child.flush()
	writeLen((*bb.buf)[bb.child.start:], bb.child.len(), bb.child.prefixLen)
	bb.child = nil
	return
}

func (bb *byteBuilder) finish() []byte {
	bb.flush()
	return *bb.buf
}

func (bb *byteBuilder) addU8(u uint8) {
	bb.flush()
	*bb.buf = append(*bb.buf, u)
}

func (bb *byteBuilder) addU16(u uint16) {
	bb.flush()
	*bb.buf = append(*bb.buf, byte(u>>8), byte(u))
}

func (bb *byteBuilder) addU24(u int) {
	bb.flush()
	*bb.buf = append(*bb.buf, byte(u>>16), byte(u>>8), byte(u))
}

func (bb *byteBuilder) addU32(u uint32) {
	bb.flush()
	*bb.buf = append(*bb.buf, byte(u>>24), byte(u>>16), byte(u>>8), byte(u))
}

func (bb *byteBuilder) addU64(u uint64) {
	bb.flush()
	var b [8]byte
	binary.BigEndian.PutUint64(b[:], u)
	*bb.buf = append(*bb.buf, b[:]...)
}

func (bb *byteBuilder) addU8LengthPrefixed() *byteBuilder {
	return bb.createChild(1)
}

func (bb *byteBuilder) addU16LengthPrefixed() *byteBuilder {
	return bb.createChild(2)
}

func (bb *byteBuilder) addU24LengthPrefixed() *byteBuilder {
	return bb.createChild(3)
}

func (bb *byteBuilder) addU32LengthPrefixed() *byteBuilder {
	return bb.createChild(4)
}

func (bb *byteBuilder) addBytes(b []byte) {
	bb.flush()
	*bb.buf = append(*bb.buf, b...)
}

func (bb *byteBuilder) createChild(lengthPrefixSize int) *byteBuilder {
	bb.flush()
	bb.child = &byteBuilder{
		buf:       bb.buf,
		start:     len(*bb.buf),
		prefixLen: lengthPrefixSize,
	}
	for i := 0; i < lengthPrefixSize; i++ {
		*bb.buf = append(*bb.buf, 0)
	}
	return bb.child
}

func (bb *byteBuilder) discardChild() {
	if bb.child == nil {
		return
	}
	*bb.buf = (*bb.buf)[:bb.child.start]
	bb.child = nil
}

type byteReader []byte

func (br *byteReader) readInternal(out *byteReader, n int) bool {
	if len(*br) < n {
		return false
	}
	*out = (*br)[:n]
	*br = (*br)[n:]
	return true
}

func (br *byteReader) readBytes(out *[]byte, n int) bool {
	var child byteReader
	if !br.readInternal(&child, n) {
		return false
	}
	*out = []byte(child)
	return true
}

func (br *byteReader) readUint(out *uint64, n int) bool {
	var b []byte
	if !br.readBytes(&b, n) {
		return false
	}
	*out = 0
	for _, v := range b {
		*out <<= 8
		*out |= uint64(v)
	}
	return true
}

func (br *byteReader) readU8(out *uint8) bool {
	var b []byte
	if !br.readBytes(&b, 1) {
		return false
	}
	*out = b[0]
	return true
}

func (br *byteReader) readU16(out *uint16) bool {
	var v uint64
	if !br.readUint(&v, 2) {
		return false
	}
	*out = uint16(v)
	return true
}

func (br *byteReader) readU24(out *uint32) bool {
	var v uint64
	if !br.readUint(&v, 3) {
		return false
	}
	*out = uint32(v)
	return true
}

func (br *byteReader) readU32(out *uint32) bool {
	var v uint64
	if !br.readUint(&v, 4) {
		return false
	}
	*out = uint32(v)
	return true
}

func (br *byteReader) readU64(out *uint64) bool {
	return br.readUint(out, 8)
}

func (br *byteReader) readLengthPrefixed(out *byteReader, n int) bool {
	var length uint64
	return br.readUint(&length, n) &&
		uint64(len(*br)) >= length &&
		br.readInternal(out, int(length))
}

func (br *byteReader) readLengthPrefixedBytes(out *[]byte, n int) bool {
	var length uint64
	return br.readUint(&length, n) &&
		uint64(len(*br)) >= length &&
		br.readBytes(out, int(length))
}

func (br *byteReader) readU8LengthPrefixed(out *byteReader) bool {
	return br.readLengthPrefixed(out, 1)
}
func (br *byteReader) readU8LengthPrefixedBytes(out *[]byte) bool {
	return br.readLengthPrefixedBytes(out, 1)
}

func (br *byteReader) readU16LengthPrefixed(out *byteReader) bool {
	return br.readLengthPrefixed(out, 2)
}
func (br *byteReader) readU16LengthPrefixedBytes(out *[]byte) bool {
	return br.readLengthPrefixedBytes(out, 2)
}

func (br *byteReader) readU24LengthPrefixed(out *byteReader) bool {
	return br.readLengthPrefixed(out, 3)
}
func (br *byteReader) readU24LengthPrefixedBytes(out *[]byte) bool {
	return br.readLengthPrefixedBytes(out, 3)
}

func (br *byteReader) readU32LengthPrefixed(out *byteReader) bool {
	return br.readLengthPrefixed(out, 4)
}
func (br *byteReader) readU32LengthPrefixedBytes(out *[]byte) bool {
	return br.readLengthPrefixedBytes(out, 4)
}

type keyShareEntry struct {
	group       CurveID
	keyExchange []byte
}

type pskIdentity struct {
	ticket              []uint8
	obfuscatedTicketAge uint32
}

type clientHelloMsg struct {
	raw                     []byte
	isDTLS                  bool
	vers                    uint16
	random                  []byte
	sessionId               []byte
	cookie                  []byte
	cipherSuites            []uint16
	compressionMethods      []uint8
	nextProtoNeg            bool
	serverName              string
	ocspStapling            bool
	supportedCurves         []CurveID
	supportedPoints         []uint8
	hasKeyShares            bool
	keyShares               []keyShareEntry
	keySharesRaw            []byte
	trailingKeyShareData    bool
	pskIdentities           []pskIdentity
	pskKEModes              []byte
	pskBinders              [][]uint8
	hasEarlyData            bool
	tls13Cookie             []byte
	ticketSupported         bool
	sessionTicket           []uint8
	signatureAlgorithms     []signatureAlgorithm
	signatureAlgorithmsCert []signatureAlgorithm
	supportedVersions       []uint16
	secureRenegotiation     []byte
	alpnProtocols           []string
	quicTransportParams     []byte
	duplicateExtension      bool
	channelIDSupported      bool
	tokenBindingParams      []byte
	tokenBindingVersion     uint16
	npnAfterAlpn            bool
	extendedMasterSecret    bool
	srtpProtectionProfiles  []uint16
	srtpMasterKeyIdentifier string
	sctListSupported        bool
	customExtension         string
	hasGREASEExtension      bool
	pskBinderFirst          bool
	omitExtensions          bool
	emptyExtensions         bool
	pad                     int
	compressedCertAlgs      []uint16
	delegatedCredentials    bool
	pqExperimentSignal      bool
}

func (m *clientHelloMsg) equal(i interface{}) bool {
	m1, ok := i.(*clientHelloMsg)
	if !ok {
		return false
	}

	return bytes.Equal(m.raw, m1.raw) &&
		m.isDTLS == m1.isDTLS &&
		m.vers == m1.vers &&
		bytes.Equal(m.random, m1.random) &&
		bytes.Equal(m.sessionId, m1.sessionId) &&
		bytes.Equal(m.cookie, m1.cookie) &&
		eqUint16s(m.cipherSuites, m1.cipherSuites) &&
		bytes.Equal(m.compressionMethods, m1.compressionMethods) &&
		m.nextProtoNeg == m1.nextProtoNeg &&
		m.serverName == m1.serverName &&
		m.ocspStapling == m1.ocspStapling &&
		eqCurveIDs(m.supportedCurves, m1.supportedCurves) &&
		bytes.Equal(m.supportedPoints, m1.supportedPoints) &&
		m.hasKeyShares == m1.hasKeyShares &&
		eqKeyShareEntryLists(m.keyShares, m1.keyShares) &&
		m.trailingKeyShareData == m1.trailingKeyShareData &&
		eqPSKIdentityLists(m.pskIdentities, m1.pskIdentities) &&
		bytes.Equal(m.pskKEModes, m1.pskKEModes) &&
		eqByteSlices(m.pskBinders, m1.pskBinders) &&
		m.hasEarlyData == m1.hasEarlyData &&
		bytes.Equal(m.tls13Cookie, m1.tls13Cookie) &&
		m.ticketSupported == m1.ticketSupported &&
		bytes.Equal(m.sessionTicket, m1.sessionTicket) &&
		eqSignatureAlgorithms(m.signatureAlgorithms, m1.signatureAlgorithms) &&
		eqSignatureAlgorithms(m.signatureAlgorithmsCert, m1.signatureAlgorithmsCert) &&
		eqUint16s(m.supportedVersions, m1.supportedVersions) &&
		bytes.Equal(m.secureRenegotiation, m1.secureRenegotiation) &&
		(m.secureRenegotiation == nil) == (m1.secureRenegotiation == nil) &&
		eqStrings(m.alpnProtocols, m1.alpnProtocols) &&
		bytes.Equal(m.quicTransportParams, m1.quicTransportParams) &&
		m.duplicateExtension == m1.duplicateExtension &&
		m.channelIDSupported == m1.channelIDSupported &&
		bytes.Equal(m.tokenBindingParams, m1.tokenBindingParams) &&
		m.tokenBindingVersion == m1.tokenBindingVersion &&
		m.npnAfterAlpn == m1.npnAfterAlpn &&
		m.extendedMasterSecret == m1.extendedMasterSecret &&
		eqUint16s(m.srtpProtectionProfiles, m1.srtpProtectionProfiles) &&
		m.srtpMasterKeyIdentifier == m1.srtpMasterKeyIdentifier &&
		m.sctListSupported == m1.sctListSupported &&
		m.customExtension == m1.customExtension &&
		m.hasGREASEExtension == m1.hasGREASEExtension &&
		m.pskBinderFirst == m1.pskBinderFirst &&
		m.omitExtensions == m1.omitExtensions &&
		m.emptyExtensions == m1.emptyExtensions &&
		m.pad == m1.pad &&
		eqUint16s(m.compressedCertAlgs, m1.compressedCertAlgs) &&
		m.delegatedCredentials == m1.delegatedCredentials &&
		m.pqExperimentSignal == m1.pqExperimentSignal
}

func (m *clientHelloMsg) marshalKeyShares(bb *byteBuilder) {
	keyShares := bb.addU16LengthPrefixed()
	for _, keyShare := range m.keyShares {
		keyShares.addU16(uint16(keyShare.group))
		keyExchange := keyShares.addU16LengthPrefixed()
		keyExchange.addBytes(keyShare.keyExchange)
	}
	if m.trailingKeyShareData {
		keyShares.addU8(0)
	}
}

func (m *clientHelloMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	handshakeMsg := newByteBuilder()
	handshakeMsg.addU8(typeClientHello)
	hello := handshakeMsg.addU24LengthPrefixed()
	hello.addU16(m.vers)
	hello.addBytes(m.random)
	sessionId := hello.addU8LengthPrefixed()
	sessionId.addBytes(m.sessionId)
	if m.isDTLS {
		cookie := hello.addU8LengthPrefixed()
		cookie.addBytes(m.cookie)
	}
	cipherSuites := hello.addU16LengthPrefixed()
	for _, suite := range m.cipherSuites {
		cipherSuites.addU16(suite)
	}
	compressionMethods := hello.addU8LengthPrefixed()
	compressionMethods.addBytes(m.compressionMethods)

	extensions := hello.addU16LengthPrefixed()
	if len(m.pskIdentities) > 0 && m.pskBinderFirst {
		extensions.addU16(extensionPreSharedKey)
		pskExtension := extensions.addU16LengthPrefixed()

		pskIdentities := pskExtension.addU16LengthPrefixed()
		for _, psk := range m.pskIdentities {
			pskIdentities.addU16LengthPrefixed().addBytes(psk.ticket)
			pskIdentities.addU32(psk.obfuscatedTicketAge)
		}
		pskBinders := pskExtension.addU16LengthPrefixed()
		for _, binder := range m.pskBinders {
			pskBinders.addU8LengthPrefixed().addBytes(binder)
		}
	}
	if m.duplicateExtension {
		// Add a duplicate bogus extension at the beginning and end.
		extensions.addU16(0xffff)
		extensions.addU16(0) // 0-length for empty extension
	}
	if m.nextProtoNeg && !m.npnAfterAlpn {
		extensions.addU16(extensionNextProtoNeg)
		extensions.addU16(0) // The length is always 0
	}
	if len(m.serverName) > 0 {
		extensions.addU16(extensionServerName)
		serverNameList := extensions.addU16LengthPrefixed()

		// RFC 3546, section 3.1
		//
		// struct {
		//     NameType name_type;
		//     select (name_type) {
		//         case host_name: HostName;
		//     } name;
		// } ServerName;
		//
		// enum {
		//     host_name(0), (255)
		// } NameType;
		//
		// opaque HostName<1..2^16-1>;
		//
		// struct {
		//     ServerName server_name_list<1..2^16-1>
		// } ServerNameList;

		serverName := serverNameList.addU16LengthPrefixed()
		serverName.addU8(0) // NameType host_name(0)
		hostName := serverName.addU16LengthPrefixed()
		hostName.addBytes([]byte(m.serverName))
	}
	if m.ocspStapling {
		extensions.addU16(extensionStatusRequest)
		certificateStatusRequest := extensions.addU16LengthPrefixed()

		// RFC 4366, section 3.6
		certificateStatusRequest.addU8(1) // OCSP type
		// Two zero valued uint16s for the two lengths.
		certificateStatusRequest.addU16(0) // ResponderID length
		certificateStatusRequest.addU16(0) // Extensions length
	}
	if len(m.supportedCurves) > 0 {
		// http://tools.ietf.org/html/rfc4492#section-5.1.1
		extensions.addU16(extensionSupportedCurves)
		supportedCurvesList := extensions.addU16LengthPrefixed()
		supportedCurves := supportedCurvesList.addU16LengthPrefixed()
		for _, curve := range m.supportedCurves {
			supportedCurves.addU16(uint16(curve))
		}
	}
	if len(m.supportedPoints) > 0 {
		// http://tools.ietf.org/html/rfc4492#section-5.1.2
		extensions.addU16(extensionSupportedPoints)
		supportedPointsList := extensions.addU16LengthPrefixed()
		supportedPoints := supportedPointsList.addU8LengthPrefixed()
		supportedPoints.addBytes(m.supportedPoints)
	}
	if m.hasKeyShares {
		extensions.addU16(extensionKeyShare)
		keyShareList := extensions.addU16LengthPrefixed()
		m.marshalKeyShares(keyShareList)
	}
	if len(m.pskKEModes) > 0 {
		extensions.addU16(extensionPSKKeyExchangeModes)
		pskModesExtension := extensions.addU16LengthPrefixed()
		pskModesExtension.addU8LengthPrefixed().addBytes(m.pskKEModes)
	}
	if m.hasEarlyData {
		extensions.addU16(extensionEarlyData)
		extensions.addU16(0) // The length is zero.
	}
	if len(m.tls13Cookie) > 0 {
		extensions.addU16(extensionCookie)
		body := extensions.addU16LengthPrefixed()
		body.addU16LengthPrefixed().addBytes(m.tls13Cookie)
	}
	if m.ticketSupported {
		// http://tools.ietf.org/html/rfc5077#section-3.2
		extensions.addU16(extensionSessionTicket)
		sessionTicketExtension := extensions.addU16LengthPrefixed()
		sessionTicketExtension.addBytes(m.sessionTicket)
	}
	if len(m.signatureAlgorithms) > 0 {
		// https://tools.ietf.org/html/rfc5246#section-7.4.1.4.1
		extensions.addU16(extensionSignatureAlgorithms)
		signatureAlgorithmsExtension := extensions.addU16LengthPrefixed()
		signatureAlgorithms := signatureAlgorithmsExtension.addU16LengthPrefixed()
		for _, sigAlg := range m.signatureAlgorithms {
			signatureAlgorithms.addU16(uint16(sigAlg))
		}
	}
	if len(m.signatureAlgorithmsCert) > 0 {
		extensions.addU16(extensionSignatureAlgorithmsCert)
		signatureAlgorithmsCertExtension := extensions.addU16LengthPrefixed()
		signatureAlgorithmsCert := signatureAlgorithmsCertExtension.addU16LengthPrefixed()
		for _, sigAlg := range m.signatureAlgorithmsCert {
			signatureAlgorithmsCert.addU16(uint16(sigAlg))
		}
	}
	if len(m.supportedVersions) > 0 {
		extensions.addU16(extensionSupportedVersions)
		supportedVersionsExtension := extensions.addU16LengthPrefixed()
		supportedVersions := supportedVersionsExtension.addU8LengthPrefixed()
		for _, version := range m.supportedVersions {
			supportedVersions.addU16(uint16(version))
		}
	}
	if m.secureRenegotiation != nil {
		extensions.addU16(extensionRenegotiationInfo)
		secureRenegoExt := extensions.addU16LengthPrefixed()
		secureRenego := secureRenegoExt.addU8LengthPrefixed()
		secureRenego.addBytes(m.secureRenegotiation)
	}
	if len(m.alpnProtocols) > 0 {
		// https://tools.ietf.org/html/rfc7301#section-3.1
		extensions.addU16(extensionALPN)
		alpnExtension := extensions.addU16LengthPrefixed()

		protocolNameList := alpnExtension.addU16LengthPrefixed()
		for _, s := range m.alpnProtocols {
			protocolName := protocolNameList.addU8LengthPrefixed()
			protocolName.addBytes([]byte(s))
		}
	}
	if len(m.quicTransportParams) > 0 {
		extensions.addU16(extensionQUICTransportParams)
		params := extensions.addU16LengthPrefixed()
		params.addBytes(m.quicTransportParams)
	}
	if m.channelIDSupported {
		extensions.addU16(extensionChannelID)
		extensions.addU16(0) // Length is always 0
	}
	if m.tokenBindingParams != nil {
		extensions.addU16(extensionTokenBinding)
		tokbindExtension := extensions.addU16LengthPrefixed()
		tokbindExtension.addU16(m.tokenBindingVersion)
		tokbindParams := tokbindExtension.addU8LengthPrefixed()
		tokbindParams.addBytes(m.tokenBindingParams)
	}
	if m.nextProtoNeg && m.npnAfterAlpn {
		extensions.addU16(extensionNextProtoNeg)
		extensions.addU16(0) // Length is always 0
	}
	if m.duplicateExtension {
		// Add a duplicate bogus extension at the beginning and end.
		extensions.addU16(0xffff)
		extensions.addU16(0)
	}
	if m.extendedMasterSecret {
		// https://tools.ietf.org/html/rfc7627
		extensions.addU16(extensionExtendedMasterSecret)
		extensions.addU16(0) // Length is always 0
	}
	if len(m.srtpProtectionProfiles) > 0 {
		// https://tools.ietf.org/html/rfc5764#section-4.1.1
		extensions.addU16(extensionUseSRTP)
		useSrtpExt := extensions.addU16LengthPrefixed()

		srtpProtectionProfiles := useSrtpExt.addU16LengthPrefixed()
		for _, p := range m.srtpProtectionProfiles {
			srtpProtectionProfiles.addU16(p)
		}
		srtpMki := useSrtpExt.addU8LengthPrefixed()
		srtpMki.addBytes([]byte(m.srtpMasterKeyIdentifier))
	}
	if m.sctListSupported {
		extensions.addU16(extensionSignedCertificateTimestamp)
		extensions.addU16(0) // Length is always 0
	}
	if l := len(m.customExtension); l > 0 {
		extensions.addU16(extensionCustom)
		customExt := extensions.addU16LengthPrefixed()
		customExt.addBytes([]byte(m.customExtension))
	}
	if len(m.compressedCertAlgs) > 0 {
		extensions.addU16(extensionCompressedCertAlgs)
		body := extensions.addU16LengthPrefixed()
		algIDs := body.addU8LengthPrefixed()
		for _, v := range m.compressedCertAlgs {
			algIDs.addU16(v)
		}
	}
	if m.delegatedCredentials {
		extensions.addU16(extensionDelegatedCredentials)
		extensions.addU16(0) // Length is always 0
	}
	if m.pqExperimentSignal {
		extensions.addU16(extensionPQExperimentSignal)
		extensions.addU16(0) // Length is always 0
	}

	// The PSK extension must be last. See https://tools.ietf.org/html/rfc8446#section-4.2.11
	if len(m.pskIdentities) > 0 && !m.pskBinderFirst {
		extensions.addU16(extensionPreSharedKey)
		pskExtension := extensions.addU16LengthPrefixed()

		pskIdentities := pskExtension.addU16LengthPrefixed()
		for _, psk := range m.pskIdentities {
			pskIdentities.addU16LengthPrefixed().addBytes(psk.ticket)
			pskIdentities.addU32(psk.obfuscatedTicketAge)
		}
		pskBinders := pskExtension.addU16LengthPrefixed()
		for _, binder := range m.pskBinders {
			pskBinders.addU8LengthPrefixed().addBytes(binder)
		}
	}

	if m.pad != 0 && hello.len()%m.pad != 0 {
		extensions.addU16(extensionPadding)
		padding := extensions.addU16LengthPrefixed()
		// Note hello.len() has changed at this point from the length
		// prefix.
		if l := hello.len() % m.pad; l != 0 {
			padding.addBytes(make([]byte, m.pad-l))
		}
	}

	if m.omitExtensions || m.emptyExtensions {
		// Silently erase any extensions which were sent.
		hello.discardChild()
		if m.emptyExtensions {
			hello.addU16(0)
		}
	}

	m.raw = handshakeMsg.finish()
	// Sanity-check padding.
	if m.pad != 0 && (len(m.raw)-4)%m.pad != 0 {
		panic(fmt.Sprintf("%d is not a multiple of %d", len(m.raw)-4, m.pad))
	}
	return m.raw
}

func parseSignatureAlgorithms(reader *byteReader, out *[]signatureAlgorithm, allowEmpty bool) bool {
	var sigAlgs byteReader
	if !reader.readU16LengthPrefixed(&sigAlgs) {
		return false
	}
	if !allowEmpty && len(sigAlgs) == 0 {
		return false
	}
	*out = make([]signatureAlgorithm, 0, len(sigAlgs)/2)
	for len(sigAlgs) > 0 {
		var v uint16
		if !sigAlgs.readU16(&v) {
			return false
		}
		*out = append(*out, signatureAlgorithm(v))
	}
	return true
}

func checkDuplicateExtensions(extensions byteReader) bool {
	seen := make(map[uint16]struct{})
	for len(extensions) > 0 {
		var extension uint16
		var body byteReader
		if !extensions.readU16(&extension) ||
			!extensions.readU16LengthPrefixed(&body) {
			return false
		}
		if _, ok := seen[extension]; ok {
			return false
		}
		seen[extension] = struct{}{}
	}
	return true
}

func (m *clientHelloMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	if !reader.readU16(&m.vers) ||
		!reader.readBytes(&m.random, 32) ||
		!reader.readU8LengthPrefixedBytes(&m.sessionId) ||
		len(m.sessionId) > 32 {
		return false
	}
	if m.isDTLS {
		if !reader.readU8LengthPrefixedBytes(&m.cookie) ||
			len(m.cookie) > 32 {
			return false
		}
	}
	var cipherSuites byteReader
	if !reader.readU16LengthPrefixed(&cipherSuites) ||
		!reader.readU8LengthPrefixedBytes(&m.compressionMethods) {
		return false
	}

	m.cipherSuites = make([]uint16, 0, len(cipherSuites)/2)
	for len(cipherSuites) > 0 {
		var v uint16
		if !cipherSuites.readU16(&v) {
			return false
		}
		m.cipherSuites = append(m.cipherSuites, v)
		if v == scsvRenegotiation {
			m.secureRenegotiation = []byte{}
		}
	}

	m.nextProtoNeg = false
	m.serverName = ""
	m.ocspStapling = false
	m.keyShares = nil
	m.pskIdentities = nil
	m.hasEarlyData = false
	m.ticketSupported = false
	m.sessionTicket = nil
	m.signatureAlgorithms = nil
	m.signatureAlgorithmsCert = nil
	m.supportedVersions = nil
	m.alpnProtocols = nil
	m.extendedMasterSecret = false
	m.customExtension = ""
	m.delegatedCredentials = false
	m.pqExperimentSignal = false

	if len(reader) == 0 {
		// ClientHello is optionally followed by extension data
		return true
	}

	var extensions byteReader
	if !reader.readU16LengthPrefixed(&extensions) || len(reader) != 0 || !checkDuplicateExtensions(extensions) {
		return false
	}
	for len(extensions) > 0 {
		var extension uint16
		var body byteReader
		if !extensions.readU16(&extension) ||
			!extensions.readU16LengthPrefixed(&body) {
			return false
		}
		switch extension {
		case extensionServerName:
			var names byteReader
			if !body.readU16LengthPrefixed(&names) || len(body) != 0 {
				return false
			}
			for len(names) > 0 {
				var nameType byte
				var name []byte
				if !names.readU8(&nameType) ||
					!names.readU16LengthPrefixedBytes(&name) {
					return false
				}
				if nameType == 0 {
					m.serverName = string(name)
				}
			}
		case extensionNextProtoNeg:
			if len(body) != 0 {
				return false
			}
			m.nextProtoNeg = true
		case extensionStatusRequest:
			m.ocspStapling = len(body) > 0 && body[0] == statusTypeOCSP
		case extensionSupportedCurves:
			// http://tools.ietf.org/html/rfc4492#section-5.5.1
			var curves byteReader
			if !body.readU16LengthPrefixed(&curves) || len(body) != 0 {
				return false
			}
			m.supportedCurves = make([]CurveID, 0, len(curves)/2)
			for len(curves) > 0 {
				var v uint16
				if !curves.readU16(&v) {
					return false
				}
				m.supportedCurves = append(m.supportedCurves, CurveID(v))
			}
		case extensionSupportedPoints:
			// http://tools.ietf.org/html/rfc4492#section-5.5.2
			if !body.readU8LengthPrefixedBytes(&m.supportedPoints) || len(body) != 0 {
				return false
			}
		case extensionSessionTicket:
			// http://tools.ietf.org/html/rfc5077#section-3.2
			m.ticketSupported = true
			m.sessionTicket = []byte(body)
		case extensionKeyShare:
			// https://tools.ietf.org/html/rfc8446#section-4.2.8
			m.hasKeyShares = true
			m.keySharesRaw = body
			var keyShares byteReader
			if !body.readU16LengthPrefixed(&keyShares) || len(body) != 0 {
				return false
			}
			for len(keyShares) > 0 {
				var entry keyShareEntry
				var group uint16
				if !keyShares.readU16(&group) ||
					!keyShares.readU16LengthPrefixedBytes(&entry.keyExchange) {
					return false
				}
				entry.group = CurveID(group)
				m.keyShares = append(m.keyShares, entry)
			}
		case extensionPreSharedKey:
			// https://tools.ietf.org/html/rfc8446#section-4.2.11
			var psks, binders byteReader
			if !body.readU16LengthPrefixed(&psks) ||
				!body.readU16LengthPrefixed(&binders) ||
				len(body) != 0 {
				return false
			}
			for len(psks) > 0 {
				var psk pskIdentity
				if !psks.readU16LengthPrefixedBytes(&psk.ticket) ||
					!psks.readU32(&psk.obfuscatedTicketAge) {
					return false
				}
				m.pskIdentities = append(m.pskIdentities, psk)
			}
			for len(binders) > 0 {
				var binder []byte
				if !binders.readU8LengthPrefixedBytes(&binder) {
					return false
				}
				m.pskBinders = append(m.pskBinders, binder)
			}

			// There must be the same number of identities as binders.
			if len(m.pskIdentities) != len(m.pskBinders) {
				return false
			}
		case extensionPSKKeyExchangeModes:
			// https://tools.ietf.org/html/rfc8446#section-4.2.9
			if !body.readU8LengthPrefixedBytes(&m.pskKEModes) || len(body) != 0 {
				return false
			}
		case extensionEarlyData:
			// https://tools.ietf.org/html/rfc8446#section-4.2.10
			if len(body) != 0 {
				return false
			}
			m.hasEarlyData = true
		case extensionCookie:
			if !body.readU16LengthPrefixedBytes(&m.tls13Cookie) || len(body) != 0 {
				return false
			}
		case extensionSignatureAlgorithms:
			// https://tools.ietf.org/html/rfc5246#section-7.4.1.4.1
			if !parseSignatureAlgorithms(&body, &m.signatureAlgorithms, false) || len(body) != 0 {
				return false
			}
		case extensionSignatureAlgorithmsCert:
			if !parseSignatureAlgorithms(&body, &m.signatureAlgorithmsCert, false) || len(body) != 0 {
				return false
			}
		case extensionSupportedVersions:
			var versions byteReader
			if !body.readU8LengthPrefixed(&versions) || len(body) != 0 {
				return false
			}
			m.supportedVersions = make([]uint16, 0, len(versions)/2)
			for len(versions) > 0 {
				var v uint16
				if !versions.readU16(&v) {
					return false
				}
				m.supportedVersions = append(m.supportedVersions, v)
			}
		case extensionRenegotiationInfo:
			if !body.readU8LengthPrefixedBytes(&m.secureRenegotiation) || len(body) != 0 {
				return false
			}
		case extensionALPN:
			var protocols byteReader
			if !body.readU16LengthPrefixed(&protocols) || len(body) != 0 {
				return false
			}
			for len(protocols) > 0 {
				var protocol []byte
				if !protocols.readU8LengthPrefixedBytes(&protocol) {
					return false
				}
				m.alpnProtocols = append(m.alpnProtocols, string(protocol))
			}
		case extensionQUICTransportParams:
			m.quicTransportParams = body
		case extensionChannelID:
			if len(body) != 0 {
				return false
			}
			m.channelIDSupported = true
		case extensionTokenBinding:
			if !body.readU16(&m.tokenBindingVersion) ||
				!body.readU8LengthPrefixedBytes(&m.tokenBindingParams) ||
				len(body) != 0 {
				return false
			}
		case extensionExtendedMasterSecret:
			if len(body) != 0 {
				return false
			}
			m.extendedMasterSecret = true
		case extensionUseSRTP:
			var profiles byteReader
			var mki []byte
			if !body.readU16LengthPrefixed(&profiles) ||
				!body.readU8LengthPrefixedBytes(&mki) ||
				len(body) != 0 {
				return false
			}
			m.srtpProtectionProfiles = make([]uint16, 0, len(profiles)/2)
			for len(profiles) > 0 {
				var v uint16
				if !profiles.readU16(&v) {
					return false
				}
				m.srtpProtectionProfiles = append(m.srtpProtectionProfiles, v)
			}
			m.srtpMasterKeyIdentifier = string(mki)
		case extensionSignedCertificateTimestamp:
			if len(body) != 0 {
				return false
			}
			m.sctListSupported = true
		case extensionCustom:
			m.customExtension = string(body)
		case extensionCompressedCertAlgs:
			var algIDs byteReader
			if !body.readU8LengthPrefixed(&algIDs) {
				return false
			}

			seen := make(map[uint16]struct{})
			for len(algIDs) > 0 {
				var algID uint16
				if !algIDs.readU16(&algID) {
					return false
				}
				if _, ok := seen[algID]; ok {
					return false
				}
				seen[algID] = struct{}{}
				m.compressedCertAlgs = append(m.compressedCertAlgs, algID)
			}
		case extensionPadding:
			// Padding bytes must be all zero.
			for _, b := range body {
				if b != 0 {
					return false
				}
			}
		case extensionDelegatedCredentials:
			if len(body) != 0 {
				return false
			}
			m.delegatedCredentials = true
		case extensionPQExperimentSignal:
			if len(body) != 0 {
				return false
			}
			m.pqExperimentSignal = true
		}

		if isGREASEValue(extension) {
			m.hasGREASEExtension = true
		}
	}

	return true
}

type serverHelloMsg struct {
	raw                   []byte
	isDTLS                bool
	vers                  uint16
	versOverride          uint16
	supportedVersOverride uint16
	omitSupportedVers     bool
	random                []byte
	sessionId             []byte
	cipherSuite           uint16
	hasKeyShare           bool
	keyShare              keyShareEntry
	hasPSKIdentity        bool
	pskIdentity           uint16
	compressionMethod     uint8
	customExtension       string
	unencryptedALPN       string
	omitExtensions        bool
	emptyExtensions       bool
	extensions            serverExtensions
}

func (m *serverHelloMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	handshakeMsg := newByteBuilder()
	handshakeMsg.addU8(typeServerHello)
	hello := handshakeMsg.addU24LengthPrefixed()

	// m.vers is used both to determine the format of the rest of the
	// ServerHello and to override the value, so include a second version
	// field.
	vers, ok := wireToVersion(m.vers, m.isDTLS)
	if !ok {
		panic("unknown version")
	}
	if m.versOverride != 0 {
		hello.addU16(m.versOverride)
	} else if vers >= VersionTLS13 {
		hello.addU16(VersionTLS12)
	} else {
		hello.addU16(m.vers)
	}

	hello.addBytes(m.random)
	sessionId := hello.addU8LengthPrefixed()
	sessionId.addBytes(m.sessionId)
	hello.addU16(m.cipherSuite)
	hello.addU8(m.compressionMethod)

	extensions := hello.addU16LengthPrefixed()

	if vers >= VersionTLS13 {
		if m.hasKeyShare {
			extensions.addU16(extensionKeyShare)
			keyShare := extensions.addU16LengthPrefixed()
			keyShare.addU16(uint16(m.keyShare.group))
			keyExchange := keyShare.addU16LengthPrefixed()
			keyExchange.addBytes(m.keyShare.keyExchange)
		}
		if m.hasPSKIdentity {
			extensions.addU16(extensionPreSharedKey)
			extensions.addU16(2) // Length
			extensions.addU16(m.pskIdentity)
		}
		if !m.omitSupportedVers {
			extensions.addU16(extensionSupportedVersions)
			extensions.addU16(2) // Length
			if m.supportedVersOverride != 0 {
				extensions.addU16(m.supportedVersOverride)
			} else {
				extensions.addU16(m.vers)
			}
		}
		if len(m.customExtension) > 0 {
			extensions.addU16(extensionCustom)
			customExt := extensions.addU16LengthPrefixed()
			customExt.addBytes([]byte(m.customExtension))
		}
		if len(m.unencryptedALPN) > 0 {
			extensions.addU16(extensionALPN)
			extension := extensions.addU16LengthPrefixed()

			protocolNameList := extension.addU16LengthPrefixed()
			protocolName := protocolNameList.addU8LengthPrefixed()
			protocolName.addBytes([]byte(m.unencryptedALPN))
		}
	} else {
		m.extensions.marshal(extensions)
		if m.omitExtensions || m.emptyExtensions {
			// Silently erasing server extensions will break the handshake. Instead,
			// assert that tests which use this field also disable all features which
			// would write an extension.
			if extensions.len() != 0 {
				panic(fmt.Sprintf("ServerHello unexpectedly contained extensions: %x, %+v", extensions.data(), m))
			}
			hello.discardChild()
			if m.emptyExtensions {
				hello.addU16(0)
			}
		}
	}

	m.raw = handshakeMsg.finish()
	return m.raw
}

func (m *serverHelloMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	if !reader.readU16(&m.vers) ||
		!reader.readBytes(&m.random, 32) {
		return false
	}
	vers, ok := wireToVersion(m.vers, m.isDTLS)
	if !ok {
		return false
	}
	if !reader.readU8LengthPrefixedBytes(&m.sessionId) ||
		!reader.readU16(&m.cipherSuite) ||
		!reader.readU8(&m.compressionMethod) {
		return false
	}

	if len(reader) == 0 && m.vers < VersionTLS13 {
		// Extension data is optional before TLS 1.3.
		m.extensions = serverExtensions{}
		m.omitExtensions = true
		return true
	}

	var extensions byteReader
	if !reader.readU16LengthPrefixed(&extensions) || len(reader) != 0 || !checkDuplicateExtensions(extensions) {
		return false
	}

	// Parse out the version from supported_versions if available.
	if m.vers == VersionTLS12 {
		extensionsCopy := extensions
		for len(extensionsCopy) > 0 {
			var extension uint16
			var body byteReader
			if !extensionsCopy.readU16(&extension) ||
				!extensionsCopy.readU16LengthPrefixed(&body) {
				return false
			}
			if extension == extensionSupportedVersions {
				if !body.readU16(&m.vers) || len(body) != 0 {
					return false
				}
				vers, ok = wireToVersion(m.vers, m.isDTLS)
				if !ok {
					return false
				}
			}
		}
	}

	if vers >= VersionTLS13 {
		for len(extensions) > 0 {
			var extension uint16
			var body byteReader
			if !extensions.readU16(&extension) ||
				!extensions.readU16LengthPrefixed(&body) {
				return false
			}
			switch extension {
			case extensionKeyShare:
				m.hasKeyShare = true
				var group uint16
				if !body.readU16(&group) ||
					!body.readU16LengthPrefixedBytes(&m.keyShare.keyExchange) ||
					len(body) != 0 {
					return false
				}
				m.keyShare.group = CurveID(group)
			case extensionPreSharedKey:
				if !body.readU16(&m.pskIdentity) || len(body) != 0 {
					return false
				}
				m.hasPSKIdentity = true
			case extensionSupportedVersions:
				// Parsed above.
			default:
				// Only allow the 3 extensions that are sent in
				// the clear in TLS 1.3.
				return false
			}
		}
	} else if !m.extensions.unmarshal(extensions, vers) {
		return false
	}

	return true
}

type encryptedExtensionsMsg struct {
	raw        []byte
	extensions serverExtensions
	empty      bool
}

func (m *encryptedExtensionsMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	encryptedExtensionsMsg := newByteBuilder()
	encryptedExtensionsMsg.addU8(typeEncryptedExtensions)
	encryptedExtensions := encryptedExtensionsMsg.addU24LengthPrefixed()
	if !m.empty {
		extensions := encryptedExtensions.addU16LengthPrefixed()
		m.extensions.marshal(extensions)
	}

	m.raw = encryptedExtensionsMsg.finish()
	return m.raw
}

func (m *encryptedExtensionsMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	var extensions byteReader
	if !reader.readU16LengthPrefixed(&extensions) || len(reader) != 0 {
		return false
	}
	return m.extensions.unmarshal(extensions, VersionTLS13)
}

type serverExtensions struct {
	nextProtoNeg            bool
	nextProtos              []string
	ocspStapling            bool
	ticketSupported         bool
	secureRenegotiation     []byte
	alpnProtocol            string
	alpnProtocolEmpty       bool
	duplicateExtension      bool
	channelIDRequested      bool
	tokenBindingParams      []byte
	tokenBindingVersion     uint16
	extendedMasterSecret    bool
	srtpProtectionProfile   uint16
	srtpMasterKeyIdentifier string
	sctList                 []byte
	customExtension         string
	npnAfterAlpn            bool
	hasKeyShare             bool
	hasEarlyData            bool
	keyShare                keyShareEntry
	supportedVersion        uint16
	supportedPoints         []uint8
	supportedCurves         []CurveID
	quicTransportParams     []byte
	serverNameAck           bool
	pqExperimentSignal      bool
}

func (m *serverExtensions) marshal(extensions *byteBuilder) {
	if m.duplicateExtension {
		// Add a duplicate bogus extension at the beginning and end.
		extensions.addU16(0xffff)
		extensions.addU16(0) // length = 0 for empty extension
	}
	if m.nextProtoNeg && !m.npnAfterAlpn {
		extensions.addU16(extensionNextProtoNeg)
		extension := extensions.addU16LengthPrefixed()

		for _, v := range m.nextProtos {
			if len(v) > 255 {
				v = v[:255]
			}
			npn := extension.addU8LengthPrefixed()
			npn.addBytes([]byte(v))
		}
	}
	if m.ocspStapling {
		extensions.addU16(extensionStatusRequest)
		extensions.addU16(0)
	}
	if m.ticketSupported {
		extensions.addU16(extensionSessionTicket)
		extensions.addU16(0)
	}
	if m.secureRenegotiation != nil {
		extensions.addU16(extensionRenegotiationInfo)
		extension := extensions.addU16LengthPrefixed()
		secureRenego := extension.addU8LengthPrefixed()
		secureRenego.addBytes(m.secureRenegotiation)
	}
	if len(m.alpnProtocol) > 0 || m.alpnProtocolEmpty {
		extensions.addU16(extensionALPN)
		extension := extensions.addU16LengthPrefixed()

		protocolNameList := extension.addU16LengthPrefixed()
		protocolName := protocolNameList.addU8LengthPrefixed()
		protocolName.addBytes([]byte(m.alpnProtocol))
	}
	if m.channelIDRequested {
		extensions.addU16(extensionChannelID)
		extensions.addU16(0)
	}
	if m.tokenBindingParams != nil {
		extensions.addU16(extensionTokenBinding)
		tokbindExtension := extensions.addU16LengthPrefixed()
		tokbindExtension.addU16(m.tokenBindingVersion)
		tokbindParams := tokbindExtension.addU8LengthPrefixed()
		tokbindParams.addBytes(m.tokenBindingParams)
	}
	if m.duplicateExtension {
		// Add a duplicate bogus extension at the beginning and end.
		extensions.addU16(0xffff)
		extensions.addU16(0)
	}
	if m.extendedMasterSecret {
		extensions.addU16(extensionExtendedMasterSecret)
		extensions.addU16(0)
	}
	if m.srtpProtectionProfile != 0 {
		extensions.addU16(extensionUseSRTP)
		extension := extensions.addU16LengthPrefixed()

		srtpProtectionProfiles := extension.addU16LengthPrefixed()
		srtpProtectionProfiles.addU16(m.srtpProtectionProfile)
		srtpMki := extension.addU8LengthPrefixed()
		srtpMki.addBytes([]byte(m.srtpMasterKeyIdentifier))
	}
	if m.sctList != nil {
		extensions.addU16(extensionSignedCertificateTimestamp)
		extension := extensions.addU16LengthPrefixed()
		extension.addBytes(m.sctList)
	}
	if l := len(m.customExtension); l > 0 {
		extensions.addU16(extensionCustom)
		customExt := extensions.addU16LengthPrefixed()
		customExt.addBytes([]byte(m.customExtension))
	}
	if m.nextProtoNeg && m.npnAfterAlpn {
		extensions.addU16(extensionNextProtoNeg)
		extension := extensions.addU16LengthPrefixed()

		for _, v := range m.nextProtos {
			if len(v) > 255 {
				v = v[0:255]
			}
			npn := extension.addU8LengthPrefixed()
			npn.addBytes([]byte(v))
		}
	}
	if m.hasKeyShare {
		extensions.addU16(extensionKeyShare)
		keyShare := extensions.addU16LengthPrefixed()
		keyShare.addU16(uint16(m.keyShare.group))
		keyExchange := keyShare.addU16LengthPrefixed()
		keyExchange.addBytes(m.keyShare.keyExchange)
	}
	if m.supportedVersion != 0 {
		extensions.addU16(extensionSupportedVersions)
		extensions.addU16(2) // Length
		extensions.addU16(m.supportedVersion)
	}
	if len(m.supportedPoints) > 0 {
		// http://tools.ietf.org/html/rfc4492#section-5.1.2
		extensions.addU16(extensionSupportedPoints)
		supportedPointsList := extensions.addU16LengthPrefixed()
		supportedPoints := supportedPointsList.addU8LengthPrefixed()
		supportedPoints.addBytes(m.supportedPoints)
	}
	if len(m.supportedCurves) > 0 {
		// https://tools.ietf.org/html/rfc8446#section-4.2.7
		extensions.addU16(extensionSupportedCurves)
		supportedCurvesList := extensions.addU16LengthPrefixed()
		supportedCurves := supportedCurvesList.addU16LengthPrefixed()
		for _, curve := range m.supportedCurves {
			supportedCurves.addU16(uint16(curve))
		}
	}
	if len(m.quicTransportParams) > 0 {
		extensions.addU16(extensionQUICTransportParams)
		params := extensions.addU16LengthPrefixed()
		params.addBytes(m.quicTransportParams)
	}
	if m.hasEarlyData {
		extensions.addU16(extensionEarlyData)
		extensions.addBytes([]byte{0, 0})
	}
	if m.serverNameAck {
		extensions.addU16(extensionServerName)
		extensions.addU16(0) // zero length
	}
	if m.pqExperimentSignal {
		extensions.addU16(extensionPQExperimentSignal)
		extensions.addU16(0) // zero length
	}
}

func (m *serverExtensions) unmarshal(data byteReader, version uint16) bool {
	// Reset all fields.
	*m = serverExtensions{}

	if !checkDuplicateExtensions(data) {
		return false
	}

	for len(data) > 0 {
		var extension uint16
		var body byteReader
		if !data.readU16(&extension) ||
			!data.readU16LengthPrefixed(&body) {
			return false
		}
		switch extension {
		case extensionNextProtoNeg:
			m.nextProtoNeg = true
			for len(body) > 0 {
				var protocol []byte
				if !body.readU8LengthPrefixedBytes(&protocol) {
					return false
				}
				m.nextProtos = append(m.nextProtos, string(protocol))
			}
		case extensionStatusRequest:
			if len(body) != 0 {
				return false
			}
			m.ocspStapling = true
		case extensionSessionTicket:
			if len(body) != 0 {
				return false
			}
			m.ticketSupported = true
		case extensionRenegotiationInfo:
			if !body.readU8LengthPrefixedBytes(&m.secureRenegotiation) || len(body) != 0 {
				return false
			}
		case extensionALPN:
			var protocols, protocol byteReader
			if !body.readU16LengthPrefixed(&protocols) ||
				len(body) != 0 ||
				!protocols.readU8LengthPrefixed(&protocol) ||
				len(protocols) != 0 {
				return false
			}
			m.alpnProtocol = string(protocol)
			m.alpnProtocolEmpty = len(protocol) == 0
		case extensionChannelID:
			if len(body) != 0 {
				return false
			}
			m.channelIDRequested = true
		case extensionTokenBinding:
			if !body.readU16(&m.tokenBindingVersion) ||
				!body.readU8LengthPrefixedBytes(&m.tokenBindingParams) ||
				len(m.tokenBindingParams) != 1 ||
				len(body) != 0 {
				return false
			}
		case extensionExtendedMasterSecret:
			if len(body) != 0 {
				return false
			}
			m.extendedMasterSecret = true
		case extensionUseSRTP:
			var profiles, mki byteReader
			if !body.readU16LengthPrefixed(&profiles) ||
				!profiles.readU16(&m.srtpProtectionProfile) ||
				len(profiles) != 0 ||
				!body.readU8LengthPrefixed(&mki) ||
				len(body) != 0 {
				return false
			}
			m.srtpMasterKeyIdentifier = string(mki)
		case extensionSignedCertificateTimestamp:
			m.sctList = []byte(body)
		case extensionCustom:
			m.customExtension = string(body)
		case extensionServerName:
			if len(body) != 0 {
				return false
			}
			m.serverNameAck = true
		case extensionSupportedPoints:
			// supported_points is illegal in TLS 1.3.
			if version >= VersionTLS13 {
				return false
			}
			// http://tools.ietf.org/html/rfc4492#section-5.5.2
			if !body.readU8LengthPrefixedBytes(&m.supportedPoints) || len(body) != 0 {
				return false
			}
		case extensionSupportedCurves:
			// The server can only send supported_curves in TLS 1.3.
			if version < VersionTLS13 {
				return false
			}
		case extensionQUICTransportParams:
			m.quicTransportParams = body
		case extensionEarlyData:
			if version < VersionTLS13 || len(body) != 0 {
				return false
			}
			m.hasEarlyData = true
		case extensionPQExperimentSignal:
			if len(body) != 0 {
				return false
			}
			m.pqExperimentSignal = true
		default:
			// Unknown extensions are illegal from the server.
			return false
		}
	}

	return true
}

type helloRetryRequestMsg struct {
	raw                 []byte
	vers                uint16
	isServerHello       bool
	sessionId           []byte
	cipherSuite         uint16
	compressionMethod   uint8
	hasSelectedGroup    bool
	selectedGroup       CurveID
	cookie              []byte
	customExtension     string
	duplicateExtensions bool
}

func (m *helloRetryRequestMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	retryRequestMsg := newByteBuilder()
	retryRequestMsg.addU8(typeServerHello)
	retryRequest := retryRequestMsg.addU24LengthPrefixed()
	retryRequest.addU16(VersionTLS12)
	retryRequest.addBytes(tls13HelloRetryRequest)
	sessionId := retryRequest.addU8LengthPrefixed()
	sessionId.addBytes(m.sessionId)
	retryRequest.addU16(m.cipherSuite)
	retryRequest.addU8(m.compressionMethod)

	extensions := retryRequest.addU16LengthPrefixed()

	count := 1
	if m.duplicateExtensions {
		count = 2
	}

	for i := 0; i < count; i++ {
		extensions.addU16(extensionSupportedVersions)
		extensions.addU16(2) // Length
		extensions.addU16(m.vers)
		if m.hasSelectedGroup {
			extensions.addU16(extensionKeyShare)
			extensions.addU16(2) // length
			extensions.addU16(uint16(m.selectedGroup))
		}
		// m.cookie may be a non-nil empty slice for empty cookie tests.
		if m.cookie != nil {
			extensions.addU16(extensionCookie)
			body := extensions.addU16LengthPrefixed()
			body.addU16LengthPrefixed().addBytes(m.cookie)
		}
		if len(m.customExtension) > 0 {
			extensions.addU16(extensionCustom)
			extensions.addU16LengthPrefixed().addBytes([]byte(m.customExtension))
		}
	}

	m.raw = retryRequestMsg.finish()
	return m.raw
}

func (m *helloRetryRequestMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	if !reader.readU16(&m.vers) {
		return false
	}
	if m.isServerHello {
		var random []byte
		var compressionMethod byte
		if !reader.readBytes(&random, 32) ||
			!reader.readU8LengthPrefixedBytes(&m.sessionId) ||
			!reader.readU16(&m.cipherSuite) ||
			!reader.readU8(&compressionMethod) ||
			compressionMethod != 0 {
			return false
		}
	} else if !reader.readU16(&m.cipherSuite) {
		return false
	}
	var extensions byteReader
	if !reader.readU16LengthPrefixed(&extensions) || len(reader) != 0 {
		return false
	}
	extensionsCopy := extensions
	for len(extensionsCopy) > 0 {
		var extension uint16
		var body byteReader
		if !extensionsCopy.readU16(&extension) ||
			!extensionsCopy.readU16LengthPrefixed(&body) {
			return false
		}
		switch extension {
		case extensionSupportedVersions:
			if !m.isServerHello ||
				!body.readU16(&m.vers) ||
				len(body) != 0 {
				return false
			}
		default:
		}
	}
	for len(extensions) > 0 {
		var extension uint16
		var body byteReader
		if !extensions.readU16(&extension) ||
			!extensions.readU16LengthPrefixed(&body) {
			return false
		}
		switch extension {
		case extensionSupportedVersions:
			// Parsed above.
		case extensionKeyShare:
			var v uint16
			if !body.readU16(&v) || len(body) != 0 {
				return false
			}
			m.hasSelectedGroup = true
			m.selectedGroup = CurveID(v)
		case extensionCookie:
			if !body.readU16LengthPrefixedBytes(&m.cookie) || len(body) != 0 {
				return false
			}
		default:
			// Unknown extensions are illegal from the server.
			return false
		}
	}
	return true
}

type certificateEntry struct {
	data                []byte
	ocspResponse        []byte
	sctList             []byte
	duplicateExtensions bool
	extraExtension      []byte
	delegatedCredential *delegatedCredential
}

type delegatedCredential struct {
	// https://tools.ietf.org/html/draft-ietf-tls-subcerts-03#section-3
	signedBytes            []byte
	lifetimeSecs           uint32
	expectedCertVerifyAlgo signatureAlgorithm
	pkixPublicKey          []byte
	algorithm              signatureAlgorithm
	signature              []byte
}

type certificateMsg struct {
	raw               []byte
	hasRequestContext bool
	requestContext    []byte
	certificates      []certificateEntry
}

func (m *certificateMsg) marshal() (x []byte) {
	if m.raw != nil {
		return m.raw
	}

	certMsg := newByteBuilder()
	certMsg.addU8(typeCertificate)
	certificate := certMsg.addU24LengthPrefixed()
	if m.hasRequestContext {
		context := certificate.addU8LengthPrefixed()
		context.addBytes(m.requestContext)
	}
	certificateList := certificate.addU24LengthPrefixed()
	for _, cert := range m.certificates {
		certEntry := certificateList.addU24LengthPrefixed()
		certEntry.addBytes(cert.data)
		if m.hasRequestContext {
			extensions := certificateList.addU16LengthPrefixed()
			count := 1
			if cert.duplicateExtensions {
				count = 2
			}

			for i := 0; i < count; i++ {
				if cert.ocspResponse != nil {
					extensions.addU16(extensionStatusRequest)
					body := extensions.addU16LengthPrefixed()
					body.addU8(statusTypeOCSP)
					response := body.addU24LengthPrefixed()
					response.addBytes(cert.ocspResponse)
				}

				if cert.sctList != nil {
					extensions.addU16(extensionSignedCertificateTimestamp)
					extension := extensions.addU16LengthPrefixed()
					extension.addBytes(cert.sctList)
				}
			}
			if cert.extraExtension != nil {
				extensions.addBytes(cert.extraExtension)
			}
		}
	}

	m.raw = certMsg.finish()
	return m.raw
}

func (m *certificateMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])

	if m.hasRequestContext && !reader.readU8LengthPrefixedBytes(&m.requestContext) {
		return false
	}

	var certs byteReader
	if !reader.readU24LengthPrefixed(&certs) || len(reader) != 0 {
		return false
	}
	m.certificates = nil
	for len(certs) > 0 {
		var cert certificateEntry
		if !certs.readU24LengthPrefixedBytes(&cert.data) {
			return false
		}
		if m.hasRequestContext {
			var extensions byteReader
			if !certs.readU16LengthPrefixed(&extensions) || !checkDuplicateExtensions(extensions) {
				return false
			}
			for len(extensions) > 0 {
				var extension uint16
				var body byteReader
				if !extensions.readU16(&extension) ||
					!extensions.readU16LengthPrefixed(&body) {
					return false
				}
				switch extension {
				case extensionStatusRequest:
					var statusType byte
					if !body.readU8(&statusType) ||
						statusType != statusTypeOCSP ||
						!body.readU24LengthPrefixedBytes(&cert.ocspResponse) ||
						len(body) != 0 {
						return false
					}
				case extensionSignedCertificateTimestamp:
					cert.sctList = []byte(body)
				case extensionDelegatedCredentials:
					// https://tools.ietf.org/html/draft-ietf-tls-subcerts-03#section-3
					if cert.delegatedCredential != nil {
						return false
					}

					dc := new(delegatedCredential)
					origBody := body
					var expectedCertVerifyAlgo, algorithm uint16

					if !body.readU32(&dc.lifetimeSecs) ||
						!body.readU16(&expectedCertVerifyAlgo) ||
						!body.readU24LengthPrefixedBytes(&dc.pkixPublicKey) ||
						!body.readU16(&algorithm) ||
						!body.readU16LengthPrefixedBytes(&dc.signature) ||
						len(body) != 0 {
						return false
					}

					dc.expectedCertVerifyAlgo = signatureAlgorithm(expectedCertVerifyAlgo)
					dc.algorithm = signatureAlgorithm(algorithm)
					dc.signedBytes = []byte(origBody)[:4+2+3+len(dc.pkixPublicKey)]
					cert.delegatedCredential = dc
				default:
					return false
				}
			}
		}
		m.certificates = append(m.certificates, cert)
	}

	return true
}

type compressedCertificateMsg struct {
	raw                []byte
	algID              uint16
	uncompressedLength uint32
	compressed         []byte
}

func (m *compressedCertificateMsg) marshal() (x []byte) {
	if m.raw != nil {
		return m.raw
	}

	certMsg := newByteBuilder()
	certMsg.addU8(typeCompressedCertificate)
	certificate := certMsg.addU24LengthPrefixed()
	certificate.addU16(m.algID)
	certificate.addU24(int(m.uncompressedLength))
	compressed := certificate.addU24LengthPrefixed()
	compressed.addBytes(m.compressed)

	m.raw = certMsg.finish()
	return m.raw
}

func (m *compressedCertificateMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])

	if !reader.readU16(&m.algID) ||
		!reader.readU24(&m.uncompressedLength) ||
		!reader.readU24LengthPrefixedBytes(&m.compressed) ||
		len(reader) != 0 {
		return false
	}

	if m.uncompressedLength >= 1<<17 {
		return false
	}

	return true
}

type serverKeyExchangeMsg struct {
	raw []byte
	key []byte
}

func (m *serverKeyExchangeMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}
	msg := newByteBuilder()
	msg.addU8(typeServerKeyExchange)
	msg.addU24LengthPrefixed().addBytes(m.key)
	m.raw = msg.finish()
	return m.raw
}

func (m *serverKeyExchangeMsg) unmarshal(data []byte) bool {
	m.raw = data
	if len(data) < 4 {
		return false
	}
	m.key = data[4:]
	return true
}

type certificateStatusMsg struct {
	raw        []byte
	statusType uint8
	response   []byte
}

func (m *certificateStatusMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	var x []byte
	if m.statusType == statusTypeOCSP {
		msg := newByteBuilder()
		msg.addU8(typeCertificateStatus)
		body := msg.addU24LengthPrefixed()
		body.addU8(statusTypeOCSP)
		body.addU24LengthPrefixed().addBytes(m.response)
		x = msg.finish()
	} else {
		x = []byte{typeCertificateStatus, 0, 0, 1, m.statusType}
	}

	m.raw = x
	return x
}

func (m *certificateStatusMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	if !reader.readU8(&m.statusType) ||
		m.statusType != statusTypeOCSP ||
		!reader.readU24LengthPrefixedBytes(&m.response) ||
		len(reader) != 0 {
		return false
	}
	return true
}

type serverHelloDoneMsg struct{}

func (m *serverHelloDoneMsg) marshal() []byte {
	x := make([]byte, 4)
	x[0] = typeServerHelloDone
	return x
}

func (m *serverHelloDoneMsg) unmarshal(data []byte) bool {
	return len(data) == 4
}

type clientKeyExchangeMsg struct {
	raw        []byte
	ciphertext []byte
}

func (m *clientKeyExchangeMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}
	msg := newByteBuilder()
	msg.addU8(typeClientKeyExchange)
	msg.addU24LengthPrefixed().addBytes(m.ciphertext)
	m.raw = msg.finish()
	return m.raw
}

func (m *clientKeyExchangeMsg) unmarshal(data []byte) bool {
	m.raw = data
	if len(data) < 4 {
		return false
	}
	l := int(data[1])<<16 | int(data[2])<<8 | int(data[3])
	if l != len(data)-4 {
		return false
	}
	m.ciphertext = data[4:]
	return true
}

type finishedMsg struct {
	raw        []byte
	verifyData []byte
}

func (m *finishedMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	msg := newByteBuilder()
	msg.addU8(typeFinished)
	msg.addU24LengthPrefixed().addBytes(m.verifyData)
	m.raw = msg.finish()
	return m.raw
}

func (m *finishedMsg) unmarshal(data []byte) bool {
	m.raw = data
	if len(data) < 4 {
		return false
	}
	m.verifyData = data[4:]
	return true
}

type nextProtoMsg struct {
	raw   []byte
	proto string
}

func (m *nextProtoMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	padding := 32 - (len(m.proto)+2)%32

	msg := newByteBuilder()
	msg.addU8(typeNextProtocol)
	body := msg.addU24LengthPrefixed()
	body.addU8LengthPrefixed().addBytes([]byte(m.proto))
	body.addU8LengthPrefixed().addBytes(make([]byte, padding))
	m.raw = msg.finish()
	return m.raw
}

func (m *nextProtoMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])
	var proto, padding []byte
	if !reader.readU8LengthPrefixedBytes(&proto) ||
		!reader.readU8LengthPrefixedBytes(&padding) ||
		len(reader) != 0 {
		return false
	}
	m.proto = string(proto)

	// Padding is not meant to be checked normally, but as this is a testing
	// implementation, we check the padding is as expected.
	if len(padding) != 32-(len(m.proto)+2)%32 {
		return false
	}
	for _, v := range padding {
		if v != 0 {
			return false
		}
	}

	return true
}

type certificateRequestMsg struct {
	raw  []byte
	vers uint16
	// hasSignatureAlgorithm indicates whether this message includes a list
	// of signature and hash functions. This change was introduced with TLS
	// 1.2.
	hasSignatureAlgorithm bool
	// hasRequestContext indicates whether this message includes a context
	// field instead of certificateTypes. This change was introduced with
	// TLS 1.3.
	hasRequestContext bool

	certificateTypes        []byte
	requestContext          []byte
	signatureAlgorithms     []signatureAlgorithm
	signatureAlgorithmsCert []signatureAlgorithm
	certificateAuthorities  [][]byte
	hasCAExtension          bool
	customExtension         uint16
}

func (m *certificateRequestMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	// See http://tools.ietf.org/html/rfc4346#section-7.4.4
	builder := newByteBuilder()
	builder.addU8(typeCertificateRequest)
	body := builder.addU24LengthPrefixed()

	if m.hasRequestContext {
		requestContext := body.addU8LengthPrefixed()
		requestContext.addBytes(m.requestContext)
		extensions := newByteBuilder()
		extensions = body.addU16LengthPrefixed()
		if m.hasSignatureAlgorithm {
			extensions.addU16(extensionSignatureAlgorithms)
			signatureAlgorithms := extensions.addU16LengthPrefixed().addU16LengthPrefixed()
			for _, sigAlg := range m.signatureAlgorithms {
				signatureAlgorithms.addU16(uint16(sigAlg))
			}
		}
		if len(m.signatureAlgorithmsCert) > 0 {
			extensions.addU16(extensionSignatureAlgorithmsCert)
			signatureAlgorithmsCert := extensions.addU16LengthPrefixed().addU16LengthPrefixed()
			for _, sigAlg := range m.signatureAlgorithmsCert {
				signatureAlgorithmsCert.addU16(uint16(sigAlg))
			}
		}
		if len(m.certificateAuthorities) > 0 {
			extensions.addU16(extensionCertificateAuthorities)
			certificateAuthorities := extensions.addU16LengthPrefixed().addU16LengthPrefixed()
			for _, ca := range m.certificateAuthorities {
				caEntry := certificateAuthorities.addU16LengthPrefixed()
				caEntry.addBytes(ca)
			}
		}

		if m.customExtension > 0 {
			extensions.addU16(m.customExtension)
			extensions.addU16LengthPrefixed()
		}
	} else {
		certificateTypes := body.addU8LengthPrefixed()
		certificateTypes.addBytes(m.certificateTypes)

		if m.hasSignatureAlgorithm {
			signatureAlgorithms := body.addU16LengthPrefixed()
			for _, sigAlg := range m.signatureAlgorithms {
				signatureAlgorithms.addU16(uint16(sigAlg))
			}
		}

		certificateAuthorities := body.addU16LengthPrefixed()
		for _, ca := range m.certificateAuthorities {
			caEntry := certificateAuthorities.addU16LengthPrefixed()
			caEntry.addBytes(ca)
		}
	}

	m.raw = builder.finish()
	return m.raw
}

func parseCAs(reader *byteReader, out *[][]byte) bool {
	var cas byteReader
	if !reader.readU16LengthPrefixed(&cas) {
		return false
	}
	for len(cas) > 0 {
		var ca []byte
		if !cas.readU16LengthPrefixedBytes(&ca) {
			return false
		}
		*out = append(*out, ca)
	}
	return true
}

func (m *certificateRequestMsg) unmarshal(data []byte) bool {
	m.raw = data
	reader := byteReader(data[4:])

	if m.hasRequestContext {
		var extensions byteReader
		if !reader.readU8LengthPrefixedBytes(&m.requestContext) ||
			!reader.readU16LengthPrefixed(&extensions) ||
			len(reader) != 0 ||
			!checkDuplicateExtensions(extensions) {
			return false
		}
		for len(extensions) > 0 {
			var extension uint16
			var body byteReader
			if !extensions.readU16(&extension) ||
				!extensions.readU16LengthPrefixed(&body) {
				return false
			}
			switch extension {
			case extensionSignatureAlgorithms:
				if !parseSignatureAlgorithms(&body, &m.signatureAlgorithms, false) || len(body) != 0 {
					return false
				}
			case extensionSignatureAlgorithmsCert:
				if !parseSignatureAlgorithms(&body, &m.signatureAlgorithmsCert, false) || len(body) != 0 {
					return false
				}
			case extensionCertificateAuthorities:
				if !parseCAs(&body, &m.certificateAuthorities) || len(body) != 0 {
					return false
				}
				m.hasCAExtension = true
			}
		}
	} else {
		if !reader.readU8LengthPrefixedBytes(&m.certificateTypes) {
			return false
		}
		// In TLS 1.2, the supported_signature_algorithms field in
		// CertificateRequest may be empty.
		if m.hasSignatureAlgorithm && !parseSignatureAlgorithms(&reader, &m.signatureAlgorithms, true) {
			return false
		}
		if !parseCAs(&reader, &m.certificateAuthorities) ||
			len(reader) != 0 {
			return false
		}
	}

	return true
}

type certificateVerifyMsg struct {
	raw                   []byte
	hasSignatureAlgorithm bool
	signatureAlgorithm    signatureAlgorithm
	signature             []byte
}

func (m *certificateVerifyMsg) marshal() (x []byte) {
	if m.raw != nil {
		return m.raw
	}

	// See http://tools.ietf.org/html/rfc4346#section-7.4.8
	siglength := len(m.signature)
	length := 2 + siglength
	if m.hasSignatureAlgorithm {
		length += 2
	}
	x = make([]byte, 4+length)
	x[0] = typeCertificateVerify
	x[1] = uint8(length >> 16)
	x[2] = uint8(length >> 8)
	x[3] = uint8(length)
	y := x[4:]
	if m.hasSignatureAlgorithm {
		y[0] = byte(m.signatureAlgorithm >> 8)
		y[1] = byte(m.signatureAlgorithm)
		y = y[2:]
	}
	y[0] = uint8(siglength >> 8)
	y[1] = uint8(siglength)
	copy(y[2:], m.signature)

	m.raw = x

	return
}

func (m *certificateVerifyMsg) unmarshal(data []byte) bool {
	m.raw = data

	if len(data) < 6 {
		return false
	}

	length := uint32(data[1])<<16 | uint32(data[2])<<8 | uint32(data[3])
	if uint32(len(data))-4 != length {
		return false
	}

	data = data[4:]
	if m.hasSignatureAlgorithm {
		m.signatureAlgorithm = signatureAlgorithm(data[0])<<8 | signatureAlgorithm(data[1])
		data = data[2:]
	}

	if len(data) < 2 {
		return false
	}
	siglength := int(data[0])<<8 + int(data[1])
	data = data[2:]
	if len(data) != siglength {
		return false
	}

	m.signature = data

	return true
}

type newSessionTicketMsg struct {
	raw                         []byte
	vers                        uint16
	isDTLS                      bool
	ticketLifetime              uint32
	ticketAgeAdd                uint32
	ticketNonce                 []byte
	ticket                      []byte
	maxEarlyDataSize            uint32
	customExtension             string
	duplicateEarlyDataExtension bool
	hasGREASEExtension          bool
}

func (m *newSessionTicketMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	version, ok := wireToVersion(m.vers, m.isDTLS)
	if !ok {
		panic("unknown version")
	}

	// See http://tools.ietf.org/html/rfc5077#section-3.3
	ticketMsg := newByteBuilder()
	ticketMsg.addU8(typeNewSessionTicket)
	body := ticketMsg.addU24LengthPrefixed()
	body.addU32(m.ticketLifetime)
	if version >= VersionTLS13 {
		body.addU32(m.ticketAgeAdd)
		body.addU8LengthPrefixed().addBytes(m.ticketNonce)
	}

	ticket := body.addU16LengthPrefixed()
	ticket.addBytes(m.ticket)

	if version >= VersionTLS13 {
		extensions := body.addU16LengthPrefixed()
		if m.maxEarlyDataSize > 0 {
			extensions.addU16(extensionEarlyData)
			extensions.addU16LengthPrefixed().addU32(m.maxEarlyDataSize)
			if m.duplicateEarlyDataExtension {
				extensions.addU16(extensionEarlyData)
				extensions.addU16LengthPrefixed().addU32(m.maxEarlyDataSize)
			}
		}
		if len(m.customExtension) > 0 {
			extensions.addU16(extensionCustom)
			extensions.addU16LengthPrefixed().addBytes([]byte(m.customExtension))
		}
	}

	m.raw = ticketMsg.finish()
	return m.raw
}

func (m *newSessionTicketMsg) unmarshal(data []byte) bool {
	m.raw = data

	version, ok := wireToVersion(m.vers, m.isDTLS)
	if !ok {
		panic("unknown version")
	}

	if len(data) < 8 {
		return false
	}
	m.ticketLifetime = uint32(data[4])<<24 | uint32(data[5])<<16 | uint32(data[6])<<8 | uint32(data[7])
	data = data[8:]

	if version >= VersionTLS13 {
		if len(data) < 4 {
			return false
		}
		m.ticketAgeAdd = uint32(data[0])<<24 | uint32(data[1])<<16 | uint32(data[2])<<8 | uint32(data[3])
		data = data[4:]
		nonceLen := int(data[0])
		data = data[1:]
		if len(data) < nonceLen {
			return false
		}
		m.ticketNonce = data[:nonceLen]
		data = data[nonceLen:]
	}

	if len(data) < 2 {
		return false
	}
	ticketLen := int(data[0])<<8 + int(data[1])
	data = data[2:]
	if len(data) < ticketLen {
		return false
	}

	if version >= VersionTLS13 && ticketLen == 0 {
		return false
	}

	m.ticket = data[:ticketLen]
	data = data[ticketLen:]

	if version >= VersionTLS13 {
		if len(data) < 2 {
			return false
		}

		extensionsLength := int(data[0])<<8 | int(data[1])
		data = data[2:]
		if extensionsLength != len(data) {
			return false
		}

		for len(data) != 0 {
			if len(data) < 4 {
				return false
			}
			extension := uint16(data[0])<<8 | uint16(data[1])
			length := int(data[2])<<8 | int(data[3])
			data = data[4:]
			if len(data) < length {
				return false
			}

			switch extension {
			case extensionEarlyData:
				if length != 4 {
					return false
				}
				m.maxEarlyDataSize = uint32(data[0])<<24 | uint32(data[1])<<16 | uint32(data[2])<<8 | uint32(data[3])
			default:
				if isGREASEValue(extension) {
					m.hasGREASEExtension = true
				}
			}

			data = data[length:]
		}
	}

	if len(data) > 0 {
		return false
	}

	return true
}

type v2ClientHelloMsg struct {
	raw          []byte
	vers         uint16
	cipherSuites []uint16
	sessionId    []byte
	challenge    []byte
}

func (m *v2ClientHelloMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	length := 1 + 2 + 2 + 2 + 2 + len(m.cipherSuites)*3 + len(m.sessionId) + len(m.challenge)

	x := make([]byte, length)
	x[0] = 1
	x[1] = uint8(m.vers >> 8)
	x[2] = uint8(m.vers)
	x[3] = uint8((len(m.cipherSuites) * 3) >> 8)
	x[4] = uint8(len(m.cipherSuites) * 3)
	x[5] = uint8(len(m.sessionId) >> 8)
	x[6] = uint8(len(m.sessionId))
	x[7] = uint8(len(m.challenge) >> 8)
	x[8] = uint8(len(m.challenge))
	y := x[9:]
	for i, spec := range m.cipherSuites {
		y[i*3] = 0
		y[i*3+1] = uint8(spec >> 8)
		y[i*3+2] = uint8(spec)
	}
	y = y[len(m.cipherSuites)*3:]
	copy(y, m.sessionId)
	y = y[len(m.sessionId):]
	copy(y, m.challenge)

	m.raw = x

	return x
}

type helloVerifyRequestMsg struct {
	raw    []byte
	vers   uint16
	cookie []byte
}

func (m *helloVerifyRequestMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	length := 2 + 1 + len(m.cookie)

	x := make([]byte, 4+length)
	x[0] = typeHelloVerifyRequest
	x[1] = uint8(length >> 16)
	x[2] = uint8(length >> 8)
	x[3] = uint8(length)
	vers := m.vers
	x[4] = uint8(vers >> 8)
	x[5] = uint8(vers)
	x[6] = uint8(len(m.cookie))
	copy(x[7:7+len(m.cookie)], m.cookie)

	return x
}

func (m *helloVerifyRequestMsg) unmarshal(data []byte) bool {
	if len(data) < 4+2+1 {
		return false
	}
	m.raw = data
	m.vers = uint16(data[4])<<8 | uint16(data[5])
	cookieLen := int(data[6])
	if cookieLen > 32 || len(data) != 7+cookieLen {
		return false
	}
	m.cookie = data[7 : 7+cookieLen]

	return true
}

type channelIDMsg struct {
	raw       []byte
	channelID []byte
}

func (m *channelIDMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	length := 2 + 2 + len(m.channelID)

	x := make([]byte, 4+length)
	x[0] = typeChannelID
	x[1] = uint8(length >> 16)
	x[2] = uint8(length >> 8)
	x[3] = uint8(length)
	x[4] = uint8(extensionChannelID >> 8)
	x[5] = uint8(extensionChannelID & 0xff)
	x[6] = uint8(len(m.channelID) >> 8)
	x[7] = uint8(len(m.channelID) & 0xff)
	copy(x[8:], m.channelID)

	return x
}

func (m *channelIDMsg) unmarshal(data []byte) bool {
	if len(data) != 4+2+2+128 {
		return false
	}
	m.raw = data
	if (uint16(data[4])<<8)|uint16(data[5]) != extensionChannelID {
		return false
	}
	if int(data[6])<<8|int(data[7]) != 128 {
		return false
	}
	m.channelID = data[4+2+2:]

	return true
}

type helloRequestMsg struct {
}

func (*helloRequestMsg) marshal() []byte {
	return []byte{typeHelloRequest, 0, 0, 0}
}

func (*helloRequestMsg) unmarshal(data []byte) bool {
	return len(data) == 4
}

type keyUpdateMsg struct {
	raw              []byte
	keyUpdateRequest byte
}

func (m *keyUpdateMsg) marshal() []byte {
	if m.raw != nil {
		return m.raw
	}

	return []byte{typeKeyUpdate, 0, 0, 1, m.keyUpdateRequest}
}

func (m *keyUpdateMsg) unmarshal(data []byte) bool {
	m.raw = data

	if len(data) != 5 {
		return false
	}

	length := int(data[1])<<16 | int(data[2])<<8 | int(data[3])
	if len(data)-4 != length {
		return false
	}

	m.keyUpdateRequest = data[4]
	return m.keyUpdateRequest == keyUpdateNotRequested || m.keyUpdateRequest == keyUpdateRequested
}

type endOfEarlyDataMsg struct {
	nonEmpty bool
}

func (m *endOfEarlyDataMsg) marshal() []byte {
	if m.nonEmpty {
		return []byte{typeEndOfEarlyData, 0, 0, 1, 42}
	}
	return []byte{typeEndOfEarlyData, 0, 0, 0}
}

func (*endOfEarlyDataMsg) unmarshal(data []byte) bool {
	return len(data) == 4
}

// ssl3NoCertificateMsg is a dummy message to handle SSL 3.0 using a warning
// alert in the handshake.
type ssl3NoCertificateMsg struct{}

func eqUint16s(x, y []uint16) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if y[i] != v {
			return false
		}
	}
	return true
}

func eqCurveIDs(x, y []CurveID) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if y[i] != v {
			return false
		}
	}
	return true
}

func eqStrings(x, y []string) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if y[i] != v {
			return false
		}
	}
	return true
}

func eqByteSlices(x, y [][]byte) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if !bytes.Equal(v, y[i]) {
			return false
		}
	}
	return true
}

func eqSignatureAlgorithms(x, y []signatureAlgorithm) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		v2 := y[i]
		if v != v2 {
			return false
		}
	}
	return true
}

func eqKeyShareEntryLists(x, y []keyShareEntry) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if y[i].group != v.group || !bytes.Equal(y[i].keyExchange, v.keyExchange) {
			return false
		}
	}
	return true

}

func eqPSKIdentityLists(x, y []pskIdentity) bool {
	if len(x) != len(y) {
		return false
	}
	for i, v := range x {
		if !bytes.Equal(y[i].ticket, v.ticket) || y[i].obfuscatedTicketAge != v.obfuscatedTicketAge {
			return false
		}
	}
	return true

}

// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"container/list"
	"crypto"
	"crypto/ecdsa"
	"crypto/rand"
	"crypto/x509"
	"errors"
	"fmt"
	"io"
	"math/big"
	"strings"
	"sync"
	"time"
)

const (
	VersionSSL30 = 0x0300
	VersionTLS10 = 0x0301
	VersionTLS11 = 0x0302
	VersionTLS12 = 0x0303
	VersionTLS13 = 0x0304
)

const (
	VersionDTLS10 = 0xfeff
	VersionDTLS12 = 0xfefd
)

var allTLSWireVersions = []uint16{
	VersionTLS13,
	VersionTLS12,
	VersionTLS11,
	VersionTLS10,
	VersionSSL30,
}

var allDTLSWireVersions = []uint16{
	VersionDTLS12,
	VersionDTLS10,
}

const (
	maxPlaintext        = 16384        // maximum plaintext payload length
	maxCiphertext       = 16384 + 2048 // maximum ciphertext payload length
	tlsRecordHeaderLen  = 5            // record header length
	dtlsRecordHeaderLen = 13
	maxHandshake        = 65536 // maximum handshake we support (protocol max is 16 MB)

	minVersion = VersionSSL30
	maxVersion = VersionTLS13
)

// TLS record types.
type recordType uint8

const (
	recordTypeChangeCipherSpec   recordType = 20
	recordTypeAlert              recordType = 21
	recordTypeHandshake          recordType = 22
	recordTypeApplicationData    recordType = 23
	recordTypePlaintextHandshake recordType = 24
)

// TLS handshake message types.
const (
	typeHelloRequest          uint8 = 0
	typeClientHello           uint8 = 1
	typeServerHello           uint8 = 2
	typeHelloVerifyRequest    uint8 = 3
	typeNewSessionTicket      uint8 = 4
	typeEndOfEarlyData        uint8 = 5
	typeHelloRetryRequest     uint8 = 6
	typeEncryptedExtensions   uint8 = 8
	typeCertificate           uint8 = 11
	typeServerKeyExchange     uint8 = 12
	typeCertificateRequest    uint8 = 13
	typeServerHelloDone       uint8 = 14
	typeCertificateVerify     uint8 = 15
	typeClientKeyExchange     uint8 = 16
	typeFinished              uint8 = 20
	typeCertificateStatus     uint8 = 22
	typeKeyUpdate             uint8 = 24
	typeCompressedCertificate uint8 = 25  // Not IANA assigned
	typeNextProtocol          uint8 = 67  // Not IANA assigned
	typeChannelID             uint8 = 203 // Not IANA assigned
	typeMessageHash           uint8 = 254
)

// TLS compression types.
const (
	compressionNone uint8 = 0
)

// TLS extension numbers
const (
	extensionServerName                 uint16 = 0
	extensionStatusRequest              uint16 = 5
	extensionSupportedCurves            uint16 = 10
	extensionSupportedPoints            uint16 = 11
	extensionSignatureAlgorithms        uint16 = 13
	extensionUseSRTP                    uint16 = 14
	extensionALPN                       uint16 = 16
	extensionSignedCertificateTimestamp uint16 = 18
	extensionPadding                    uint16 = 21
	extensionExtendedMasterSecret       uint16 = 23
	extensionTokenBinding               uint16 = 24
	extensionCompressedCertAlgs         uint16 = 27
	extensionSessionTicket              uint16 = 35
	extensionPreSharedKey               uint16 = 41
	extensionEarlyData                  uint16 = 42
	extensionSupportedVersions          uint16 = 43
	extensionCookie                     uint16 = 44
	extensionPSKKeyExchangeModes        uint16 = 45
	extensionCertificateAuthorities     uint16 = 47
	extensionSignatureAlgorithmsCert    uint16 = 50
	extensionKeyShare                   uint16 = 51
	extensionCustom                     uint16 = 1234  // not IANA assigned
	extensionNextProtoNeg               uint16 = 13172 // not IANA assigned
	extensionRenegotiationInfo          uint16 = 0xff01
	extensionQUICTransportParams        uint16 = 0xffa5 // draft-ietf-quic-tls-13
	extensionChannelID                  uint16 = 30032  // not IANA assigned
	extensionDelegatedCredentials       uint16 = 0xff02 // not IANA assigned
	extensionPQExperimentSignal         uint16 = 54538
)

// TLS signaling cipher suite values
const (
	scsvRenegotiation uint16 = 0x00ff
)

var tls13HelloRetryRequest = []uint8{
	0xcf, 0x21, 0xad, 0x74, 0xe5, 0x9a, 0x61, 0x11, 0xbe, 0x1d, 0x8c,
	0x02, 0x1e, 0x65, 0xb8, 0x91, 0xc2, 0xa2, 0x11, 0x16, 0x7a, 0xbb,
	0x8c, 0x5e, 0x07, 0x9e, 0x09, 0xe2, 0xc8, 0xa8, 0x33, 0x9c,
}

// CurveID is the type of a TLS identifier for an elliptic curve. See
// http://www.iana.org/assignments/tls-parameters/tls-parameters.xml#tls-parameters-8
type CurveID uint16

const (
	CurveP224    CurveID = 21
	CurveP256    CurveID = 23
	CurveP384    CurveID = 24
	CurveP521    CurveID = 25
	CurveX25519  CurveID = 29
	CurveCECPQ2  CurveID = 16696
	CurveCECPQ2b CurveID = 65074
)

// TLS Elliptic Curve Point Formats
// http://www.iana.org/assignments/tls-parameters/tls-parameters.xml#tls-parameters-9
const (
	pointFormatUncompressed    uint8 = 0
	pointFormatCompressedPrime uint8 = 1
)

// TLS CertificateStatusType (RFC 3546)
const (
	statusTypeOCSP uint8 = 1
)

// Certificate types (for certificateRequestMsg)
const (
	CertTypeRSASign    = 1 // A certificate containing an RSA key
	CertTypeDSSSign    = 2 // A certificate containing a DSA key
	CertTypeRSAFixedDH = 3 // A certificate containing a static DH key
	CertTypeDSSFixedDH = 4 // A certificate containing a static DH key

	// See RFC4492 sections 3 and 5.5.
	CertTypeECDSASign      = 64 // A certificate containing an ECDSA-capable public key, signed with ECDSA.
	CertTypeRSAFixedECDH   = 65 // A certificate containing an ECDH-capable public key, signed with RSA.
	CertTypeECDSAFixedECDH = 66 // A certificate containing an ECDH-capable public key, signed with ECDSA.

	// Rest of these are reserved by the TLS spec
)

// signatureAlgorithm corresponds to a SignatureScheme value from TLS 1.3. Note
// that TLS 1.3 names the production 'SignatureScheme' to avoid colliding with
// TLS 1.2's SignatureAlgorithm but otherwise refers to them as 'signature
// algorithms' throughout. We match the latter.
type signatureAlgorithm uint16

const (
	// RSASSA-PKCS1-v1_5 algorithms
	signatureRSAPKCS1WithMD5    signatureAlgorithm = 0x0101
	signatureRSAPKCS1WithSHA1   signatureAlgorithm = 0x0201
	signatureRSAPKCS1WithSHA256 signatureAlgorithm = 0x0401
	signatureRSAPKCS1WithSHA384 signatureAlgorithm = 0x0501
	signatureRSAPKCS1WithSHA512 signatureAlgorithm = 0x0601

	// ECDSA algorithms
	signatureECDSAWithSHA1          signatureAlgorithm = 0x0203
	signatureECDSAWithP256AndSHA256 signatureAlgorithm = 0x0403
	signatureECDSAWithP384AndSHA384 signatureAlgorithm = 0x0503
	signatureECDSAWithP521AndSHA512 signatureAlgorithm = 0x0603

	// RSASSA-PSS algorithms
	signatureRSAPSSWithSHA256 signatureAlgorithm = 0x0804
	signatureRSAPSSWithSHA384 signatureAlgorithm = 0x0805
	signatureRSAPSSWithSHA512 signatureAlgorithm = 0x0806

	// EdDSA algorithms
	signatureEd25519 signatureAlgorithm = 0x0807
	signatureEd448   signatureAlgorithm = 0x0808
)

// supportedSignatureAlgorithms contains the default supported signature
// algorithms.
var supportedSignatureAlgorithms = []signatureAlgorithm{
	signatureRSAPSSWithSHA256,
	signatureRSAPKCS1WithSHA256,
	signatureECDSAWithP256AndSHA256,
	signatureRSAPKCS1WithSHA1,
	signatureECDSAWithSHA1,
	signatureEd25519,
}

// SRTP protection profiles (See RFC 5764, section 4.1.2)
const (
	SRTP_AES128_CM_HMAC_SHA1_80 uint16 = 0x0001
	SRTP_AES128_CM_HMAC_SHA1_32        = 0x0002
)

// PskKeyExchangeMode values (see RFC 8446, section 4.2.9)
const (
	pskKEMode    = 0
	pskDHEKEMode = 1
)

// KeyUpdateRequest values (see RFC 8446, section 4.6.3)
const (
	keyUpdateNotRequested = 0
	keyUpdateRequested    = 1
)

// ConnectionState records basic TLS details about the connection.
type ConnectionState struct {
	Version                    uint16                // TLS version used by the connection (e.g. VersionTLS12)
	HandshakeComplete          bool                  // TLS handshake is complete
	DidResume                  bool                  // connection resumes a previous TLS connection
	CipherSuite                uint16                // cipher suite in use (TLS_RSA_WITH_RC4_128_SHA, ...)
	NegotiatedProtocol         string                // negotiated next protocol (from Config.NextProtos)
	NegotiatedProtocolIsMutual bool                  // negotiated protocol was advertised by server
	NegotiatedProtocolFromALPN bool                  // protocol negotiated with ALPN
	ServerName                 string                // server name requested by client, if any (server side only)
	PeerCertificates           []*x509.Certificate   // certificate chain presented by remote peer
	VerifiedChains             [][]*x509.Certificate // verified chains built from PeerCertificates
	ChannelID                  *ecdsa.PublicKey      // the channel ID for this connection
	TokenBindingNegotiated     bool                  // whether Token Binding was negotiated
	TokenBindingParam          uint8                 // the negotiated Token Binding key parameter
	SRTPProtectionProfile      uint16                // the negotiated DTLS-SRTP protection profile
	TLSUnique                  []byte                // the tls-unique channel binding
	SCTList                    []byte                // signed certificate timestamp list
	PeerSignatureAlgorithm     signatureAlgorithm    // algorithm used by the peer in the handshake
	CurveID                    CurveID               // the curve used in ECDHE
	QUICTransportParams        []byte                // the QUIC transport params received from the peer
}

// ClientAuthType declares the policy the server will follow for
// TLS Client Authentication.
type ClientAuthType int

const (
	NoClientCert ClientAuthType = iota
	RequestClientCert
	RequireAnyClientCert
	VerifyClientCertIfGiven
	RequireAndVerifyClientCert
)

// ClientSessionState contains the state needed by clients to resume TLS
// sessions.
type ClientSessionState struct {
	sessionId            []uint8             // Session ID supplied by the server. nil if the session has a ticket.
	sessionTicket        []uint8             // Encrypted ticket used for session resumption with server
	vers                 uint16              // SSL/TLS version negotiated for the session
	wireVersion          uint16              // Wire SSL/TLS version negotiated for the session
	cipherSuite          uint16              // Ciphersuite negotiated for the session
	masterSecret         []byte              // MasterSecret generated by client on a full handshake
	handshakeHash        []byte              // Handshake hash for Channel ID purposes.
	serverCertificates   []*x509.Certificate // Certificate chain presented by the server
	extendedMasterSecret bool                // Whether an extended master secret was used to generate the session
	sctList              []byte
	ocspResponse         []byte
	earlyALPN            string
	ticketCreationTime   time.Time
	ticketExpiration     time.Time
	ticketAgeAdd         uint32
	maxEarlyDataSize     uint32
}

// ClientSessionCache is a cache of ClientSessionState objects that can be used
// by a client to resume a TLS session with a given server. ClientSessionCache
// implementations should expect to be called concurrently from different
// goroutines.
type ClientSessionCache interface {
	// Get searches for a ClientSessionState associated with the given key.
	// On return, ok is true if one was found.
	Get(sessionKey string) (session *ClientSessionState, ok bool)

	// Put adds the ClientSessionState to the cache with the given key.
	Put(sessionKey string, cs *ClientSessionState)
}

// ServerSessionCache is a cache of sessionState objects that can be used by a
// client to resume a TLS session with a given server. ServerSessionCache
// implementations should expect to be called concurrently from different
// goroutines.
type ServerSessionCache interface {
	// Get searches for a sessionState associated with the given session
	// ID. On return, ok is true if one was found.
	Get(sessionId string) (session *sessionState, ok bool)

	// Put adds the sessionState to the cache with the given session ID.
	Put(sessionId string, session *sessionState)
}

// CertCompressionAlg is a certificate compression algorithm, specified as a
// pair of functions for compressing and decompressing certificates.
type CertCompressionAlg struct {
	// Compress returns a compressed representation of the input.
	Compress func([]byte) []byte
	// Decompress depresses the contents of in and writes the result to out, which
	// will be the correct size. It returns true on success and false otherwise.
	Decompress func(out, in []byte) bool
}

// A Config structure is used to configure a TLS client or server.
// After one has been passed to a TLS function it must not be
// modified. A Config may be reused; the tls package will also not
// modify it.
type Config struct {
	// Rand provides the source of entropy for nonces and RSA blinding.
	// If Rand is nil, TLS uses the cryptographic random reader in package
	// crypto/rand.
	// The Reader must be safe for use by multiple goroutines.
	Rand io.Reader

	// Time returns the current time as the number of seconds since the epoch.
	// If Time is nil, TLS uses time.Now.
	Time func() time.Time

	// Certificates contains one or more certificate chains
	// to present to the other side of the connection.
	// Server configurations must include at least one certificate.
	Certificates []Certificate

	// NameToCertificate maps from a certificate name to an element of
	// Certificates. Note that a certificate name can be of the form
	// '*.example.com' and so doesn't have to be a domain name as such.
	// See Config.BuildNameToCertificate
	// The nil value causes the first element of Certificates to be used
	// for all connections.
	NameToCertificate map[string]*Certificate

	// RootCAs defines the set of root certificate authorities
	// that clients use when verifying server certificates.
	// If RootCAs is nil, TLS uses the host's root CA set.
	RootCAs *x509.CertPool

	// NextProtos is a list of supported, application level protocols.
	NextProtos []string

	// ServerName is used to verify the hostname on the returned
	// certificates unless InsecureSkipVerify is given. It is also included
	// in the client's handshake to support virtual hosting.
	ServerName string

	// ClientAuth determines the server's policy for
	// TLS Client Authentication. The default is NoClientCert.
	ClientAuth ClientAuthType

	// ClientCAs defines the set of root certificate authorities
	// that servers use if required to verify a client certificate
	// by the policy in ClientAuth.
	ClientCAs *x509.CertPool

	// ClientCertificateTypes defines the set of allowed client certificate
	// types. The default is CertTypeRSASign and CertTypeECDSASign.
	ClientCertificateTypes []byte

	// InsecureSkipVerify controls whether a client verifies the
	// server's certificate chain and host name.
	// If InsecureSkipVerify is true, TLS accepts any certificate
	// presented by the server and any host name in that certificate.
	// In this mode, TLS is susceptible to man-in-the-middle attacks.
	// This should be used only for testing.
	InsecureSkipVerify bool

	// CipherSuites is a list of supported cipher suites. If CipherSuites
	// is nil, TLS uses a list of suites supported by the implementation.
	CipherSuites []uint16

	// PreferServerCipherSuites controls whether the server selects the
	// client's most preferred ciphersuite, or the server's most preferred
	// ciphersuite. If true then the server's preference, as expressed in
	// the order of elements in CipherSuites, is used.
	PreferServerCipherSuites bool

	// SessionTicketsDisabled may be set to true to disable session ticket
	// (resumption) support.
	SessionTicketsDisabled bool

	// SessionTicketKey is used by TLS servers to provide session
	// resumption. See RFC 5077. If zero, it will be filled with
	// random data before the first server handshake.
	//
	// If multiple servers are terminating connections for the same host
	// they should all have the same SessionTicketKey. If the
	// SessionTicketKey leaks, previously recorded and future TLS
	// connections using that key are compromised.
	SessionTicketKey [32]byte

	// ClientSessionCache is a cache of ClientSessionState entries
	// for TLS session resumption.
	ClientSessionCache ClientSessionCache

	// ServerSessionCache is a cache of sessionState entries for TLS session
	// resumption.
	ServerSessionCache ServerSessionCache

	// MinVersion contains the minimum SSL/TLS version that is acceptable.
	// If zero, then SSLv3 is taken as the minimum.
	MinVersion uint16

	// MaxVersion contains the maximum SSL/TLS version that is acceptable.
	// If zero, then the maximum version supported by this package is used,
	// which is currently TLS 1.2.
	MaxVersion uint16

	// CurvePreferences contains the elliptic curves that will be used in
	// an ECDHE handshake, in preference order. If empty, the default will
	// be used.
	CurvePreferences []CurveID

	// DefaultCurves contains the elliptic curves for which public values will
	// be sent in the ClientHello's KeyShare extension. If this value is nil,
	// all supported curves will have public values sent. This field is ignored
	// on servers.
	DefaultCurves []CurveID

	// ChannelID contains the ECDSA key for the client to use as
	// its TLS Channel ID.
	ChannelID *ecdsa.PrivateKey

	// RequestChannelID controls whether the server requests a TLS
	// Channel ID. If negotiated, the client's public key is
	// returned in the ConnectionState.
	RequestChannelID bool

	// TokenBindingParams contains a list of TokenBindingKeyParameters
	// (draft-ietf-tokbind-protocol-16) to attempt to negotiate. If
	// nil, Token Binding will not be negotiated.
	TokenBindingParams []byte

	// TokenBindingVersion contains the serialized ProtocolVersion to
	// use when negotiating Token Binding.
	TokenBindingVersion uint16

	// ExpectTokenBindingParams is checked by a server that the client
	// sent ExpectTokenBindingParams as its list of Token Binding
	// paramters.
	ExpectTokenBindingParams []byte

	// PreSharedKey, if not nil, is the pre-shared key to use with
	// the PSK cipher suites.
	PreSharedKey []byte

	// PreSharedKeyIdentity, if not empty, is the identity to use
	// with the PSK cipher suites.
	PreSharedKeyIdentity string

	// MaxEarlyDataSize controls the maximum number of bytes that the
	// server will accept in early data and advertise in a
	// NewSessionTicketMsg. If 0, no early data will be accepted and
	// the early_data extension in the NewSessionTicketMsg will be omitted.
	MaxEarlyDataSize uint32

	// SRTPProtectionProfiles, if not nil, is the list of SRTP
	// protection profiles to offer in DTLS-SRTP.
	SRTPProtectionProfiles []uint16

	// SignSignatureAlgorithms, if not nil, overrides the default set of
	// supported signature algorithms to sign with.
	SignSignatureAlgorithms []signatureAlgorithm

	// VerifySignatureAlgorithms, if not nil, overrides the default set of
	// supported signature algorithms that are accepted.
	VerifySignatureAlgorithms []signatureAlgorithm

	// QUICTransportParams, if not empty, will be sent in the QUIC
	// transport parameters extension.
	QUICTransportParams []byte

	CertCompressionAlgs map[uint16]CertCompressionAlg

	// PQExperimentSignal instructs a client to send a non-IANA defined extension
	// that signals participation in an experiment of post-quantum key exchange
	// methods.
	PQExperimentSignal bool

	// Bugs specifies optional misbehaviour to be used for testing other
	// implementations.
	Bugs ProtocolBugs

	serverInitOnce sync.Once // guards calling (*Config).serverInit
}

type BadValue int

const (
	BadValueNone BadValue = iota
	BadValueNegative
	BadValueZero
	BadValueLimit
	BadValueLarge
	NumBadValues
)

type RSABadValue int

const (
	RSABadValueNone RSABadValue = iota
	RSABadValueCorrupt
	RSABadValueTooLong
	RSABadValueTooShort
	RSABadValueWrongVersion1
	RSABadValueWrongVersion2
	RSABadValueWrongBlockType
	RSABadValueWrongLeadingByte
	RSABadValueNoZero
	NumRSABadValues
)

type RSAPSSSupport int

const (
	RSAPSSSupportAny RSAPSSSupport = iota
	RSAPSSSupportNone
	RSAPSSSupportOnlineSignatureOnly
	RSAPSSSupportBoth
)

type ProtocolBugs struct {
	// InvalidSignature specifies that the signature in a ServerKeyExchange
	// or CertificateVerify message should be invalid.
	InvalidSignature bool

	// SendCurve, if non-zero, causes the server to send the specified curve
	// ID in ServerKeyExchange (TLS 1.2) or ServerHello (TLS 1.3) rather
	// than the negotiated one.
	SendCurve CurveID

	// InvalidECDHPoint, if true, causes the ECC points in
	// ServerKeyExchange or ClientKeyExchange messages to be invalid.
	InvalidECDHPoint bool

	// BadECDSAR controls ways in which the 'r' value of an ECDSA signature
	// can be invalid.
	BadECDSAR BadValue
	BadECDSAS BadValue

	// MaxPadding causes CBC records to have the maximum possible padding.
	MaxPadding bool
	// PaddingFirstByteBad causes the first byte of the padding to be
	// incorrect.
	PaddingFirstByteBad bool
	// PaddingFirstByteBadIf255 causes the first byte of padding to be
	// incorrect if there's a maximum amount of padding (i.e. 255 bytes).
	PaddingFirstByteBadIf255 bool

	// FailIfNotFallbackSCSV causes a server handshake to fail if the
	// client doesn't send the fallback SCSV value.
	FailIfNotFallbackSCSV bool

	// DuplicateExtension causes an extra empty extension of bogus type to
	// be emitted in either the ClientHello or the ServerHello.
	DuplicateExtension bool

	// UnauthenticatedECDH causes the server to pretend ECDHE_RSA
	// and ECDHE_ECDSA cipher suites are actually ECDH_anon. No
	// Certificate message is sent and no signature is added to
	// ServerKeyExchange.
	UnauthenticatedECDH bool

	// SkipHelloVerifyRequest causes a DTLS server to skip the
	// HelloVerifyRequest message.
	SkipHelloVerifyRequest bool

	// SkipCertificateStatus, if true, causes the server to skip the
	// CertificateStatus message. This is legal because CertificateStatus is
	// optional, even with a status_request in ServerHello.
	SkipCertificateStatus bool

	// SkipServerKeyExchange causes the server to skip sending
	// ServerKeyExchange messages.
	SkipServerKeyExchange bool

	// SkipNewSessionTicket causes the server to skip sending the
	// NewSessionTicket message despite promising to in ServerHello.
	SkipNewSessionTicket bool

	// UseFirstSessionTicket causes the client to cache only the first session
	// ticket received.
	UseFirstSessionTicket bool

	// SkipClientCertificate causes the client to skip the Certificate
	// message.
	SkipClientCertificate bool

	// SkipChangeCipherSpec causes the implementation to skip
	// sending the ChangeCipherSpec message (and adjusting cipher
	// state accordingly for the Finished message).
	SkipChangeCipherSpec bool

	// SkipFinished causes the implementation to skip sending the Finished
	// message.
	SkipFinished bool

	// SkipEndOfEarlyData causes the implementation to skip
	// end_of_early_data.
	SkipEndOfEarlyData bool

	// NonEmptyEndOfEarlyData causes the implementation to end an extra byte in the
	// EndOfEarlyData.
	NonEmptyEndOfEarlyData bool

	// SkipCertificateVerify, if true causes peer to skip sending a
	// CertificateVerify message after the Certificate message.
	SkipCertificateVerify bool

	// EarlyChangeCipherSpec causes the client to send an early
	// ChangeCipherSpec message before the ClientKeyExchange. A value of
	// zero disables this behavior. One and two configure variants for
	// 1.0.1 and 0.9.8 modes, respectively.
	EarlyChangeCipherSpec int

	// StrayChangeCipherSpec causes every pre-ChangeCipherSpec handshake
	// message in DTLS to be prefaced by stray ChangeCipherSpec record. This
	// may be used to test DTLS's handling of reordered ChangeCipherSpec.
	StrayChangeCipherSpec bool

	// ReorderChangeCipherSpec causes the ChangeCipherSpec message to be
	// sent at start of each flight in DTLS. Unlike EarlyChangeCipherSpec,
	// the cipher change happens at the usual time.
	ReorderChangeCipherSpec bool

	// FragmentAcrossChangeCipherSpec causes the implementation to fragment
	// the Finished (or NextProto) message around the ChangeCipherSpec
	// messages.
	FragmentAcrossChangeCipherSpec bool

	// SendExtraChangeCipherSpec causes the implementation to send extra
	// ChangeCipherSpec messages.
	SendExtraChangeCipherSpec int

	// SendPostHandshakeChangeCipherSpec causes the implementation to send
	// a ChangeCipherSpec record before every application data record.
	SendPostHandshakeChangeCipherSpec bool

	// SendUnencryptedFinished, if true, causes the Finished message to be
	// send unencrypted before ChangeCipherSpec rather than after it.
	SendUnencryptedFinished bool

	// PartialEncryptedExtensionsWithServerHello, if true, causes the TLS
	// 1.3 server to send part of EncryptedExtensions unencrypted
	// in the same record as ServerHello.
	PartialEncryptedExtensionsWithServerHello bool

	// PartialClientFinishedWithClientHello, if true, causes the TLS 1.3
	// client to send part of Finished unencrypted in the same record as
	// ClientHello.
	PartialClientFinishedWithClientHello bool

	// SendV2ClientHello causes the client to send a V2ClientHello
	// instead of a normal ClientHello.
	SendV2ClientHello bool

	// SendFallbackSCSV causes the client to include
	// TLS_FALLBACK_SCSV in the ClientHello.
	SendFallbackSCSV bool

	// SendRenegotiationSCSV causes the client to include the renegotiation
	// SCSV in the ClientHello.
	SendRenegotiationSCSV bool

	// MaxHandshakeRecordLength, if non-zero, is the maximum size of a
	// handshake record. Handshake messages will be split into multiple
	// records at the specified size, except that the client_version will
	// never be fragmented. For DTLS, it is the maximum handshake fragment
	// size, not record size; DTLS allows multiple handshake fragments in a
	// single handshake record. See |PackHandshakeFragments|.
	MaxHandshakeRecordLength int

	// FragmentClientVersion will allow MaxHandshakeRecordLength to apply to
	// the first 6 bytes of the ClientHello.
	FragmentClientVersion bool

	// FragmentAlert will cause all alerts to be fragmented across
	// two records.
	FragmentAlert bool

	// DoubleAlert will cause all alerts to be sent as two copies packed
	// within one record.
	DoubleAlert bool

	// SendSpuriousAlert, if non-zero, will cause an spurious, unwanted
	// alert to be sent.
	SendSpuriousAlert alert

	// BadRSAClientKeyExchange causes the client to send a corrupted RSA
	// ClientKeyExchange which would not pass padding checks.
	BadRSAClientKeyExchange RSABadValue

	// RenewTicketOnResume causes the server to renew the session ticket and
	// send a NewSessionTicket message during an abbreviated handshake.
	RenewTicketOnResume bool

	// SendClientVersion, if non-zero, causes the client to send the
	// specified value in the ClientHello version field.
	SendClientVersion uint16

	// OmitSupportedVersions, if true, causes the client to omit the
	// supported versions extension.
	OmitSupportedVersions bool

	// SendSupportedVersions, if non-empty, causes the client to send a
	// supported versions extension with the values from array.
	SendSupportedVersions []uint16

	// NegotiateVersion, if non-zero, causes the server to negotiate the
	// specifed wire version rather than the version supported by either
	// peer.
	NegotiateVersion uint16

	// NegotiateVersionOnRenego, if non-zero, causes the server to negotiate
	// the specified wire version on renegotiation rather than retaining it.
	NegotiateVersionOnRenego uint16

	// ExpectFalseStart causes the server to, on full handshakes,
	// expect the peer to False Start; the server Finished message
	// isn't sent until we receive an application data record
	// from the peer.
	ExpectFalseStart bool

	// AlertBeforeFalseStartTest, if non-zero, causes the server to, on full
	// handshakes, send an alert just before reading the application data
	// record to test False Start. This can be used in a negative False
	// Start test to determine whether the peer processed the alert (and
	// closed the connection) before or after sending app data.
	AlertBeforeFalseStartTest alert

	// ExpectServerName, if not empty, is the hostname the client
	// must specify in the server_name extension.
	ExpectServerName string

	// SwapNPNAndALPN switches the relative order between NPN and ALPN in
	// both ClientHello and ServerHello.
	SwapNPNAndALPN bool

	// ALPNProtocol, if not nil, sets the ALPN protocol that a server will
	// return.
	ALPNProtocol *string

	// AcceptAnySession causes the server to resume sessions regardless of
	// the version associated with the session or cipher suite. It also
	// causes the server to look in both TLS 1.2 and 1.3 extensions to
	// process a ticket.
	AcceptAnySession bool

	// SendBothTickets, if true, causes the client to send tickets in both
	// TLS 1.2 and 1.3 extensions.
	SendBothTickets bool

	// FilterTicket, if not nil, causes the client to modify a session
	// ticket before sending it in a resume handshake.
	FilterTicket func([]byte) ([]byte, error)

	// TicketSessionIDLength, if non-zero, is the length of the session ID
	// to send with a ticket resumption offer.
	TicketSessionIDLength int

	// EmptyTicketSessionID, if true, causes the client to send an empty
	// session ID with a ticket resumption offer. For simplicity, this will
	// also cause the client to interpret a ServerHello with empty session
	// ID as a resumption. (A client which sends empty session ID is
	// normally expected to look ahead for ChangeCipherSpec.)
	EmptyTicketSessionID bool

	// SendClientHelloSessionID, if not nil, is the session ID sent in the
	// ClientHello.
	SendClientHelloSessionID []byte

	// ExpectClientHelloSessionID, if true, causes the server to fail the
	// connection if there is not a session ID in the ClientHello.
	ExpectClientHelloSessionID bool

	// EchoSessionIDInFullHandshake, if true, causes the server to echo the
	// ClientHello session ID, even in TLS 1.2 full handshakes.
	EchoSessionIDInFullHandshake bool

	// ExpectNoTLS12Session, if true, causes the server to fail the
	// connection if either a session ID or TLS 1.2 ticket is offered.
	ExpectNoTLS12Session bool

	// ExpectNoTLS13PSK, if true, causes the server to fail the connection
	// if a TLS 1.3 PSK is offered.
	ExpectNoTLS13PSK bool

	// ExpectNoTLS13PSKAfterHRR, if true, causes the server to fail the connection
	// if a TLS 1.3 PSK is offered after HRR.
	ExpectNoTLS13PSKAfterHRR bool

	// RequireExtendedMasterSecret, if true, requires that the peer support
	// the extended master secret option.
	RequireExtendedMasterSecret bool

	// NoExtendedMasterSecret causes the client and server to behave as if
	// they didn't support an extended master secret in the initial
	// handshake.
	NoExtendedMasterSecret bool

	// NoExtendedMasterSecretOnRenegotiation causes the client and server to
	// behave as if they didn't support an extended master secret in
	// renegotiation handshakes.
	NoExtendedMasterSecretOnRenegotiation bool

	// EmptyRenegotiationInfo causes the renegotiation extension to be
	// empty in a renegotiation handshake.
	EmptyRenegotiationInfo bool

	// BadRenegotiationInfo causes the renegotiation extension value in a
	// renegotiation handshake to be incorrect at the start.
	BadRenegotiationInfo bool

	// BadRenegotiationInfoEnd causes the renegotiation extension value in
	// a renegotiation handshake to be incorrect at the end.
	BadRenegotiationInfoEnd bool

	// NoRenegotiationInfo disables renegotiation info support in all
	// handshakes.
	NoRenegotiationInfo bool

	// NoRenegotiationInfoInInitial disables renegotiation info support in
	// the initial handshake.
	NoRenegotiationInfoInInitial bool

	// NoRenegotiationInfoAfterInitial disables renegotiation info support
	// in renegotiation handshakes.
	NoRenegotiationInfoAfterInitial bool

	// RequireRenegotiationInfo, if true, causes the client to return an
	// error if the server doesn't reply with the renegotiation extension.
	RequireRenegotiationInfo bool

	// SequenceNumberMapping, if non-nil, is the mapping function to apply
	// to the sequence number of outgoing packets. For both TLS and DTLS,
	// the two most-significant bytes in the resulting sequence number are
	// ignored so that the DTLS epoch cannot be changed.
	SequenceNumberMapping func(uint64) uint64

	// RSAEphemeralKey, if true, causes the server to send a
	// ServerKeyExchange message containing an ephemeral key (as in
	// RSA_EXPORT) in the plain RSA key exchange.
	RSAEphemeralKey bool

	// SRTPMasterKeyIdentifer, if not empty, is the SRTP MKI value that the
	// client offers when negotiating SRTP. MKI support is still missing so
	// the peer must still send none.
	SRTPMasterKeyIdentifer string

	// SendSRTPProtectionProfile, if non-zero, is the SRTP profile that the
	// server sends in the ServerHello instead of the negotiated one.
	SendSRTPProtectionProfile uint16

	// NoSignatureAlgorithms, if true, causes the client to omit the
	// signature and hashes extension.
	//
	// For a server, it will cause an empty list to be sent in the
	// CertificateRequest message. None the less, the configured set will
	// still be enforced.
	NoSignatureAlgorithms bool

	// NoSupportedCurves, if true, causes the client to omit the
	// supported_curves extension.
	NoSupportedCurves bool

	// RequireSameRenegoClientVersion, if true, causes the server
	// to require that all ClientHellos match in offered version
	// across a renego.
	RequireSameRenegoClientVersion bool

	// ExpectInitialRecordVersion, if non-zero, is the expected value of
	// record-layer version field before the protocol version is determined.
	ExpectInitialRecordVersion uint16

	// SendRecordVersion, if non-zero, is the value to send as the
	// record-layer version.
	SendRecordVersion uint16

	// SendInitialRecordVersion, if non-zero, is the value to send as the
	// record-layer version before the protocol version is determined.
	SendInitialRecordVersion uint16

	// MaxPacketLength, if non-zero, is the maximum acceptable size for a
	// packet.
	MaxPacketLength int

	// SendCipherSuite, if non-zero, is the cipher suite value that the
	// server will send in the ServerHello. This does not affect the cipher
	// the server believes it has actually negotiated.
	SendCipherSuite uint16

	// SendCipherSuites, if not nil, is the cipher suite list that the
	// client will send in the ClientHello. This does not affect the cipher
	// the client believes it has actually offered.
	SendCipherSuites []uint16

	// AppDataBeforeHandshake, if not nil, causes application data to be
	// sent immediately before the first handshake message.
	AppDataBeforeHandshake []byte

	// AppDataAfterChangeCipherSpec, if not nil, causes application data to
	// be sent immediately after ChangeCipherSpec.
	AppDataAfterChangeCipherSpec []byte

	// AlertAfterChangeCipherSpec, if non-zero, causes an alert to be sent
	// immediately after ChangeCipherSpec.
	AlertAfterChangeCipherSpec alert

	// TimeoutSchedule is the schedule of packet drops and simulated
	// timeouts for before each handshake leg from the peer.
	TimeoutSchedule []time.Duration

	// PacketAdaptor is the packetAdaptor to use to simulate timeouts.
	PacketAdaptor *packetAdaptor

	// ReorderHandshakeFragments, if true, causes handshake fragments in
	// DTLS to overlap and be sent in the wrong order. It also causes
	// pre-CCS flights to be sent twice. (Post-CCS flights consist of
	// Finished and will trigger a spurious retransmit.)
	ReorderHandshakeFragments bool

	// ReverseHandshakeFragments, if true, causes handshake fragments in
	// DTLS to be reversed within a flight.
	ReverseHandshakeFragments bool

	// MixCompleteMessageWithFragments, if true, causes handshake
	// messages in DTLS to redundantly both fragment the message
	// and include a copy of the full one.
	MixCompleteMessageWithFragments bool

	// RetransmitFinished, if true, causes the DTLS Finished message to be
	// sent twice.
	RetransmitFinished bool

	// SendInvalidRecordType, if true, causes a record with an invalid
	// content type to be sent immediately following the handshake.
	SendInvalidRecordType bool

	// SendWrongMessageType, if non-zero, causes messages of the specified
	// type to be sent with the wrong value.
	SendWrongMessageType byte

	// SendTrailingMessageData, if non-zero, causes messages of the
	// specified type to be sent with trailing data.
	SendTrailingMessageData byte

	// FragmentMessageTypeMismatch, if true, causes all non-initial
	// handshake fragments in DTLS to have the wrong message type.
	FragmentMessageTypeMismatch bool

	// FragmentMessageLengthMismatch, if true, causes all non-initial
	// handshake fragments in DTLS to have the wrong message length.
	FragmentMessageLengthMismatch bool

	// SplitFragments, if non-zero, causes the handshake fragments in DTLS
	// to be split across two records. The value of |SplitFragments| is the
	// number of bytes in the first fragment.
	SplitFragments int

	// SendEmptyFragments, if true, causes handshakes to include empty
	// fragments in DTLS.
	SendEmptyFragments bool

	// SendSplitAlert, if true, causes an alert to be sent with the header
	// and record body split across multiple packets. The peer should
	// discard these packets rather than process it.
	SendSplitAlert bool

	// FailIfResumeOnRenego, if true, causes renegotiations to fail if the
	// client offers a resumption or the server accepts one.
	FailIfResumeOnRenego bool

	// IgnorePeerCipherPreferences, if true, causes the peer's cipher
	// preferences to be ignored.
	IgnorePeerCipherPreferences bool

	// IgnorePeerSignatureAlgorithmPreferences, if true, causes the peer's
	// signature algorithm preferences to be ignored.
	IgnorePeerSignatureAlgorithmPreferences bool

	// IgnorePeerCurvePreferences, if true, causes the peer's curve
	// preferences to be ignored.
	IgnorePeerCurvePreferences bool

	// BadFinished, if true, causes the Finished hash to be broken.
	BadFinished bool

	// PackHandshakeFragments, if true, causes handshake fragments in DTLS
	// to be packed into individual handshake records, up to the specified
	// record size.
	PackHandshakeFragments int

	// PackHandshakeRecords, if non-zero, causes handshake and
	// ChangeCipherSpec records in DTLS to be packed into individual
	// packets, up to the specified packet size.
	PackHandshakeRecords int

	// PackAppDataWithHandshake, if true, extends PackHandshakeRecords to
	// additionally include the first application data record sent after the
	// final Finished message in a handshake. (If the final Finished message
	// is sent by the peer, this option has no effect.) This requires that
	// the runner rather than shim speak first in a given test.
	PackAppDataWithHandshake bool

	// SplitAndPackAppData, if true, causes application data in DTLS to be
	// split into two records each and packed into one packet.
	SplitAndPackAppData bool

	// PackHandshakeFlight, if true, causes each handshake flight in TLS to
	// be packed into records, up to the largest size record available.
	PackHandshakeFlight bool

	// AdvertiseAllConfiguredCiphers, if true, causes the client to
	// advertise all configured cipher suite values.
	AdvertiseAllConfiguredCiphers bool

	// EmptyCertificateList, if true, causes the server to send an empty
	// certificate list in the Certificate message.
	EmptyCertificateList bool

	// ExpectNewTicket, if true, causes the client to abort if it does not
	// receive a new ticket.
	ExpectNewTicket bool

	// RequireClientHelloSize, if not zero, is the required length in bytes
	// of the ClientHello /record/. This is checked by the server.
	RequireClientHelloSize int

	// CustomExtension, if not empty, contains the contents of an extension
	// that will be added to client/server hellos.
	CustomExtension string

	// CustomUnencryptedExtension, if not empty, contains the contents of
	// an extension that will be added to ServerHello in TLS 1.3.
	CustomUnencryptedExtension string

	// ExpectedCustomExtension, if not nil, contains the expected contents
	// of a custom extension.
	ExpectedCustomExtension *string

	// CustomTicketExtension, if not empty, contains the contents of an
	// extension what will be added to NewSessionTicket in TLS 1.3.
	CustomTicketExtension string

	// CustomTicketExtension, if not empty, contains the contents of an
	// extension what will be added to HelloRetryRequest in TLS 1.3.
	CustomHelloRetryRequestExtension string

	// NoCloseNotify, if true, causes the close_notify alert to be skipped
	// on connection shutdown.
	NoCloseNotify bool

	// SendAlertOnShutdown, if non-zero, is the alert to send instead of
	// close_notify on shutdown.
	SendAlertOnShutdown alert

	// ExpectCloseNotify, if true, requires a close_notify from the peer on
	// shutdown. Records from the peer received after close_notify is sent
	// are not discard.
	ExpectCloseNotify bool

	// SendLargeRecords, if true, allows outgoing records to be sent
	// arbitrarily large.
	SendLargeRecords bool

	// NegotiateALPNAndNPN, if true, causes the server to negotiate both
	// ALPN and NPN in the same connetion.
	NegotiateALPNAndNPN bool

	// SendALPN, if non-empty, causes the server to send the specified
	// string in the ALPN extension regardless of the content or presence of
	// the client offer.
	SendALPN string

	// SendUnencryptedALPN, if non-empty, causes the server to send the
	// specified string in a ServerHello ALPN extension in TLS 1.3.
	SendUnencryptedALPN string

	// SendEmptySessionTicket, if true, causes the server to send an empty
	// session ticket.
	SendEmptySessionTicket bool

	// SendPSKKeyExchangeModes, if present, determines the PSK key exchange modes
	// to send.
	SendPSKKeyExchangeModes []byte

	// ExpectNoNewSessionTicket, if present, means that the client will fail upon
	// receipt of a NewSessionTicket message.
	ExpectNoNewSessionTicket bool

	// DuplicateTicketEarlyData causes an extra empty extension of early_data to
	// be sent in NewSessionTicket.
	DuplicateTicketEarlyData bool

	// ExpectTicketEarlyData, if true, means that the client will fail upon
	// absence of the early_data extension.
	ExpectTicketEarlyData bool

	// ExpectTicketAge, if non-zero, is the expected age of the ticket that the
	// server receives from the client.
	ExpectTicketAge time.Duration

	// SendTicketAge, if non-zero, is the ticket age to be sent by the
	// client.
	SendTicketAge time.Duration

	// FailIfSessionOffered, if true, causes the server to fail any
	// connections where the client offers a non-empty session ID or session
	// ticket.
	FailIfSessionOffered bool

	// SendHelloRequestBeforeEveryAppDataRecord, if true, causes a
	// HelloRequest handshake message to be sent before each application
	// data record. This only makes sense for a server.
	SendHelloRequestBeforeEveryAppDataRecord bool

	// SendHelloRequestBeforeEveryHandshakeMessage, if true, causes a
	// HelloRequest handshake message to be sent before each handshake
	// message. This only makes sense for a server.
	SendHelloRequestBeforeEveryHandshakeMessage bool

	// BadChangeCipherSpec, if not nil, is the body to be sent in
	// ChangeCipherSpec records instead of {1}.
	BadChangeCipherSpec []byte

	// BadHelloRequest, if not nil, is what to send instead of a
	// HelloRequest.
	BadHelloRequest []byte

	// RequireSessionTickets, if true, causes the client to require new
	// sessions use session tickets instead of session IDs.
	RequireSessionTickets bool

	// RequireSessionIDs, if true, causes the client to require new sessions use
	// session IDs instead of session tickets.
	RequireSessionIDs bool

	// NullAllCiphers, if true, causes every cipher to behave like the null
	// cipher.
	NullAllCiphers bool

	// SendSCTListOnResume, if not nil, causes the server to send the
	// supplied SCT list in resumption handshakes.
	SendSCTListOnResume []byte

	// SendSCTListOnRenegotiation, if not nil, causes the server to send the
	// supplied SCT list on renegotiation.
	SendSCTListOnRenegotiation []byte

	// SendOCSPResponseOnResume, if not nil, causes the server to advertise
	// OCSP stapling in resumption handshakes and, if applicable, send the
	// supplied stapled response.
	SendOCSPResponseOnResume []byte

	// SendOCSPResponseOnResume, if not nil, causes the server to send the
	// supplied OCSP response on renegotiation.
	SendOCSPResponseOnRenegotiation []byte

	// SendExtensionOnCertificate, if not nil, causes the runner to send the
	// supplied bytes in the extensions on the Certificate message.
	SendExtensionOnCertificate []byte

	// SendOCSPOnIntermediates, if not nil, causes the server to send the
	// supplied OCSP on intermediate certificates in the Certificate message.
	SendOCSPOnIntermediates []byte

	// SendSCTOnIntermediates, if not nil, causes the server to send the
	// supplied SCT on intermediate certificates in the Certificate message.
	SendSCTOnIntermediates []byte

	// SendDuplicateCertExtensions, if true, causes the server to send an extra
	// copy of the OCSP/SCT extensions in the Certificate message.
	SendDuplicateCertExtensions bool

	// ExpectNoExtensionsOnIntermediate, if true, causes the client to
	// reject extensions on intermediate certificates.
	ExpectNoExtensionsOnIntermediate bool

	// RecordPadding is the number of bytes of padding to add to each
	// encrypted record in TLS 1.3.
	RecordPadding int

	// OmitRecordContents, if true, causes encrypted records in TLS 1.3 to
	// be missing their body and content type. Padding, if configured, is
	// still added.
	OmitRecordContents bool

	// OuterRecordType, if non-zero, is the outer record type to use instead
	// of application data.
	OuterRecordType recordType

	// SendSignatureAlgorithm, if non-zero, causes all signatures to be sent
	// with the given signature algorithm rather than the one negotiated.
	SendSignatureAlgorithm signatureAlgorithm

	// SkipECDSACurveCheck, if true, causes all ECDSA curve checks to be
	// skipped.
	SkipECDSACurveCheck bool

	// IgnoreSignatureVersionChecks, if true, causes all signature
	// algorithms to be enabled at all TLS versions.
	IgnoreSignatureVersionChecks bool

	// NegotiateRenegotiationInfoAtAllVersions, if true, causes
	// Renegotiation Info to be negotiated at all versions.
	NegotiateRenegotiationInfoAtAllVersions bool

	// NegotiateNPNAtAllVersions, if true, causes NPN to be negotiated at
	// all versions.
	NegotiateNPNAtAllVersions bool

	// NegotiateEMSAtAllVersions, if true, causes EMS to be negotiated at
	// all versions.
	NegotiateEMSAtAllVersions bool

	// AdvertiseTicketExtension, if true, causes the ticket extension to be
	// advertised in server extensions
	AdvertiseTicketExtension bool

	// NegotiatePSKResumption, if true, causes the server to attempt pure PSK
	// resumption.
	NegotiatePSKResumption bool

	// AlwaysSelectPSKIdentity, if true, causes the server in TLS 1.3 to
	// always acknowledge a session, regardless of one was offered.
	AlwaysSelectPSKIdentity bool

	// SelectPSKIdentityOnResume, if non-zero, causes the server to select
	// the specified PSK identity index rather than the actual value.
	SelectPSKIdentityOnResume uint16

	// ExtraPSKIdentity, if true, causes the client to send an extra PSK
	// identity.
	ExtraPSKIdentity bool

	// MissingKeyShare, if true, causes the TLS 1.3 implementation to skip
	// sending a key_share extension and use the zero ECDHE secret
	// instead.
	MissingKeyShare bool

	// SecondClientHelloMissingKeyShare, if true, causes the second TLS 1.3
	// ClientHello to skip sending a key_share extension and use the zero
	// ECDHE secret instead.
	SecondClientHelloMissingKeyShare bool

	// MisinterpretHelloRetryRequestCurve, if non-zero, causes the TLS 1.3
	// client to pretend the server requested a HelloRetryRequest with the
	// given curve rather than the actual one.
	MisinterpretHelloRetryRequestCurve CurveID

	// DuplicateKeyShares, if true, causes the TLS 1.3 client to send two
	// copies of each KeyShareEntry.
	DuplicateKeyShares bool

	// SendEarlyAlert, if true, sends a fatal alert after the ClientHello.
	SendEarlyAlert bool

	// SendFakeEarlyDataLength, if non-zero, is the amount of early data to
	// send after the ClientHello.
	SendFakeEarlyDataLength int

	// SendStrayEarlyHandshake, if non-zero, causes the client to send a stray
	// handshake record before sending end of early data.
	SendStrayEarlyHandshake bool

	// OmitEarlyDataExtension, if true, causes the early data extension to
	// be omitted in the ClientHello.
	OmitEarlyDataExtension bool

	// SendEarlyDataOnSecondClientHello, if true, causes the TLS 1.3 client to
	// send early data after the second ClientHello.
	SendEarlyDataOnSecondClientHello bool

	// InterleaveEarlyData, if true, causes the TLS 1.3 client to send early
	// data interleaved with the second ClientHello and the client Finished.
	InterleaveEarlyData bool

	// SendEarlyData causes a TLS 1.3 client to send the provided data
	// in application data records immediately after the ClientHello,
	// provided that the client offers a TLS 1.3 session. It will do this
	// whether or not the server advertised early data for the ticket.
	SendEarlyData [][]byte

	// ExpectEarlyDataAccepted causes a TLS 1.3 client to check that early data
	// was accepted by the server.
	ExpectEarlyDataAccepted bool

	// AlwaysAcceptEarlyData causes a TLS 1.3 server to always accept early data
	// regardless of ALPN mismatch.
	AlwaysAcceptEarlyData bool

	// AlwaysRejectEarlyData causes a TLS 1.3 server to always reject early data.
	AlwaysRejectEarlyData bool

	// SendEarlyDataExtension, if true, causes a TLS 1.3 server to send the
	// early_data extension in EncryptedExtensions, independent of whether
	// it was accepted.
	SendEarlyDataExtension bool

	// ExpectEarlyData causes a TLS 1.3 server to read application
	// data after the ClientHello (assuming the server is able to
	// derive the key under which the data is encrypted) before it
	// sends a ServerHello. It checks that the application data it
	// reads matches what is provided in ExpectEarlyData and errors if
	// the number of records or their content do not match.
	ExpectEarlyData [][]byte

	// ExpectLateEarlyData causes a TLS 1.3 server to read application
	// data after the ServerFinished (assuming the server is able to
	// derive the key under which the data is encrypted) before it
	// sends the ClientFinished. It checks that the application data it
	// reads matches what is provided in ExpectLateEarlyData and errors if
	// the number of records or their content do not match.
	ExpectLateEarlyData [][]byte

	// SendHalfRTTData causes a TLS 1.3 server to send the provided
	// data in application data records before reading the client's
	// Finished message.
	SendHalfRTTData [][]byte

	// ExpectHalfRTTData causes a TLS 1.3 client, if 0-RTT was accepted, to
	// read application data after reading the server's Finished message and
	// before sending any subsequent handshake messages. It checks that the
	// application data it reads matches what is provided in
	// ExpectHalfRTTData and errors if the number of records or their
	// content do not match.
	ExpectHalfRTTData [][]byte

	// EmptyEncryptedExtensions, if true, causes the TLS 1.3 server to
	// emit an empty EncryptedExtensions block.
	EmptyEncryptedExtensions bool

	// EncryptedExtensionsWithKeyShare, if true, causes the TLS 1.3 server to
	// include the KeyShare extension in the EncryptedExtensions block.
	EncryptedExtensionsWithKeyShare bool

	// AlwaysSendHelloRetryRequest, if true, causes a HelloRetryRequest to
	// be sent by the server, even if empty.
	AlwaysSendHelloRetryRequest bool

	// SecondHelloRetryRequest, if true, causes the TLS 1.3 server to send
	// two HelloRetryRequests instead of one.
	SecondHelloRetryRequest bool

	// SendHelloRetryRequestCurve, if non-zero, causes the server to send
	// the specified curve in a HelloRetryRequest.
	SendHelloRetryRequestCurve CurveID

	// SendHelloRetryRequestCipherSuite, if non-zero, causes the server to send
	// the specified cipher suite in a HelloRetryRequest.
	SendHelloRetryRequestCipherSuite uint16

	// SendHelloRetryRequestCookie, if not nil, contains a cookie to be
	// sent by the server in HelloRetryRequest.
	SendHelloRetryRequestCookie []byte

	// DuplicateHelloRetryRequestExtensions, if true, causes all
	// HelloRetryRequest extensions to be sent twice.
	DuplicateHelloRetryRequestExtensions bool

	// SendServerHelloVersion, if non-zero, causes the server to send the
	// specified value in ServerHello version field.
	SendServerHelloVersion uint16

	// SendServerSupportedVersionExtension, if non-zero, causes the server to send
	// the specified value in supported_versions extension in the ServerHello (but
	// not the HelloRetryRequest).
	SendServerSupportedVersionExtension uint16

	// OmitServerSupportedVersionExtension, if true, causes the server to
	// omit the supported_versions extension in the ServerHello (but not the
	// HelloRetryRequest)
	OmitServerSupportedVersionExtension bool

	// SkipHelloRetryRequest, if true, causes the TLS 1.3 server to not send
	// HelloRetryRequest.
	SkipHelloRetryRequest bool

	// PackHelloRequestWithFinished, if true, causes the TLS server to send
	// HelloRequest in the same record as Finished.
	PackHelloRequestWithFinished bool

	// ExpectMissingKeyShare, if true, causes the TLS server to fail the
	// connection if the selected curve appears in the client's initial
	// ClientHello. That is, it requires that a HelloRetryRequest be sent.
	ExpectMissingKeyShare bool

	// SendExtraFinished, if true, causes an extra Finished message to be
	// sent.
	SendExtraFinished bool

	// SendRequestContext, if not empty, is the request context to send in
	// a TLS 1.3 CertificateRequest.
	SendRequestContext []byte

	// OmitCertificateRequestAlgorithms, if true, omits the signature_algorithm
	// extension in a TLS 1.3 CertificateRequest.
	OmitCertificateRequestAlgorithms bool

	// SendCustomCertificateRequest, if non-zero, send an additional custom
	// extension in a TLS 1.3 CertificateRequest.
	SendCustomCertificateRequest uint16

	// SendSNIWarningAlert, if true, causes the server to send an
	// unrecognized_name alert before the ServerHello.
	SendSNIWarningAlert bool

	// SendCompressionMethods, if not nil, is the compression method list to
	// send in the ClientHello.
	SendCompressionMethods []byte

	// SendCompressionMethod is the compression method to send in the
	// ServerHello.
	SendCompressionMethod byte

	// AlwaysSendPreSharedKeyIdentityHint, if true, causes the server to
	// always send a ServerKeyExchange for PSK ciphers, even if the identity
	// hint is empty.
	AlwaysSendPreSharedKeyIdentityHint bool

	// TrailingKeyShareData, if true, causes the client key share list to
	// include a trailing byte.
	TrailingKeyShareData bool

	// InvalidChannelIDSignature, if true, causes the client to generate an
	// invalid Channel ID signature.
	InvalidChannelIDSignature bool

	// ExpectGREASE, if true, causes messages without GREASE values to be
	// rejected. See draft-davidben-tls-grease-01.
	ExpectGREASE bool

	// OmitPSKsOnSecondClientHello, if true, causes the client to omit the
	// PSK extension on the second ClientHello.
	OmitPSKsOnSecondClientHello bool

	// OnlyCorruptSecondPSKBinder, if true, causes the options below to
	// only apply to the second PSK binder.
	OnlyCorruptSecondPSKBinder bool

	// SendShortPSKBinder, if true, causes the client to send a PSK binder
	// that is one byte shorter than it should be.
	SendShortPSKBinder bool

	// SendInvalidPSKBinder, if true, causes the client to send an invalid
	// PSK binder.
	SendInvalidPSKBinder bool

	// SendNoPSKBinder, if true, causes the client to send no PSK binders.
	SendNoPSKBinder bool

	// SendExtraPSKBinder, if true, causes the client to send an extra PSK
	// binder.
	SendExtraPSKBinder bool

	// PSKBinderFirst, if true, causes the client to send the PSK Binder
	// extension as the first extension instead of the last extension.
	PSKBinderFirst bool

	// NoOCSPStapling, if true, causes the client to not request OCSP
	// stapling.
	NoOCSPStapling bool

	// NoSignedCertificateTimestamps, if true, causes the client to not
	// request signed certificate timestamps.
	NoSignedCertificateTimestamps bool

	// SendSupportedPointFormats, if not nil, is the list of supported point
	// formats to send in ClientHello or ServerHello. If set to a non-nil
	// empty slice, no extension will be sent.
	SendSupportedPointFormats []byte

	// SendServerSupportedCurves, if true, causes the server to send its
	// supported curves list in the ServerHello (TLS 1.2) or
	// EncryptedExtensions (TLS 1.3) message. This is invalid in TLS 1.2 and
	// valid in TLS 1.3.
	SendServerSupportedCurves bool

	// MaxReceivePlaintext, if non-zero, is the maximum plaintext record
	// length accepted from the peer.
	MaxReceivePlaintext int

	// ExpectPackedEncryptedHandshake, if non-zero, requires that the peer maximally
	// pack their encrypted handshake messages, fitting at most the
	// specified number of plaintext bytes per record.
	ExpectPackedEncryptedHandshake int

	// SendTicketLifetime, if non-zero, is the ticket lifetime to send in
	// NewSessionTicket messages.
	SendTicketLifetime time.Duration

	// SendServerNameAck, if true, causes the server to acknowledge the SNI
	// extension.
	SendServerNameAck bool

	// ExpectCertificateReqNames, if not nil, contains the list of X.509
	// names that must be sent in a CertificateRequest from the server.
	ExpectCertificateReqNames [][]byte

	// RenegotiationCertificate, if not nil, is the certificate to use on
	// renegotiation handshakes.
	RenegotiationCertificate *Certificate

	// ExpectNoCertificateAuthoritiesExtension, if true, causes the client to
	// reject CertificateRequest with the CertificateAuthorities extension.
	ExpectNoCertificateAuthoritiesExtension bool

	// UseLegacySigningAlgorithm, if non-zero, is the signature algorithm
	// to use when signing in TLS 1.1 and earlier where algorithms are not
	// negotiated.
	UseLegacySigningAlgorithm signatureAlgorithm

	// SendServerHelloAsHelloRetryRequest, if true, causes the server to
	// send ServerHello messages with a HelloRetryRequest type field.
	SendServerHelloAsHelloRetryRequest bool

	// RejectUnsolicitedKeyUpdate, if true, causes all unsolicited
	// KeyUpdates from the peer to be rejected.
	RejectUnsolicitedKeyUpdate bool

	// OmitExtensions, if true, causes the extensions field in ClientHello
	// and ServerHello messages to be omitted.
	OmitExtensions bool

	// EmptyExtensions, if true, causes the extensions field in ClientHello
	// and ServerHello messages to be present, but empty.
	EmptyExtensions bool

	// ExpectOmitExtensions, if true, causes the client to reject
	// ServerHello messages that do not omit extensions.
	ExpectOmitExtensions bool

	// ExpectRecordSplitting, if true, causes application records to only be
	// accepted if they follow a 1/n-1 record split.
	ExpectRecordSplitting bool

	// PadClientHello, if non-zero, pads the ClientHello to a multiple of
	// that many bytes.
	PadClientHello int

	// SendTLS13DowngradeRandom, if true, causes the server to send the
	// TLS 1.3 anti-downgrade signal.
	SendTLS13DowngradeRandom bool

	// CheckTLS13DowngradeRandom, if true, causes the client to check the
	// TLS 1.3 anti-downgrade signal regardless of its variant.
	CheckTLS13DowngradeRandom bool

	// IgnoreTLS13DowngradeRandom, if true, causes the client to ignore the
	// TLS 1.3 anti-downgrade signal.
	IgnoreTLS13DowngradeRandom bool

	// SendCompressedCoordinates, if true, causes ECDH key shares over NIST
	// curves to use compressed coordinates.
	SendCompressedCoordinates bool

	// ExpectRSAPSSSupport specifies the level of RSA-PSS support expected
	// from the peer.
	ExpectRSAPSSSupport RSAPSSSupport

	// SetX25519HighBit, if true, causes X25519 key shares to set their
	// high-order bit.
	SetX25519HighBit bool

	// DuplicateCompressedCertAlgs, if true, causes two, equal, certificate
	// compression algorithm IDs to be sent.
	DuplicateCompressedCertAlgs bool

	// ExpectedCompressedCert specifies the compression algorithm ID that must be
	// used on this connection, or zero if there are no special requirements.
	ExpectedCompressedCert uint16

	// SendCertCompressionAlgId, if not zero, sets the algorithm ID that will be
	// sent in the compressed certificate message.
	SendCertCompressionAlgId uint16

	// SendCertUncompressedLength, if not zero, sets the uncompressed length that
	// will be sent in the compressed certificate message.
	SendCertUncompressedLength uint32

	// SendClientHelloWithFixes, if not nil, sends the specified byte string
	// instead of the ClientHello. This string is incorporated into the
	// transcript as if it were the real ClientHello, but the handshake will
	// otherwise behave as if this was not sent in terms of what ciphers it
	// will accept, etc.
	//
	// The input is modified to match key share entries. DefaultCurves must
	// be configured to match. The random and session ID fields are
	// extracted from the ClientHello.
	SendClientHelloWithFixes []byte

	// SendJDK11DowngradeRandom, if true, causes the server to send the JDK
	// 11 downgrade signal.
	SendJDK11DowngradeRandom bool

	// ExpectJDK11DowngradeRandom is whether the client should expect the
	// server to send the JDK 11 downgrade signal.
	ExpectJDK11DowngradeRandom bool

	// FailIfHelloRetryRequested causes a handshake failure if a server requests a
	// hello retry.
	FailIfHelloRetryRequested bool

	// FailedIfCECPQ2Offered will cause a server to reject a ClientHello if CECPQ2
	// is supported.
	FailIfCECPQ2Offered bool

	// ExpectKeyShares, if not nil, lists (in order) the curves that a ClientHello
	// should have key shares for.
	ExpectedKeyShares []CurveID

	// ExpectDelegatedCredentials, if true, requires that the handshake present
	// delegated credentials.
	ExpectDelegatedCredentials bool

	// FailIfDelegatedCredentials, if true, causes a handshake failure if the
	// server returns delegated credentials.
	FailIfDelegatedCredentials bool

	// DisableDelegatedCredentials, if true, disables client support for delegated
	// credentials.
	DisableDelegatedCredentials bool

	// ExpectPQExperimentSignal specifies whether or not the post-quantum
	// experiment signal should be received by a client or server.
	ExpectPQExperimentSignal bool
}

func (c *Config) serverInit() {
	if c.SessionTicketsDisabled {
		return
	}

	// If the key has already been set then we have nothing to do.
	for _, b := range c.SessionTicketKey {
		if b != 0 {
			return
		}
	}

	if _, err := io.ReadFull(c.rand(), c.SessionTicketKey[:]); err != nil {
		c.SessionTicketsDisabled = true
	}
}

func (c *Config) rand() io.Reader {
	r := c.Rand
	if r == nil {
		return rand.Reader
	}
	return r
}

func (c *Config) time() time.Time {
	t := c.Time
	if t == nil {
		t = time.Now
	}
	return t()
}

func (c *Config) cipherSuites() []uint16 {
	s := c.CipherSuites
	if s == nil {
		s = defaultCipherSuites()
	}
	return s
}

func (c *Config) minVersion(isDTLS bool) uint16 {
	ret := uint16(minVersion)
	if c != nil && c.MinVersion != 0 {
		ret = c.MinVersion
	}
	if isDTLS {
		// The lowest version of DTLS is 1.0. There is no DSSL 3.0.
		if ret < VersionTLS10 {
			return VersionTLS10
		}
		// There is no such thing as DTLS 1.1.
		if ret == VersionTLS11 {
			return VersionTLS12
		}
	}
	return ret
}

func (c *Config) maxVersion(isDTLS bool) uint16 {
	ret := uint16(maxVersion)
	if c != nil && c.MaxVersion != 0 {
		ret = c.MaxVersion
	}
	if isDTLS {
		// We only implement up to DTLS 1.2.
		if ret > VersionTLS12 {
			return VersionTLS12
		}
		// There is no such thing as DTLS 1.1.
		if ret == VersionTLS11 {
			return VersionTLS10
		}
	}
	return ret
}

var defaultCurvePreferences = []CurveID{CurveCECPQ2b, CurveCECPQ2, CurveX25519, CurveP256, CurveP384, CurveP521}

func (c *Config) curvePreferences() []CurveID {
	if c == nil || len(c.CurvePreferences) == 0 {
		return defaultCurvePreferences
	}
	return c.CurvePreferences
}

func (c *Config) defaultCurves() map[CurveID]bool {
	defaultCurves := make(map[CurveID]bool)
	curves := c.DefaultCurves
	if c == nil || c.DefaultCurves == nil {
		curves = c.curvePreferences()
	}
	for _, curveID := range curves {
		defaultCurves[curveID] = true
	}
	return defaultCurves
}

func wireToVersion(vers uint16, isDTLS bool) (uint16, bool) {
	if isDTLS {
		switch vers {
		case VersionDTLS12:
			return VersionTLS12, true
		case VersionDTLS10:
			return VersionTLS10, true
		}
	} else {
		switch vers {
		case VersionSSL30, VersionTLS10, VersionTLS11, VersionTLS12, VersionTLS13:
			return vers, true
		}
	}

	return 0, false
}

// isSupportedVersion checks if the specified wire version is acceptable. If so,
// it returns true and the corresponding protocol version. Otherwise, it returns
// false.
func (c *Config) isSupportedVersion(wireVers uint16, isDTLS bool) (uint16, bool) {
	vers, ok := wireToVersion(wireVers, isDTLS)
	if !ok || c.minVersion(isDTLS) > vers || vers > c.maxVersion(isDTLS) {
		return 0, false
	}
	return vers, true
}

func (c *Config) supportedVersions(isDTLS bool) []uint16 {
	versions := allTLSWireVersions
	if isDTLS {
		versions = allDTLSWireVersions
	}
	var ret []uint16
	for _, vers := range versions {
		if _, ok := c.isSupportedVersion(vers, isDTLS); ok {
			ret = append(ret, vers)
		}
	}
	return ret
}

// getCertificateForName returns the best certificate for the given name,
// defaulting to the first element of c.Certificates if there are no good
// options.
func (c *Config) getCertificateForName(name string) *Certificate {
	if len(c.Certificates) == 1 || c.NameToCertificate == nil {
		// There's only one choice, so no point doing any work.
		return &c.Certificates[0]
	}

	name = strings.ToLower(name)
	for len(name) > 0 && name[len(name)-1] == '.' {
		name = name[:len(name)-1]
	}

	if cert, ok := c.NameToCertificate[name]; ok {
		return cert
	}

	// try replacing labels in the name with wildcards until we get a
	// match.
	labels := strings.Split(name, ".")
	for i := range labels {
		labels[i] = "*"
		candidate := strings.Join(labels, ".")
		if cert, ok := c.NameToCertificate[candidate]; ok {
			return cert
		}
	}

	// If nothing matches, return the first certificate.
	return &c.Certificates[0]
}

func (c *Config) signSignatureAlgorithms() []signatureAlgorithm {
	if c != nil && c.SignSignatureAlgorithms != nil {
		return c.SignSignatureAlgorithms
	}
	return supportedSignatureAlgorithms
}

func (c *Config) verifySignatureAlgorithms() []signatureAlgorithm {
	if c != nil && c.VerifySignatureAlgorithms != nil {
		return c.VerifySignatureAlgorithms
	}
	return supportedSignatureAlgorithms
}

// BuildNameToCertificate parses c.Certificates and builds c.NameToCertificate
// from the CommonName and SubjectAlternateName fields of each of the leaf
// certificates.
func (c *Config) BuildNameToCertificate() {
	c.NameToCertificate = make(map[string]*Certificate)
	for i := range c.Certificates {
		cert := &c.Certificates[i]
		x509Cert, err := x509.ParseCertificate(cert.Certificate[0])
		if err != nil {
			continue
		}
		if len(x509Cert.Subject.CommonName) > 0 {
			c.NameToCertificate[x509Cert.Subject.CommonName] = cert
		}
		for _, san := range x509Cert.DNSNames {
			c.NameToCertificate[san] = cert
		}
	}
}

// A Certificate is a chain of one or more certificates, leaf first.
type Certificate struct {
	Certificate [][]byte
	PrivateKey  crypto.PrivateKey // supported types: *rsa.PrivateKey, *ecdsa.PrivateKey
	// OCSPStaple contains an optional OCSP response which will be served
	// to clients that request it.
	OCSPStaple []byte
	// SignedCertificateTimestampList contains an optional encoded
	// SignedCertificateTimestampList structure which will be
	// served to clients that request it.
	SignedCertificateTimestampList []byte
	// Leaf is the parsed form of the leaf certificate, which may be
	// initialized using x509.ParseCertificate to reduce per-handshake
	// processing for TLS clients doing client authentication. If nil, the
	// leaf certificate will be parsed as needed.
	Leaf *x509.Certificate
}

// A TLS record.
type record struct {
	contentType  recordType
	major, minor uint8
	payload      []byte
}

type handshakeMessage interface {
	marshal() []byte
	unmarshal([]byte) bool
}

// lruSessionCache is a client or server session cache implementation
// that uses an LRU caching strategy.
type lruSessionCache struct {
	sync.Mutex

	m        map[string]*list.Element
	q        *list.List
	capacity int
}

type lruSessionCacheEntry struct {
	sessionKey string
	state      interface{}
}

// Put adds the provided (sessionKey, cs) pair to the cache.
func (c *lruSessionCache) Put(sessionKey string, cs interface{}) {
	c.Lock()
	defer c.Unlock()

	if elem, ok := c.m[sessionKey]; ok {
		entry := elem.Value.(*lruSessionCacheEntry)
		entry.state = cs
		c.q.MoveToFront(elem)
		return
	}

	if c.q.Len() < c.capacity {
		entry := &lruSessionCacheEntry{sessionKey, cs}
		c.m[sessionKey] = c.q.PushFront(entry)
		return
	}

	elem := c.q.Back()
	entry := elem.Value.(*lruSessionCacheEntry)
	delete(c.m, entry.sessionKey)
	entry.sessionKey = sessionKey
	entry.state = cs
	c.q.MoveToFront(elem)
	c.m[sessionKey] = elem
}

// Get returns the value associated with a given key. It returns (nil,
// false) if no value is found.
func (c *lruSessionCache) Get(sessionKey string) (interface{}, bool) {
	c.Lock()
	defer c.Unlock()

	if elem, ok := c.m[sessionKey]; ok {
		c.q.MoveToFront(elem)
		return elem.Value.(*lruSessionCacheEntry).state, true
	}
	return nil, false
}

// lruClientSessionCache is a ClientSessionCache implementation that
// uses an LRU caching strategy.
type lruClientSessionCache struct {
	lruSessionCache
}

func (c *lruClientSessionCache) Put(sessionKey string, cs *ClientSessionState) {
	c.lruSessionCache.Put(sessionKey, cs)
}

func (c *lruClientSessionCache) Get(sessionKey string) (*ClientSessionState, bool) {
	cs, ok := c.lruSessionCache.Get(sessionKey)
	if !ok {
		return nil, false
	}
	return cs.(*ClientSessionState), true
}

// lruServerSessionCache is a ServerSessionCache implementation that
// uses an LRU caching strategy.
type lruServerSessionCache struct {
	lruSessionCache
}

func (c *lruServerSessionCache) Put(sessionId string, session *sessionState) {
	c.lruSessionCache.Put(sessionId, session)
}

func (c *lruServerSessionCache) Get(sessionId string) (*sessionState, bool) {
	cs, ok := c.lruSessionCache.Get(sessionId)
	if !ok {
		return nil, false
	}
	return cs.(*sessionState), true
}

// NewLRUClientSessionCache returns a ClientSessionCache with the given
// capacity that uses an LRU strategy. If capacity is < 1, a default capacity
// is used instead.
func NewLRUClientSessionCache(capacity int) ClientSessionCache {
	const defaultSessionCacheCapacity = 64

	if capacity < 1 {
		capacity = defaultSessionCacheCapacity
	}
	return &lruClientSessionCache{
		lruSessionCache{
			m:        make(map[string]*list.Element),
			q:        list.New(),
			capacity: capacity,
		},
	}
}

// NewLRUServerSessionCache returns a ServerSessionCache with the given
// capacity that uses an LRU strategy. If capacity is < 1, a default capacity
// is used instead.
func NewLRUServerSessionCache(capacity int) ServerSessionCache {
	const defaultSessionCacheCapacity = 64

	if capacity < 1 {
		capacity = defaultSessionCacheCapacity
	}
	return &lruServerSessionCache{
		lruSessionCache{
			m:        make(map[string]*list.Element),
			q:        list.New(),
			capacity: capacity,
		},
	}
}

// TODO(jsing): Make these available to both crypto/x509 and crypto/tls.
type dsaSignature struct {
	R, S *big.Int
}

type ecdsaSignature dsaSignature

var emptyConfig Config

func defaultConfig() *Config {
	return &emptyConfig
}

var (
	once                   sync.Once
	varDefaultCipherSuites []uint16
)

func defaultCipherSuites() []uint16 {
	once.Do(initDefaultCipherSuites)
	return varDefaultCipherSuites
}

func initDefaultCipherSuites() {
	for _, suite := range cipherSuites {
		if suite.flags&suitePSK == 0 {
			varDefaultCipherSuites = append(varDefaultCipherSuites, suite.id)
		}
	}
}

func unexpectedMessageError(wanted, got interface{}) error {
	return fmt.Errorf("tls: received unexpected handshake message of type %T when waiting for %T", got, wanted)
}

func isSupportedSignatureAlgorithm(sigAlg signatureAlgorithm, sigAlgs []signatureAlgorithm) bool {
	for _, s := range sigAlgs {
		if s == sigAlg {
			return true
		}
	}
	return false
}

var (
	// See RFC 8446, section 4.1.3.
	downgradeTLS13 = []byte{0x44, 0x4f, 0x57, 0x4e, 0x47, 0x52, 0x44, 0x01}
	downgradeTLS12 = []byte{0x44, 0x4f, 0x57, 0x4e, 0x47, 0x52, 0x44, 0x00}

	// This is a non-standard randomly-generated value.
	downgradeJDK11 = []byte{0xed, 0xbf, 0xb4, 0xa8, 0xc2, 0x47, 0x10, 0xff}
)

func containsGREASE(values []uint16) bool {
	for _, v := range values {
		if isGREASEValue(v) {
			return true
		}
	}
	return false
}

func checkRSAPSSSupport(support RSAPSSSupport, sigAlgs, sigAlgsCert []signatureAlgorithm) error {
	if sigAlgsCert == nil {
		sigAlgsCert = sigAlgs
	} else if eqSignatureAlgorithms(sigAlgs, sigAlgsCert) {
		// The peer should have only sent the list once.
		return errors.New("tls: signature_algorithms and signature_algorithms_cert extensions were identical")
	}

	if support == RSAPSSSupportAny {
		return nil
	}

	var foundPSS, foundPSSCert bool
	for _, sigAlg := range sigAlgs {
		if sigAlg == signatureRSAPSSWithSHA256 || sigAlg == signatureRSAPSSWithSHA384 || sigAlg == signatureRSAPSSWithSHA512 {
			foundPSS = true
			break
		}
	}
	for _, sigAlg := range sigAlgsCert {
		if sigAlg == signatureRSAPSSWithSHA256 || sigAlg == signatureRSAPSSWithSHA384 || sigAlg == signatureRSAPSSWithSHA512 {
			foundPSSCert = true
			break
		}
	}

	expectPSS := support != RSAPSSSupportNone
	if foundPSS != expectPSS {
		if expectPSS {
			return errors.New("tls: peer did not support PSS")
		} else {
			return errors.New("tls: peer unexpectedly supported PSS")
		}
	}

	expectPSSCert := support == RSAPSSSupportBoth
	if foundPSSCert != expectPSSCert {
		if expectPSSCert {
			return errors.New("tls: peer did not support PSS in certificates")
		} else {
			return errors.New("tls: peer unexpectedly supported PSS in certificates")
		}
	}

	return nil
}

// Copyright (c) 2016, Google Inc.
//
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
// OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
// CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

package runner

import (
	"bytes"
	"crypto"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"math/big"
	"net"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"boringssl.googlesource.com/boringssl/util/testresult"
)

var (
	useValgrind        = flag.Bool("valgrind", false, "If true, run code under valgrind")
	useGDB             = flag.Bool("gdb", false, "If true, run BoringSSL code under gdb")
	useLLDB            = flag.Bool("lldb", false, "If true, run BoringSSL code under lldb")
	flagDebug          = flag.Bool("debug", false, "Hexdump the contents of the connection")
	mallocTest         = flag.Int64("malloc-test", -1, "If non-negative, run each test with each malloc in turn failing from the given number onwards.")
	mallocTestDebug    = flag.Bool("malloc-test-debug", false, "If true, ask bssl_shim to abort rather than fail a malloc. This can be used with a specific value for --malloc-test to identity the malloc failing that is causing problems.")
	jsonOutput         = flag.String("json-output", "", "The file to output JSON results to.")
	pipe               = flag.Bool("pipe", false, "If true, print status output suitable for piping into another program.")
	testToRun          = flag.String("test", "", "The pattern to filter tests to run, or empty to run all tests")
	numWorkers         = flag.Int("num-workers", runtime.NumCPU(), "The number of workers to run in parallel.")
	shimPath           = flag.String("shim-path", "../../../build/ssl/test/bssl_shim", "The location of the shim binary.")
	handshakerPath     = flag.String("handshaker-path", "../../../build/ssl/test/handshaker", "The location of the handshaker binary.")
	resourceDir        = flag.String("resource-dir", ".", "The directory in which to find certificate and key files.")
	fuzzer             = flag.Bool("fuzzer", false, "If true, tests against a BoringSSL built in fuzzer mode.")
	transcriptDir      = flag.String("transcript-dir", "", "The directory in which to write transcripts.")
	idleTimeout        = flag.Duration("idle-timeout", 15*time.Second, "The number of seconds to wait for a read or write to bssl_shim.")
	deterministic      = flag.Bool("deterministic", false, "If true, uses a deterministic PRNG in the runner.")
	allowUnimplemented = flag.Bool("allow-unimplemented", false, "If true, report pass even if some tests are unimplemented.")
	looseErrors        = flag.Bool("loose-errors", false, "If true, allow shims to report an untranslated error code.")
	shimConfigFile     = flag.String("shim-config", "", "A config file to use to configure the tests for this shim.")
	includeDisabled    = flag.Bool("include-disabled", false, "If true, also runs disabled tests.")
	repeatUntilFailure = flag.Bool("repeat-until-failure", false, "If true, the first selected test will be run repeatedly until failure.")
)

// ShimConfigurations is used with the “json” package and represents a shim
// config file.
type ShimConfiguration struct {
	// DisabledTests maps from a glob-based pattern to a freeform string.
	// The glob pattern is used to exclude tests from being run and the
	// freeform string is unparsed but expected to explain why the test is
	// disabled.
	DisabledTests map[string]string

	// ErrorMap maps from expected error strings to the correct error
	// string for the shim in question. For example, it might map
	// “:NO_SHARED_CIPHER:” (a BoringSSL error string) to something
	// like “SSL_ERROR_NO_CYPHER_OVERLAP”.
	ErrorMap map[string]string

	// HalfRTTTickets is the number of half-RTT tickets the client should
	// expect before half-RTT data when testing 0-RTT.
	HalfRTTTickets int
}

// Setup shimConfig defaults aligning with BoringSSL.
var shimConfig ShimConfiguration = ShimConfiguration{
	HalfRTTTickets: 2,
}

type testCert int

const (
	testCertRSA testCert = iota
	testCertRSA1024
	testCertRSAChain
	testCertECDSAP224
	testCertECDSAP256
	testCertECDSAP384
	testCertECDSAP521
	testCertEd25519
)

const (
	rsaCertificateFile       = "cert.pem"
	rsa1024CertificateFile   = "rsa_1024_cert.pem"
	rsaChainCertificateFile  = "rsa_chain_cert.pem"
	ecdsaP224CertificateFile = "ecdsa_p224_cert.pem"
	ecdsaP256CertificateFile = "ecdsa_p256_cert.pem"
	ecdsaP384CertificateFile = "ecdsa_p384_cert.pem"
	ecdsaP521CertificateFile = "ecdsa_p521_cert.pem"
	ed25519CertificateFile   = "ed25519_cert.pem"
)

const (
	rsaKeyFile       = "key.pem"
	rsa1024KeyFile   = "rsa_1024_key.pem"
	rsaChainKeyFile  = "rsa_chain_key.pem"
	ecdsaP224KeyFile = "ecdsa_p224_key.pem"
	ecdsaP256KeyFile = "ecdsa_p256_key.pem"
	ecdsaP384KeyFile = "ecdsa_p384_key.pem"
	ecdsaP521KeyFile = "ecdsa_p521_key.pem"
	ed25519KeyFile   = "ed25519_key.pem"
	channelIDKeyFile = "channel_id_key.pem"
)

var (
	rsaCertificate       Certificate
	rsa1024Certificate   Certificate
	rsaChainCertificate  Certificate
	ecdsaP224Certificate Certificate
	ecdsaP256Certificate Certificate
	ecdsaP384Certificate Certificate
	ecdsaP521Certificate Certificate
	ed25519Certificate   Certificate
	garbageCertificate   Certificate
)

var testCerts = []struct {
	id                testCert
	certFile, keyFile string
	cert              *Certificate
}{
	{
		id:       testCertRSA,
		certFile: rsaCertificateFile,
		keyFile:  rsaKeyFile,
		cert:     &rsaCertificate,
	},
	{
		id:       testCertRSA1024,
		certFile: rsa1024CertificateFile,
		keyFile:  rsa1024KeyFile,
		cert:     &rsa1024Certificate,
	},
	{
		id:       testCertRSAChain,
		certFile: rsaChainCertificateFile,
		keyFile:  rsaChainKeyFile,
		cert:     &rsaChainCertificate,
	},
	{
		id:       testCertECDSAP224,
		certFile: ecdsaP224CertificateFile,
		keyFile:  ecdsaP224KeyFile,
		cert:     &ecdsaP224Certificate,
	},
	{
		id:       testCertECDSAP256,
		certFile: ecdsaP256CertificateFile,
		keyFile:  ecdsaP256KeyFile,
		cert:     &ecdsaP256Certificate,
	},
	{
		id:       testCertECDSAP384,
		certFile: ecdsaP384CertificateFile,
		keyFile:  ecdsaP384KeyFile,
		cert:     &ecdsaP384Certificate,
	},
	{
		id:       testCertECDSAP521,
		certFile: ecdsaP521CertificateFile,
		keyFile:  ecdsaP521KeyFile,
		cert:     &ecdsaP521Certificate,
	},
	{
		id:       testCertEd25519,
		certFile: ed25519CertificateFile,
		keyFile:  ed25519KeyFile,
		cert:     &ed25519Certificate,
	},
}

var channelIDKey *ecdsa.PrivateKey
var channelIDBytes []byte

var testOCSPResponse = []byte{1, 2, 3, 4}
var testOCSPResponse2 = []byte{5, 6, 7, 8}
var testSCTList = []byte{0, 6, 0, 4, 5, 6, 7, 8}
var testSCTList2 = []byte{0, 6, 0, 4, 1, 2, 3, 4}

var testOCSPExtension = append([]byte{byte(extensionStatusRequest) >> 8, byte(extensionStatusRequest), 0, 8, statusTypeOCSP, 0, 0, 4}, testOCSPResponse...)
var testSCTExtension = append([]byte{byte(extensionSignedCertificateTimestamp) >> 8, byte(extensionSignedCertificateTimestamp), 0, byte(len(testSCTList))}, testSCTList...)

func initCertificates() {
	for i := range testCerts {
		cert, err := LoadX509KeyPair(path.Join(*resourceDir, testCerts[i].certFile), path.Join(*resourceDir, testCerts[i].keyFile))
		if err != nil {
			panic(err)
		}
		cert.OCSPStaple = testOCSPResponse
		cert.SignedCertificateTimestampList = testSCTList
		*testCerts[i].cert = cert
	}

	channelIDPEMBlock, err := ioutil.ReadFile(path.Join(*resourceDir, channelIDKeyFile))
	if err != nil {
		panic(err)
	}
	channelIDDERBlock, _ := pem.Decode(channelIDPEMBlock)
	if channelIDDERBlock.Type != "EC PRIVATE KEY" {
		panic("bad key type")
	}
	channelIDKey, err = x509.ParseECPrivateKey(channelIDDERBlock.Bytes)
	if err != nil {
		panic(err)
	}
	if channelIDKey.Curve != elliptic.P256() {
		panic("bad curve")
	}

	channelIDBytes = make([]byte, 64)
	writeIntPadded(channelIDBytes[:32], channelIDKey.X)
	writeIntPadded(channelIDBytes[32:], channelIDKey.Y)

	garbageCertificate.Certificate = [][]byte{[]byte("GARBAGE")}
	garbageCertificate.PrivateKey = rsaCertificate.PrivateKey
}

// delegatedCredentialConfig specifies the shape of a delegated credential, not
// including the keys themselves.
type delegatedCredentialConfig struct {
	// lifetime is the amount of time, from the notBefore of the parent
	// certificate, that the delegated credential is valid for. If zero, then 24
	// hours is assumed.
	lifetime time.Duration
	// expectedAlgo is the signature scheme that should be used with this
	// delegated credential. If zero, ECDSA with P-256 is assumed.
	expectedAlgo signatureAlgorithm
	// tlsVersion is the version of TLS that should be used with this delegated
	// credential. If zero, TLS 1.3 is assumed.
	tlsVersion uint16
	// algo is the signature algorithm that the delegated credential itself is
	// signed with. Cannot be zero.
	algo signatureAlgorithm
}

func loadRSAPrivateKey(filename string) (priv *rsa.PrivateKey, privPKCS8 []byte, err error) {
	pemPath := path.Join(*resourceDir, filename)
	pemBytes, err := ioutil.ReadFile(pemPath)
	if err != nil {
		return nil, nil, err
	}

	block, _ := pem.Decode(pemBytes)
	if block == nil {
		return nil, nil, fmt.Errorf("no PEM block found in %q", pemPath)
	}
	privPKCS8 = block.Bytes

	parsed, err := x509.ParsePKCS8PrivateKey(privPKCS8)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to parse PKCS#8 key from %q", pemPath)
	}

	priv, ok := parsed.(*rsa.PrivateKey)
	if !ok {
		return nil, nil, fmt.Errorf("found %T in %q rather than an RSA private key", parsed, pemPath)
	}

	return priv, privPKCS8, nil
}

func createDelegatedCredential(config delegatedCredentialConfig, parentDER []byte, parentPriv crypto.PrivateKey) (dc, privPKCS8 []uint8, err error) {
	expectedAlgo := config.expectedAlgo
	if expectedAlgo == signatureAlgorithm(0) {
		expectedAlgo = signatureECDSAWithP256AndSHA256
	}

	var pub crypto.PublicKey

	switch expectedAlgo {
	case signatureRSAPKCS1WithMD5, signatureRSAPKCS1WithSHA1, signatureRSAPKCS1WithSHA256, signatureRSAPKCS1WithSHA384, signatureRSAPKCS1WithSHA512, signatureRSAPSSWithSHA256, signatureRSAPSSWithSHA384, signatureRSAPSSWithSHA512:
		// RSA keys are expensive to generate so load from disk instead.
		var priv *rsa.PrivateKey
		if priv, privPKCS8, err = loadRSAPrivateKey(rsaKeyFile); err != nil {
			return nil, nil, err
		}

		pub = &priv.PublicKey

	case signatureECDSAWithSHA1, signatureECDSAWithP256AndSHA256, signatureECDSAWithP384AndSHA384, signatureECDSAWithP521AndSHA512:
		var curve elliptic.Curve
		switch expectedAlgo {
		case signatureECDSAWithSHA1, signatureECDSAWithP256AndSHA256:
			curve = elliptic.P256()
		case signatureECDSAWithP384AndSHA384:
			curve = elliptic.P384()
		case signatureECDSAWithP521AndSHA512:
			curve = elliptic.P521()
		default:
			panic("internal error")
		}

		priv, err := ecdsa.GenerateKey(curve, rand.Reader)
		if err != nil {
			return nil, nil, err
		}

		if privPKCS8, err = x509.MarshalPKCS8PrivateKey(priv); err != nil {
			return nil, nil, err
		}

		pub = &priv.PublicKey

	default:
		return nil, nil, fmt.Errorf("unsupported expected signature algorithm: %x", expectedAlgo)
	}

	lifetime := config.lifetime
	if lifetime == 0 {
		lifetime = 24 * time.Hour
	}
	lifetimeSecs := int64(lifetime.Seconds())
	if lifetimeSecs > 1<<32 {
		return nil, nil, fmt.Errorf("lifetime %s is too long to be expressed", lifetime)
	}
	tlsVersion := config.tlsVersion
	if tlsVersion == 0 {
		tlsVersion = VersionTLS13
	}

	if tlsVersion < VersionTLS13 {
		return nil, nil, fmt.Errorf("delegated credentials require TLS 1.3")
	}

	// https://tools.ietf.org/html/draft-ietf-tls-subcerts-03#section-3
	dc = append(dc, byte(lifetimeSecs>>24), byte(lifetimeSecs>>16), byte(lifetimeSecs>>8), byte(lifetimeSecs))
	dc = append(dc, byte(expectedAlgo>>8), byte(expectedAlgo))

	pubBytes, err := x509.MarshalPKIXPublicKey(pub)
	if err != nil {
		return nil, nil, err
	}

	dc = append(dc, byte(len(pubBytes)>>16), byte(len(pubBytes)>>8), byte(len(pubBytes)))
	dc = append(dc, pubBytes...)

	var dummyConfig Config
	parentSigner, err := getSigner(tlsVersion, parentPriv, &dummyConfig, config.algo, false /* not for verification */)
	if err != nil {
		return nil, nil, err
	}

	parentSignature, err := parentSigner.signMessage(parentPriv, &dummyConfig, delegatedCredentialSignedMessage(dc, config.algo, parentDER))
	if err != nil {
		return nil, nil, err
	}

	dc = append(dc, byte(config.algo>>8), byte(config.algo))
	dc = append(dc, byte(len(parentSignature)>>8), byte(len(parentSignature)))
	dc = append(dc, parentSignature...)

	return dc, privPKCS8, nil
}

func getRunnerCertificate(t testCert) Certificate {
	for _, cert := range testCerts {
		if cert.id == t {
			return *cert.cert
		}
	}
	panic("Unknown test certificate")
}

func getShimCertificate(t testCert) string {
	for _, cert := range testCerts {
		if cert.id == t {
			return cert.certFile
		}
	}
	panic("Unknown test certificate")
}

func getShimKey(t testCert) string {
	for _, cert := range testCerts {
		if cert.id == t {
			return cert.keyFile
		}
	}
	panic("Unknown test certificate")
}

// recordVersionToWire maps a record-layer protocol version to its wire
// representation.
func recordVersionToWire(vers uint16, protocol protocol) uint16 {
	if protocol == dtls {
		switch vers {
		case VersionTLS12:
			return VersionDTLS12
		case VersionTLS10:
			return VersionDTLS10
		}
	} else {
		switch vers {
		case VersionSSL30, VersionTLS10, VersionTLS11, VersionTLS12:
			return vers
		}
	}

	panic("unknown version")
}

// encodeDERValues encodes a series of bytestrings in comma-separated-hex form.
func encodeDERValues(values [][]byte) string {
	var ret string
	for i, v := range values {
		if i > 0 {
			ret += ","
		}
		ret += hex.EncodeToString(v)
	}

	return ret
}

func decodeHexOrPanic(in string) []byte {
	ret, err := hex.DecodeString(in)
	if err != nil {
		panic(err)
	}
	return ret
}

type testType int

const (
	clientTest testType = iota
	serverTest
)

type protocol int

const (
	tls protocol = iota
	dtls
)

const (
	alpn = 1
	npn  = 2
)

type testCase struct {
	testType      testType
	protocol      protocol
	name          string
	config        Config
	shouldFail    bool
	expectedError string
	// expectedLocalError, if not empty, contains a substring that must be
	// found in the local error.
	expectedLocalError string
	// expectedVersion, if non-zero, specifies the TLS version that must be
	// negotiated.
	expectedVersion uint16
	// expectedResumeVersion, if non-zero, specifies the TLS version that
	// must be negotiated on resumption. If zero, expectedVersion is used.
	expectedResumeVersion uint16
	// expectedCipher, if non-zero, specifies the TLS cipher suite that
	// should be negotiated.
	expectedCipher uint16
	// expectChannelID controls whether the connection should have
	// negotiated a Channel ID with channelIDKey.
	expectChannelID bool
	// expectTokenBinding controls whether the connection should have
	// negotiated Token Binding.
	expectTokenBinding bool
	// expectedTokenBindingParam is the Token Binding parameter that should
	// have been negotiated (if expectTokenBinding is true).
	expectedTokenBindingParam uint8
	// expectedNextProto controls whether the connection should
	// negotiate a next protocol via NPN or ALPN.
	expectedNextProto string
	// expectNoNextProto, if true, means that no next protocol should be
	// negotiated.
	expectNoNextProto bool
	// expectedNextProtoType, if non-zero, is the expected next
	// protocol negotiation mechanism.
	expectedNextProtoType int
	// expectedSRTPProtectionProfile is the DTLS-SRTP profile that
	// should be negotiated. If zero, none should be negotiated.
	expectedSRTPProtectionProfile uint16
	// expectedOCSPResponse, if not nil, is the expected OCSP response to be received.
	expectedOCSPResponse []uint8
	// expectedSCTList, if not nil, is the expected SCT list to be received.
	expectedSCTList []uint8
	// expectedPeerSignatureAlgorithm, if not zero, is the signature
	// algorithm that the peer should have used in the handshake.
	expectedPeerSignatureAlgorithm signatureAlgorithm
	// expectedCurveID, if not zero, is the curve that the handshake should
	// have used.
	expectedCurveID CurveID
	// messageLen is the length, in bytes, of the test message that will be
	// sent.
	messageLen int
	// messageCount is the number of test messages that will be sent.
	messageCount int
	// certFile is the path to the certificate to use for the server.
	certFile string
	// keyFile is the path to the private key to use for the server.
	keyFile string
	// resumeSession controls whether a second connection should be tested
	// which attempts to resume the first session.
	resumeSession bool
	// resumeRenewedSession controls whether a third connection should be
	// tested which attempts to resume the second connection's session.
	resumeRenewedSession bool
	// expectResumeRejected, if true, specifies that the attempted
	// resumption must be rejected by the client. This is only valid for a
	// serverTest.
	expectResumeRejected bool
	// resumeConfig, if not nil, points to a Config to be used on
	// resumption. Unless newSessionsOnResume is set,
	// SessionTicketKey, ServerSessionCache, and
	// ClientSessionCache are copied from the initial connection's
	// config. If nil, the initial connection's config is used.
	resumeConfig *Config
	// newSessionsOnResume, if true, will cause resumeConfig to
	// use a different session resumption context.
	newSessionsOnResume bool
	// noSessionCache, if true, will cause the server to run without a
	// session cache.
	noSessionCache bool
	// sendPrefix sends a prefix on the socket before actually performing a
	// handshake.
	sendPrefix string
	// shimWritesFirst controls whether the shim sends an initial "hello"
	// message before doing a roundtrip with the runner.
	shimWritesFirst bool
	// readWithUnfinishedWrite behaves like shimWritesFirst, but the shim
	// does not complete the write until responding to the first runner
	// message.
	readWithUnfinishedWrite bool
	// shimShutsDown, if true, runs a test where the shim shuts down the
	// connection immediately after the handshake rather than echoing
	// messages from the runner. The runner will default to not sending
	// application data.
	shimShutsDown bool
	// renegotiate indicates the number of times the connection should be
	// renegotiated during the exchange.
	renegotiate int
	// sendHalfHelloRequest, if true, causes the server to send half a
	// HelloRequest when the handshake completes.
	sendHalfHelloRequest bool
	// renegotiateCiphers is a list of ciphersuite ids that will be
	// switched in just before renegotiation.
	renegotiateCiphers []uint16
	// replayWrites, if true, configures the underlying transport
	// to replay every write it makes in DTLS tests.
	replayWrites bool
	// damageFirstWrite, if true, configures the underlying transport to
	// damage the final byte of the first application data write.
	damageFirstWrite bool
	// exportKeyingMaterial, if non-zero, configures the test to exchange
	// keying material and verify they match.
	exportKeyingMaterial int
	exportLabel          string
	exportContext        string
	useExportContext     bool
	// flags, if not empty, contains a list of command-line flags that will
	// be passed to the shim program.
	flags []string
	// testTLSUnique, if true, causes the shim to send the tls-unique value
	// which will be compared against the expected value.
	testTLSUnique bool
	// sendEmptyRecords is the number of consecutive empty records to send
	// before each test message.
	sendEmptyRecords int
	// sendWarningAlerts is the number of consecutive warning alerts to send
	// before each test message.
	sendWarningAlerts int
	// sendBogusAlertType, if true, causes a bogus alert of invalid type to
	// be sent before each test message.
	sendBogusAlertType bool
	// sendKeyUpdates is the number of consecutive key updates to send
	// before and after the test message.
	sendKeyUpdates int
	// keyUpdateRequest is the KeyUpdateRequest value to send in KeyUpdate messages.
	keyUpdateRequest byte
	// expectUnsolicitedKeyUpdate makes the test expect a one or more KeyUpdate
	// messages while reading data from the shim. Don't use this in combination
	// with any of the fields that send a KeyUpdate otherwise any received
	// KeyUpdate might not be as unsolicited as expected.
	expectUnsolicitedKeyUpdate bool
	// expectMessageDropped, if true, means the test message is expected to
	// be dropped by the client rather than echoed back.
	expectMessageDropped bool
	// expectPeerCertificate, if not nil, is the certificate chain the peer
	// is expected to send.
	expectPeerCertificate *Certificate
	// shimPrefix is the prefix that the shim will send to the server.
	shimPrefix string
	// resumeShimPrefix is the prefix that the shim will send to the server on a
	// resumption.
	resumeShimPrefix string
	// expectedQUICTransportParams contains the QUIC transport
	// parameters that are expected to be sent by the peer.
	expectedQUICTransportParams []byte
	// exportTrafficSecrets, if true, configures the test to export the TLS 1.3
	// traffic secrets and confirms that they match.
	exportTrafficSecrets bool
}

var testCases []testCase

func appendTranscript(path string, data []byte) error {
	if len(data) == 0 {
		return nil
	}

	settings, err := ioutil.ReadFile(path)
	if err != nil {
		if !os.IsNotExist(err) {
			return err
		}
		// If the shim aborted before writing a file, use a default
		// settings block, so the transcript is still somewhat valid.
		settings = []byte{0, 0} // kDataTag
	}

	settings = append(settings, data...)
	return ioutil.WriteFile(path, settings, 0644)
}

// A timeoutConn implements an idle timeout on each Read and Write operation.
type timeoutConn struct {
	net.Conn
	timeout time.Duration
}

func (t *timeoutConn) Read(b []byte) (int, error) {
	if !*useGDB {
		if err := t.SetReadDeadline(time.Now().Add(t.timeout)); err != nil {
			return 0, err
		}
	}
	return t.Conn.Read(b)
}

func (t *timeoutConn) Write(b []byte) (int, error) {
	if !*useGDB {
		if err := t.SetWriteDeadline(time.Now().Add(t.timeout)); err != nil {
			return 0, err
		}
	}
	return t.Conn.Write(b)
}

func doExchange(test *testCase, config *Config, conn net.Conn, isResume bool, transcripts *[][]byte, num int) error {
	if !test.noSessionCache {
		if config.ClientSessionCache == nil {
			config.ClientSessionCache = NewLRUClientSessionCache(1)
		}
		if config.ServerSessionCache == nil {
			config.ServerSessionCache = NewLRUServerSessionCache(1)
		}
	}
	if test.testType == clientTest {
		if len(config.Certificates) == 0 {
			config.Certificates = []Certificate{rsaCertificate}
		}
	} else {
		// Supply a ServerName to ensure a constant session cache key,
		// rather than falling back to net.Conn.RemoteAddr.
		if len(config.ServerName) == 0 {
			config.ServerName = "test"
		}
	}
	if *fuzzer {
		config.Bugs.NullAllCiphers = true
	}
	if *deterministic {
		config.Time = func() time.Time { return time.Unix(1234, 1234) }
	}

	conn = &timeoutConn{conn, *idleTimeout}

	if test.protocol == dtls {
		config.Bugs.PacketAdaptor = newPacketAdaptor(conn)
		conn = config.Bugs.PacketAdaptor
	}

	if *flagDebug || len(*transcriptDir) != 0 {
		local, peer := "client", "server"
		if test.testType == clientTest {
			local, peer = peer, local
		}
		connDebug := &recordingConn{
			Conn:       conn,
			isDatagram: test.protocol == dtls,
			local:      local,
			peer:       peer,
		}
		conn = connDebug
		if *flagDebug {
			defer connDebug.WriteTo(os.Stdout)
		}
		if len(*transcriptDir) != 0 {
			defer func() {
				if num == len(*transcripts) {
					*transcripts = append(*transcripts, connDebug.Transcript())
				} else {
					panic("transcripts are out of sync")
				}
			}()
		}

		if config.Bugs.PacketAdaptor != nil {
			config.Bugs.PacketAdaptor.debug = connDebug
		}
	}

	if test.replayWrites {
		conn = newReplayAdaptor(conn)
	}

	var connDamage *damageAdaptor
	if test.damageFirstWrite {
		connDamage = newDamageAdaptor(conn)
		conn = connDamage
	}

	if test.sendPrefix != "" {
		if _, err := conn.Write([]byte(test.sendPrefix)); err != nil {
			return err
		}
	}

	var tlsConn *Conn
	if test.testType == clientTest {
		if test.protocol == dtls {
			tlsConn = DTLSServer(conn, config)
		} else {
			tlsConn = Server(conn, config)
		}
	} else {
		config.InsecureSkipVerify = true
		if test.protocol == dtls {
			tlsConn = DTLSClient(conn, config)
		} else {
			tlsConn = Client(conn, config)
		}
	}
	defer tlsConn.Close()

	if err := tlsConn.Handshake(); err != nil {
		return err
	}

	// TODO(davidben): move all per-connection expectations into a dedicated
	// expectations struct that can be specified separately for the two
	// legs.
	expectedVersion := test.expectedVersion
	if isResume && test.expectedResumeVersion != 0 {
		expectedVersion = test.expectedResumeVersion
	}
	connState := tlsConn.ConnectionState()
	if vers := connState.Version; expectedVersion != 0 && vers != expectedVersion {
		return fmt.Errorf("got version %x, expected %x", vers, expectedVersion)
	}

	if cipher := connState.CipherSuite; test.expectedCipher != 0 && cipher != test.expectedCipher {
		return fmt.Errorf("got cipher %x, expected %x", cipher, test.expectedCipher)
	}
	if didResume := connState.DidResume; isResume && didResume == test.expectResumeRejected {
		return fmt.Errorf("didResume is %t, but we expected the opposite", didResume)
	}

	if test.expectChannelID {
		channelID := connState.ChannelID
		if channelID == nil {
			return fmt.Errorf("no channel ID negotiated")
		}
		if channelID.Curve != channelIDKey.Curve ||
			channelIDKey.X.Cmp(channelIDKey.X) != 0 ||
			channelIDKey.Y.Cmp(channelIDKey.Y) != 0 {
			return fmt.Errorf("incorrect channel ID")
		}
	} else if connState.ChannelID != nil {
		return fmt.Errorf("channel ID unexpectedly negotiated")
	}

	if test.expectTokenBinding {
		if !connState.TokenBindingNegotiated {
			return errors.New("no Token Binding negotiated")
		}
		if connState.TokenBindingParam != test.expectedTokenBindingParam {
			return fmt.Errorf("expected param %02x, but got %02x", test.expectedTokenBindingParam, connState.TokenBindingParam)
		}
	} else if connState.TokenBindingNegotiated {
		return errors.New("Token Binding unexpectedly negotiated")
	}

	if expected := test.expectedNextProto; expected != "" {
		if actual := connState.NegotiatedProtocol; actual != expected {
			return fmt.Errorf("next proto mismatch: got %s, wanted %s", actual, expected)
		}
	}

	if test.expectNoNextProto {
		if actual := connState.NegotiatedProtocol; actual != "" {
			return fmt.Errorf("got unexpected next proto %s", actual)
		}
	}

	if test.expectedNextProtoType != 0 {
		if (test.expectedNextProtoType == alpn) != connState.NegotiatedProtocolFromALPN {
			return fmt.Errorf("next proto type mismatch")
		}
	}

	if p := connState.SRTPProtectionProfile; p != test.expectedSRTPProtectionProfile {
		return fmt.Errorf("SRTP profile mismatch: got %d, wanted %d", p, test.expectedSRTPProtectionProfile)
	}

	if test.expectedOCSPResponse != nil && !bytes.Equal(test.expectedOCSPResponse, tlsConn.OCSPResponse()) {
		return fmt.Errorf("OCSP Response mismatch: got %x, wanted %x", tlsConn.OCSPResponse(), test.expectedOCSPResponse)
	}

	if test.expectedSCTList != nil && !bytes.Equal(test.expectedSCTList, connState.SCTList) {
		return fmt.Errorf("SCT list mismatch")
	}

	if expected := test.expectedPeerSignatureAlgorithm; expected != 0 && expected != connState.PeerSignatureAlgorithm {
		return fmt.Errorf("expected peer to use signature algorithm %04x, but got %04x", expected, connState.PeerSignatureAlgorithm)
	}

	if expected := test.expectedCurveID; expected != 0 && expected != connState.CurveID {
		return fmt.Errorf("expected peer to use curve %04x, but got %04x", expected, connState.CurveID)
	}

	if test.expectPeerCertificate != nil {
		if len(connState.PeerCertificates) != len(test.expectPeerCertificate.Certificate) {
			return fmt.Errorf("expected peer to send %d certificates, but got %d", len(connState.PeerCertificates), len(test.expectPeerCertificate.Certificate))
		}
		for i, cert := range connState.PeerCertificates {
			if !bytes.Equal(cert.Raw, test.expectPeerCertificate.Certificate[i]) {
				return fmt.Errorf("peer certificate %d did not match", i+1)
			}
		}
	}

	if len(test.expectedQUICTransportParams) > 0 {
		if !bytes.Equal(test.expectedQUICTransportParams, connState.QUICTransportParams) {
			return errors.New("Peer did not send expected QUIC transport params")
		}
	}

	if test.exportKeyingMaterial > 0 {
		actual := make([]byte, test.exportKeyingMaterial)
		if _, err := io.ReadFull(tlsConn, actual); err != nil {
			return err
		}
		expected, err := tlsConn.ExportKeyingMaterial(test.exportKeyingMaterial, []byte(test.exportLabel), []byte(test.exportContext), test.useExportContext)
		if err != nil {
			return err
		}
		if !bytes.Equal(actual, expected) {
			return fmt.Errorf("keying material mismatch; got %x, wanted %x", actual, expected)
		}
	}

	if test.exportTrafficSecrets {
		secretLenBytes := make([]byte, 2)
		if _, err := io.ReadFull(tlsConn, secretLenBytes); err != nil {
			return err
		}
		secretLen := binary.LittleEndian.Uint16(secretLenBytes)

		theirReadSecret := make([]byte, secretLen)
		theirWriteSecret := make([]byte, secretLen)
		if _, err := io.ReadFull(tlsConn, theirReadSecret); err != nil {
			return err
		}
		if _, err := io.ReadFull(tlsConn, theirWriteSecret); err != nil {
			return err
		}

		myReadSecret := tlsConn.in.trafficSecret
		myWriteSecret := tlsConn.out.trafficSecret
		if !bytes.Equal(myWriteSecret, theirReadSecret) {
			return fmt.Errorf("read traffic-secret mismatch; got %x, wanted %x", theirReadSecret, myWriteSecret)
		}
		if !bytes.Equal(myReadSecret, theirWriteSecret) {
			return fmt.Errorf("write traffic-secret mismatch; got %x, wanted %x", theirWriteSecret, myReadSecret)
		}
	}

	if test.testTLSUnique {
		var peersValue [12]byte
		if _, err := io.ReadFull(tlsConn, peersValue[:]); err != nil {
			return err
		}
		expected := tlsConn.ConnectionState().TLSUnique
		if !bytes.Equal(peersValue[:], expected) {
			return fmt.Errorf("tls-unique mismatch: peer sent %x, but %x was expected", peersValue[:], expected)
		}
	}

	if test.sendHalfHelloRequest {
		tlsConn.SendHalfHelloRequest()
	}

	shimPrefix := test.shimPrefix
	if isResume {
		shimPrefix = test.resumeShimPrefix
	}
	if test.shimWritesFirst || test.readWithUnfinishedWrite {
		shimPrefix = "hello"
	}
	if test.renegotiate > 0 {
		// If readWithUnfinishedWrite is set, the shim prefix will be
		// available later.
		if shimPrefix != "" && !test.readWithUnfinishedWrite {
			var buf = make([]byte, len(shimPrefix))
			_, err := io.ReadFull(tlsConn, buf)
			if err != nil {
				return err
			}
			if string(buf) != shimPrefix {
				return fmt.Errorf("bad initial message %v vs %v", string(buf), shimPrefix)
			}
			shimPrefix = ""
		}

		if test.renegotiateCiphers != nil {
			config.CipherSuites = test.renegotiateCiphers
		}
		for i := 0; i < test.renegotiate; i++ {
			if err := tlsConn.Renegotiate(); err != nil {
				return err
			}
		}
	} else if test.renegotiateCiphers != nil {
		panic("renegotiateCiphers without renegotiate")
	}

	if test.damageFirstWrite {
		connDamage.setDamage(true)
		tlsConn.Write([]byte("DAMAGED WRITE"))
		connDamage.setDamage(false)
	}

	messageLen := test.messageLen
	if messageLen < 0 {
		if test.protocol == dtls {
			return fmt.Errorf("messageLen < 0 not supported for DTLS tests")
		}
		// Read until EOF.
		_, err := io.Copy(ioutil.Discard, tlsConn)
		return err
	}
	if messageLen == 0 {
		messageLen = 32
	}

	messageCount := test.messageCount
	// shimShutsDown sets the default message count to zero.
	if messageCount == 0 && !test.shimShutsDown {
		messageCount = 1
	}

	for j := 0; j < messageCount; j++ {
		for i := 0; i < test.sendKeyUpdates; i++ {
			tlsConn.SendKeyUpdate(test.keyUpdateRequest)
		}

		for i := 0; i < test.sendEmptyRecords; i++ {
			tlsConn.Write(nil)
		}

		for i := 0; i < test.sendWarningAlerts; i++ {
			tlsConn.SendAlert(alertLevelWarning, alertUnexpectedMessage)
		}

		if test.sendBogusAlertType {
			tlsConn.SendAlert(0x42, alertUnexpectedMessage)
		}

		testMessage := make([]byte, messageLen)
		for i := range testMessage {
			testMessage[i] = 0x42 ^ byte(j)
		}
		tlsConn.Write(testMessage)

		// Consume the shim prefix if needed.
		if shimPrefix != "" {
			var buf = make([]byte, len(shimPrefix))
			_, err := io.ReadFull(tlsConn, buf)
			if err != nil {
				return err
			}
			if string(buf) != shimPrefix {
				return fmt.Errorf("bad initial message %v vs %v", string(buf), shimPrefix)
			}
			shimPrefix = ""
		}

		if test.shimShutsDown || test.expectMessageDropped {
			// The shim will not respond.
			continue
		}

		// Process the KeyUpdate ACK. However many KeyUpdates the runner
		// sends, the shim should respond only once.
		if test.sendKeyUpdates > 0 && test.keyUpdateRequest == keyUpdateRequested {
			if err := tlsConn.ReadKeyUpdateACK(); err != nil {
				return err
			}
		}

		buf := make([]byte, len(testMessage))
		if test.protocol == dtls {
			bufTmp := make([]byte, len(buf)+1)
			n, err := tlsConn.Read(bufTmp)
			if err != nil {
				return err
			}
			if config.Bugs.SplitAndPackAppData {
				m, err := tlsConn.Read(bufTmp[n:])
				if err != nil {
					return err
				}
				n += m
			}
			if n != len(buf) {
				return fmt.Errorf("bad reply; length mismatch (%d vs %d)", n, len(buf))
			}
			copy(buf, bufTmp)
		} else {
			_, err := io.ReadFull(tlsConn, buf)
			if err != nil {
				return err
			}
		}

		for i, v := range buf {
			if v != testMessage[i]^0xff {
				return fmt.Errorf("bad reply contents at byte %d; got %q and wanted %q", i, buf, testMessage)
			}
		}

		if seen := tlsConn.keyUpdateSeen; seen != test.expectUnsolicitedKeyUpdate {
			return fmt.Errorf("keyUpdateSeen (%t) != expectUnsolicitedKeyUpdate", seen)
		}
	}

	return nil
}

const xtermSize = "140x50"

func valgrindOf(dbAttach bool, path string, args ...string) *exec.Cmd {
	valgrindArgs := []string{"--error-exitcode=99", "--track-origins=yes", "--leak-check=full", "--quiet"}
	if dbAttach {
		valgrindArgs = append(valgrindArgs, "--db-attach=yes", "--db-command=xterm -geometry "+xtermSize+" -e gdb -nw %f %p")
	}
	valgrindArgs = append(valgrindArgs, path)
	valgrindArgs = append(valgrindArgs, args...)

	return exec.Command("valgrind", valgrindArgs...)
}

func gdbOf(path string, args ...string) *exec.Cmd {
	xtermArgs := []string{"-geometry", xtermSize, "-e", "gdb", "--args"}
	xtermArgs = append(xtermArgs, path)
	xtermArgs = append(xtermArgs, args...)

	return exec.Command("xterm", xtermArgs...)
}

func lldbOf(path string, args ...string) *exec.Cmd {
	xtermArgs := []string{"-geometry", xtermSize, "-e", "lldb", "--"}
	xtermArgs = append(xtermArgs, path)
	xtermArgs = append(xtermArgs, args...)

	return exec.Command("xterm", xtermArgs...)
}

func removeFirstLineIfSuffix(s, suffix string) string {
	idx := strings.IndexByte(s, '\n')
	if idx < 0 {
		return s
	}
	if strings.HasSuffix(s[:idx], suffix) {
		return s[idx+1:]
	}
	return s
}

var (
	errMoreMallocs   = errors.New("child process did not exhaust all allocation calls")
	errUnimplemented = errors.New("child process does not implement needed flags")
)

// accept accepts a connection from listener, unless waitChan signals a process
// exit first.
func acceptOrWait(listener *net.TCPListener, waitChan chan error) (net.Conn, error) {
	type connOrError struct {
		conn net.Conn
		err  error
	}
	connChan := make(chan connOrError, 1)
	go func() {
		if !*useGDB {
			listener.SetDeadline(time.Now().Add(*idleTimeout))
		}
		conn, err := listener.Accept()
		connChan <- connOrError{conn, err}
		close(connChan)
	}()
	select {
	case result := <-connChan:
		return result.conn, result.err
	case childErr := <-waitChan:
		waitChan <- childErr
		return nil, fmt.Errorf("child exited early: %s", childErr)
	}
}

func translateExpectedError(errorStr string) string {
	if translated, ok := shimConfig.ErrorMap[errorStr]; ok {
		return translated
	}

	if *looseErrors {
		return ""
	}

	return errorStr
}

func runTest(test *testCase, shimPath string, mallocNumToFail int64) error {
	// Help debugging panics on the Go side.
	defer func() {
		if r := recover(); r != nil {
			fmt.Fprintf(os.Stderr, "Test '%s' panicked.\n", test.name)
			panic(r)
		}
	}()

	if !test.shouldFail && (len(test.expectedError) > 0 || len(test.expectedLocalError) > 0) {
		panic("Error expected without shouldFail in " + test.name)
	}

	if test.expectResumeRejected && !test.resumeSession {
		panic("expectResumeRejected without resumeSession in " + test.name)
	}

	for _, ver := range tlsVersions {
		if !strings.Contains("-"+test.name+"-", "-"+ver.name+"-") {
			continue
		}

		if test.config.MaxVersion == 0 && test.config.MinVersion == 0 && test.expectedVersion == 0 {
			panic(fmt.Sprintf("The name of test %q suggests that it's version specific, but min/max version in the Config is %x/%x. One of them should probably be %x", test.name, test.config.MinVersion, test.config.MaxVersion, ver.version))
		}
	}

	listener, err := net.ListenTCP("tcp", &net.TCPAddr{IP: net.IPv6loopback})
	if err != nil {
		listener, err = net.ListenTCP("tcp4", &net.TCPAddr{IP: net.IP{127, 0, 0, 1}})
	}
	if err != nil {
		panic(err)
	}
	defer func() {
		if listener != nil {
			listener.Close()
		}
	}()

	flags := []string{"-port", strconv.Itoa(listener.Addr().(*net.TCPAddr).Port)}
	if test.testType == serverTest {
		flags = append(flags, "-server")

		flags = append(flags, "-key-file")
		if test.keyFile == "" {
			flags = append(flags, path.Join(*resourceDir, rsaKeyFile))
		} else {
			flags = append(flags, path.Join(*resourceDir, test.keyFile))
		}

		flags = append(flags, "-cert-file")
		if test.certFile == "" {
			flags = append(flags, path.Join(*resourceDir, rsaCertificateFile))
		} else {
			flags = append(flags, path.Join(*resourceDir, test.certFile))
		}
	}

	if test.protocol == dtls {
		flags = append(flags, "-dtls")
	}

	var resumeCount int
	if test.resumeSession {
		resumeCount++
		if test.resumeRenewedSession {
			resumeCount++
		}
	}

	if resumeCount > 0 {
		flags = append(flags, "-resume-count", strconv.Itoa(resumeCount))
	}

	if test.shimWritesFirst {
		flags = append(flags, "-shim-writes-first")
	}

	if test.readWithUnfinishedWrite {
		flags = append(flags, "-read-with-unfinished-write")
	}

	if test.shimShutsDown {
		flags = append(flags, "-shim-shuts-down")
	}

	if test.exportKeyingMaterial > 0 {
		flags = append(flags, "-export-keying-material", strconv.Itoa(test.exportKeyingMaterial))
		if test.useExportContext {
			flags = append(flags, "-use-export-context")
		}
	}
	if test.exportKeyingMaterial > 0 {
		flags = append(flags, "-export-label", test.exportLabel)
		flags = append(flags, "-export-context", test.exportContext)
	}

	if test.exportTrafficSecrets {
		flags = append(flags, "-export-traffic-secrets")
	}

	if test.expectResumeRejected {
		flags = append(flags, "-expect-session-miss")
	}

	if test.testTLSUnique {
		flags = append(flags, "-tls-unique")
	}

	flags = append(flags, "-handshaker-path", *handshakerPath)

	var transcriptPrefix string
	var transcripts [][]byte
	if len(*transcriptDir) != 0 {
		protocol := "tls"
		if test.protocol == dtls {
			protocol = "dtls"
		}

		side := "client"
		if test.testType == serverTest {
			side = "server"
		}

		dir := filepath.Join(*transcriptDir, protocol, side)
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
		transcriptPrefix = filepath.Join(dir, test.name+"-")
		flags = append(flags, "-write-settings", transcriptPrefix)
	}

	flags = append(flags, test.flags...)

	var shim *exec.Cmd
	if *useValgrind {
		shim = valgrindOf(false, shimPath, flags...)
	} else if *useGDB {
		shim = gdbOf(shimPath, flags...)
	} else if *useLLDB {
		shim = lldbOf(shimPath, flags...)
	} else {
		shim = exec.Command(shimPath, flags...)
	}
	shim.Stdin = os.Stdin
	var stdoutBuf, stderrBuf bytes.Buffer
	shim.Stdout = &stdoutBuf
	shim.Stderr = &stderrBuf
	if mallocNumToFail >= 0 {
		shim.Env = os.Environ()
		shim.Env = append(shim.Env, "MALLOC_NUMBER_TO_FAIL="+strconv.FormatInt(mallocNumToFail, 10))
		if *mallocTestDebug {
			shim.Env = append(shim.Env, "MALLOC_BREAK_ON_FAIL=1")
		}
		shim.Env = append(shim.Env, "_MALLOC_CHECK=1")
	}

	if err := shim.Start(); err != nil {
		panic(err)
	}
	waitChan := make(chan error, 1)
	go func() { waitChan <- shim.Wait() }()

	config := test.config

	if *deterministic {
		config.Rand = &deterministicRand{}
	}

	conn, err := acceptOrWait(listener, waitChan)
	if err == nil {
		err = doExchange(test, &config, conn, false /* not a resumption */, &transcripts, 0)
		conn.Close()
	}

	for i := 0; err == nil && i < resumeCount; i++ {
		var resumeConfig Config
		if test.resumeConfig != nil {
			resumeConfig = *test.resumeConfig
			if !test.newSessionsOnResume {
				resumeConfig.SessionTicketKey = config.SessionTicketKey
				resumeConfig.ClientSessionCache = config.ClientSessionCache
				resumeConfig.ServerSessionCache = config.ServerSessionCache
			}
			resumeConfig.Rand = config.Rand
		} else {
			resumeConfig = config
		}
		var connResume net.Conn
		connResume, err = acceptOrWait(listener, waitChan)
		if err == nil {
			err = doExchange(test, &resumeConfig, connResume, true /* resumption */, &transcripts, i+1)
			connResume.Close()
		}
	}

	// Close the listener now. This is to avoid hangs should the shim try to
	// open more connections than expected.
	listener.Close()
	listener = nil

	var childErr error
	if *useGDB {
		childErr = <-waitChan
	} else {
		waitTimeout := time.AfterFunc(*idleTimeout, func() {
			shim.Process.Kill()
		})
		childErr = <-waitChan
		waitTimeout.Stop()
	}

	// Now that the shim has exitted, all the settings files have been
	// written. Append the saved transcripts.
	for i, transcript := range transcripts {
		if err := appendTranscript(transcriptPrefix+strconv.Itoa(i), transcript); err != nil {
			return err
		}
	}

	var isValgrindError, mustFail bool
	if exitError, ok := childErr.(*exec.ExitError); ok {
		switch exitError.Sys().(syscall.WaitStatus).ExitStatus() {
		case 88:
			return errMoreMallocs
		case 89:
			return errUnimplemented
		case 90:
			mustFail = true
		case 99:
			isValgrindError = true
		}
	}

	// Account for Windows line endings.
	stdout := strings.Replace(string(stdoutBuf.Bytes()), "\r\n", "\n", -1)
	stderr := strings.Replace(string(stderrBuf.Bytes()), "\r\n", "\n", -1)

	// Work around an NDK / Android bug. The NDK r16 sometimes generates
	// binaries with the DF_1_PIE, which the runtime linker on Android N
	// complains about. The next NDK revision should work around this but,
	// in the meantime, strip its error out.
	//
	// https://github.com/android-ndk/ndk/issues/602
	// https://android-review.googlesource.com/c/platform/bionic/+/259790
	// https://android-review.googlesource.com/c/toolchain/binutils/+/571550
	//
	// Remove this after switching to the r17 NDK.
	stderr = removeFirstLineIfSuffix(stderr, ": unsupported flags DT_FLAGS_1=0x8000001")

	// Separate the errors from the shim and those from tools like
	// AddressSanitizer.
	var extraStderr string
	if stderrParts := strings.SplitN(stderr, "--- DONE ---\n", 2); len(stderrParts) == 2 {
		stderr = stderrParts[0]
		extraStderr = stderrParts[1]
	}

	failed := err != nil || childErr != nil
	expectedError := translateExpectedError(test.expectedError)
	correctFailure := len(expectedError) == 0 || strings.Contains(stderr, expectedError)

	localError := "none"
	if err != nil {
		localError = err.Error()
	}
	if len(test.expectedLocalError) != 0 {
		correctFailure = correctFailure && strings.Contains(localError, test.expectedLocalError)
	}

	if failed != test.shouldFail || failed && !correctFailure || mustFail {
		childError := "none"
		if childErr != nil {
			childError = childErr.Error()
		}

		var msg string
		switch {
		case failed && !test.shouldFail:
			msg = "unexpected failure"
		case !failed && test.shouldFail:
			msg = "unexpected success"
		case failed && !correctFailure:
			msg = "bad error (wanted '" + expectedError + "' / '" + test.expectedLocalError + "')"
		case mustFail:
			msg = "test failure"
		default:
			panic("internal error")
		}

		return fmt.Errorf("%s: local error '%s', child error '%s', stdout:\n%s\nstderr:\n%s\n%s", msg, localError, childError, stdout, stderr, extraStderr)
	}

	if len(extraStderr) > 0 || (!failed && len(stderr) > 0) {
		return fmt.Errorf("unexpected error output:\n%s\n%s", stderr, extraStderr)
	}

	if *useValgrind && isValgrindError {
		return fmt.Errorf("valgrind error:\n%s\n%s", stderr, extraStderr)
	}

	return nil
}

type tlsVersion struct {
	name string
	// version is the protocol version.
	version uint16
	// excludeFlag is the legacy shim flag to disable the version.
	excludeFlag string
	hasDTLS     bool
	// versionDTLS, if non-zero, is the DTLS-specific representation of the version.
	versionDTLS uint16
	// versionWire, if non-zero, is the wire representation of the
	// version. Otherwise the wire version is the protocol version or
	// versionDTLS.
	versionWire uint16
}

func (vers tlsVersion) shimFlag(protocol protocol) string {
	// The shim uses the protocol version in its public API, but uses the
	// DTLS-specific version if it exists.
	if protocol == dtls && vers.versionDTLS != 0 {
		return strconv.Itoa(int(vers.versionDTLS))
	}
	return strconv.Itoa(int(vers.version))
}

func (vers tlsVersion) wire(protocol protocol) uint16 {
	if protocol == dtls && vers.versionDTLS != 0 {
		return vers.versionDTLS
	}
	if vers.versionWire != 0 {
		return vers.versionWire
	}
	return vers.version
}

var tlsVersions = []tlsVersion{
	{
		name:        "TLS1",
		version:     VersionTLS10,
		excludeFlag: "-no-tls1",
		hasDTLS:     true,
		versionDTLS: VersionDTLS10,
	},
	{
		name:        "TLS11",
		version:     VersionTLS11,
		excludeFlag: "-no-tls11",
	},
	{
		name:        "TLS12",
		version:     VersionTLS12,
		excludeFlag: "-no-tls12",
		hasDTLS:     true,
		versionDTLS: VersionDTLS12,
	},
	{
		name:        "TLS13",
		version:     VersionTLS13,
		excludeFlag: "-no-tls13",
		versionWire: VersionTLS13,
	},
}

func allVersions(protocol protocol) []tlsVersion {
	if protocol == tls {
		return tlsVersions
	}

	var ret []tlsVersion
	for _, vers := range tlsVersions {
		if vers.hasDTLS {
			ret = append(ret, vers)
		}
	}
	return ret
}

type testCipherSuite struct {
	name string
	id   uint16
}

var testCipherSuites = []testCipherSuite{
	{"RSA_WITH_3DES_EDE_CBC_SHA", TLS_RSA_WITH_3DES_EDE_CBC_SHA},
	{"RSA_WITH_AES_128_GCM_SHA256", TLS_RSA_WITH_AES_128_GCM_SHA256},
	{"RSA_WITH_AES_128_CBC_SHA", TLS_RSA_WITH_AES_128_CBC_SHA},
	{"RSA_WITH_AES_256_GCM_SHA384", TLS_RSA_WITH_AES_256_GCM_SHA384},
	{"RSA_WITH_AES_256_CBC_SHA", TLS_RSA_WITH_AES_256_CBC_SHA},
	{"ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
	{"ECDHE_ECDSA_WITH_AES_128_CBC_SHA", TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA},
	{"ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384},
	{"ECDHE_ECDSA_WITH_AES_256_CBC_SHA", TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA},
	{"ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256},
	{"ECDHE_RSA_WITH_AES_128_GCM_SHA256", TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
	{"ECDHE_RSA_WITH_AES_128_CBC_SHA", TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
	{"ECDHE_RSA_WITH_AES_256_GCM_SHA384", TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384},
	{"ECDHE_RSA_WITH_AES_256_CBC_SHA", TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA},
	{"ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
	{"PSK_WITH_AES_128_CBC_SHA", TLS_PSK_WITH_AES_128_CBC_SHA},
	{"PSK_WITH_AES_256_CBC_SHA", TLS_PSK_WITH_AES_256_CBC_SHA},
	{"ECDHE_PSK_WITH_AES_128_CBC_SHA", TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA},
	{"ECDHE_PSK_WITH_AES_256_CBC_SHA", TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA},
	{"ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256", TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256},
	{"CHACHA20_POLY1305_SHA256", TLS_CHACHA20_POLY1305_SHA256},
	{"AES_128_GCM_SHA256", TLS_AES_128_GCM_SHA256},
	{"AES_256_GCM_SHA384", TLS_AES_256_GCM_SHA384},
	{"RSA_WITH_NULL_SHA", TLS_RSA_WITH_NULL_SHA},
}

func hasComponent(suiteName, component string) bool {
	return strings.Contains("_"+suiteName+"_", "_"+component+"_")
}

func isTLS12Only(suiteName string) bool {
	return hasComponent(suiteName, "GCM") ||
		hasComponent(suiteName, "SHA256") ||
		hasComponent(suiteName, "SHA384") ||
		hasComponent(suiteName, "POLY1305")
}

func isTLS13Suite(suiteName string) bool {
	return !hasComponent(suiteName, "WITH")
}

func bigFromHex(hex string) *big.Int {
	ret, ok := new(big.Int).SetString(hex, 16)
	if !ok {
		panic("failed to parse hex number 0x" + hex)
	}
	return ret
}

func convertToSplitHandshakeTests(tests []testCase) (splitHandshakeTests []testCase) {
	var stdout bytes.Buffer
	shim := exec.Command(*shimPath, "-is-handshaker-supported")
	shim.Stdout = &stdout
	if err := shim.Run(); err != nil {
		panic(err)
	}

	switch strings.TrimSpace(string(stdout.Bytes())) {
	case "No":
		return
	case "Yes":
		break
	default:
		panic("Unknown output from shim: 0x" + hex.EncodeToString(stdout.Bytes()))
	}

NextTest:
	for _, test := range tests {
		if test.protocol != tls ||
			test.testType != serverTest ||
			test.config.MaxVersion >= VersionTLS13 ||
			test.config.MaxVersion < VersionTLS10 ||
			(test.resumeConfig != nil && (test.resumeConfig.MaxVersion < VersionTLS10 || test.resumeConfig.MaxVersion >= VersionTLS13)) ||
			strings.HasPrefix(test.name, "VersionNegotiation-") {
			continue
		}

		for _, flag := range test.flags {
			if flag == "-implicit-handshake" {
				continue NextTest
			}
		}

		shTest := test
		shTest.name += "-Split"
		shTest.flags = make([]string, len(test.flags), len(test.flags)+1)
		copy(shTest.flags, test.flags)
		shTest.flags = append(shTest.flags, "-handoff")

		splitHandshakeTests = append(splitHandshakeTests, shTest)
	}

	return splitHandshakeTests
}

func addBasicTests() {
	basicTests := []testCase{
		{
			name: "NoFallbackSCSV",
			config: Config{
				Bugs: ProtocolBugs{
					FailIfNotFallbackSCSV: true,
				},
			},
			shouldFail:         true,
			expectedLocalError: "no fallback SCSV found",
		},
		{
			name: "SendFallbackSCSV",
			config: Config{
				Bugs: ProtocolBugs{
					FailIfNotFallbackSCSV: true,
				},
			},
			flags: []string{"-fallback-scsv"},
		},
		{
			name: "ClientCertificateTypes",
			config: Config{
				MaxVersion: VersionTLS12,
				ClientAuth: RequestClientCert,
				ClientCertificateTypes: []byte{
					CertTypeDSSSign,
					CertTypeRSASign,
					CertTypeECDSASign,
				},
			},
			flags: []string{
				"-expect-certificate-types",
				base64.StdEncoding.EncodeToString([]byte{
					CertTypeDSSSign,
					CertTypeRSASign,
					CertTypeECDSASign,
				}),
			},
		},
		{
			name: "UnauthenticatedECDH",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					UnauthenticatedECDH: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_MESSAGE:",
		},
		{
			name: "SkipCertificateStatus",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					SkipCertificateStatus: true,
				},
			},
			flags: []string{
				"-enable-ocsp-stapling",
				// This test involves an optional message. Test the message callback
				// trace to ensure we do not miss or double-report any.
				"-expect-msg-callback",
				`write hs 1
read hs 2
read hs 11
read hs 12
read hs 14
write hs 16
write ccs
write hs 20
read hs 4
read ccs
read hs 20
read alert 1 0
`,
			},
		},
		{
			protocol: dtls,
			name:     "SkipCertificateStatus-DTLS",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					SkipCertificateStatus: true,
				},
			},
			flags: []string{
				"-enable-ocsp-stapling",
				// This test involves an optional message. Test the message callback
				// trace to ensure we do not miss or double-report any.
				"-expect-msg-callback",
				`write hs 1
read hs 3
write hs 1
read hs 2
read hs 11
read hs 12
read hs 14
write hs 16
write ccs
write hs 20
read hs 4
read ccs
read hs 20
read alert 1 0
`,
			},
		},
		{
			name: "SkipServerKeyExchange",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					SkipServerKeyExchange: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_MESSAGE:",
		},
		{
			testType: serverTest,
			name:     "ServerSkipCertificateVerify",
			config: Config{
				MaxVersion:   VersionTLS12,
				Certificates: []Certificate{rsaChainCertificate},
				Bugs: ProtocolBugs{
					SkipCertificateVerify: true,
				},
			},
			expectPeerCertificate: &rsaChainCertificate,
			flags: []string{
				"-require-any-client-certificate",
			},
			shouldFail:         true,
			expectedError:      ":UNEXPECTED_RECORD:",
			expectedLocalError: "remote error: unexpected message",
		},
		{
			testType: serverTest,
			name:     "Alert",
			config: Config{
				Bugs: ProtocolBugs{
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":TLSV1_ALERT_RECORD_OVERFLOW:",
		},
		{
			protocol: dtls,
			testType: serverTest,
			name:     "Alert-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":TLSV1_ALERT_RECORD_OVERFLOW:",
		},
		{
			testType: serverTest,
			name:     "FragmentAlert",
			config: Config{
				Bugs: ProtocolBugs{
					FragmentAlert:     true,
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_ALERT:",
		},
		{
			protocol: dtls,
			testType: serverTest,
			name:     "FragmentAlert-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					FragmentAlert:     true,
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_ALERT:",
		},
		{
			testType: serverTest,
			name:     "DoubleAlert",
			config: Config{
				Bugs: ProtocolBugs{
					DoubleAlert:       true,
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_ALERT:",
		},
		{
			protocol: dtls,
			testType: serverTest,
			name:     "DoubleAlert-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					DoubleAlert:       true,
					SendSpuriousAlert: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_ALERT:",
		},
		{
			name: "SkipNewSessionTicket",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SkipNewSessionTicket: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			testType: serverTest,
			name:     "FallbackSCSV",
			config: Config{
				MaxVersion: VersionTLS11,
				Bugs: ProtocolBugs{
					SendFallbackSCSV: true,
				},
			},
			shouldFail:         true,
			expectedError:      ":INAPPROPRIATE_FALLBACK:",
			expectedLocalError: "remote error: inappropriate fallback",
		},
		{
			testType: serverTest,
			name:     "FallbackSCSV-VersionMatch-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendFallbackSCSV: true,
				},
			},
		},
		{
			testType: serverTest,
			name:     "FallbackSCSV-VersionMatch-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SendFallbackSCSV: true,
				},
			},
			flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
		},
		{
			testType: serverTest,
			name:     "FragmentedClientVersion",
			config: Config{
				Bugs: ProtocolBugs{
					MaxHandshakeRecordLength: 1,
					FragmentClientVersion:    true,
				},
			},
			expectedVersion: VersionTLS13,
		},
		{
			testType:      serverTest,
			name:          "HttpGET",
			sendPrefix:    "GET / HTTP/1.0\n",
			shouldFail:    true,
			expectedError: ":HTTP_REQUEST:",
		},
		{
			testType:      serverTest,
			name:          "HttpPOST",
			sendPrefix:    "POST / HTTP/1.0\n",
			shouldFail:    true,
			expectedError: ":HTTP_REQUEST:",
		},
		{
			testType:      serverTest,
			name:          "HttpHEAD",
			sendPrefix:    "HEAD / HTTP/1.0\n",
			shouldFail:    true,
			expectedError: ":HTTP_REQUEST:",
		},
		{
			testType:      serverTest,
			name:          "HttpPUT",
			sendPrefix:    "PUT / HTTP/1.0\n",
			shouldFail:    true,
			expectedError: ":HTTP_REQUEST:",
		},
		{
			testType:      serverTest,
			name:          "HttpCONNECT",
			sendPrefix:    "CONNECT www.google.com:443 HTTP/1.0\n",
			shouldFail:    true,
			expectedError: ":HTTPS_PROXY_REQUEST:",
		},
		{
			testType:      serverTest,
			name:          "Garbage",
			sendPrefix:    "blah",
			shouldFail:    true,
			expectedError: ":WRONG_VERSION_NUMBER:",
		},
		{
			name: "RSAEphemeralKey",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_RSA_WITH_AES_128_CBC_SHA},
				Bugs: ProtocolBugs{
					RSAEphemeralKey: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_MESSAGE:",
		},
		{
			name:          "DisableEverything",
			flags:         []string{"-no-tls13", "-no-tls12", "-no-tls11", "-no-tls1"},
			shouldFail:    true,
			expectedError: ":NO_SUPPORTED_VERSIONS_ENABLED:",
		},
		{
			protocol:      dtls,
			name:          "DisableEverything-DTLS",
			flags:         []string{"-no-tls12", "-no-tls1"},
			shouldFail:    true,
			expectedError: ":NO_SUPPORTED_VERSIONS_ENABLED:",
		},
		{
			protocol: dtls,
			testType: serverTest,
			name:     "MTU",
			config: Config{
				Bugs: ProtocolBugs{
					MaxPacketLength: 256,
				},
			},
			flags: []string{"-mtu", "256"},
		},
		{
			protocol: dtls,
			testType: serverTest,
			name:     "MTUExceeded",
			config: Config{
				Bugs: ProtocolBugs{
					MaxPacketLength: 255,
				},
			},
			flags:              []string{"-mtu", "256"},
			shouldFail:         true,
			expectedLocalError: "dtls: exceeded maximum packet length",
		},
		{
			name: "EmptyCertificateList",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					EmptyCertificateList: true,
				},
			},
			shouldFail:    true,
			expectedError: ":DECODE_ERROR:",
		},
		{
			name: "EmptyCertificateList-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					EmptyCertificateList: true,
				},
			},
			shouldFail:    true,
			expectedError: ":PEER_DID_NOT_RETURN_A_CERTIFICATE:",
		},
		{
			name:             "TLSFatalBadPackets",
			damageFirstWrite: true,
			shouldFail:       true,
			expectedError:    ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
		},
		{
			protocol:         dtls,
			name:             "DTLSIgnoreBadPackets",
			damageFirstWrite: true,
		},
		{
			protocol:         dtls,
			name:             "DTLSIgnoreBadPackets-Async",
			damageFirstWrite: true,
			flags:            []string{"-async"},
		},
		{
			name: "AppDataBeforeHandshake",
			config: Config{
				Bugs: ProtocolBugs{
					AppDataBeforeHandshake: []byte("TEST MESSAGE"),
				},
			},
			shouldFail:    true,
			expectedError: ":APPLICATION_DATA_INSTEAD_OF_HANDSHAKE:",
		},
		{
			name: "AppDataBeforeHandshake-Empty",
			config: Config{
				Bugs: ProtocolBugs{
					AppDataBeforeHandshake: []byte{},
				},
			},
			shouldFail:    true,
			expectedError: ":APPLICATION_DATA_INSTEAD_OF_HANDSHAKE:",
		},
		{
			protocol: dtls,
			name:     "AppDataBeforeHandshake-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					AppDataBeforeHandshake: []byte("TEST MESSAGE"),
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			protocol: dtls,
			name:     "AppDataBeforeHandshake-DTLS-Empty",
			config: Config{
				Bugs: ProtocolBugs{
					AppDataBeforeHandshake: []byte{},
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			name: "AppDataAfterChangeCipherSpec",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AppDataAfterChangeCipherSpec: []byte("TEST MESSAGE"),
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			name: "AppDataAfterChangeCipherSpec-Empty",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AppDataAfterChangeCipherSpec: []byte{},
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			protocol: dtls,
			name:     "AppDataAfterChangeCipherSpec-DTLS",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AppDataAfterChangeCipherSpec: []byte("TEST MESSAGE"),
				},
			},
			// BoringSSL's DTLS implementation will drop the out-of-order
			// application data.
		},
		{
			protocol: dtls,
			name:     "AppDataAfterChangeCipherSpec-DTLS-Empty",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AppDataAfterChangeCipherSpec: []byte{},
				},
			},
			// BoringSSL's DTLS implementation will drop the out-of-order
			// application data.
		},
		{
			name: "AlertAfterChangeCipherSpec",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AlertAfterChangeCipherSpec: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":TLSV1_ALERT_RECORD_OVERFLOW:",
		},
		{
			protocol: dtls,
			name:     "AlertAfterChangeCipherSpec-DTLS",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					AlertAfterChangeCipherSpec: alertRecordOverflow,
				},
			},
			shouldFail:    true,
			expectedError: ":TLSV1_ALERT_RECORD_OVERFLOW:",
		},
		{
			protocol: dtls,
			name:     "ReorderHandshakeFragments-Small-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					ReorderHandshakeFragments: true,
					// Small enough that every handshake message is
					// fragmented.
					MaxHandshakeRecordLength: 2,
				},
			},
		},
		{
			protocol: dtls,
			name:     "ReorderHandshakeFragments-Large-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					ReorderHandshakeFragments: true,
					// Large enough that no handshake message is
					// fragmented.
					MaxHandshakeRecordLength: 2048,
				},
			},
		},
		{
			protocol: dtls,
			name:     "MixCompleteMessageWithFragments-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					ReorderHandshakeFragments:       true,
					MixCompleteMessageWithFragments: true,
					MaxHandshakeRecordLength:        2,
				},
			},
		},
		{
			name: "SendInvalidRecordType",
			config: Config{
				Bugs: ProtocolBugs{
					SendInvalidRecordType: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			protocol: dtls,
			name:     "SendInvalidRecordType-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SendInvalidRecordType: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			name: "FalseStart-SkipServerSecondLeg",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					SkipNewSessionTicket: true,
					SkipChangeCipherSpec: true,
					SkipFinished:         true,
					ExpectFalseStart:     true,
				},
			},
			flags: []string{
				"-false-start",
				"-handshake-never-done",
				"-advertise-alpn", "\x03foo",
				"-expect-alpn", "foo",
			},
			shimWritesFirst: true,
			shouldFail:      true,
			expectedError:   ":APPLICATION_DATA_INSTEAD_OF_HANDSHAKE:",
		},
		{
			name: "FalseStart-SkipServerSecondLeg-Implicit",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					SkipNewSessionTicket: true,
					SkipChangeCipherSpec: true,
					SkipFinished:         true,
				},
			},
			flags: []string{
				"-implicit-handshake",
				"-false-start",
				"-handshake-never-done",
				"-advertise-alpn", "\x03foo",
			},
			shouldFail:    true,
			expectedError: ":APPLICATION_DATA_INSTEAD_OF_HANDSHAKE:",
		},
		{
			testType:           serverTest,
			name:               "FailEarlyCallback",
			flags:              []string{"-fail-early-callback"},
			shouldFail:         true,
			expectedError:      ":CONNECTION_REJECTED:",
			expectedLocalError: "remote error: handshake failure",
		},
		{
			name: "FailCertCallback-Client-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				ClientAuth: RequestClientCert,
			},
			flags:              []string{"-fail-cert-callback"},
			shouldFail:         true,
			expectedError:      ":CERT_CB_ERROR:",
			expectedLocalError: "remote error: internal error",
		},
		{
			testType: serverTest,
			name:     "FailCertCallback-Server-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			flags:              []string{"-fail-cert-callback"},
			shouldFail:         true,
			expectedError:      ":CERT_CB_ERROR:",
			expectedLocalError: "remote error: internal error",
		},
		{
			name: "FailCertCallback-Client-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				ClientAuth: RequestClientCert,
			},
			flags:              []string{"-fail-cert-callback"},
			shouldFail:         true,
			expectedError:      ":CERT_CB_ERROR:",
			expectedLocalError: "remote error: internal error",
		},
		{
			testType: serverTest,
			name:     "FailCertCallback-Server-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			flags:              []string{"-fail-cert-callback"},
			shouldFail:         true,
			expectedError:      ":CERT_CB_ERROR:",
			expectedLocalError: "remote error: internal error",
		},
		{
			protocol: dtls,
			name:     "FragmentMessageTypeMismatch-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					MaxHandshakeRecordLength:    2,
					FragmentMessageTypeMismatch: true,
				},
			},
			shouldFail:    true,
			expectedError: ":FRAGMENT_MISMATCH:",
		},
		{
			protocol: dtls,
			name:     "FragmentMessageLengthMismatch-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					MaxHandshakeRecordLength:      2,
					FragmentMessageLengthMismatch: true,
				},
			},
			shouldFail:    true,
			expectedError: ":FRAGMENT_MISMATCH:",
		},
		{
			protocol: dtls,
			name:     "SplitFragments-Header-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SplitFragments: 2,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_HANDSHAKE_RECORD:",
		},
		{
			protocol: dtls,
			name:     "SplitFragments-Boundary-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SplitFragments: dtlsRecordHeaderLen,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_HANDSHAKE_RECORD:",
		},
		{
			protocol: dtls,
			name:     "SplitFragments-Body-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SplitFragments: dtlsRecordHeaderLen + 1,
				},
			},
			shouldFail:    true,
			expectedError: ":BAD_HANDSHAKE_RECORD:",
		},
		{
			protocol: dtls,
			name:     "SendEmptyFragments-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SendEmptyFragments: true,
				},
			},
		},
		{
			testType: serverTest,
			protocol: dtls,
			name:     "SendEmptyFragments-Padded-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					// Test empty fragments for a message with a
					// nice power-of-two length.
					PadClientHello:     64,
					SendEmptyFragments: true,
				},
			},
		},
		{
			name: "BadFinished-Client",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					BadFinished: true,
				},
			},
			shouldFail:    true,
			expectedError: ":DIGEST_CHECK_FAILED:",
		},
		{
			name: "BadFinished-Client-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					BadFinished: true,
				},
			},
			shouldFail:    true,
			expectedError: ":DIGEST_CHECK_FAILED:",
		},
		{
			testType: serverTest,
			name:     "BadFinished-Server",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					BadFinished: true,
				},
			},
			shouldFail:    true,
			expectedError: ":DIGEST_CHECK_FAILED:",
		},
		{
			testType: serverTest,
			name:     "BadFinished-Server-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					BadFinished: true,
				},
			},
			shouldFail:    true,
			expectedError: ":DIGEST_CHECK_FAILED:",
		},
		{
			name: "FalseStart-BadFinished",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					BadFinished:      true,
					ExpectFalseStart: true,
				},
			},
			flags: []string{
				"-false-start",
				"-handshake-never-done",
				"-advertise-alpn", "\x03foo",
				"-expect-alpn", "foo",
			},
			shimWritesFirst: true,
			shouldFail:      true,
			expectedError:   ":DIGEST_CHECK_FAILED:",
		},
		{
			name: "NoFalseStart-NoALPN",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					ExpectFalseStart:          true,
					AlertBeforeFalseStartTest: alertAccessDenied,
				},
			},
			flags: []string{
				"-false-start",
			},
			shimWritesFirst:    true,
			shouldFail:         true,
			expectedError:      ":TLSV1_ALERT_ACCESS_DENIED:",
			expectedLocalError: "tls: peer did not false start: EOF",
		},
		{
			name: "FalseStart-NoALPNAllowed",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				Bugs: ProtocolBugs{
					ExpectFalseStart: true,
				},
			},
			flags: []string{
				"-false-start",
				"-allow-false-start-without-alpn",
			},
			shimWritesFirst: true,
		},
		{
			name: "NoFalseStart-NoAEAD",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					ExpectFalseStart:          true,
					AlertBeforeFalseStartTest: alertAccessDenied,
				},
			},
			flags: []string{
				"-false-start",
				"-advertise-alpn", "\x03foo",
			},
			shimWritesFirst:    true,
			shouldFail:         true,
			expectedError:      ":TLSV1_ALERT_ACCESS_DENIED:",
			expectedLocalError: "tls: peer did not false start: EOF",
		},
		{
			name: "NoFalseStart-RSA",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					ExpectFalseStart:          true,
					AlertBeforeFalseStartTest: alertAccessDenied,
				},
			},
			flags: []string{
				"-false-start",
				"-advertise-alpn", "\x03foo",
			},
			shimWritesFirst:    true,
			shouldFail:         true,
			expectedError:      ":TLSV1_ALERT_ACCESS_DENIED:",
			expectedLocalError: "tls: peer did not false start: EOF",
		},
		{
			protocol: dtls,
			name:     "SendSplitAlert-Sync",
			config: Config{
				Bugs: ProtocolBugs{
					SendSplitAlert: true,
				},
			},
		},
		{
			protocol: dtls,
			name:     "SendSplitAlert-Async",
			config: Config{
				Bugs: ProtocolBugs{
					SendSplitAlert: true,
				},
			},
			flags: []string{"-async"},
		},
		{
			name:             "SendEmptyRecords-Pass",
			sendEmptyRecords: 32,
		},
		{
			name:             "SendEmptyRecords",
			sendEmptyRecords: 33,
			shouldFail:       true,
			expectedError:    ":TOO_MANY_EMPTY_FRAGMENTS:",
		},
		{
			name:             "SendEmptyRecords-Async",
			sendEmptyRecords: 33,
			flags:            []string{"-async"},
			shouldFail:       true,
			expectedError:    ":TOO_MANY_EMPTY_FRAGMENTS:",
		},
		{
			name: "SendWarningAlerts-Pass",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			sendWarningAlerts: 4,
		},
		{
			protocol: dtls,
			name:     "SendWarningAlerts-DTLS-Pass",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			sendWarningAlerts: 4,
		},
		{
			name: "SendWarningAlerts-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			sendWarningAlerts:  4,
			shouldFail:         true,
			expectedError:      ":BAD_ALERT:",
			expectedLocalError: "remote error: error decoding message",
		},
		{
			name: "SendWarningAlerts",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			sendWarningAlerts: 5,
			shouldFail:        true,
			expectedError:     ":TOO_MANY_WARNING_ALERTS:",
		},
		{
			name: "SendWarningAlerts-Async",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			sendWarningAlerts: 5,
			flags:             []string{"-async"},
			shouldFail:        true,
			expectedError:     ":TOO_MANY_WARNING_ALERTS:",
		},
		{
			name:               "SendBogusAlertType",
			sendBogusAlertType: true,
			shouldFail:         true,
			expectedError:      ":UNKNOWN_ALERT_TYPE:",
			expectedLocalError: "remote error: illegal parameter",
		},
		{
			protocol:           dtls,
			name:               "SendBogusAlertType-DTLS",
			sendBogusAlertType: true,
			shouldFail:         true,
			expectedError:      ":UNKNOWN_ALERT_TYPE:",
			expectedLocalError: "remote error: illegal parameter",
		},
		{
			name: "TooManyKeyUpdates",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			sendKeyUpdates:   33,
			keyUpdateRequest: keyUpdateNotRequested,
			shouldFail:       true,
			expectedError:    ":TOO_MANY_KEY_UPDATES:",
		},
		{
			name: "EmptySessionID",
			config: Config{
				MaxVersion:             VersionTLS12,
				SessionTicketsDisabled: true,
			},
			noSessionCache: true,
			flags:          []string{"-expect-no-session"},
		},
		{
			name: "Unclean-Shutdown",
			config: Config{
				Bugs: ProtocolBugs{
					NoCloseNotify:     true,
					ExpectCloseNotify: true,
				},
			},
			shimShutsDown: true,
			flags:         []string{"-check-close-notify"},
			shouldFail:    true,
			expectedError: "Unexpected SSL_shutdown result: -1 != 1",
		},
		{
			name: "Unclean-Shutdown-Ignored",
			config: Config{
				Bugs: ProtocolBugs{
					NoCloseNotify: true,
				},
			},
			shimShutsDown: true,
		},
		{
			name: "Unclean-Shutdown-Alert",
			config: Config{
				Bugs: ProtocolBugs{
					SendAlertOnShutdown: alertDecompressionFailure,
					ExpectCloseNotify:   true,
				},
			},
			shimShutsDown: true,
			flags:         []string{"-check-close-notify"},
			shouldFail:    true,
			expectedError: ":SSLV3_ALERT_DECOMPRESSION_FAILURE:",
		},
		{
			name: "LargePlaintext",
			config: Config{
				Bugs: ProtocolBugs{
					SendLargeRecords: true,
				},
			},
			messageLen:         maxPlaintext + 1,
			shouldFail:         true,
			expectedError:      ":DATA_LENGTH_TOO_LONG:",
			expectedLocalError: "remote error: record overflow",
		},
		{
			protocol: dtls,
			name:     "LargePlaintext-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SendLargeRecords: true,
				},
			},
			messageLen:         maxPlaintext + 1,
			shouldFail:         true,
			expectedError:      ":DATA_LENGTH_TOO_LONG:",
			expectedLocalError: "remote error: record overflow",
		},
		{
			name: "LargePlaintext-TLS13-Padded-8192-8192",
			config: Config{
				MinVersion: VersionTLS13,
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RecordPadding:    8192,
					SendLargeRecords: true,
				},
			},
			messageLen: 8192,
		},
		{
			name: "LargePlaintext-TLS13-Padded-8193-8192",
			config: Config{
				MinVersion: VersionTLS13,
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RecordPadding:    8193,
					SendLargeRecords: true,
				},
			},
			messageLen:         8192,
			shouldFail:         true,
			expectedError:      ":DATA_LENGTH_TOO_LONG:",
			expectedLocalError: "remote error: record overflow",
		},
		{
			name: "LargePlaintext-TLS13-Padded-16383-1",
			config: Config{
				MinVersion: VersionTLS13,
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RecordPadding:    1,
					SendLargeRecords: true,
				},
			},
			messageLen: 16383,
		},
		{
			name: "LargePlaintext-TLS13-Padded-16384-1",
			config: Config{
				MinVersion: VersionTLS13,
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RecordPadding:    1,
					SendLargeRecords: true,
				},
			},
			messageLen:         16384,
			shouldFail:         true,
			expectedError:      ":DATA_LENGTH_TOO_LONG:",
			expectedLocalError: "remote error: record overflow",
		},
		{
			name: "LargeCiphertext",
			config: Config{
				Bugs: ProtocolBugs{
					SendLargeRecords: true,
				},
			},
			messageLen:    maxPlaintext * 2,
			shouldFail:    true,
			expectedError: ":ENCRYPTED_LENGTH_TOO_LONG:",
		},
		{
			protocol: dtls,
			name:     "LargeCiphertext-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SendLargeRecords: true,
				},
			},
			messageLen: maxPlaintext * 2,
			// Unlike the other four cases, DTLS drops records which
			// are invalid before authentication, so the connection
			// does not fail.
			expectMessageDropped: true,
		},
		{
			name:        "BadHelloRequest-1",
			renegotiate: 1,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					BadHelloRequest: []byte{typeHelloRequest, 0, 0, 1, 1},
				},
			},
			flags: []string{
				"-renegotiate-freely",
				"-expect-total-renegotiations", "1",
			},
			shouldFail:    true,
			expectedError: ":BAD_HELLO_REQUEST:",
		},
		{
			name:        "BadHelloRequest-2",
			renegotiate: 1,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					BadHelloRequest: []byte{typeServerKeyExchange, 0, 0, 0},
				},
			},
			flags: []string{
				"-renegotiate-freely",
				"-expect-total-renegotiations", "1",
			},
			shouldFail:    true,
			expectedError: ":BAD_HELLO_REQUEST:",
		},
		{
			testType: serverTest,
			name:     "SupportTicketsWithSessionID",
			config: Config{
				MaxVersion:             VersionTLS12,
				SessionTicketsDisabled: true,
			},
			resumeConfig: &Config{
				MaxVersion: VersionTLS12,
			},
			resumeSession: true,
		},
		{
			protocol: dtls,
			name:     "DTLS-SendExtraFinished",
			config: Config{
				Bugs: ProtocolBugs{
					SendExtraFinished: true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			protocol: dtls,
			name:     "DTLS-SendExtraFinished-Reordered",
			config: Config{
				Bugs: ProtocolBugs{
					MaxHandshakeRecordLength:  2,
					ReorderHandshakeFragments: true,
					SendExtraFinished:         true,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		},
		{
			testType: serverTest,
			name:     "V2ClientHello-EmptyRecordPrefix",
			config: Config{
				// Choose a cipher suite that does not involve
				// elliptic curves, so no extensions are
				// involved.
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
				Bugs: ProtocolBugs{
					SendV2ClientHello: true,
				},
			},
			sendPrefix: string([]byte{
				byte(recordTypeHandshake),
				3, 1, // version
				0, 0, // length
			}),
			// A no-op empty record may not be sent before V2ClientHello.
			shouldFail:    true,
			expectedError: ":WRONG_VERSION_NUMBER:",
		},
		{
			testType: serverTest,
			name:     "V2ClientHello-WarningAlertPrefix",
			config: Config{
				// Choose a cipher suite that does not involve
				// elliptic curves, so no extensions are
				// involved.
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
				Bugs: ProtocolBugs{
					SendV2ClientHello: true,
				},
			},
			sendPrefix: string([]byte{
				byte(recordTypeAlert),
				3, 1, // version
				0, 2, // length
				alertLevelWarning, byte(alertDecompressionFailure),
			}),
			// A no-op warning alert may not be sent before V2ClientHello.
			shouldFail:    true,
			expectedError: ":WRONG_VERSION_NUMBER:",
		},
		{
			name: "KeyUpdate-ToClient",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			sendKeyUpdates:   1,
			keyUpdateRequest: keyUpdateNotRequested,
		},
		{
			testType: serverTest,
			name:     "KeyUpdate-ToServer",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			sendKeyUpdates:   1,
			keyUpdateRequest: keyUpdateNotRequested,
		},
		{
			name: "KeyUpdate-FromClient",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			expectUnsolicitedKeyUpdate: true,
			flags:                      []string{"-key-update"},
		},
		{
			testType: serverTest,
			name:     "KeyUpdate-FromServer",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			expectUnsolicitedKeyUpdate: true,
			flags:                      []string{"-key-update"},
		},
		{
			name: "KeyUpdate-InvalidRequestMode",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			sendKeyUpdates:   1,
			keyUpdateRequest: 42,
			shouldFail:       true,
			expectedError:    ":DECODE_ERROR:",
		},
		{
			// Test that KeyUpdates are acknowledged properly.
			name: "KeyUpdate-RequestACK",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RejectUnsolicitedKeyUpdate: true,
				},
			},
			// Test the shim receiving many KeyUpdates in a row.
			sendKeyUpdates:   5,
			messageCount:     5,
			keyUpdateRequest: keyUpdateRequested,
		},
		{
			// Test that KeyUpdates are acknowledged properly if the
			// peer's KeyUpdate is discovered while a write is
			// pending.
			name: "KeyUpdate-RequestACK-UnfinishedWrite",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					RejectUnsolicitedKeyUpdate: true,
				},
			},
			// Test the shim receiving many KeyUpdates in a row.
			sendKeyUpdates:          5,
			messageCount:            5,
			keyUpdateRequest:        keyUpdateRequested,
			readWithUnfinishedWrite: true,
			flags:                   []string{"-async"},
		},
		{
			name: "SendSNIWarningAlert",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SendSNIWarningAlert: true,
				},
			},
		},
		{
			testType: serverTest,
			name:     "ExtraCompressionMethods-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SendCompressionMethods: []byte{1, 2, 3, compressionNone, 4, 5, 6},
				},
			},
		},
		{
			testType: serverTest,
			name:     "ExtraCompressionMethods-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendCompressionMethods: []byte{1, 2, 3, compressionNone, 4, 5, 6},
				},
			},
			shouldFail:         true,
			expectedError:      ":INVALID_COMPRESSION_LIST:",
			expectedLocalError: "remote error: illegal parameter",
		},
		{
			testType: serverTest,
			name:     "NoNullCompression-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SendCompressionMethods: []byte{1, 2, 3, 4, 5, 6},
				},
			},
			shouldFail:         true,
			expectedError:      ":INVALID_COMPRESSION_LIST:",
			expectedLocalError: "remote error: illegal parameter",
		},
		{
			testType: serverTest,
			name:     "NoNullCompression-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendCompressionMethods: []byte{1, 2, 3, 4, 5, 6},
				},
			},
			shouldFail:         true,
			expectedError:      ":INVALID_COMPRESSION_LIST:",
			expectedLocalError: "remote error: illegal parameter",
		},
		// Test that the client rejects invalid compression methods
		// from the server.
		{
			testType: clientTest,
			name:     "InvalidCompressionMethod",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SendCompressionMethod: 1,
				},
			},
			shouldFail:         true,
			expectedError:      ":UNSUPPORTED_COMPRESSION_ALGORITHM:",
			expectedLocalError: "remote error: illegal parameter",
		},
		{
			testType: clientTest,
			name:     "TLS13-InvalidCompressionMethod",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendCompressionMethod: 1,
				},
			},
			shouldFail:    true,
			expectedError: ":DECODE_ERROR:",
		},
		{
			testType: clientTest,
			name:     "TLS13-HRR-InvalidCompressionMethod",
			config: Config{
				MaxVersion:       VersionTLS13,
				CurvePreferences: []CurveID{CurveP384},
				Bugs: ProtocolBugs{
					SendCompressionMethod: 1,
				},
			},
			shouldFail:         true,
			expectedError:      ":DECODE_ERROR:",
			expectedLocalError: "remote error: error decoding message",
		},
		{
			name: "GREASE-Client-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					ExpectGREASE: true,
				},
			},
			flags: []string{"-enable-grease"},
		},
		{
			name: "GREASE-Client-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					ExpectGREASE: true,
				},
			},
			flags: []string{"-enable-grease"},
		},
		{
			testType: serverTest,
			name:     "GREASE-Server-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					// TLS 1.3 servers are expected to
					// always enable GREASE. TLS 1.3 is new,
					// so there is no existing ecosystem to
					// worry about.
					ExpectGREASE: true,
				},
			},
		},
		{
			// Test the TLS 1.2 server so there is a large
			// unencrypted certificate as well as application data.
			testType: serverTest,
			name:     "MaxSendFragment-TLS12",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					MaxReceivePlaintext: 512,
				},
			},
			messageLen: 1024,
			flags: []string{
				"-max-send-fragment", "512",
				"-read-size", "1024",
			},
		},
		{
			// Test the TLS 1.2 server so there is a large
			// unencrypted certificate as well as application data.
			testType: serverTest,
			name:     "MaxSendFragment-TLS12-TooLarge",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					// Ensure that some of the records are
					// 512.
					MaxReceivePlaintext: 511,
				},
			},
			messageLen: 1024,
			flags: []string{
				"-max-send-fragment", "512",
				"-read-size", "1024",
			},
			shouldFail:         true,
			expectedLocalError: "local error: record overflow",
		},
		{
			// Test the TLS 1.3 server so there is a large encrypted
			// certificate as well as application data.
			testType: serverTest,
			name:     "MaxSendFragment-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					MaxReceivePlaintext:            512,
					ExpectPackedEncryptedHandshake: 512,
				},
			},
			messageLen: 1024,
			flags: []string{
				"-max-send-fragment", "512",
				"-read-size", "1024",
			},
		},
		{
			// Test the TLS 1.3 server so there is a large encrypted
			// certificate as well as application data.
			testType: serverTest,
			name:     "MaxSendFragment-TLS13-TooLarge",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					// Ensure that some of the records are
					// 512.
					MaxReceivePlaintext: 511,
				},
			},
			messageLen: 1024,
			flags: []string{
				"-max-send-fragment", "512",
				"-read-size", "1024",
			},
			shouldFail:         true,
			expectedLocalError: "local error: record overflow",
		},
		{
			// Test that handshake data is tightly packed in TLS 1.3.
			testType: serverTest,
			name:     "PackedEncryptedHandshake-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					ExpectPackedEncryptedHandshake: 16384,
				},
			},
		},
		{
			// Test that DTLS can handle multiple application data
			// records in a single packet.
			protocol: dtls,
			name:     "SplitAndPackAppData-DTLS",
			config: Config{
				Bugs: ProtocolBugs{
					SplitAndPackAppData: true,
				},
			},
		},
		{
			protocol: dtls,
			name:     "SplitAndPackAppData-DTLS-Async",
			config: Config{
				Bugs: ProtocolBugs{
					SplitAndPackAppData: true,
				},
			},
			flags: []string{"-async"},
		},
	}
	testCases = append(testCases, basicTests...)

	// Test that very large messages can be received.
	cert := rsaCertificate
	for i := 0; i < 50; i++ {
		cert.Certificate = append(cert.Certificate, cert.Certificate[0])
	}
	testCases = append(testCases, testCase{
		name: "LargeMessage",
		config: Config{
			Certificates: []Certificate{cert},
		},
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "LargeMessage-DTLS",
		config: Config{
			Certificates: []Certificate{cert},
		},
	})

	// They are rejected if the maximum certificate chain length is capped.
	testCases = append(testCases, testCase{
		name: "LargeMessage-Reject",
		config: Config{
			Certificates: []Certificate{cert},
		},
		flags:         []string{"-max-cert-list", "16384"},
		shouldFail:    true,
		expectedError: ":EXCESSIVE_MESSAGE_SIZE:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "LargeMessage-Reject-DTLS",
		config: Config{
			Certificates: []Certificate{cert},
		},
		flags:         []string{"-max-cert-list", "16384"},
		shouldFail:    true,
		expectedError: ":EXCESSIVE_MESSAGE_SIZE:",
	})

	// Servers echoing the TLS 1.3 compatibility mode session ID should be
	// rejected.
	testCases = append(testCases, testCase{
		name: "EchoTLS13CompatibilitySessionID",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				EchoSessionIDInFullHandshake: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":SERVER_ECHOED_INVALID_SESSION_ID:",
		expectedLocalError: "remote error: illegal parameter",
	})
}

func addTestForCipherSuite(suite testCipherSuite, ver tlsVersion, protocol protocol) {
	const psk = "12345"
	const pskIdentity = "luggage combo"

	var prefix string
	if protocol == dtls {
		if !ver.hasDTLS {
			return
		}
		prefix = "D"
	}

	var cert Certificate
	var certFile string
	var keyFile string
	if hasComponent(suite.name, "ECDSA") {
		cert = ecdsaP256Certificate
		certFile = ecdsaP256CertificateFile
		keyFile = ecdsaP256KeyFile
	} else {
		cert = rsaCertificate
		certFile = rsaCertificateFile
		keyFile = rsaKeyFile
	}

	var flags []string
	if hasComponent(suite.name, "PSK") {
		flags = append(flags,
			"-psk", psk,
			"-psk-identity", pskIdentity)
	}
	if hasComponent(suite.name, "NULL") {
		// NULL ciphers must be explicitly enabled.
		flags = append(flags, "-cipher", "DEFAULT:NULL-SHA")
	}

	var shouldFail bool
	if isTLS12Only(suite.name) && ver.version < VersionTLS12 {
		shouldFail = true
	}
	if !isTLS13Suite(suite.name) && ver.version >= VersionTLS13 {
		shouldFail = true
	}
	if isTLS13Suite(suite.name) && ver.version < VersionTLS13 {
		shouldFail = true
	}

	var sendCipherSuite uint16
	var expectedServerError, expectedClientError string
	serverCipherSuites := []uint16{suite.id}
	if shouldFail {
		expectedServerError = ":NO_SHARED_CIPHER:"
		expectedClientError = ":WRONG_CIPHER_RETURNED:"
		// Configure the server to select ciphers as normal but
		// select an incompatible cipher in ServerHello.
		serverCipherSuites = nil
		sendCipherSuite = suite.id
	}

	// Verify exporters interoperate.
	exportKeyingMaterial := 1024

	testCases = append(testCases, testCase{
		testType: serverTest,
		protocol: protocol,
		name:     prefix + ver.name + "-" + suite.name + "-server",
		config: Config{
			MinVersion:           ver.version,
			MaxVersion:           ver.version,
			CipherSuites:         []uint16{suite.id},
			Certificates:         []Certificate{cert},
			PreSharedKey:         []byte(psk),
			PreSharedKeyIdentity: pskIdentity,
			Bugs: ProtocolBugs{
				AdvertiseAllConfiguredCiphers: true,
			},
		},
		certFile:             certFile,
		keyFile:              keyFile,
		flags:                flags,
		resumeSession:        true,
		shouldFail:           shouldFail,
		expectedError:        expectedServerError,
		exportKeyingMaterial: exportKeyingMaterial,
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		protocol: protocol,
		name:     prefix + ver.name + "-" + suite.name + "-client",
		config: Config{
			MinVersion:           ver.version,
			MaxVersion:           ver.version,
			CipherSuites:         serverCipherSuites,
			Certificates:         []Certificate{cert},
			PreSharedKey:         []byte(psk),
			PreSharedKeyIdentity: pskIdentity,
			Bugs: ProtocolBugs{
				IgnorePeerCipherPreferences: shouldFail,
				SendCipherSuite:             sendCipherSuite,
			},
		},
		flags:                flags,
		resumeSession:        true,
		shouldFail:           shouldFail,
		expectedError:        expectedClientError,
		exportKeyingMaterial: exportKeyingMaterial,
	})

	if shouldFail {
		return
	}

	// Ensure the maximum record size is accepted.
	testCases = append(testCases, testCase{
		protocol: protocol,
		name:     prefix + ver.name + "-" + suite.name + "-LargeRecord",
		config: Config{
			MinVersion:           ver.version,
			MaxVersion:           ver.version,
			CipherSuites:         []uint16{suite.id},
			Certificates:         []Certificate{cert},
			PreSharedKey:         []byte(psk),
			PreSharedKeyIdentity: pskIdentity,
		},
		flags:      flags,
		messageLen: maxPlaintext,
	})

	// Test bad records for all ciphers. Bad records are fatal in TLS
	// and ignored in DTLS.
	shouldFail = protocol == tls
	var expectedError string
	if shouldFail {
		expectedError = ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:"
	}

	testCases = append(testCases, testCase{
		protocol: protocol,
		name:     prefix + ver.name + "-" + suite.name + "-BadRecord",
		config: Config{
			MinVersion:           ver.version,
			MaxVersion:           ver.version,
			CipherSuites:         []uint16{suite.id},
			Certificates:         []Certificate{cert},
			PreSharedKey:         []byte(psk),
			PreSharedKeyIdentity: pskIdentity,
		},
		flags:            flags,
		damageFirstWrite: true,
		messageLen:       maxPlaintext,
		shouldFail:       shouldFail,
		expectedError:    expectedError,
	})
}

func addCipherSuiteTests() {
	const bogusCipher = 0xfe00

	for _, suite := range testCipherSuites {
		for _, ver := range tlsVersions {
			for _, protocol := range []protocol{tls, dtls} {
				addTestForCipherSuite(suite, ver, protocol)
			}
		}
	}

	testCases = append(testCases, testCase{
		name: "NoSharedCipher",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{},
		},
		shouldFail:    true,
		expectedError: ":HANDSHAKE_FAILURE_ON_CLIENT_HELLO:",
	})

	testCases = append(testCases, testCase{
		name: "NoSharedCipher-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{},
		},
		shouldFail:    true,
		expectedError: ":HANDSHAKE_FAILURE_ON_CLIENT_HELLO:",
	})

	testCases = append(testCases, testCase{
		name: "UnsupportedCipherSuite",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_CBC_SHA},
			Bugs: ProtocolBugs{
				IgnorePeerCipherPreferences: true,
			},
		},
		flags:         []string{"-cipher", "DEFAULT:!AES"},
		shouldFail:    true,
		expectedError: ":WRONG_CIPHER_RETURNED:",
	})

	testCases = append(testCases, testCase{
		name: "ServerHelloBogusCipher",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendCipherSuite: bogusCipher,
			},
		},
		shouldFail:    true,
		expectedError: ":UNKNOWN_CIPHER_RETURNED:",
	})
	testCases = append(testCases, testCase{
		name: "ServerHelloBogusCipher-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendCipherSuite: bogusCipher,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CIPHER_RETURNED:",
	})

	// The server must be tolerant to bogus ciphers.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "UnknownCipher",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{bogusCipher, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				AdvertiseAllConfiguredCiphers: true,
			},
		},
	})

	// The server must be tolerant to bogus ciphers.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "UnknownCipher-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{bogusCipher, TLS_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				AdvertiseAllConfiguredCiphers: true,
			},
		},
	})

	// Test empty ECDHE_PSK identity hints work as expected.
	testCases = append(testCases, testCase{
		name: "EmptyECDHEPSKHint",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
		},
		flags: []string{"-psk", "secret"},
	})

	// Test empty PSK identity hints work as expected, even if an explicit
	// ServerKeyExchange is sent.
	testCases = append(testCases, testCase{
		name: "ExplicitEmptyPSKHint",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
			Bugs: ProtocolBugs{
				AlwaysSendPreSharedKeyIdentityHint: true,
			},
		},
		flags: []string{"-psk", "secret"},
	})

	// Test that clients enforce that the server-sent certificate and cipher
	// suite match in TLS 1.2.
	testCases = append(testCases, testCase{
		name: "CertificateCipherMismatch-RSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				SendCipherSuite: TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CERTIFICATE_TYPE:",
	})
	testCases = append(testCases, testCase{
		name: "CertificateCipherMismatch-ECDSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
			Certificates: []Certificate{ecdsaP256Certificate},
			Bugs: ProtocolBugs{
				SendCipherSuite: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CERTIFICATE_TYPE:",
	})
	testCases = append(testCases, testCase{
		name: "CertificateCipherMismatch-Ed25519",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
			Certificates: []Certificate{ed25519Certificate},
			Bugs: ProtocolBugs{
				SendCipherSuite: TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CERTIFICATE_TYPE:",
	})

	// Test that servers decline to select a cipher suite which is
	// inconsistent with their configured certificate.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerCipherFilter-RSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_SHARED_CIPHER:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerCipherFilter-ECDSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_SHARED_CIPHER:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerCipherFilter-Ed25519",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ed25519CertificateFile),
			"-key-file", path.Join(*resourceDir, ed25519KeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_SHARED_CIPHER:",
	})

	// Test cipher suite negotiation works as expected. Configure a
	// complicated cipher suite configuration.
	const negotiationTestCiphers = "" +
		"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:" +
		"[TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384|TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256|TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA]:" +
		"TLS_RSA_WITH_AES_128_GCM_SHA256:" +
		"TLS_RSA_WITH_AES_128_CBC_SHA:" +
		"[TLS_RSA_WITH_AES_256_GCM_SHA384|TLS_RSA_WITH_AES_256_CBC_SHA]"
	negotiationTests := []struct {
		ciphers  []uint16
		expected uint16
	}{
		// Server preferences are honored, including when
		// equipreference groups are involved.
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_128_CBC_SHA,
				TLS_RSA_WITH_AES_128_GCM_SHA256,
				TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
				TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			},
			TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
		},
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_128_CBC_SHA,
				TLS_RSA_WITH_AES_128_GCM_SHA256,
				TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
			},
			TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
		},
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_128_CBC_SHA,
				TLS_RSA_WITH_AES_128_GCM_SHA256,
			},
			TLS_RSA_WITH_AES_128_GCM_SHA256,
		},
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_128_CBC_SHA,
			},
			TLS_RSA_WITH_AES_128_CBC_SHA,
		},
		// Equipreference groups use the client preference.
		{
			[]uint16{
				TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
				TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
				TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
			},
			TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
		},
		{
			[]uint16{
				TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
				TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
			},
			TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
		},
		{
			[]uint16{
				TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
				TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
			},
			TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
		},
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_256_CBC_SHA,
			},
			TLS_RSA_WITH_AES_256_GCM_SHA384,
		},
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_CBC_SHA,
				TLS_RSA_WITH_AES_256_GCM_SHA384,
			},
			TLS_RSA_WITH_AES_256_CBC_SHA,
		},
		// If there are two equipreference groups, the preferred one
		// takes precedence.
		{
			[]uint16{
				TLS_RSA_WITH_AES_256_GCM_SHA384,
				TLS_RSA_WITH_AES_256_CBC_SHA,
				TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
				TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
			},
			TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
		},
	}
	for i, t := range negotiationTests {
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "CipherNegotiation-" + strconv.Itoa(i),
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: t.ciphers,
			},
			flags:          []string{"-cipher", negotiationTestCiphers},
			expectedCipher: t.expected,
		})
	}
}

func addBadECDSASignatureTests() {
	for badR := BadValue(1); badR < NumBadValues; badR++ {
		for badS := BadValue(1); badS < NumBadValues; badS++ {
			testCases = append(testCases, testCase{
				name: fmt.Sprintf("BadECDSA-%d-%d", badR, badS),
				config: Config{
					MaxVersion:   VersionTLS12,
					CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
					Certificates: []Certificate{ecdsaP256Certificate},
					Bugs: ProtocolBugs{
						BadECDSAR: badR,
						BadECDSAS: badS,
					},
				},
				shouldFail:    true,
				expectedError: ":BAD_SIGNATURE:",
			})
			testCases = append(testCases, testCase{
				name: fmt.Sprintf("BadECDSA-%d-%d-TLS13", badR, badS),
				config: Config{
					MaxVersion:   VersionTLS13,
					Certificates: []Certificate{ecdsaP256Certificate},
					Bugs: ProtocolBugs{
						BadECDSAR: badR,
						BadECDSAS: badS,
					},
				},
				shouldFail:    true,
				expectedError: ":BAD_SIGNATURE:",
			})
		}
	}
}

func addCBCPaddingTests() {
	testCases = append(testCases, testCase{
		name: "MaxCBCPadding",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
			Bugs: ProtocolBugs{
				MaxPadding: true,
			},
		},
		messageLen: 12, // 20 bytes of SHA-1 + 12 == 0 % block size
	})
	testCases = append(testCases, testCase{
		name: "BadCBCPadding",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
			Bugs: ProtocolBugs{
				PaddingFirstByteBad: true,
			},
		},
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})
	// OpenSSL previously had an issue where the first byte of padding in
	// 255 bytes of padding wasn't checked.
	testCases = append(testCases, testCase{
		name: "BadCBCPadding255",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
			Bugs: ProtocolBugs{
				MaxPadding:               true,
				PaddingFirstByteBadIf255: true,
			},
		},
		messageLen:    12, // 20 bytes of SHA-1 + 12 == 0 % block size
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})
}

func addCBCSplittingTests() {
	var cbcCiphers = []struct {
		name   string
		cipher uint16
	}{
		{"3DES", TLS_RSA_WITH_3DES_EDE_CBC_SHA},
		{"AES128", TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
		{"AES256", TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA},
	}
	for _, t := range cbcCiphers {
		testCases = append(testCases, testCase{
			name: "CBCRecordSplitting-" + t.name,
			config: Config{
				MaxVersion:   VersionTLS10,
				MinVersion:   VersionTLS10,
				CipherSuites: []uint16{t.cipher},
				Bugs: ProtocolBugs{
					ExpectRecordSplitting: true,
				},
			},
			messageLen:    -1, // read until EOF
			resumeSession: true,
			flags: []string{
				"-async",
				"-write-different-record-sizes",
				"-cbc-record-splitting",
			},
		})
		testCases = append(testCases, testCase{
			name: "CBCRecordSplittingPartialWrite-" + t.name,
			config: Config{
				MaxVersion:   VersionTLS10,
				MinVersion:   VersionTLS10,
				CipherSuites: []uint16{t.cipher},
				Bugs: ProtocolBugs{
					ExpectRecordSplitting: true,
				},
			},
			messageLen: -1, // read until EOF
			flags: []string{
				"-async",
				"-write-different-record-sizes",
				"-cbc-record-splitting",
				"-partial-write",
			},
		})
	}
}

func addClientAuthTests() {
	// Add a dummy cert pool to stress certificate authority parsing.
	certPool := x509.NewCertPool()
	for _, cert := range []Certificate{rsaCertificate, rsa1024Certificate} {
		cert, err := x509.ParseCertificate(cert.Certificate[0])
		if err != nil {
			panic(err)
		}
		certPool.AddCert(cert)
	}
	caNames := certPool.Subjects()

	for _, ver := range tlsVersions {
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     ver.name + "-Client-ClientAuth-RSA",
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				ClientAuth: RequireAnyClientCert,
				ClientCAs:  certPool,
			},
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaKeyFile),
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     ver.name + "-Server-ClientAuth-RSA",
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{"-require-any-client-certificate"},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     ver.name + "-Server-ClientAuth-ECDSA",
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{ecdsaP256Certificate},
			},
			flags: []string{"-require-any-client-certificate"},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     ver.name + "-Client-ClientAuth-ECDSA",
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				ClientAuth: RequireAnyClientCert,
				ClientCAs:  certPool,
			},
			flags: []string{
				"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
				"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
			},
		})

		testCases = append(testCases, testCase{
			name: "NoClientCertificate-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				ClientAuth: RequireAnyClientCert,
			},
			shouldFail:         true,
			expectedLocalError: "client didn't provide a certificate",
		})

		testCases = append(testCases, testCase{
			// Even if not configured to expect a certificate, OpenSSL will
			// return X509_V_OK as the verify_result.
			testType: serverTest,
			name:     "NoClientCertificateRequested-Server-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			flags: []string{
				"-expect-verify-result",
			},
			resumeSession: true,
		})

		testCases = append(testCases, testCase{
			// If a client certificate is not provided, OpenSSL will still
			// return X509_V_OK as the verify_result.
			testType: serverTest,
			name:     "NoClientCertificate-Server-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			flags: []string{
				"-expect-verify-result",
				"-verify-peer",
			},
			resumeSession: true,
		})

		certificateRequired := "remote error: certificate required"
		if ver.version < VersionTLS13 {
			// Prior to TLS 1.3, the generic handshake_failure alert
			// was used.
			certificateRequired = "remote error: handshake failure"
		}
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "RequireAnyClientCertificate-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			flags:              []string{"-require-any-client-certificate"},
			shouldFail:         true,
			expectedError:      ":PEER_DID_NOT_RETURN_A_CERTIFICATE:",
			expectedLocalError: certificateRequired,
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "SkipClientCertificate-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SkipClientCertificate: true,
				},
			},
			// Setting SSL_VERIFY_PEER allows anonymous clients.
			flags:         []string{"-verify-peer"},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_MESSAGE:",
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "VerifyPeerIfNoOBC-NoChannelID-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			flags: []string{
				"-enable-channel-id",
				"-verify-peer-if-no-obc",
			},
			shouldFail:         true,
			expectedError:      ":PEER_DID_NOT_RETURN_A_CERTIFICATE:",
			expectedLocalError: certificateRequired,
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "VerifyPeerIfNoOBC-ChannelID-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				ChannelID:  channelIDKey,
			},
			expectChannelID: true,
			flags: []string{
				"-enable-channel-id",
				"-verify-peer-if-no-obc",
			},
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     ver.name + "-Server-CertReq-CA-List",
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
				Bugs: ProtocolBugs{
					ExpectCertificateReqNames: caNames,
				},
			},
			flags: []string{
				"-require-any-client-certificate",
				"-use-client-ca-list", encodeDERValues(caNames),
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     ver.name + "-Client-CertReq-CA-List",
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
				ClientAuth:   RequireAnyClientCert,
				ClientCAs:    certPool,
			},
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaKeyFile),
				"-expect-client-ca-list", encodeDERValues(caNames),
			},
		})
	}

	// Client auth is only legal in certificate-based ciphers.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ClientAuth-PSK",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
			ClientAuth:   RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-psk", "secret",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ClientAuth-ECDHE_PSK",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
			ClientAuth:   RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-psk", "secret",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})

	// Regression test for a bug where the client CA list, if explicitly
	// set to NULL, was mis-encoded.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Null-Client-CA-List",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				ExpectCertificateReqNames: [][]byte{},
			},
		},
		flags: []string{
			"-require-any-client-certificate",
			"-use-client-ca-list", "<NULL>",
		},
	})

	// Test that an empty client CA list doesn't send a CA extension.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-Empty-Client-CA-List",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				ExpectNoCertificateAuthoritiesExtension: true,
			},
		},
		flags: []string{
			"-require-any-client-certificate",
			"-use-client-ca-list", "<EMPTY>",
		},
	})

}

func addExtendedMasterSecretTests() {
	const expectEMSFlag = "-expect-extended-master-secret"

	for _, with := range []bool{false, true} {
		prefix := "No"
		if with {
			prefix = ""
		}

		for _, isClient := range []bool{false, true} {
			suffix := "-Server"
			testType := serverTest
			if isClient {
				suffix = "-Client"
				testType = clientTest
			}

			for _, ver := range tlsVersions {
				// In TLS 1.3, the extension is irrelevant and
				// always reports as enabled.
				var flags []string
				if with || ver.version >= VersionTLS13 {
					flags = []string{expectEMSFlag}
				}

				testCases = append(testCases, testCase{
					testType: testType,
					name:     prefix + "ExtendedMasterSecret-" + ver.name + suffix,
					config: Config{
						MinVersion: ver.version,
						MaxVersion: ver.version,
						Bugs: ProtocolBugs{
							NoExtendedMasterSecret:      !with,
							RequireExtendedMasterSecret: with,
						},
					},
					flags: flags,
				})
			}
		}
	}

	for _, isClient := range []bool{false, true} {
		for _, supportedInFirstConnection := range []bool{false, true} {
			for _, supportedInResumeConnection := range []bool{false, true} {
				boolToWord := func(b bool) string {
					if b {
						return "Yes"
					}
					return "No"
				}
				suffix := boolToWord(supportedInFirstConnection) + "To" + boolToWord(supportedInResumeConnection) + "-"
				if isClient {
					suffix += "Client"
				} else {
					suffix += "Server"
				}

				supportedConfig := Config{
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						RequireExtendedMasterSecret: true,
					},
				}

				noSupportConfig := Config{
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						NoExtendedMasterSecret: true,
					},
				}

				test := testCase{
					name:          "ExtendedMasterSecret-" + suffix,
					resumeSession: true,
				}

				if !isClient {
					test.testType = serverTest
				}

				if supportedInFirstConnection {
					test.config = supportedConfig
				} else {
					test.config = noSupportConfig
				}

				if supportedInResumeConnection {
					test.resumeConfig = &supportedConfig
				} else {
					test.resumeConfig = &noSupportConfig
				}

				switch suffix {
				case "YesToYes-Client", "YesToYes-Server":
					// When a session is resumed, it should
					// still be aware that its master
					// secret was generated via EMS and
					// thus it's safe to use tls-unique.
					test.flags = []string{expectEMSFlag}
				case "NoToYes-Server":
					// If an original connection did not
					// contain EMS, but a resumption
					// handshake does, then a server should
					// not resume the session.
					test.expectResumeRejected = true
				case "YesToNo-Server":
					// Resuming an EMS session without the
					// EMS extension should cause the
					// server to abort the connection.
					test.shouldFail = true
					test.expectedError = ":RESUMED_EMS_SESSION_WITHOUT_EMS_EXTENSION:"
				case "NoToYes-Client":
					// A client should abort a connection
					// where the server resumed a non-EMS
					// session but echoed the EMS
					// extension.
					test.shouldFail = true
					test.expectedError = ":RESUMED_NON_EMS_SESSION_WITH_EMS_EXTENSION:"
				case "YesToNo-Client":
					// A client should abort a connection
					// where the server didn't echo EMS
					// when the session used it.
					test.shouldFail = true
					test.expectedError = ":RESUMED_EMS_SESSION_WITHOUT_EMS_EXTENSION:"
				}

				testCases = append(testCases, test)
			}
		}
	}

	// Switching EMS on renegotiation is forbidden.
	testCases = append(testCases, testCase{
		name: "ExtendedMasterSecret-Renego-NoEMS",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoExtendedMasterSecret:                true,
				NoExtendedMasterSecretOnRenegotiation: true,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})

	testCases = append(testCases, testCase{
		name: "ExtendedMasterSecret-Renego-Upgrade",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoExtendedMasterSecret: true,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
		shouldFail:    true,
		expectedError: ":RENEGOTIATION_EMS_MISMATCH:",
	})

	testCases = append(testCases, testCase{
		name: "ExtendedMasterSecret-Renego-Downgrade",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoExtendedMasterSecretOnRenegotiation: true,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
		shouldFail:    true,
		expectedError: ":RENEGOTIATION_EMS_MISMATCH:",
	})
}

type stateMachineTestConfig struct {
	protocol          protocol
	async             bool
	splitHandshake    bool
	packHandshake     bool
	implicitHandshake bool
}

// Adds tests that try to cover the range of the handshake state machine, under
// various conditions. Some of these are redundant with other tests, but they
// only cover the synchronous case.
func addAllStateMachineCoverageTests() {
	for _, async := range []bool{false, true} {
		for _, protocol := range []protocol{tls, dtls} {
			addStateMachineCoverageTests(stateMachineTestConfig{
				protocol: protocol,
				async:    async,
			})
			addStateMachineCoverageTests(stateMachineTestConfig{
				protocol:          protocol,
				async:             async,
				implicitHandshake: true,
			})
			addStateMachineCoverageTests(stateMachineTestConfig{
				protocol:       protocol,
				async:          async,
				splitHandshake: true,
			})
			addStateMachineCoverageTests(stateMachineTestConfig{
				protocol:      protocol,
				async:         async,
				packHandshake: true,
			})
		}
	}
}

func addStateMachineCoverageTests(config stateMachineTestConfig) {
	var tests []testCase

	// Basic handshake, with resumption. Client and server,
	// session ID and session ticket.
	tests = append(tests, testCase{
		name: "Basic-Client",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		resumeSession: true,
		// Ensure session tickets are used, not session IDs.
		noSessionCache: true,
	})
	tests = append(tests, testCase{
		name: "Basic-Client-RenewTicket",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				RenewTicketOnResume: true,
			},
		},
		flags:                []string{"-expect-ticket-renewal"},
		resumeSession:        true,
		resumeRenewedSession: true,
	})
	tests = append(tests, testCase{
		name: "Basic-Client-NoTicket",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
		resumeSession: true,
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				RequireSessionTickets: true,
			},
		},
		resumeSession: true,
		flags:         []string{"-expect-no-session-id"},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-NoTickets",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
		resumeSession: true,
		flags:         []string{"-expect-session-id"},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-EarlyCallback",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		flags:         []string{"-use-early-callback"},
		resumeSession: true,
	})

	// TLS 1.3 basic handshake shapes.
	if config.protocol == tls {
		tests = append(tests, testCase{
			name: "TLS13-1RTT-Client",
			config: Config{
				MaxVersion: VersionTLS13,
				MinVersion: VersionTLS13,
			},
			resumeSession:        true,
			resumeRenewedSession: true,
			// 0-RTT being disabled overrides all other 0-RTT reasons.
			flags: []string{"-expect-early-data-reason", "disabled"},
		})

		tests = append(tests, testCase{
			testType: serverTest,
			name:     "TLS13-1RTT-Server",
			config: Config{
				MaxVersion: VersionTLS13,
				MinVersion: VersionTLS13,
			},
			resumeSession:        true,
			resumeRenewedSession: true,
			flags: []string{
				// TLS 1.3 uses tickets, so the session should not be
				// cached statefully.
				"-expect-no-session-id",
				// 0-RTT being disabled overrides all other 0-RTT reasons.
				"-expect-early-data-reason", "disabled",
			},
		})

		tests = append(tests, testCase{
			name: "TLS13-HelloRetryRequest-Client",
			config: Config{
				MaxVersion: VersionTLS13,
				MinVersion: VersionTLS13,
				// P-384 requires a HelloRetryRequest against BoringSSL's default
				// configuration. Assert this with ExpectMissingKeyShare.
				CurvePreferences: []CurveID{CurveP384},
				Bugs: ProtocolBugs{
					ExpectMissingKeyShare: true,
				},
			},
			// Cover HelloRetryRequest during an ECDHE-PSK resumption.
			resumeSession: true,
		})

		tests = append(tests, testCase{
			testType: serverTest,
			name:     "TLS13-HelloRetryRequest-Server",
			config: Config{
				MaxVersion: VersionTLS13,
				MinVersion: VersionTLS13,
				// Require a HelloRetryRequest for every curve.
				DefaultCurves: []CurveID{},
			},
			// Cover HelloRetryRequest during an ECDHE-PSK resumption.
			resumeSession: true,
		})

		tests = append(tests, testCase{
			testType: clientTest,
			name:     "TLS13-EarlyData-TooMuchData-Client",
			config: Config{
				MaxVersion:       VersionTLS13,
				MinVersion:       VersionTLS13,
				MaxEarlyDataSize: 2,
			},
			resumeConfig: &Config{
				MaxVersion:       VersionTLS13,
				MinVersion:       VersionTLS13,
				MaxEarlyDataSize: 2,
				Bugs: ProtocolBugs{
					ExpectEarlyData: [][]byte{{'h', 'e'}},
				},
			},
			resumeShimPrefix: "llo",
			resumeSession:    true,
			flags: []string{
				"-enable-early-data",
				"-expect-ticket-supports-early-data",
				"-on-resume-expect-accept-early-data",
				"-on-resume-shim-writes-first",
			},
		})

		// Unfinished writes can only be tested when operations are async. EarlyData
		// can't be tested as part of an ImplicitHandshake in this case since
		// otherwise the early data will be sent as normal data.
		if config.async && !config.implicitHandshake {
			tests = append(tests, testCase{
				testType: clientTest,
				name:     "TLS13-EarlyData-UnfinishedWrite-Client",
				config: Config{
					MaxVersion:       VersionTLS13,
					MinVersion:       VersionTLS13,
					MaxEarlyDataSize: 16384,
				},
				resumeConfig: &Config{
					MaxVersion:       VersionTLS13,
					MinVersion:       VersionTLS13,
					MaxEarlyDataSize: 16384,
					Bugs: ProtocolBugs{
						ExpectLateEarlyData: [][]byte{{'h', 'e', 'l', 'l', 'o'}},
					},
				},
				resumeSession: true,
				flags: []string{
					"-enable-early-data",
					"-expect-ticket-supports-early-data",
					"-on-resume-expect-accept-early-data",
					"-on-resume-read-with-unfinished-write",
					"-on-resume-shim-writes-first",
				},
			})

			// Rejected unfinished writes are discarded (from the
			// perspective of the calling application) on 0-RTT
			// reject.
			tests = append(tests, testCase{
				testType: clientTest,
				name:     "TLS13-EarlyData-RejectUnfinishedWrite-Client",
				config: Config{
					MaxVersion:       VersionTLS13,
					MinVersion:       VersionTLS13,
					MaxEarlyDataSize: 16384,
				},
				resumeConfig: &Config{
					MaxVersion:       VersionTLS13,
					MinVersion:       VersionTLS13,
					MaxEarlyDataSize: 16384,
					Bugs: ProtocolBugs{
						AlwaysRejectEarlyData: true,
					},
				},
				resumeSession: true,
				flags: []string{
					"-enable-early-data",
					"-expect-ticket-supports-early-data",
					"-expect-reject-early-data",
					"-on-resume-read-with-unfinished-write",
					"-on-resume-shim-writes-first",
				},
			})
		}

		tests = append(tests, testCase{
			testType: serverTest,
			name:     "TLS13-MaxEarlyData-Server",
			config: Config{
				MaxVersion: VersionTLS13,
				MinVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendEarlyData:           [][]byte{bytes.Repeat([]byte{1}, 14336+1)},
					ExpectEarlyDataAccepted: true,
				},
			},
			messageCount:  2,
			resumeSession: true,
			flags: []string{
				"-enable-early-data",
				"-on-resume-expect-accept-early-data",
			},
			shouldFail:    true,
			expectedError: ":TOO_MUCH_READ_EARLY_DATA:",
		})
	}

	// TLS client auth.
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-NoCertificate-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequestClientCert,
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "ClientAuth-NoCertificate-Server",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		// Setting SSL_VERIFY_PEER allows anonymous clients.
		flags: []string{"-verify-peer"},
	})
	if config.protocol == tls {
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "ClientAuth-NoCertificate-Client-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
				ClientAuth: RequestClientCert,
			},
		})
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "ClientAuth-NoCertificate-Server-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			// Setting SSL_VERIFY_PEER allows anonymous clients.
			flags: []string{"-verify-peer"},
		})
	}
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-RSA-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-RSA-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-ECDSA-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-ECDSA-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-NoCertificate-OldCallback",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequestClientCert,
		},
		flags: []string{"-use-old-client-cert-callback"},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-NoCertificate-OldCallback-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequestClientCert,
		},
		flags: []string{"-use-old-client-cert-callback"},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-OldCallback",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-use-old-client-cert-callback",
		},
	})
	tests = append(tests, testCase{
		testType: clientTest,
		name:     "ClientAuth-OldCallback-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-use-old-client-cert-callback",
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "ClientAuth-Server",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
		},
		flags: []string{"-require-any-client-certificate"},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "ClientAuth-Server-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaCertificate},
		},
		flags: []string{"-require-any-client-certificate"},
	})

	// Test each key exchange on the server side for async keys.
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-RSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-ECDHE-RSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-ECDHE-ECDSA",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "Basic-Server-Ed25519",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ed25519CertificateFile),
			"-key-file", path.Join(*resourceDir, ed25519KeyFile),
			"-enable-ed25519",
		},
	})

	// No session ticket support; server doesn't send NewSessionTicket.
	tests = append(tests, testCase{
		name: "SessionTicketsDisabled-Client",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "SessionTicketsDisabled-Server",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
	})

	// Skip ServerKeyExchange in PSK key exchange if there's no
	// identity hint.
	tests = append(tests, testCase{
		name: "EmptyPSKHint-Client",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
		},
		flags: []string{"-psk", "secret"},
	})
	tests = append(tests, testCase{
		testType: serverTest,
		name:     "EmptyPSKHint-Server",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_PSK_WITH_AES_128_CBC_SHA},
			PreSharedKey: []byte("secret"),
		},
		flags: []string{"-psk", "secret"},
	})

	// OCSP stapling tests.
	for _, vers := range tlsVersions {
		if config.protocol == dtls && !vers.hasDTLS {
			continue
		}
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "OCSPStapling-Client-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			flags: []string{
				"-enable-ocsp-stapling",
				"-expect-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
				"-verify-peer",
			},
			resumeSession: true,
		})
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "OCSPStapling-Server-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			expectedOCSPResponse: testOCSPResponse,
			flags: []string{
				"-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
			},
			resumeSession: true,
		})

		// The client OCSP callback is an alternate certificate
		// verification callback.
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "ClientOCSPCallback-Pass-" + vers.name,
			config: Config{
				MaxVersion:   vers.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-enable-ocsp-stapling",
				"-use-ocsp-callback",
			},
		})
		var expectedLocalError string
		if !config.async {
			// TODO(davidben): Asynchronous fatal alerts are never
			// sent. https://crbug.com/boringssl/130.
			expectedLocalError = "remote error: bad certificate status response"
		}
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "ClientOCSPCallback-Fail-" + vers.name,
			config: Config{
				MaxVersion:   vers.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-enable-ocsp-stapling",
				"-use-ocsp-callback",
				"-fail-ocsp-callback",
			},
			shouldFail:         true,
			expectedLocalError: expectedLocalError,
			expectedError:      ":OCSP_CB_ERROR:",
		})
		// The callback still runs if the server does not send an OCSP
		// response.
		certNoStaple := rsaCertificate
		certNoStaple.OCSPStaple = nil
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "ClientOCSPCallback-FailNoStaple-" + vers.name,
			config: Config{
				MaxVersion:   vers.version,
				Certificates: []Certificate{certNoStaple},
			},
			flags: []string{
				"-enable-ocsp-stapling",
				"-use-ocsp-callback",
				"-fail-ocsp-callback",
			},
			shouldFail:         true,
			expectedLocalError: expectedLocalError,
			expectedError:      ":OCSP_CB_ERROR:",
		})

		// The server OCSP callback is a legacy mechanism for
		// configuring OCSP, used by unreliable server software.
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "ServerOCSPCallback-SetInCallback-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			expectedOCSPResponse: testOCSPResponse,
			flags: []string{
				"-use-ocsp-callback",
				"-set-ocsp-in-callback",
				"-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
			},
			resumeSession: true,
		})

		// The callback may decline OCSP, in which case  we act as if
		// the client did not support it, even if a response was
		// configured.
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "ServerOCSPCallback-Decline-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			expectedOCSPResponse: []byte{},
			flags: []string{
				"-use-ocsp-callback",
				"-decline-ocsp-callback",
				"-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
			},
			resumeSession: true,
		})

		// The callback may also signal an internal error.
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "ServerOCSPCallback-Fail-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			flags: []string{
				"-use-ocsp-callback",
				"-fail-ocsp-callback",
				"-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
			},
			shouldFail:    true,
			expectedError: ":OCSP_CB_ERROR:",
		})
	}

	// Certificate verification tests.
	for _, vers := range tlsVersions {
		if config.protocol == dtls && !vers.hasDTLS {
			continue
		}
		for _, useCustomCallback := range []bool{false, true} {
			for _, testType := range []testType{clientTest, serverTest} {
				suffix := "-Client"
				if testType == serverTest {
					suffix = "-Server"
				}
				suffix += "-" + vers.name
				if useCustomCallback {
					suffix += "-CustomCallback"
				}

				flags := []string{"-verify-peer"}
				if testType == serverTest {
					flags = append(flags, "-require-any-client-certificate")
				}
				if useCustomCallback {
					flags = append(flags, "-use-custom-verify-callback")
				}

				tests = append(tests, testCase{
					testType: testType,
					name:     "CertificateVerificationSucceed" + suffix,
					config: Config{
						MaxVersion:   vers.version,
						Certificates: []Certificate{rsaCertificate},
					},
					flags:         append([]string{"-expect-verify-result"}, flags...),
					resumeSession: true,
				})
				tests = append(tests, testCase{
					testType: testType,
					name:     "CertificateVerificationFail" + suffix,
					config: Config{
						MaxVersion:   vers.version,
						Certificates: []Certificate{rsaCertificate},
					},
					flags:         append([]string{"-verify-fail"}, flags...),
					shouldFail:    true,
					expectedError: ":CERTIFICATE_VERIFY_FAILED:",
				})
				// Tests that although the verify callback fails on resumption, by default we don't call it.
				tests = append(tests, testCase{
					testType: testType,
					name:     "CertificateVerificationDoesNotFailOnResume" + suffix,
					config: Config{
						MaxVersion:   vers.version,
						Certificates: []Certificate{rsaCertificate},
					},
					flags:         append([]string{"-on-resume-verify-fail"}, flags...),
					resumeSession: true,
				})
				if testType == clientTest && useCustomCallback {
					tests = append(tests, testCase{
						testType: testType,
						name:     "CertificateVerificationFailsOnResume" + suffix,
						config: Config{
							MaxVersion:   vers.version,
							Certificates: []Certificate{rsaCertificate},
						},
						flags: append([]string{
							"-on-resume-verify-fail",
							"-reverify-on-resume",
						}, flags...),
						resumeSession: true,
						shouldFail:    true,
						expectedError: ":CERTIFICATE_VERIFY_FAILED:",
					})
					tests = append(tests, testCase{
						testType: testType,
						name:     "CertificateVerificationPassesOnResume" + suffix,
						config: Config{
							MaxVersion:   vers.version,
							Certificates: []Certificate{rsaCertificate},
						},
						flags: append([]string{
							"-reverify-on-resume",
						}, flags...),
						resumeSession: true,
					})
					if vers.version >= VersionTLS13 {
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-RejectTicket-Client-Reverify" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:             vers.version,
								MaxEarlyDataSize:       16384,
								SessionTicketsDisabled: true,
							},
							resumeSession:        true,
							expectResumeRejected: true,
							flags: append([]string{
								"-enable-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-shim-writes-first",
								// Session tickets are disabled, so the runner will not send a ticket.
								"-on-retry-expect-no-session",
								"-expect-reject-early-data",
							}, flags...),
						})
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-Reject0RTT-Client-Reverify" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
								Bugs: ProtocolBugs{
									AlwaysRejectEarlyData: true,
								},
							},
							resumeSession:        true,
							expectResumeRejected: false,
							flags: append([]string{
								"-enable-early-data",
								"-expect-reject-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-shim-writes-first",
							}, flags...),
						})
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-RejectTicket-Client-ReverifyFails" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:             vers.version,
								MaxEarlyDataSize:       16384,
								SessionTicketsDisabled: true,
							},
							resumeSession:        true,
							expectResumeRejected: true,
							shouldFail:           true,
							expectedError:        ":CERTIFICATE_VERIFY_FAILED:",
							flags: append([]string{
								"-enable-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-shim-writes-first",
								// Session tickets are disabled, so the runner will not send a ticket.
								"-on-retry-expect-no-session",
								"-on-retry-verify-fail",
								"-expect-reject-early-data",
							}, flags...),
						})
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-Reject0RTT-Client-ReverifyFails" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
								Bugs: ProtocolBugs{
									AlwaysRejectEarlyData: true,
								},
							},
							resumeSession:        true,
							expectResumeRejected: false,
							shouldFail:           true,
							expectedError:        ":CERTIFICATE_VERIFY_FAILED:",
							flags: append([]string{
								"-enable-early-data",
								"-expect-reject-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-shim-writes-first",
								"-on-retry-verify-fail",
							}, flags...),
						})
						// This tests that we only call the verify callback once.
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-Accept0RTT-Client-Reverify" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
								Bugs: ProtocolBugs{
									ExpectEarlyData: [][]byte{[]byte("hello")},
								},
							},
							resumeSession:        true,
							expectResumeRejected: false,
							flags: append([]string{
								"-enable-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-shim-writes-first",
							}, flags...),
						})
						tests = append(tests, testCase{
							testType: testType,
							name:     "EarlyData-Accept0RTT-Client-ReverifyFails" + suffix,
							config: Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
							},
							resumeConfig: &Config{
								MaxVersion:       vers.version,
								MaxEarlyDataSize: 16384,
								Bugs: ProtocolBugs{
									ExpectEarlyData: [][]byte{[]byte("hello")},
								},
							},
							resumeSession: true,
							shouldFail:    true,
							expectedError: ":CERTIFICATE_VERIFY_FAILED:",
							flags: append([]string{
								"-enable-early-data",
								"-expect-ticket-supports-early-data",
								"-reverify-on-resume",
								"-on-resume-verify-fail",
								"-on-resume-shim-writes-first",
							}, flags...),
						})
					}
				}
			}
		}

		// By default, the client is in a soft fail mode where the peer
		// certificate is verified but failures are non-fatal.
		tests = append(tests, testCase{
			testType: clientTest,
			name:     "CertificateVerificationSoftFail-" + vers.name,
			config: Config{
				MaxVersion:   vers.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-verify-fail",
				"-expect-verify-result",
			},
			resumeSession: true,
		})
	}

	tests = append(tests, testCase{
		name:               "ShimSendAlert",
		flags:              []string{"-send-alert"},
		shimWritesFirst:    true,
		shouldFail:         true,
		expectedLocalError: "remote error: decompression failure",
	})

	if config.protocol == tls {
		tests = append(tests, testCase{
			name: "Renegotiate-Client",
			config: Config{
				MaxVersion: VersionTLS12,
			},
			renegotiate: 1,
			flags: []string{
				"-renegotiate-freely",
				"-expect-total-renegotiations", "1",
			},
		})

		tests = append(tests, testCase{
			name: "SendHalfHelloRequest",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					PackHelloRequestWithFinished: config.packHandshake,
				},
			},
			sendHalfHelloRequest: true,
			flags:                []string{"-renegotiate-ignore"},
			shouldFail:           true,
			expectedError:        ":UNEXPECTED_RECORD:",
		})

		// NPN on client and server; results in post-handshake message.
		tests = append(tests, testCase{
			name: "NPN-Client",
			config: Config{
				MaxVersion: VersionTLS12,
				NextProtos: []string{"foo"},
			},
			flags:                 []string{"-select-next-proto", "foo"},
			resumeSession:         true,
			expectedNextProto:     "foo",
			expectedNextProtoType: npn,
		})
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "NPN-Server",
			config: Config{
				MaxVersion: VersionTLS12,
				NextProtos: []string{"bar"},
			},
			flags: []string{
				"-advertise-npn", "\x03foo\x03bar\x03baz",
				"-expect-next-proto", "bar",
			},
			resumeSession:         true,
			expectedNextProto:     "bar",
			expectedNextProtoType: npn,
		})

		// Client does False Start and negotiates NPN.
		tests = append(tests, testCase{
			name: "FalseStart",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					ExpectFalseStart: true,
				},
			},
			flags: []string{
				"-false-start",
				"-select-next-proto", "foo",
			},
			shimWritesFirst: true,
			resumeSession:   true,
		})

		// Client does False Start and negotiates ALPN.
		tests = append(tests, testCase{
			name: "FalseStart-ALPN",
			config: Config{
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:   []string{"foo"},
				Bugs: ProtocolBugs{
					ExpectFalseStart: true,
				},
			},
			flags: []string{
				"-false-start",
				"-advertise-alpn", "\x03foo",
				"-expect-alpn", "foo",
			},
			shimWritesFirst: true,
			resumeSession:   true,
		})

		// False Start without session tickets.
		tests = append(tests, testCase{
			name: "FalseStart-SessionTicketsDisabled",
			config: Config{
				MaxVersion:             VersionTLS12,
				CipherSuites:           []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				NextProtos:             []string{"foo"},
				SessionTicketsDisabled: true,
				Bugs: ProtocolBugs{
					ExpectFalseStart: true,
				},
			},
			flags: []string{
				"-false-start",
				"-select-next-proto", "foo",
			},
			shimWritesFirst: true,
		})

		// Server parses a V2ClientHello.
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "SendV2ClientHello",
			config: Config{
				// Choose a cipher suite that does not involve
				// elliptic curves, so no extensions are
				// involved.
				MaxVersion:   VersionTLS12,
				CipherSuites: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
				Bugs: ProtocolBugs{
					SendV2ClientHello: true,
				},
			},
			flags: []string{
				"-expect-msg-callback",
				`read v2clienthello
write hs 2
write hs 11
write hs 14
read hs 16
read ccs
read hs 20
write ccs
write hs 20
read alert 1 0
`,
			},
		})

		// Test Channel ID
		for _, ver := range tlsVersions {
			if ver.version < VersionTLS10 {
				continue
			}
			// Client sends a Channel ID.
			tests = append(tests, testCase{
				name: "ChannelID-Client-" + ver.name,
				config: Config{
					MaxVersion:       ver.version,
					RequestChannelID: true,
				},
				flags:           []string{"-send-channel-id", path.Join(*resourceDir, channelIDKeyFile)},
				resumeSession:   true,
				expectChannelID: true,
			})

			// Server accepts a Channel ID.
			tests = append(tests, testCase{
				testType: serverTest,
				name:     "ChannelID-Server-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					ChannelID:  channelIDKey,
				},
				flags: []string{
					"-expect-channel-id",
					base64.StdEncoding.EncodeToString(channelIDBytes),
				},
				resumeSession:   true,
				expectChannelID: true,
			})

			tests = append(tests, testCase{
				testType: serverTest,
				name:     "InvalidChannelIDSignature-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					ChannelID:  channelIDKey,
					Bugs: ProtocolBugs{
						InvalidChannelIDSignature: true,
					},
				},
				flags:         []string{"-enable-channel-id"},
				shouldFail:    true,
				expectedError: ":CHANNEL_ID_SIGNATURE_INVALID:",
			})

			if ver.version < VersionTLS13 {
				// Channel ID requires ECDHE ciphers.
				tests = append(tests, testCase{
					testType: serverTest,
					name:     "ChannelID-NoECDHE-" + ver.name,
					config: Config{
						MaxVersion:   ver.version,
						CipherSuites: []uint16{TLS_RSA_WITH_AES_128_CBC_SHA},
						ChannelID:    channelIDKey,
					},
					expectChannelID: false,
					flags:           []string{"-enable-channel-id"},
				})

				// Sanity-check setting expectChannelID false works.
				tests = append(tests, testCase{
					testType: serverTest,
					name:     "ChannelID-ECDHE-" + ver.name,
					config: Config{
						MaxVersion:   ver.version,
						CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA},
						ChannelID:    channelIDKey,
					},
					expectChannelID:    false,
					flags:              []string{"-enable-channel-id"},
					shouldFail:         true,
					expectedLocalError: "channel ID unexpectedly negotiated",
				})
			}
		}

		// Channel ID and NPN at the same time, to ensure their relative
		// ordering is correct.
		tests = append(tests, testCase{
			name: "ChannelID-NPN-Client",
			config: Config{
				MaxVersion:       VersionTLS12,
				RequestChannelID: true,
				NextProtos:       []string{"foo"},
			},
			flags: []string{
				"-send-channel-id", path.Join(*resourceDir, channelIDKeyFile),
				"-select-next-proto", "foo",
			},
			resumeSession:         true,
			expectChannelID:       true,
			expectedNextProto:     "foo",
			expectedNextProtoType: npn,
		})
		tests = append(tests, testCase{
			testType: serverTest,
			name:     "ChannelID-NPN-Server",
			config: Config{
				MaxVersion: VersionTLS12,
				ChannelID:  channelIDKey,
				NextProtos: []string{"bar"},
			},
			flags: []string{
				"-expect-channel-id",
				base64.StdEncoding.EncodeToString(channelIDBytes),
				"-advertise-npn", "\x03foo\x03bar\x03baz",
				"-expect-next-proto", "bar",
			},
			resumeSession:         true,
			expectChannelID:       true,
			expectedNextProto:     "bar",
			expectedNextProtoType: npn,
		})

		// Bidirectional shutdown with the runner initiating.
		tests = append(tests, testCase{
			name: "Shutdown-Runner",
			config: Config{
				Bugs: ProtocolBugs{
					ExpectCloseNotify: true,
				},
			},
			flags: []string{"-check-close-notify"},
		})

		if !config.implicitHandshake {
			// Bidirectional shutdown with the shim initiating. The runner,
			// in the meantime, sends garbage before the close_notify which
			// the shim must ignore. This test is disabled under implicit
			// handshake tests because the shim never reads or writes.
			tests = append(tests, testCase{
				name: "Shutdown-Shim",
				config: Config{
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown:     true,
				sendEmptyRecords:  1,
				sendWarningAlerts: 1,
				flags:             []string{"-check-close-notify"},
			})

			// The shim should reject unexpected application data
			// when shutting down.
			tests = append(tests, testCase{
				name: "Shutdown-Shim-ApplicationData",
				config: Config{
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown:     true,
				messageCount:      1,
				sendEmptyRecords:  1,
				sendWarningAlerts: 1,
				flags:             []string{"-check-close-notify"},
				shouldFail:        true,
				expectedError:     ":APPLICATION_DATA_ON_SHUTDOWN:",
			})

			// Test that SSL_shutdown still processes KeyUpdate.
			tests = append(tests, testCase{
				name: "Shutdown-Shim-KeyUpdate",
				config: Config{
					MinVersion: VersionTLS13,
					MaxVersion: VersionTLS13,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown:    true,
				sendKeyUpdates:   1,
				keyUpdateRequest: keyUpdateRequested,
				flags:            []string{"-check-close-notify"},
			})

			// Test that SSL_shutdown processes HelloRequest
			// correctly.
			tests = append(tests, testCase{
				name: "Shutdown-Shim-HelloRequest-Ignore",
				config: Config{
					MinVersion: VersionTLS12,
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						SendHelloRequestBeforeEveryAppDataRecord: true,
						ExpectCloseNotify:                        true,
					},
				},
				shimShutsDown: true,
				flags: []string{
					"-renegotiate-ignore",
					"-check-close-notify",
				},
			})
			tests = append(tests, testCase{
				name: "Shutdown-Shim-HelloRequest-Reject",
				config: Config{
					MinVersion: VersionTLS12,
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown: true,
				renegotiate:   1,
				shouldFail:    true,
				expectedError: ":NO_RENEGOTIATION:",
				flags:         []string{"-check-close-notify"},
			})
			tests = append(tests, testCase{
				name: "Shutdown-Shim-HelloRequest-CannotHandshake",
				config: Config{
					MinVersion: VersionTLS12,
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown: true,
				renegotiate:   1,
				shouldFail:    true,
				expectedError: ":NO_RENEGOTIATION:",
				flags: []string{
					"-check-close-notify",
					"-renegotiate-freely",
				},
			})

			tests = append(tests, testCase{
				testType: serverTest,
				name:     "Shutdown-Shim-Renegotiate-Server-Forbidden",
				config: Config{
					MaxVersion: VersionTLS12,
					Bugs: ProtocolBugs{
						ExpectCloseNotify: true,
					},
				},
				shimShutsDown: true,
				renegotiate:   1,
				shouldFail:    true,
				expectedError: ":NO_RENEGOTIATION:",
				flags: []string{
					"-check-close-notify",
				},
			})
		}
	} else {
		// TODO(davidben): DTLS 1.3 will want a similar thing for
		// HelloRetryRequest.
		tests = append(tests, testCase{
			name: "SkipHelloVerifyRequest",
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					SkipHelloVerifyRequest: true,
				},
			},
		})
	}

	for _, test := range tests {
		test.protocol = config.protocol
		if config.protocol == dtls {
			test.name += "-DTLS"
		}
		if config.async {
			test.name += "-Async"
			test.flags = append(test.flags, "-async")
		} else {
			test.name += "-Sync"
		}
		if config.splitHandshake {
			test.name += "-SplitHandshakeRecords"
			test.config.Bugs.MaxHandshakeRecordLength = 1
			if config.protocol == dtls {
				test.config.Bugs.MaxPacketLength = 256
				test.flags = append(test.flags, "-mtu", "256")
			}
		}
		if config.packHandshake {
			test.name += "-PackHandshake"
			if config.protocol == dtls {
				test.config.Bugs.MaxHandshakeRecordLength = 2
				test.config.Bugs.PackHandshakeFragments = 20
				test.config.Bugs.PackHandshakeRecords = 1500
				test.config.Bugs.PackAppDataWithHandshake = true
			} else {
				test.config.Bugs.PackHandshakeFlight = true
			}
		}
		if config.implicitHandshake {
			test.name += "-ImplicitHandshake"
			test.flags = append(test.flags, "-implicit-handshake")
		}
		testCases = append(testCases, test)
	}
}

func addDDoSCallbackTests() {
	// DDoS callback.
	for _, resume := range []bool{false, true} {
		suffix := "Resume"
		if resume {
			suffix = "No" + suffix
		}

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "Server-DDoS-OK-" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
			},
			flags:         []string{"-install-ddos-callback"},
			resumeSession: resume,
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "Server-DDoS-OK-" + suffix + "-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			flags:         []string{"-install-ddos-callback"},
			resumeSession: resume,
		})

		failFlag := "-fail-ddos-callback"
		if resume {
			failFlag = "-on-resume-fail-ddos-callback"
		}
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "Server-DDoS-Reject-" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
			},
			flags:              []string{"-install-ddos-callback", failFlag},
			resumeSession:      resume,
			shouldFail:         true,
			expectedError:      ":CONNECTION_REJECTED:",
			expectedLocalError: "remote error: internal error",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "Server-DDoS-Reject-" + suffix + "-TLS13",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			flags:              []string{"-install-ddos-callback", failFlag},
			resumeSession:      resume,
			shouldFail:         true,
			expectedError:      ":CONNECTION_REJECTED:",
			expectedLocalError: "remote error: internal error",
		})
	}
}

func addVersionNegotiationTests() {
	for _, protocol := range []protocol{tls, dtls} {
		for _, shimVers := range allVersions(protocol) {
			// Assemble flags to disable all newer versions on the shim.
			var flags []string
			for _, vers := range allVersions(protocol) {
				if vers.version > shimVers.version {
					flags = append(flags, vers.excludeFlag)
				}
			}

			flags2 := []string{"-max-version", shimVers.shimFlag(protocol)}

			// Test configuring the runner's maximum version.
			for _, runnerVers := range allVersions(protocol) {
				expectedVersion := shimVers.version
				if runnerVers.version < shimVers.version {
					expectedVersion = runnerVers.version
				}

				suffix := shimVers.name + "-" + runnerVers.name
				if protocol == dtls {
					suffix += "-DTLS"
				}

				// Determine the expected initial record-layer versions.
				clientVers := shimVers.version
				if clientVers > VersionTLS10 {
					clientVers = VersionTLS10
				}
				clientVers = recordVersionToWire(clientVers, protocol)
				serverVers := expectedVersion
				if expectedVersion >= VersionTLS13 {
					serverVers = VersionTLS12
				}
				serverVers = recordVersionToWire(serverVers, protocol)

				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: clientTest,
					name:     "VersionNegotiation-Client-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							ExpectInitialRecordVersion: clientVers,
						},
					},
					flags:           flags,
					expectedVersion: expectedVersion,
				})
				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: clientTest,
					name:     "VersionNegotiation-Client2-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							ExpectInitialRecordVersion: clientVers,
						},
					},
					flags:           flags2,
					expectedVersion: expectedVersion,
				})

				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: serverTest,
					name:     "VersionNegotiation-Server-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							ExpectInitialRecordVersion: serverVers,
						},
					},
					flags:           flags,
					expectedVersion: expectedVersion,
				})
				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: serverTest,
					name:     "VersionNegotiation-Server2-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							ExpectInitialRecordVersion: serverVers,
						},
					},
					flags:           flags2,
					expectedVersion: expectedVersion,
				})
			}
		}
	}

	// Test the version extension at all versions.
	for _, vers := range tlsVersions {
		protocols := []protocol{tls}
		if vers.hasDTLS {
			protocols = append(protocols, dtls)
		}
		for _, protocol := range protocols {
			suffix := vers.name
			if protocol == dtls {
				suffix += "-DTLS"
			}

			testCases = append(testCases, testCase{
				protocol: protocol,
				testType: serverTest,
				name:     "VersionNegotiationExtension-" + suffix,
				config: Config{
					Bugs: ProtocolBugs{
						SendSupportedVersions:      []uint16{0x1111, vers.wire(protocol), 0x2222},
						IgnoreTLS13DowngradeRandom: true,
					},
				},
				expectedVersion: vers.version,
			})
		}
	}

	// If all versions are unknown, negotiation fails.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoSupportedVersions",
		config: Config{
			Bugs: ProtocolBugs{
				SendSupportedVersions: []uint16{0x1111},
			},
		},
		shouldFail:    true,
		expectedError: ":UNSUPPORTED_PROTOCOL:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "NoSupportedVersions-DTLS",
		config: Config{
			Bugs: ProtocolBugs{
				SendSupportedVersions: []uint16{0x1111},
			},
		},
		shouldFail:    true,
		expectedError: ":UNSUPPORTED_PROTOCOL:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ClientHelloVersionTooHigh",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendClientVersion:          0x0304,
				OmitSupportedVersions:      true,
				IgnoreTLS13DowngradeRandom: true,
			},
		},
		expectedVersion: VersionTLS12,
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ConflictingVersionNegotiation",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:          VersionTLS12,
				SendSupportedVersions:      []uint16{VersionTLS11},
				IgnoreTLS13DowngradeRandom: true,
			},
		},
		// The extension takes precedence over the ClientHello version.
		expectedVersion: VersionTLS11,
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ConflictingVersionNegotiation-2",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:          VersionTLS11,
				SendSupportedVersions:      []uint16{VersionTLS12},
				IgnoreTLS13DowngradeRandom: true,
			},
		},
		// The extension takes precedence over the ClientHello version.
		expectedVersion: VersionTLS12,
	})

	// Test that TLS 1.2 isn't negotiated by the supported_versions extension in
	// the ServerHello.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "SupportedVersionSelection-TLS12",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendServerSupportedVersionExtension: VersionTLS12,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	// Test that the maximum version is selected regardless of the
	// client-sent order.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "IgnoreClientVersionOrder",
		config: Config{
			Bugs: ProtocolBugs{
				SendSupportedVersions: []uint16{VersionTLS12, VersionTLS13},
			},
		},
		expectedVersion: VersionTLS13,
	})

	// Test for version tolerance.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "MinorVersionTolerance",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:          0x03ff,
				OmitSupportedVersions:      true,
				IgnoreTLS13DowngradeRandom: true,
			},
		},
		expectedVersion: VersionTLS12,
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "MajorVersionTolerance",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:          0x0400,
				OmitSupportedVersions:      true,
				IgnoreTLS13DowngradeRandom: true,
			},
		},
		// TLS 1.3 must be negotiated with the supported_versions
		// extension, not ClientHello.version.
		expectedVersion: VersionTLS12,
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "VersionTolerance-TLS13",
		config: Config{
			Bugs: ProtocolBugs{
				// Although TLS 1.3 does not use
				// ClientHello.version, it still tolerates high
				// values there.
				SendClientVersion: 0x0400,
			},
		},
		expectedVersion: VersionTLS13,
	})

	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "MinorVersionTolerance-DTLS",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:     0xfe00,
				OmitSupportedVersions: true,
			},
		},
		expectedVersion: VersionTLS12,
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "MajorVersionTolerance-DTLS",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:     0xfdff,
				OmitSupportedVersions: true,
			},
		},
		expectedVersion: VersionTLS12,
	})

	// Test that versions below 3.0 are rejected.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "VersionTooLow",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion:     0x0200,
				OmitSupportedVersions: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNSUPPORTED_PROTOCOL:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "VersionTooLow-DTLS",
		config: Config{
			Bugs: ProtocolBugs{
				SendClientVersion: 0xffff,
			},
		},
		shouldFail:    true,
		expectedError: ":UNSUPPORTED_PROTOCOL:",
	})

	testCases = append(testCases, testCase{
		name: "ServerBogusVersion",
		config: Config{
			Bugs: ProtocolBugs{
				SendServerHelloVersion: 0x1234,
			},
		},
		shouldFail:    true,
		expectedError: ":UNSUPPORTED_PROTOCOL:",
	})

	// Test TLS 1.3's downgrade signal.
	var downgradeTests = []struct {
		name            string
		version         uint16
		clientShimError string
	}{
		{"TLS12", VersionTLS12, "tls: downgrade from TLS 1.3 detected"},
		{"TLS11", VersionTLS11, "tls: downgrade from TLS 1.2 detected"},
		// TLS 1.0 does not have a dedicated value.
		{"TLS10", VersionTLS10, "tls: downgrade from TLS 1.2 detected"},
	}

	for _, test := range downgradeTests {
		// The client should enforce the downgrade sentinel.
		testCases = append(testCases, testCase{
			name: "Downgrade-" + test.name + "-Client",
			config: Config{
				Bugs: ProtocolBugs{
					NegotiateVersion: test.version,
				},
			},
			expectedVersion:    test.version,
			shouldFail:         true,
			expectedError:      ":TLS13_DOWNGRADE:",
			expectedLocalError: "remote error: illegal parameter",
		})

		// The client should ignore the downgrade sentinel if
		// configured.
		testCases = append(testCases, testCase{
			name: "Downgrade-" + test.name + "-Client-Ignore",
			config: Config{
				Bugs: ProtocolBugs{
					NegotiateVersion: test.version,
				},
			},
			expectedVersion: test.version,
			flags: []string{
				"-ignore-tls13-downgrade",
				"-expect-tls13-downgrade",
			},
		})

		// The server should emit the downgrade signal.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "Downgrade-" + test.name + "-Server",
			config: Config{
				Bugs: ProtocolBugs{
					SendSupportedVersions: []uint16{test.version},
				},
			},
			expectedVersion:    test.version,
			shouldFail:         true,
			expectedLocalError: test.clientShimError,
		})
	}

	// Test that False Start is disabled when the downgrade logic triggers.
	testCases = append(testCases, testCase{
		name: "Downgrade-FalseStart",
		config: Config{
			NextProtos: []string{"foo"},
			Bugs: ProtocolBugs{
				NegotiateVersion:          VersionTLS12,
				ExpectFalseStart:          true,
				AlertBeforeFalseStartTest: alertAccessDenied,
			},
		},
		expectedVersion: VersionTLS12,
		flags: []string{
			"-false-start",
			"-advertise-alpn", "\x03foo",
			"-ignore-tls13-downgrade",
		},
		shimWritesFirst:    true,
		shouldFail:         true,
		expectedError:      ":TLSV1_ALERT_ACCESS_DENIED:",
		expectedLocalError: "tls: peer did not false start: EOF",
	})

	// SSL 3.0 support has been removed. Test that the shim does not
	// support it.
	testCases = append(testCases, testCase{
		name: "NoSSL3-Client",
		config: Config{
			MinVersion: VersionSSL30,
			MaxVersion: VersionSSL30,
		},
		shouldFail:         true,
		expectedLocalError: "tls: client did not offer any supported protocol versions",
	})
	testCases = append(testCases, testCase{
		name: "NoSSL3-Client-Unsolicited",
		config: Config{
			MinVersion: VersionSSL30,
			MaxVersion: VersionSSL30,
			Bugs: ProtocolBugs{
				// The above test asserts the client does not
				// offer SSL 3.0 in the supported_versions
				// list. Additionally assert that it rejects an
				// unsolicited SSL 3.0 ServerHello.
				NegotiateVersion: VersionSSL30,
			},
		},
		shouldFail:         true,
		expectedError:      ":UNSUPPORTED_PROTOCOL:",
		expectedLocalError: "remote error: protocol version not supported",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoSSL3-Server",
		config: Config{
			MinVersion: VersionSSL30,
			MaxVersion: VersionSSL30,
		},
		shouldFail:         true,
		expectedError:      ":UNSUPPORTED_PROTOCOL:",
		expectedLocalError: "remote error: protocol version not supported",
	})
}

func addMinimumVersionTests() {
	for _, protocol := range []protocol{tls, dtls} {
		for _, shimVers := range allVersions(protocol) {
			// Assemble flags to disable all older versions on the shim.
			var flags []string
			for _, vers := range allVersions(protocol) {
				if vers.version < shimVers.version {
					flags = append(flags, vers.excludeFlag)
				}
			}

			flags2 := []string{"-min-version", shimVers.shimFlag(protocol)}

			for _, runnerVers := range allVersions(protocol) {
				suffix := shimVers.name + "-" + runnerVers.name
				if protocol == dtls {
					suffix += "-DTLS"
				}

				var expectedVersion uint16
				var shouldFail bool
				var expectedError, expectedLocalError string
				if runnerVers.version >= shimVers.version {
					expectedVersion = runnerVers.version
				} else {
					shouldFail = true
					expectedError = ":UNSUPPORTED_PROTOCOL:"
					expectedLocalError = "remote error: protocol version not supported"
				}

				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: clientTest,
					name:     "MinimumVersion-Client-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							// Ensure the server does not decline to
							// select a version (versions extension) or
							// cipher (some ciphers depend on versions).
							NegotiateVersion:            runnerVers.wire(protocol),
							IgnorePeerCipherPreferences: shouldFail,
						},
					},
					flags:              flags,
					expectedVersion:    expectedVersion,
					shouldFail:         shouldFail,
					expectedError:      expectedError,
					expectedLocalError: expectedLocalError,
				})
				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: clientTest,
					name:     "MinimumVersion-Client2-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
						Bugs: ProtocolBugs{
							// Ensure the server does not decline to
							// select a version (versions extension) or
							// cipher (some ciphers depend on versions).
							NegotiateVersion:            runnerVers.wire(protocol),
							IgnorePeerCipherPreferences: shouldFail,
						},
					},
					flags:              flags2,
					expectedVersion:    expectedVersion,
					shouldFail:         shouldFail,
					expectedError:      expectedError,
					expectedLocalError: expectedLocalError,
				})

				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: serverTest,
					name:     "MinimumVersion-Server-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
					},
					flags:              flags,
					expectedVersion:    expectedVersion,
					shouldFail:         shouldFail,
					expectedError:      expectedError,
					expectedLocalError: expectedLocalError,
				})
				testCases = append(testCases, testCase{
					protocol: protocol,
					testType: serverTest,
					name:     "MinimumVersion-Server2-" + suffix,
					config: Config{
						MaxVersion: runnerVers.version,
					},
					flags:              flags2,
					expectedVersion:    expectedVersion,
					shouldFail:         shouldFail,
					expectedError:      expectedError,
					expectedLocalError: expectedLocalError,
				})
			}
		}
	}
}

func addExtensionTests() {
	// TODO(davidben): Extensions, where applicable, all move their server
	// halves to EncryptedExtensions in TLS 1.3. Duplicate each of these
	// tests for both. Also test interaction with 0-RTT when implemented.

	// Repeat extensions tests at all versions.
	for _, ver := range tlsVersions {
		// Test that duplicate extensions are rejected.
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "DuplicateExtensionClient-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					DuplicateExtension: true,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: error decoding message",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "DuplicateExtensionServer-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					DuplicateExtension: true,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: error decoding message",
		})

		// Test SNI.
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ServerNameExtensionClient-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					ExpectServerName: "example.com",
				},
			},
			flags: []string{"-host-name", "example.com"},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ServerNameExtensionClientMismatch-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					ExpectServerName: "mismatch.com",
				},
			},
			flags:              []string{"-host-name", "example.com"},
			shouldFail:         true,
			expectedLocalError: "tls: unexpected server name",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ServerNameExtensionClientMissing-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					ExpectServerName: "missing.com",
				},
			},
			shouldFail:         true,
			expectedLocalError: "tls: unexpected server name",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TolerateServerNameAck-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendServerNameAck: true,
				},
			},
			flags:         []string{"-host-name", "example.com"},
			resumeSession: true,
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "UnsolicitedServerNameAck-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendServerNameAck: true,
				},
			},
			shouldFail:         true,
			expectedError:      ":UNEXPECTED_EXTENSION:",
			expectedLocalError: "remote error: unsupported extension",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ServerNameExtensionServer-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				ServerName: "example.com",
			},
			flags:         []string{"-expect-server-name", "example.com"},
			resumeSession: true,
		})

		// Test ALPN.
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ALPNClient-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{"foo"},
			},
			flags: []string{
				"-advertise-alpn", "\x03foo\x03bar\x03baz",
				"-expect-alpn", "foo",
			},
			expectedNextProto:     "foo",
			expectedNextProtoType: alpn,
			resumeSession:         true,
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ALPNClient-RejectUnknown-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendALPN: "baz",
				},
			},
			flags: []string{
				"-advertise-alpn", "\x03foo\x03bar",
			},
			shouldFail:         true,
			expectedError:      ":INVALID_ALPN_PROTOCOL:",
			expectedLocalError: "remote error: illegal parameter",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ALPNClient-AllowUnknown-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendALPN: "baz",
				},
			},
			flags: []string{
				"-advertise-alpn", "\x03foo\x03bar",
				"-allow-unknown-alpn-protos",
				"-expect-alpn", "baz",
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ALPNServer-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{"foo", "bar", "baz"},
			},
			flags: []string{
				"-expect-advertised-alpn", "\x03foo\x03bar\x03baz",
				"-select-alpn", "foo",
			},
			expectedNextProto:     "foo",
			expectedNextProtoType: alpn,
			resumeSession:         true,
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ALPNServer-Decline-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{"foo", "bar", "baz"},
			},
			flags:             []string{"-decline-alpn"},
			expectNoNextProto: true,
			resumeSession:     true,
		})
		// Test that the server implementation catches itself if the
		// callback tries to return an invalid empty ALPN protocol.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ALPNServer-SelectEmpty-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{"foo", "bar", "baz"},
			},
			flags: []string{
				"-expect-advertised-alpn", "\x03foo\x03bar\x03baz",
				"-select-empty-alpn",
			},
			shouldFail:         true,
			expectedLocalError: "remote error: internal error",
			expectedError:      ":INVALID_ALPN_PROTOCOL:",
		})

		// Test ALPN in async mode as well to ensure that extensions callbacks are only
		// called once.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ALPNServer-Async-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{"foo", "bar", "baz"},
				// Prior to TLS 1.3, exercise the asynchronous session callback.
				SessionTicketsDisabled: ver.version < VersionTLS13,
			},
			flags: []string{
				"-expect-advertised-alpn", "\x03foo\x03bar\x03baz",
				"-select-alpn", "foo",
				"-async",
			},
			expectedNextProto:     "foo",
			expectedNextProtoType: alpn,
			resumeSession:         true,
		})

		var emptyString string
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ALPNClient-EmptyProtocolName-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				NextProtos: []string{""},
				Bugs: ProtocolBugs{
					// A server returning an empty ALPN protocol
					// should be rejected.
					ALPNProtocol: &emptyString,
				},
			},
			flags: []string{
				"-advertise-alpn", "\x03foo",
			},
			shouldFail:    true,
			expectedError: ":PARSE_TLSEXT:",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ALPNServer-EmptyProtocolName-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				// A ClientHello containing an empty ALPN protocol
				// should be rejected.
				NextProtos: []string{"foo", "", "baz"},
			},
			flags: []string{
				"-select-alpn", "foo",
			},
			shouldFail:    true,
			expectedError: ":PARSE_TLSEXT:",
		})

		// Test NPN and the interaction with ALPN.
		if ver.version < VersionTLS13 {
			// Test that the server prefers ALPN over NPN.
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "ALPNServer-Preferred-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					NextProtos: []string{"foo", "bar", "baz"},
				},
				flags: []string{
					"-expect-advertised-alpn", "\x03foo\x03bar\x03baz",
					"-select-alpn", "foo",
					"-advertise-npn", "\x03foo\x03bar\x03baz",
				},
				expectedNextProto:     "foo",
				expectedNextProtoType: alpn,
				resumeSession:         true,
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "ALPNServer-Preferred-Swapped-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					NextProtos: []string{"foo", "bar", "baz"},
					Bugs: ProtocolBugs{
						SwapNPNAndALPN: true,
					},
				},
				flags: []string{
					"-expect-advertised-alpn", "\x03foo\x03bar\x03baz",
					"-select-alpn", "foo",
					"-advertise-npn", "\x03foo\x03bar\x03baz",
				},
				expectedNextProto:     "foo",
				expectedNextProtoType: alpn,
				resumeSession:         true,
			})

			// Test that negotiating both NPN and ALPN is forbidden.
			testCases = append(testCases, testCase{
				name: "NegotiateALPNAndNPN-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					NextProtos: []string{"foo", "bar", "baz"},
					Bugs: ProtocolBugs{
						NegotiateALPNAndNPN: true,
					},
				},
				flags: []string{
					"-advertise-alpn", "\x03foo",
					"-select-next-proto", "foo",
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_BOTH_NPN_AND_ALPN:",
			})
			testCases = append(testCases, testCase{
				name: "NegotiateALPNAndNPN-Swapped-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					NextProtos: []string{"foo", "bar", "baz"},
					Bugs: ProtocolBugs{
						NegotiateALPNAndNPN: true,
						SwapNPNAndALPN:      true,
					},
				},
				flags: []string{
					"-advertise-alpn", "\x03foo",
					"-select-next-proto", "foo",
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_BOTH_NPN_AND_ALPN:",
			})
		}

		// Test Token Binding.

		const maxTokenBindingVersion = 16
		const minTokenBindingVersion = 13
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{0, 1, 2},
				TokenBindingVersion: maxTokenBindingVersion,
			},
			expectTokenBinding:        true,
			expectedTokenBindingParam: 2,
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				"-expect-token-binding-param",
				"2",
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-UnsupportedParam-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{3},
				TokenBindingVersion: maxTokenBindingVersion,
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-OldVersion-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{0, 1, 2},
				TokenBindingVersion: minTokenBindingVersion - 1,
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-NewVersion-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{0, 1, 2},
				TokenBindingVersion: maxTokenBindingVersion + 1,
			},
			expectTokenBinding:        true,
			expectedTokenBindingParam: 2,
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				"-expect-token-binding-param",
				"2",
			},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-NoParams-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{},
				TokenBindingVersion: maxTokenBindingVersion,
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TokenBinding-Server-RepeatedParam" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{0, 1, 2, 2},
				TokenBindingVersion: maxTokenBindingVersion,
			},
			expectTokenBinding:        true,
			expectedTokenBindingParam: 2,
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				"-expect-token-binding-param",
				"2",
			},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{2},
				TokenBindingVersion:      maxTokenBindingVersion,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
				"-expect-token-binding-param",
				"2",
			},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-Unexpected-" + ver.name,

			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				TokenBindingParams:  []byte{2},
				TokenBindingVersion: maxTokenBindingVersion,
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_EXTENSION:",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-ExtraParams-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{2, 1},
				TokenBindingVersion:      maxTokenBindingVersion,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
				"-expect-token-binding-param",
				"2",
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-NoParams-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{},
				TokenBindingVersion:      maxTokenBindingVersion,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
				"-expect-token-binding-param",
				"2",
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-WrongParam-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{3},
				TokenBindingVersion:      maxTokenBindingVersion,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
				"-expect-token-binding-param",
				"2",
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-OldVersion-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{2},
				TokenBindingVersion:      minTokenBindingVersion - 1,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
			},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-MinVersion-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{2},
				TokenBindingVersion:      minTokenBindingVersion,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
				"-expect-token-binding-param",
				"2",
			},
		})
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "TokenBinding-Client-VersionTooNew-" + ver.name,

			config: Config{
				MinVersion:               ver.version,
				MaxVersion:               ver.version,
				TokenBindingParams:       []byte{2},
				TokenBindingVersion:      maxTokenBindingVersion + 1,
				ExpectTokenBindingParams: []byte{0, 1, 2},
			},
			flags: []string{
				"-token-binding-params",
				base64.StdEncoding.EncodeToString([]byte{0, 1, 2}),
			},
			shouldFail:    true,
			expectedError: "ERROR_PARSING_EXTENSION",
		})
		if ver.version < VersionTLS13 {
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "TokenBinding-Client-NoEMS-" + ver.name,

				config: Config{
					MinVersion:               ver.version,
					MaxVersion:               ver.version,
					TokenBindingParams:       []byte{2},
					TokenBindingVersion:      maxTokenBindingVersion,
					ExpectTokenBindingParams: []byte{2, 1, 0},
					Bugs: ProtocolBugs{
						NoExtendedMasterSecret: true,
					},
				},
				flags: []string{
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_TB_WITHOUT_EMS_OR_RI:",
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TokenBinding-Server-NoEMS-" + ver.name,

				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					TokenBindingParams:  []byte{0, 1, 2},
					TokenBindingVersion: maxTokenBindingVersion,
					Bugs: ProtocolBugs{
						NoExtendedMasterSecret: true,
					},
				},
				flags: []string{
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_TB_WITHOUT_EMS_OR_RI:",
			})
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "TokenBinding-Client-NoRI-" + ver.name,

				config: Config{
					MinVersion:               ver.version,
					MaxVersion:               ver.version,
					TokenBindingParams:       []byte{2},
					TokenBindingVersion:      maxTokenBindingVersion,
					ExpectTokenBindingParams: []byte{2, 1, 0},
					Bugs: ProtocolBugs{
						NoRenegotiationInfo: true,
					},
				},
				flags: []string{
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_TB_WITHOUT_EMS_OR_RI:",
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TokenBinding-Server-NoRI-" + ver.name,

				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					TokenBindingParams:  []byte{0, 1, 2},
					TokenBindingVersion: maxTokenBindingVersion,
					Bugs: ProtocolBugs{
						NoRenegotiationInfo: true,
					},
				},
				flags: []string{
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				},
				shouldFail:    true,
				expectedError: ":NEGOTIATED_TB_WITHOUT_EMS_OR_RI:",
			})
		} else {
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "TokenBinding-WithEarlyDataFails-" + ver.name,
				config: Config{
					MinVersion:               ver.version,
					MaxVersion:               ver.version,
					TokenBindingParams:       []byte{2},
					TokenBindingVersion:      maxTokenBindingVersion,
					ExpectTokenBindingParams: []byte{2, 1, 0},
					MaxEarlyDataSize:         16384,
				},
				resumeSession: true,
				flags: []string{
					"-enable-early-data",
					"-expect-ticket-supports-early-data",
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
				},
				shouldFail:    true,
				expectedError: ":UNEXPECTED_EXTENSION_ON_EARLY_DATA:",
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TokenBinding-EarlyDataRejected-" + ver.name,
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					TokenBindingParams:  []byte{0, 1, 2},
					TokenBindingVersion: maxTokenBindingVersion,
					MaxEarlyDataSize:    16384,
				},
				resumeSession:             true,
				expectTokenBinding:        true,
				expectedTokenBindingParam: 2,
				flags: []string{
					"-enable-early-data",
					"-expect-ticket-supports-early-data",
					"-token-binding-params",
					base64.StdEncoding.EncodeToString([]byte{2, 1, 0}),
					"-expect-reject-early-data",
					"-on-retry-expect-early-data-reason", "token_binding",
				},
			})
		}

		// Test QUIC transport params
		if ver.version >= VersionTLS13 {
			// Client sends params
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "QUICTransportParams-Client-" + ver.name,
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					QUICTransportParams: []byte{1, 2},
				},
				flags: []string{
					"-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{3, 4}),
					"-expect-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{1, 2}),
				},
				expectedQUICTransportParams: []byte{3, 4},
			})
			// Server sends params
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "QUICTransportParams-Server-" + ver.name,
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					QUICTransportParams: []byte{1, 2},
				},
				flags: []string{
					"-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{3, 4}),
					"-expect-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{1, 2}),
				},
				expectedQUICTransportParams: []byte{3, 4},
			})
		} else {
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "QUICTransportParams-Client-NotSent-" + ver.name,
				config: Config{
					MinVersion: ver.version,
					MaxVersion: ver.version,
				},
				flags: []string{
					"-max-version",
					strconv.Itoa(int(ver.version)),
					"-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{3, 4}),
				},
			})
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "QUICTransportParams-Client-Rejected-" + ver.name,
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					QUICTransportParams: []byte{1, 2},
				},
				flags: []string{
					"-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{3, 4}),
				},
				shouldFail:    true,
				expectedError: ":ERROR_PARSING_EXTENSION:",
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "QUICTransportParams-Server-Rejected-" + ver.name,
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					QUICTransportParams: []byte{1, 2},
				},
				flags: []string{
					"-expect-quic-transport-params",
					base64.StdEncoding.EncodeToString([]byte{1, 2}),
				},
				shouldFail:    true,
				expectedError: "QUIC transport params mismatch",
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "QUICTransportParams-OldServerIgnores-" + ver.name,
				config: Config{
					MaxVersion:          VersionTLS13,
					QUICTransportParams: []byte{1, 2},
				},
				flags: []string{
					"-min-version", ver.shimFlag(tls),
					"-max-version", ver.shimFlag(tls),
				},
			})
		}

		// Test ticket behavior.

		// Resume with a corrupt ticket.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "CorruptTicket-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					FilterTicket: func(in []byte) ([]byte, error) {
						in[len(in)-1] ^= 1
						return in, nil
					},
				},
			},
			resumeSession:        true,
			expectResumeRejected: true,
		})
		// Test the ticket callback, with and without renewal.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TicketCallback-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
			},
			resumeSession: true,
			flags:         []string{"-use-ticket-callback"},
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TicketCallback-Renew-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					ExpectNewTicket: true,
				},
			},
			flags:         []string{"-use-ticket-callback", "-renew-ticket"},
			resumeSession: true,
		})

		// Test that the ticket callback is only called once when everything before
		// it in the ClientHello is asynchronous. This corrupts the ticket so
		// certificate selection callbacks run.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "TicketCallback-SingleCall-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					FilterTicket: func(in []byte) ([]byte, error) {
						in[len(in)-1] ^= 1
						return in, nil
					},
				},
			},
			resumeSession:        true,
			expectResumeRejected: true,
			flags: []string{
				"-use-ticket-callback",
				"-async",
			},
		})

		// Resume with various lengths of ticket session id.
		if ver.version < VersionTLS13 {
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TicketSessionIDLength-0-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					Bugs: ProtocolBugs{
						EmptyTicketSessionID: true,
					},
				},
				resumeSession: true,
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TicketSessionIDLength-16-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					Bugs: ProtocolBugs{
						TicketSessionIDLength: 16,
					},
				},
				resumeSession: true,
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TicketSessionIDLength-32-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					Bugs: ProtocolBugs{
						TicketSessionIDLength: 32,
					},
				},
				resumeSession: true,
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "TicketSessionIDLength-33-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					Bugs: ProtocolBugs{
						TicketSessionIDLength: 33,
					},
				},
				resumeSession: true,
				shouldFail:    true,
				// The maximum session ID length is 32.
				expectedError: ":DECODE_ERROR:",
			})
		}

		// Basic DTLS-SRTP tests. Include fake profiles to ensure they
		// are ignored.
		if ver.hasDTLS {
			testCases = append(testCases, testCase{
				protocol: dtls,
				name:     "SRTP-Client-" + ver.name,
				config: Config{
					MaxVersion:             ver.version,
					SRTPProtectionProfiles: []uint16{40, SRTP_AES128_CM_HMAC_SHA1_80, 42},
				},
				flags: []string{
					"-srtp-profiles",
					"SRTP_AES128_CM_SHA1_80:SRTP_AES128_CM_SHA1_32",
				},
				expectedSRTPProtectionProfile: SRTP_AES128_CM_HMAC_SHA1_80,
			})
			testCases = append(testCases, testCase{
				protocol: dtls,
				testType: serverTest,
				name:     "SRTP-Server-" + ver.name,
				config: Config{
					MaxVersion:             ver.version,
					SRTPProtectionProfiles: []uint16{40, SRTP_AES128_CM_HMAC_SHA1_80, 42},
				},
				flags: []string{
					"-srtp-profiles",
					"SRTP_AES128_CM_SHA1_80:SRTP_AES128_CM_SHA1_32",
				},
				expectedSRTPProtectionProfile: SRTP_AES128_CM_HMAC_SHA1_80,
			})
			// Test that the MKI is ignored.
			testCases = append(testCases, testCase{
				protocol: dtls,
				testType: serverTest,
				name:     "SRTP-Server-IgnoreMKI-" + ver.name,
				config: Config{
					MaxVersion:             ver.version,
					SRTPProtectionProfiles: []uint16{SRTP_AES128_CM_HMAC_SHA1_80},
					Bugs: ProtocolBugs{
						SRTPMasterKeyIdentifer: "bogus",
					},
				},
				flags: []string{
					"-srtp-profiles",
					"SRTP_AES128_CM_SHA1_80:SRTP_AES128_CM_SHA1_32",
				},
				expectedSRTPProtectionProfile: SRTP_AES128_CM_HMAC_SHA1_80,
			})
			// Test that SRTP isn't negotiated on the server if there were
			// no matching profiles.
			testCases = append(testCases, testCase{
				protocol: dtls,
				testType: serverTest,
				name:     "SRTP-Server-NoMatch-" + ver.name,
				config: Config{
					MaxVersion:             ver.version,
					SRTPProtectionProfiles: []uint16{100, 101, 102},
				},
				flags: []string{
					"-srtp-profiles",
					"SRTP_AES128_CM_SHA1_80:SRTP_AES128_CM_SHA1_32",
				},
				expectedSRTPProtectionProfile: 0,
			})
			// Test that the server returning an invalid SRTP profile is
			// flagged as an error by the client.
			testCases = append(testCases, testCase{
				protocol: dtls,
				name:     "SRTP-Client-NoMatch-" + ver.name,
				config: Config{
					MaxVersion: ver.version,
					Bugs: ProtocolBugs{
						SendSRTPProtectionProfile: SRTP_AES128_CM_HMAC_SHA1_32,
					},
				},
				flags: []string{
					"-srtp-profiles",
					"SRTP_AES128_CM_SHA1_80",
				},
				shouldFail:    true,
				expectedError: ":BAD_SRTP_PROTECTION_PROFILE_LIST:",
			})
		}

		// Test SCT list.
		testCases = append(testCases, testCase{
			name:     "SignedCertificateTimestampList-Client-" + ver.name,
			testType: clientTest,
			config: Config{
				MaxVersion: ver.version,
			},
			flags: []string{
				"-enable-signed-cert-timestamps",
				"-expect-signed-cert-timestamps",
				base64.StdEncoding.EncodeToString(testSCTList),
			},
			resumeSession: true,
		})

		var differentSCTList []byte
		differentSCTList = append(differentSCTList, testSCTList...)
		differentSCTList[len(differentSCTList)-1] ^= 1

		// The SCT extension did not specify that it must only be sent on resumption as it
		// should have, so test that we tolerate but ignore it.
		testCases = append(testCases, testCase{
			name: "SendSCTListOnResume-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendSCTListOnResume: differentSCTList,
				},
			},
			flags: []string{
				"-enable-signed-cert-timestamps",
				"-expect-signed-cert-timestamps",
				base64.StdEncoding.EncodeToString(testSCTList),
			},
			resumeSession: true,
		})

		testCases = append(testCases, testCase{
			name:     "SignedCertificateTimestampList-Server-" + ver.name,
			testType: serverTest,
			config: Config{
				MaxVersion: ver.version,
			},
			flags: []string{
				"-signed-cert-timestamps",
				base64.StdEncoding.EncodeToString(testSCTList),
			},
			expectedSCTList: testSCTList,
			resumeSession:   true,
		})

		emptySCTListCert := *testCerts[0].cert
		emptySCTListCert.SignedCertificateTimestampList = []byte{0, 0}

		// Test empty SCT list.
		testCases = append(testCases, testCase{
			name:     "SignedCertificateTimestampListEmpty-Client-" + ver.name,
			testType: clientTest,
			config: Config{
				MaxVersion:   ver.version,
				Certificates: []Certificate{emptySCTListCert},
			},
			flags: []string{
				"-enable-signed-cert-timestamps",
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})

		emptySCTCert := *testCerts[0].cert
		emptySCTCert.SignedCertificateTimestampList = []byte{0, 6, 0, 2, 1, 2, 0, 0}

		// Test empty SCT in non-empty list.
		testCases = append(testCases, testCase{
			name:     "SignedCertificateTimestampListEmptySCT-Client-" + ver.name,
			testType: clientTest,
			config: Config{
				MaxVersion:   ver.version,
				Certificates: []Certificate{emptySCTCert},
			},
			flags: []string{
				"-enable-signed-cert-timestamps",
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})

		// Test that certificate-related extensions are not sent unsolicited.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "UnsolicitedCertificateExtensions-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					NoOCSPStapling:                true,
					NoSignedCertificateTimestamps: true,
				},
			},
			flags: []string{
				"-ocsp-response",
				base64.StdEncoding.EncodeToString(testOCSPResponse),
				"-signed-cert-timestamps",
				base64.StdEncoding.EncodeToString(testSCTList),
			},
		})
	}

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ClientHelloPadding",
		config: Config{
			Bugs: ProtocolBugs{
				RequireClientHelloSize: 512,
			},
		},
		// This hostname just needs to be long enough to push the
		// ClientHello into F5's danger zone between 256 and 511 bytes
		// long.
		flags: []string{"-host-name", "01234567890123456789012345678901234567890123456789012345678901234567890123456789.com"},
	})

	// Test that illegal extensions in TLS 1.3 are rejected by the client if
	// in ServerHello.
	testCases = append(testCases, testCase{
		name: "NPN-Forbidden-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"foo"},
			Bugs: ProtocolBugs{
				NegotiateNPNAtAllVersions: true,
			},
		},
		flags:         []string{"-select-next-proto", "foo"},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})
	testCases = append(testCases, testCase{
		name: "EMS-Forbidden-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				NegotiateEMSAtAllVersions: true,
			},
		},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})
	testCases = append(testCases, testCase{
		name: "RenegotiationInfo-Forbidden-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				NegotiateRenegotiationInfoAtAllVersions: true,
			},
		},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})
	testCases = append(testCases, testCase{
		name: "Ticket-Forbidden-TLS13",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				AdvertiseTicketExtension: true,
			},
		},
		resumeSession: true,
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})

	// Test that illegal extensions in TLS 1.3 are declined by the server if
	// offered in ClientHello. The runner's server will fail if this occurs,
	// so we exercise the offering path. (EMS and Renegotiation Info are
	// implicit in every test.)
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NPN-Declined-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"bar"},
		},
		flags: []string{"-advertise-npn", "\x03foo\x03bar\x03baz"},
	})

	// OpenSSL sends the status_request extension on resumption in TLS 1.2. Test that this is
	// tolerated.
	testCases = append(testCases, testCase{
		name: "SendOCSPResponseOnResume-TLS12",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendOCSPResponseOnResume: []byte("bogus"),
			},
		},
		flags: []string{
			"-enable-ocsp-stapling",
			"-expect-ocsp-response",
			base64.StdEncoding.EncodeToString(testOCSPResponse),
		},
		resumeSession: true,
	})

	testCases = append(testCases, testCase{
		name: "SendUnsolicitedOCSPOnCertificate-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendExtensionOnCertificate: testOCSPExtension,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	testCases = append(testCases, testCase{
		name: "SendUnsolicitedSCTOnCertificate-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendExtensionOnCertificate: testSCTExtension,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	// Test that extensions on client certificates are never accepted.
	testCases = append(testCases, testCase{
		name:     "SendExtensionOnClientCertificate-TLS13",
		testType: serverTest,
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				SendExtensionOnCertificate: testOCSPExtension,
			},
		},
		flags: []string{
			"-enable-ocsp-stapling",
			"-require-any-client-certificate",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	testCases = append(testCases, testCase{
		name: "SendUnknownExtensionOnCertificate-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendExtensionOnCertificate: []byte{0x00, 0x7f, 0, 0},
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	// Test that extensions on intermediates are allowed but ignored.
	testCases = append(testCases, testCase{
		name: "IgnoreExtensionsOnIntermediates-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaChainCertificate},
			Bugs: ProtocolBugs{
				// Send different values on the intermediate. This tests
				// the intermediate's extensions do not override the
				// leaf's.
				SendOCSPOnIntermediates: testOCSPResponse2,
				SendSCTOnIntermediates:  testSCTList2,
			},
		},
		flags: []string{
			"-enable-ocsp-stapling",
			"-expect-ocsp-response",
			base64.StdEncoding.EncodeToString(testOCSPResponse),
			"-enable-signed-cert-timestamps",
			"-expect-signed-cert-timestamps",
			base64.StdEncoding.EncodeToString(testSCTList),
		},
		resumeSession: true,
	})

	// Test that extensions are not sent on intermediates when configured
	// only for a leaf.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SendNoExtensionsOnIntermediate-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectNoExtensionsOnIntermediate: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaChainKeyFile),
			"-ocsp-response",
			base64.StdEncoding.EncodeToString(testOCSPResponse),
			"-signed-cert-timestamps",
			base64.StdEncoding.EncodeToString(testSCTList),
		},
	})

	// Test that extensions are not sent on client certificates.
	testCases = append(testCases, testCase{
		name: "SendNoClientCertificateExtensions-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-ocsp-response",
			base64.StdEncoding.EncodeToString(testOCSPResponse),
			"-signed-cert-timestamps",
			base64.StdEncoding.EncodeToString(testSCTList),
		},
	})

	testCases = append(testCases, testCase{
		name: "SendDuplicateExtensionsOnCerts-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendDuplicateCertExtensions: true,
			},
		},
		flags: []string{
			"-enable-ocsp-stapling",
			"-enable-signed-cert-timestamps",
		},
		resumeSession: true,
		shouldFail:    true,
		expectedError: ":DUPLICATE_EXTENSION:",
	})

	testCases = append(testCases, testCase{
		name:     "SignedCertificateTimestampListInvalid-Server",
		testType: serverTest,
		flags: []string{
			"-signed-cert-timestamps",
			base64.StdEncoding.EncodeToString([]byte{0, 0}),
		},
		shouldFail:    true,
		expectedError: ":INVALID_SCT_LIST:",
	})
}

func addResumptionVersionTests() {
	for _, sessionVers := range tlsVersions {
		for _, resumeVers := range tlsVersions {
			protocols := []protocol{tls}
			if sessionVers.hasDTLS && resumeVers.hasDTLS {
				protocols = append(protocols, dtls)
			}
			for _, protocol := range protocols {
				suffix := "-" + sessionVers.name + "-" + resumeVers.name
				if protocol == dtls {
					suffix += "-DTLS"
				}

				if sessionVers.version == resumeVers.version {
					testCases = append(testCases, testCase{
						protocol:      protocol,
						name:          "Resume-Client" + suffix,
						resumeSession: true,
						config: Config{
							MaxVersion: sessionVers.version,
							Bugs: ProtocolBugs{
								ExpectNoTLS13PSK: sessionVers.version < VersionTLS13,
							},
						},
						expectedVersion:       sessionVers.version,
						expectedResumeVersion: resumeVers.version,
					})
				} else {
					testCases = append(testCases, testCase{
						protocol:      protocol,
						name:          "Resume-Client-Mismatch" + suffix,
						resumeSession: true,
						config: Config{
							MaxVersion: sessionVers.version,
						},
						expectedVersion: sessionVers.version,
						resumeConfig: &Config{
							MaxVersion: resumeVers.version,
							Bugs: ProtocolBugs{
								AcceptAnySession: true,
							},
						},
						expectedResumeVersion: resumeVers.version,
						shouldFail:            true,
						expectedError:         ":OLD_SESSION_VERSION_NOT_RETURNED:",
					})
				}

				testCases = append(testCases, testCase{
					protocol:      protocol,
					name:          "Resume-Client-NoResume" + suffix,
					resumeSession: true,
					config: Config{
						MaxVersion: sessionVers.version,
					},
					expectedVersion: sessionVers.version,
					resumeConfig: &Config{
						MaxVersion: resumeVers.version,
					},
					newSessionsOnResume:   true,
					expectResumeRejected:  true,
					expectedResumeVersion: resumeVers.version,
				})

				testCases = append(testCases, testCase{
					protocol:      protocol,
					testType:      serverTest,
					name:          "Resume-Server" + suffix,
					resumeSession: true,
					config: Config{
						MaxVersion: sessionVers.version,
					},
					expectedVersion:      sessionVers.version,
					expectResumeRejected: sessionVers != resumeVers,
					resumeConfig: &Config{
						MaxVersion: resumeVers.version,
						Bugs: ProtocolBugs{
							SendBothTickets: true,
						},
					},
					expectedResumeVersion: resumeVers.version,
				})

				// Repeat the test using session IDs, rather than tickets.
				if sessionVers.version < VersionTLS13 && resumeVers.version < VersionTLS13 {
					testCases = append(testCases, testCase{
						protocol:      protocol,
						testType:      serverTest,
						name:          "Resume-Server-NoTickets" + suffix,
						resumeSession: true,
						config: Config{
							MaxVersion:             sessionVers.version,
							SessionTicketsDisabled: true,
						},
						expectedVersion:      sessionVers.version,
						expectResumeRejected: sessionVers != resumeVers,
						resumeConfig: &Config{
							MaxVersion:             resumeVers.version,
							SessionTicketsDisabled: true,
						},
						expectedResumeVersion: resumeVers.version,
					})
				}
			}
		}
	}

	// Make sure shim ticket mutations are functional.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "ShimTicketRewritable",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				FilterTicket: func(in []byte) ([]byte, error) {
					in, err := SetShimTicketVersion(in, VersionTLS12)
					if err != nil {
						return nil, err
					}
					return SetShimTicketCipherSuite(in, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
	})

	// Resumptions are declined if the version does not match.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-DeclineCrossVersion",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ExpectNewTicket: true,
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketVersion(in, VersionTLS13)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		expectResumeRejected: true,
	})

	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-DeclineCrossVersion-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketVersion(in, VersionTLS12)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		expectResumeRejected: true,
	})

	// Resumptions are declined if the cipher is invalid or disabled.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-DeclineBadCipher",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ExpectNewTicket: true,
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketCipherSuite(in, TLS_AES_128_GCM_SHA256)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		expectResumeRejected: true,
	})

	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-DeclineBadCipher-2",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ExpectNewTicket: true,
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketCipherSuite(in, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384)
				},
			},
		},
		flags: []string{
			"-cipher", "AES128",
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		expectResumeRejected: true,
	})

	// Sessions are not resumed if they do not use the preferred cipher.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-CipherNotPreferred",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ExpectNewTicket: true,
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketCipherSuite(in, TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		shouldFail:           false,
		expectResumeRejected: true,
	})

	// TLS 1.3 allows sessions to be resumed at a different cipher if their
	// PRF hashes match, but BoringSSL will always decline such resumptions.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-CipherNotPreferred-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_CHACHA20_POLY1305_SHA256, TLS_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				FilterTicket: func(in []byte) ([]byte, error) {
					// If the client (runner) offers ChaCha20-Poly1305 first, the
					// server (shim) always prefers it. Switch it to AES-GCM.
					return SetShimTicketCipherSuite(in, TLS_AES_128_GCM_SHA256)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		shouldFail:           false,
		expectResumeRejected: true,
	})

	// Sessions may not be resumed if they contain another version's cipher.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-DeclineBadCipher-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FilterTicket: func(in []byte) ([]byte, error) {
					return SetShimTicketCipherSuite(in, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
				},
			},
		},
		flags: []string{
			"-ticket-key",
			base64.StdEncoding.EncodeToString(TestShimTicketKey),
		},
		expectResumeRejected: true,
	})

	// If the client does not offer the cipher from the session, decline to
	// resume. Clients are forbidden from doing this, but BoringSSL selects
	// the cipher first, so we only decline.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-UnofferedCipher",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
		},
		resumeConfig: &Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
			Bugs: ProtocolBugs{
				SendCipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			},
		},
		expectResumeRejected: true,
	})

	// In TLS 1.3, clients may advertise a cipher list which does not
	// include the selected cipher. Test that we tolerate this. Servers may
	// resume at another cipher if the PRF matches and are not doing 0-RTT, but
	// BoringSSL will always decline.
	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-UnofferedCipher-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_CHACHA20_POLY1305_SHA256},
		},
		resumeConfig: &Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_CHACHA20_POLY1305_SHA256},
			Bugs: ProtocolBugs{
				SendCipherSuites: []uint16{TLS_AES_128_GCM_SHA256},
			},
		},
		expectResumeRejected: true,
	})

	// Sessions may not be resumed at a different cipher.
	testCases = append(testCases, testCase{
		name:          "Resume-Client-CipherMismatch",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_GCM_SHA256},
		},
		resumeConfig: &Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				SendCipherSuite: TLS_RSA_WITH_AES_128_CBC_SHA,
			},
		},
		shouldFail:    true,
		expectedError: ":OLD_SESSION_CIPHER_NOT_RETURNED:",
	})

	// Session resumption in TLS 1.3 may change the cipher suite if the PRF
	// matches.
	testCases = append(testCases, testCase{
		name:          "Resume-Client-CipherMismatch-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_AES_128_GCM_SHA256},
		},
		resumeConfig: &Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_CHACHA20_POLY1305_SHA256},
		},
	})

	// Session resumption in TLS 1.3 is forbidden if the PRF does not match.
	testCases = append(testCases, testCase{
		name:          "Resume-Client-PRFMismatch-TLS13",
		resumeSession: true,
		config: Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_AES_128_GCM_SHA256},
		},
		resumeConfig: &Config{
			MaxVersion:   VersionTLS13,
			CipherSuites: []uint16{TLS_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				SendCipherSuite: TLS_AES_256_GCM_SHA384,
			},
		},
		shouldFail:    true,
		expectedError: ":OLD_SESSION_PRF_HASH_MISMATCH:",
	})

	for _, secondBinder := range []bool{false, true} {
		var suffix string
		var defaultCurves []CurveID
		if secondBinder {
			suffix = "-SecondBinder"
			// Force a HelloRetryRequest by predicting an empty curve list.
			defaultCurves = []CurveID{}
		}

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-BinderWrongLength" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					SendShortPSKBinder:         true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: error decrypting message",
			expectedError:      ":DIGEST_CHECK_FAILED:",
		})

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-NoPSKBinder" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					SendNoPSKBinder:            true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: error decoding message",
			expectedError:      ":DECODE_ERROR:",
		})

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-ExtraPSKBinder" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					SendExtraPSKBinder:         true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: illegal parameter",
			expectedError:      ":PSK_IDENTITY_BINDER_COUNT_MISMATCH:",
		})

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-ExtraIdentityNoBinder" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					ExtraPSKIdentity:           true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: illegal parameter",
			expectedError:      ":PSK_IDENTITY_BINDER_COUNT_MISMATCH:",
		})

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-InvalidPSKBinder" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					SendInvalidPSKBinder:       true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: error decrypting message",
			expectedError:      ":DIGEST_CHECK_FAILED:",
		})

		testCases = append(testCases, testCase{
			testType:      serverTest,
			name:          "Resume-Server-PSKBinderFirstExtension" + suffix,
			resumeSession: true,
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: defaultCurves,
				Bugs: ProtocolBugs{
					PSKBinderFirst:             true,
					OnlyCorruptSecondPSKBinder: secondBinder,
				},
			},
			shouldFail:         true,
			expectedLocalError: "remote error: illegal parameter",
			expectedError:      ":PRE_SHARED_KEY_MUST_BE_LAST:",
		})
	}

	testCases = append(testCases, testCase{
		testType:      serverTest,
		name:          "Resume-Server-OmitPSKsOnSecondClientHello",
		resumeSession: true,
		config: Config{
			MaxVersion:    VersionTLS13,
			DefaultCurves: []CurveID{},
			Bugs: ProtocolBugs{
				OmitPSKsOnSecondClientHello: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "remote error: illegal parameter",
		expectedError:      ":INCONSISTENT_CLIENT_HELLO:",
	})
}

func addRenegotiationTests() {
	// Servers cannot renegotiate.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Renegotiate-Server-Forbidden",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate:        1,
		shouldFail:         true,
		expectedError:      ":NO_RENEGOTIATION:",
		expectedLocalError: "remote error: no renegotiation",
	})
	// The server shouldn't echo the renegotiation extension unless
	// requested by the client.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Renegotiate-Server-NoExt",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoRenegotiationInfo:      true,
				RequireRenegotiationInfo: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "renegotiation extension missing",
	})
	// The renegotiation SCSV should be sufficient for the server to echo
	// the extension.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Renegotiate-Server-NoExt-SCSV",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoRenegotiationInfo:      true,
				SendRenegotiationSCSV:    true,
				RequireRenegotiationInfo: true,
			},
		},
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				FailIfResumeOnRenego: true,
			},
		},
		renegotiate: 1,
		// Test renegotiation after both an initial and resumption
		// handshake.
		resumeSession: true,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
			"-expect-secure-renegotiation",
		},
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-TLS12",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				FailIfResumeOnRenego: true,
			},
		},
		renegotiate: 1,
		// Test renegotiation after both an initial and resumption
		// handshake.
		resumeSession: true,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
			"-expect-secure-renegotiation",
		},
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-EmptyExt",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				EmptyRenegotiationInfo: true,
			},
		},
		flags:              []string{"-renegotiate-freely"},
		shouldFail:         true,
		expectedError:      ":RENEGOTIATION_MISMATCH:",
		expectedLocalError: "handshake failure",
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-BadExt",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadRenegotiationInfo: true,
			},
		},
		flags:              []string{"-renegotiate-freely"},
		shouldFail:         true,
		expectedError:      ":RENEGOTIATION_MISMATCH:",
		expectedLocalError: "handshake failure",
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-BadExt2",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadRenegotiationInfoEnd: true,
			},
		},
		flags:              []string{"-renegotiate-freely"},
		shouldFail:         true,
		expectedError:      ":RENEGOTIATION_MISMATCH:",
		expectedLocalError: "handshake failure",
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-Downgrade",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoRenegotiationInfoAfterInitial: true,
			},
		},
		flags:              []string{"-renegotiate-freely"},
		shouldFail:         true,
		expectedError:      ":RENEGOTIATION_MISMATCH:",
		expectedLocalError: "handshake failure",
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-Upgrade",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoRenegotiationInfoInInitial: true,
			},
		},
		flags:              []string{"-renegotiate-freely"},
		shouldFail:         true,
		expectedError:      ":RENEGOTIATION_MISMATCH:",
		expectedLocalError: "handshake failure",
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-NoExt-Allowed",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				NoRenegotiationInfo: true,
			},
		},
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
			"-expect-no-secure-renegotiation",
		},
	})

	// Test that the server may switch ciphers on renegotiation without
	// problems.
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-SwitchCiphers",
		renegotiate: 1,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
		},
		renegotiateCiphers: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-Client-SwitchCiphers2",
		renegotiate: 1,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
		},
		renegotiateCiphers: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})

	// Test that the server may not switch versions on renegotiation.
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-SwitchVersion",
		config: Config{
			MaxVersion: VersionTLS12,
			// Pick a cipher which exists at both versions.
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_CBC_SHA},
			Bugs: ProtocolBugs{
				NegotiateVersionOnRenego: VersionTLS11,
				// Avoid failing early at the record layer.
				SendRecordVersion: VersionTLS12,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
		shouldFail:    true,
		expectedError: ":WRONG_SSL_VERSION:",
	})

	testCases = append(testCases, testCase{
		name:        "Renegotiate-SameClientVersion",
		renegotiate: 1,
		config: Config{
			MaxVersion: VersionTLS10,
			Bugs: ProtocolBugs{
				RequireSameRenegoClientVersion: true,
			},
		},
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})
	testCases = append(testCases, testCase{
		name:        "Renegotiate-FalseStart",
		renegotiate: 1,
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			NextProtos:   []string{"foo"},
		},
		flags: []string{
			"-false-start",
			"-select-next-proto", "foo",
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
		shimWritesFirst: true,
	})

	// Client-side renegotiation controls.
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Forbidden-1",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate:        1,
		shouldFail:         true,
		expectedError:      ":NO_RENEGOTIATION:",
		expectedLocalError: "remote error: no renegotiation",
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Once-1",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-once",
			"-expect-total-renegotiations", "1",
		},
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Freely-1",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Once-2",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate:        2,
		flags:              []string{"-renegotiate-once"},
		shouldFail:         true,
		expectedError:      ":NO_RENEGOTIATION:",
		expectedLocalError: "remote error: no renegotiation",
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Freely-2",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate: 2,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "2",
		},
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-NoIgnore",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendHelloRequestBeforeEveryAppDataRecord: true,
			},
		},
		shouldFail:    true,
		expectedError: ":NO_RENEGOTIATION:",
	})
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Ignore",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendHelloRequestBeforeEveryAppDataRecord: true,
			},
		},
		flags: []string{
			"-renegotiate-ignore",
			"-expect-total-renegotiations", "0",
		},
	})

	// Renegotiation may be enabled and then disabled immediately after the
	// handshake.
	testCases = append(testCases, testCase{
		name: "Renegotiate-ForbidAfterHandshake",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate:        1,
		flags:              []string{"-forbid-renegotiation-after-handshake"},
		shouldFail:         true,
		expectedError:      ":NO_RENEGOTIATION:",
		expectedLocalError: "remote error: no renegotiation",
	})

	// Renegotiation is not allowed when there is an unfinished write.
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-UnfinishedWrite",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate:             1,
		readWithUnfinishedWrite: true,
		flags: []string{
			"-async",
			"-renegotiate-freely",
		},
		shouldFail:    true,
		expectedError: ":NO_RENEGOTIATION:",
		// We do not successfully send the no_renegotiation alert in
		// this case. https://crbug.com/boringssl/130
	})

	// We reject stray HelloRequests during the handshake in TLS 1.2.
	testCases = append(testCases, testCase{
		name: "StrayHelloRequest",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendHelloRequestBeforeEveryHandshakeMessage: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})
	testCases = append(testCases, testCase{
		name: "StrayHelloRequest-Packed",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				PackHandshakeFlight:                         true,
				SendHelloRequestBeforeEveryHandshakeMessage: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})

	// Test renegotiation works if HelloRequest and server Finished come in
	// the same record.
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-Packed",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				PackHandshakeFlight:          true,
				PackHelloRequestWithFinished: true,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})

	// Renegotiation is forbidden in TLS 1.3.
	testCases = append(testCases, testCase{
		name: "Renegotiate-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendHelloRequestBeforeEveryAppDataRecord: true,
			},
		},
		flags: []string{
			"-renegotiate-freely",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})

	// Stray HelloRequests during the handshake are forbidden in TLS 1.3.
	testCases = append(testCases, testCase{
		name: "StrayHelloRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendHelloRequestBeforeEveryHandshakeMessage: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})

	// The renegotiation_info extension is not sent in TLS 1.3, but TLS 1.3
	// always reads as supporting it, regardless of whether it was
	// negotiated.
	testCases = append(testCases, testCase{
		name: "AlwaysReportRenegotiationInfo-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				NoRenegotiationInfo: true,
			},
		},
		flags: []string{
			"-expect-secure-renegotiation",
		},
	})

	// Certificates may not change on renegotiation.
	testCases = append(testCases, testCase{
		name: "Renegotiation-CertificateChange",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				RenegotiationCertificate: &rsaChainCertificate,
			},
		},
		renegotiate:   1,
		flags:         []string{"-renegotiate-freely"},
		shouldFail:    true,
		expectedError: ":SERVER_CERT_CHANGED:",
	})
	testCases = append(testCases, testCase{
		name: "Renegotiation-CertificateChange-2",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
			Bugs: ProtocolBugs{
				RenegotiationCertificate: &rsa1024Certificate,
			},
		},
		renegotiate:   1,
		flags:         []string{"-renegotiate-freely"},
		shouldFail:    true,
		expectedError: ":SERVER_CERT_CHANGED:",
	})

	// We do not negotiate ALPN after the initial handshake. This is
	// error-prone and only risks bugs in consumers.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Renegotiation-ForbidALPN",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				// Forcibly negotiate ALPN on both initial and
				// renegotiation handshakes. The test stack will
				// internally check the client does not offer
				// it.
				SendALPN: "foo",
			},
		},
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar\x03baz",
			"-expect-alpn", "foo",
			"-renegotiate-freely",
		},
		renegotiate:   1,
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	// The server may send different stapled OCSP responses or SCT lists on
	// renegotiation, but BoringSSL ignores this and reports the old values.
	// Also test that non-fatal verify results are preserved.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Renegotiation-ChangeAuthProperties",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendOCSPResponseOnRenegotiation: testOCSPResponse2,
				SendSCTListOnRenegotiation:      testSCTList2,
			},
		},
		renegotiate: 1,
		flags: []string{
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
			"-enable-ocsp-stapling",
			"-expect-ocsp-response",
			base64.StdEncoding.EncodeToString(testOCSPResponse),
			"-enable-signed-cert-timestamps",
			"-expect-signed-cert-timestamps",
			base64.StdEncoding.EncodeToString(testSCTList),
			"-verify-fail",
			"-expect-verify-result",
		},
	})
}

func addDTLSReplayTests() {
	// Test that sequence number replays are detected.
	testCases = append(testCases, testCase{
		protocol:     dtls,
		name:         "DTLS-Replay",
		messageCount: 200,
		replayWrites: true,
	})

	// Test the incoming sequence number skipping by values larger
	// than the retransmit window.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "DTLS-Replay-LargeGaps",
		config: Config{
			Bugs: ProtocolBugs{
				SequenceNumberMapping: func(in uint64) uint64 {
					return in * 127
				},
			},
		},
		messageCount: 200,
		replayWrites: true,
	})

	// Test the incoming sequence number changing non-monotonically.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "DTLS-Replay-NonMonotonic",
		config: Config{
			Bugs: ProtocolBugs{
				SequenceNumberMapping: func(in uint64) uint64 {
					return in ^ 31
				},
			},
		},
		messageCount: 200,
		replayWrites: true,
	})
}

var testSignatureAlgorithms = []struct {
	name string
	id   signatureAlgorithm
	cert testCert
}{
	{"RSA_PKCS1_SHA1", signatureRSAPKCS1WithSHA1, testCertRSA},
	{"RSA_PKCS1_SHA256", signatureRSAPKCS1WithSHA256, testCertRSA},
	{"RSA_PKCS1_SHA384", signatureRSAPKCS1WithSHA384, testCertRSA},
	{"RSA_PKCS1_SHA512", signatureRSAPKCS1WithSHA512, testCertRSA},
	{"ECDSA_SHA1", signatureECDSAWithSHA1, testCertECDSAP256},
	// The “P256” in the following line is not a mistake. In TLS 1.2 the
	// hash function doesn't have to match the curve and so the same
	// signature algorithm works with P-224.
	{"ECDSA_P224_SHA256", signatureECDSAWithP256AndSHA256, testCertECDSAP224},
	{"ECDSA_P256_SHA256", signatureECDSAWithP256AndSHA256, testCertECDSAP256},
	{"ECDSA_P384_SHA384", signatureECDSAWithP384AndSHA384, testCertECDSAP384},
	{"ECDSA_P521_SHA512", signatureECDSAWithP521AndSHA512, testCertECDSAP521},
	{"RSA_PSS_SHA256", signatureRSAPSSWithSHA256, testCertRSA},
	{"RSA_PSS_SHA384", signatureRSAPSSWithSHA384, testCertRSA},
	{"RSA_PSS_SHA512", signatureRSAPSSWithSHA512, testCertRSA},
	{"Ed25519", signatureEd25519, testCertEd25519},
	// Tests for key types prior to TLS 1.2.
	{"RSA", 0, testCertRSA},
	{"ECDSA", 0, testCertECDSAP256},
}

const fakeSigAlg1 signatureAlgorithm = 0x2a01
const fakeSigAlg2 signatureAlgorithm = 0xff01

func addSignatureAlgorithmTests() {
	// Not all ciphers involve a signature. Advertise a list which gives all
	// versions a signing cipher.
	signingCiphers := []uint16{
		TLS_AES_256_GCM_SHA384,
		TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
		TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
		TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
		TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
	}

	var allAlgorithms []signatureAlgorithm
	for _, alg := range testSignatureAlgorithms {
		if alg.id != 0 {
			allAlgorithms = append(allAlgorithms, alg.id)
		}
	}

	// Make sure each signature algorithm works. Include some fake values in
	// the list and ensure they're ignored.
	for _, alg := range testSignatureAlgorithms {
		for _, ver := range tlsVersions {
			if (ver.version < VersionTLS12) != (alg.id == 0) {
				continue
			}

			var shouldSignFail, shouldVerifyFail bool
			// ecdsa_sha1 does not exist in TLS 1.3.
			if ver.version >= VersionTLS13 && alg.id == signatureECDSAWithSHA1 {
				shouldSignFail = true
				shouldVerifyFail = true
			}
			// RSA-PKCS1 does not exist in TLS 1.3.
			if ver.version >= VersionTLS13 && hasComponent(alg.name, "PKCS1") {
				shouldSignFail = true
				shouldVerifyFail = true
			}
			// SHA-224 has been removed from TLS 1.3 and, in 1.3,
			// the curve has to match the hash size.
			if ver.version >= VersionTLS13 && alg.cert == testCertECDSAP224 {
				shouldSignFail = true
				shouldVerifyFail = true
			}

			// BoringSSL will sign SHA-1 and SHA-512 with ECDSA but not accept them.
			if alg.id == signatureECDSAWithSHA1 || alg.id == signatureECDSAWithP521AndSHA512 {
				shouldVerifyFail = true
			}

			var signError, signLocalError, verifyError, verifyLocalError string
			if shouldSignFail {
				signError = ":NO_COMMON_SIGNATURE_ALGORITHMS:"
				signLocalError = "remote error: handshake failure"
			}
			if shouldVerifyFail {
				verifyError = ":WRONG_SIGNATURE_TYPE:"
				verifyLocalError = "remote error"
			}

			suffix := "-" + alg.name + "-" + ver.name

			testCases = append(testCases, testCase{
				name: "ClientAuth-Sign" + suffix,
				config: Config{
					MaxVersion: ver.version,
					ClientAuth: RequireAnyClientCert,
					VerifySignatureAlgorithms: []signatureAlgorithm{
						fakeSigAlg1,
						alg.id,
						fakeSigAlg2,
					},
				},
				flags: []string{
					"-cert-file", path.Join(*resourceDir, getShimCertificate(alg.cert)),
					"-key-file", path.Join(*resourceDir, getShimKey(alg.cert)),
					"-enable-all-curves",
					"-enable-ed25519",
				},
				shouldFail:                     shouldSignFail,
				expectedError:                  signError,
				expectedLocalError:             signLocalError,
				expectedPeerSignatureAlgorithm: alg.id,
			})

			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "ClientAuth-Verify" + suffix,
				config: Config{
					MaxVersion:   ver.version,
					Certificates: []Certificate{getRunnerCertificate(alg.cert)},
					SignSignatureAlgorithms: []signatureAlgorithm{
						alg.id,
					},
					Bugs: ProtocolBugs{
						SkipECDSACurveCheck:          shouldVerifyFail,
						IgnoreSignatureVersionChecks: shouldVerifyFail,
						// Some signature algorithms may not be advertised.
						IgnorePeerSignatureAlgorithmPreferences: shouldVerifyFail,
					},
				},
				flags: []string{
					"-require-any-client-certificate",
					"-expect-peer-signature-algorithm", strconv.Itoa(int(alg.id)),
					"-enable-all-curves",
					"-enable-ed25519",
				},
				// Resume the session to assert the peer signature
				// algorithm is reported on both handshakes.
				resumeSession:      !shouldVerifyFail,
				shouldFail:         shouldVerifyFail,
				expectedError:      verifyError,
				expectedLocalError: verifyLocalError,
			})

			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "ServerAuth-Sign" + suffix,
				config: Config{
					MaxVersion:   ver.version,
					CipherSuites: signingCiphers,
					VerifySignatureAlgorithms: []signatureAlgorithm{
						fakeSigAlg1,
						alg.id,
						fakeSigAlg2,
					},
				},
				flags: []string{
					"-cert-file", path.Join(*resourceDir, getShimCertificate(alg.cert)),
					"-key-file", path.Join(*resourceDir, getShimKey(alg.cert)),
					"-enable-all-curves",
					"-enable-ed25519",
				},
				shouldFail:                     shouldSignFail,
				expectedError:                  signError,
				expectedLocalError:             signLocalError,
				expectedPeerSignatureAlgorithm: alg.id,
			})

			testCases = append(testCases, testCase{
				name: "ServerAuth-Verify" + suffix,
				config: Config{
					MaxVersion:   ver.version,
					Certificates: []Certificate{getRunnerCertificate(alg.cert)},
					CipherSuites: signingCiphers,
					SignSignatureAlgorithms: []signatureAlgorithm{
						alg.id,
					},
					Bugs: ProtocolBugs{
						SkipECDSACurveCheck:          shouldVerifyFail,
						IgnoreSignatureVersionChecks: shouldVerifyFail,
						// Some signature algorithms may not be advertised.
						IgnorePeerSignatureAlgorithmPreferences: shouldVerifyFail,
					},
				},
				flags: []string{
					"-expect-peer-signature-algorithm", strconv.Itoa(int(alg.id)),
					"-enable-all-curves",
					"-enable-ed25519",
				},
				// Resume the session to assert the peer signature
				// algorithm is reported on both handshakes.
				resumeSession:      !shouldVerifyFail,
				shouldFail:         shouldVerifyFail,
				expectedError:      verifyError,
				expectedLocalError: verifyLocalError,
			})

			if !shouldVerifyFail {
				testCases = append(testCases, testCase{
					testType: serverTest,
					name:     "ClientAuth-InvalidSignature" + suffix,
					config: Config{
						MaxVersion:   ver.version,
						Certificates: []Certificate{getRunnerCertificate(alg.cert)},
						SignSignatureAlgorithms: []signatureAlgorithm{
							alg.id,
						},
						Bugs: ProtocolBugs{
							InvalidSignature: true,
						},
					},
					flags: []string{
						"-require-any-client-certificate",
						"-enable-all-curves",
						"-enable-ed25519",
					},
					shouldFail:    true,
					expectedError: ":BAD_SIGNATURE:",
				})

				testCases = append(testCases, testCase{
					name: "ServerAuth-InvalidSignature" + suffix,
					config: Config{
						MaxVersion:   ver.version,
						Certificates: []Certificate{getRunnerCertificate(alg.cert)},
						CipherSuites: signingCiphers,
						SignSignatureAlgorithms: []signatureAlgorithm{
							alg.id,
						},
						Bugs: ProtocolBugs{
							InvalidSignature: true,
						},
					},
					flags: []string{
						"-enable-all-curves",
						"-enable-ed25519",
					},
					shouldFail:    true,
					expectedError: ":BAD_SIGNATURE:",
				})
			}

			if ver.version >= VersionTLS12 && !shouldSignFail {
				testCases = append(testCases, testCase{
					name: "ClientAuth-Sign-Negotiate" + suffix,
					config: Config{
						MaxVersion:                ver.version,
						ClientAuth:                RequireAnyClientCert,
						VerifySignatureAlgorithms: allAlgorithms,
					},
					flags: []string{
						"-cert-file", path.Join(*resourceDir, getShimCertificate(alg.cert)),
						"-key-file", path.Join(*resourceDir, getShimKey(alg.cert)),
						"-enable-all-curves",
						"-enable-ed25519",
						"-signing-prefs", strconv.Itoa(int(alg.id)),
					},
					expectedPeerSignatureAlgorithm: alg.id,
				})

				testCases = append(testCases, testCase{
					testType: serverTest,
					name:     "ServerAuth-Sign-Negotiate" + suffix,
					config: Config{
						MaxVersion:                ver.version,
						CipherSuites:              signingCiphers,
						VerifySignatureAlgorithms: allAlgorithms,
					},
					flags: []string{
						"-cert-file", path.Join(*resourceDir, getShimCertificate(alg.cert)),
						"-key-file", path.Join(*resourceDir, getShimKey(alg.cert)),
						"-enable-all-curves",
						"-enable-ed25519",
						"-signing-prefs", strconv.Itoa(int(alg.id)),
					},
					expectedPeerSignatureAlgorithm: alg.id,
				})
			}
		}
	}

	// Test the peer's verify preferences are available.
	for _, ver := range tlsVersions {
		if ver.version < VersionTLS12 {
			continue
		}
		testCases = append(testCases, testCase{
			name: "ClientAuth-PeerVerifyPrefs-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				ClientAuth: RequireAnyClientCert,
				VerifySignatureAlgorithms: []signatureAlgorithm{
					signatureRSAPSSWithSHA256,
					signatureEd25519,
					signatureECDSAWithP256AndSHA256,
				},
			},
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaKeyFile),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureRSAPSSWithSHA256)),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureEd25519)),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureECDSAWithP256AndSHA256)),
			},
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "ServerAuth-PeerVerifyPrefs-" + ver.name,
			config: Config{
				MaxVersion: ver.version,
				VerifySignatureAlgorithms: []signatureAlgorithm{
					signatureRSAPSSWithSHA256,
					signatureEd25519,
					signatureECDSAWithP256AndSHA256,
				},
			},
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaKeyFile),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureRSAPSSWithSHA256)),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureEd25519)),
				"-expect-peer-verify-pref", strconv.Itoa(int(signatureECDSAWithP256AndSHA256)),
			},
		})

	}

	// Test that algorithm selection takes the key type into account.
	testCases = append(testCases, testCase{
		name: "ClientAuth-SignatureType",
		config: Config{
			ClientAuth: RequireAnyClientCert,
			MaxVersion: VersionTLS12,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP521AndSHA512,
				signatureRSAPKCS1WithSHA384,
				signatureECDSAWithSHA1,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA384,
	})

	testCases = append(testCases, testCase{
		name: "ClientAuth-SignatureType-TLS13",
		config: Config{
			ClientAuth: RequireAnyClientCert,
			MaxVersion: VersionTLS13,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP521AndSHA512,
				signatureRSAPKCS1WithSHA384,
				signatureRSAPSSWithSHA384,
				signatureECDSAWithSHA1,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPSSWithSHA384,
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerAuth-SignatureType",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP521AndSHA512,
				signatureRSAPKCS1WithSHA384,
				signatureECDSAWithSHA1,
			},
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA384,
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerAuth-SignatureType-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP521AndSHA512,
				signatureRSAPKCS1WithSHA384,
				signatureRSAPSSWithSHA384,
				signatureECDSAWithSHA1,
			},
		},
		expectedPeerSignatureAlgorithm: signatureRSAPSSWithSHA384,
	})

	// Test that signature verification takes the key type into account.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Verify-ClientAuth-SignatureType",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA256,
			},
			Bugs: ProtocolBugs{
				SendSignatureAlgorithm: signatureECDSAWithP256AndSHA256,
			},
		},
		flags: []string{
			"-require-any-client-certificate",
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Verify-ClientAuth-SignatureType-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
			},
			Bugs: ProtocolBugs{
				SendSignatureAlgorithm: signatureECDSAWithP256AndSHA256,
			},
		},
		flags: []string{
			"-require-any-client-certificate",
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	testCases = append(testCases, testCase{
		name: "Verify-ServerAuth-SignatureType",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA256,
			},
			Bugs: ProtocolBugs{
				SendSignatureAlgorithm: signatureECDSAWithP256AndSHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	testCases = append(testCases, testCase{
		name: "Verify-ServerAuth-SignatureType-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
			},
			Bugs: ProtocolBugs{
				SendSignatureAlgorithm: signatureECDSAWithP256AndSHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	// Test that, if the ClientHello list is missing, the server falls back
	// to SHA-1 in TLS 1.2, but not TLS 1.3.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerAuth-SHA1-Fallback-RSA",
		config: Config{
			MaxVersion: VersionTLS12,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerAuth-SHA1-Fallback-ECDSA",
		config: Config{
			MaxVersion: VersionTLS12,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerAuth-NoFallback-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})

	// The CertificateRequest list, however, may never be omitted. It is a
	// syntax error for it to be empty.
	testCases = append(testCases, testCase{
		name: "ClientAuth-NoFallback-RSA",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		shouldFail:         true,
		expectedError:      ":DECODE_ERROR:",
		expectedLocalError: "remote error: error decoding message",
	})

	testCases = append(testCases, testCase{
		name: "ClientAuth-NoFallback-ECDSA",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
		shouldFail:         true,
		expectedError:      ":DECODE_ERROR:",
		expectedLocalError: "remote error: error decoding message",
	})

	testCases = append(testCases, testCase{
		name: "ClientAuth-NoFallback-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
			},
			Bugs: ProtocolBugs{
				NoSignatureAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		shouldFail:         true,
		expectedError:      ":DECODE_ERROR:",
		expectedLocalError: "remote error: error decoding message",
	})

	// Test that signature preferences are enforced. BoringSSL does not
	// implement MD5 signatures.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ClientAuth-Enforced",
		config: Config{
			MaxVersion:   VersionTLS12,
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithMD5,
			},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
			},
		},
		flags:         []string{"-require-any-client-certificate"},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	testCases = append(testCases, testCase{
		name: "ServerAuth-Enforced",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithMD5,
			},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ClientAuth-Enforced-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithMD5,
			},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
				IgnoreSignatureVersionChecks:            true,
			},
		},
		flags:         []string{"-require-any-client-certificate"},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	testCases = append(testCases, testCase{
		name: "ServerAuth-Enforced-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithMD5,
			},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
				IgnoreSignatureVersionChecks:            true,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	// Test that the negotiated signature algorithm respects the client and
	// server preferences.
	testCases = append(testCases, testCase{
		name: "NoCommonAlgorithms",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA512,
				signatureRSAPKCS1WithSHA1,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA256)),
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})
	testCases = append(testCases, testCase{
		name: "NoCommonAlgorithms-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA512,
				signatureRSAPSSWithSHA384,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPSSWithSHA256)),
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})
	testCases = append(testCases, testCase{
		name: "Agree-Digest-SHA256",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
				signatureRSAPKCS1WithSHA256,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA256)),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA1)),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA256,
	})
	testCases = append(testCases, testCase{
		name: "Agree-Digest-SHA1",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA1,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA512)),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA256)),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA1)),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA1,
	})
	testCases = append(testCases, testCase{
		name: "Agree-Digest-Default",
		config: Config{
			MaxVersion: VersionTLS12,
			ClientAuth: RequireAnyClientCert,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA256,
				signatureECDSAWithP256AndSHA256,
				signatureRSAPKCS1WithSHA1,
				signatureECDSAWithSHA1,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA256,
	})

	// Test that the signing preference list may include extra algorithms
	// without negotiation problems.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "FilterExtraAlgorithms",
		config: Config{
			MaxVersion: VersionTLS12,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPKCS1WithSHA256,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
			"-signing-prefs", strconv.Itoa(int(fakeSigAlg1)),
			"-signing-prefs", strconv.Itoa(int(signatureECDSAWithP256AndSHA256)),
			"-signing-prefs", strconv.Itoa(int(signatureRSAPKCS1WithSHA256)),
			"-signing-prefs", strconv.Itoa(int(fakeSigAlg2)),
		},
		expectedPeerSignatureAlgorithm: signatureRSAPKCS1WithSHA256,
	})

	// In TLS 1.2 and below, ECDSA uses the curve list rather than the
	// signature algorithms.
	testCases = append(testCases, testCase{
		name: "CheckLeafCurve",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
			Certificates: []Certificate{ecdsaP256Certificate},
		},
		flags:         []string{"-curves", strconv.Itoa(int(CurveP384))},
		shouldFail:    true,
		expectedError: ":BAD_ECC_CERT:",
	})

	// In TLS 1.3, ECDSA does not use the ECDHE curve list.
	testCases = append(testCases, testCase{
		name: "CheckLeafCurve-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{ecdsaP256Certificate},
		},
		flags: []string{"-curves", strconv.Itoa(int(CurveP384))},
	})

	// In TLS 1.2, the ECDSA curve is not in the signature algorithm.
	testCases = append(testCases, testCase{
		name: "ECDSACurveMismatch-Verify-TLS12",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256},
			Certificates: []Certificate{ecdsaP256Certificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP384AndSHA384,
			},
		},
	})

	// In TLS 1.3, the ECDSA curve comes from the signature algorithm.
	testCases = append(testCases, testCase{
		name: "ECDSACurveMismatch-Verify-TLS13",
		config: Config{
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{ecdsaP256Certificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP384AndSHA384,
			},
			Bugs: ProtocolBugs{
				SkipECDSACurveCheck: true,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_SIGNATURE_TYPE:",
	})

	// Signature algorithm selection in TLS 1.3 should take the curve into
	// account.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ECDSACurveMismatch-Sign-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureECDSAWithP384AndSHA384,
				signatureECDSAWithP256AndSHA256,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			"-key-file", path.Join(*resourceDir, ecdsaP256KeyFile),
		},
		expectedPeerSignatureAlgorithm: signatureECDSAWithP256AndSHA256,
	})

	// RSASSA-PSS with SHA-512 is too large for 1024-bit RSA. Test that the
	// server does not attempt to sign in that case.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "RSA-PSS-Large",
		config: Config{
			MaxVersion: VersionTLS13,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA512,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsa1024CertificateFile),
			"-key-file", path.Join(*resourceDir, rsa1024KeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})

	// Test that RSA-PSS is enabled by default for TLS 1.2.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "RSA-PSS-Default-Verify",
		config: Config{
			MaxVersion: VersionTLS12,
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
			},
		},
		flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "RSA-PSS-Default-Sign",
		config: Config{
			MaxVersion: VersionTLS12,
			VerifySignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
			},
		},
		flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})

	// TLS 1.1 and below has no way to advertise support for or negotiate
	// Ed25519's signature algorithm.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "NoEd25519-TLS11-ServerAuth-Verify",
		config: Config{
			MaxVersion:   VersionTLS11,
			Certificates: []Certificate{ed25519Certificate},
			Bugs: ProtocolBugs{
				// Sign with Ed25519 even though it is TLS 1.1.
				UseLegacySigningAlgorithm: signatureEd25519,
			},
		},
		flags:         []string{"-enable-ed25519"},
		shouldFail:    true,
		expectedError: ":PEER_ERROR_UNSUPPORTED_CERTIFICATE_TYPE:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoEd25519-TLS11-ServerAuth-Sign",
		config: Config{
			MaxVersion: VersionTLS11,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ed25519CertificateFile),
			"-key-file", path.Join(*resourceDir, ed25519KeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoEd25519-TLS11-ClientAuth-Verify",
		config: Config{
			MaxVersion:   VersionTLS11,
			Certificates: []Certificate{ed25519Certificate},
			Bugs: ProtocolBugs{
				// Sign with Ed25519 even though it is TLS 1.1.
				UseLegacySigningAlgorithm: signatureEd25519,
			},
		},
		flags: []string{
			"-enable-ed25519",
			"-require-any-client-certificate",
		},
		shouldFail:    true,
		expectedError: ":PEER_ERROR_UNSUPPORTED_CERTIFICATE_TYPE:",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "NoEd25519-TLS11-ClientAuth-Sign",
		config: Config{
			MaxVersion: VersionTLS11,
			ClientAuth: RequireAnyClientCert,
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, ed25519CertificateFile),
			"-key-file", path.Join(*resourceDir, ed25519KeyFile),
		},
		shouldFail:    true,
		expectedError: ":NO_COMMON_SIGNATURE_ALGORITHMS:",
	})

	// Test Ed25519 is not advertised by default.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Ed25519DefaultDisable-NoAdvertise",
		config: Config{
			Certificates: []Certificate{ed25519Certificate},
		},
		shouldFail:         true,
		expectedLocalError: "tls: no common signature algorithms",
	})

	// Test Ed25519, when disabled, is not accepted if the peer ignores our
	// preferences.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Ed25519DefaultDisable-NoAccept",
		config: Config{
			Certificates: []Certificate{ed25519Certificate},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "remote error: illegal parameter",
		expectedError:      ":WRONG_SIGNATURE_TYPE:",
	})

	// Test that configuring verify preferences changes what the client
	// advertises.
	testCases = append(testCases, testCase{
		name: "VerifyPreferences-Advertised",
		config: Config{
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
				signatureRSAPSSWithSHA384,
				signatureRSAPSSWithSHA512,
			},
		},
		flags: []string{
			"-verify-prefs", strconv.Itoa(int(signatureRSAPSSWithSHA384)),
			"-expect-peer-signature-algorithm", strconv.Itoa(int(signatureRSAPSSWithSHA384)),
		},
	})

	// Test that the client advertises a set which the runner can find
	// nothing in common with.
	testCases = append(testCases, testCase{
		name: "VerifyPreferences-NoCommonAlgorithms",
		config: Config{
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
				signatureRSAPSSWithSHA512,
			},
		},
		flags: []string{
			"-verify-prefs", strconv.Itoa(int(signatureRSAPSSWithSHA384)),
		},
		shouldFail:         true,
		expectedLocalError: "tls: no common signature algorithms",
	})

	// Test that the client enforces its preferences when configured.
	testCases = append(testCases, testCase{
		name: "VerifyPreferences-Enforced",
		config: Config{
			Certificates: []Certificate{rsaCertificate},
			SignSignatureAlgorithms: []signatureAlgorithm{
				signatureRSAPSSWithSHA256,
				signatureRSAPSSWithSHA512,
			},
			Bugs: ProtocolBugs{
				IgnorePeerSignatureAlgorithmPreferences: true,
			},
		},
		flags: []string{
			"-verify-prefs", strconv.Itoa(int(signatureRSAPSSWithSHA384)),
		},
		shouldFail:         true,
		expectedLocalError: "remote error: illegal parameter",
		expectedError:      ":WRONG_SIGNATURE_TYPE:",
	})

	// Test that explicitly configuring Ed25519 is as good as changing the
	// boolean toggle.
	testCases = append(testCases, testCase{
		name: "VerifyPreferences-Ed25519",
		config: Config{
			Certificates: []Certificate{ed25519Certificate},
		},
		flags: []string{
			"-verify-prefs", strconv.Itoa(int(signatureEd25519)),
		},
	})

	rsaPSSSupportTests := []struct {
		name                string
		expectRSAPSSSupport RSAPSSSupport
		verifyPrefs         []signatureAlgorithm
		noCerts             bool
	}{
		// By default, RSA-PSS is fully advertised.
		{
			name:                "Default",
			expectRSAPSSSupport: RSAPSSSupportBoth,
		},
		// Disabling RSA-PSS certificates makes it online-signature-only.
		{
			name:                "Default-NoCerts",
			expectRSAPSSSupport: RSAPSSSupportOnlineSignatureOnly,
			noCerts:             true,
		},
		// The above also apply if verify preferences were explicitly configured.
		{
			name:                "ConfigPSS",
			expectRSAPSSSupport: RSAPSSSupportBoth,
			verifyPrefs:         []signatureAlgorithm{signatureRSAPSSWithSHA256, signatureRSAPKCS1WithSHA256, signatureECDSAWithP256AndSHA256},
		},
		{
			name:                "ConfigPSS-NoCerts",
			expectRSAPSSSupport: RSAPSSSupportOnlineSignatureOnly,
			verifyPrefs:         []signatureAlgorithm{signatureRSAPSSWithSHA256, signatureRSAPKCS1WithSHA256, signatureECDSAWithP256AndSHA256},
			noCerts:             true,
		},
		// If verify preferences were explicitly configured without RSA-PSS support,
		// NoCerts is a no-op and the shim correctly only sends one extension.
		// (This is checked internally in the runner.)
		{
			name:                "ConfigNoPSS",
			expectRSAPSSSupport: RSAPSSSupportNone,
			verifyPrefs:         []signatureAlgorithm{signatureRSAPKCS1WithSHA256, signatureECDSAWithP256AndSHA256},
		},
		{
			name:                "ConfigNoPSS-NoCerts",
			expectRSAPSSSupport: RSAPSSSupportNone,
			verifyPrefs:         []signatureAlgorithm{signatureRSAPKCS1WithSHA256, signatureECDSAWithP256AndSHA256},
			noCerts:             true,
		},
	}

	for _, test := range rsaPSSSupportTests {
		var pssFlags []string
		for _, pref := range test.verifyPrefs {
			pssFlags = append(pssFlags, "-verify-prefs", strconv.Itoa(int(pref)))
		}
		if test.noCerts {
			pssFlags = append(pssFlags, "-no-rsa-pss-rsae-certs")
		}
		for _, ver := range tlsVersions {
			if ver.version < VersionTLS12 {
				continue
			}

			// TLS 1.2 cannot express different RSAPSSSupportOnlineSignatureOnly,
			// so it decays to RSAPSSSupportNone.
			expect := test.expectRSAPSSSupport
			if ver.version < VersionTLS13 && expect == RSAPSSSupportOnlineSignatureOnly {
				expect = RSAPSSSupportNone
			}

			// If the configuration results in no RSA-PSS support, the handshake won't complete.
			// (The test, however, still covers the RSA-PSS assertion.)
			var localError string
			var shouldFail bool
			if ver.version >= VersionTLS13 && expect == RSAPSSSupportNone {
				shouldFail = true
				localError = "tls: no common signature algorithms"
			}

			flags := []string{"-max-version", ver.shimFlag(tls)}
			flags = append(flags, pssFlags...)
			testCases = append(testCases, testCase{
				name: fmt.Sprintf("RSAPSSSupport-%s-%s-Client", test.name, ver.name),
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{rsaCertificate},
					Bugs: ProtocolBugs{
						ExpectRSAPSSSupport: expect,
					},
				},
				flags:              flags,
				shouldFail:         shouldFail,
				expectedLocalError: localError,
			})

			serverFlags := []string{"-require-any-client-certificate"}
			serverFlags = append(flags, serverFlags...)
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     fmt.Sprintf("RSAPSSSupport-%s-%s-Server", test.name, ver.name),
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{rsaCertificate},
					Bugs: ProtocolBugs{
						ExpectRSAPSSSupport: expect,
					},
				},
				flags:              serverFlags,
				shouldFail:         shouldFail,
				expectedLocalError: localError,
			})
		}
	}
}

// timeouts is the retransmit schedule for BoringSSL. It doubles and
// caps at 60 seconds. On the 13th timeout, it gives up.
var timeouts = []time.Duration{
	1 * time.Second,
	2 * time.Second,
	4 * time.Second,
	8 * time.Second,
	16 * time.Second,
	32 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
}

// shortTimeouts is an alternate set of timeouts which would occur if the
// initial timeout duration was set to 250ms.
var shortTimeouts = []time.Duration{
	250 * time.Millisecond,
	500 * time.Millisecond,
	1 * time.Second,
	2 * time.Second,
	4 * time.Second,
	8 * time.Second,
	16 * time.Second,
	32 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
	60 * time.Second,
}

func addDTLSRetransmitTests() {
	// These tests work by coordinating some behavior on both the shim and
	// the runner.
	//
	// TimeoutSchedule configures the runner to send a series of timeout
	// opcodes to the shim (see packetAdaptor) immediately before reading
	// each peer handshake flight N. The timeout opcode both simulates a
	// timeout in the shim and acts as a synchronization point to help the
	// runner bracket each handshake flight.
	//
	// We assume the shim does not read from the channel eagerly. It must
	// first wait until it has sent flight N and is ready to receive
	// handshake flight N+1. At this point, it will process the timeout
	// opcode. It must then immediately respond with a timeout ACK and act
	// as if the shim was idle for the specified amount of time.
	//
	// The runner then drops all packets received before the ACK and
	// continues waiting for flight N. This ordering results in one attempt
	// at sending flight N to be dropped. For the test to complete, the
	// shim must send flight N again, testing that the shim implements DTLS
	// retransmit on a timeout.

	// TODO(davidben): Add DTLS 1.3 versions of these tests. There will
	// likely be more epochs to cross and the final message's retransmit may
	// be more complex.

	// Test that this is indeed the timeout schedule. Stress all
	// four patterns of handshake.
	for i := 1; i < len(timeouts); i++ {
		number := strconv.Itoa(i)
		testCases = append(testCases, testCase{
			protocol: dtls,
			name:     "DTLS-Retransmit-Client-" + number,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					TimeoutSchedule: timeouts[:i],
				},
			},
			resumeSession: true,
			flags:         []string{"-async"},
		})
		testCases = append(testCases, testCase{
			protocol: dtls,
			testType: serverTest,
			name:     "DTLS-Retransmit-Server-" + number,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					TimeoutSchedule: timeouts[:i],
				},
			},
			resumeSession: true,
			flags:         []string{"-async"},
		})
	}

	// Test that exceeding the timeout schedule hits a read
	// timeout.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "DTLS-Retransmit-Timeout",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				TimeoutSchedule: timeouts,
			},
		},
		resumeSession: true,
		flags:         []string{"-async"},
		shouldFail:    true,
		expectedError: ":READ_TIMEOUT_EXPIRED:",
	})

	// Test that timeout handling has a fudge factor, due to API
	// problems.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "DTLS-Retransmit-Fudge",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				TimeoutSchedule: []time.Duration{
					timeouts[0] - 10*time.Millisecond,
				},
			},
		},
		resumeSession: true,
		flags:         []string{"-async"},
	})

	// Test that the final Finished retransmitting isn't
	// duplicated if the peer badly fragments everything.
	testCases = append(testCases, testCase{
		testType: serverTest,
		protocol: dtls,
		name:     "DTLS-Retransmit-Fragmented",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				TimeoutSchedule:          []time.Duration{timeouts[0]},
				MaxHandshakeRecordLength: 2,
			},
		},
		flags: []string{"-async"},
	})

	// Test the timeout schedule when a shorter initial timeout duration is set.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "DTLS-Retransmit-Short-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				TimeoutSchedule: shortTimeouts[:len(shortTimeouts)-1],
			},
		},
		resumeSession: true,
		flags: []string{
			"-async",
			"-initial-timeout-duration-ms", "250",
		},
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "DTLS-Retransmit-Short-Server",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				TimeoutSchedule: shortTimeouts[:len(shortTimeouts)-1],
			},
		},
		resumeSession: true,
		flags: []string{
			"-async",
			"-initial-timeout-duration-ms", "250",
		},
	})

	// If the shim sends the last Finished (server full or client resume
	// handshakes), it must retransmit that Finished when it sees a
	// post-handshake penultimate Finished from the runner. The above tests
	// cover this. Conversely, if the shim sends the penultimate Finished
	// (client full or server resume), test that it does not retransmit.
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: clientTest,
		name:     "DTLS-StrayRetransmitFinished-ClientFull",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				RetransmitFinished: true,
			},
		},
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "DTLS-StrayRetransmitFinished-ServerResume",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				RetransmitFinished: true,
			},
		},
		resumeSession: true,
	})
}

func addExportKeyingMaterialTests() {
	for _, vers := range tlsVersions {
		testCases = append(testCases, testCase{
			name: "ExportKeyingMaterial-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			// Test the exporter in both initial and resumption
			// handshakes.
			resumeSession:        true,
			exportKeyingMaterial: 1024,
			exportLabel:          "label",
			exportContext:        "context",
			useExportContext:     true,
		})
		testCases = append(testCases, testCase{
			name: "ExportKeyingMaterial-NoContext-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			exportKeyingMaterial: 1024,
		})
		testCases = append(testCases, testCase{
			name: "ExportKeyingMaterial-EmptyContext-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			exportKeyingMaterial: 1024,
			useExportContext:     true,
		})
		testCases = append(testCases, testCase{
			name: "ExportKeyingMaterial-Small-" + vers.name,
			config: Config{
				MaxVersion: vers.version,
			},
			exportKeyingMaterial: 1,
			exportLabel:          "label",
			exportContext:        "context",
			useExportContext:     true,
		})

		if vers.version >= VersionTLS13 {
			// Test the exporters do not work while the client is
			// sending 0-RTT data.
			testCases = append(testCases, testCase{
				name: "NoEarlyKeyingMaterial-Client-InEarlyData-" + vers.name,
				config: Config{
					MaxVersion:       vers.version,
					MaxEarlyDataSize: 16384,
				},
				resumeSession: true,
				flags: []string{
					"-enable-early-data",
					"-expect-ticket-supports-early-data",
					"-on-resume-expect-accept-early-data",
					"-on-resume-export-keying-material", "1024",
					"-on-resume-export-label", "label",
					"-on-resume-export-context", "context",
				},
				shouldFail:    true,
				expectedError: ":HANDSHAKE_NOT_COMPLETE:",
			})

			// Test the normal exporter on the server in half-RTT.
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "ExportKeyingMaterial-Server-HalfRTT-" + vers.name,
				config: Config{
					MaxVersion: vers.version,
					Bugs: ProtocolBugs{
						SendEarlyData:           [][]byte{},
						ExpectEarlyDataAccepted: true,
					},
				},
				resumeSession:        true,
				exportKeyingMaterial: 1024,
				exportLabel:          "label",
				exportContext:        "context",
				useExportContext:     true,
				flags:                []string{"-enable-early-data"},
			})
		}
	}

	// Exporters work during a False Start.
	testCases = append(testCases, testCase{
		name: "ExportKeyingMaterial-FalseStart",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			NextProtos:   []string{"foo"},
			Bugs: ProtocolBugs{
				ExpectFalseStart: true,
			},
		},
		flags: []string{
			"-false-start",
			"-advertise-alpn", "\x03foo",
			"-expect-alpn", "foo",
		},
		shimWritesFirst:      true,
		exportKeyingMaterial: 1024,
		exportLabel:          "label",
		exportContext:        "context",
		useExportContext:     true,
	})

	// Exporters do not work in the middle of a renegotiation. Test this by
	// triggering the exporter after every SSL_read call and configuring the
	// shim to run asynchronously.
	testCases = append(testCases, testCase{
		name: "ExportKeyingMaterial-Renegotiate",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate: 1,
		flags: []string{
			"-async",
			"-use-exporter-between-reads",
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
		shouldFail:    true,
		expectedError: "failed to export keying material",
	})
}

func addExportTrafficSecretsTests() {
	for _, cipherSuite := range []testCipherSuite{
		// Test a SHA-256 and SHA-384 based cipher suite.
		{"AEAD-AES128-GCM-SHA256", TLS_AES_128_GCM_SHA256},
		{"AEAD-AES256-GCM-SHA384", TLS_AES_256_GCM_SHA384},
	} {

		testCases = append(testCases, testCase{
			name: "ExportTrafficSecrets-" + cipherSuite.name,
			config: Config{
				MinVersion:   VersionTLS13,
				CipherSuites: []uint16{cipherSuite.id},
			},
			exportTrafficSecrets: true,
		})
	}
}

func addTLSUniqueTests() {
	for _, isClient := range []bool{false, true} {
		for _, isResumption := range []bool{false, true} {
			for _, hasEMS := range []bool{false, true} {
				var suffix string
				if isResumption {
					suffix = "Resume-"
				} else {
					suffix = "Full-"
				}

				if hasEMS {
					suffix += "EMS-"
				} else {
					suffix += "NoEMS-"
				}

				if isClient {
					suffix += "Client"
				} else {
					suffix += "Server"
				}

				test := testCase{
					name:          "TLSUnique-" + suffix,
					testTLSUnique: true,
					config: Config{
						MaxVersion: VersionTLS12,
						Bugs: ProtocolBugs{
							NoExtendedMasterSecret: !hasEMS,
						},
					},
				}

				if isResumption {
					test.resumeSession = true
					test.resumeConfig = &Config{
						MaxVersion: VersionTLS12,
						Bugs: ProtocolBugs{
							NoExtendedMasterSecret: !hasEMS,
						},
					}
				}

				if isResumption && !hasEMS {
					test.shouldFail = true
					test.expectedError = "failed to get tls-unique"
				}

				testCases = append(testCases, test)
			}
		}
	}
}

func addCustomExtensionTests() {
	// Test an unknown extension from the server.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnknownExtension-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				CustomExtension: "custom extension",
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_EXTENSION:",
		expectedLocalError: "remote error: unsupported extension",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnknownExtension-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				CustomExtension: "custom extension",
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_EXTENSION:",
		expectedLocalError: "remote error: unsupported extension",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnknownUnencryptedExtension-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				CustomUnencryptedExtension: "custom extension",
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
		// The shim must send an alert, but alerts at this point do not
		// get successfully decrypted by the runner.
		expectedLocalError: "local error: bad record MAC",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnexpectedUnencryptedExtension-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendUnencryptedALPN: "foo",
			},
		},
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
		// The shim must send an alert, but alerts at this point do not
		// get successfully decrypted by the runner.
		expectedLocalError: "local error: bad record MAC",
	})

	// Test a known but unoffered extension from the server.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnofferedExtension-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendALPN: "alpn",
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_EXTENSION:",
		expectedLocalError: "remote error: unsupported extension",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "UnofferedExtension-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendALPN: "alpn",
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_EXTENSION:",
		expectedLocalError: "remote error: unsupported extension",
	})
}

func addRSAClientKeyExchangeTests() {
	for bad := RSABadValue(1); bad < NumRSABadValues; bad++ {
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     fmt.Sprintf("BadRSAClientKeyExchange-%d", bad),
			config: Config{
				// Ensure the ClientHello version and final
				// version are different, to detect if the
				// server uses the wrong one.
				MaxVersion:   VersionTLS11,
				CipherSuites: []uint16{TLS_RSA_WITH_3DES_EDE_CBC_SHA},
				Bugs: ProtocolBugs{
					BadRSAClientKeyExchange: bad,
				},
			},
			shouldFail:    true,
			expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
		})
	}

	// The server must compare whatever was in ClientHello.version for the
	// RSA premaster.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SendClientVersion-RSA",
		config: Config{
			CipherSuites: []uint16{TLS_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				SendClientVersion: 0x1234,
			},
		},
		flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})
}

var testCurves = []struct {
	name string
	id   CurveID
}{
	{"P-224", CurveP224},
	{"P-256", CurveP256},
	{"P-384", CurveP384},
	{"P-521", CurveP521},
	{"X25519", CurveX25519},
	{"CECPQ2", CurveCECPQ2},
	{"CECPQ2b", CurveCECPQ2b},
}

const bogusCurve = 0x1234

func isPqGroup(r CurveID) bool {
	return r == CurveCECPQ2 || r == CurveCECPQ2b
}

func addCurveTests() {
	for _, curve := range testCurves {
		for _, ver := range tlsVersions {
			if isPqGroup(curve.id) && ver.version < VersionTLS13 {
				continue
			}

			suffix := curve.name + "-" + ver.name

			testCases = append(testCases, testCase{
				name: "CurveTest-Client-" + suffix,
				config: Config{
					MaxVersion: ver.version,
					CipherSuites: []uint16{
						TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
						TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
						TLS_AES_256_GCM_SHA384,
					},
					CurvePreferences: []CurveID{curve.id},
				},
				flags: []string{
					"-enable-all-curves",
					"-expect-curve-id", strconv.Itoa(int(curve.id)),
				},
				expectedCurveID: curve.id,
			})
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "CurveTest-Server-" + suffix,
				config: Config{
					MaxVersion: ver.version,
					CipherSuites: []uint16{
						TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
						TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
						TLS_AES_256_GCM_SHA384,
					},
					CurvePreferences: []CurveID{curve.id},
				},
				flags: []string{
					"-enable-all-curves",
					"-expect-curve-id", strconv.Itoa(int(curve.id)),
				},
				expectedCurveID: curve.id,
			})

			if curve.id != CurveX25519 && !isPqGroup(curve.id) {
				testCases = append(testCases, testCase{
					name: "CurveTest-Client-Compressed-" + suffix,
					config: Config{
						MaxVersion: ver.version,
						CipherSuites: []uint16{
							TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
							TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
							TLS_AES_256_GCM_SHA384,
						},
						CurvePreferences: []CurveID{curve.id},
						Bugs: ProtocolBugs{
							SendCompressedCoordinates: true,
						},
					},
					flags:         []string{"-enable-all-curves"},
					shouldFail:    true,
					expectedError: ":BAD_ECPOINT:",
				})
				testCases = append(testCases, testCase{
					testType: serverTest,
					name:     "CurveTest-Server-Compressed-" + suffix,
					config: Config{
						MaxVersion: ver.version,
						CipherSuites: []uint16{
							TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
							TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
							TLS_AES_256_GCM_SHA384,
						},
						CurvePreferences: []CurveID{curve.id},
						Bugs: ProtocolBugs{
							SendCompressedCoordinates: true,
						},
					},
					flags:         []string{"-enable-all-curves"},
					shouldFail:    true,
					expectedError: ":BAD_ECPOINT:",
				})
			}
		}
	}

	// The server must be tolerant to bogus curves.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "UnknownCurve",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{bogusCurve, CurveP256},
		},
	})

	// The server must be tolerant to bogus curves.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "UnknownCurve-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{bogusCurve, CurveP256},
		},
	})

	// The server must not consider ECDHE ciphers when there are no
	// supported curves.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoSupportedCurves",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				NoSupportedCurves: true,
			},
		},
		shouldFail:    true,
		expectedError: ":NO_SHARED_CIPHER:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoSupportedCurves-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				NoSupportedCurves: true,
			},
		},
		shouldFail:    true,
		expectedError: ":NO_SHARED_GROUP:",
	})

	// The server must fall back to another cipher when there are no
	// supported curves.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "NoCommonCurves",
		config: Config{
			MaxVersion: VersionTLS12,
			CipherSuites: []uint16{
				TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
				TLS_RSA_WITH_AES_128_GCM_SHA256,
			},
			CurvePreferences: []CurveID{CurveP224},
		},
		expectedCipher: TLS_RSA_WITH_AES_128_GCM_SHA256,
	})

	// The client must reject bogus curves and disabled curves.
	testCases = append(testCases, testCase{
		name: "BadECDHECurve",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			Bugs: ProtocolBugs{
				SendCurve: bogusCurve,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})
	testCases = append(testCases, testCase{
		name: "BadECDHECurve-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendCurve: bogusCurve,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "UnsupportedCurve",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				IgnorePeerCurvePreferences: true,
			},
		},
		flags:         []string{"-curves", strconv.Itoa(int(CurveP384))},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		// TODO(davidben): Add a TLS 1.3 version where
		// HelloRetryRequest requests an unsupported curve.
		name: "UnsupportedCurve-ServerHello-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendCurve: CurveP256,
			},
		},
		flags:         []string{"-curves", strconv.Itoa(int(CurveP384))},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	// Test invalid curve points.
	testCases = append(testCases, testCase{
		name: "InvalidECDHPoint-Client",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				InvalidECDHPoint: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_ECPOINT:",
	})
	testCases = append(testCases, testCase{
		name: "InvalidECDHPoint-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				InvalidECDHPoint: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_ECPOINT:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "InvalidECDHPoint-Server",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				InvalidECDHPoint: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_ECPOINT:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "InvalidECDHPoint-Server-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				InvalidECDHPoint: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_ECPOINT:",
	})

	// The previous curve ID should be reported on TLS 1.2 resumption.
	testCases = append(testCases, testCase{
		name: "CurveID-Resume-Client",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{CurveX25519},
		},
		flags:         []string{"-expect-curve-id", strconv.Itoa(int(CurveX25519))},
		resumeSession: true,
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "CurveID-Resume-Server",
		config: Config{
			MaxVersion:       VersionTLS12,
			CipherSuites:     []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			CurvePreferences: []CurveID{CurveX25519},
		},
		flags:         []string{"-expect-curve-id", strconv.Itoa(int(CurveX25519))},
		resumeSession: true,
	})

	// TLS 1.3 allows resuming at a differet curve. If this happens, the new
	// one should be reported.
	testCases = append(testCases, testCase{
		name: "CurveID-Resume-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveX25519},
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP256},
		},
		flags: []string{
			"-on-initial-expect-curve-id", strconv.Itoa(int(CurveX25519)),
			"-on-resume-expect-curve-id", strconv.Itoa(int(CurveP256)),
		},
		resumeSession: true,
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "CurveID-Resume-Server-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveX25519},
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP256},
		},
		flags: []string{
			"-on-initial-expect-curve-id", strconv.Itoa(int(CurveX25519)),
			"-on-resume-expect-curve-id", strconv.Itoa(int(CurveP256)),
		},
		resumeSession: true,
	})

	// Server-sent point formats are legal in TLS 1.2, but not in TLS 1.3.
	testCases = append(testCases, testCase{
		name: "PointFormat-ServerHello-TLS12",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{pointFormatUncompressed},
			},
		},
	})
	testCases = append(testCases, testCase{
		name: "PointFormat-EncryptedExtensions-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{pointFormatUncompressed},
			},
		},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})

	// Server-sent supported groups/curves are legal in TLS 1.3. They are
	// illegal in TLS 1.2, but some servers send them anyway, so we must
	// tolerate them.
	testCases = append(testCases, testCase{
		name: "SupportedCurves-ServerHello-TLS12",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendServerSupportedCurves: true,
			},
		},
	})
	testCases = append(testCases, testCase{
		name: "SupportedCurves-EncryptedExtensions-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendServerSupportedCurves: true,
			},
		},
	})

	// Test that we tolerate unknown point formats, as long as
	// pointFormatUncompressed is present. Limit ciphers to ECDHE ciphers to
	// check they are still functional.
	testCases = append(testCases, testCase{
		name: "PointFormat-Client-Tolerance",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{42, pointFormatUncompressed, 99, pointFormatCompressedPrime},
			},
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PointFormat-Server-Tolerance",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{42, pointFormatUncompressed, 99, pointFormatCompressedPrime},
			},
		},
	})

	// Test TLS 1.2 does not require the point format extension to be
	// present.
	testCases = append(testCases, testCase{
		name: "PointFormat-Client-Missing",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{},
			},
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PointFormat-Server-Missing",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256},
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{},
			},
		},
	})

	// If the point format extension is present, uncompressed points must be
	// offered. BoringSSL requires this whether or not ECDHE is used.
	testCases = append(testCases, testCase{
		name: "PointFormat-Client-MissingUncompressed",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{pointFormatCompressedPrime},
			},
		},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PointFormat-Server-MissingUncompressed",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendSupportedPointFormats: []byte{pointFormatCompressedPrime},
			},
		},
		shouldFail:    true,
		expectedError: ":ERROR_PARSING_EXTENSION:",
	})

	// Implementations should mask off the high order bit in X25519.
	testCases = append(testCases, testCase{
		name: "SetX25519HighBit",
		config: Config{
			CipherSuites: []uint16{
				TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
				TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
				TLS_AES_128_GCM_SHA256,
			},
			CurvePreferences: []CurveID{CurveX25519},
			Bugs: ProtocolBugs{
				SetX25519HighBit: true,
			},
		},
	})

	// CECPQ2 should not be offered by a TLS < 1.3 client.
	testCases = append(testCases, testCase{
		name: "CECPQ2NotInTLS12",
		config: Config{
			Bugs: ProtocolBugs{
				FailIfCECPQ2Offered: true,
			},
		},
		flags: []string{
			"-max-version", strconv.Itoa(VersionTLS12),
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
	})

	// CECPQ2b should not be offered by a TLS < 1.3 client.
	testCases = append(testCases, testCase{
		name: "CECPQ2bNotInTLS12",
		config: Config{
			Bugs: ProtocolBugs{
				FailIfCECPQ2Offered: true,
			},
		},
		flags: []string{
			"-max-version", strconv.Itoa(VersionTLS12),
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
	})

	// CECPQ2 should not crash a TLS < 1.3 client if the server mistakenly
	// selects it.
	testCases = append(testCases, testCase{
		name: "CECPQ2NotAcceptedByTLS12Client",
		config: Config{
			Bugs: ProtocolBugs{
				SendCurve: CurveCECPQ2,
			},
		},
		flags: []string{
			"-max-version", strconv.Itoa(VersionTLS12),
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	// CECPQ2b should not crash a TLS < 1.3 client if the server mistakenly
	// selects it.
	testCases = append(testCases, testCase{
		name: "CECPQ2bNotAcceptedByTLS12Client",
		config: Config{
			Bugs: ProtocolBugs{
				SendCurve: CurveCECPQ2b,
			},
		},
		flags: []string{
			"-max-version", strconv.Itoa(VersionTLS12),
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	// CECPQ2 should not be offered by default as a client.
	testCases = append(testCases, testCase{
		name: "CECPQ2NotEnabledByDefaultInClients",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FailIfCECPQ2Offered: true,
			},
		},
	})

	// CECPQ2b should not be offered by default as a client.
	testCases = append(testCases, testCase{
		name: "CECPQ2bNotEnabledByDefaultInClients",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FailIfCECPQ2Offered: true,
			},
		},
	})

	// If CECPQ2 is offered, both X25519 and CECPQ2 should have a key-share.
	testCases = append(testCases, testCase{
		name: "NotJustCECPQ2KeyShare",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveCECPQ2, CurveX25519},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-curves", strconv.Itoa(int(CurveX25519)),
			"-expect-curve-id", strconv.Itoa(int(CurveCECPQ2)),
		},
	})

	// ... but only if CECPQ2 is listed first.
	testCases = append(testCases, testCase{
		name: "CECPQ2KeyShareNotIncludedSecond",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveX25519},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveX25519)),
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-expect-curve-id", strconv.Itoa(int(CurveX25519)),
		},
	})

	// If CECPQ2b is offered, both X25519 and CECPQ2b should have a key-share.
	testCases = append(testCases, testCase{
		name: "NotJustCECPQ2bKeyShare",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveCECPQ2b, CurveX25519},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-curves", strconv.Itoa(int(CurveX25519)),
			"-expect-curve-id", strconv.Itoa(int(CurveCECPQ2b)),
		},
	})

	// ... but only if CECPQ2b is listed first.
	testCases = append(testCases, testCase{
		name: "CECPQ2bKeyShareNotIncludedSecond",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveX25519},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveX25519)),
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-expect-curve-id", strconv.Itoa(int(CurveX25519)),
		},
	})

	// If CECPQ2 is the only configured curve, the key share is sent.
	testCases = append(testCases, testCase{
		name: "JustConfiguringCECPQ2Works",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveCECPQ2},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-expect-curve-id", strconv.Itoa(int(CurveCECPQ2)),
		},
	})

	// If CECPQ2b is the only configured curve, the key share is sent.
	testCases = append(testCases, testCase{
		name: "JustConfiguringCECPQ2bWorks",
		config: Config{
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectedKeyShares: []CurveID{CurveCECPQ2b},
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-expect-curve-id", strconv.Itoa(int(CurveCECPQ2b)),
		},
	})

	// As a server, CECPQ2 is not yet supported by default.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "CECPQ2NotEnabledByDefaultForAServer",
		config: Config{
			MinVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveCECPQ2, CurveX25519},
			DefaultCurves:    []CurveID{CurveCECPQ2},
		},
		flags: []string{
			"-server-preference",
			"-expect-curve-id", strconv.Itoa(int(CurveX25519)),
		},
	})

	// As a server, CECPQ2b is not yet supported by default.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "CECPQ2bNotEnabledByDefaultForAServer",
		config: Config{
			MinVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveCECPQ2b, CurveX25519},
			DefaultCurves:    []CurveID{CurveCECPQ2b},
		},
		flags: []string{
			"-server-preference",
			"-expect-curve-id", strconv.Itoa(int(CurveX25519)),
		},
	})
}

func addTLS13RecordTests() {
	testCases = append(testCases, testCase{
		name: "TLS13-RecordPadding",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				RecordPadding: 10,
			},
		},
	})

	testCases = append(testCases, testCase{
		name: "TLS13-EmptyRecords",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				OmitRecordContents: true,
			},
		},
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})

	testCases = append(testCases, testCase{
		name: "TLS13-OnlyPadding",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				OmitRecordContents: true,
				RecordPadding:      10,
			},
		},
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})

	testCases = append(testCases, testCase{
		name: "TLS13-WrongOuterRecord",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				OuterRecordType: recordTypeHandshake,
			},
		},
		shouldFail:    true,
		expectedError: ":INVALID_OUTER_RECORD_TYPE:",
	})
}

func addSessionTicketTests() {
	testCases = append(testCases, testCase{
		// In TLS 1.2 and below, empty NewSessionTicket messages
		// mean the server changed its mind on sending a ticket.
		name: "SendEmptySessionTicket",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendEmptySessionTicket: true,
			},
		},
		flags: []string{"-expect-no-session"},
	})

	// Test that the server ignores unknown PSK modes.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-SendUnknownModeSessionTicket-Server",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendPSKKeyExchangeModes: []byte{0x1a, pskDHEKEMode, 0x2a},
			},
		},
		resumeSession:         true,
		expectedResumeVersion: VersionTLS13,
	})

	// Test that the server does not send session tickets with no matching key exchange mode.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-ExpectNoSessionTicketOnBadKEMode-Server",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendPSKKeyExchangeModes:  []byte{0x1a},
				ExpectNoNewSessionTicket: true,
			},
		},
	})

	// Test that the server does not accept a session with no matching key exchange mode.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-SendBadKEModeSessionTicket-Server",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendPSKKeyExchangeModes: []byte{0x1a},
			},
		},
		resumeSession:        true,
		expectResumeRejected: true,
	})

	// Test that the client ticket age is sent correctly.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-TestValidTicketAge-Client",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectTicketAge: 10 * time.Second,
			},
		},
		resumeSession: true,
		flags: []string{
			"-resumption-delay", "10",
		},
	})

	// Test that the client ticket age is enforced.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-TestBadTicketAge-Client",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectTicketAge: 1000 * time.Second,
			},
		},
		resumeSession:      true,
		shouldFail:         true,
		expectedLocalError: "tls: invalid ticket age",
	})

	// Test that the server's ticket age skew reporting works.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Forward",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge: 15 * time.Second,
			},
		},
		resumeSession:        true,
		resumeRenewedSession: true,
		flags: []string{
			"-resumption-delay", "10",
			"-expect-ticket-age-skew", "5",
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Backward",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge: 5 * time.Second,
			},
		},
		resumeSession:        true,
		resumeRenewedSession: true,
		flags: []string{
			"-resumption-delay", "10",
			"-expect-ticket-age-skew", "-5",
		},
	})

	// Test that ticket age skew up to 60 seconds in either direction is accepted.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Forward-60-Accept",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge:           70 * time.Second,
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		resumeSession: true,
		flags: []string{
			"-resumption-delay", "10",
			"-expect-ticket-age-skew", "60",
			// 0-RTT is accepted.
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Backward-60-Accept",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge:           10 * time.Second,
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		resumeSession: true,
		flags: []string{
			"-resumption-delay", "70",
			"-expect-ticket-age-skew", "-60",
			// 0-RTT is accepted.
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})

	// Test that ticket age skew beyond 60 seconds in either direction is rejected.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Forward-61-Reject",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge:           71 * time.Second,
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-resumption-delay", "10",
			"-expect-ticket-age-skew", "61",
			// 0-RTT is rejected.
			"-enable-early-data",
			"-expect-reject-early-data",
			"-on-resume-expect-early-data-reason", "ticket_age_skew",
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-TicketAgeSkew-Backward-61-Reject",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketAge:           10 * time.Second,
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-resumption-delay", "71",
			"-expect-ticket-age-skew", "-61",
			// 0-RTT is rejected.
			"-enable-early-data",
			"-expect-reject-early-data",
			"-on-resume-expect-early-data-reason", "ticket_age_skew",
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-SendTicketEarlyDataSupport",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
		},
	})

	// Test that 0-RTT tickets are still recorded as such when early data is disabled overall.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-SendTicketEarlyDataSupport-Disabled",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		flags: []string{
			"-expect-ticket-supports-early-data",
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-DuplicateTicketEarlyDataSupport",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				DuplicateTicketEarlyData: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":DUPLICATE_EXTENSION:",
		expectedLocalError: "remote error: illegal parameter",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-ExpectTicketEarlyDataSupport",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectTicketEarlyData: true,
			},
		},
		flags: []string{
			"-enable-early-data",
		},
	})

	// Test that, in TLS 1.3, the server-offered NewSessionTicket lifetime
	// is honored.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-HonorServerSessionTicketLifetime",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketLifetime: 20 * time.Second,
			},
		},
		flags: []string{
			"-resumption-delay", "19",
		},
		resumeSession: true,
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13-HonorServerSessionTicketLifetime-2",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendTicketLifetime: 20 * time.Second,
				// The client should not offer the expired session.
				ExpectNoTLS13PSK: true,
			},
		},
		flags: []string{
			"-resumption-delay", "21",
		},
		resumeSession:        true,
		expectResumeRejected: true,
	})

	for _, ver := range tlsVersions {
		// Prior to TLS 1.3, disabling session tickets enables session IDs.
		useStatefulResumption := ver.version < VersionTLS13

		// SSL_OP_NO_TICKET implies the server must not mint any tickets.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     ver.name + "-NoTicket-NoMint",
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					ExpectNoNewSessionTicket: true,
					RequireSessionIDs:        useStatefulResumption,
				},
			},
			resumeSession: useStatefulResumption,
			flags:         []string{"-no-ticket"},
		})

		// SSL_OP_NO_TICKET implies the server must not accept any tickets.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     ver.name + "-NoTicket-NoAccept",
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			resumeSession:        true,
			expectResumeRejected: true,
			// Set SSL_OP_NO_TICKET on the second connection, after the first
			// has established tickets.
			flags: []string{"-on-resume-no-ticket"},
		})
	}
}

func addChangeCipherSpecTests() {
	// Test missing ChangeCipherSpecs.
	testCases = append(testCases, testCase{
		name: "SkipChangeCipherSpec-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SkipChangeCipherSpec: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipChangeCipherSpec-Server",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SkipChangeCipherSpec: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipChangeCipherSpec-Server-NPN",
		config: Config{
			MaxVersion: VersionTLS12,
			NextProtos: []string{"bar"},
			Bugs: ProtocolBugs{
				SkipChangeCipherSpec: true,
			},
		},
		flags: []string{
			"-advertise-npn", "\x03foo\x03bar\x03baz",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})

	// Test synchronization between the handshake and ChangeCipherSpec.
	// Partial post-CCS handshake messages before ChangeCipherSpec should be
	// rejected. Test both with and without handshake packing to handle both
	// when the partial post-CCS message is in its own record and when it is
	// attached to the pre-CCS message.
	for _, packed := range []bool{false, true} {
		var suffix string
		if packed {
			suffix = "-Packed"
		}

		testCases = append(testCases, testCase{
			name: "FragmentAcrossChangeCipherSpec-Client" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					FragmentAcrossChangeCipherSpec: true,
					PackHandshakeFlight:            packed,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		})
		testCases = append(testCases, testCase{
			name: "FragmentAcrossChangeCipherSpec-Client-Resume" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
			},
			resumeSession: true,
			resumeConfig: &Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					FragmentAcrossChangeCipherSpec: true,
					PackHandshakeFlight:            packed,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "FragmentAcrossChangeCipherSpec-Server" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					FragmentAcrossChangeCipherSpec: true,
					PackHandshakeFlight:            packed,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "FragmentAcrossChangeCipherSpec-Server-Resume" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
			},
			resumeSession: true,
			resumeConfig: &Config{
				MaxVersion: VersionTLS12,
				Bugs: ProtocolBugs{
					FragmentAcrossChangeCipherSpec: true,
					PackHandshakeFlight:            packed,
				},
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		})
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "FragmentAcrossChangeCipherSpec-Server-NPN" + suffix,
			config: Config{
				MaxVersion: VersionTLS12,
				NextProtos: []string{"bar"},
				Bugs: ProtocolBugs{
					FragmentAcrossChangeCipherSpec: true,
					PackHandshakeFlight:            packed,
				},
			},
			flags: []string{
				"-advertise-npn", "\x03foo\x03bar\x03baz",
			},
			shouldFail:    true,
			expectedError: ":UNEXPECTED_RECORD:",
		})
	}

	// Test that, in DTLS, ChangeCipherSpec is not allowed when there are
	// messages in the handshake queue. Do this by testing the server
	// reading the client Finished, reversing the flight so Finished comes
	// first.
	testCases = append(testCases, testCase{
		protocol: dtls,
		testType: serverTest,
		name:     "SendUnencryptedFinished-DTLS",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendUnencryptedFinished:   true,
				ReverseHandshakeFragments: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BUFFERED_MESSAGES_ON_CIPHER_CHANGE:",
	})

	// Test synchronization between encryption changes and the handshake in
	// TLS 1.3, where ChangeCipherSpec is implicit.
	testCases = append(testCases, testCase{
		name: "PartialEncryptedExtensionsWithServerHello",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				PartialEncryptedExtensionsWithServerHello: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BUFFERED_MESSAGES_ON_CIPHER_CHANGE:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PartialClientFinishedWithClientHello",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				PartialClientFinishedWithClientHello: true,
			},
		},
		shouldFail:    true,
		expectedError: ":BUFFERED_MESSAGES_ON_CIPHER_CHANGE:",
	})

	// Test that early ChangeCipherSpecs are handled correctly.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyChangeCipherSpec-server-1",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				EarlyChangeCipherSpec: 1,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyChangeCipherSpec-server-2",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				EarlyChangeCipherSpec: 2,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "StrayChangeCipherSpec",
		config: Config{
			// TODO(davidben): Once DTLS 1.3 exists, test
			// that stray ChangeCipherSpec messages are
			// rejected.
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				StrayChangeCipherSpec: true,
			},
		},
	})

	// Test that reordered ChangeCipherSpecs are tolerated.
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "ReorderChangeCipherSpec-DTLS-Client",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ReorderChangeCipherSpec: true,
			},
		},
		resumeSession: true,
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		protocol: dtls,
		name:     "ReorderChangeCipherSpec-DTLS-Server",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ReorderChangeCipherSpec: true,
			},
		},
		resumeSession: true,
	})

	// Test that the contents of ChangeCipherSpec are checked.
	testCases = append(testCases, testCase{
		name: "BadChangeCipherSpec-1",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadChangeCipherSpec: []byte{2},
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_CHANGE_CIPHER_SPEC:",
	})
	testCases = append(testCases, testCase{
		name: "BadChangeCipherSpec-2",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadChangeCipherSpec: []byte{1, 1},
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_CHANGE_CIPHER_SPEC:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "BadChangeCipherSpec-DTLS-1",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadChangeCipherSpec: []byte{2},
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_CHANGE_CIPHER_SPEC:",
	})
	testCases = append(testCases, testCase{
		protocol: dtls,
		name:     "BadChangeCipherSpec-DTLS-2",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				BadChangeCipherSpec: []byte{1, 1},
			},
		},
		shouldFail:    true,
		expectedError: ":BAD_CHANGE_CIPHER_SPEC:",
	})
}

type perMessageTest struct {
	messageType uint8
	test        testCase
}

// makePerMessageTests returns a series of test templates which cover each
// message in the TLS handshake. These may be used with bugs like
// WrongMessageType to fully test a per-message bug.
func makePerMessageTests() []perMessageTest {
	var ret []perMessageTest
	for _, protocol := range []protocol{tls, dtls} {
		var suffix string
		if protocol == dtls {
			suffix = "-DTLS"
		}

		ret = append(ret, perMessageTest{
			messageType: typeClientHello,
			test: testCase{
				protocol: protocol,
				testType: serverTest,
				name:     "ClientHello" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		if protocol == dtls {
			ret = append(ret, perMessageTest{
				messageType: typeHelloVerifyRequest,
				test: testCase{
					protocol: protocol,
					name:     "HelloVerifyRequest" + suffix,
					config: Config{
						MaxVersion: VersionTLS12,
					},
				},
			})
		}

		ret = append(ret, perMessageTest{
			messageType: typeServerHello,
			test: testCase{
				protocol: protocol,
				name:     "ServerHello" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeCertificate,
			test: testCase{
				protocol: protocol,
				name:     "ServerCertificate" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeCertificateStatus,
			test: testCase{
				protocol: protocol,
				name:     "CertificateStatus" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
				flags: []string{"-enable-ocsp-stapling"},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeServerKeyExchange,
			test: testCase{
				protocol: protocol,
				name:     "ServerKeyExchange" + suffix,
				config: Config{
					MaxVersion:   VersionTLS12,
					CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeCertificateRequest,
			test: testCase{
				protocol: protocol,
				name:     "CertificateRequest" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
					ClientAuth: RequireAnyClientCert,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeServerHelloDone,
			test: testCase{
				protocol: protocol,
				name:     "ServerHelloDone" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeCertificate,
			test: testCase{
				testType: serverTest,
				protocol: protocol,
				name:     "ClientCertificate" + suffix,
				config: Config{
					Certificates: []Certificate{rsaCertificate},
					MaxVersion:   VersionTLS12,
				},
				flags: []string{"-require-any-client-certificate"},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeCertificateVerify,
			test: testCase{
				testType: serverTest,
				protocol: protocol,
				name:     "CertificateVerify" + suffix,
				config: Config{
					Certificates: []Certificate{rsaCertificate},
					MaxVersion:   VersionTLS12,
				},
				flags: []string{"-require-any-client-certificate"},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeClientKeyExchange,
			test: testCase{
				testType: serverTest,
				protocol: protocol,
				name:     "ClientKeyExchange" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		if protocol != dtls {
			ret = append(ret, perMessageTest{
				messageType: typeNextProtocol,
				test: testCase{
					testType: serverTest,
					protocol: protocol,
					name:     "NextProtocol" + suffix,
					config: Config{
						MaxVersion: VersionTLS12,
						NextProtos: []string{"bar"},
					},
					flags: []string{"-advertise-npn", "\x03foo\x03bar\x03baz"},
				},
			})

			ret = append(ret, perMessageTest{
				messageType: typeChannelID,
				test: testCase{
					testType: serverTest,
					protocol: protocol,
					name:     "ChannelID" + suffix,
					config: Config{
						MaxVersion: VersionTLS12,
						ChannelID:  channelIDKey,
					},
					flags: []string{
						"-expect-channel-id",
						base64.StdEncoding.EncodeToString(channelIDBytes),
					},
				},
			})
		}

		ret = append(ret, perMessageTest{
			messageType: typeFinished,
			test: testCase{
				testType: serverTest,
				protocol: protocol,
				name:     "ClientFinished" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeNewSessionTicket,
			test: testCase{
				protocol: protocol,
				name:     "NewSessionTicket" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

		ret = append(ret, perMessageTest{
			messageType: typeFinished,
			test: testCase{
				protocol: protocol,
				name:     "ServerFinished" + suffix,
				config: Config{
					MaxVersion: VersionTLS12,
				},
			},
		})

	}

	ret = append(ret, perMessageTest{
		messageType: typeClientHello,
		test: testCase{
			testType: serverTest,
			name:     "TLS13-ClientHello",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeServerHello,
		test: testCase{
			name: "TLS13-ServerHello",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeEncryptedExtensions,
		test: testCase{
			name: "TLS13-EncryptedExtensions",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeCertificateRequest,
		test: testCase{
			name: "TLS13-CertificateRequest",
			config: Config{
				MaxVersion: VersionTLS13,
				ClientAuth: RequireAnyClientCert,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeCertificate,
		test: testCase{
			name: "TLS13-ServerCertificate",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeCertificateVerify,
		test: testCase{
			name: "TLS13-ServerCertificateVerify",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeFinished,
		test: testCase{
			name: "TLS13-ServerFinished",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeCertificate,
		test: testCase{
			testType: serverTest,
			name:     "TLS13-ClientCertificate",
			config: Config{
				Certificates: []Certificate{rsaCertificate},
				MaxVersion:   VersionTLS13,
			},
			flags: []string{"-require-any-client-certificate"},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeCertificateVerify,
		test: testCase{
			testType: serverTest,
			name:     "TLS13-ClientCertificateVerify",
			config: Config{
				Certificates: []Certificate{rsaCertificate},
				MaxVersion:   VersionTLS13,
			},
			flags: []string{"-require-any-client-certificate"},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeFinished,
		test: testCase{
			testType: serverTest,
			name:     "TLS13-ClientFinished",
			config: Config{
				MaxVersion: VersionTLS13,
			},
		},
	})

	ret = append(ret, perMessageTest{
		messageType: typeEndOfEarlyData,
		test: testCase{
			testType: serverTest,
			name:     "TLS13-EndOfEarlyData",
			config: Config{
				MaxVersion: VersionTLS13,
			},
			resumeConfig: &Config{
				MaxVersion: VersionTLS13,
				Bugs: ProtocolBugs{
					SendEarlyData:           [][]byte{{1, 2, 3, 4}},
					ExpectEarlyDataAccepted: true,
				},
			},
			resumeSession: true,
			flags:         []string{"-enable-early-data"},
		},
	})

	return ret
}

func addWrongMessageTypeTests() {
	for _, t := range makePerMessageTests() {
		t.test.name = "WrongMessageType-" + t.test.name
		if t.test.resumeConfig != nil {
			t.test.resumeConfig.Bugs.SendWrongMessageType = t.messageType
		} else {
			t.test.config.Bugs.SendWrongMessageType = t.messageType
		}
		t.test.shouldFail = true
		t.test.expectedError = ":UNEXPECTED_MESSAGE:"
		t.test.expectedLocalError = "remote error: unexpected message"

		if t.test.config.MaxVersion >= VersionTLS13 && t.messageType == typeServerHello {
			// In TLS 1.3, a bad ServerHello means the client sends
			// an unencrypted alert while the server expects
			// encryption, so the alert is not readable by runner.
			t.test.expectedLocalError = "local error: bad record MAC"
		}

		testCases = append(testCases, t.test)
	}

	// The processing order for TLS 1.3 version negotiation is such that one
	// may accidentally accept a HelloRetryRequest in lieu of ServerHello in
	// TLS 1.2. Test that we do not do this.
	testCases = append(testCases, testCase{
		name: "SendServerHelloAsHelloRetryRequest",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendServerHelloAsHelloRetryRequest: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_MESSAGE:",
		expectedLocalError: "remote error: unexpected message",
	})
}

func addTrailingMessageDataTests() {
	for _, t := range makePerMessageTests() {
		t.test.name = "TrailingMessageData-" + t.test.name
		if t.test.resumeConfig != nil {
			t.test.resumeConfig.Bugs.SendTrailingMessageData = t.messageType
		} else {
			t.test.config.Bugs.SendTrailingMessageData = t.messageType
		}
		t.test.shouldFail = true
		t.test.expectedError = ":DECODE_ERROR:"
		t.test.expectedLocalError = "remote error: error decoding message"

		if t.test.config.MaxVersion >= VersionTLS13 && t.messageType == typeServerHello {
			// In TLS 1.3, a bad ServerHello means the client sends
			// an unencrypted alert while the server expects
			// encryption, so the alert is not readable by runner.
			t.test.expectedLocalError = "local error: bad record MAC"
		}

		if t.messageType == typeFinished {
			// Bad Finished messages read as the verify data having
			// the wrong length.
			t.test.expectedError = ":DIGEST_CHECK_FAILED:"
			t.test.expectedLocalError = "remote error: error decrypting message"
		}

		testCases = append(testCases, t.test)
	}
}

func addTLS13HandshakeTests() {
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "NegotiatePSKResumption-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				NegotiatePSKResumption: true,
			},
		},
		resumeSession: true,
		shouldFail:    true,
		expectedError: ":MISSING_KEY_SHARE:",
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "MissingKeyShare-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				MissingKeyShare: true,
			},
		},
		shouldFail:    true,
		expectedError: ":MISSING_KEY_SHARE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "MissingKeyShare-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				MissingKeyShare: true,
			},
		},
		shouldFail:    true,
		expectedError: ":MISSING_KEY_SHARE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "DuplicateKeyShares-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				DuplicateKeyShares: true,
			},
		},
		shouldFail:    true,
		expectedError: ":DUPLICATE_KEY_SHARE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
			},
		},
	})

	// Test that enabling TLS 1.3 does not interfere with TLS 1.2 session ID
	// resumption.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ResumeTLS12SessionID-TLS13",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
		resumeSession: true,
	})

	// Test that the client correctly handles a TLS 1.3 ServerHello which echoes
	// a TLS 1.2 session ID.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS12SessionID-TLS13",
		config: Config{
			MaxVersion:             VersionTLS12,
			SessionTicketsDisabled: true,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
		},
		resumeSession:        true,
		expectResumeRejected: true,
	})

	// Test that the server correctly echoes back session IDs of
	// various lengths. The first test additionally asserts that
	// BoringSSL always sends the ChangeCipherSpec messages for
	// compatibility mode, rather than negotiating it based on the
	// ClientHello.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EmptySessionID-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendClientHelloSessionID: []byte{},
			},
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ShortSessionID-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendClientHelloSessionID: make([]byte, 16),
			},
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "FullSessionID-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendClientHelloSessionID: make([]byte, 32),
			},
		},
	})

	// Test that the client sends a fake session ID in TLS 1.3.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS13SessionID-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectClientHelloSessionID: true,
			},
		},
	})

	// Test that the client omits the fake session ID when the max version is TLS 1.2 and below.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TLS12NoSessionID-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectNoTLS12Session: true,
			},
		},
		flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MinVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MinVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				ExpectEarlyData: [][]byte{{'h', 'e', 'l', 'l', 'o'}},
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-on-initial-expect-early-data-reason", "no_session_offered",
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
			"-on-resume-shim-writes-first",
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-Reject-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				AlwaysRejectEarlyData: true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			"-on-resume-shim-writes-first",
			"-on-retry-expect-early-data-reason", "peer_declined",
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		messageCount:  2,
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-initial-expect-early-data-reason", "no_session_offered",
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})

	// The above tests the most recent ticket. Additionally test that 0-RTT
	// works on the first ticket issued by the server.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-FirstTicket-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				UseFirstSessionTicket:   true,
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		messageCount:  2,
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-OmitEarlyDataExtension-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
				OmitEarlyDataExtension:  true,
			},
		},
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-TooMuchData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 16384 + 1,
			},
		},
		shouldFail:    true,
		expectedError: ":TOO_MUCH_SKIPPED_EARLY_DATA:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-Interleaved-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
				InterleaveEarlyData:     true,
			},
		},
		shouldFail:    true,
		expectedError: ":DECRYPTION_FAILED_OR_BAD_RECORD_MAC:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-EarlyDataInTLS12-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
		flags:         []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-HRR-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
			},
			DefaultCurves: []CurveID{},
		},
		// Though the session is not resumed and we send HelloRetryRequest,
		// early data being disabled takes priority as the reject reason.
		flags: []string{"-expect-early-data-reason", "disabled"},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-HRR-Interleaved-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 4,
				InterleaveEarlyData:     true,
			},
			DefaultCurves: []CurveID{},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_RECORD:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-HRR-TooMuchData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendFakeEarlyDataLength: 16384 + 1,
			},
			DefaultCurves: []CurveID{},
		},
		shouldFail:    true,
		expectedError: ":TOO_MUCH_SKIPPED_EARLY_DATA:",
	})

	// Test that skipping early data looking for cleartext correctly
	// processes an alert record.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-HRR-FatalAlert-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyAlert:          true,
				SendFakeEarlyDataLength: 4,
			},
			DefaultCurves: []CurveID{},
		},
		shouldFail:    true,
		expectedError: ":SSLV3_ALERT_HANDSHAKE_FAILURE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipEarlyData-SecondClientHelloEarlyData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyDataOnSecondClientHello: true,
			},
			DefaultCurves: []CurveID{},
		},
		shouldFail:         true,
		expectedLocalError: "remote error: bad record MAC",
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EmptyEncryptedExtensions-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				EmptyEncryptedExtensions: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "remote error: error decoding message",
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EncryptedExtensionsWithKeyShare-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				EncryptedExtensionsWithKeyShare: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "remote error: unsupported extension",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SendHelloRetryRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// Require a HelloRetryRequest for every curve.
			DefaultCurves:    []CurveID{},
			CurvePreferences: []CurveID{CurveX25519},
		},
		expectedCurveID: CurveX25519,
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SendHelloRetryRequest-2-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			DefaultCurves:    []CurveID{CurveP384},
			CurvePreferences: []CurveID{CurveX25519, CurveP384},
		},
		// Although the ClientHello did not predict our preferred curve,
		// we always select it whether it is predicted or not.
		expectedCurveID: CurveX25519,
	})

	testCases = append(testCases, testCase{
		name: "UnknownCurve-HelloRetryRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCurve: bogusCurve,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-CipherChange-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendCipherSuite:                  TLS_AES_128_GCM_SHA256,
				SendHelloRetryRequestCipherSuite: TLS_CHACHA20_POLY1305_SHA256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CIPHER_RETURNED:",
	})

	// Test that the client does not offer a PSK in the second ClientHello if the
	// HelloRetryRequest is incompatible with it.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "HelloRetryRequest-NonResumableCipher-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
			},
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				ExpectNoTLS13PSKAfterHRR: true,
			},
			CipherSuites: []uint16{
				TLS_AES_256_GCM_SHA384,
			},
		},
		resumeSession:        true,
		expectResumeRejected: true,
	})

	testCases = append(testCases, testCase{
		name: "DisabledCurve-HelloRetryRequest-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveP256},
			Bugs: ProtocolBugs{
				IgnorePeerCurvePreferences: true,
			},
		},
		flags:         []string{"-curves", strconv.Itoa(int(CurveP384))},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "UnnecessaryHelloRetryRequest-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			CurvePreferences: []CurveID{CurveX25519},
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCurve: CurveX25519,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "SecondHelloRetryRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SecondHelloRetryRequest: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_MESSAGE:",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-Empty-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				AlwaysSendHelloRetryRequest: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":EMPTY_HELLO_RETRY_REQUEST:",
		expectedLocalError: "remote error: illegal parameter",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-DuplicateCurve-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires a HelloRetryRequest against BoringSSL's default
			// configuration. Assert this ExpectMissingKeyShare.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				ExpectMissingKeyShare:                true,
				DuplicateHelloRetryRequestExtensions: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":DUPLICATE_EXTENSION:",
		expectedLocalError: "remote error: illegal parameter",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-Cookie-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte("cookie"),
			},
		},
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-DuplicateCookie-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie:          []byte("cookie"),
				DuplicateHelloRetryRequestExtensions: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":DUPLICATE_EXTENSION:",
		expectedLocalError: "remote error: illegal parameter",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-EmptyCookie-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte{},
			},
		},
		shouldFail:    true,
		expectedError: ":DECODE_ERROR:",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-Cookie-Curve-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte("cookie"),
				ExpectMissingKeyShare:       true,
			},
		},
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequest-Unknown-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				CustomHelloRetryRequestExtension: "extension",
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_EXTENSION:",
		expectedLocalError: "remote error: unsupported extension",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SecondClientHelloMissingKeyShare-TLS13",
		config: Config{
			MaxVersion:    VersionTLS13,
			DefaultCurves: []CurveID{},
			Bugs: ProtocolBugs{
				SecondClientHelloMissingKeyShare: true,
			},
		},
		shouldFail:    true,
		expectedError: ":MISSING_KEY_SHARE:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SecondClientHelloWrongCurve-TLS13",
		config: Config{
			MaxVersion:    VersionTLS13,
			DefaultCurves: []CurveID{},
			Bugs: ProtocolBugs{
				MisinterpretHelloRetryRequestCurve: CurveP521,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequestVersionMismatch-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendServerHelloVersion: 0x0305,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_VERSION_NUMBER:",
	})

	testCases = append(testCases, testCase{
		name: "HelloRetryRequestCurveMismatch-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				// Send P-384 (correct) in the HelloRetryRequest.
				SendHelloRetryRequestCurve: CurveP384,
				// But send P-256 in the ServerHello.
				SendCurve: CurveP256,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	// Test the server selecting a curve that requires a HelloRetryRequest
	// without sending it.
	testCases = append(testCases, testCase{
		name: "SkipHelloRetryRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SkipHelloRetryRequest: true,
			},
		},
		shouldFail:    true,
		expectedError: ":WRONG_CURVE:",
	})

	testCases = append(testCases, testCase{
		name: "SecondServerHelloNoVersion-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				OmitServerSupportedVersionExtension: true,
			},
		},
		shouldFail:    true,
		expectedError: ":SECOND_SERVERHELLO_VERSION_MISMATCH:",
	})
	testCases = append(testCases, testCase{
		name: "SecondServerHelloWrongVersion-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			// P-384 requires HelloRetryRequest in BoringSSL.
			CurvePreferences: []CurveID{CurveP384},
			Bugs: ProtocolBugs{
				SendServerSupportedVersionExtension: 0x1234,
			},
		},
		shouldFail:    true,
		expectedError: ":SECOND_SERVERHELLO_VERSION_MISMATCH:",
	})

	testCases = append(testCases, testCase{
		name: "RequestContextInHandshake-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
			Bugs: ProtocolBugs{
				SendRequestContext: []byte("request context"),
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		shouldFail:    true,
		expectedError: ":DECODE_ERROR:",
	})

	testCases = append(testCases, testCase{
		name: "UnknownExtensionInCertificateRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
			Bugs: ProtocolBugs{
				SendCustomCertificateRequest: 0x1212,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
	})

	testCases = append(testCases, testCase{
		name: "MissingSignatureAlgorithmsInCertificateRequest-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			ClientAuth: RequireAnyClientCert,
			Bugs: ProtocolBugs{
				OmitCertificateRequestAlgorithms: true,
			},
		},
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaKeyFile),
		},
		shouldFail:    true,
		expectedError: ":DECODE_ERROR:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TrailingKeyShareData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				TrailingKeyShareData: true,
			},
		},
		shouldFail:    true,
		expectedError: ":DECODE_ERROR:",
	})

	testCases = append(testCases, testCase{
		name: "AlwaysSelectPSKIdentity-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				AlwaysSelectPSKIdentity: true,
			},
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	testCases = append(testCases, testCase{
		name: "InvalidPSKIdentity-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SelectPSKIdentityOnResume: 1,
			},
		},
		resumeSession: true,
		shouldFail:    true,
		expectedError: ":PSK_IDENTITY_NOT_FOUND:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ExtraPSKIdentity-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExtraPSKIdentity:   true,
				SendExtraPSKBinder: true,
			},
		},
		resumeSession: true,
	})

	// Test that unknown NewSessionTicket extensions are tolerated.
	testCases = append(testCases, testCase{
		name: "CustomTicketExtension-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				CustomTicketExtension: "1234",
			},
		},
	})

	// Test the client handles 0-RTT being rejected by a full handshake.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-RejectTicket-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Certificates:     []Certificate{rsaCertificate},
		},
		resumeConfig: &Config{
			MaxVersion:             VersionTLS13,
			MaxEarlyDataSize:       16384,
			Certificates:           []Certificate{ecdsaP256Certificate},
			SessionTicketsDisabled: true,
		},
		resumeSession:        true,
		expectResumeRejected: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			"-on-resume-shim-writes-first",
			"-on-retry-expect-early-data-reason", "session_not_resumed",
			// Test the peer certificate is reported correctly in each of the
			// three logical connections.
			"-on-initial-expect-peer-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-on-resume-expect-peer-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-on-retry-expect-peer-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			// Session tickets are disabled, so the runner will not send a ticket.
			"-on-retry-expect-no-session",
		},
	})

	// Test the server rejects 0-RTT if it does not recognize the ticket.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-RejectTicket-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
				// Corrupt the ticket.
				FilterTicket: func(in []byte) ([]byte, error) {
					in[len(in)-1] ^= 1
					return in, nil
				},
			},
		},
		messageCount:         2,
		resumeSession:        true,
		expectResumeRejected: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-reject-early-data",
			"-on-resume-expect-early-data-reason", "session_not_resumed",
		},
	})

	// Test the client handles 0-RTT being rejected via a HelloRetryRequest.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-HRR-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte{1, 2, 3, 4},
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			"-on-retry-expect-early-data-reason", "hello_retry_request",
		},
	})

	// Test the server rejects 0-RTT if it needs to send a HelloRetryRequest.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-HRR-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			// Require a HelloRetryRequest for every curve.
			DefaultCurves: []CurveID{},
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		messageCount:  2,
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-reject-early-data",
			"-on-resume-expect-early-data-reason", "hello_retry_request",
		},
	})

	// Test the client handles a 0-RTT reject from both ticket rejection and
	// HelloRetryRequest.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-HRR-RejectTicket-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Certificates:     []Certificate{rsaCertificate},
		},
		resumeConfig: &Config{
			MaxVersion:             VersionTLS13,
			MaxEarlyDataSize:       16384,
			Certificates:           []Certificate{ecdsaP256Certificate},
			SessionTicketsDisabled: true,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte{1, 2, 3, 4},
			},
		},
		resumeSession:        true,
		expectResumeRejected: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			// The client sees HelloRetryRequest before the resumption result,
			// though neither value is inherently preferable.
			"-on-retry-expect-early-data-reason", "hello_retry_request",
			// Test the peer certificate is reported correctly in each of the
			// three logical connections.
			"-on-initial-expect-peer-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-on-resume-expect-peer-cert-file", path.Join(*resourceDir, rsaCertificateFile),
			"-on-retry-expect-peer-cert-file", path.Join(*resourceDir, ecdsaP256CertificateFile),
			// Session tickets are disabled, so the runner will not send a ticket.
			"-on-retry-expect-no-session",
		},
	})

	// Test the server rejects 0-RTT if it needs to send a HelloRetryRequest.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-HRR-RejectTicket-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			// Require a HelloRetryRequest for every curve.
			DefaultCurves: []CurveID{},
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
				// Corrupt the ticket.
				FilterTicket: func(in []byte) ([]byte, error) {
					in[len(in)-1] ^= 1
					return in, nil
				},
			},
		},
		messageCount:         2,
		resumeSession:        true,
		expectResumeRejected: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-reject-early-data",
			// The server sees the missed resumption before HelloRetryRequest,
			// though neither value is inherently preferable.
			"-on-resume-expect-early-data-reason", "session_not_resumed",
		},
	})

	// The client must check the server does not send the early_data
	// extension while rejecting the session.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyDataWithoutResume-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:             VersionTLS13,
			SessionTicketsDisabled: true,
			Bugs: ProtocolBugs{
				SendEarlyDataExtension: true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	// The client must fail with a dedicated error code if the server
	// responds with TLS 1.2 when offering 0-RTT.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyDataVersionDowngrade-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS12,
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
		},
		shouldFail:    true,
		expectedError: ":WRONG_VERSION_ON_EARLY_DATA:",
	})

	// Test that the client rejects an (unsolicited) early_data extension if
	// the server sent an HRR.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ServerAcceptsEarlyDataOnHRR-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				SendHelloRetryRequestCookie: []byte{1, 2, 3, 4},
				SendEarlyDataExtension:      true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
		},
		shouldFail:    true,
		expectedError: ":UNEXPECTED_EXTENSION:",
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "SkipChangeCipherSpec-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SkipChangeCipherSpec: true,
			},
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "SkipChangeCipherSpec-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SkipChangeCipherSpec: true,
			},
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "TooManyChangeCipherSpec-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendExtraChangeCipherSpec: 33,
			},
		},
		shouldFail:    true,
		expectedError: ":TOO_MANY_EMPTY_FRAGMENTS:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TooManyChangeCipherSpec-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendExtraChangeCipherSpec: 33,
			},
		},
		shouldFail:    true,
		expectedError: ":TOO_MANY_EMPTY_FRAGMENTS:",
	})

	testCases = append(testCases, testCase{
		name: "SendPostHandshakeChangeCipherSpec-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendPostHandshakeChangeCipherSpec: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_RECORD:",
		expectedLocalError: "remote error: unexpected message",
	})

	fooString := "foo"
	barString := "bar"

	// Test that the client reports the correct ALPN after a 0-RTT reject
	// that changed it.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-ALPNMismatch-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				ALPNProtocol: &fooString,
			},
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				ALPNProtocol: &barString,
			},
		},
		resumeSession: true,
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar",
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			// The client does not learn ALPN was the cause.
			"-on-retry-expect-early-data-reason", "peer_declined",
			// In the 0-RTT state, we surface the predicted ALPN. After
			// processing the reject, we surface the real one.
			"-on-initial-expect-alpn", "foo",
			"-on-resume-expect-alpn", "foo",
			"-on-retry-expect-alpn", "bar",
		},
	})

	// Test that the client reports the correct ALPN after a 0-RTT reject if
	// ALPN was omitted from the first connection.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-ALPNOmitted1-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			NextProtos:       []string{"foo"},
		},
		resumeSession: true,
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar",
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			// The client does not learn ALPN was the cause.
			"-on-retry-expect-early-data-reason", "peer_declined",
			// In the 0-RTT state, we surface the predicted ALPN. After
			// processing the reject, we surface the real one.
			"-on-initial-expect-alpn", "",
			"-on-resume-expect-alpn", "",
			"-on-retry-expect-alpn", "foo",
			"-on-resume-shim-writes-first",
		},
	})

	// Test that the client reports the correct ALPN after a 0-RTT reject if
	// ALPN was omitted from the second connection.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-ALPNOmitted2-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			NextProtos:       []string{"foo"},
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeSession: true,
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar",
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-reject-early-data",
			// The client does not learn ALPN was the cause.
			"-on-retry-expect-early-data-reason", "peer_declined",
			// In the 0-RTT state, we surface the predicted ALPN. After
			// processing the reject, we surface the real one.
			"-on-initial-expect-alpn", "foo",
			"-on-resume-expect-alpn", "foo",
			"-on-retry-expect-alpn", "",
			"-on-resume-shim-writes-first",
		},
	})

	// Test that the client enforces ALPN match on 0-RTT accept.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-BadALPNMismatch-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				ALPNProtocol: &fooString,
			},
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				AlwaysAcceptEarlyData: true,
				ALPNProtocol:          &barString,
			},
		},
		resumeSession: true,
		flags: []string{
			"-advertise-alpn", "\x03foo\x03bar",
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-on-initial-expect-alpn", "foo",
			"-on-resume-expect-alpn", "foo",
			"-on-retry-expect-alpn", "bar",
		},
		shouldFail:    true,
		expectedError: ":ALPN_MISMATCH_ON_EARLY_DATA:",
	})

	// Test that the client does not offer early data if it is incompatible
	// with ALPN preferences.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-ALPNPreferenceChanged-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			NextProtos:       []string{"foo", "bar"},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-expect-no-offer-early-data",
			// Offer different ALPN values in the initial and resumption.
			"-on-initial-advertise-alpn", "\x03foo",
			"-on-initial-expect-alpn", "foo",
			"-on-resume-advertise-alpn", "\x03bar",
			"-on-resume-expect-alpn", "bar",
			// The ALPN mismatch comes from the client, so it reports it as the
			// reason.
			"-on-resume-expect-early-data-reason", "alpn_mismatch",
		},
	})

	// Test that the client does not offer 0-RTT to servers which never
	// advertise it.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-NonZeroRTTSession-Client-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-no-offer-early-data",
			// The client declines to offer 0-RTT because of the session.
			"-on-resume-expect-early-data-reason", "unsupported_for_session",
		},
	})

	// Test that the server correctly rejects 0-RTT when the previous
	// session did not allow early data on resumption.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-NonZeroRTTSession-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-on-resume-enable-early-data",
			"-expect-reject-early-data",
			// The server rejects 0-RTT because of the session.
			"-on-resume-expect-early-data-reason", "unsupported_for_session",
		},
	})

	// Test that we reject early data where ALPN is omitted from the first
	// connection.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-ALPNOmitted1-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{},
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"foo"},
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-initial-select-alpn", "",
			"-on-resume-select-alpn", "foo",
			"-on-resume-expect-early-data-reason", "alpn_mismatch",
		},
	})

	// Test that we reject early data where ALPN is omitted from the second
	// connection.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-ALPNOmitted2-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"foo"},
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{},
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-initial-select-alpn", "foo",
			"-on-resume-select-alpn", "",
			"-on-resume-expect-early-data-reason", "alpn_mismatch",
		},
	})

	// Test that we reject early data with mismatched ALPN.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-ALPNMismatch-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"foo"},
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			NextProtos: []string{"bar"},
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-initial-select-alpn", "foo",
			"-on-resume-select-alpn", "bar",
			"-on-resume-expect-early-data-reason", "alpn_mismatch",
		},
	})

	// Test that the client offering 0-RTT and Channel ID forbids the server
	// from accepting both.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyDataChannelID-AcceptBoth-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			RequestChannelID: true,
		},
		resumeSession:   true,
		expectChannelID: true,
		shouldFail:      true,
		expectedError:   ":UNEXPECTED_EXTENSION_ON_EARLY_DATA:",
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-send-channel-id", path.Join(*resourceDir, channelIDKeyFile),
		},
	})

	// Test that the client offering Channel ID and 0-RTT allows the server
	// to decline 0-RTT.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyDataChannelID-AcceptChannelID-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			RequestChannelID: true,
			Bugs: ProtocolBugs{
				AlwaysRejectEarlyData: true,
			},
		},
		resumeSession:   true,
		expectChannelID: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-send-channel-id", path.Join(*resourceDir, channelIDKeyFile),
			"-expect-reject-early-data",
			// The client never learns the reason was Channel ID.
			"-on-retry-expect-early-data-reason", "peer_declined",
		},
	})

	// Test that the client offering Channel ID and 0-RTT allows the server
	// to decline Channel ID.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyDataChannelID-AcceptEarlyData-Client-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-send-channel-id", path.Join(*resourceDir, channelIDKeyFile),
			"-on-resume-expect-accept-early-data",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})

	// Test that the server supporting Channel ID and 0-RTT declines 0-RTT
	// if it would negotiate Channel ID.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyDataChannelID-OfferBoth-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			ChannelID:  channelIDKey,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: false,
			},
		},
		resumeSession:   true,
		expectChannelID: true,
		flags: []string{
			"-enable-early-data",
			"-expect-reject-early-data",
			"-expect-channel-id",
			base64.StdEncoding.EncodeToString(channelIDBytes),
			"-on-resume-expect-early-data-reason", "channel_id",
		},
	})

	// Test that the server supporting Channel ID and 0-RTT accepts 0-RTT
	// if not offered Channel ID.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyDataChannelID-OfferEarlyData-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		resumeSession:   true,
		expectChannelID: false,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
			"-enable-channel-id",
			"-on-resume-expect-early-data-reason", "accept",
		},
	})

	// Test that the server rejects 0-RTT streams without end_of_early_data.
	// The subsequent records should fail to decrypt.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-SkipEndOfEarlyData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				SkipEndOfEarlyData:      true,
			},
		},
		resumeSession:      true,
		flags:              []string{"-enable-early-data"},
		shouldFail:         true,
		expectedLocalError: "remote error: bad record MAC",
		expectedError:      ":BAD_DECRYPT:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-UnexpectedHandshake-Server-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				SendStrayEarlyHandshake: true,
				ExpectEarlyDataAccepted: true,
			},
		},
		resumeSession:      true,
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_MESSAGE:",
		expectedLocalError: "remote error: unexpected message",
		flags: []string{
			"-enable-early-data",
		},
	})

	// Test that the client reports TLS 1.3 as the version while sending
	// early data.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-Client-VersionAPI-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-on-resume-expect-accept-early-data",
			"-expect-version", strconv.Itoa(VersionTLS13),
		},
	})

	// Test that client and server both notice handshake errors after data
	// has started flowing.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "EarlyData-Client-BadFinished-TLS13",
		config: Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
		},
		resumeConfig: &Config{
			MaxVersion:       VersionTLS13,
			MaxEarlyDataSize: 16384,
			Bugs: ProtocolBugs{
				BadFinished: true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-on-resume-expect-accept-early-data",
		},
		shouldFail:         true,
		expectedError:      ":DIGEST_CHECK_FAILED:",
		expectedLocalError: "remote error: error decrypting message",
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyData-Server-BadFinished-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
				BadFinished:             true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
		},
		shouldFail:         true,
		expectedError:      ":DIGEST_CHECK_FAILED:",
		expectedLocalError: "remote error: error decrypting message",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "Server-NonEmptyEndOfEarlyData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		resumeConfig: &Config{
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				NonEmptyEndOfEarlyData:  true,
			},
		},
		resumeSession: true,
		flags: []string{
			"-enable-early-data",
			"-expect-ticket-supports-early-data",
			"-on-resume-expect-accept-early-data",
		},
		shouldFail:    true,
		expectedError: ":DECODE_ERROR:",
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ServerSkipCertificateVerify-TLS13",
		config: Config{
			MinVersion:   VersionTLS13,
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaChainCertificate},
			Bugs: ProtocolBugs{
				SkipCertificateVerify: true,
			},
		},
		expectPeerCertificate: &rsaChainCertificate,
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaChainKeyFile),
			"-require-any-client-certificate",
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_MESSAGE:",
		expectedLocalError: "remote error: unexpected message",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ClientSkipCertificateVerify-TLS13",
		config: Config{
			MinVersion:   VersionTLS13,
			MaxVersion:   VersionTLS13,
			Certificates: []Certificate{rsaChainCertificate},
			Bugs: ProtocolBugs{
				SkipCertificateVerify: true,
			},
		},
		expectPeerCertificate: &rsaChainCertificate,
		flags: []string{
			"-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
			"-key-file", path.Join(*resourceDir, rsaChainKeyFile),
		},
		shouldFail:         true,
		expectedError:      ":UNEXPECTED_MESSAGE:",
		expectedLocalError: "remote error: unexpected message",
	})

	// If the client or server has 0-RTT enabled but disabled TLS 1.3, it should
	// report a reason of protocol_version.
	testCases = append(testCases, testCase{
		testType:        clientTest,
		name:            "EarlyDataEnabled-Client-MaxTLS12",
		expectedVersion: VersionTLS12,
		flags: []string{
			"-enable-early-data",
			"-max-version", strconv.Itoa(VersionTLS12),
			"-expect-early-data-reason", "protocol_version",
		},
	})
	testCases = append(testCases, testCase{
		testType:        serverTest,
		name:            "EarlyDataEnabled-Server-MaxTLS12",
		expectedVersion: VersionTLS12,
		flags: []string{
			"-enable-early-data",
			"-max-version", strconv.Itoa(VersionTLS12),
			"-expect-early-data-reason", "protocol_version",
		},
	})

	// The server additionally reports protocol_version if it enabled TLS 1.3,
	// but the peer negotiated TLS 1.2. (The corresponding situation does not
	// exist on the client because negotiating TLS 1.2 with a 0-RTT ClientHello
	// is a fatal error.)
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "EarlyDataEnabled-Server-NegotiateTLS12",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		expectedVersion: VersionTLS12,
		flags: []string{
			"-enable-early-data",
			"-expect-early-data-reason", "protocol_version",
		},
	})
}

func addTLS13CipherPreferenceTests() {
	// Test that client preference is honored if the shim has AES hardware
	// and ChaCha20-Poly1305 is preferred otherwise.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-Server-ChaCha20-AES",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_CHACHA20_POLY1305_SHA256,
				TLS_AES_128_GCM_SHA256,
			},
			CurvePreferences: []CurveID{CurveX25519},
		},
		flags: []string{
			"-expect-cipher-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-Server-AES-ChaCha20",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_CHACHA20_POLY1305_SHA256,
			},
			CurvePreferences: []CurveID{CurveX25519},
		},
		flags: []string{
			"-expect-cipher-aes", strconv.Itoa(int(TLS_AES_128_GCM_SHA256)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})

	// Test that the client orders ChaCha20-Poly1305 and AES-GCM based on
	// whether it has AES hardware.
	testCases = append(testCases, testCase{
		name: "TLS13-CipherPreference-Client",
		config: Config{
			MaxVersion: VersionTLS13,
			// Use the client cipher order. (This is the default but
			// is listed to be explicit.)
			PreferServerCipherSuites: false,
		},
		flags: []string{
			"-expect-cipher-aes", strconv.Itoa(int(TLS_AES_128_GCM_SHA256)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})

	// CECPQ2 prefers 256-bit ciphers but will use AES-128 if there's nothing else.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2-AES128Only",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
		},
	})

	// CECPQ2b prefers 256-bit ciphers but will use AES-128 if there's nothing else.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2b-AES128Only",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
		},
	})

	// When a 256-bit cipher is offered, even if not in first place, it should be
	// picked.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2-AES256Preferred",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
		},
		expectedCipher: TLS_AES_256_GCM_SHA384,
	})
	// ... but when CECPQ2 isn't being used, the client's preference controls.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2-AES128PreferredOtherwise",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
		expectedCipher: TLS_AES_128_GCM_SHA256,
	})

	// When a 256-bit cipher is offered, even if not in first place, it should be
	// picked.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2b-AES256Preferred",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
		},
		expectedCipher: TLS_AES_256_GCM_SHA384,
	})
	// ... but when CECPQ2b isn't being used, the client's preference controls.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2b-AES128PreferredOtherwise",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveX25519)),
		},
		expectedCipher: TLS_AES_128_GCM_SHA256,
	})

	// Test that CECPQ2 continues to honor AES vs ChaCha20 logic.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2-AES128-ChaCha20-AES256",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_CHACHA20_POLY1305_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-expect-cipher-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2-AES128-AES256-ChaCha20",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
				TLS_CHACHA20_POLY1305_SHA256,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2)),
			"-expect-cipher-aes", strconv.Itoa(int(TLS_AES_256_GCM_SHA384)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})

	// Test that CECPQ2b continues to honor AES vs ChaCha20 logic.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2b-AES128-ChaCha20-AES256",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_CHACHA20_POLY1305_SHA256,
				TLS_AES_256_GCM_SHA384,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-expect-cipher-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "TLS13-CipherPreference-CECPQ2b-AES128-AES256-ChaCha20",
		config: Config{
			MaxVersion: VersionTLS13,
			CipherSuites: []uint16{
				TLS_AES_128_GCM_SHA256,
				TLS_AES_256_GCM_SHA384,
				TLS_CHACHA20_POLY1305_SHA256,
			},
		},
		flags: []string{
			"-curves", strconv.Itoa(int(CurveCECPQ2b)),
			"-expect-cipher-aes", strconv.Itoa(int(TLS_AES_256_GCM_SHA384)),
			"-expect-cipher-no-aes", strconv.Itoa(int(TLS_CHACHA20_POLY1305_SHA256)),
		},
	})
}

func addPeekTests() {
	// Test SSL_peek works, including on empty records.
	testCases = append(testCases, testCase{
		name:             "Peek-Basic",
		sendEmptyRecords: 1,
		flags:            []string{"-peek-then-read"},
	})

	// Test SSL_peek can drive the initial handshake.
	testCases = append(testCases, testCase{
		name: "Peek-ImplicitHandshake",
		flags: []string{
			"-peek-then-read",
			"-implicit-handshake",
		},
	})

	// Test SSL_peek can discover and drive a renegotiation.
	testCases = append(testCases, testCase{
		name: "Peek-Renegotiate",
		config: Config{
			MaxVersion: VersionTLS12,
		},
		renegotiate: 1,
		flags: []string{
			"-peek-then-read",
			"-renegotiate-freely",
			"-expect-total-renegotiations", "1",
		},
	})

	// Test SSL_peek can discover a close_notify.
	testCases = append(testCases, testCase{
		name: "Peek-Shutdown",
		config: Config{
			Bugs: ProtocolBugs{
				ExpectCloseNotify: true,
			},
		},
		flags: []string{
			"-peek-then-read",
			"-check-close-notify",
		},
	})

	// Test SSL_peek can discover an alert.
	testCases = append(testCases, testCase{
		name: "Peek-Alert",
		config: Config{
			Bugs: ProtocolBugs{
				SendSpuriousAlert: alertRecordOverflow,
			},
		},
		flags:         []string{"-peek-then-read"},
		shouldFail:    true,
		expectedError: ":TLSV1_ALERT_RECORD_OVERFLOW:",
	})

	// Test SSL_peek can handle KeyUpdate.
	testCases = append(testCases, testCase{
		name: "Peek-KeyUpdate",
		config: Config{
			MaxVersion: VersionTLS13,
		},
		sendKeyUpdates:   1,
		keyUpdateRequest: keyUpdateNotRequested,
		flags:            []string{"-peek-then-read"},
	})
}

func addRecordVersionTests() {
	for _, ver := range tlsVersions {
		// Test that the record version is enforced.
		testCases = append(testCases, testCase{
			name: "CheckRecordVersion-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendRecordVersion: 0x03ff,
				},
			},
			shouldFail:    true,
			expectedError: ":WRONG_VERSION_NUMBER:",
		})

		// Test that the ClientHello may use any record version, for
		// compatibility reasons.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "LooseInitialRecordVersion-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendInitialRecordVersion: 0x03ff,
				},
			},
		})

		// Test that garbage ClientHello record versions are rejected.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "GarbageInitialRecordVersion-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					SendInitialRecordVersion: 0xffff,
				},
			},
			shouldFail:    true,
			expectedError: ":WRONG_VERSION_NUMBER:",
		})
	}
}

func addCertificateTests() {
	for _, ver := range tlsVersions {
		// Test that a certificate chain with intermediate may be sent
		// and received as both client and server.
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "SendReceiveIntermediate-Client-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaChainCertificate},
				ClientAuth:   RequireAnyClientCert,
			},
			expectPeerCertificate: &rsaChainCertificate,
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaChainKeyFile),
				"-expect-peer-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
			},
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "SendReceiveIntermediate-Server-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaChainCertificate},
			},
			expectPeerCertificate: &rsaChainCertificate,
			flags: []string{
				"-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
				"-key-file", path.Join(*resourceDir, rsaChainKeyFile),
				"-require-any-client-certificate",
				"-expect-peer-cert-file", path.Join(*resourceDir, rsaChainCertificateFile),
			},
		})

		// Test that garbage leaf certificates are properly rejected.
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "GarbageCertificate-Client-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{garbageCertificate},
			},
			shouldFail:         true,
			expectedError:      ":CANNOT_PARSE_LEAF_CERT:",
			expectedLocalError: "remote error: error decoding message",
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "GarbageCertificate-Server-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{garbageCertificate},
			},
			flags:              []string{"-require-any-client-certificate"},
			shouldFail:         true,
			expectedError:      ":CANNOT_PARSE_LEAF_CERT:",
			expectedLocalError: "remote error: error decoding message",
		})
	}
}

func addRetainOnlySHA256ClientCertTests() {
	for _, ver := range tlsVersions {
		// Test that enabling
		// SSL_CTX_set_retain_only_sha256_of_client_certs without
		// actually requesting a client certificate is a no-op.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "RetainOnlySHA256-NoCert-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
			},
			flags: []string{
				"-on-initial-retain-only-sha256-client-cert",
				"-on-resume-retain-only-sha256-client-cert",
			},
			resumeSession: true,
		})

		// Test that when retaining only a SHA-256 certificate is
		// enabled, the hash appears as expected.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "RetainOnlySHA256-Cert-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-verify-peer",
				"-on-initial-retain-only-sha256-client-cert",
				"-on-resume-retain-only-sha256-client-cert",
				"-on-initial-expect-sha256-client-cert",
				"-on-resume-expect-sha256-client-cert",
			},
			resumeSession: true,
		})

		// Test that when the config changes from on to off, a
		// resumption is rejected because the server now wants the full
		// certificate chain.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "RetainOnlySHA256-OnOff-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-verify-peer",
				"-on-initial-retain-only-sha256-client-cert",
				"-on-initial-expect-sha256-client-cert",
			},
			resumeSession:        true,
			expectResumeRejected: true,
		})

		// Test that when the config changes from off to on, a
		// resumption is rejected because the server now wants just the
		// hash.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "RetainOnlySHA256-OffOn-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{rsaCertificate},
			},
			flags: []string{
				"-verify-peer",
				"-on-resume-retain-only-sha256-client-cert",
				"-on-resume-expect-sha256-client-cert",
			},
			resumeSession:        true,
			expectResumeRejected: true,
		})
	}
}

func addECDSAKeyUsageTests() {
	p256 := elliptic.P256()
	priv, err := ecdsa.GenerateKey(p256, rand.Reader)
	if err != nil {
		panic(err)
	}

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if err != nil {
		panic(err)
	}

	template := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"Acme Co"},
		},
		NotBefore: time.Now(),
		NotAfter:  time.Now(),

		// An ECC certificate with only the keyAgreement key usgae may
		// be used with ECDH, but not ECDSA.
		KeyUsage:              x509.KeyUsageKeyAgreement,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}

	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		panic(err)
	}

	cert := Certificate{
		Certificate: [][]byte{derBytes},
		PrivateKey:  priv,
	}

	for _, ver := range tlsVersions {
		if ver.version < VersionTLS12 {
			continue
		}

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "ECDSAKeyUsage-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{cert},
			},
			shouldFail:    true,
			expectedError: ":KEY_USAGE_BIT_INCORRECT:",
		})
	}
}

func addRSAKeyUsageTests() {
	priv := rsaCertificate.PrivateKey.(*rsa.PrivateKey)

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if err != nil {
		panic(err)
	}

	dsTemplate := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"Acme Co"},
		},
		NotBefore: time.Now(),
		NotAfter:  time.Now(),

		KeyUsage:              x509.KeyUsageDigitalSignature,
		BasicConstraintsValid: true,
	}

	encTemplate := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"Acme Co"},
		},
		NotBefore: time.Now(),
		NotAfter:  time.Now(),

		KeyUsage:              x509.KeyUsageKeyEncipherment,
		BasicConstraintsValid: true,
	}

	dsDerBytes, err := x509.CreateCertificate(rand.Reader, &dsTemplate, &dsTemplate, &priv.PublicKey, priv)
	if err != nil {
		panic(err)
	}

	encDerBytes, err := x509.CreateCertificate(rand.Reader, &encTemplate, &encTemplate, &priv.PublicKey, priv)
	if err != nil {
		panic(err)
	}

	dsCert := Certificate{
		Certificate: [][]byte{dsDerBytes},
		PrivateKey:  priv,
	}

	encCert := Certificate{
		Certificate: [][]byte{encDerBytes},
		PrivateKey:  priv,
	}

	dsSuites := []uint16{
		TLS_AES_128_GCM_SHA256,
		TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
		TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
	}
	encSuites := []uint16{
		TLS_RSA_WITH_AES_128_GCM_SHA256,
		TLS_RSA_WITH_AES_128_CBC_SHA,
	}

	for _, ver := range tlsVersions {
		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "RSAKeyUsage-WantSignature-GotEncipherment-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{encCert},
				CipherSuites: dsSuites,
			},
			shouldFail:    true,
			expectedError: ":KEY_USAGE_BIT_INCORRECT:",
			flags: []string{
				"-enforce-rsa-key-usage",
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "RSAKeyUsage-WantSignature-GotSignature-" + ver.name,
			config: Config{
				MinVersion:   ver.version,
				MaxVersion:   ver.version,
				Certificates: []Certificate{dsCert},
				CipherSuites: dsSuites,
			},
			flags: []string{
				"-enforce-rsa-key-usage",
			},
		})

		// TLS 1.3 removes the encipherment suites.
		if ver.version < VersionTLS13 {
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "RSAKeyUsage-WantEncipherment-GotEncipherment" + ver.name,
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{encCert},
					CipherSuites: encSuites,
				},
				flags: []string{
					"-enforce-rsa-key-usage",
				},
			})

			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "RSAKeyUsage-WantEncipherment-GotSignature-" + ver.name,
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{dsCert},
					CipherSuites: encSuites,
				},
				shouldFail:    true,
				expectedError: ":KEY_USAGE_BIT_INCORRECT:",
				flags: []string{
					"-enforce-rsa-key-usage",
				},
			})

			// In 1.2 and below, we should not enforce without the enforce-rsa-key-usage flag.
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "RSAKeyUsage-WantSignature-GotEncipherment-Unenforced" + ver.name,
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{dsCert},
					CipherSuites: encSuites,
				},
			})

			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "RSAKeyUsage-WantEncipherment-GotSignature-Unenforced" + ver.name,
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{encCert},
					CipherSuites: dsSuites,
				},
			})

		}

		if ver.version >= VersionTLS13 {
			// In 1.3 and above, we enforce keyUsage even without the flag.
			testCases = append(testCases, testCase{
				testType: clientTest,
				name:     "RSAKeyUsage-WantSignature-GotEncipherment-Enforced" + ver.name,
				config: Config{
					MinVersion:   ver.version,
					MaxVersion:   ver.version,
					Certificates: []Certificate{encCert},
					CipherSuites: dsSuites,
				},
				shouldFail:    true,
				expectedError: ":KEY_USAGE_BIT_INCORRECT:",
			})

		}
	}
}

func addExtraHandshakeTests() {
	// An extra SSL_do_handshake is normally a no-op. These tests use -async
	// to ensure there is no transport I/O.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ExtraHandshake-Client-TLS12",
		config: Config{
			MinVersion: VersionTLS12,
			MaxVersion: VersionTLS12,
		},
		flags: []string{
			"-async",
			"-no-op-extra-handshake",
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ExtraHandshake-Server-TLS12",
		config: Config{
			MinVersion: VersionTLS12,
			MaxVersion: VersionTLS12,
		},
		flags: []string{
			"-async",
			"-no-op-extra-handshake",
		},
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ExtraHandshake-Client-TLS13",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
		},
		flags: []string{
			"-async",
			"-no-op-extra-handshake",
		},
	})
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ExtraHandshake-Server-TLS13",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
		},
		flags: []string{
			"-async",
			"-no-op-extra-handshake",
		},
	})

	// An extra SSL_do_handshake is a no-op in server 0-RTT.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ExtraHandshake-Server-EarlyData-TLS13",
		config: Config{
			MaxVersion: VersionTLS13,
			MinVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				SendEarlyData:           [][]byte{{1, 2, 3, 4}},
				ExpectEarlyDataAccepted: true,
				ExpectHalfRTTData:       [][]byte{{254, 253, 252, 251}},
			},
		},
		messageCount:  2,
		resumeSession: true,
		flags: []string{
			"-async",
			"-enable-early-data",
			"-on-resume-expect-accept-early-data",
			"-no-op-extra-handshake",
		},
	})

	// An extra SSL_do_handshake drives the handshake to completion in False
	// Start. We test this by handshaking twice and asserting the False
	// Start does not appear to happen. See AlertBeforeFalseStartTest for
	// how the test works.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "ExtraHandshake-FalseStart",
		config: Config{
			MaxVersion:   VersionTLS12,
			CipherSuites: []uint16{TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256},
			NextProtos:   []string{"foo"},
			Bugs: ProtocolBugs{
				ExpectFalseStart:          true,
				AlertBeforeFalseStartTest: alertAccessDenied,
			},
		},
		flags: []string{
			"-handshake-twice",
			"-false-start",
			"-advertise-alpn", "\x03foo",
			"-expect-alpn", "foo",
		},
		shimWritesFirst:    true,
		shouldFail:         true,
		expectedError:      ":TLSV1_ALERT_ACCESS_DENIED:",
		expectedLocalError: "tls: peer did not false start: EOF",
	})
}

// Test that omitted and empty extensions blocks are tolerated.
func addOmitExtensionsTests() {
	// Check the ExpectOmitExtensions setting works.
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "ExpectOmitExtensions",
		config: Config{
			MinVersion: VersionTLS12,
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				ExpectOmitExtensions: true,
			},
		},
		shouldFail:         true,
		expectedLocalError: "tls: ServerHello did not omit extensions",
	})

	for _, ver := range tlsVersions {
		if ver.version > VersionTLS12 {
			continue
		}

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "OmitExtensions-ClientHello-" + ver.name,
			config: Config{
				MinVersion:             ver.version,
				MaxVersion:             ver.version,
				SessionTicketsDisabled: true,
				Bugs: ProtocolBugs{
					OmitExtensions: true,
					// With no client extensions, the ServerHello must not have
					// extensions. It should then omit the extensions field.
					ExpectOmitExtensions: true,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "EmptyExtensions-ClientHello-" + ver.name,
			config: Config{
				MinVersion:             ver.version,
				MaxVersion:             ver.version,
				SessionTicketsDisabled: true,
				Bugs: ProtocolBugs{
					EmptyExtensions: true,
					// With no client extensions, the ServerHello must not have
					// extensions. It should then omit the extensions field.
					ExpectOmitExtensions: true,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "OmitExtensions-ServerHello-" + ver.name,
			config: Config{
				MinVersion:             ver.version,
				MaxVersion:             ver.version,
				SessionTicketsDisabled: true,
				Bugs: ProtocolBugs{
					OmitExtensions: true,
					// Disable all ServerHello extensions so
					// OmitExtensions works.
					NoExtendedMasterSecret:        true,
					NoRenegotiationInfo:           true,
					NoOCSPStapling:                true,
					NoSignedCertificateTimestamps: true,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "EmptyExtensions-ServerHello-" + ver.name,
			config: Config{
				MinVersion:             ver.version,
				MaxVersion:             ver.version,
				SessionTicketsDisabled: true,
				Bugs: ProtocolBugs{
					EmptyExtensions: true,
					// Disable all ServerHello extensions so
					// EmptyExtensions works.
					NoExtendedMasterSecret:        true,
					NoRenegotiationInfo:           true,
					NoOCSPStapling:                true,
					NoSignedCertificateTimestamps: true,
				},
			},
		})
	}
}

func addCertCompressionTests() {
	// shrinkingPrefix is the first two bytes of a Certificate message.
	shrinkingPrefix := []byte{0, 0}
	// expandingPrefix is just some arbitrary byte string. This has to match the
	// value in the shim.
	expandingPrefix := []byte{1, 2, 3, 4}

	shrinking := CertCompressionAlg{
		Compress: func(uncompressed []byte) []byte {
			if !bytes.HasPrefix(uncompressed, shrinkingPrefix) {
				panic(fmt.Sprintf("cannot compress certificate message %x", uncompressed))
			}
			return uncompressed[len(shrinkingPrefix):]
		},
		Decompress: func(out []byte, compressed []byte) bool {
			if len(out) != len(shrinkingPrefix)+len(compressed) {
				return false
			}

			copy(out, shrinkingPrefix)
			copy(out[len(shrinkingPrefix):], compressed)
			return true
		},
	}

	expanding := CertCompressionAlg{
		Compress: func(uncompressed []byte) []byte {
			ret := make([]byte, 0, len(expandingPrefix)+len(uncompressed))
			ret = append(ret, expandingPrefix...)
			return append(ret, uncompressed...)
		},
		Decompress: func(out []byte, compressed []byte) bool {
			if !bytes.HasPrefix(compressed, expandingPrefix) {
				return false
			}
			copy(out, compressed[len(expandingPrefix):])
			return true
		},
	}

	const (
		shrinkingAlgId = 0xff01
		expandingAlgId = 0xff02
	)

	for _, ver := range tlsVersions {
		if ver.version < VersionTLS12 {
			continue
		}

		// Duplicate compression algorithms is an error, even if nothing is
		// configured.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "DuplicateCertCompressionExt-" + ver.name,
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					DuplicateCompressedCertAlgs: true,
				},
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})

		// With compression algorithms configured, an duplicate values should still
		// be an error.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "DuplicateCertCompressionExt2-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				Bugs: ProtocolBugs{
					DuplicateCompressedCertAlgs: true,
				},
			},
			shouldFail:    true,
			expectedError: ":ERROR_PARSING_EXTENSION:",
		})

		if ver.version < VersionTLS13 {
			testCases = append(testCases, testCase{
				testType: serverTest,
				name:     "CertCompressionIgnoredBefore13-" + ver.name,
				flags:    []string{"-install-cert-compression-algs"},
				config: Config{
					MinVersion:          ver.version,
					MaxVersion:          ver.version,
					CertCompressionAlgs: map[uint16]CertCompressionAlg{expandingAlgId: expanding},
				},
			})

			continue
		}

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "CertCompressionExpands-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{expandingAlgId: expanding},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert: expandingAlgId,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "CertCompressionShrinks-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion:          ver.version,
				MaxVersion:          ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{shrinkingAlgId: shrinking},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert: shrinkingAlgId,
				},
			},
		})

		// With both algorithms configured, the server should pick its most
		// preferable. (Which is expandingAlgId.)
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     "CertCompressionPriority-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					shrinkingAlgId: shrinking,
					expandingAlgId: expanding,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert: expandingAlgId,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "CertCompressionExpandsClient-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					expandingAlgId: expanding,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert: expandingAlgId,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "CertCompressionShrinksClient-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					shrinkingAlgId: shrinking,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert: shrinkingAlgId,
				},
			},
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "CertCompressionBadAlgIdClient-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					shrinkingAlgId: shrinking,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert:   shrinkingAlgId,
					SendCertCompressionAlgId: 1234,
				},
			},
			shouldFail:    true,
			expectedError: ":UNKNOWN_CERT_COMPRESSION_ALG:",
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "CertCompressionTooSmallClient-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					shrinkingAlgId: shrinking,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert:     shrinkingAlgId,
					SendCertUncompressedLength: 12,
				},
			},
			shouldFail:    true,
			expectedError: ":CERT_DECOMPRESSION_FAILED:",
		})

		testCases = append(testCases, testCase{
			testType: clientTest,
			name:     "CertCompressionTooLargeClient-" + ver.name,
			flags:    []string{"-install-cert-compression-algs"},
			config: Config{
				MinVersion: ver.version,
				MaxVersion: ver.version,
				CertCompressionAlgs: map[uint16]CertCompressionAlg{
					shrinkingAlgId: shrinking,
				},
				Bugs: ProtocolBugs{
					ExpectedCompressedCert:     shrinkingAlgId,
					SendCertUncompressedLength: 1 << 20,
				},
			},
			shouldFail:    true,
			expectedError: ":UNCOMPRESSED_CERT_TOO_LARGE:",
		})
	}
}

func addJDK11WorkaroundTests() {
	// Test the client treats the JDK 11 downgrade random like the usual one.
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Client-RejectJDK11DowngradeRandom",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendJDK11DowngradeRandom: true,
			},
		},
		shouldFail:         true,
		expectedError:      ":TLS13_DOWNGRADE:",
		expectedLocalError: "remote error: illegal parameter",
	})
	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "Client-AcceptJDK11DowngradeRandom",
		config: Config{
			MaxVersion: VersionTLS12,
			Bugs: ProtocolBugs{
				SendJDK11DowngradeRandom: true,
			},
		},
		flags: []string{"-max-version", strconv.Itoa(VersionTLS12)},
	})

	var clientHelloTests = []struct {
		clientHello []byte
		isJDK11     bool
	}{
		{
			// A default JDK 11 ClientHello.
			decodeHexOrPanic("010001a9030336a379aa355a22a064b4402760efae1c73977b0b4c975efc7654c35677723dde201fe3f8a2bca60418a68f72463ea19f3c241e7cbfceb347e451a62bd2417d8981005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000106000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b0009080304030303020301002d000201010033004700450017004104721f007464cb08a0f36e093ad178eb78d6968df20077b2dd882694a85dc4c9884caf5092db41f16cc3f8d41f59426992fa5e32cfb9ad08deee752cdd95b1a6b5"),
			true,
		},
		{
			// The above with supported_versions and
			// psk_key_exchange_modes in the wrong order.
			decodeHexOrPanic("010001a9030336a379aa355a22a064b4402760efae1c73977b0b4c975efc7654c35677723dde201fe3f8a2bca60418a68f72463ea19f3c241e7cbfceb347e451a62bd2417d8981005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000106000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002d00020101002b00090803040303030203010033004700450017004104721f007464cb08a0f36e093ad178eb78d6968df20077b2dd882694a85dc4c9884caf5092db41f16cc3f8d41f59426992fa5e32cfb9ad08deee752cdd95b1a6b5"),
			false,
		},
		{
			// The above with a padding extension added at the end.
			decodeHexOrPanic("010001b4030336a379aa355a22a064b4402760efae1c73977b0b4c975efc7654c35677723dde201fe3f8a2bca60418a68f72463ea19f3c241e7cbfceb347e451a62bd2417d8981005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000111000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b0009080304030303020301002d000201010033004700450017004104721f007464cb08a0f36e093ad178eb78d6968df20077b2dd882694a85dc4c9884caf5092db41f16cc3f8d41f59426992fa5e32cfb9ad08deee752cdd95b1a6b50015000700000000000000"),
			false,
		},
		{
			// A JDK 11 ClientHello offering a TLS 1.3 PSK.
			decodeHexOrPanic("0100024c0303a8d71b20f060545a398226e807d21371a7a02b7ca2f96f476c2dea7e5860c5a400005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff010001c9000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b0009080304030303020301002d000201010033004700450017004104aaec585ea9e121b24710a23560571322b2cf8ab8cd14e5762ef0486d8a6d0ecd721d8f2abda2eb8ed5ab7195505660450f49bba94bbf0c3f0070a531d9a1be4f002900cb00a600a0e6f7586d9a2bf64a54c1adf55a2f76657047e8e88e26629e2e7b9d630941e06fd87792770f6834e159a70b252157a9b4b082183f24629c8ff5049088b07ce37c49de8cf752a2ed7a545aff63bdc7a1b18e1bc201f23f159ee75d4987a04e00f840824f764691ab83a20e3032646e793065874cdb46138a52f50ed71406f399f96f9309eba4e5b1966148c22a63dc4aa1364269dd41dd5cc0e848d07af0095622c52cfcfc00212009cc315259e2328d65ad17a3de7c182c7874140a9356fecdd4614657806cd659"),
			true,
		},
		{
			// A JDK 11 ClientHello offering a TLS 1.2 session.
			decodeHexOrPanic("010001a903038cdec49f4836d064a75046c93f22d0b9c2cf4900917332e6f0e1f41d692d3146201a3e99047492285ec65ab4e0eeee59f8f9d1eb7687398887bcd7b81353e93923005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000106000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b0009080304030303020301002d0002010100330047004500170041041c83c42fcd8fc06265b9f6e4f076f7e7ee17ace915c587845c0e1bc8cd177f904befeb611b682cae4702509a5f5d0c7162a282b8152d843169b91136e7c6f3e7"),
			true,
		},
		{
			// A JDK 11 ClientHello with EMS disabled.
			decodeHexOrPanic("010001a50303323a857c324a9ef57d6e2544d129073830385cb1dc75ea79f6a2ec8ae09d2e7320f85fdd081678874c67ebab235e6d6a81d947f690bc0af9be4d39854ed67d9ef9005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000102000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b040105010601040203030301030202030201020200110009000702000400000000002b0009080304030303020301002d0002010100330047004500170041049c904c4850b495d75522f955d79e9cabea065c90279d6037a101a4c4ee712afc93ad0df5d12d287d53e458c7075d9a3ce3969c939bb62222bda779cecf54a603"),
			true,
		},
		{
			// A JDK 11 ClientHello with OCSP stapling disabled.
			decodeHexOrPanic("0100019303038a50481dc85ee4f6581670821c50f2b3d34ac3251dc6e9b751bfd2521ab47ab02069a963c5486034c37ae0577ddb4c2db28cab592380ef8e4599d1305148712112005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff010000f0000000080006000003736e69000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b040105010601040203030301030202030201020200170000002b0009080304030303020301002d00020101003300470045001700410438a97824f842c549e3c339322d8b2dbaa85d10bd7bca9c969376cb0c60b1e929eb4d13db38dcb0082ad8c637b24f55466a9acbb0b63634c1f431ec8342cf720d"),
			true,
		},
		{
			// A JDK 11 ClientHello configured with a smaller set of
			// ciphers.
			decodeHexOrPanic("0100015603036f5706bbdf1dcae671cd9be043603f5ed20f8fc195b426504cafb4f353edb0012007aabd35e588bc2504a72eda42cbbf89d69cfc0a6a1d77db0d757606f1f4811800061301c02bc02f01000107000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b00050403040303002d000201010033004700450017004104d283f3d5a90259b61d43ea1511211f568ce5d18457326b717e1f9d6b7d1476f2b51cdc3c798d3bdfba5095edff0ffd0540f6bc0c324bd9744f3b3f24317496e3ff01000100"),
			true,
		},
		{
			// The above with TLS_CHACHA20_POLY1305_SHA256 added,
			// which JDK 11 does not support.
			decodeHexOrPanic("0100015803036f5706bbdf1dcae671cd9be043603f5ed20f8fc195b426504cafb4f353edb0012007aabd35e588bc2504a72eda42cbbf89d69cfc0a6a1d77db0d757606f1f48118000813011303c02bc02f01000107000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b00050403040303002d000201010033004700450017004104d283f3d5a90259b61d43ea1511211f568ce5d18457326b717e1f9d6b7d1476f2b51cdc3c798d3bdfba5095edff0ffd0540f6bc0c324bd9744f3b3f24317496e3ff01000100"),
			false,
		},
		{
			// The above with X25519 added, which JDK 11 does not
			// support.
			decodeHexOrPanic("0100015803036f5706bbdf1dcae671cd9be043603f5ed20f8fc195b426504cafb4f353edb0012007aabd35e588bc2504a72eda42cbbf89d69cfc0a6a1d77db0d757606f1f4811800061301c02bc02f01000109000000080006000003736e69000500050100000000000a00220020001d0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020011000900070200040000000000170000002b00050403040303002d000201010033004700450017004104d283f3d5a90259b61d43ea1511211f568ce5d18457326b717e1f9d6b7d1476f2b51cdc3c798d3bdfba5095edff0ffd0540f6bc0c324bd9744f3b3f24317496e3ff01000100"),
			false,
		},
		{
			// A JDK 11 ClientHello with ALPN protocols configured.
			decodeHexOrPanic("010001bb0303c0e0ea707b00c5311eb09cabd58626692cebfaefaef7265637e4550811dae16220da86d6eea04e214e873675223f08a6926bcf79f16d866280bdbab85e9e09c3ff005a13011302c02cc02bc030009dc02ec032009f00a3c02f009cc02dc031009e00a2c024c028003dc026c02a006b006ac00ac0140035c005c00f00390038c023c027003cc025c02900670040c009c013002fc004c00e0033003200ff01000118000000080006000003736e69000500050100000000000a0020001e0017001800190009000a000b000c000d000e001601000101010201030104000b00020100000d002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020032002800260403050306030804080508060809080a080b04010501060104020303030103020203020102020010000e000c02683208687474702f312e310011000900070200040000000000170000002b0009080304030303020301002d00020101003300470045001700410416def07c1d66ddde5fc9dcc328c8e77022d321c590c0d30cb41d515b38dca34540819a216c6c053bd47b9068f4f6b960f03647de4e36e8b7ffeea78f7252e3d9"),
			true,
		},
	}
	for i, t := range clientHelloTests {
		expectedVersion := uint16(VersionTLS13)
		if t.isJDK11 {
			expectedVersion = VersionTLS12
		}

		// In each of these tests, we set DefaultCurves to P-256 to
		// match the test inputs. SendClientHelloWithFixes requires the
		// key_shares extension to match in type.

		// With the workaround enabled, we should negotiate TLS 1.2 on
		// JDK 11 ClientHellos.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     fmt.Sprintf("Server-JDK11-%d", i),
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: []CurveID{CurveP256},
				Bugs: ProtocolBugs{
					SendClientHelloWithFixes:   t.clientHello,
					ExpectJDK11DowngradeRandom: t.isJDK11,
				},
			},
			expectedVersion: expectedVersion,
			flags:           []string{"-jdk11-workaround"},
		})

		// With the workaround disabled, we always negotiate TLS 1.3.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     fmt.Sprintf("Server-JDK11-NoWorkaround-%d", i),
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: []CurveID{CurveP256},
				Bugs: ProtocolBugs{
					SendClientHelloWithFixes:   t.clientHello,
					ExpectJDK11DowngradeRandom: false,
				},
			},
			expectedVersion: VersionTLS13,
		})

		// If the server does not support TLS 1.3, the workaround should
		// be a no-op. In particular, it should not send the downgrade
		// signal.
		testCases = append(testCases, testCase{
			testType: serverTest,
			name:     fmt.Sprintf("Server-JDK11-TLS12-%d", i),
			config: Config{
				MaxVersion:    VersionTLS13,
				DefaultCurves: []CurveID{CurveP256},
				Bugs: ProtocolBugs{
					SendClientHelloWithFixes:   t.clientHello,
					ExpectJDK11DowngradeRandom: false,
				},
			},
			expectedVersion: VersionTLS12,
			flags: []string{
				"-jdk11-workaround",
				"-max-version", strconv.Itoa(VersionTLS12),
			},
		})
	}
}

func addDelegatedCredentialTests() {
	certPath := path.Join(*resourceDir, rsaCertificateFile)
	pemBytes, err := ioutil.ReadFile(certPath)
	if err != nil {
		panic(err)
	}

	block, _ := pem.Decode(pemBytes)
	if block == nil {
		panic(fmt.Sprintf("no PEM block found in %q", certPath))
	}
	parentDER := block.Bytes

	rsaPriv, _, err := loadRSAPrivateKey(rsaKeyFile)
	if err != nil {
		panic(err)
	}

	ecdsaDC, ecdsaPKCS8, err := createDelegatedCredential(delegatedCredentialConfig{
		algo: signatureRSAPSSWithSHA256,
	}, parentDER, rsaPriv)
	if err != nil {
		panic(err)
	}
	ecdsaFlagValue := fmt.Sprintf("%x,%x", ecdsaDC, ecdsaPKCS8)

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "DelegatedCredentials-NoClientSupport",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				DisableDelegatedCredentials: true,
			},
		},
		flags: []string{
			"-delegated-credential", ecdsaFlagValue,
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "DelegatedCredentials-Basic",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectDelegatedCredentials: true,
			},
		},
		flags: []string{
			"-delegated-credential", ecdsaFlagValue,
			"-expect-delegated-credential-used",
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "DelegatedCredentials-SigAlgoMissing",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FailIfDelegatedCredentials: true,
			},
			// If the client doesn't support the delegated credential signature
			// algorithm then the handshake should complete without using delegated
			// credentials.
			VerifySignatureAlgorithms: []signatureAlgorithm{signatureRSAPSSWithSHA256},
		},
		flags: []string{
			"-delegated-credential", ecdsaFlagValue,
		},
	})

	// This flag value has mismatched public and private keys which should cause a
	// configuration error in the shim.
	_, badTLSVersionPKCS8, err := createDelegatedCredential(delegatedCredentialConfig{
		algo:       signatureRSAPSSWithSHA256,
		tlsVersion: 0x1234,
	}, parentDER, rsaPriv)
	if err != nil {
		panic(err)
	}
	mismatchFlagValue := fmt.Sprintf("%x,%x", ecdsaDC, badTLSVersionPKCS8)
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "DelegatedCredentials-KeyMismatch",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				FailIfDelegatedCredentials: true,
			},
		},
		flags: []string{
			"-delegated-credential", mismatchFlagValue,
		},
		shouldFail:    true,
		expectedError: ":KEY_VALUES_MISMATCH:",
	})
}

func addPQExperimentSignalTests() {
	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PQExperimentSignal-Server-NoEchoIfNotConfigured",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectPQExperimentSignal: false,
			},
			PQExperimentSignal: true,
		},
	})

	testCases = append(testCases, testCase{
		testType: serverTest,
		name:     "PQExperimentSignal-Server-Echo",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectPQExperimentSignal: true,
			},
			PQExperimentSignal: true,
		},
		flags: []string{
			"-enable-pq-experiment-signal",
			"-expect-pq-experiment-signal",
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "PQExperimentSignal-Client-NotDefault",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectPQExperimentSignal: false,
			},
			PQExperimentSignal: true,
		},
	})

	testCases = append(testCases, testCase{
		testType: clientTest,
		name:     "PQExperimentSignal-Client",
		config: Config{
			MinVersion: VersionTLS13,
			MaxVersion: VersionTLS13,
			Bugs: ProtocolBugs{
				ExpectPQExperimentSignal: true,
			},
		},
		flags: []string{
			"-enable-pq-experiment-signal",
			"-expect-pq-experiment-signal",
		},
	})
}

func worker(statusChan chan statusMsg, c chan *testCase, shimPath string, wg *sync.WaitGroup) {
	defer wg.Done()

	for test := range c {
		var err error

		if *mallocTest >= 0 {
			for mallocNumToFail := int64(*mallocTest); ; mallocNumToFail++ {
				statusChan <- statusMsg{test: test, started: true}
				if err = runTest(test, shimPath, mallocNumToFail); err != errMoreMallocs {
					if err != nil {
						fmt.Printf("\n\nmalloc test failed at %d: %s\n", mallocNumToFail, err)
					}
					break
				}
			}
		} else if *repeatUntilFailure {
			for err == nil {
				statusChan <- statusMsg{test: test, started: true}
				err = runTest(test, shimPath, -1)
			}
		} else {
			statusChan <- statusMsg{test: test, started: true}
			err = runTest(test, shimPath, -1)
		}
		statusChan <- statusMsg{test: test, err: err}
	}
}

type statusMsg struct {
	test    *testCase
	started bool
	err     error
}

func statusPrinter(doneChan chan *testresult.Results, statusChan chan statusMsg, total int) {
	var started, done, failed, unimplemented, lineLen int

	testOutput := testresult.NewResults()
	for msg := range statusChan {
		if !*pipe {
			// Erase the previous status line.
			var erase string
			for i := 0; i < lineLen; i++ {
				erase += "\b \b"
			}
			fmt.Print(erase)
		}

		if msg.started {
			started++
		} else {
			done++

			if msg.err != nil {
				if msg.err == errUnimplemented {
					if *pipe {
						// Print each test instead of a status line.
						fmt.Printf("UNIMPLEMENTED (%s)\n", msg.test.name)
					}
					unimplemented++
					if *allowUnimplemented {
						testOutput.AddSkip(msg.test.name)
					} else {
						testOutput.AddResult(msg.test.name, "SKIP")
					}
				} else {
					fmt.Printf("FAILED (%s)\n%s\n", msg.test.name, msg.err)
					failed++
					testOutput.AddResult(msg.test.name, "FAIL")
				}
			} else {
				if *pipe {
					// Print each test instead of a status line.
					fmt.Printf("PASSED (%s)\n", msg.test.name)
				}
				testOutput.AddResult(msg.test.name, "PASS")
			}
		}

		if !*pipe {
			// Print a new status line.
			line := fmt.Sprintf("%d/%d/%d/%d/%d", failed, unimplemented, done, started, total)
			lineLen = len(line)
			os.Stdout.WriteString(line)
		}
	}

	doneChan <- testOutput
}

func main() {
	flag.Parse()
	*resourceDir = path.Clean(*resourceDir)
	initCertificates()

	addBasicTests()
	addCipherSuiteTests()
	addBadECDSASignatureTests()
	addCBCPaddingTests()
	addCBCSplittingTests()
	addClientAuthTests()
	addDDoSCallbackTests()
	addVersionNegotiationTests()
	addMinimumVersionTests()
	addExtensionTests()
	addResumptionVersionTests()
	addExtendedMasterSecretTests()
	addRenegotiationTests()
	addDTLSReplayTests()
	addSignatureAlgorithmTests()
	addDTLSRetransmitTests()
	addExportKeyingMaterialTests()
	addExportTrafficSecretsTests()
	addTLSUniqueTests()
	addCustomExtensionTests()
	addRSAClientKeyExchangeTests()
	addCurveTests()
	addSessionTicketTests()
	addTLS13RecordTests()
	addAllStateMachineCoverageTests()
	addChangeCipherSpecTests()
	addWrongMessageTypeTests()
	addTrailingMessageDataTests()
	addTLS13HandshakeTests()
	addTLS13CipherPreferenceTests()
	addPeekTests()
	addRecordVersionTests()
	addCertificateTests()
	addRetainOnlySHA256ClientCertTests()
	addECDSAKeyUsageTests()
	addRSAKeyUsageTests()
	addExtraHandshakeTests()
	addOmitExtensionsTests()
	addCertCompressionTests()
	addJDK11WorkaroundTests()
	addDelegatedCredentialTests()
	addPQExperimentSignalTests()

	testCases = append(testCases, convertToSplitHandshakeTests(testCases)...)

	var wg sync.WaitGroup

	statusChan := make(chan statusMsg, *numWorkers)
	testChan := make(chan *testCase, *numWorkers)
	doneChan := make(chan *testresult.Results)

	if len(*shimConfigFile) != 0 {
		encoded, err := ioutil.ReadFile(*shimConfigFile)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Couldn't read config file %q: %s\n", *shimConfigFile, err)
			os.Exit(1)
		}

		if err := json.Unmarshal(encoded, &shimConfig); err != nil {
			fmt.Fprintf(os.Stderr, "Couldn't decode config file %q: %s\n", *shimConfigFile, err)
			os.Exit(1)
		}
	}

	go statusPrinter(doneChan, statusChan, len(testCases))

	for i := 0; i < *numWorkers; i++ {
		wg.Add(1)
		go worker(statusChan, testChan, *shimPath, &wg)
	}

	var foundTest bool
	for i := range testCases {
		matched := true
		if len(*testToRun) != 0 {
			var err error
			matched, err = filepath.Match(*testToRun, testCases[i].name)
			if err != nil {
				fmt.Fprintf(os.Stderr, "Error matching pattern: %s\n", err)
				os.Exit(1)
			}
		}

		if !*includeDisabled {
			for pattern := range shimConfig.DisabledTests {
				isDisabled, err := filepath.Match(pattern, testCases[i].name)
				if err != nil {
					fmt.Fprintf(os.Stderr, "Error matching pattern %q from config file: %s\n", pattern, err)
					os.Exit(1)
				}

				if isDisabled {
					matched = false
					break
				}
			}
		}

		if matched {
			foundTest = true
			testChan <- &testCases[i]

			// Only run one test if repeating until failure.
			if *repeatUntilFailure {
				break
			}
		}
	}

	if !foundTest {
		fmt.Fprintf(os.Stderr, "No tests run\n")
		os.Exit(1)
	}

	close(testChan)
	wg.Wait()
	close(statusChan)
	testOutput := <-doneChan

	fmt.Printf("\n")

	if *jsonOutput != "" {
		if err := testOutput.WriteToFile(*jsonOutput); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %s\n", err)
		}
	}

	if !testOutput.HasUnexpectedResults() {
		os.Exit(1)
	}
}

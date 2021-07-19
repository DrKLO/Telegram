// Copyright 2009 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"crypto"
	"crypto/hmac"
	"crypto/md5"
	"crypto/sha1"
	"crypto/sha256"
	"hash"
)

// Split a premaster secret in two as specified in RFC 4346, section 5.
func splitPreMasterSecret(secret []byte) (s1, s2 []byte) {
	s1 = secret[0 : (len(secret)+1)/2]
	s2 = secret[len(secret)/2:]
	return
}

// pHash implements the P_hash function, as defined in RFC 4346, section 5.
func pHash(result, secret, seed []byte, hash func() hash.Hash) {
	h := hmac.New(hash, secret)
	h.Write(seed)
	a := h.Sum(nil)

	j := 0
	for j < len(result) {
		h.Reset()
		h.Write(a)
		h.Write(seed)
		b := h.Sum(nil)
		todo := len(b)
		if j+todo > len(result) {
			todo = len(result) - j
		}
		copy(result[j:j+todo], b)
		j += todo

		h.Reset()
		h.Write(a)
		a = h.Sum(nil)
	}
}

// prf10 implements the TLS 1.0 pseudo-random function, as defined in RFC 2246, section 5.
func prf10(result, secret, label, seed []byte) {
	hashSHA1 := sha1.New
	hashMD5 := md5.New

	labelAndSeed := make([]byte, len(label)+len(seed))
	copy(labelAndSeed, label)
	copy(labelAndSeed[len(label):], seed)

	s1, s2 := splitPreMasterSecret(secret)
	pHash(result, s1, labelAndSeed, hashMD5)
	result2 := make([]byte, len(result))
	pHash(result2, s2, labelAndSeed, hashSHA1)

	for i, b := range result2 {
		result[i] ^= b
	}
}

// prf12 implements the TLS 1.2 pseudo-random function, as defined in RFC 5246, section 5.
func prf12(hashFunc func() hash.Hash) func(result, secret, label, seed []byte) {
	return func(result, secret, label, seed []byte) {
		labelAndSeed := make([]byte, len(label)+len(seed))
		copy(labelAndSeed, label)
		copy(labelAndSeed[len(label):], seed)

		pHash(result, secret, labelAndSeed, hashFunc)
	}
}

// prf30 implements the SSL 3.0 pseudo-random function, as defined in
// www.mozilla.org/projects/security/pki/nss/ssl/draft302.txt section 6.
func prf30(result, secret, label, seed []byte) {
	hashSHA1 := sha1.New()
	hashMD5 := md5.New()

	done := 0
	i := 0
	// RFC5246 section 6.3 says that the largest PRF output needed is 128
	// bytes. Since no more ciphersuites will be added to SSLv3, this will
	// remain true. Each iteration gives us 16 bytes so 10 iterations will
	// be sufficient.
	var b [11]byte
	for done < len(result) {
		for j := 0; j <= i; j++ {
			b[j] = 'A' + byte(i)
		}

		hashSHA1.Reset()
		hashSHA1.Write(b[:i+1])
		hashSHA1.Write(secret)
		hashSHA1.Write(seed)
		digest := hashSHA1.Sum(nil)

		hashMD5.Reset()
		hashMD5.Write(secret)
		hashMD5.Write(digest)

		done += copy(result[done:], hashMD5.Sum(nil))
		i++
	}
}

const (
	tlsRandomLength      = 32 // Length of a random nonce in TLS 1.1.
	masterSecretLength   = 48 // Length of a master secret in TLS 1.1.
	finishedVerifyLength = 12 // Length of verify_data in a Finished message.
)

var masterSecretLabel = []byte("master secret")
var extendedMasterSecretLabel = []byte("extended master secret")
var keyExpansionLabel = []byte("key expansion")
var clientFinishedLabel = []byte("client finished")
var serverFinishedLabel = []byte("server finished")
var finishedLabel = []byte("finished")
var channelIDLabel = []byte("TLS Channel ID signature\x00")
var channelIDResumeLabel = []byte("Resumption\x00")

func prfForVersion(version uint16, suite *cipherSuite) func(result, secret, label, seed []byte) {
	switch version {
	case VersionSSL30:
		return prf30
	case VersionTLS10, VersionTLS11:
		return prf10
	case VersionTLS12:
		return prf12(suite.hash().New)
	}
	panic("unknown version")
}

// masterFromPreMasterSecret generates the master secret from the pre-master
// secret. See http://tools.ietf.org/html/rfc5246#section-8.1
func masterFromPreMasterSecret(version uint16, suite *cipherSuite, preMasterSecret, clientRandom, serverRandom []byte) []byte {
	var seed [tlsRandomLength * 2]byte
	copy(seed[0:len(clientRandom)], clientRandom)
	copy(seed[len(clientRandom):], serverRandom)
	masterSecret := make([]byte, masterSecretLength)
	prfForVersion(version, suite)(masterSecret, preMasterSecret, masterSecretLabel, seed[0:])
	return masterSecret
}

// extendedMasterFromPreMasterSecret generates the master secret from the
// pre-master secret when the Triple Handshake fix is in effect. See
// https://tools.ietf.org/html/rfc7627
func extendedMasterFromPreMasterSecret(version uint16, suite *cipherSuite, preMasterSecret []byte, h finishedHash) []byte {
	masterSecret := make([]byte, masterSecretLength)
	prfForVersion(version, suite)(masterSecret, preMasterSecret, extendedMasterSecretLabel, h.Sum())
	return masterSecret
}

// keysFromMasterSecret generates the connection keys from the master
// secret, given the lengths of the MAC key, cipher key and IV, as defined in
// RFC 2246, section 6.3.
func keysFromMasterSecret(version uint16, suite *cipherSuite, masterSecret, clientRandom, serverRandom []byte, macLen, keyLen, ivLen int) (clientMAC, serverMAC, clientKey, serverKey, clientIV, serverIV []byte) {
	var seed [tlsRandomLength * 2]byte
	copy(seed[0:len(clientRandom)], serverRandom)
	copy(seed[len(serverRandom):], clientRandom)

	n := 2*macLen + 2*keyLen + 2*ivLen
	keyMaterial := make([]byte, n)
	prfForVersion(version, suite)(keyMaterial, masterSecret, keyExpansionLabel, seed[0:])
	clientMAC = keyMaterial[:macLen]
	keyMaterial = keyMaterial[macLen:]
	serverMAC = keyMaterial[:macLen]
	keyMaterial = keyMaterial[macLen:]
	clientKey = keyMaterial[:keyLen]
	keyMaterial = keyMaterial[keyLen:]
	serverKey = keyMaterial[:keyLen]
	keyMaterial = keyMaterial[keyLen:]
	clientIV = keyMaterial[:ivLen]
	keyMaterial = keyMaterial[ivLen:]
	serverIV = keyMaterial[:ivLen]
	return
}

func newFinishedHash(wireVersion uint16, isDTLS bool, cipherSuite *cipherSuite) finishedHash {
	var ret finishedHash

	version, ok := wireToVersion(wireVersion, isDTLS)
	if !ok {
		panic("unknown version")
	}

	if version >= VersionTLS12 {
		ret.hash = cipherSuite.hash()

		ret.client = ret.hash.New()
		ret.server = ret.hash.New()

		if version == VersionTLS12 {
			ret.prf = prf12(ret.hash.New)
		} else {
			ret.secret = make([]byte, ret.hash.Size())
		}
	} else {
		ret.hash = crypto.MD5SHA1

		ret.client = sha1.New()
		ret.server = sha1.New()
		ret.clientMD5 = md5.New()
		ret.serverMD5 = md5.New()

		ret.prf = prf10
	}

	ret.buffer = []byte{}
	ret.version = version
	ret.wireVersion = wireVersion
	return ret
}

// A finishedHash calculates the hash of a set of handshake messages suitable
// for including in a Finished message.
type finishedHash struct {
	hash crypto.Hash

	client hash.Hash
	server hash.Hash

	// Prior to TLS 1.2, an additional MD5 hash is required.
	clientMD5 hash.Hash
	serverMD5 hash.Hash

	// In TLS 1.2 (and SSL 3 for implementation convenience), a
	// full buffer is required.
	buffer []byte

	version     uint16
	wireVersion uint16
	prf         func(result, secret, label, seed []byte)

	// secret, in TLS 1.3, is the running input secret.
	secret []byte
}

func (h *finishedHash) UpdateForHelloRetryRequest() (err error) {
	data := newByteBuilder()
	data.addU8(typeMessageHash)
	data.addU24(h.hash.Size())
	data.addBytes(h.Sum())
	h.client = h.hash.New()
	h.server = h.hash.New()
	if h.buffer != nil {
		h.buffer = []byte{}
	}
	h.Write(data.finish())
	return nil
}

func (h *finishedHash) Write(msg []byte) (n int, err error) {
	h.client.Write(msg)
	h.server.Write(msg)

	if h.version < VersionTLS12 {
		h.clientMD5.Write(msg)
		h.serverMD5.Write(msg)
	}

	if h.buffer != nil {
		h.buffer = append(h.buffer, msg...)
	}

	return len(msg), nil
}

func (h finishedHash) Sum() []byte {
	if h.version >= VersionTLS12 {
		return h.client.Sum(nil)
	}

	out := make([]byte, 0, md5.Size+sha1.Size)
	out = h.clientMD5.Sum(out)
	return h.client.Sum(out)
}

// finishedSum30 calculates the contents of the verify_data member of a SSLv3
// Finished message given the MD5 and SHA1 hashes of a set of handshake
// messages.
func finishedSum30(md5, sha1 hash.Hash, masterSecret []byte, magic []byte) []byte {
	md5.Write(magic)
	md5.Write(masterSecret)
	md5.Write(ssl30Pad1[:])
	md5Digest := md5.Sum(nil)

	md5.Reset()
	md5.Write(masterSecret)
	md5.Write(ssl30Pad2[:])
	md5.Write(md5Digest)
	md5Digest = md5.Sum(nil)

	sha1.Write(magic)
	sha1.Write(masterSecret)
	sha1.Write(ssl30Pad1[:40])
	sha1Digest := sha1.Sum(nil)

	sha1.Reset()
	sha1.Write(masterSecret)
	sha1.Write(ssl30Pad2[:40])
	sha1.Write(sha1Digest)
	sha1Digest = sha1.Sum(nil)

	ret := make([]byte, len(md5Digest)+len(sha1Digest))
	copy(ret, md5Digest)
	copy(ret[len(md5Digest):], sha1Digest)
	return ret
}

var ssl3ClientFinishedMagic = [4]byte{0x43, 0x4c, 0x4e, 0x54}
var ssl3ServerFinishedMagic = [4]byte{0x53, 0x52, 0x56, 0x52}

// clientSum returns the contents of the verify_data member of a client's
// Finished message.
func (h finishedHash) clientSum(baseKey []byte) []byte {
	if h.version == VersionSSL30 {
		return finishedSum30(h.clientMD5, h.client, baseKey, ssl3ClientFinishedMagic[:])
	}

	if h.version < VersionTLS13 {
		out := make([]byte, finishedVerifyLength)
		h.prf(out, baseKey, clientFinishedLabel, h.Sum())
		return out
	}

	clientFinishedKey := hkdfExpandLabel(h.hash, baseKey, finishedLabel, nil, h.hash.Size())
	finishedHMAC := hmac.New(h.hash.New, clientFinishedKey)
	finishedHMAC.Write(h.appendContextHashes(nil))
	return finishedHMAC.Sum(nil)
}

// serverSum returns the contents of the verify_data member of a server's
// Finished message.
func (h finishedHash) serverSum(baseKey []byte) []byte {
	if h.version == VersionSSL30 {
		return finishedSum30(h.serverMD5, h.server, baseKey, ssl3ServerFinishedMagic[:])
	}

	if h.version < VersionTLS13 {
		out := make([]byte, finishedVerifyLength)
		h.prf(out, baseKey, serverFinishedLabel, h.Sum())
		return out
	}

	serverFinishedKey := hkdfExpandLabel(h.hash, baseKey, finishedLabel, nil, h.hash.Size())
	finishedHMAC := hmac.New(h.hash.New, serverFinishedKey)
	finishedHMAC.Write(h.appendContextHashes(nil))
	return finishedHMAC.Sum(nil)
}

// hashForClientCertificateSSL3 returns the hash to be signed for client
// certificates in SSL 3.0.
func (h finishedHash) hashForClientCertificateSSL3(masterSecret []byte) []byte {
	md5Hash := md5.New()
	md5Hash.Write(h.buffer)
	sha1Hash := sha1.New()
	sha1Hash.Write(h.buffer)
	return finishedSum30(md5Hash, sha1Hash, masterSecret, nil)
}

// hashForChannelID returns the hash to be signed for TLS Channel
// ID. If a resumption, resumeHash has the previous handshake
// hash. Otherwise, it is nil.
func (h finishedHash) hashForChannelID(resumeHash []byte) []byte {
	hash := sha256.New()
	hash.Write(channelIDLabel)
	if resumeHash != nil {
		hash.Write(channelIDResumeLabel)
		hash.Write(resumeHash)
	}
	hash.Write(h.Sum())
	return hash.Sum(nil)
}

// discardHandshakeBuffer is called when there is no more need to
// buffer the entirety of the handshake messages.
func (h *finishedHash) discardHandshakeBuffer() {
	h.buffer = nil
}

// zeroSecretTLS13 returns the default all zeros secret for TLS 1.3, used when a
// given secret is not available in the handshake. See RFC 8446, section 7.1.
func (h *finishedHash) zeroSecret() []byte {
	return make([]byte, h.hash.Size())
}

// addEntropy incorporates ikm into the running TLS 1.3 secret with HKDF-Expand.
func (h *finishedHash) addEntropy(ikm []byte) {
	h.secret = hkdfExtract(h.hash.New, h.secret, ikm)
}

func (h *finishedHash) nextSecret() {
	h.secret = hkdfExpandLabel(h.hash, h.secret, []byte("derived"), h.hash.New().Sum(nil), h.hash.Size())
}

// hkdfExpandLabel implements TLS 1.3's HKDF-Expand-Label function, as defined
// in section 7.1 of RFC 8446.
func hkdfExpandLabel(hash crypto.Hash, secret, label, hashValue []byte, length int) []byte {
	if len(label) > 255 || len(hashValue) > 255 {
		panic("hkdfExpandLabel: label or hashValue too long")
	}

	versionLabel := []byte("tls13 ")
	hkdfLabel := make([]byte, 3+len(versionLabel)+len(label)+1+len(hashValue))
	x := hkdfLabel
	x[0] = byte(length >> 8)
	x[1] = byte(length)
	x[2] = byte(len(versionLabel) + len(label))
	x = x[3:]
	copy(x, versionLabel)
	x = x[len(versionLabel):]
	copy(x, label)
	x = x[len(label):]
	x[0] = byte(len(hashValue))
	copy(x[1:], hashValue)
	return hkdfExpand(hash.New, secret, hkdfLabel, length)
}

// appendContextHashes returns the concatenation of the handshake hash and the
// resumption context hash, as used in TLS 1.3.
func (h *finishedHash) appendContextHashes(b []byte) []byte {
	b = h.client.Sum(b)
	return b
}

// The following are labels for traffic secret derivation in TLS 1.3.
var (
	externalPSKBinderLabel        = []byte("ext binder")
	resumptionPSKBinderLabel      = []byte("res binder")
	earlyTrafficLabel             = []byte("c e traffic")
	clientHandshakeTrafficLabel   = []byte("c hs traffic")
	serverHandshakeTrafficLabel   = []byte("s hs traffic")
	clientApplicationTrafficLabel = []byte("c ap traffic")
	serverApplicationTrafficLabel = []byte("s ap traffic")
	applicationTrafficLabel       = []byte("traffic upd")
	earlyExporterLabel            = []byte("e exp master")
	exporterLabel                 = []byte("exp master")
	resumptionLabel               = []byte("res master")

	resumptionPSKLabel = []byte("resumption")
)

// deriveSecret implements TLS 1.3's Derive-Secret function, as defined in
// section 7.1 of draft ietf-tls-tls13-16.
func (h *finishedHash) deriveSecret(label []byte) []byte {
	return hkdfExpandLabel(h.hash, h.secret, label, h.appendContextHashes(nil), h.hash.Size())
}

// The following are context strings for CertificateVerify in TLS 1.3.
var (
	clientCertificateVerifyContextTLS13 = []byte("TLS 1.3, client CertificateVerify")
	serverCertificateVerifyContextTLS13 = []byte("TLS 1.3, server CertificateVerify")
	channelIDContextTLS13               = []byte("TLS 1.3, Channel ID")
)

// certificateVerifyMessage returns the input to be signed for CertificateVerify
// in TLS 1.3.
func (h *finishedHash) certificateVerifyInput(context []byte) []byte {
	const paddingLen = 64
	b := make([]byte, paddingLen, paddingLen+len(context)+1+2*h.hash.Size())
	for i := 0; i < paddingLen; i++ {
		b[i] = 32
	}
	b = append(b, context...)
	b = append(b, 0)
	b = h.appendContextHashes(b)
	return b
}

type trafficDirection int

const (
	clientWrite trafficDirection = iota
	serverWrite
)

var (
	keyTLS13 = []byte("key")
	ivTLS13  = []byte("iv")
)

// deriveTrafficAEAD derives traffic keys and constructs an AEAD given a traffic
// secret.
func deriveTrafficAEAD(version uint16, suite *cipherSuite, secret []byte, side trafficDirection) interface{} {
	key := hkdfExpandLabel(suite.hash(), secret, keyTLS13, nil, suite.keyLen)
	iv := hkdfExpandLabel(suite.hash(), secret, ivTLS13, nil, suite.ivLen(version))

	return suite.aead(version, key, iv)
}

func updateTrafficSecret(hash crypto.Hash, version uint16, secret []byte) []byte {
	return hkdfExpandLabel(hash, secret, applicationTrafficLabel, nil, hash.Size())
}

func computePSKBinder(psk []byte, version uint16, label []byte, cipherSuite *cipherSuite, clientHello, helloRetryRequest, truncatedHello []byte) []byte {
	finishedHash := newFinishedHash(version, false, cipherSuite)
	finishedHash.addEntropy(psk)
	binderKey := finishedHash.deriveSecret(label)
	finishedHash.Write(clientHello)
	if len(helloRetryRequest) != 0 {
		finishedHash.UpdateForHelloRetryRequest()
	}
	finishedHash.Write(helloRetryRequest)
	finishedHash.Write(truncatedHello)
	return finishedHash.clientSum(binderKey)
}

func deriveSessionPSK(suite *cipherSuite, version uint16, masterSecret []byte, nonce []byte) []byte {
	hash := suite.hash()
	return hkdfExpandLabel(hash, masterSecret, resumptionPSKLabel, nonce, hash.Size())
}

// Copyright 2010 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
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

	"boringssl.googlesource.com/boringssl/ssl/test/runner/curve25519"
	"boringssl.googlesource.com/boringssl/ssl/test/runner/hrss"
	"boringssl.googlesource.com/boringssl/ssl/test/runner/sike"
)

type keyType int

const (
	keyTypeRSA keyType = iota + 1
	keyTypeECDSA
)

var errClientKeyExchange = errors.New("tls: invalid ClientKeyExchange message")
var errServerKeyExchange = errors.New("tls: invalid ServerKeyExchange message")

// rsaKeyAgreement implements the standard TLS key agreement where the client
// encrypts the pre-master secret to the server's public key.
type rsaKeyAgreement struct {
	version       uint16
	clientVersion uint16
	exportKey     *rsa.PrivateKey
}

func (ka *rsaKeyAgreement) generateServerKeyExchange(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, version uint16) (*serverKeyExchangeMsg, error) {
	// Save the client version for comparison later.
	ka.clientVersion = clientHello.vers

	if !config.Bugs.RSAEphemeralKey {
		return nil, nil
	}

	// Generate an ephemeral RSA key to use instead of the real
	// one, as in RSA_EXPORT.
	key, err := rsa.GenerateKey(config.rand(), 512)
	if err != nil {
		return nil, err
	}
	ka.exportKey = key

	modulus := key.N.Bytes()
	exponent := big.NewInt(int64(key.E)).Bytes()
	serverRSAParams := make([]byte, 0, 2+len(modulus)+2+len(exponent))
	serverRSAParams = append(serverRSAParams, byte(len(modulus)>>8), byte(len(modulus)))
	serverRSAParams = append(serverRSAParams, modulus...)
	serverRSAParams = append(serverRSAParams, byte(len(exponent)>>8), byte(len(exponent)))
	serverRSAParams = append(serverRSAParams, exponent...)

	var sigAlg signatureAlgorithm
	if ka.version >= VersionTLS12 {
		sigAlg, err = selectSignatureAlgorithm(ka.version, cert.PrivateKey, config, clientHello.signatureAlgorithms)
		if err != nil {
			return nil, err
		}
	}

	sig, err := signMessage(ka.version, cert.PrivateKey, config, sigAlg, serverRSAParams)
	if err != nil {
		return nil, errors.New("failed to sign RSA parameters: " + err.Error())
	}

	skx := new(serverKeyExchangeMsg)
	sigAlgsLen := 0
	if ka.version >= VersionTLS12 {
		sigAlgsLen = 2
	}
	skx.key = make([]byte, len(serverRSAParams)+sigAlgsLen+2+len(sig))
	copy(skx.key, serverRSAParams)
	k := skx.key[len(serverRSAParams):]
	if ka.version >= VersionTLS12 {
		k[0] = byte(sigAlg >> 8)
		k[1] = byte(sigAlg)
		k = k[2:]
	}
	k[0] = byte(len(sig) >> 8)
	k[1] = byte(len(sig))
	copy(k[2:], sig)

	return skx, nil
}

func (ka *rsaKeyAgreement) processClientKeyExchange(config *Config, cert *Certificate, ckx *clientKeyExchangeMsg, version uint16) ([]byte, error) {
	preMasterSecret := make([]byte, 48)
	_, err := io.ReadFull(config.rand(), preMasterSecret[2:])
	if err != nil {
		return nil, err
	}

	if len(ckx.ciphertext) < 2 {
		return nil, errClientKeyExchange
	}

	ciphertext := ckx.ciphertext
	if version != VersionSSL30 {
		ciphertextLen := int(ckx.ciphertext[0])<<8 | int(ckx.ciphertext[1])
		if ciphertextLen != len(ckx.ciphertext)-2 {
			return nil, errClientKeyExchange
		}
		ciphertext = ckx.ciphertext[2:]
	}

	key := cert.PrivateKey.(*rsa.PrivateKey)
	if ka.exportKey != nil {
		key = ka.exportKey
	}
	err = rsa.DecryptPKCS1v15SessionKey(config.rand(), key, ciphertext, preMasterSecret)
	if err != nil {
		return nil, err
	}
	// This check should be done in constant-time, but this is a testing
	// implementation. See the discussion at the end of section 7.4.7.1 of
	// RFC 4346.
	vers := uint16(preMasterSecret[0])<<8 | uint16(preMasterSecret[1])
	if ka.clientVersion != vers {
		return nil, fmt.Errorf("tls: invalid version in RSA premaster (got %04x, wanted %04x)", vers, ka.clientVersion)
	}
	return preMasterSecret, nil
}

func (ka *rsaKeyAgreement) processServerKeyExchange(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, skx *serverKeyExchangeMsg) error {
	return errors.New("tls: unexpected ServerKeyExchange")
}

func rsaSize(pub *rsa.PublicKey) int {
	return (pub.N.BitLen() + 7) / 8
}

func rsaRawEncrypt(pub *rsa.PublicKey, msg []byte) ([]byte, error) {
	k := rsaSize(pub)
	if len(msg) != k {
		return nil, errors.New("tls: bad padded RSA input")
	}
	m := new(big.Int).SetBytes(msg)
	e := big.NewInt(int64(pub.E))
	m.Exp(m, e, pub.N)
	unpadded := m.Bytes()
	ret := make([]byte, k)
	copy(ret[len(ret)-len(unpadded):], unpadded)
	return ret, nil
}

// nonZeroRandomBytes fills the given slice with non-zero random octets.
func nonZeroRandomBytes(s []byte, rand io.Reader) {
	if _, err := io.ReadFull(rand, s); err != nil {
		panic(err)
	}

	for i := range s {
		for s[i] == 0 {
			if _, err := io.ReadFull(rand, s[i:i+1]); err != nil {
				panic(err)
			}
		}
	}
}

func (ka *rsaKeyAgreement) generateClientKeyExchange(config *Config, clientHello *clientHelloMsg, cert *x509.Certificate) ([]byte, *clientKeyExchangeMsg, error) {
	bad := config.Bugs.BadRSAClientKeyExchange
	preMasterSecret := make([]byte, 48)
	vers := clientHello.vers
	if bad == RSABadValueWrongVersion1 {
		vers ^= 1
	} else if bad == RSABadValueWrongVersion2 {
		vers ^= 0x100
	}
	preMasterSecret[0] = byte(vers >> 8)
	preMasterSecret[1] = byte(vers)
	_, err := io.ReadFull(config.rand(), preMasterSecret[2:])
	if err != nil {
		return nil, nil, err
	}

	sentPreMasterSecret := preMasterSecret
	if bad == RSABadValueTooLong {
		sentPreMasterSecret = make([]byte, 1, len(sentPreMasterSecret)+1)
		sentPreMasterSecret = append(sentPreMasterSecret, preMasterSecret...)
	} else if bad == RSABadValueTooShort {
		sentPreMasterSecret = sentPreMasterSecret[:len(sentPreMasterSecret)-1]
	}

	// Pad for PKCS#1 v1.5.
	padded := make([]byte, rsaSize(cert.PublicKey.(*rsa.PublicKey)))
	padded[1] = 2
	nonZeroRandomBytes(padded[2:len(padded)-len(sentPreMasterSecret)-1], config.rand())
	copy(padded[len(padded)-len(sentPreMasterSecret):], sentPreMasterSecret)

	if bad == RSABadValueWrongBlockType {
		padded[1] = 3
	} else if bad == RSABadValueWrongLeadingByte {
		padded[0] = 1
	} else if bad == RSABadValueNoZero {
		for i := 2; i < len(padded); i++ {
			if padded[i] == 0 {
				padded[i]++
			}
		}
	}

	encrypted, err := rsaRawEncrypt(cert.PublicKey.(*rsa.PublicKey), padded)
	if err != nil {
		return nil, nil, err
	}
	if bad == RSABadValueCorrupt {
		encrypted[len(encrypted)-1] ^= 1
		// Clear the high byte to ensure |encrypted| is still below the RSA modulus.
		encrypted[0] = 0
	}
	ckx := new(clientKeyExchangeMsg)
	if ka.version != VersionSSL30 {
		ckx.ciphertext = make([]byte, len(encrypted)+2)
		ckx.ciphertext[0] = byte(len(encrypted) >> 8)
		ckx.ciphertext[1] = byte(len(encrypted))
		copy(ckx.ciphertext[2:], encrypted)
	} else {
		ckx.ciphertext = encrypted
	}
	return preMasterSecret, ckx, nil
}

func (ka *rsaKeyAgreement) peerSignatureAlgorithm() signatureAlgorithm {
	return 0
}

// A ecdhCurve is an instance of ECDH-style key agreement for TLS.
type ecdhCurve interface {
	// offer generates a keypair using rand. It returns the encoded |publicKey|.
	offer(rand io.Reader) (publicKey []byte, err error)

	// accept responds to the |peerKey| generated by |offer| with the acceptor's
	// |publicKey|, and returns agreed-upon |preMasterSecret| to the acceptor.
	accept(rand io.Reader, peerKey []byte) (publicKey []byte, preMasterSecret []byte, err error)

	// finish returns the computed |preMasterSecret|, given the |peerKey|
	// generated by |accept|.
	finish(peerKey []byte) (preMasterSecret []byte, err error)
}

// ellipticECDHCurve implements ecdhCurve with an elliptic.Curve.
type ellipticECDHCurve struct {
	curve          elliptic.Curve
	privateKey     []byte
	sendCompressed bool
}

func (e *ellipticECDHCurve) offer(rand io.Reader) (publicKey []byte, err error) {
	var x, y *big.Int
	e.privateKey, x, y, err = elliptic.GenerateKey(e.curve, rand)
	if err != nil {
		return nil, err
	}
	ret := elliptic.Marshal(e.curve, x, y)
	if e.sendCompressed {
		l := (len(ret) - 1) / 2
		tmp := make([]byte, 1+l)
		tmp[0] = byte(2 | y.Bit(0))
		copy(tmp[1:], ret[1:1+l])
		ret = tmp
	}
	return ret, nil
}

func (e *ellipticECDHCurve) accept(rand io.Reader, peerKey []byte) (publicKey []byte, preMasterSecret []byte, err error) {
	publicKey, err = e.offer(rand)
	if err != nil {
		return nil, nil, err
	}
	preMasterSecret, err = e.finish(peerKey)
	if err != nil {
		return nil, nil, err
	}
	return
}

func (e *ellipticECDHCurve) finish(peerKey []byte) (preMasterSecret []byte, err error) {
	x, y := elliptic.Unmarshal(e.curve, peerKey)
	if x == nil {
		return nil, errors.New("tls: invalid peer key")
	}
	x, _ = e.curve.ScalarMult(x, y, e.privateKey)
	preMasterSecret = make([]byte, (e.curve.Params().BitSize+7)>>3)
	xBytes := x.Bytes()
	copy(preMasterSecret[len(preMasterSecret)-len(xBytes):], xBytes)

	return preMasterSecret, nil
}

// x25519ECDHCurve implements ecdhCurve with X25519.
type x25519ECDHCurve struct {
	privateKey [32]byte
	setHighBit bool
}

func (e *x25519ECDHCurve) offer(rand io.Reader) (publicKey []byte, err error) {
	_, err = io.ReadFull(rand, e.privateKey[:])
	if err != nil {
		return
	}
	var out [32]byte
	curve25519.ScalarBaseMult(&out, &e.privateKey)
	if e.setHighBit {
		out[31] |= 0x80
	}
	return out[:], nil
}

func (e *x25519ECDHCurve) accept(rand io.Reader, peerKey []byte) (publicKey []byte, preMasterSecret []byte, err error) {
	publicKey, err = e.offer(rand)
	if err != nil {
		return nil, nil, err
	}
	preMasterSecret, err = e.finish(peerKey)
	if err != nil {
		return nil, nil, err
	}
	return
}

func (e *x25519ECDHCurve) finish(peerKey []byte) (preMasterSecret []byte, err error) {
	if len(peerKey) != 32 {
		return nil, errors.New("tls: invalid peer key")
	}
	var out, peerKeyCopy [32]byte
	copy(peerKeyCopy[:], peerKey)
	curve25519.ScalarMult(&out, &e.privateKey, &peerKeyCopy)

	// Per RFC 7748, reject the all-zero value in constant time.
	var zeros [32]byte
	if subtle.ConstantTimeCompare(zeros[:], out[:]) == 1 {
		return nil, errors.New("tls: X25519 value with wrong order")
	}

	return out[:], nil
}

// cecpq2Curve implements CECPQ2, which is HRSS+SXY combined with X25519.
type cecpq2Curve struct {
	x25519PrivateKey [32]byte
	hrssPrivateKey   hrss.PrivateKey
}

func (e *cecpq2Curve) offer(rand io.Reader) (publicKey []byte, err error) {
	if _, err := io.ReadFull(rand, e.x25519PrivateKey[:]); err != nil {
		return nil, err
	}

	var x25519Public [32]byte
	curve25519.ScalarBaseMult(&x25519Public, &e.x25519PrivateKey)

	e.hrssPrivateKey = hrss.GenerateKey(rand)
	hrssPublic := e.hrssPrivateKey.PublicKey.Marshal()

	var ret []byte
	ret = append(ret, x25519Public[:]...)
	ret = append(ret, hrssPublic...)
	return ret, nil
}

func (e *cecpq2Curve) accept(rand io.Reader, peerKey []byte) (publicKey []byte, preMasterSecret []byte, err error) {
	if len(peerKey) != 32+hrss.PublicKeySize {
		return nil, nil, errors.New("tls: bad length CECPQ2 offer")
	}

	if _, err := io.ReadFull(rand, e.x25519PrivateKey[:]); err != nil {
		return nil, nil, err
	}

	var x25519Shared, x25519PeerKey, x25519Public [32]byte
	copy(x25519PeerKey[:], peerKey)
	curve25519.ScalarBaseMult(&x25519Public, &e.x25519PrivateKey)
	curve25519.ScalarMult(&x25519Shared, &e.x25519PrivateKey, &x25519PeerKey)

	// Per RFC 7748, reject the all-zero value in constant time.
	var zeros [32]byte
	if subtle.ConstantTimeCompare(zeros[:], x25519Shared[:]) == 1 {
		return nil, nil, errors.New("tls: X25519 value with wrong order")
	}

	hrssPublicKey, ok := hrss.ParsePublicKey(peerKey[32:])
	if !ok {
		return nil, nil, errors.New("tls: bad CECPQ2 offer")
	}

	hrssCiphertext, hrssShared := hrssPublicKey.Encap(rand)

	publicKey = append(publicKey, x25519Public[:]...)
	publicKey = append(publicKey, hrssCiphertext...)
	preMasterSecret = append(preMasterSecret, x25519Shared[:]...)
	preMasterSecret = append(preMasterSecret, hrssShared...)

	return publicKey, preMasterSecret, nil
}

func (e *cecpq2Curve) finish(peerKey []byte) (preMasterSecret []byte, err error) {
	if len(peerKey) != 32+hrss.CiphertextSize {
		return nil, errors.New("tls: bad length CECPQ2 reply")
	}

	var x25519Shared, x25519PeerKey [32]byte
	copy(x25519PeerKey[:], peerKey)
	curve25519.ScalarMult(&x25519Shared, &e.x25519PrivateKey, &x25519PeerKey)

	// Per RFC 7748, reject the all-zero value in constant time.
	var zeros [32]byte
	if subtle.ConstantTimeCompare(zeros[:], x25519Shared[:]) == 1 {
		return nil, errors.New("tls: X25519 value with wrong order")
	}

	hrssShared, ok := e.hrssPrivateKey.Decap(peerKey[32:])
	if !ok {
		return nil, errors.New("tls: invalid HRSS ciphertext")
	}

	preMasterSecret = append(preMasterSecret, x25519Shared[:]...)
	preMasterSecret = append(preMasterSecret, hrssShared...)

	return preMasterSecret, nil
}

// cecpq2BCurve implements CECPQ2b, which is SIKE combined with X25519.
type cecpq2BCurve struct {
	// Both public key and shared secret size
	x25519PrivateKey [32]byte
	sikePrivateKey   *sike.PrivateKey
}

func (e *cecpq2BCurve) offer(rand io.Reader) (publicKey []byte, err error) {
	if _, err = io.ReadFull(rand, e.x25519PrivateKey[:]); err != nil {
		return nil, err
	}

	var x25519Public [32]byte
	curve25519.ScalarBaseMult(&x25519Public, &e.x25519PrivateKey)

	e.sikePrivateKey = sike.NewPrivateKey(sike.KeyVariant_SIKE)
	if err = e.sikePrivateKey.Generate(rand); err != nil {
		return nil, err
	}

	sikePublic := e.sikePrivateKey.GeneratePublicKey().Export()
	var ret []byte
	ret = append(ret, x25519Public[:]...)
	ret = append(ret, sikePublic...)
	return ret, nil
}

func (e *cecpq2BCurve) accept(rand io.Reader, peerKey []byte) (publicKey []byte, preMasterSecret []byte, err error) {
	if len(peerKey) != 32+sike.Params.PublicKeySize {
		return nil, nil, errors.New("tls: bad length CECPQ2b offer")
	}

	if _, err = io.ReadFull(rand, e.x25519PrivateKey[:]); err != nil {
		return nil, nil, err
	}

	var x25519Shared, x25519PeerKey, x25519Public [32]byte
	copy(x25519PeerKey[:], peerKey)
	curve25519.ScalarBaseMult(&x25519Public, &e.x25519PrivateKey)
	curve25519.ScalarMult(&x25519Shared, &e.x25519PrivateKey, &x25519PeerKey)

	// Per RFC 7748, reject the all-zero value in constant time.
	var zeros [32]byte
	if subtle.ConstantTimeCompare(zeros[:], x25519Shared[:]) == 1 {
		return nil, nil, errors.New("tls: X25519 value with wrong order")
	}

	var sikePubKey = sike.NewPublicKey(sike.KeyVariant_SIKE)
	if err = sikePubKey.Import(peerKey[32:]); err != nil {
		// should never happen as size was already checked
		return nil, nil, errors.New("tls: implementation error")
	}
	sikeCiphertext, sikeShared, err := sike.Encapsulate(rand, sikePubKey)
	if err != nil {
		return nil, nil, err
	}

	publicKey = append(publicKey, x25519Public[:]...)
	publicKey = append(publicKey, sikeCiphertext...)
	preMasterSecret = append(preMasterSecret, x25519Shared[:]...)
	preMasterSecret = append(preMasterSecret, sikeShared...)

	return publicKey, preMasterSecret, nil
}

func (e *cecpq2BCurve) finish(peerKey []byte) (preMasterSecret []byte, err error) {
	if len(peerKey) != 32+(sike.Params.PublicKeySize+sike.Params.MsgLen) {
		return nil, errors.New("tls: bad length CECPQ2b reply")
	}

	var x25519Shared, x25519PeerKey [32]byte
	copy(x25519PeerKey[:], peerKey)
	curve25519.ScalarMult(&x25519Shared, &e.x25519PrivateKey, &x25519PeerKey)

	// Per RFC 7748, reject the all-zero value in constant time.
	var zeros [32]byte
	if subtle.ConstantTimeCompare(zeros[:], x25519Shared[:]) == 1 {
		return nil, errors.New("tls: X25519 value with wrong order")
	}

	var sikePubKey = e.sikePrivateKey.GeneratePublicKey()
	sikeShared, err := sike.Decapsulate(e.sikePrivateKey, sikePubKey, peerKey[32:])
	if err != nil {
		return nil, errors.New("tls: invalid SIKE ciphertext")
	}

	preMasterSecret = append(preMasterSecret, x25519Shared[:]...)
	preMasterSecret = append(preMasterSecret, sikeShared...)

	return preMasterSecret, nil
}

func curveForCurveID(id CurveID, config *Config) (ecdhCurve, bool) {
	switch id {
	case CurveP224:
		return &ellipticECDHCurve{curve: elliptic.P224(), sendCompressed: config.Bugs.SendCompressedCoordinates}, true
	case CurveP256:
		return &ellipticECDHCurve{curve: elliptic.P256(), sendCompressed: config.Bugs.SendCompressedCoordinates}, true
	case CurveP384:
		return &ellipticECDHCurve{curve: elliptic.P384(), sendCompressed: config.Bugs.SendCompressedCoordinates}, true
	case CurveP521:
		return &ellipticECDHCurve{curve: elliptic.P521(), sendCompressed: config.Bugs.SendCompressedCoordinates}, true
	case CurveX25519:
		return &x25519ECDHCurve{setHighBit: config.Bugs.SetX25519HighBit}, true
	case CurveCECPQ2:
		return &cecpq2Curve{}, true
	case CurveCECPQ2b:
		return &cecpq2BCurve{}, true
	default:
		return nil, false
	}

}

// keyAgreementAuthentication is a helper interface that specifies how
// to authenticate the ServerKeyExchange parameters.
type keyAgreementAuthentication interface {
	signParameters(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, params []byte) (*serverKeyExchangeMsg, error)
	verifyParameters(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, params []byte, sig []byte) error
}

// nilKeyAgreementAuthentication does not authenticate the key
// agreement parameters.
type nilKeyAgreementAuthentication struct{}

func (ka *nilKeyAgreementAuthentication) signParameters(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, params []byte) (*serverKeyExchangeMsg, error) {
	skx := new(serverKeyExchangeMsg)
	skx.key = params
	return skx, nil
}

func (ka *nilKeyAgreementAuthentication) verifyParameters(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, params []byte, sig []byte) error {
	return nil
}

// signedKeyAgreement signs the ServerKeyExchange parameters with the
// server's private key.
type signedKeyAgreement struct {
	keyType                keyType
	version                uint16
	peerSignatureAlgorithm signatureAlgorithm
}

func (ka *signedKeyAgreement) signParameters(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, params []byte) (*serverKeyExchangeMsg, error) {
	// The message to be signed is prepended by the randoms.
	var msg []byte
	msg = append(msg, clientHello.random...)
	msg = append(msg, hello.random...)
	msg = append(msg, params...)

	var sigAlg signatureAlgorithm
	var err error
	if ka.version >= VersionTLS12 {
		sigAlg, err = selectSignatureAlgorithm(ka.version, cert.PrivateKey, config, clientHello.signatureAlgorithms)
		if err != nil {
			return nil, err
		}
	}

	sig, err := signMessage(ka.version, cert.PrivateKey, config, sigAlg, msg)
	if err != nil {
		return nil, err
	}
	if config.Bugs.SendSignatureAlgorithm != 0 {
		sigAlg = config.Bugs.SendSignatureAlgorithm
	}

	skx := new(serverKeyExchangeMsg)
	if config.Bugs.UnauthenticatedECDH {
		skx.key = params
	} else {
		sigAlgsLen := 0
		if ka.version >= VersionTLS12 {
			sigAlgsLen = 2
		}
		skx.key = make([]byte, len(params)+sigAlgsLen+2+len(sig))
		copy(skx.key, params)
		k := skx.key[len(params):]
		if ka.version >= VersionTLS12 {
			k[0] = byte(sigAlg >> 8)
			k[1] = byte(sigAlg)
			k = k[2:]
		}
		k[0] = byte(len(sig) >> 8)
		k[1] = byte(len(sig))
		copy(k[2:], sig)
	}

	return skx, nil
}

func (ka *signedKeyAgreement) verifyParameters(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, publicKey crypto.PublicKey, params []byte, sig []byte) error {
	// The peer's key must match the cipher type.
	switch ka.keyType {
	case keyTypeECDSA:
		_, edsaOk := publicKey.(*ecdsa.PublicKey)
		_, ed25519Ok := publicKey.(ed25519.PublicKey)
		if !edsaOk && !ed25519Ok {
			return errors.New("tls: ECDHE ECDSA requires a ECDSA or Ed25519 server public key")
		}
	case keyTypeRSA:
		_, ok := publicKey.(*rsa.PublicKey)
		if !ok {
			return errors.New("tls: ECDHE RSA requires a RSA server public key")
		}
	default:
		return errors.New("tls: unknown key type")
	}

	// The message to be signed is prepended by the randoms.
	var msg []byte
	msg = append(msg, clientHello.random...)
	msg = append(msg, serverHello.random...)
	msg = append(msg, params...)

	var sigAlg signatureAlgorithm
	if ka.version >= VersionTLS12 {
		if len(sig) < 2 {
			return errServerKeyExchange
		}
		sigAlg = signatureAlgorithm(sig[0])<<8 | signatureAlgorithm(sig[1])
		sig = sig[2:]
		// Stash the signature algorithm to be extracted by the handshake.
		ka.peerSignatureAlgorithm = sigAlg
	}

	if len(sig) < 2 {
		return errServerKeyExchange
	}
	sigLen := int(sig[0])<<8 | int(sig[1])
	if sigLen+2 != len(sig) {
		return errServerKeyExchange
	}
	sig = sig[2:]

	return verifyMessage(ka.version, publicKey, config, sigAlg, msg, sig)
}

// ecdheKeyAgreement implements a TLS key agreement where the server
// generates a ephemeral EC public/private key pair and signs it. The
// pre-master secret is then calculated using ECDH. The signature may
// either be ECDSA or RSA.
type ecdheKeyAgreement struct {
	auth    keyAgreementAuthentication
	curve   ecdhCurve
	curveID CurveID
	peerKey []byte
}

func (ka *ecdheKeyAgreement) generateServerKeyExchange(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, version uint16) (*serverKeyExchangeMsg, error) {
	var curveid CurveID
	preferredCurves := config.curvePreferences()

NextCandidate:
	for _, candidate := range preferredCurves {
		if isPqGroup(candidate) && version < VersionTLS13 {
			// CECPQ2 and CECPQ2b is TLS 1.3-only.
			continue
		}

		for _, c := range clientHello.supportedCurves {
			if candidate == c {
				curveid = c
				break NextCandidate
			}
		}
	}

	if curveid == 0 {
		return nil, errors.New("tls: no supported elliptic curves offered")
	}

	var ok bool
	if ka.curve, ok = curveForCurveID(curveid, config); !ok {
		return nil, errors.New("tls: preferredCurves includes unsupported curve")
	}
	ka.curveID = curveid

	publicKey, err := ka.curve.offer(config.rand())
	if err != nil {
		return nil, err
	}

	// http://tools.ietf.org/html/rfc4492#section-5.4
	serverECDHParams := make([]byte, 1+2+1+len(publicKey))
	serverECDHParams[0] = 3 // named curve
	if config.Bugs.SendCurve != 0 {
		curveid = config.Bugs.SendCurve
	}
	serverECDHParams[1] = byte(curveid >> 8)
	serverECDHParams[2] = byte(curveid)
	serverECDHParams[3] = byte(len(publicKey))
	copy(serverECDHParams[4:], publicKey)
	if config.Bugs.InvalidECDHPoint {
		serverECDHParams[4] ^= 0xff
	}

	return ka.auth.signParameters(config, cert, clientHello, hello, serverECDHParams)
}

func (ka *ecdheKeyAgreement) processClientKeyExchange(config *Config, cert *Certificate, ckx *clientKeyExchangeMsg, version uint16) ([]byte, error) {
	if len(ckx.ciphertext) == 0 || int(ckx.ciphertext[0]) != len(ckx.ciphertext)-1 {
		return nil, errClientKeyExchange
	}
	return ka.curve.finish(ckx.ciphertext[1:])
}

func (ka *ecdheKeyAgreement) processServerKeyExchange(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, skx *serverKeyExchangeMsg) error {
	if len(skx.key) < 4 {
		return errServerKeyExchange
	}
	if skx.key[0] != 3 { // named curve
		return errors.New("tls: server selected unsupported curve")
	}
	curveid := CurveID(skx.key[1])<<8 | CurveID(skx.key[2])
	ka.curveID = curveid

	var ok bool
	if ka.curve, ok = curveForCurveID(curveid, config); !ok {
		return errors.New("tls: server selected unsupported curve")
	}

	publicLen := int(skx.key[3])
	if publicLen+4 > len(skx.key) {
		return errServerKeyExchange
	}
	// Save the peer key for later.
	ka.peerKey = skx.key[4 : 4+publicLen]

	// Check the signature.
	serverECDHParams := skx.key[:4+publicLen]
	sig := skx.key[4+publicLen:]
	return ka.auth.verifyParameters(config, clientHello, serverHello, key, serverECDHParams, sig)
}

func (ka *ecdheKeyAgreement) generateClientKeyExchange(config *Config, clientHello *clientHelloMsg, cert *x509.Certificate) ([]byte, *clientKeyExchangeMsg, error) {
	if ka.curve == nil {
		return nil, nil, errors.New("missing ServerKeyExchange message")
	}

	publicKey, preMasterSecret, err := ka.curve.accept(config.rand(), ka.peerKey)
	if err != nil {
		return nil, nil, err
	}

	ckx := new(clientKeyExchangeMsg)
	ckx.ciphertext = make([]byte, 1+len(publicKey))
	ckx.ciphertext[0] = byte(len(publicKey))
	copy(ckx.ciphertext[1:], publicKey)
	if config.Bugs.InvalidECDHPoint {
		ckx.ciphertext[1] ^= 0xff
	}

	return preMasterSecret, ckx, nil
}

func (ka *ecdheKeyAgreement) peerSignatureAlgorithm() signatureAlgorithm {
	if auth, ok := ka.auth.(*signedKeyAgreement); ok {
		return auth.peerSignatureAlgorithm
	}
	return 0
}

// nilKeyAgreement is a fake key agreement used to implement the plain PSK key
// exchange.
type nilKeyAgreement struct{}

func (ka *nilKeyAgreement) generateServerKeyExchange(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, version uint16) (*serverKeyExchangeMsg, error) {
	return nil, nil
}

func (ka *nilKeyAgreement) processClientKeyExchange(config *Config, cert *Certificate, ckx *clientKeyExchangeMsg, version uint16) ([]byte, error) {
	if len(ckx.ciphertext) != 0 {
		return nil, errClientKeyExchange
	}

	// Although in plain PSK, otherSecret is all zeros, the base key
	// agreement does not access to the length of the pre-shared
	// key. pskKeyAgreement instead interprets nil to mean to use all zeros
	// of the appropriate length.
	return nil, nil
}

func (ka *nilKeyAgreement) processServerKeyExchange(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, skx *serverKeyExchangeMsg) error {
	if len(skx.key) != 0 {
		return errServerKeyExchange
	}
	return nil
}

func (ka *nilKeyAgreement) generateClientKeyExchange(config *Config, clientHello *clientHelloMsg, cert *x509.Certificate) ([]byte, *clientKeyExchangeMsg, error) {
	// Although in plain PSK, otherSecret is all zeros, the base key
	// agreement does not access to the length of the pre-shared
	// key. pskKeyAgreement instead interprets nil to mean to use all zeros
	// of the appropriate length.
	return nil, &clientKeyExchangeMsg{}, nil
}

func (ka *nilKeyAgreement) peerSignatureAlgorithm() signatureAlgorithm {
	return 0
}

// makePSKPremaster formats a PSK pre-master secret based on otherSecret from
// the base key exchange and psk.
func makePSKPremaster(otherSecret, psk []byte) []byte {
	out := make([]byte, 0, 2+len(otherSecret)+2+len(psk))
	out = append(out, byte(len(otherSecret)>>8), byte(len(otherSecret)))
	out = append(out, otherSecret...)
	out = append(out, byte(len(psk)>>8), byte(len(psk)))
	out = append(out, psk...)
	return out
}

// pskKeyAgreement implements the PSK key agreement.
type pskKeyAgreement struct {
	base         keyAgreement
	identityHint string
}

func (ka *pskKeyAgreement) generateServerKeyExchange(config *Config, cert *Certificate, clientHello *clientHelloMsg, hello *serverHelloMsg, version uint16) (*serverKeyExchangeMsg, error) {
	// Assemble the identity hint.
	bytes := make([]byte, 2+len(config.PreSharedKeyIdentity))
	bytes[0] = byte(len(config.PreSharedKeyIdentity) >> 8)
	bytes[1] = byte(len(config.PreSharedKeyIdentity))
	copy(bytes[2:], []byte(config.PreSharedKeyIdentity))

	// If there is one, append the base key agreement's
	// ServerKeyExchange.
	baseSkx, err := ka.base.generateServerKeyExchange(config, cert, clientHello, hello, version)
	if err != nil {
		return nil, err
	}

	if baseSkx != nil {
		bytes = append(bytes, baseSkx.key...)
	} else if config.PreSharedKeyIdentity == "" && !config.Bugs.AlwaysSendPreSharedKeyIdentityHint {
		// ServerKeyExchange is optional if the identity hint is empty
		// and there would otherwise be no ServerKeyExchange.
		return nil, nil
	}

	skx := new(serverKeyExchangeMsg)
	skx.key = bytes
	return skx, nil
}

func (ka *pskKeyAgreement) processClientKeyExchange(config *Config, cert *Certificate, ckx *clientKeyExchangeMsg, version uint16) ([]byte, error) {
	// First, process the PSK identity.
	if len(ckx.ciphertext) < 2 {
		return nil, errClientKeyExchange
	}
	identityLen := (int(ckx.ciphertext[0]) << 8) | int(ckx.ciphertext[1])
	if 2+identityLen > len(ckx.ciphertext) {
		return nil, errClientKeyExchange
	}
	identity := string(ckx.ciphertext[2 : 2+identityLen])

	if identity != config.PreSharedKeyIdentity {
		return nil, errors.New("tls: unexpected identity")
	}

	if config.PreSharedKey == nil {
		return nil, errors.New("tls: pre-shared key not configured")
	}

	// Process the remainder of the ClientKeyExchange to compute the base
	// pre-master secret.
	newCkx := new(clientKeyExchangeMsg)
	newCkx.ciphertext = ckx.ciphertext[2+identityLen:]
	otherSecret, err := ka.base.processClientKeyExchange(config, cert, newCkx, version)
	if err != nil {
		return nil, err
	}

	if otherSecret == nil {
		// Special-case for the plain PSK key exchanges.
		otherSecret = make([]byte, len(config.PreSharedKey))
	}
	return makePSKPremaster(otherSecret, config.PreSharedKey), nil
}

func (ka *pskKeyAgreement) processServerKeyExchange(config *Config, clientHello *clientHelloMsg, serverHello *serverHelloMsg, key crypto.PublicKey, skx *serverKeyExchangeMsg) error {
	if len(skx.key) < 2 {
		return errServerKeyExchange
	}
	identityLen := (int(skx.key[0]) << 8) | int(skx.key[1])
	if 2+identityLen > len(skx.key) {
		return errServerKeyExchange
	}
	ka.identityHint = string(skx.key[2 : 2+identityLen])

	// Process the remainder of the ServerKeyExchange.
	newSkx := new(serverKeyExchangeMsg)
	newSkx.key = skx.key[2+identityLen:]
	return ka.base.processServerKeyExchange(config, clientHello, serverHello, key, newSkx)
}

func (ka *pskKeyAgreement) generateClientKeyExchange(config *Config, clientHello *clientHelloMsg, cert *x509.Certificate) ([]byte, *clientKeyExchangeMsg, error) {
	// The server only sends an identity hint but, for purposes of
	// test code, the server always sends the hint and it is
	// required to match.
	if ka.identityHint != config.PreSharedKeyIdentity {
		return nil, nil, errors.New("tls: unexpected identity")
	}

	// Serialize the identity.
	bytes := make([]byte, 2+len(config.PreSharedKeyIdentity))
	bytes[0] = byte(len(config.PreSharedKeyIdentity) >> 8)
	bytes[1] = byte(len(config.PreSharedKeyIdentity))
	copy(bytes[2:], []byte(config.PreSharedKeyIdentity))

	// Append the base key exchange's ClientKeyExchange.
	otherSecret, baseCkx, err := ka.base.generateClientKeyExchange(config, clientHello, cert)
	if err != nil {
		return nil, nil, err
	}
	ckx := new(clientKeyExchangeMsg)
	ckx.ciphertext = append(bytes, baseCkx.ciphertext...)

	if config.PreSharedKey == nil {
		return nil, nil, errors.New("tls: pre-shared key not configured")
	}
	if otherSecret == nil {
		otherSecret = make([]byte, len(config.PreSharedKey))
	}
	return makePSKPremaster(otherSecret, config.PreSharedKey), ckx, nil
}

func (ka *pskKeyAgreement) peerSignatureAlgorithm() signatureAlgorithm {
	return 0
}

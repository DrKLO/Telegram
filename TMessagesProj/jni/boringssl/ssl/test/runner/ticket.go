// Copyright 2012 The Go Authors. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package runner

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/binary"
	"errors"
	"io"
	"time"
)

// sessionState contains the information that is serialized into a session
// ticket in order to later resume a connection.
type sessionState struct {
	vers                 uint16
	cipherSuite          uint16
	masterSecret         []byte
	handshakeHash        []byte
	certificates         [][]byte
	extendedMasterSecret bool
	earlyALPN            []byte
	ticketCreationTime   time.Time
	ticketExpiration     time.Time
	ticketFlags          uint32
	ticketAgeAdd         uint32
}

func (s *sessionState) marshal() []byte {
	msg := newByteBuilder()
	msg.addU16(s.vers)
	msg.addU16(s.cipherSuite)
	masterSecret := msg.addU16LengthPrefixed()
	masterSecret.addBytes(s.masterSecret)
	handshakeHash := msg.addU16LengthPrefixed()
	handshakeHash.addBytes(s.handshakeHash)
	msg.addU16(uint16(len(s.certificates)))
	for _, cert := range s.certificates {
		certMsg := msg.addU32LengthPrefixed()
		certMsg.addBytes(cert)
	}

	if s.extendedMasterSecret {
		msg.addU8(1)
	} else {
		msg.addU8(0)
	}

	if s.vers >= VersionTLS13 {
		msg.addU64(uint64(s.ticketCreationTime.UnixNano()))
		msg.addU64(uint64(s.ticketExpiration.UnixNano()))
		msg.addU32(s.ticketFlags)
		msg.addU32(s.ticketAgeAdd)
	}

	earlyALPN := msg.addU16LengthPrefixed()
	earlyALPN.addBytes(s.earlyALPN)

	return msg.finish()
}

func (s *sessionState) unmarshal(data []byte) bool {
	if len(data) < 8 {
		return false
	}

	s.vers = uint16(data[0])<<8 | uint16(data[1])
	s.cipherSuite = uint16(data[2])<<8 | uint16(data[3])
	masterSecretLen := int(data[4])<<8 | int(data[5])
	data = data[6:]
	if len(data) < masterSecretLen {
		return false
	}

	s.masterSecret = data[:masterSecretLen]
	data = data[masterSecretLen:]

	if len(data) < 2 {
		return false
	}

	handshakeHashLen := int(data[0])<<8 | int(data[1])
	data = data[2:]
	if len(data) < handshakeHashLen {
		return false
	}

	s.handshakeHash = data[:handshakeHashLen]
	data = data[handshakeHashLen:]

	if len(data) < 2 {
		return false
	}

	numCerts := int(data[0])<<8 | int(data[1])
	data = data[2:]

	s.certificates = make([][]byte, numCerts)
	for i := range s.certificates {
		if len(data) < 4 {
			return false
		}
		certLen := int(data[0])<<24 | int(data[1])<<16 | int(data[2])<<8 | int(data[3])
		data = data[4:]
		if certLen < 0 {
			return false
		}
		if len(data) < certLen {
			return false
		}
		s.certificates[i] = data[:certLen]
		data = data[certLen:]
	}

	if len(data) < 1 {
		return false
	}

	s.extendedMasterSecret = false
	if data[0] == 1 {
		s.extendedMasterSecret = true
	}
	data = data[1:]

	if s.vers >= VersionTLS13 {
		if len(data) < 24 {
			return false
		}
		s.ticketCreationTime = time.Unix(0, int64(binary.BigEndian.Uint64(data)))
		data = data[8:]
		s.ticketExpiration = time.Unix(0, int64(binary.BigEndian.Uint64(data)))
		data = data[8:]
		s.ticketFlags = binary.BigEndian.Uint32(data)
		data = data[4:]
		s.ticketAgeAdd = binary.BigEndian.Uint32(data)
		data = data[4:]
	}

	earlyALPNLen := int(data[0])<<8 | int(data[1])
	data = data[2:]
	if len(data) < earlyALPNLen {
		return false
	}
	s.earlyALPN = data[:earlyALPNLen]
	data = data[earlyALPNLen:]

	if len(data) > 0 {
		return false
	}

	return true
}

func (c *Conn) encryptTicket(state *sessionState) ([]byte, error) {
	serialized := state.marshal()
	encrypted := make([]byte, aes.BlockSize+len(serialized)+sha256.Size)
	iv := encrypted[:aes.BlockSize]
	macBytes := encrypted[len(encrypted)-sha256.Size:]

	if _, err := io.ReadFull(c.config.rand(), iv); err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(c.config.SessionTicketKey[:16])
	if err != nil {
		return nil, errors.New("tls: failed to create cipher while encrypting ticket: " + err.Error())
	}
	cipher.NewCTR(block, iv).XORKeyStream(encrypted[aes.BlockSize:], serialized)

	mac := hmac.New(sha256.New, c.config.SessionTicketKey[16:32])
	mac.Write(encrypted[:len(encrypted)-sha256.Size])
	mac.Sum(macBytes[:0])

	return encrypted, nil
}

func (c *Conn) decryptTicket(encrypted []byte) (*sessionState, bool) {
	if len(encrypted) < aes.BlockSize+sha256.Size {
		return nil, false
	}

	iv := encrypted[:aes.BlockSize]
	macBytes := encrypted[len(encrypted)-sha256.Size:]

	mac := hmac.New(sha256.New, c.config.SessionTicketKey[16:32])
	mac.Write(encrypted[:len(encrypted)-sha256.Size])
	expected := mac.Sum(nil)

	if subtle.ConstantTimeCompare(macBytes, expected) != 1 {
		return nil, false
	}

	block, err := aes.NewCipher(c.config.SessionTicketKey[:16])
	if err != nil {
		return nil, false
	}
	ciphertext := encrypted[aes.BlockSize : len(encrypted)-sha256.Size]
	plaintext := make([]byte, len(ciphertext))
	cipher.NewCTR(block, iv).XORKeyStream(plaintext, ciphertext)

	state := new(sessionState)
	ok := state.unmarshal(plaintext)
	return state, ok
}

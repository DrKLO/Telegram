/********************************************************************************************
* SIDH: an efficient supersingular isogeny cryptography library
*
* Abstract: API header file for SIKE
*********************************************************************************************/

#ifndef SIKE_H_
#define SIKE_H_

#include <stdint.h>
#include <openssl/base.h>

#if defined(__cplusplus)
extern "C" {
#endif

/* SIKE
 *
 * SIKE is a isogeny based post-quantum key encapsulation mechanism. Description of the
 * algorithm is provided in [SIKE]. This implementation uses 434-bit field size. The code
 * is based on "Additional_Implementations" from PQC NIST submission package which can
 * be found here:
 * https://csrc.nist.gov/CSRC/media/Projects/Post-Quantum-Cryptography/documents/round-1/submissions/SIKE.zip
 *
 * [SIKE] https://sike.org/files/SIDH-spec.pdf
 */

// SIKE_PUB_BYTESZ is the number of bytes in a public key.
#define SIKE_PUB_BYTESZ 330
// SIKE_PRV_BYTESZ is the number of bytes in a private key.
#define SIKE_PRV_BYTESZ 28
// SIKE_SS_BYTESZ is the number of bytes in a shared key.
#define SIKE_SS_BYTESZ  16
// SIKE_MSG_BYTESZ is the number of bytes in a random bit string concatenated
// with the public key (see 1.4 of SIKE).
#define SIKE_MSG_BYTESZ 16
// SIKE_SS_BYTESZ is the number of bytes in a ciphertext.
#define SIKE_CT_BYTESZ  (SIKE_PUB_BYTESZ + SIKE_MSG_BYTESZ)

// SIKE_keypair outputs a public and secret key. Internally it uses BN_rand() as
// an entropy source. In case of success function returns 1, otherwise 0.
OPENSSL_EXPORT int SIKE_keypair(
    uint8_t out_priv[SIKE_PRV_BYTESZ],
    uint8_t out_pub[SIKE_PUB_BYTESZ]);

// SIKE_encaps generates and encrypts a random session key, writing those values to
// |out_shared_key| and |out_ciphertext|, respectively.
OPENSSL_EXPORT void SIKE_encaps(
    uint8_t out_shared_key[SIKE_SS_BYTESZ],
    uint8_t out_ciphertext[SIKE_CT_BYTESZ],
    const uint8_t pub_key[SIKE_PUB_BYTESZ]);

// SIKE_decaps outputs a random session key, writing it to |out_shared_key|.
OPENSSL_EXPORT void SIKE_decaps(
    uint8_t out_shared_key[SIKE_SS_BYTESZ],
    const uint8_t ciphertext[SIKE_CT_BYTESZ],
    const uint8_t pub_key[SIKE_PUB_BYTESZ],
    const uint8_t priv_key[SIKE_PRV_BYTESZ]);

#if defined(__cplusplus)
}
#endif

#endif

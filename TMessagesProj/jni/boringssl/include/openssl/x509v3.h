// Copyright 2023 The BoringSSL Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef OPENSSL_HEADER_X509V3_H
#define OPENSSL_HEADER_X509V3_H

// This header primarily exists in order to make compiling against code that
// expects OpenSSL easier. We have merged this header into <openssl/x509.h>.
// However, due to conflicts, some deprecated symbols are defined here.
#include <openssl/x509.h>


// CRL reason constants.

// TODO(davidben): These constants live here because strongswan defines
// conflicting symbols and has been relying on them only being defined in
// <openssl/x509v3.h>. Defining the constants in <openssl/x509.h> would break
// strongswan, but we would also like for new code to only need
// <openssl/x509.h>. Introduce properly namespaced versions of these constants
// and, separately, see if we can fix strongswan to similarly avoid the
// conflict. Between OpenSSL, strongswan, and wincrypt.h all defining these
// constants, it seems best for everyone to just avoid them going forward.
#define CRL_REASON_NONE (-1)
#define CRL_REASON_UNSPECIFIED 0
#define CRL_REASON_KEY_COMPROMISE 1
#define CRL_REASON_CA_COMPROMISE 2
#define CRL_REASON_AFFILIATION_CHANGED 3
#define CRL_REASON_SUPERSEDED 4
#define CRL_REASON_CESSATION_OF_OPERATION 5
#define CRL_REASON_CERTIFICATE_HOLD 6
#define CRL_REASON_REMOVE_FROM_CRL 8
#define CRL_REASON_PRIVILEGE_WITHDRAWN 9
#define CRL_REASON_AA_COMPROMISE 10


// Deprecated constants.

// The following constants are legacy aliases for |X509v3_KU_*|. They are
// defined here instead of in <openssl/x509.h> because NSS's public headers use
// the same symbols. Some callers have inadvertently relied on the conflicts
// only being defined in this header.
#define KU_DIGITAL_SIGNATURE X509v3_KU_DIGITAL_SIGNATURE
#define KU_NON_REPUDIATION X509v3_KU_NON_REPUDIATION
#define KU_KEY_ENCIPHERMENT X509v3_KU_KEY_ENCIPHERMENT
#define KU_DATA_ENCIPHERMENT X509v3_KU_DATA_ENCIPHERMENT
#define KU_KEY_AGREEMENT X509v3_KU_KEY_AGREEMENT
#define KU_KEY_CERT_SIGN X509v3_KU_KEY_CERT_SIGN
#define KU_CRL_SIGN X509v3_KU_CRL_SIGN
#define KU_ENCIPHER_ONLY X509v3_KU_ENCIPHER_ONLY
#define KU_DECIPHER_ONLY X509v3_KU_DECIPHER_ONLY

#endif  // OPENSSL_HEADER_X509V3_H

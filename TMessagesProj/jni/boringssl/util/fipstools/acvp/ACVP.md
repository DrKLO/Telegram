ACVP Client
===========

[ACVP](https://github.com/usnistgov/ACVP) is the next version of NIST's [CAVP](https://csrc.nist.gov/projects/cryptographic-algorithm-validation-program)—a program for running cryptographic implementations against a set of test vectors. CAVP involved emailing around zip files of somewhat-INI-like test vectors where no two files had quite the same format. ACVP is supposed to replace that with a) TLS connections rather than email (but not yet) and b) JSON rather than bespoke formats.

The tool in this directory can speak to ACVP servers, fetch test vectors, and lower the operations to a simpler protocol. A wrapper around the FIPS module in question implements that protocol for testing. Test vectors can also come from files on disk.

The tool also provides an interface for querying and manipulating the ACVP database, which contains lists of modules, vendors, contacts, operating environments, etc.

For an alternative, see [libacvp](https://github.com/cisco/libacvp).

## Building

This tool builds independently of the rest of the BoringSSL: just run `go build` within the `acvptool` directory.

## File-based operation

While ACVP hopes to replace emailing files around at some point, emailing is still common practice with most NVLAP labs. To process a file of test vectors, use the `-json` flag:

```
% ./acvptool -json input.json > output.json
```

The top-level structure of these JSON files is not specified by NIST. This tool consumes the form that appears to be most commonly used.

The lab will need to know the configuration of the module to generate tests. Obtain that with the `-regcap` option and redirect the output to a file.

### Testing other FIPS modules

Lowering ACVP to a simpler form might be useful for other modules so the protocol is described here. The tool has far from complete coverage of ACVP's mountain of options, but common cases are handled. If you have additional needs then it's hopefully straightforward to extend the tool yourself.

The FIPS module being tested needs to be wrapped such that the tool can fork and exec a binary that speaks this protocol over stdin/stdout. For BoringSSL that binary is in the `modulewrapper` directory and serves as a reference implementation if you have questions about the protocol that aren't answered below. BoringSSL's modulewrapper contains the FIPS module itself, but your binary could forward the communication over, e.g., a serial link to a hardware module. Specify the path to the binary with the `-wrapper` option.

The protocol is request–response: the subprocess only speaks in response to a request and there is exactly one response for every request. Requests consist of one or more byte strings and responses consist of zero or more byte strings.

A request contains: the number of byte strings, the length of each byte string, and the contents of each byte string. All numbers are 32-bit little-endian and values are concatenated in the order specified. The first byte string is mandatory and is the name of the command to perform. A response has the same format except that there may be zero byte strings and the first byte string has no special meaning.

All implementations must support the `getConfig` command which takes no arguments and returns a single byte string which is a JSON blob of ACVP algorithm configuration. This blob describes all the algorithms and capabilities that the module supports and is an array of JSON objects suitable for including as the `algorithms` value when [creating an ACVP vector set](http://usnistgov.github.io/ACVP/artifacts/draft-fussell-acvp-spec-00.html#rfc.section.11.15.2.1).

Crafting this JSON is an art. You can get some information from reading the [NIST documentation](https://github.com/usnistgov/ACVP#supported-algorithms) but you might also want to crib from the BoringSSL wrapper in `modulewrapper`.

The other commands are as follows. (Note that you only need to implement the commands required by the ACVP configuration returned.)

| Command              | Arguments                 | Outputs |
|----------------------|---------------------------|---------|
| 3DES-CBC/decrypt     | Key, ciphertext, IV, num iterations¹ | Result, Previous result |
| 3DES-CBC/encrypt     | Key, plaintext, IV, num iterations¹ | Result, Previous result |
| 3DES/decrypt         | Key, input block, num iterations¹ | Result, Previous result |
| 3DES/encrypt         | Key, input block, num iterations¹ | Result, Previous result |
| AES-CBC/decrypt      | Key, ciphertext, IV, num iterations¹ | Result, Previous result |
| AES-CBC/encrypt      | Key, plaintext, IV, num iterations¹ | Result, Previous result |
| AES-CBC-CS3/decrypt  | Key, ciphertext, IV, num iterations² | Result |
| AES-CBC-CS3/encrypt  | Key, plaintext, IV, num iterations²  | Result |
| AES-CCM/open         | Tag length, key, ciphertext, nonce, ad | One-byte success flag, plaintext or empty |
| AES-CCM/seal         | Tag length, key, plaintext, nonce, ad | Ciphertext |
| AES-CTR/decrypt      | Key, ciphertext, initial counter, constant 1 | Plaintext |
| AES-CTR/encrypt      | Key, plaintexttext, initial counter, constant 1 | Ciphertext |
| AES-GCM/open         | Tag length, key, ciphertext, nonce, ad | One-byte success flag, plaintext or empty |
| AES-GCM/seal         | Tag length, key, plaintext, nonce, ad | Ciphertext |
| AES-GCM-randnonce/open | Tag length, key, ciphertext (with rand nonce appended), nonce (empty), ad | One-byte success flag, plaintext or empty |
| AES-GCM-randnonce/seal | Tag length, key, plaintext, nonce (empty), ad | Ciphertext (with rand nonce appended) |
| AES-KW/open          | (dummy), key, ciphertext, (dummy), (dummy) | One-byte success flag, plaintext or empty |
| AES-KW/seal          | (dummy), key, plaintext, (dummy), (dummy) | Ciphertext |
| AES-KWP/open         | (dummy), key, ciphertext, (dummy), (dummy) | One-byte success flag, plaintext or empty |
| AES-KWP/seal         | (dummy), key, plaintext, (dummy), (dummy) | Ciphertext |
| AES-XTS/decrypt      | Key, ciphertext, tweak | Plaintext |
| AES-XTS/encrypt      | Key, plaintext, tweak | Ciphertext |
| AES/decrypt          | Key, input block, num iterations¹ | Result, Previous result |
| AES/encrypt          | Key, input block, num iterations¹ | Result, Previous result |
| CMAC-AES             | Number output bytes, key, message | MAC |
| CMAC-AES/verify      | Key, message, claimed MAC | One-byte success flag |
| ctrDRBG/AES-256      | Output length, entropy, personalisation, ad1, ad2, nonce | Output |
| ctrDRBG-reseed/AES-256| Output length, entropy, personalisation, reseedAD, reseedEntropy, ad1, ad2, nonce | Output |
| ctrDRBG-pr/AES-256   | Output length, entropy, personalisation, ad1, entropy1, ad2, entropy2, nonce | Output |
| ECDH/&lt;CURVE&gt;   | X, Y, private key | X, Y, shared key |
| ECDSA/keyGen         | Curve name | Private key, X, Y |
| ECDSA/keyVer         | Curve name, X, Y | Single-byte valid flag |
| ECDSA/sigGen         | Curve name, private key, hash name, message | R, S |
| ECDSA/sigVer         | Curve name, hash name, message, X, Y, R, S | Single-byte validity flag |
| DetECDSA/sigGen      | Curve name, private key, hash name, message | R, S |
| EDDSA/keyGen         | Curve name | private key seed (D), public key (Q) |
| EDDSA/keyVer         | Curve name, public key (Q) | Single-byte valid flag |
| EDDSA/sigGen         | Curve name, private key seed (D), message, single-byte prehash flag, prehash context | Signature |
| EDDSA/sigVer         | Curve name, message, public key (Q), signature, single-byte prehash flag | Single-byte validity flag |
| FFDH                 | p, q, g, peer public key, local private key (or empty),  local public key (or empty) | Local public key, shared key |
| HKDF/&lt;HASH&gt;    | key, salt, info, num output bytes | Key |
| HKDFExtract/&lt;HASH&gt; | secret, salt | Key |
| HKDFExpandLabel/&lt;HASH&gt; | Output length, secret, label, transcript hash | Key |
| HMAC-SHA-1           | Value to hash, key        | Digest  |
| HMAC-SHA2-224        | Value to hash, key        | Digest  |
| HMAC-SHA2-256        | Value to hash, key        | Digest  |
| HMAC-SHA2-384        | Value to hash, key        | Digest  |
| HMAC-SHA2-512        | Value to hash, key        | Digest  |
| HMAC-SHA2-512/224    | Value to hash, key        | Digest  |
| HMAC-SHA2-512/256    | Value to hash, key        | Digest  |
| hmacDRBG/&lt;HASH&gt;| Output length, entropy, personalisation, ad1, ad2, nonce | Output |
| hmacDRBG-reseed/&lt;HASH&gt;| Output length, entropy, personalisation, reseedAD, reseedEntropy, ad1, ad2, nonce | Output |
| hmacDRBG-pr/&lt;HASH&gt;| Output length, entropy, personalisation, ad1, entropy1, ad2, entropy2, nonce | Output |
| KDF-counter          | Number output bytes, PRF name, counter location string, key (or empty), number of counter bits | key, counter, derived key |
| KDF-feedback | Number output bytes, PRF name, counter location string, key (or empty), number of counter bits | key, counter, derived key |
| RSA/keyGen           | Modulus bit-size | e, p, q, n, d |
| RSA/sigGen/&lt;HASH&gt;/pkcs1v1.5 | Modulus bit-size, message | n, e, signature |
| RSA/sigGen/&lt;HASH&gt;/pss       | Modulus bit-size, message | n, e, signature |
| RSA/sigVer/&lt;HASH&gt;/pkcs1v1.5 | n, e, message, signature | Single-byte validity flag |
| RSA/sigVer/&lt;HASH&gt;/pss       | n, e, message, signature | Single-byte validity flag |
| SHA-1                | Value to hash             | Digest  |
| SHA2-224             | Value to hash             | Digest  |
| SHA2-256             | Value to hash             | Digest  |
| SHA2-384             | Value to hash             | Digest  |
| SHA2-512             | Value to hash             | Digest  |
| SHA2-512/224         | Value to hash             | Digest  |
| SHA2-512/256         | Value to hash             | Digest  |
| SHA3-224             | Value to hash             | Digest  |
| SHA3-256             | Value to hash             | Digest  |
| SHA3-384             | Value to hash             | Digest  |
| SHA3-512             | Value to hash             | Digest  |
| SHAKE-128            | Value to hash, output length bytes | Digest |
| SHAKE-128/VOT        | Value to hash, output length bytes | Digest |
| SHAKE-128/MCT        | Initial seed¹, min output bytes, max output bytes, output length bytes | Digest, output length bytes |
| SHAKE-256            | Value to hash, output length bytes | Digest |
| SHAKE-256/VOT        | Value to hash, output length bytes | Digest |
| SHAKE-256/MCT        | Initial seed¹, min output bytes, max output bytes, output length bytes | Digest, output length bytes |
| cSHAKE-128           | Value to hash, output length bytes, function name bytes, customization bytes | Digest |
| cSHAKE-128/MCT       | Initial seed¹, min output bytes, max output bytes, output length bytes, customization bytes | Digest, output length bytes, customization bytes |
| SHA-1/MCT            | Initial seed¹             | Digest  |
| SHA2-224/MCT         | Initial seed¹             | Digest  |
| SHA2-256/MCT         | Initial seed¹             | Digest  |
| SHA2-384/MCT         | Initial seed¹             | Digest  |
| SHA2-512/MCT         | Initial seed¹             | Digest  |
| SHA2-512/224/MCT     | Initial seed¹             | Digest  |
| SHA2-512/256/MCT     | Initial seed¹             | Digest  |
| SHA3-224/MCT         | Initial seed¹             | Digest  |
| SHA3-256/MCT         | Initial seed¹             | Digest  |
| SHA3-384/MCT         | Initial seed¹             | Digest  |
| SHA3-512/MCT         | Initial seed¹             | Digest  |
| TLSKDF/1.2/&lt;HASH&gt; | Number output bytes, secret, label, seed1, seed2 | Output |
| PBKDF                | HMAC name, key length (bits), salt, password, iteration count | Derived key |
| ML-DSA-XX/keyGen     | Seed | Public key, private key |
| ML-DSA-XX/sigGen     | Private key, message, randomizer | Signature |
| ML-DSA-XX/sigVer     | Public key, message, signature | Single-byte validity flag |
| ML-KEM-XX/keyGen     | Seed | Public key, private key |
| ML-KEM-XX/encap      | Public key, entropy | Ciphertext, shared secret |
| ML-KEM-XX/decap      | Private key, ciphertext | Shared secret |
| SLH-DSA-XX/keyGen    | Seed | Private key, public key |
| SLH-DSA-XX/sigGen    | Private key, message, entropy or empty | Signature |
| SLH-DSA-XX/sigVer    | Public key, message, signature | Single-byte validity flag |
| SSHKDF/&lt;HASH&gt;/client | K, H, SessionID, cipher algorithm | client IV key, client encryption key, client integrity key |
| SSHKDF/&lt;HASH&gt;/server | K, H, SessionID, cipher algorithm | server IV key, server encryption key, server integrity key |
| KTS-IFC/&lt;HASH&gt;/initiator | output length bytes, serverN bytes, serverE bytes | generated ciphertext (iutC), derived keying material (dkm) |
| KTS-IFC/&lt;HASH&gt;/responder | iutN bytes, iutE bytes, iutP bytes, iutQ bytes, iutD bytes, ciphertext (serverC) bytes | derived keying material (dkm) |
| OneStepNoCounter/&lt;HASH&gt; | key, info, salt, output length bytes | derived key |

¹ The iterated tests would result in excessive numbers of round trips if the module wrapper handled only basic operations. Thus some ACVP logic is pushed down for these tests so that the inner loop can be handled locally. Either read the NIST documentation ([block-ciphers](https://pages.nist.gov/ACVP/draft-celi-acvp-symmetric.html#name-monte-carlo-tests-for-block) [hashes](https://pages.nist.gov/ACVP/draft-celi-acvp-sha.html#name-monte-carlo-tests-for-sha-1)) to understand the iteration count and return values or, probably more fruitfully, see how these functions are handled in the `modulewrapper` directory.

² Will always be one because MCT tests are not supported for CS3.

### Batching

Requests are written without waiting for responses. Implementations can run a read-execute-reply loop without worrying about this. However, if batching is useful then implementations may gather up multiple requests before executing them. But this risks deadlock because some requests depend on the result of the previous one. If the `getConfig` result contains a dummy entry for the algorithm `acvptool` it will be filtered out when running with `-regcap`. However, a list of strings called `features` in that block may include the string `batch` to indicate that the implementation would like to receive a `flush` command whenever previous results must be received in order to progress. Implementations that batch can observe this to avoid deadlock.

The `flush` command must not produce a response itself; it only indicates that all previous responses must be received to progress. The `getConfig` command must always be serviced immediately because a flush command will not be sent prior to processing the `getConfig` response.

## Online operation

If you have credentials to speak to either of the NIST ACVP servers then you can run the tool in online mode.

Configuration is done via a `config.json` file in the current working directory. Here's a template:

```
{
        "ACVPServer": "https://demo.acvts.nist.gov/",
        "CertPEMFile": "certificate_from_nist.pem",
        "PrivateKeyFile": "your_private_key.key",
        "TOTPSecret": "<base64 from NIST goes here>",
        "SessionTokensCache": "~/.cache/acvp-session-tokens",
        "LogFile": "log"
}
```

NIST's ACVP servers use both TLS client certificates and TOTP for authentication. When registering with NIST they'll sign a CSR and return a certificate in PEM format, which is pointed to by `CertPEMFile`. The corresponding private key is expected in `PrivateKeyFile`. Lastly, NIST will provide a file that contains the base64-encoded TOTP seed, which must be pasted in as the value of `TOTPSecret`.

NIST's ACVP server provides special access tokens for each test session and test sessions can _only_ be accessed via those tokens. The reasoning behind this is unclear but this client can, optionally, keep records of these access tokens in the directory named by `SessionTokensCache`. If that directory name begins with `~/` then that prefix will be replaced with the value of `$HOME`.

Lastly, a log of all HTTP traffic will be written to the file named by `LogFile`, if provided. This is useful for debugging.

### Interactive Use

ACVP provides a fairly complex interface to a database of several types of objects. A rough UI is provided for this which is triggered when the client is invoked with no command-line arguments.

The simplest objects in ACVP are request objects. These record the status of requested changes to the database and, in practice, changes to the NIST demo database never succeed. The set of pending requests for the current user can be enumerated just by evaluating the `requests` object:

```
> requests
[
  {
    "url": "/acvp/v1/requests/374",
    "status": "processing"
  },
  {
    "url": "/acvp/v1/requests/218",
    "status": "processing"
  }
]
```

A specific request can be evaluated by using indexing syntax:

```
> requests[374]
{
  "url": "/acvp/v1/requests/374",
  "status": "processing"
}
```

The list of vendors provides a more complex example. Since there are large number of duplicates in NIST's database, there are more than 10 000 vendor objects and enumerating them all takes a long time. Thus evaluating the `vendors` object doesn't do that:

```
> vendors
[object set vendors]
```

It is still possible to use indexing syntax to read a specific vendor object if you know the ID:

```
> vendors[1234]
{
  "url": "/acvp/v1/vendors/1234",
  "name": "Apple Inc.",
  "website": "www.apple.com",
  "contactsUrl": "/acvp/v1/vendors/1234/contacts",
  "addresses": [
    {
      "url": "/acvp/v1/vendors/1234/addresses/1234",
      "street1": "1 Infinite Loop",
      "locality": "Cupertino",
      "region": "CA",
      "country": "USA",
      "postalCode": "95014"
    }
  ]
}
```

Finding a vendor when the ID is not known requires searching and the ACVP spec [documents](http://usnistgov.github.io/ACVP/artifacts/draft-fussell-acvp-spec-00.html#rfc.section.11.8.1), for each object type, what values and what relations can be searched on. This is reflected in a variant of the indexing syntax:

```
> vendors[where name contains "Google LLC"]
[
  {
    "url": "/acvp/v1/vendors/11136",
    "name": "Google LLC",
    "website": "www.google.com",
    "contactsUrl": "/acvp/v1/vendors/11136/contacts",
    "addresses": [
      {
        "url": "/acvp/v1/vendors/11136/addresses/11136",
        "street1": "1600 Amphitheatre Parkway",
        "locality": "Mountain View",
        "region": "CA",
        "country": "USA",
        "postalCode": "94043"
      }
    ]
  },
  {
    "url": "/acvp/v1/vendors/11137",
    "name": "Google LLC",
    "website": "www.google.com",
    "contactsUrl": "/acvp/v1/vendors/11137/contacts",
    "addresses": [
      {
        "url": "/acvp/v1/vendors/11137/addresses/11137",
        "street1": "1600 Amphitheatre Parkway",
        "locality": "Mountain View",
        "region": "CA",
        "country": "USA",
        "postalCode": "94043"
      }
    ]
  }
]
```

In general, `&&` and `||` can be used as in C and the relationships are `==`, `!=`, `contains`, `startsWith`, and `endsWith`. Only values and relations listed in the ACVP spec for a given object can be used.

More complex interaction remains to be fleshed out. However, it is generally possible to create new objects by evaluating, for example, `vendors.new()`. That will invoke `$EDITOR` to edit the JSON to be submitted. (For now, however, no helpful templates are provided.)

The current list of objects is:

* `requests`
* `vendors`
* `persons`
* `modules`
* `oes` (operating environments)
* `deps`
* `algos`
* `sessions`

### Running test sessions

In online mode, a given algorithm can be run by using the `-run` option. For example, `-run SHA2-256`. This will fetch a vector set, have the module-under-test answer it, and upload the answer. If you want to just fetch the vector set for later use with the `-json` option (documented above) then you can use `-fetch` instead of `-run`. The `-fetch` option also supports passing `-expected-out <filename>` to fetch and write the expected results, if the server supports that.

After results have been produced with `-json`, they can be uploaded with `-upload`. So `-run` is effectively these three steps combined:

```
./acvptool -fetch SHA2-256 > request
./acvptool -json request > result
./acvptool -upload result
```

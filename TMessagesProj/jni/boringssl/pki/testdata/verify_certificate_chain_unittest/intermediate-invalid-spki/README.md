This test verifies behavior when a certificate has an unparseable/unsupported
SPKI. It should be handled equivalently to a certificate with a failed
signature verification: further processing should be shortcircuited.
The certificate chain has 2 problems:
* leaf is expired
* intermediate has invalid SPKI

The verification should fail with only the SPKI parsing error, since further
processing should be short-circuited.

Instructions for generating test certificate chain:
* `cp ../expired-target/chain.pem .`
* extract intermediate cert to `int-pre.pem`
* `print_certificates --output=der2ascii int-pre.pem > int.derascii`
* edit `int.derascii` to replace SPKI OID with something invalid
* extract the TBSCertificate part of the certificate to `int.tbs.derascii`
  `ascii2der < int.tbs.derascii  > int.tbs.der`
* generate new signature: `openssl pkeyutl -sign -rawin -in int.tbs.der -digest sha256 -inkey ../expired-target/keys/Root.key -out - | xxd -p -c 0`
* replace the signature hex in `int.derascii`
* `ascii2der < int.derascii > int.der`
* `print_certificates --output=openssl_text,pem int.der > int.pem`
* replace the intermediate certificate in `chain.pem` with the contents of `int.pem`

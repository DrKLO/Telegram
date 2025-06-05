# NIST Public Key Interoperability Test Suite

This directory contains test data from the NIST [Public Key Interoperability Test Suite](http://csrc.nist.gov/groups/ST/crypto_apps_infra/pki/pkitesting.html) (NIST PKITS), version 1.0.1, fetched 2011-04-14. NIST PKITS test data is under public domain (United States Government Work under 17 U.S.C. 105). This directory is not included in BoringSSL when compiled and is only used for testing.

Only the `certs/` and `crls/` directories were extracted from `PKITS_data.zip`.

`pkits_testcases-inl.h` is generated from the test descriptions in `PKITS.pdf` using `generate_tests.py`.

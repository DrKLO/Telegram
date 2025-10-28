#!/bin/sh

set -xe

go run make_legacy_aead_tests.go -cipher aes128 -mac sha1 > aes_128_cbc_sha1_tls_tests.txt
go run make_legacy_aead_tests.go -cipher aes128 -mac sha1 -implicit-iv > aes_128_cbc_sha1_tls_implicit_iv_tests.txt

go run make_legacy_aead_tests.go -cipher aes256 -mac sha1 > aes_256_cbc_sha1_tls_tests.txt
go run make_legacy_aead_tests.go -cipher aes256 -mac sha1 -implicit-iv > aes_256_cbc_sha1_tls_implicit_iv_tests.txt

go run make_legacy_aead_tests.go -cipher 3des -mac sha1 > des_ede3_cbc_sha1_tls_tests.txt
go run make_legacy_aead_tests.go -cipher 3des -mac sha1 -implicit-iv > des_ede3_cbc_sha1_tls_implicit_iv_tests.txt

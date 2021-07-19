include_directories(../include)

if(NOT OPENSSL_NO_ASM)
  if(UNIX)
    if(${ARCH} STREQUAL "aarch64")
      # The "armx" Perl scripts look for "64" in the style argument
      # in order to decide whether to generate 32- or 64-bit asm.
      if(APPLE)
        set(PERLASM_STYLE ios64)
      else()
        set(PERLASM_STYLE linux64)
      endif()
    elseif(${ARCH} STREQUAL "arm")
      if(APPLE)
        set(PERLASM_STYLE ios32)
      else()
        set(PERLASM_STYLE linux32)
      endif()
    elseif(${ARCH} STREQUAL "ppc64le")
      set(PERLASM_STYLE linux64le)
    else()
      if(${ARCH} STREQUAL "x86")
        set(PERLASM_FLAGS "-fPIC -DOPENSSL_IA32_SSE2")
      endif()
      if(APPLE)
        set(PERLASM_STYLE macosx)
      else()
        set(PERLASM_STYLE elf)
      endif()
    endif()
    set(ASM_EXT S)
    enable_language(ASM)
    set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,--noexecstack")

    # Clang's integerated assembler does not support debug symbols.
    if(NOT CMAKE_ASM_COMPILER_ID MATCHES "Clang")
      set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -Wa,-g")
    endif()

    # CMake does not add -isysroot and -arch flags to assembly.
    if(APPLE)
      if(CMAKE_OSX_SYSROOT)
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -isysroot \"${CMAKE_OSX_SYSROOT}\"")
      endif()
      foreach(arch ${CMAKE_OSX_ARCHITECTURES})
        set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -arch ${arch}")
      endforeach()
    endif()
  else()
    if(${ARCH} STREQUAL "x86_64")
      set(PERLASM_STYLE nasm)
    else()
      set(PERLASM_STYLE win32n)
      set(PERLASM_FLAGS "-DOPENSSL_IA32_SSE2")
    endif()
    set(CMAKE_ASM_NASM_FLAGS "${CMAKE_ASM_NASM_FLAGS} -gcv8")

    # On Windows, we use the NASM output, specifically built with Yasm.
    set(ASM_EXT asm)
    enable_language(ASM_NASM)
  endif()
endif()

function(perlasm dest src)
  get_filename_component(dir ${dest} DIRECTORY)
  if ("${dir}" STREQUAL "")
    set(dir ".")
  endif()

  add_custom_command(
    OUTPUT ${dest}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${dir}
    COMMAND ${PERL_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/${src} ${PERLASM_STYLE} ${PERLASM_FLAGS} ${ARGN} ${dest}
    DEPENDS
    ${src}
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/arm-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/ppc-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86_64-xlate.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86asm.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86gas.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86masm.pl
    ${PROJECT_SOURCE_DIR}/crypto/perlasm/x86nasm.pl
    WORKING_DIRECTORY .
  )
endfunction()

add_subdirectory(fipsmodule)
add_subdirectory(test)

if(FIPS_DELOCATE OR FIPS_SHARED)
  SET_SOURCE_FILES_PROPERTIES(fipsmodule/bcm.o PROPERTIES EXTERNAL_OBJECT true)
  SET_SOURCE_FILES_PROPERTIES(fipsmodule/bcm.o PROPERTIES GENERATED true)

  set(
    CRYPTO_FIPS_OBJECTS

    fipsmodule/bcm.o
  )
endif()

if(${ARCH} STREQUAL "arm")
  set(
    CRYPTO_ARCH_SOURCES

    chacha/chacha-armv4.${ASM_EXT}
    curve25519/asm/x25519-asm-arm.S
    poly1305/poly1305_arm_asm.S
    test/trampoline-armv4.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "aarch64")
  set(
    CRYPTO_ARCH_SOURCES

    chacha/chacha-armv8.${ASM_EXT}
    test/trampoline-armv8.${ASM_EXT}
    third_party/sike/asm/fp-armv8.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "x86")
  set(
    CRYPTO_ARCH_SOURCES

    chacha/chacha-x86.${ASM_EXT}
    test/trampoline-x86.${ASM_EXT}
  )
endif()

if(${ARCH} STREQUAL "x86_64")
  set(
    CRYPTO_ARCH_SOURCES

    chacha/chacha-x86_64.${ASM_EXT}
    cipher_extra/aes128gcmsiv-x86_64.${ASM_EXT}
    cipher_extra/chacha20_poly1305_x86_64.${ASM_EXT}
    hrss/asm/poly_rq_mul.S
    test/trampoline-x86_64.${ASM_EXT}
    third_party/sike/asm/fp-x86_64.${ASM_EXT}
  )
endif()

perlasm(chacha/chacha-armv4.${ASM_EXT} chacha/asm/chacha-armv4.pl)
perlasm(chacha/chacha-armv8.${ASM_EXT} chacha/asm/chacha-armv8.pl)
perlasm(chacha/chacha-x86.${ASM_EXT} chacha/asm/chacha-x86.pl)
perlasm(chacha/chacha-x86_64.${ASM_EXT} chacha/asm/chacha-x86_64.pl)
perlasm(cipher_extra/aes128gcmsiv-x86_64.${ASM_EXT} cipher_extra/asm/aes128gcmsiv-x86_64.pl)
perlasm(cipher_extra/chacha20_poly1305_x86_64.${ASM_EXT} cipher_extra/asm/chacha20_poly1305_x86_64.pl)
perlasm(third_party/sike/asm/fp-x86_64.${ASM_EXT} ../third_party/sike/asm/fp-x86_64.pl)
perlasm(third_party/sike/asm/fp-armv8.${ASM_EXT} ../third_party/sike/asm/fp-armv8.pl)
perlasm(test/trampoline-armv4.${ASM_EXT} test/asm/trampoline-armv4.pl)
perlasm(test/trampoline-armv8.${ASM_EXT} test/asm/trampoline-armv8.pl)
perlasm(test/trampoline-x86.${ASM_EXT} test/asm/trampoline-x86.pl)
perlasm(test/trampoline-x86_64.${ASM_EXT} test/asm/trampoline-x86_64.pl)

add_custom_command(
  OUTPUT err_data.c
  COMMAND ${GO_EXECUTABLE} run err_data_generate.go > ${CMAKE_CURRENT_BINARY_DIR}/err_data.c
  DEPENDS
  err/err_data_generate.go
  err/asn1.errordata
  err/bio.errordata
  err/bn.errordata
  err/cipher.errordata
  err/conf.errordata
  err/dh.errordata
  err/digest.errordata
  err/dsa.errordata
  err/ecdh.errordata
  err/ecdsa.errordata
  err/ec.errordata
  err/engine.errordata
  err/evp.errordata
  err/hkdf.errordata
  err/obj.errordata
  err/pem.errordata
  err/pkcs7.errordata
  err/pkcs8.errordata
  err/rsa.errordata
  err/ssl.errordata
  err/x509.errordata
  err/x509v3.errordata
  WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/err
)

add_library(
  crypto

  asn1/a_bitstr.c
  asn1/a_bool.c
  asn1/a_d2i_fp.c
  asn1/a_dup.c
  asn1/a_enum.c
  asn1/a_gentm.c
  asn1/a_i2d_fp.c
  asn1/a_int.c
  asn1/a_mbstr.c
  asn1/a_object.c
  asn1/a_octet.c
  asn1/a_print.c
  asn1/a_strnid.c
  asn1/a_time.c
  asn1/a_type.c
  asn1/a_utctm.c
  asn1/a_utf8.c
  asn1/asn1_lib.c
  asn1/asn1_par.c
  asn1/asn_pack.c
  asn1/f_enum.c
  asn1/f_int.c
  asn1/f_string.c
  asn1/tasn_dec.c
  asn1/tasn_enc.c
  asn1/tasn_fre.c
  asn1/tasn_new.c
  asn1/tasn_typ.c
  asn1/tasn_utl.c
  asn1/time_support.c
  base64/base64.c
  bio/bio.c
  bio/bio_mem.c
  bio/connect.c
  bio/fd.c
  bio/file.c
  bio/hexdump.c
  bio/pair.c
  bio/printf.c
  bio/socket.c
  bio/socket_helper.c
  bn_extra/bn_asn1.c
  bn_extra/convert.c
  buf/buf.c
  bytestring/asn1_compat.c
  bytestring/ber.c
  bytestring/cbb.c
  bytestring/cbs.c
  bytestring/unicode.c
  chacha/chacha.c
  cipher_extra/cipher_extra.c
  cipher_extra/derive_key.c
  cipher_extra/e_aesccm.c
  cipher_extra/e_aesctrhmac.c
  cipher_extra/e_aesgcmsiv.c
  cipher_extra/e_chacha20poly1305.c
  cipher_extra/e_null.c
  cipher_extra/e_rc2.c
  cipher_extra/e_rc4.c
  cipher_extra/e_tls.c
  cipher_extra/tls_cbc.c
  cmac/cmac.c
  conf/conf.c
  cpu-aarch64-fuchsia.c
  cpu-aarch64-linux.c
  cpu-arm-linux.c
  cpu-arm.c
  cpu-intel.c
  cpu-ppc64le.c
  crypto.c
  curve25519/spake25519.c
  dh/dh.c
  dh/params.c
  dh/check.c
  dh/dh_asn1.c
  digest_extra/digest_extra.c
  dsa/dsa.c
  dsa/dsa_asn1.c
  ecdh_extra/ecdh_extra.c
  ecdsa_extra/ecdsa_asn1.c
  ec_extra/ec_asn1.c
  err/err.c
  err_data.c
  engine/engine.c
  evp/digestsign.c
  evp/evp.c
  evp/evp_asn1.c
  evp/evp_ctx.c
  evp/p_dsa_asn1.c
  evp/p_ec.c
  evp/p_ec_asn1.c
  evp/p_ed25519.c
  evp/p_ed25519_asn1.c
  evp/p_rsa.c
  evp/p_rsa_asn1.c
  evp/p_x25519.c
  evp/p_x25519_asn1.c
  evp/pbkdf.c
  evp/print.c
  evp/scrypt.c
  evp/sign.c
  ex_data.c
  hkdf/hkdf.c
  hrss/hrss.c
  lhash/lhash.c
  mem.c
  obj/obj.c
  obj/obj_xref.c
  pem/pem_all.c
  pem/pem_info.c
  pem/pem_lib.c
  pem/pem_oth.c
  pem/pem_pk8.c
  pem/pem_pkey.c
  pem/pem_x509.c
  pem/pem_xaux.c
  pkcs7/pkcs7.c
  pkcs7/pkcs7_x509.c
  pkcs8/pkcs8.c
  pkcs8/pkcs8_x509.c
  pkcs8/p5_pbev2.c
  poly1305/poly1305.c
  poly1305/poly1305_arm.c
  poly1305/poly1305_vec.c
  pool/pool.c
  rand_extra/deterministic.c
  rand_extra/forkunsafe.c
  rand_extra/fuchsia.c
  rand_extra/rand_extra.c
  rand_extra/windows.c
  rc4/rc4.c
  refcount_c11.c
  refcount_lock.c
  rsa_extra/rsa_asn1.c
  rsa_extra/rsa_print.c
  stack/stack.c
  siphash/siphash.c
  thread.c
  thread_none.c
  thread_pthread.c
  thread_win.c
  x509/a_digest.c
  x509/a_sign.c
  x509/a_strex.c
  x509/a_verify.c
  x509/algorithm.c
  x509/asn1_gen.c
  x509/by_dir.c
  x509/by_file.c
  x509/i2d_pr.c
  x509/rsa_pss.c
  x509/t_crl.c
  x509/t_req.c
  x509/t_x509.c
  x509/t_x509a.c
  x509/x509.c
  x509/x509_att.c
  x509/x509_cmp.c
  x509/x509_d2.c
  x509/x509_def.c
  x509/x509_ext.c
  x509/x509_lu.c
  x509/x509_obj.c
  x509/x509_r2x.c
  x509/x509_req.c
  x509/x509_set.c
  x509/x509_trs.c
  x509/x509_txt.c
  x509/x509_v3.c
  x509/x509_vfy.c
  x509/x509_vpm.c
  x509/x509cset.c
  x509/x509name.c
  x509/x509rset.c
  x509/x509spki.c
  x509/x_algor.c
  x509/x_all.c
  x509/x_attrib.c
  x509/x_crl.c
  x509/x_exten.c
  x509/x_info.c
  x509/x_name.c
  x509/x_pkey.c
  x509/x_pubkey.c
  x509/x_req.c
  x509/x_sig.c
  x509/x_spki.c
  x509/x_val.c
  x509/x_x509.c
  x509/x_x509a.c
  x509v3/pcy_cache.c
  x509v3/pcy_data.c
  x509v3/pcy_lib.c
  x509v3/pcy_map.c
  x509v3/pcy_node.c
  x509v3/pcy_tree.c
  x509v3/v3_akey.c
  x509v3/v3_akeya.c
  x509v3/v3_alt.c
  x509v3/v3_bcons.c
  x509v3/v3_bitst.c
  x509v3/v3_conf.c
  x509v3/v3_cpols.c
  x509v3/v3_crld.c
  x509v3/v3_enum.c
  x509v3/v3_extku.c
  x509v3/v3_genn.c
  x509v3/v3_ia5.c
  x509v3/v3_info.c
  x509v3/v3_int.c
  x509v3/v3_lib.c
  x509v3/v3_ncons.c
  x509v3/v3_ocsp.c
  x509v3/v3_pci.c
  x509v3/v3_pcia.c
  x509v3/v3_pcons.c
  x509v3/v3_pku.c
  x509v3/v3_pmaps.c
  x509v3/v3_prn.c
  x509v3/v3_purp.c
  x509v3/v3_skey.c
  x509v3/v3_sxnet.c
  x509v3/v3_utl.c
  ../third_party/fiat/curve25519.c
  ../third_party/sike/fpx.c
  ../third_party/sike/isogeny.c
  ../third_party/sike/curve_params.c
  ../third_party/sike/sike.c
  ../third_party/sike/asm/fp_generic.c

  $<TARGET_OBJECTS:fipsmodule>

  ${CRYPTO_ARCH_SOURCES}
  ${CRYPTO_FIPS_OBJECTS}
)

if(FIPS_SHARED)
  # Rewrite libcrypto.so to inject the correct module hash value. This assumes
  # UNIX-style library naming, but we only support FIPS mode on Linux anyway.
  add_custom_command(
    TARGET crypto POST_BUILD
    COMMAND ${GO_EXECUTABLE} run
    ${CMAKE_CURRENT_SOURCE_DIR}/../util/fipstools/inject_hash/inject_hash.go
    -o libcrypto.so -in-object libcrypto.so
    # The DEPENDS argument to a POST_BUILD rule appears to be ignored. Thus
    # go_executable isn't used (as it doesn't get built), but we list this
    # dependency anyway in case it starts working in some CMake version.
    DEPENDS ../util/fipstools/inject_hash/inject_hash.go
    WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
  )
endif()

add_dependencies(crypto global_target)

if(FIPS_DELOCATE OR FIPS_SHARED)
  add_dependencies(crypto bcm_o_target)
endif()

SET_TARGET_PROPERTIES(crypto PROPERTIES LINKER_LANGUAGE C)

if(NOT MSVC AND NOT ANDROID)
  target_link_libraries(crypto pthread)
endif()

# Every target depends on crypto, so we add libcxx as a dependency here to
# simplify injecting it everywhere.
if(USE_CUSTOM_LIBCXX)
  target_link_libraries(crypto libcxx)
endif()

add_executable(
  crypto_test

  abi_self_test.cc
  asn1/asn1_test.cc
  base64/base64_test.cc
  buf/buf_test.cc
  bio/bio_test.cc
  bytestring/bytestring_test.cc
  chacha/chacha_test.cc
  cipher_extra/aead_test.cc
  cipher_extra/cipher_test.cc
  cmac/cmac_test.cc
  compiler_test.cc
  constant_time_test.cc
  cpu-arm-linux_test.cc
  curve25519/ed25519_test.cc
  curve25519/spake25519_test.cc
  curve25519/x25519_test.cc
  ecdh_extra/ecdh_test.cc
  dh/dh_test.cc
  digest_extra/digest_test.cc
  dsa/dsa_test.cc
  err/err_test.cc
  evp/evp_extra_test.cc
  evp/evp_test.cc
  evp/pbkdf_test.cc
  evp/scrypt_test.cc
  fipsmodule/aes/aes_test.cc
  fipsmodule/bn/bn_test.cc
  fipsmodule/ec/ec_test.cc
  fipsmodule/ec/p256-x86_64_test.cc
  fipsmodule/ecdsa/ecdsa_test.cc
  fipsmodule/md5/md5_test.cc
  fipsmodule/modes/gcm_test.cc
  fipsmodule/rand/ctrdrbg_test.cc
  fipsmodule/sha/sha_test.cc
  hkdf/hkdf_test.cc
  hmac_extra/hmac_test.cc
  hrss/hrss_test.cc
  impl_dispatch_test.cc
  lhash/lhash_test.cc
  obj/obj_test.cc
  pem/pem_test.cc
  pkcs7/pkcs7_test.cc
  pkcs8/pkcs8_test.cc
  pkcs8/pkcs12_test.cc
  poly1305/poly1305_test.cc
  pool/pool_test.cc
  rand_extra/rand_test.cc
  refcount_test.cc
  rsa_extra/rsa_test.cc
  self_test.cc
  stack/stack_test.cc
  siphash/siphash_test.cc
  test/file_test_gtest.cc
  thread_test.cc
  x509/x509_test.cc
  x509/x509_time_test.cc
  x509v3/tab_test.cc
  x509v3/v3name_test.cc
  ../third_party/sike/sike_test.cc

  $<TARGET_OBJECTS:crypto_test_data>
  $<TARGET_OBJECTS:boringssl_gtest_main>
)

add_dependencies(crypto_test global_target)

target_link_libraries(crypto_test test_support_lib boringssl_gtest crypto)
if(WIN32)
  target_link_libraries(crypto_test ws2_32)
endif()
add_dependencies(all_tests crypto_test)

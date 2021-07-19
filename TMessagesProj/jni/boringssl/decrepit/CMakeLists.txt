include_directories(../include)

add_library(
  decrepit

  bio/base64_bio.c
  blowfish/blowfish.c
  cast/cast.c
  cast/cast_tables.c
  cfb/cfb.c
  des/cfb64ede.c
  dh/dh_decrepit.c
  dsa/dsa_decrepit.c
  evp/dss1.c
  evp/evp_do_all.c
  obj/obj_decrepit.c
  rc4/rc4_decrepit.c
  ripemd/ripemd.c
  rsa/rsa_decrepit.c
  ssl/ssl_decrepit.c
  x509/x509_decrepit.c
  xts/xts.c
)

add_dependencies(decrepit global_target)

target_link_libraries(decrepit crypto ssl)

add_executable(
  decrepit_test

  blowfish/blowfish_test.cc
  cast/cast_test.cc
  cfb/cfb_test.cc
  ripemd/ripemd_test.cc

  $<TARGET_OBJECTS:boringssl_gtest_main>
)

add_dependencies(decrepit_test global_target)

target_link_libraries(decrepit_test test_support_lib boringssl_gtest decrepit
                      crypto)
if(WIN32)
  target_link_libraries(decrepit_test ws2_32)
endif()
add_dependencies(all_tests decrepit_test)

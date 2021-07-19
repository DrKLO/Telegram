include_directories(../include)

add_executable(
  bssl

  args.cc
  ciphers.cc
  client.cc
  const.cc
  digest.cc
  file.cc
  generate_ed25519.cc
  genrsa.cc
  pkcs12.cc
  rand.cc
  server.cc
  sign.cc
  speed.cc
  tool.cc
  transport_common.cc
)

add_dependencies(bssl global_target)

if(APPLE OR WIN32 OR ANDROID)
  target_link_libraries(bssl ssl crypto)
else()
  find_library(FOUND_LIBRT rt)
  if(FOUND_LIBRT)
    target_link_libraries(bssl ssl crypto -lrt)
  else()
    target_link_libraries(bssl ssl crypto)
  endif()
endif()

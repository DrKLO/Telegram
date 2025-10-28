add_executable(
  test_fips

  test_fips.cc
)
target_link_libraries(test_fips crypto)

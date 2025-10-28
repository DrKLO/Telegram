if(FIPS)
  add_executable(
    modulewrapper

    main.cc
    modulewrapper.cc
  )
  target_link_libraries(modulewrapper crypto)
endif()

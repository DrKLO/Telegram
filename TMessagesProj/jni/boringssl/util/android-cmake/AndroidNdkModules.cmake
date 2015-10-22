# Copyright (c) 2014, Pavel Rojtberg
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
# this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
# this list of conditions and the following disclaimer in the documentation
# and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
# contributors may be used to endorse or promote products derived from this
# software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.

macro(android_ndk_import_module_cpufeatures)
    if(ANDROID)
        include_directories(${ANDROID_NDK}/sources/android/cpufeatures)
        add_library(cpufeatures ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c)
        target_link_libraries(cpufeatures dl)
    endif()
endmacro()

macro(android_ndk_import_module_native_app_glue)
    if(ANDROID)
        include_directories(${ANDROID_NDK}/sources/android/native_app_glue)
        add_library(native_app_glue ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c)
        target_link_libraries(native_app_glue log)
    endif()
endmacro()

macro(android_ndk_import_module_ndk_helper)
    if(ANDROID)
        android_ndk_import_module_cpufeatures()
        android_ndk_import_module_native_app_glue()
        
        include_directories(${ANDROID_NDK}/sources/android/ndk_helper)
        file(GLOB _NDK_HELPER_SRCS ${ANDROID_NDK}/sources/android/ndk_helper/*.cpp ${ANDROID_NDK}/sources/android/ndk_helper/gl3stub.c)
        add_library(ndk_helper ${_NDK_HELPER_SRCS})
        target_link_libraries(ndk_helper log android EGL GLESv2 cpufeatures native_app_glue)
        
        unset(_NDK_HELPER_SRCS)
    endif()
endmacro()
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE:= libwebm
LOCAL_CPPFLAGS:=-D__STDC_CONSTANT_MACROS -D__STDC_FORMAT_MACROS
LOCAL_CPPFLAGS+=-D__STDC_LIMIT_MACROS -std=c++11
LOCAL_C_INCLUDES:= $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES:= $(LOCAL_PATH)

LOCAL_SRC_FILES:= common/file_util.cc \
                  common/hdr_util.cc \
                  mkvparser/mkvparser.cc \
                  mkvparser/mkvreader.cc \
                  mkvmuxer/mkvmuxer.cc \
                  mkvmuxer/mkvmuxerutil.cc \
                  mkvmuxer/mkvwriter.cc
include $(BUILD_STATIC_LIBRARY)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
include $(OPENCV_PACKAGE_DIR)/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := run_text_detection
LOCAL_SRC_FILES := text_detect.cpp android.cpp
LOCAL_LDLIBS    += -landroid -llog -ldl

include $(BUILD_SHARED_LIBRARY)






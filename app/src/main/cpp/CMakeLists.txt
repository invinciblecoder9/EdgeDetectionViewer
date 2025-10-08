cmake_minimum_required(VERSION 3.22.1)
project("edgedetectionviewer")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ABI-specific optimization flags
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(ARCH_FLAGS "-mfpu=neon -mfloat-abi=softfp")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(ARCH_FLAGS "-march=armv8-a+simd -mfpu=neon")
else()
    set(ARCH_FLAGS "")
endif()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Ofast -ffast-math -funroll-loops ${ARCH_FLAGS} -flto")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -s")

# --- OpenCV ---
set(OpenCV_INCLUDE_DIR "C:/Users/Suryansh Ayush/Downloads/opencv-4.8.0-android-sdk/OpenCV-android-sdk/sdk/native/jni/include")
set(OpenCV_LIB_DIR "C:/Users/Suryansh Ayush/Downloads/opencv-4.8.0-android-sdk/OpenCV-android-sdk/sdk/native/libs")

# Add library manually (no need for find_package)
add_library(opencv_java4 SHARED IMPORTED)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION ${OpenCV_LIB_DIR}/${ANDROID_ABI}/libopencv_java4.so
)

# --- Native library ---
add_library(
        native-lib
        SHARED
        native-lib.cpp
)

# --- Include directories ---
target_include_directories(
        native-lib
        PRIVATE
        ${OpenCV_INCLUDE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# --- Link libraries ---
target_link_libraries(
        native-lib
        opencv_java4
        android
        log
        jnigraphics
        c++_shared
)

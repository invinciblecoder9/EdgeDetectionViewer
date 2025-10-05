cmake_minimum_required(VERSION 3.22.1)

project("edgedetectionviewer")

# Set C++ standard (matches build.gradle.kts)
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set OpenCV directory (absolute path to your SDK)
set(OpenCV_DIR "C:/Users/Suryansh Ayush/Downloads/opencv-4.8.0-android-sdk/OpenCV-android-sdk/sdk/native/jni")

# Find OpenCV package
find_package(OpenCV REQUIRED)
message(STATUS "OpenCV version: ${OpenCV_VERSION}")
message(STATUS "OpenCV include dirs: ${OpenCV_INCLUDE_DIRS}")
message(STATUS "OpenCV libs: ${OpenCV_LIBS}")

# Add library with your native source files
add_library(
        native-lib
        SHARED
        native-lib.cpp
)

# Include directories (project + OpenCV)
target_include_directories(
        native-lib
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${OpenCV_INCLUDE_DIRS}
)

# Link libraries (OpenCV first, then Android essentials)
target_link_libraries(
        native-lib
        ${OpenCV_LIBS}      # OpenCV core + imgproc
        android             # Android NDK
        log                 # Android logging
        jnigraphics         # JNI Bitmap access
        c++_shared          # C++ STL (matches Gradle)
)
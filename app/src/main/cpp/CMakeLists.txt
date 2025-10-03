cmake_minimum_required(VERSION 3.22.1)

project("edgedetectionviewer")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set OpenCV directory
set(OPENCV_DIR ${CMAKE_SOURCE_DIR}/../../../../../opencv/sdk/native/jni)

# Find OpenCV package
find_package(OpenCV REQUIRED)

# Include directories
include_directories(
        ${CMAKE_SOURCE_DIR}
        ${OpenCV_INCLUDE_DIRS}
)

# Add native source files
add_library(
        native-processor
        SHARED
        native-lib.cpp
        EdgeDetector.cpp
)

# Link libraries
target_link_libraries(
        native-processor
        ${OpenCV_LIBS}
        android
        log
        jnigraphics
        GLESv2
        EGL
)

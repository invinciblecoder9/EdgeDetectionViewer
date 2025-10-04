cmake_minimum_required(VERSION 3.22.1)

project("edgedetectionviewer")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Set OpenCV directory
set(OpenCV_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencv/sdk/native/jni)

# Find OpenCV package
find_package(OpenCV REQUIRED)

message(STATUS "OpenCV found: ${OpenCV_VERSION}")
message(STATUS "OpenCV libs: ${OpenCV_LIBS}")

# Include directories
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${OpenCV_INCLUDE_DIRS}
)

# Add library with your native source files
add_library(
        native-processor
        SHARED
        native-lib.cpp
        EdgeDetector.cpp
)

# IMPORTANT: Link all required libraries in correct order
target_link_libraries(
        native-processor
        ${OpenCV_LIBS}      # OpenCV libraries first
        android             # Android system
        log                 # Android logging
        jnigraphics         # Bitmap access
        GLESv2              # OpenGL ES 2.0
        EGL                 # EGL for OpenGL
        c++_shared          # C++ standard library (CRITICAL!)
)

# Set C++ features
target_compile_features(native-processor PUBLIC cxx_std_17)

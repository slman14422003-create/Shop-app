cmake_minimum_required(VERSION 3.22.1)
project(shopwhisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/CMakeLists.txt")
    add_subdirectory(whisper.cpp ${CMAKE_BINARY_DIR}/whisper_build)
else()
    include(FetchContent)
    FetchContent_Declare(
        whisper_src
        GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
        GIT_TAG v1.7.6
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(whisper_src)
endif()

add_library(shopwhisper SHARED whisper_jni.cpp)
target_link_libraries(shopwhisper PRIVATE whisper log)
target_link_options(shopwhisper PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections)

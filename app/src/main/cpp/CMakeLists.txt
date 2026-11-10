# The optional on-phone AI (0.18.0): llama.cpp, fetched at a pinned version (the same as
# tools/ai-check/models.sh), with the app's own code (fitness_ai.cpp) and its link to Kotlin
# (fitness_ai_jni.cpp). Built for 64-bit ARM phones only; elsewhere the AI parts say they can't run.
# Each CPU variant of ggml is its own library, picked when the app starts, so older phones work too.
cmake_minimum_required(VERSION 3.22.1)
project(fitness_ai C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
set(LLAMA_TAG b11900)
set(LLAMA_COMMIT 74b104c9ba5717116b7e65e0cd35397778653304)
FetchContent_Declare(llama GIT_REPOSITORY https://github.com/ggml-org/llama.cpp GIT_TAG ${LLAMA_TAG} GIT_SHALLOW TRUE)

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(llama)

execute_process(COMMAND git rev-parse HEAD WORKING_DIRECTORY ${llama_SOURCE_DIR} OUTPUT_VARIABLE got OUTPUT_STRIP_TRAILING_WHITESPACE)
if (NOT got STREQUAL LLAMA_COMMIT)
    message(FATAL_ERROR "llama.cpp ${LLAMA_TAG} is ${got}, expected ${LLAMA_COMMIT}")
endif()

add_library(fitness_ai SHARED fitness_ai.cpp fitness_ai_jni.cpp)
target_include_directories(fitness_ai PRIVATE ${llama_SOURCE_DIR}/tools/mtmd)
target_link_libraries(fitness_ai PRIVATE llama mtmd log)

cmake_minimum_required(VERSION 3.22.1)
project(birlevo_mp3 LANGUAGES C)

# Offline, pinned upstream archive. Only the encoder is compiled: no mpglib/GPL decoder.
set(LAME_ARCHIVE "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/lame/lame-4.0.tar.gz")
file(SHA256 "${LAME_ARCHIVE}" LAME_SHA)
if(NOT LAME_SHA STREQUAL "3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb")
    message(FATAL_ERROR "Unexpected LAME source archive")
endif()
file(ARCHIVE_EXTRACT INPUT "${LAME_ARCHIVE}" DESTINATION "${CMAKE_CURRENT_BINARY_DIR}/upstream")
set(LAME_ROOT "${CMAKE_CURRENT_BINARY_DIR}/upstream/lame-4.0")
configure_file(config.h.in "${CMAKE_CURRENT_BINARY_DIR}/config.h")
set(LAME_SOURCES VbrTag bitstream encoder fft gain_analysis id3tag lame newmdct
    presets psymodel quantize quantize_pvt reservoir set_get tables takehiro util
    vbrquantize version)
list(TRANSFORM LAME_SOURCES PREPEND "${LAME_ROOT}/libmp3lame/")
list(TRANSFORM LAME_SOURCES APPEND ".c")
add_library(mp3lame SHARED ${LAME_SOURCES})
target_include_directories(mp3lame PRIVATE "${CMAKE_CURRENT_BINARY_DIR}" "${LAME_ROOT}/include" "${LAME_ROOT}/libmp3lame")
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H=1)
target_compile_options(mp3lame PRIVATE -O2)
target_link_libraries(mp3lame PRIVATE m)

# Separate replaceable shared encoder, linked dynamically by the small JNI bridge.
add_library(birlevo_mp3 SHARED lame_bridge.c)
target_include_directories(birlevo_mp3 PRIVATE "${LAME_ROOT}/include")
target_compile_options(birlevo_mp3 PRIVATE -Wall -Wextra -Werror -O2)
target_link_libraries(birlevo_mp3 PRIVATE mp3lame)
foreach(target mp3lame birlevo_mp3)
    target_link_options(${target} PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384" "-Wl,-z,relro" "-Wl,-z,now")
endforeach()

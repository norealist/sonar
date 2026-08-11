cmake_minimum_required(VERSION 3.22)

project(sonar_core_native LANGUAGES C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build decoder dependencies as static libraries" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(SONAR_CORE_SOURCES
    buffer/ring_buffer.cpp
    decoder/decoder_factory.cpp
    decoder/flac_decoder.cpp
    decoder/mp3_decoder.cpp
    decoder/opus_decoder.cpp
    decoder/vorbis_decoder.cpp
    decoder/wav_decoder.cpp
    engine/playback_engine.cpp
    format/format_converter.cpp
)

if(ANDROID)
    list(APPEND SONAR_CORE_SOURCES jni/native_bridge.cpp)
endif()

if(ANDROID)
    add_library(sonar_core SHARED ${SONAR_CORE_SOURCES})
else()
    add_library(sonar_core STATIC ${SONAR_CORE_SOURCES})
endif()

target_include_directories(sonar_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(sonar_core PUBLIC cxx_std_17)
target_compile_definitions(sonar_core PRIVATE SONAR_CORE_BUILD=1)

if(MSVC)
    target_compile_definitions(sonar_core PRIVATE _CRT_SECURE_NO_WARNINGS)
    target_compile_options(sonar_core PRIVATE /W4)
else()
    target_compile_options(sonar_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

# Third-party code is optional. A normal host checkout therefore still builds
# WAV, the engine, and the factory without downloading or requiring submodules.
set(SONAR_THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
# minimp3/minimp3.h and minimp3_ex.h are copied from lieff/minimp3 revision
# ea99364f61c14656440e8d77e9c233ccf3124633 (header-only dependency).
if(EXISTS ${SONAR_THIRD_PARTY}/minimp3/minimp3_ex.h)
    target_include_directories(sonar_core PRIVATE ${SONAR_THIRD_PARTY}/minimp3)
    target_compile_definitions(sonar_core PRIVATE SONAR_HAS_MINIMP3=1)
endif()

if(EXISTS ${SONAR_THIRD_PARTY}/ogg/CMakeLists.txt)
    set(BUILD_TESTING OFF CACHE BOOL "Disable Ogg tests" FORCE)
    set(INSTALL_DOCS OFF CACHE BOOL "Disable Ogg documentation" FORCE)
    set(INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "Disable Ogg pkg-config" FORCE)
    set(INSTALL_CMAKE_PACKAGE_MODULE OFF CACHE BOOL "Disable Ogg CMake package" FORCE)
    add_subdirectory(${SONAR_THIRD_PARTY}/ogg ${CMAKE_CURRENT_BINARY_DIR}/third_party/ogg EXCLUDE_FROM_ALL)
endif()
if(EXISTS ${SONAR_THIRD_PARTY}/opus/CMakeLists.txt)
    set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "Build Opus statically" FORCE)
    set(OPUS_BUILD_TESTING OFF CACHE BOOL "Disable Opus tests" FORCE)
    set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "Disable Opus programs" FORCE)
    set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "Disable Opus pkg-config" FORCE)
    set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "Disable Opus CMake package" FORCE)
    add_subdirectory(${SONAR_THIRD_PARTY}/opus ${CMAKE_CURRENT_BINARY_DIR}/third_party/opus EXCLUDE_FROM_ALL)
endif()
if(EXISTS ${SONAR_THIRD_PARTY}/opusfile/src/opusfile.c AND TARGET opus AND TARGET ogg)
    # Opusfile v0.12 ships autotools files, so build only its local-file
    # decoder sources here. Omitting http.c/wincerts.c deliberately disables
    # URL support and keeps the native core local-file-only.
    add_library(opusfile STATIC
        ${SONAR_THIRD_PARTY}/opusfile/src/info.c
        ${SONAR_THIRD_PARTY}/opusfile/src/internal.c
        ${SONAR_THIRD_PARTY}/opusfile/src/opusfile.c
        ${SONAR_THIRD_PARTY}/opusfile/src/stream.c
    )
    target_include_directories(opusfile PUBLIC
        ${SONAR_THIRD_PARTY}/opusfile/include
        ${SONAR_THIRD_PARTY}/opusfile/src
    )
    target_link_libraries(opusfile PUBLIC opus ogg)
endif()
if(EXISTS ${SONAR_THIRD_PARTY}/vorbis/lib/vorbisfile.c AND TARGET ogg)
    # Build only the Vorbis decoder and vorbisfile wrapper. The encoder target
    # is intentionally omitted from the Android library.
    add_library(vorbis STATIC
        ${SONAR_THIRD_PARTY}/vorbis/lib/analysis.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/bitrate.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/block.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/codebook.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/envelope.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/floor0.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/floor1.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/info.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/lookup.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/lpc.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/lsp.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/mapping0.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/mdct.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/psy.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/registry.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/res0.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/sharedbook.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/smallft.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/synthesis.c
        ${SONAR_THIRD_PARTY}/vorbis/lib/window.c
    )
    target_include_directories(vorbis PUBLIC
        ${SONAR_THIRD_PARTY}/vorbis/include
        ${SONAR_THIRD_PARTY}/vorbis/lib
    )
    target_link_libraries(vorbis PUBLIC ogg)
    add_library(vorbisfile STATIC ${SONAR_THIRD_PARTY}/vorbis/lib/vorbisfile.c)
    target_include_directories(vorbisfile PUBLIC ${SONAR_THIRD_PARTY}/vorbis/include)
    target_link_libraries(vorbisfile PUBLIC vorbis)
endif()
if(EXISTS ${SONAR_THIRD_PARTY}/flac/CMakeLists.txt)
    set(BUILD_CXXLIBS OFF CACHE BOOL "Disable FLAC++" FORCE)
    set(BUILD_PROGRAMS OFF CACHE BOOL "Disable FLAC programs" FORCE)
    set(BUILD_EXAMPLES OFF CACHE BOOL "Disable FLAC examples" FORCE)
    set(BUILD_TESTING OFF CACHE BOOL "Disable FLAC tests" FORCE)
    set(BUILD_DOCS OFF CACHE BOOL "Disable FLAC documentation" FORCE)
    set(INSTALL_MANPAGES OFF CACHE BOOL "Disable FLAC manpages" FORCE)
    set(INSTALL_PKGCONFIG_MODULES OFF CACHE BOOL "Disable FLAC pkg-config" FORCE)
    set(INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "Disable FLAC CMake package" FORCE)
    add_subdirectory(${SONAR_THIRD_PARTY}/flac ${CMAKE_CURRENT_BINARY_DIR}/third_party/flac EXCLUDE_FROM_ALL)
    if(TARGET FLAC)
        if(ANDROID)
            # Android API 24+ provides fseeko/ftello, but FLAC's configure
            # probe does not detect them when cross-compiling with the NDK.
            target_compile_definitions(FLAC PRIVATE HAVE_FSEEKO=)
        endif()
        # libFLAC's upstream target contains encoder sources. Remove them from
        # the target after configuration to keep the shipped native library
        # decoder-only while retaining upstream's maintained decoder code.
        get_target_property(_flac_sources FLAC SOURCES)
        list(FILTER _flac_sources EXCLUDE REGEX "stream_encoder|ogg_encoder_aspect")
        set_property(TARGET FLAC PROPERTY SOURCES "${_flac_sources}")
    endif()
endif()

if(TARGET opusfile)
    target_compile_definitions(sonar_core PRIVATE SONAR_HAS_OPUSFILE=1)
    target_link_libraries(sonar_core PRIVATE opusfile)
    if(TARGET opus)
        target_link_libraries(sonar_core PRIVATE opus)
    endif()
    if(TARGET ogg)
        target_link_libraries(sonar_core PRIVATE ogg)
    endif()
elseif(EXISTS ${SONAR_THIRD_PARTY}/opusfile/include/opus/opusfile.h)
    message(STATUS "opusfile headers found without a CMake target; Opus decoder disabled")
endif()
if(TARGET vorbisfile)
    target_compile_definitions(sonar_core PRIVATE SONAR_HAS_VORBIS=1)
    target_link_libraries(sonar_core PRIVATE vorbisfile vorbis)
endif()
if(TARGET FLAC::FLAC)
    target_compile_definitions(sonar_core PRIVATE SONAR_HAS_FLAC=1)
    target_link_libraries(sonar_core PRIVATE FLAC::FLAC)
elseif(TARGET FLAC)
    target_compile_definitions(sonar_core PRIVATE SONAR_HAS_FLAC=1)
    target_link_libraries(sonar_core PRIVATE FLAC)
endif()

if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(sonar_core PRIVATE ${log-lib})
endif()

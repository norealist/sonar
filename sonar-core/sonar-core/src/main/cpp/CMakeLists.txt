cmake_minimum_required(VERSION 3.22)
project(sonar_core_module LANGUAGES C CXX)

# Keep the pre-existing native source tree authoritative while exposing the
# conventional module-local CMake entry point to Android Gradle Plugin.
add_subdirectory(
    "${CMAKE_CURRENT_LIST_DIR}/../../../../src/main/cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/sonar-core-native"
)

cmake_minimum_required(VERSION 3.22)
project(sonar_core_native_tests LANGUAGES CXX)

enable_testing()
find_package(Threads REQUIRED)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../src/main/cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/sonar_core)

find_package(GTest QUIET)
if(GTest_FOUND)
    add_executable(sonar_core_native_tests native_smoke_tests.cpp)
    target_compile_definitions(sonar_core_native_tests PRIVATE SONAR_HAS_GTEST=1)
    target_link_libraries(sonar_core_native_tests PRIVATE sonar_core GTest::gtest_main Threads::Threads)
    include(GoogleTest)
    gtest_discover_tests(sonar_core_native_tests)
else()
    add_executable(sonar_core_native_smoke native_smoke_tests.cpp)
    target_link_libraries(sonar_core_native_smoke PRIVATE sonar_core Threads::Threads)
    add_test(NAME sonar_core_native_smoke COMMAND sonar_core_native_smoke)
endif()

set_property(TARGET sonar_core PROPERTY CXX_STANDARD 17)

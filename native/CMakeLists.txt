cmake_minimum_required(VERSION 3.22.1)
project(bbplus_bepinex_android CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory(main)
add_subdirectory(nextbep)

set_target_properties(main nextbep PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/out")

# Based on this gRPC cmake example
# https://github.com/grpc/grpc/blob/v1.19.0/examples/cpp/helloworld/CMakeLists.txt

cmake_minimum_required(VERSION 2.8)

project(autheid_sample C CXX)

if(NOT MSVC)
  set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
else()
  add_definitions(-D_WIN32_WINNT=0x600)
endif()

set(AUTHEID_API ${CMAKE_CURRENT_SOURCE_DIR}/../AuthAPI)

add_subdirectory(grpc ${CMAKE_CURRENT_BINARY_DIR}/grpc EXCLUDE_FROM_ALL)
message(STATUS "Using gRPC via add_subdirectory.")

set(_PROTOBUF_LIBPROTOBUF libprotobuf)
set(_PROTOBUF_PROTOC $<TARGET_FILE:protoc>)
set(_GRPC_GRPCPP grpc++)
set(_GRPC_CPP_PLUGIN_EXECUTABLE $<TARGET_FILE:grpc_cpp_plugin>)

# Proto file
get_filename_component(RP_PROTO "${AUTHEID_API}/proto/rp.proto" ABSOLUTE)
get_filename_component(RP_PROTO_PATH "${RP_PROTO}" PATH)

# Generated sources
set(RP_PROTO_SRCS "${CMAKE_CURRENT_BINARY_DIR}/rp.pb.cc")
set(RP_PROTO_HDRS "${CMAKE_CURRENT_BINARY_DIR}/rp.pb.h")
set(RP_GRPC_SRCS "${CMAKE_CURRENT_BINARY_DIR}/rp.grpc.pb.cc")
set(RP_GRPC_HDRS "${CMAKE_CURRENT_BINARY_DIR}/rp.grpc.pb.h")
add_custom_command(
      OUTPUT "${RP_PROTO_SRCS}" "${RP_PROTO_HDRS}" "${RP_GRPC_SRCS}" "${RP_GRPC_HDRS}"
      COMMAND ${_PROTOBUF_PROTOC}
      ARGS --grpc_out "${CMAKE_CURRENT_BINARY_DIR}"
        --cpp_out "${CMAKE_CURRENT_BINARY_DIR}"
        -I "${RP_PROTO_PATH}"
        --plugin=protoc-gen-grpc="${_GRPC_CPP_PLUGIN_EXECUTABLE}"
        "${RP_PROTO}"
      DEPENDS "${RP_PROTO}"
)

# Include generated *.pb.h files
include_directories("${CMAKE_CURRENT_BINARY_DIR}")
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/botan)
include_directories(${AUTHEID_API}/utils/cpp)

add_executable(autheid_sample
    ${CMAKE_CURRENT_SOURCE_DIR}/main.cpp
    ${AUTHEID_API}/utils/cpp/autheid_utils.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/botan/botan_all.cpp
    ${RP_PROTO_SRCS}
    ${RP_PROTO_HDRS}
    ${RP_GRPC_SRCS}
    ${RP_GRPC_HDRS}
)

target_link_libraries(autheid_sample
    ${_GRPC_GRPCPP}
    ${_PROTOBUF_LIBPROTOBUF}
)

target_compile_definitions(autheid_sample PRIVATE -DAUTHEID_BOTAN_AMALGAMATION)

cmake_minimum_required(VERSION 3.22.1)

project(gtcrash)

add_library(gtcrash SHARED crash_handler.cpp)

target_link_libraries(gtcrash log)

cmake_minimum_required(VERSION 3.22.1)
project(oritwig_motion LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 14)
set(UPSTREAM ${CMAKE_CURRENT_LIST_DIR}/../../../../vendor/rlottie)
# The exact historical Telegram rlottie source list, compiled as a separate shared library.
add_library(rlottie SHARED
${UPSTREAM}/src/lottie/lottieanimation.cpp
        ${UPSTREAM}/src/lottie/lottieitem.cpp
        ${UPSTREAM}/src/lottie/lottiekeypath.cpp
        ${UPSTREAM}/src/lottie/lottieloader.cpp
        ${UPSTREAM}/src/lottie/lottiemodel.cpp
        ${UPSTREAM}/src/lottie/lottieparser.cpp
        ${UPSTREAM}/src/lottie/lottieproxymodel.cpp
        ${UPSTREAM}/src/vector/freetype/v_ft_math.cpp
        ${UPSTREAM}/src/vector/freetype/v_ft_raster.cpp
        ${UPSTREAM}/src/vector/freetype/v_ft_stroker.cpp
        ${UPSTREAM}/src/vector/pixman/vregion.cpp
        ${UPSTREAM}/src/vector/stb/stb_image.cpp
        ${UPSTREAM}/src/vector/vbezier.cpp
        ${UPSTREAM}/src/vector/vbitmap.cpp
        ${UPSTREAM}/src/vector/vbrush.cpp
        ${UPSTREAM}/src/vector/vcompositionfunctions.cpp
        ${UPSTREAM}/src/vector/vdasher.cpp
        ${UPSTREAM}/src/vector/vdebug.cpp
        ${UPSTREAM}/src/vector/vdrawable.cpp
        ${UPSTREAM}/src/vector/vdrawhelper.cpp
        ${UPSTREAM}/src/vector/vdrawhelper_neon.cpp
        ${UPSTREAM}/src/vector/velapsedtimer.cpp
        ${UPSTREAM}/src/vector/vimageloader.cpp
        ${UPSTREAM}/src/vector/vinterpolator.cpp
        ${UPSTREAM}/src/vector/vmatrix.cpp
        ${UPSTREAM}/src/vector/vpainter.cpp
        ${UPSTREAM}/src/vector/vpath.cpp
        ${UPSTREAM}/src/vector/vpathmesure.cpp
        ${UPSTREAM}/src/vector/vraster.cpp
        ${UPSTREAM}/src/vector/vrect.cpp
        ${UPSTREAM}/src/vector/vrle.cpp)
target_compile_options(rlottie PRIVATE -Wall -fno-rtti -finline-functions -ffast-math -Os -Wno-unused-parameter -fvisibility=hidden)
target_compile_definitions(rlottie PRIVATE HAVE_PTHREAD NDEBUG)
target_include_directories(rlottie PUBLIC ${UPSTREAM}/inc PRIVATE ${UPSTREAM}/src/vector ${UPSTREAM}/src/vector/pixman ${UPSTREAM}/src/vector/freetype ${UPSTREAM}/src/vector/stb)
# Portable upstream scalar path, including arm64. No renderer changes.
if(ANDROID)
 target_link_libraries(rlottie PRIVATE log dl)
 add_library(oritwig_motion_jni SHARED motion_jni.cpp)
 target_compile_options(oritwig_motion_jni PRIVATE -fvisibility=hidden)
 target_link_libraries(oritwig_motion_jni PRIVATE rlottie jnigraphics log)
 target_link_options(rlottie PRIVATE "-Wl,-z,max-page-size=16384")
 target_link_options(oritwig_motion_jni PRIVATE "-Wl,-z,max-page-size=16384")
else()
 target_compile_options(rlottie PRIVATE "SHELL:-include climits" "SHELL:-include limits") # Host GCC needs the standard limit declarations transitively provided on Android.
 find_package(Threads REQUIRED)
 target_link_libraries(rlottie PRIVATE Threads::Threads ${CMAKE_DL_LIBS})
 add_executable(motion_native_test ../../../../tools/native-test.cpp)
 target_compile_options(motion_native_test PRIVATE -UNDEBUG)
 target_link_libraries(motion_native_test PRIVATE rlottie)
endif()

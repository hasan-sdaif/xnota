# Two shared libraries from sources vendored at pinned commits (see THIRD_PARTY);
# nothing is fetched at build time, keeping the F-Droid build reproducible.
cmake_minimum_required(VERSION 3.22.1)
project(xnotats C CXX)

# No build-id note: it hashes the pre-strip output (build paths included), so it
# varies across build hosts and breaks F-Droid's reproducible-build verification.
# max-page-size=16384 keeps the libs loadable on 16 KB page-size devices.
set(XNOTES_LINK_OPTIONS -Wl,--gc-sections -Wl,--build-id=none -Wl,-z,max-page-size=16384)

set(GRAMMAR_SOURCES
    grammars/bash/parser.c
    grammars/bash/scanner.c
    grammars/c/parser.c
    grammars/cpp/parser.c
    grammars/cpp/scanner.c
    grammars/java/parser.c
    grammars/javascript/parser.c
    grammars/javascript/scanner.c
    grammars/json/parser.c
    grammars/kotlin/parser.c
    grammars/kotlin/scanner.c
    grammars/python/parser.c
    grammars/python/scanner.c
)

# libxnotats: the tree-sitter runtime, the bundled grammars and a thin JNI bridge.
add_library(xnotats SHARED
    tree-sitter/lib/src/lib.c
    ${GRAMMAR_SOURCES}
    ts_jni.c
)
set_target_properties(xnotats PROPERTIES C_STANDARD 11)
# Applied to every build type (AGP release uses RelWithDebInfo, not Release).
target_compile_options(xnotats PRIVATE
    -Os -fvisibility=hidden -ffunction-sections -fdata-sections)

target_include_directories(xnotats PRIVATE
    tree-sitter/lib/include
    tree-sitter/lib/src
)

# Each grammar carries its own tree_sitter/parser.h; scope it to that grammar's
# sources so the copies never collide.
foreach(src ${GRAMMAR_SOURCES})
    get_filename_component(dir ${src} DIRECTORY)
    set_source_files_properties(${src} PROPERTIES
        INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/${dir}")
endforeach()

target_link_options(xnotats PRIVATE ${XNOTES_LINK_OPTIONS})

# libxnotapdf: PDFium and its JNI bridge. pdfium.cmake (tools/pdfium-sync.sh)
# gives each PDFium library upstream's own flags, so none set here leak in.
set(PDFIUM_DIR ${CMAKE_CURRENT_SOURCE_DIR}/pdfium)
include(pdfium.cmake)

add_library(xnotapdf SHARED pdf_jni.cpp)
target_include_directories(xnotapdf PRIVATE ${PDFIUM_DIR})
target_compile_definitions(xnotapdf PRIVATE XNOTES_PDFIUM_REVISION="${PDFIUM_REVISION}")
target_compile_options(xnotapdf PRIVATE -std=c++20 -fno-exceptions -fno-rtti -Os
    -fvisibility=hidden -ffunction-sections -fdata-sections
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}/=")
target_link_libraries(xnotapdf PRIVATE ${PDFIUM_LIBRARIES} jnigraphics log)
# Only the JNI entry points are exported; FreeType would export its whole API.
set(XNOTAPDF_EXPORTS ${CMAKE_CURRENT_SOURCE_DIR}/pdf_jni.map)
target_link_options(xnotapdf PRIVATE ${XNOTES_LINK_OPTIONS}
    "-Wl,--version-script=${XNOTAPDF_EXPORTS}")
set_target_properties(xnotapdf PROPERTIES LINK_DEPENDS ${XNOTAPDF_EXPORTS})

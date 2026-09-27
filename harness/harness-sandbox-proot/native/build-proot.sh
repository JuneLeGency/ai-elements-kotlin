#!/usr/bin/env bash
# Builds PRoot (GPL-2.0, https://github.com/termux/proot) for Android arm64 with the NDK and
# installs it as native libraries, so Android lets the app execute it (W^X):
#   src/main/jniLibs/arm64-v8a/libproot.so           the proot executable
#   src/main/jniLibs/arm64-v8a/libproot-loader.so    its ELF loader (PROOT_LOADER)
#   src/main/jniLibs/arm64-v8a/libproot-loader32.so  loader for 32-bit ARM programs (PROOT_LOADER_32)
# talloc (LGPL-3.0) is linked statically from Termux's prebuilt libtalloc-static package.
# Inputs are pinned (commit, sha256) so the build is reproducible; see ../NOTICE for licences
# and where to get the corresponding source.
set -euo pipefail

PROOT_COMMIT=d4d2a19081c3c07f75250e4ce2980b9fa2f5720f
TALLOC_VERSION=2.4.3
TALLOC_STATIC_SHA256=a2f3bb400395520cc1380626907e61ec59bd072ec2209fbff9bf21e167c5be2e
TALLOC_SHA256=ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da
TERMUX=https://packages.termux.dev/apt/termux-main/pool/main
API=26

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/../src/main/jniLibs/arm64-v8a"
WORK="${WORK:-$HERE/build}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$HOME"/Library/Android/sdk/ndk/* | sort -V | tail -1)}"
TOOLCHAIN="$(echo "$NDK"/toolchains/llvm/prebuilt/*)"
mkdir -p "$WORK" "$OUT"
cd "$WORK"

fetch() { # url sha256 file
  [ -f "$3" ] || curl -fsSL "$1" -o "$3"
  echo "$2  $3" | shasum -a 256 -c - >/dev/null
}
unpack_deb() { # deb dir
  mkdir -p "$2" && (cd "$2" && tar -xf "../$1" && tar -xf data.tar.xz)
}

fetch "$TERMUX/libt/libtalloc-static/libtalloc-static_${TALLOC_VERSION}_aarch64.deb" "$TALLOC_STATIC_SHA256" talloc-static.deb
fetch "$TERMUX/libt/libtalloc/libtalloc_${TALLOC_VERSION}_aarch64.deb" "$TALLOC_SHA256" talloc.deb
unpack_deb talloc-static.deb talloc-static
unpack_deb talloc.deb talloc
TALLOC_PREFIX_LIB="$WORK/$(dirname "$(find talloc-static -name libtalloc.a | head -1)")"
TALLOC_INCLUDE="$WORK/$(dirname "$(find talloc -name talloc.h | head -1)")"

if [ ! -d proot ]; then
  git clone -q https://github.com/termux/proot proot
fi
(cd proot && git fetch -q origin "$PROOT_COMMIT" 2>/dev/null || true; git checkout -q "$PROOT_COMMIT")

export CC="$TOOLCHAIN/bin/aarch64-linux-android$API-clang"
export LD="$CC"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export OBJCOPY="$TOOLCHAIN/bin/llvm-objcopy"
export OBJDUMP="$TOOLCHAIN/bin/llvm-objdump"
export CFLAGS="-I$TALLOC_INCLUDE -Os -fPIE"
export LDFLAGS="-L$TALLOC_PREFIX_LIB -pie"
export PROOT_UNBUNDLE_LOADER=/unused # the app passes PROOT_LOADER / PROOT_LOADER_32

make -C proot/src clean >/dev/null
JOBS="$(sysctl -n hw.ncpu 2>/dev/null || nproc)"
# The freestanding loaders build with the plain flags; proot itself also needs `-include string.h`
# because upstream ashmem_memfd.c relies on implicit declarations, which newer clang rejects.
make -C proot/src -j"$JOBS" loader/loader loader/loader-m32 V=1 >build.log 2>&1 || { tail -40 build.log; exit 1; }
CFLAGS="$CFLAGS -include string.h" make -C proot/src -j"$JOBS" proot V=1 >>build.log 2>&1 || { tail -40 build.log; exit 1; }

install -m 0755 proot/src/proot "$OUT/libproot.so"
install -m 0755 proot/src/loader/loader "$OUT/libproot-loader.so"
install -m 0755 proot/src/loader/loader-m32 "$OUT/libproot-loader32.so"
"$STRIP" "$OUT/libproot.so"
ls -l "$OUT"

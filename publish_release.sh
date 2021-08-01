#!/usr/bin/env bash

./gradlew TMessagesProj:assembleRelease

if [ ! -x "$(command -v ghr)" ]; then

  wget -O ghr.tar.gz https://github.com/tcnksm/ghr/releases/download/v0.13.0/ghr_v0.13.0_linux_amd64.tar.gz &&
  tar xvzf ghr.tar.gz &&
  rm ghr.tar.gz &&
  sudo mv ghr*linux_amd64/ghr /usr/local/bin &&
  rm -rf ghr*linux_amd64 || exit 1

fi

rm -rf build/release &&
mkdir -p build/release &&
find TMessagesProj/build/outputs/apk -name "*.apk" -exec cp {} build/release \; &&
ghr -delete -n "$1" "$1" build/release
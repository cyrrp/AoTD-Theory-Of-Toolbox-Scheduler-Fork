#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOL_ROOT="$REPOSITORY_ROOT/.build/format-tools"
CLASSES="$TOOL_ROOT/classes"
SPOTLESS_VERSION='4.8.0'
GOOGLE_JAVA_FORMAT_VERSION='1.28.0'
SLF4J_VERSION='2.0.18'
SPOTLESS_JAR="$TOOL_ROOT/spotless-lib-$SPOTLESS_VERSION.jar"
GOOGLE_JAVA_FORMAT_JAR="$TOOL_ROOT/google-java-format-$GOOGLE_JAVA_FORMAT_VERSION-all-deps.jar"
SLF4J_API_JAR="$TOOL_ROOT/slf4j-api-$SLF4J_VERSION.jar"
SLF4J_NOP_JAR="$TOOL_ROOT/slf4j-nop-$SLF4J_VERSION.jar"
SPOTLESS_SHA256='fea24fd8250f7049dcf83e9c537dca70d4eeddf27f6562cc05b73945d5586a15'
GOOGLE_JAVA_FORMAT_SHA256='32342e7c1b4600f80df3471da46aee8012d3e1445d5ea1be1fb71289b07cc735'
SLF4J_API_SHA256='44508fd1576500688c790b190acdd16fec4f8c79a3e0b900afd70503cf055f55'
SLF4J_NOP_SHA256='40e6be27d583d884183ca466cd20203112691f2a075a650e9e8d5c2e51aa5f49'

case "${1:-}" in
  '') MODE='apply' ;;
  --check) MODE='check' ;;
  *) echo 'Usage: ./format-java.sh [--check]' >&2; exit 2 ;;
esac

checksum_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo 'Neither sha256sum nor shasum is available.' >&2
    return 1
  fi
}

download_file() {
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$1" --output "$2"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "$1" --output-document "$2"
  else
    echo 'Neither curl nor wget is available.' >&2
    return 1
  fi
}

get_verified_artifact() {
  local uri="$1"
  local target="$2"
  local expected="$3"
  local download="$target.download"

  if [[ -f "$target" && "$(checksum_file "$target")" == "$expected" ]]; then
    return
  fi

  rm -f -- "$target" "$download"
  download_file "$uri" "$download"
  local actual
  actual="$(checksum_file "$download")"
  if [[ "$actual" != "$expected" ]]; then
    rm -f -- "$download"
    echo "Checksum mismatch for $uri (expected $expected, got $actual)." >&2
    return 1
  fi
  mv -- "$download" "$target"
}

mkdir -p "$TOOL_ROOT" "$CLASSES"
get_verified_artifact \
  "https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-lib/$SPOTLESS_VERSION/spotless-lib-$SPOTLESS_VERSION.jar" \
  "$SPOTLESS_JAR" \
  "$SPOTLESS_SHA256"
get_verified_artifact \
  "https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/$GOOGLE_JAVA_FORMAT_VERSION/google-java-format-$GOOGLE_JAVA_FORMAT_VERSION-all-deps.jar" \
  "$GOOGLE_JAVA_FORMAT_JAR" \
  "$GOOGLE_JAVA_FORMAT_SHA256"
get_verified_artifact \
  "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/$SLF4J_VERSION/slf4j-api-$SLF4J_VERSION.jar" \
  "$SLF4J_API_JAR" \
  "$SLF4J_API_SHA256"
get_verified_artifact \
  "https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/$SLF4J_VERSION/slf4j-nop-$SLF4J_VERSION.jar" \
  "$SLF4J_NOP_JAR" \
  "$SLF4J_NOP_SHA256"

javac -encoding UTF-8 -cp "$SPOTLESS_JAR" -d "$CLASSES" "$REPOSITORY_ROOT/tools/SpotlessJavaFormat.java"
java -cp "$CLASSES:$SPOTLESS_JAR:$SLF4J_API_JAR:$SLF4J_NOP_JAR" \
  SpotlessJavaFormat "$MODE" "$REPOSITORY_ROOT" "$GOOGLE_JAVA_FORMAT_JAR"

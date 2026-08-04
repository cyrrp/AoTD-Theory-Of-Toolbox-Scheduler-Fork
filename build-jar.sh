#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
game_root=${1:-"$repository_root/../.."}
output_path=${2:-"$repository_root/jars/AoTDToolboxTheory.jar"}
game_root=$(cd -- "$game_root" && pwd)

source_root="$repository_root/src"
build_root="$repository_root/.build/jar-build"
classes_directory="$build_root/classes"
source_list="$build_root/sources.args"
core_root="$game_root/starsector-core"

compile_classpath=(
    "$core_root/starfarer.api.jar"
    "$core_root/starfarer_obf.jar"
    "$core_root/fs.common_obf.jar"
    "$core_root/lwjgl.jar"
    "$core_root/lwjgl_util.jar"
    "$core_root/log4j-1.2.9.jar"
    "$core_root/json.jar"
    "$core_root/xstream-1.4.10.jar"
    "$game_root/mods/LazyLib/jars/LazyLib.jar"
    "$game_root/mods/Ashlib_/jars/ashlib.jar"
    "$game_root/mods/Building Menu Overhaul/jars/bmo.jar"
    "$game_root/mods/Nexerelin/jars/ExerelinCore.jar"
    "$game_root/mods/HMI/jars/HMI.jar"
    "$game_root/mods/Ashes of  The Domain- Vaults of Knowledge/jars/Vok.jar"
    "$game_root/mods/Ashes of  The Domain -Seats Of Power/jars/AodCapitals.jar"
)

for dependency in "${compile_classpath[@]}"; do
    if [[ ! -f "$dependency" ]]; then
        printf 'Required compile-time JAR is missing: %s\n' "$dependency" >&2
        exit 1
    fi
done

command -v javac >/dev/null
command -v jar >/dev/null
javac_version=$(javac -version 2>&1)
if [[ $javac_version != "javac 17" && $javac_version != "javac 17."* ]]; then
    printf 'JDK 17 javac is required; found: %s\n' "$javac_version" >&2
    exit 1
fi

case "$build_root" in
    "$repository_root"/.build/jar-build) ;;
    *)
        printf 'Refusing to clean a build directory outside the repository: %s\n' "$build_root" >&2
        exit 1
        ;;
esac
rm -rf -- "$build_root"
mkdir -p -- "$classes_directory"

mapfile -d '' sources < <(find "$source_root" -type f -name '*.java' -print0 | sort -z)
if (( ${#sources[@]} == 0 )); then
    printf 'No Java sources were found under %s\n' "$source_root" >&2
    exit 1
fi
for source in "${sources[@]}"; do
    printf '"%s"\n' "$source" >> "$source_list"
done

classpath=$(IFS=:; printf '%s' "${compile_classpath[*]}")
javac -encoding UTF-8 -source 17 -target 17 \
    -classpath "$classpath" -d "$classes_directory" "@$source_list"

pending_jar="$build_root/AoTDToolboxTheory.jar"
jar --create --file "$pending_jar" --no-manifest \
    --date=2000-01-01T00:00:00Z -C "$classes_directory" .

required_entries=(
    'data/kaysaar/aotd/tot/compat/PrepatcherContract.class'
    'data/kaysaar/aotd/tot/compat/SchedulerBridge.class'
    'data/kaysaar/aotd/tot/scripts/economy/AoTDEconomy.class'
    'data/kaysaar/aotd/tot/ui/core/DomainTabListener.class'
)
jar_entries=$(jar --list --file "$pending_jar")
for entry in "${required_entries[@]}"; do
    if ! grep -Fxq -- "$entry" <<< "$jar_entries"; then
        printf 'Newly built JAR is missing required class: %s\n' "$entry" >&2
        exit 1
    fi
done

mkdir -p -- "$(dirname -- "$output_path")"
staged_output="$(dirname -- "$output_path")/.$(basename -- "$output_path").pending-$$"
mv -- "$pending_jar" "$staged_output"
mv -f -- "$staged_output" "$output_path"

printf 'Compiled %d Java sources with %s.\n' "${#sources[@]}" "$javac_version"
printf 'JAR: %s\n' "$output_path"

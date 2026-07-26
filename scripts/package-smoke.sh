#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'JAVA_HOME must point to a full JDK 8.' >&2
  exit 1
fi

javac_version=$($JAVA_HOME/bin/javac -version 2>&1)
case "$javac_version" in
  javac\ 1.8.*) ;;
  *)
    printf 'package-smoke requires full JDK 8 javac, found: %s\n' "$javac_version" >&2
    exit 1
    ;;
esac

version=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | sed -n '1p')
if [ -z "$version" ]; then
  printf '%s\n' 'Unable to read reactor version.' >&2
  exit 1
fi
dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

dirty=false
if [ -n "$(git status --porcelain)" ]; then
  dirty=true
fi
if [ "$dirty" = true ] && [ "${SOMA_PACKAGE_ALLOW_DIRTY:-false}" != true ]; then
  printf '%s\n' 'package-smoke requires a clean commit; set SOMA_PACKAGE_ALLOW_DIRTY=true only for non-G6 diagnostic runs.' >&2
  exit 1
fi

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/soma-java-package-smoke.XXXXXX")
first_dir="$work_dir/first"
second_dir="$work_dir/second"
mkdir -p "$first_dir" "$second_dir"

checksum_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    printf '%s\n' 'Neither shasum nor sha256sum is available.' >&2
    exit 1
  fi
}

collect_artifacts() {
  output_dir=$1
  cp pom.xml "$output_dir/soma-java-parent-$version.pom"
  for module in soma-annotations soma-processor soma-runtime-core; do
    cp "$module/pom.xml" "$output_dir/$module-$version.pom"
    cp "$module/target/$module-$version.jar" "$output_dir/"
    cp "$module/target/$module-$version-sources.jar" "$output_dir/"
    cp "$module/target/$module-$version-javadoc.jar" "$output_dir/"
  done
  (
    cd "$output_dir"
    for artifact in $(find . -maxdepth 1 -type f ! -name checksums.sha256 -print | LC_ALL=C sort); do
      printf '%s  %s\n' "$(checksum_file "$artifact")" "${artifact#./}"
    done
  ) > "$output_dir/checksums.sha256"
}

write_expected_artifact_set() {
  output_file=$1
  {
    printf 'soma-java-parent-%s.pom\n' "$version"
    for module in soma-annotations soma-processor soma-runtime-core; do
      printf '%s-%s-javadoc.jar\n' "$module" "$version"
      printf '%s-%s-sources.jar\n' "$module" "$version"
      printf '%s-%s.jar\n' "$module" "$version"
      printf '%s-%s.pom\n' "$module" "$version"
    done
  } | LC_ALL=C sort > "$output_file"
}

validate_artifact_set() {
  artifact_dir=$1
  label=$2
  actual_set="$work_dir/$label-artifact-set.txt"
  expected_set="$work_dir/expected-artifact-set.txt"
  find "$artifact_dir" -maxdepth 1 -type f ! -name checksums.sha256 \
    -exec basename {} \; | LC_ALL=C sort > "$actual_set"
  diff -u "$expected_set" "$actual_set" > "$work_dir/$label-artifact-set.diff"

  for module in soma-annotations soma-processor soma-runtime-core; do
    binary_jar="$artifact_dir/$module-$version.jar"
    source_jar="$artifact_dir/$module-$version-sources.jar"
    javadoc_jar="$artifact_dir/$module-$version-javadoc.jar"

    test "$(unzip -Z1 "$binary_jar" | grep -c '\.class$')" -gt 0
    test "$(unzip -Z1 "$source_jar" | grep -c '\.java$')" -gt 0
    unzip -Z1 "$source_jar" | grep '\.class$' > "$work_dir/$label-$module-source-class-leak.txt" && {
      printf '%s\n' "package-smoke: class file leaked into $source_jar" >&2
      exit 1
    }
    unzip -Z1 "$javadoc_jar" | grep -qx 'index.html'
    test "$(unzip -Z1 "$javadoc_jar" | grep -c '\.html$')" -gt 1
    unzip -Z1 "$javadoc_jar" | grep -E '\.(class|java)$' \
      > "$work_dir/$label-$module-javadoc-code-leak.txt" && {
      printf '%s\n' "package-smoke: class/source file leaked into $javadoc_jar" >&2
      exit 1
    }

    for archive in "$binary_jar" "$source_jar"; do
      unzip -Z1 "$archive" | grep -qx 'META-INF/LICENSE'
      unzip -Z1 "$archive" | grep -qx 'META-INF/NOTICE'
      test "$(unzip -p "$archive" META-INF/LICENSE | shasum -a 256 | awk '{print $1}')" = \
        "$(checksum_file LICENSE)"
      test "$(unzip -p "$archive" META-INF/NOTICE | shasum -a 256 | awk '{print $1}')" = \
        "$(checksum_file NOTICE)"
    done

    class_dir="$work_dir/$label-classes/$module"
    mkdir -p "$class_dir"
    unzip -qq "$binary_jar" '*.class' -d "$class_dir"
    class_count=0
    while IFS= read -r class_file; do
      major=$($JAVA_HOME/bin/javap -verbose "$class_file" |
        sed -n 's/^[[:space:]]*major version: //p' | sed -n '1p')
      if [ "$major" != 52 ]; then
        printf '%s\n' "package-smoke: non-Java-8 classfile $class_file has major $major" >&2
        exit 1
      fi
      class_count=$((class_count + 1))
    done <<EOF
$(find "$class_dir" -type f -name '*.class' | LC_ALL=C sort)
EOF
    printf '%s=%s\n' "$module" "$class_count" >> "$work_dir/$label-class-counts.properties"
  done
}

build_release_shape() {
  local_repository=$1
  build_log=$2
  seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
  mkdir -p "$local_repository"
  if [ -d "$seed_repository" ]; then
    # 只预热已校验的 plugin/dependency bytes；两次 clean build 仍写入彼此独占仓库。
    cp -R "$seed_repository/." "$local_repository/"
  fi
  if ! ./mvnw -B -ntp -Prelease-artifacts \
    -Dmaven.repo.local="$local_repository" \
    -pl soma-annotations,soma-processor,soma-runtime-core,soma-dataflow -am clean package \
    > "$build_log" 2>&1; then
    cat "$build_log" >&2
    return 1
  fi
}

write_expected_artifact_set "$work_dir/expected-artifact-set.txt"

build_release_shape "$work_dir/repository-first" "$work_dir/build-first.log"
collect_artifacts "$first_dir"
validate_artifact_set "$first_dir" first

build_release_shape "$work_dir/repository-second" "$work_dir/build-second.log"
collect_artifacts "$second_dir"
validate_artifact_set "$second_dir" second

diff -u "$first_dir/checksums.sha256" "$second_dir/checksums.sha256" > "$work_dir/reproducibility.diff"

./mvnw -B -ntp -Dmaven.repo.local="$work_dir/repository-second" \
  -pl soma-annotations,soma-processor,soma-runtime-core,soma-dataflow "$dependency_plugin":tree \
  -Dscope=runtime > "$work_dir/runtime-dependency-tree.txt"

mkdir -p "$root_dir/target"
evidence_dir=$(mktemp -d "$root_dir/target/package-smoke.XXXXXX")
cp -R "$first_dir" "$evidence_dir/first"
cp -R "$second_dir" "$evidence_dir/second"
cp "$work_dir"/build-*.log "$evidence_dir/"
cp "$work_dir"/*-artifact-set.txt "$evidence_dir/"
cp "$work_dir"/*-artifact-set.diff "$evidence_dir/"
cp "$work_dir"/*-class-counts.properties "$evidence_dir/"
cp "$work_dir/reproducibility.diff" "$evidence_dir/"
cp "$work_dir/runtime-dependency-tree.txt" "$evidence_dir/"

commit=$(git rev-parse HEAD)

{
  printf 'format=soma-java-local-provenance-v1\n'
  printf 'commit=%s\n' "$commit"
  printf 'dirty=%s\n' "$dirty"
  printf 'artifactVersion=%s\n' "$version"
  printf 'artifactSet=parent-pom-plus-three-module-pom-binary-source-javadoc\n'
  printf 'classfileMajor=52\n'
  printf 'licenseNotice=binary-and-source-jars-exact-root-content\n'
  printf 'buildCommand=./mvnw -B -ntp -Prelease-artifacts -Dmaven.repo.local=<isolated> -pl soma-annotations,soma-processor,soma-runtime-core,soma-dataflow -am clean package\n'
  printf 'javaHome=%s\n' "$JAVA_HOME"
  "$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/java=/'
  ./mvnw -version | sed 's/^/maven=/'
  uname -srm | sed 's/^/os=/'
  if command -v sw_vers >/dev/null 2>&1; then
    sw_vers | sed 's/^/osDetail=/'
  fi
  printf 'reproducibility=byte-for-byte\n'
  printf 'signature=not-performed\n'
  printf 'signatureReason=no-release-signing-key-in-local-implementation-environment\n'
} > "$evidence_dir/provenance.properties"

cp "$second_dir/checksums.sha256" "$evidence_dir/release-checksums.sha256"

printf 'package-smoke: ok\n'
printf 'artifact-version: %s\n' "$version"
printf 'evidence: %s\n' "$evidence_dir"
printf '%s\n' 'claim-boundary: local package/reproducibility evidence only; not a signed public release'

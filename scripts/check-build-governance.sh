#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  printf '%s\n' 'build-governance-check: JAVA_HOME must point to a full JDK 8' >&2
  exit 1
fi
if ! command -v rg >/dev/null 2>&1; then
  printf '%s\n' 'build-governance-check: rg is required for source-shape validation' >&2
  exit 1
fi

dependency_plugin_version=$(sed -n \
  's:.*<maven.dependency.plugin.version>\([^<]*\)</maven.dependency.plugin.version>.*:\1:p' \
  pom.xml | sed -n '1p')
if [ -z "$dependency_plugin_version" ]; then
  printf '%s\n' 'build-governance-check: dependency plugin version property missing' >&2
  exit 1
fi
dependency_plugin=org.apache.maven.plugins:maven-dependency-plugin:$dependency_plugin_version

mkdir -p target
evidence_dir=$(mktemp -d "$root_dir/target/build-governance.XXXXXX")
effective_pom=$evidence_dir/effective-pom.xml
repository=$evidence_dir/repository
seed_repository=$root_dir/soma-testkit/target/phase0-m2/repository
mkdir -p "$repository"
if [ -d "$seed_repository" ]; then
  # 只预热已校验的 Maven/plugin bytes；本次解析仍写入独占仓库并使用 exact goal 坐标。
  cp -R "$seed_repository/." "$repository/"
fi
./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom \
  -Doutput="$effective_pom"

grep -A3 -F '<artifactId>maven-dependency-plugin</artifactId>' "$effective_pom" \
  | grep -F "<version>$dependency_plugin_version</version>" >/dev/null

if rg -n '(^|[[:space:]])dependency:(tree|build-classpath|resolve-plugins)([[:space:]\\]|$)' \
    scripts -g '*.sh' >"$evidence_dir/unpinned-dependency-plugin.txt"; then
  printf '%s\n' 'build-governance-check: unpinned Maven dependency plugin prefix found' >&2
  exit 1
fi

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-annotations,soma-processor,soma-runtime-core \
  "$dependency_plugin":tree -Dscope=runtime \
  >"$evidence_dir/runtime-dependency-tree.txt"
grep -F "dependency:$dependency_plugin_version:tree" \
  "$evidence_dir/runtime-dependency-tree.txt" >/dev/null

"$JAVA_HOME/bin/java" -version
./mvnw -version
printf '%s\n' "build-governance-effective-pom: $effective_pom"
printf '%s\n' 'build-governance-check: ok'

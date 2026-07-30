#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"
. "$root_dir/scripts/lib/project-version.sh"
. "$root_dir/scripts/lib/sha256.sh"

manifest=scripts/manifests/source-release-files.txt
readme_template=scripts/manifests/source-release-README.md

if [ ! -f "$manifest" ] || [ ! -f "$readme_template" ]; then
  printf '%s\n' 'source-package-smoke: manifest or README template missing' >&2
  exit 1
fi

version=$(soma_project_version)
case "$version" in
  *-SNAPSHOT)
    printf '%s\n' \
      "source-package-smoke requires a release version, found $version" >&2
    exit 1
    ;;
esac

if [ -n "$(git status --porcelain)" ]; then
  printf '%s\n' 'source-package-smoke requires a clean immutable commit' >&2
  exit 1
fi

commit=$(git rev-parse HEAD)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/soma-java-source-package.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT HUP INT TERM

bundle_name="soma-java-$version"
bundle_root="$work_dir/$bundle_name"
archive_input="$work_dir/source-input.tar"
expected="$work_dir/expected-contents.txt"
actual="$work_dir/actual-contents.txt"

set --
while IFS= read -r manifest_path; do
  case "$manifest_path" in
    ''|'#'*) continue ;;
  esac
  case "$manifest_path" in
    /*|*'..'*)
      printf '%s\n' \
        "source-package-smoke: unsafe manifest path $manifest_path" >&2
      exit 1
      ;;
  esac
  if ! git cat-file -e "HEAD:$manifest_path" 2>/dev/null; then
    printf '%s\n' \
      "source-package-smoke: untracked or missing manifest path $manifest_path" >&2
    exit 1
  fi
  set -- "$@" "$manifest_path"
done < "$manifest"

if [ "$#" -eq 0 ]; then
  printf '%s\n' 'source-package-smoke: manifest contains no source paths' >&2
  exit 1
fi

git archive --format=tar --prefix="$bundle_name/" HEAD -- "$@" \
  > "$archive_input"
tar -xf "$archive_input" -C "$work_dir"

mv "$bundle_root/$readme_template" "$bundle_root/README.md"
rmdir "$bundle_root/scripts/manifests"

git ls-tree -r --name-only HEAD -- "$@" |
  sed "s#^$readme_template\$#README.md#" |
  LC_ALL=C sort > "$expected"
find "$bundle_root" -type f -print |
  sed "s#^$bundle_root/##" |
  LC_ALL=C sort > "$actual"
if ! diff -u "$expected" "$actual" > "$work_dir/content-set.diff"; then
  printf '%s\n' \
    'source-package-smoke: archive contents differ from manifest' >&2
  cat "$work_dir/content-set.diff" >&2
  exit 1
fi

for forbidden in \
  project/ \
  tests/ \
  .github/ \
  .git/ \
  target/; do
  if grep -F "$forbidden" "$actual" >/dev/null 2>&1; then
    printf '%s\n' \
      "source-package-smoke: forbidden path leaked: $forbidden" >&2
    exit 1
  fi
done

if grep -Ei '(^|/)(\.env|id_rsa|credentials|secrets?)(\.|/|$)|\.(jfr|log)$' \
    "$actual" >/dev/null 2>&1; then
  printf '%s\n' 'source-package-smoke: sensitive or raw evidence path leaked' >&2
  exit 1
fi

for required in \
  README.md \
  LICENSE \
  NOTICE \
  pom.xml \
  mvnw \
  soma-annotations/pom.xml \
  soma-processor/pom.xml \
  soma-runtime-core/pom.xml \
  soma-dataflow/pom.xml \
  soma-examples/pom.xml \
  soma-benchmarks/pom.xml; do
  grep -Fqx "$required" "$actual" \
    || {
      printf '%s\n' \
        "source-package-smoke: required path missing: $required" >&2
      exit 1
    }
done

tar_file="$work_dir/$bundle_name-source.tar"
gzip_file="$tar_file.gz"
build_log="$work_dir/archive-build.log"
(
  cd "$work_dir"
  tar -cf "$tar_file" "$bundle_name"
)
gzip -n "$tar_file"

if ! (
  cd "$bundle_root"
  ./scripts/check-toolchain.sh
  ./mvnw -B -ntp verify
) >"$build_log" 2>&1; then
  printf '%s\n' \
    'source-package-smoke: extracted archive source failed isolated verify' >&2
  tail -n 120 "$build_log" >&2
  exit 1
fi

evidence_root=${SOMA_RELEASE_EVIDENCE_ROOT:-$root_dir/target}
mkdir -p "$evidence_root"
evidence_dir=$(mktemp -d "$evidence_root/source-package-smoke.XXXXXX")
cp "$gzip_file" "$evidence_dir/"
cp "$actual" "$evidence_dir/contents.txt"
cp "$work_dir/content-set.diff" "$evidence_dir/"
cp "$build_log" "$evidence_dir/"

archive_checksum=$(soma_sha256_hex "$gzip_file")
printf '%s  %s\n' "$archive_checksum" "$(basename "$gzip_file")" \
  > "$evidence_dir/checksums.sha256"

{
  printf 'format=soma-java-source-package-v1\n'
  printf 'commit=%s\n' "$commit"
  printf 'dirty=false\n'
  printf 'artifactVersion=%s\n' "$version"
  printf 'deliveryProfile=curated-private-source\n'
  printf 'manifest=%s\n' "$manifest"
  printf 'projectFactsIncluded=false\n'
  printf 'repositoryTestsIncluded=false\n'
  printf 'rawEvidenceIncluded=false\n'
  printf 'agentSkillIncluded=false\n'
  printf 'archiveBuildVerified=true\n'
  printf 'archiveBuildCommand=./mvnw -B -ntp verify\n'
  printf 'archive=%s\n' "$(basename "$gzip_file")"
  printf 'archiveSha256=%s\n' "$archive_checksum"
} > "$evidence_dir/provenance.properties"

printf '%s\n' 'source-package-smoke: ok'
printf 'artifact-version: %s\n' "$version"
printf 'evidence: %s\n' "$evidence_dir"
printf '%s\n' \
  'claim-boundary: curated private source asset; not public or Maven publication'

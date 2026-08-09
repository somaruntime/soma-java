#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo_root"

if [ -z "${JAVA_HOME:-}" ]; then
    echo "i8-package: JAVA_HOME must select the qualified Java 8 JDK" >&2
    exit 1
fi
java_cmd="$JAVA_HOME/bin/java"
jar_cmd="$JAVA_HOME/bin/jar"
test -x "$java_cmd" && test -x "$jar_cmd"
"$java_cmd" -version 2>&1 | grep -q 'version "1\.8\.'

if [ "${SOMA_I8_REUSE_BUILD:-0}" != 1 ]; then
    mvn clean package
fi

runtime_jar=
runtime_sources=
runtime_javadoc=
processor_jar=
processor_sources=
processor_javadoc=
for candidate in "$repo_root"/soma-runtime/target/soma-runtime-*.jar; do
    case "$candidate" in
        *-sources.jar) runtime_sources=$candidate ;;
        *-javadoc.jar) runtime_javadoc=$candidate ;;
        *) runtime_jar=$candidate ;;
    esac
done
for candidate in "$repo_root"/soma-processor/target/soma-processor-*.jar; do
    case "$candidate" in
        *-sources.jar) processor_sources=$candidate ;;
        *-javadoc.jar) processor_javadoc=$candidate ;;
        *) processor_jar=$candidate ;;
    esac
done
for artifact in "$runtime_jar" "$runtime_sources" "$runtime_javadoc" \
        "$processor_jar" "$processor_sources" "$processor_javadoc"; do
    test -s "$artifact" || {
        echo "i8-package: production artifact set is incomplete" >&2
        exit 1
    }
done

version=$(sed -n 's/^artifact.version=//p' \
    soma-runtime/target/classes/META-INF/soma/linkage.properties)
test -n "$version"

output_root="$repo_root/target/i8-package"
case "$output_root" in "$repo_root"/target/i8-package) rm -rf -- "$output_root" ;; *) exit 1 ;; esac
mkdir -p "$output_root"
cp "$runtime_jar" "$runtime_sources" "$runtime_javadoc" \
    "$processor_jar" "$processor_sources" "$processor_javadoc" "$output_root/"

checksum() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}
runtime_sha=$(checksum "$runtime_jar")
processor_sha=$(checksum "$processor_jar")
created=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
commit=$(git rev-parse HEAD)
if test -z "$(git status --porcelain)"; then tree_state=clean; else tree_state=dirty; fi

cat > "$output_root/provenance.properties" <<EOF
product=SOMA
repository=somaruntime/soma-java
version=$version
commit=$commit
tree.state=$tree_state
java=$($java_cmd -version 2>&1 | head -n 1)
maven=$(mvn -version | head -n 1)
created.utc=$created
runtime.sha256=$runtime_sha
processor.sha256=$processor_sha
publication=none
EOF

cat > "$output_root/sbom.spdx.json" <<EOF
{
  "spdxVersion": "SPDX-2.3",
  "dataLicense": "CC0-1.0",
  "SPDXID": "SPDXRef-DOCUMENT",
  "name": "SOMA-$version",
  "documentNamespace": "https://github.com/somaruntime/soma-java/sbom/$commit",
  "creationInfo": {
    "created": "$created",
    "creators": ["Organization: ArthurFeng", "Tool: SOMA-I8-package"]
  },
  "packages": [
    {
      "name": "soma-runtime",
      "SPDXID": "SPDXRef-Package-Runtime",
      "versionInfo": "$version",
      "downloadLocation": "NOASSERTION",
      "filesAnalyzed": false,
      "licenseConcluded": "Apache-2.0",
      "licenseDeclared": "Apache-2.0",
      "checksums": [{"algorithm": "SHA256", "checksumValue": "$runtime_sha"}]
    },
    {
      "name": "soma-processor",
      "SPDXID": "SPDXRef-Package-Processor",
      "versionInfo": "$version",
      "downloadLocation": "NOASSERTION",
      "filesAnalyzed": false,
      "licenseConcluded": "Apache-2.0",
      "licenseDeclared": "Apache-2.0",
      "checksums": [{"algorithm": "SHA256", "checksumValue": "$processor_sha"}]
    }
  ],
  "relationships": [
    {"spdxElementId": "SPDXRef-DOCUMENT", "relationshipType": "DESCRIBES", "relatedSpdxElement": "SPDXRef-Package-Runtime"},
    {"spdxElementId": "SPDXRef-DOCUMENT", "relationshipType": "DESCRIBES", "relatedSpdxElement": "SPDXRef-Package-Processor"},
    {"spdxElementId": "SPDXRef-Package-Processor", "relationshipType": "DEPENDS_ON", "relatedSpdxElement": "SPDXRef-Package-Runtime"}
  ]
}
EOF

stage=$(mktemp -d "${TMPDIR:-/tmp}/soma-i8-source.XXXXXX")
cleanup() {
    case "$stage" in */soma-i8-source.*) rm -rf -- "$stage" ;; *) exit 1 ;; esac
}
trap cleanup EXIT HUP INT TERM
while IFS= read -r source_path; do
    test -n "$source_path" || continue
    case "$source_path" in /*|../*|*/../*|*/..) echo "i8-package: unsafe allowlist path" >&2; exit 1 ;; esac
    test -e "$repo_root/$source_path" || {
        echo "i8-package: missing allowlist path: $source_path" >&2
        exit 1
    }
    mkdir -p "$stage/$(dirname -- "$source_path")"
    cp -R "$repo_root/$source_path" "$stage/$source_path"
done < config/release/source-delivery-allowlist.txt
find "$stage" -type d -empty -delete
COPYFILE_DISABLE=1 tar -czf "$output_root/soma-java-$version-source-bundle.tar.gz" \
    -C "$stage" .

checksums="$output_root/checksums.sha256"
: > "$checksums"
find "$output_root" -maxdepth 1 -type f ! -name 'checksums.sha256' -print \
    | LC_ALL=C sort | while IFS= read -r artifact; do
        echo "$(checksum "$artifact")  $(basename -- "$artifact")"
    done > "$checksums"

for artifact in "$runtime_jar" "$processor_jar"; do
    "$jar_cmd" tf "$artifact" | grep -qx 'META-INF/LICENSE'
    "$jar_cmd" tf "$artifact" | grep -qx 'META-INF/NOTICE'
    unzip -p "$artifact" META-INF/MANIFEST.MF | grep -q '^Built-For-Java: 8'
done

echo "i8-package: PASS ($output_root)"

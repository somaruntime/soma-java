#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

for command_name in jq rg shasum; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf '%s\n' "security-release-scan: required command not found: $command_name" >&2
    exit 1
  fi
done

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/javac" ]; then
  printf '%s\n' 'security-release-scan: JAVA_HOME must point to a full JDK 8.' >&2
  exit 1
fi
javac_version=$($JAVA_HOME/bin/javac -version 2>&1)
case "$javac_version" in
  javac\ 1.8.*) ;;
  *)
    printf '%s\n' "security-release-scan: full JDK 8 is required, found $javac_version" >&2
    exit 1
    ;;
esac

version=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | sed -n '1p')
if [ -z "$version" ]; then
  printf '%s\n' 'security-release-scan: unable to read reactor version' >&2
  exit 1
fi

scanner=${OSV_SCANNER:-}
if [ -z "$scanner" ] || [ ! -x "$scanner" ]; then
  printf '%s\n' 'security-release-scan: set OSV_SCANNER to the verified OSV-Scanner v2.3.8 binary' >&2
  exit 1
fi

scanner_version=$($scanner --version 2>&1)
printf '%s\n' "$scanner_version" | grep -F 'osv-scanner version: 2.3.8' >/dev/null
expected_scanner_sha=a8cd6507b06239f463a7642430cfd2d154882f150f6e30cdc0653e28dfc34216
actual_scanner_sha=$(shasum -a 256 "$scanner" | awk '{print $1}')
if [ "$actual_scanner_sha" != "$expected_scanner_sha" ]; then
  printf '%s\n' "security-release-scan: OSV-Scanner checksum mismatch: $actual_scanner_sha" >&2
  exit 1
fi

expected_license_sha=cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30
actual_license_sha=$(shasum -a 256 LICENSE | awk '{print $1}')
if [ "$actual_license_sha" != "$expected_license_sha" ]; then
  printf '%s\n' "security-release-scan: Apache-2.0 text checksum mismatch: $actual_license_sha" >&2
  exit 1
fi
expected_notice_sha=30ee2fc6260f43d20ae9c8e062196dc9bddf9f92f565872de70256f61f1070bb
actual_notice_sha=$(shasum -a 256 NOTICE | awk '{print $1}')
if [ "$actual_notice_sha" != "$expected_notice_sha" ]; then
  printf '%s\n' "security-release-scan: NOTICE checksum mismatch: $actual_notice_sha" >&2
  exit 1
fi

dirty=false
if [ -n "$(git status --porcelain)" ]; then
  dirty=true
fi

mkdir -p "$root_dir/target"
evidence_dir=$(mktemp -d "$root_dir/target/security-release-scan.XXXXXX")
repository="$evidence_dir/repository"

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -DskipTests -DincludeTestScope=false -DoutputFormat=json -DoutputName=soma-java-sbom \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
  >"$evidence_dir/cyclonedx.log"

sbom=target/soma-java-sbom.json
test -s "$sbom"
cp "$sbom" "$evidence_dir/soma-java-sbom.cdx.json"

$scanner scan source --sbom "$evidence_dir/soma-java-sbom.cdx.json" \
  --format=json --all-packages --output-file "$evidence_dir/osv-vulnerabilities.json" \
  >"$evidence_dir/osv-vulnerabilities.log" 2>&1

$scanner scan source --sbom "$evidence_dir/soma-java-sbom.cdx.json" \
  --format=json --all-packages --licenses \
  --output-file "$evidence_dir/osv-licenses.json" \
  >"$evidence_dir/osv-licenses.log" 2>&1

vulnerability_count=$(jq '[.results[]?.packages[]?.vulnerabilities[]?] | length' \
  "$evidence_dir/osv-vulnerabilities.json")
if [ "$vulnerability_count" -ne 0 ]; then
  printf '%s\n' "security-release-scan: detected $vulnerability_count known vulnerabilities" >&2
  exit 1
fi

license_violation_count=$(jq '[.components[]
  | select(.group != "jdk")
  | select(([.licenses[]?.license.id] | index("Apache-2.0")) == null)] | length' \
  "$evidence_dir/soma-java-sbom.cdx.json")
if [ "$license_violation_count" -ne 0 ]; then
  printf '%s\n' "security-release-scan: detected $license_violation_count license violations" >&2
  exit 1
fi

jq -r '.components[] | [.group, .name, .version] | @tsv' \
  "$evidence_dir/soma-java-sbom.cdx.json" | LC_ALL=C sort \
  > "$evidence_dir/sbom-components.tsv"
{
  printf 'com.hgtech.soma\tsoma-annotations\t%s\n' "$version"
  printf 'com.hgtech.soma\tsoma-processor\t%s\n' "$version"
  printf 'com.hgtech.soma\tsoma-runtime-core\t%s\n' "$version"
  printf 'jdk\ttools\t1.8\n'
} > "$evidence_dir/expected-sbom-components.tsv"
LC_ALL=C sort "$evidence_dir/expected-sbom-components.tsv" \
  > "$evidence_dir/expected-sbom-components.sorted.tsv"
diff -u "$evidence_dir/expected-sbom-components.sorted.tsv" \
  "$evidence_dir/sbom-components.tsv" > "$evidence_dir/sbom-components.diff"

./mvnw -B -ntp -Dmaven.repo.local="$repository" \
  -pl soma-annotations,soma-processor,soma-runtime-core dependency:tree \
  -Dscope=runtime >"$evidence_dir/runtime-dependency-tree.txt"
./mvnw -B -ntp -Dmaven.repo.local="$repository" dependency:resolve-plugins \
  >"$evidence_dir/build-plugin-inventory.txt"

if rg -n '<dependency>' soma-annotations/pom.xml soma-runtime-core/pom.xml >/dev/null; then
  printf '%s\n' 'security-release-scan: production JDK-only module gained a dependency' >&2
  exit 1
fi

{
  printf 'scanner=OSV-Scanner 2.3.8\n'
  printf 'scannerSha256=%s\n' "$actual_scanner_sha"
  printf 'scannerCommit=408fcd6f8707999a29e7ba45e15809764cf24f67\n'
  printf 'scannerDatabase=OSV.dev live query at execution time\n'
  printf 'sbomGenerator=org.cyclonedx:cyclonedx-maven-plugin:2.9.1\n'
  printf 'commit=%s\n' "$(git rev-parse HEAD)"
  printf 'dirty=%s\n' "$dirty"
  printf 'cycloneDxCommand=./mvnw -B -ntp -Dmaven.repo.local=<isolated> -DskipTests -DincludeTestScope=false -DoutputFormat=json -DoutputName=soma-java-sbom org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom\n'
  printf 'osvCommand=OSV_SCANNER=<verified-v2.3.8> security-release-scan.sh; scanner subcommand: scan source --sbom <sbom> --format=json --all-packages\n'
  printf 'licenseTextSha256=%s\n' "$actual_license_sha"
  printf 'noticeTextSha256=%s\n' "$actual_notice_sha"
  printf 'sbomSha256=%s\n' "$(shasum -a 256 "$evidence_dir/soma-java-sbom.cdx.json" | awk '{print $1}')"
  printf 'vulnerabilityReportSha256=%s\n' "$(shasum -a 256 "$evidence_dir/osv-vulnerabilities.json" | awk '{print $1}')"
  printf 'licenseReportSha256=%s\n' "$(shasum -a 256 "$evidence_dir/osv-licenses.json" | awk '{print $1}')"
  printf 'vulnerabilityCount=%s\n' "$vulnerability_count"
  printf 'licenseViolationCount=%s\n' "$license_violation_count"
  printf 'productionRuntimeThirdPartyDependencies=0\n'
  printf 'licenseEvidence=CycloneDX SBOM declares Apache-2.0 for every SOMA component; JDK tools.jar is system compiler input and not distributed\n'
  printf 'knownLimitation=OSV covers known published advisories; unpublished SOMA coordinates report UNKNOWN in deps.dev, so project license is verified from LICENSE checksum, POM metadata, and CycloneDX SBOM\n'
  printf 'knownLimitation=Build-plugin inventory is recorded but plugin transitive code is not part of the SOMA runtime artifact\n'
  printf 'claimBoundary=local known-vulnerability dependency and declared-license diagnostic only; not a public release security sign-off\n'
  "$JAVA_HOME/bin/java" -version 2>&1 | sed 's/^/java=/'
  ./mvnw -version | sed 's/^/maven=/'
  uname -srm | sed 's/^/os=/'
  if command -v sw_vers >/dev/null 2>&1; then
    sw_vers | sed 's/^/osDetail=/'
  fi
} >"$evidence_dir/summary.properties"

printf 'security-release-scan: ok\n'
printf 'security-release-evidence: %s\n' "$evidence_dir"

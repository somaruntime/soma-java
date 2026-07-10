#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

failed=0

fail() {
  printf '%s\n' "scope-check: $*" >&2
  failed=1
}

require_once() {
  file=$1
  text=$2
  count=$(grep -F -c "$text" "$file" 2>/dev/null || true)
  if [ "$count" -ne 1 ]; then
    fail "$file must contain exactly one: $text"
  fi
}

require_once docs/soma-table-design-constitution.md '### 原则十五：实现阶段不得缩水'
require_once docs/implementation-strategy.md '### 8.1 唯一 V1 目标与 Phase 语义'
require_once docs/implementation-strategy.md '### 8.2 单调收敛原则'
require_once docs/implementation-strategy.md '### 8.3 V1 capability ledger'
require_once docs/implementation-strategy.md '### 8.5 Scope change hard stop'
require_once docs/validation-gates.md '### 4.1 V1 scope non-regression'
require_once docs/validation-gates.md '### 4.2 实施验证环境与支持矩阵边界'
require_once docs/versioning-and-release-contract.md '### 3.1 V1.0 RC 完成边界'
require_once docs/versioning-and-release-contract.md '### 3.2 发布身份原则'
require_once docs/build-and-dependency-contract.md '### 2.1 实施验证基线'
require_once AGENTS.md '## V1 Scope Preservation'
require_once .github/pull_request_template.md '## V1 防缩水'
require_once pom.xml '<name>HGTECH</name>'

excluded_identity=$(printf '%s%s' 'hg' 'cyber')
excluded_identity_files=$(rg -l -i "$excluded_identity" --glob '!target/**' --glob '!.git/**' . 2>/dev/null | sort || true)
if [ -n "$excluded_identity_files" ]; then
  fail 'the excluded historical identity must not appear anywhere in the repository'
fi

capability_ids='
V1-ANNOTATION-SCHEMA
V1-COMPILER-LOWERING
V1-PROCESSING-MODEL
V1-SCHEMA-HASH
V1-PUBLIC-COMPATIBILITY
V1-GENERATED-API
V1-DENSE-STORAGE
V1-ROW-PIPELINE
V1-COLUMN-ACCESS
V1-KEYED-IDENTITY
V1-ACCESS-STRUCTURES
V1-MUTATION
V1-CHILD-OWNERSHIP
V1-MATERIALIZATION
V1-RUNTIME-LIFECYCLE
V1-RUNTIME-ERRORS
V1-RUNTIME-PLAN
V1-PERFORMANCE-SHAPE
V1-SECURITY-INTEGRITY
V1-EVIDENCE-TOOLING
V1-CONSUMER-PACKAGE
V1-SCENARIO-BENCHMARK
V1-RELEASE-EVIDENCE
'

for capability_id in $capability_ids; do
  require_once docs/implementation-strategy.md "| \`$capability_id\` |"
done

if [ "$failed" -ne 0 ]; then
  exit 1
fi

printf '%s\n' 'scope-check: ok'

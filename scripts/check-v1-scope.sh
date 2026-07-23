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

require_once docs/design/soma-java-design-constitution.md '当前 slice 必须是最终架构的有效子集，不能依赖未来 migration/rewrite 才成立。'
require_once docs/design/soma-java-design-constitution.md 'V1 只承诺 Java 8 进程内使用'
require_once docs/engineering/validation-gates.md '## 1. Gate 不是阶段折扣'
require_once docs/engineering/validation-gates.md '## 4. Scope non-regression'
require_once docs/design/compatibility-security-and-versioning.md '组织与发布主体为 HGTECH，产品品牌为 SOMA'
require_once docs/design/compatibility-security-and-versioning.md '## 5. Release 边界'
require_once docs/engineering/build-and-validation.md 'V1 compiler/runtime validation 使用 Azul Zulu full JDK 8 javac/runtime'
require_once docs/engineering/documentation-governance.md '重大长期设计变化先进入 Temporary'
require_once docs/conformance/known-gaps.md '| `CF-006` | Release evidence |'
require_once AGENTS.md '## Design-driven Scope Preservation'
require_once .github/pull_request_template.md '## V1 防缩水'
require_once pom.xml '<name>HGTECH</name>'

excluded_identity=$(printf '%s%s' 'hg' 'cyber')
excluded_identity_files=$(rg -l -i "$excluded_identity" --glob '!target/**' --glob '!.git/**' . 2>/dev/null | sort || true)
if [ -n "$excluded_identity_files" ]; then
  fail 'the excluded historical identity must not appear anywhere in the repository'
fi

if [ "$failed" -ne 0 ]; then
  exit 1
fi

printf '%s\n' 'scope-check: ok'

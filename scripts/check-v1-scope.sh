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

require_once project/design/soma-java-design-constitution.md '当前 slice 必须是最终架构的有效子集，不能依赖未来 migration/rewrite 才成立。'
require_once project/design/soma-java-design-constitution.md 'V1 只承诺 Java 8 进程内使用'
require_once project/process/validation-gates.md '## 1. Gate 不是阶段折扣'
require_once project/process/validation-gates.md '## 4. Scope non-regression'
require_once project/design/compatibility-security-and-versioning.md 'copyright owner 与发布主体为 ArthurFeng，产品品牌为 SOMA'
require_once project/design/compatibility-security-and-versioning.md '## 5. Release 边界'
require_once project/process/build-and-validation.md 'V1 compiler/runtime validation 使用 Amazon Corretto 8.502.07.1 full JDK 8'
require_once project/process/documentation-governance.md '重大长期设计或项目体系变化先进入'
require_once project/design/compatibility-security-and-versioning.md '当前选择的发布渠道为 private GitHub source repository'
require_once project/process/release-governance.md 'artifact distribution provenance。未选择的 profile 保持 `not-selected`，不能写成'
require_once AGENTS.md '## Design-driven Scope Preservation'
require_once .github/pull_request_template.md '## V1 防缩水'
require_once pom.xml '<name>SOMA</name>'
require_once pom.xml '<connection>scm:git:https://github.com/somaruntime/soma-java.git</connection>'
require_once pom.xml '<id>ArthurFeng</id>'

excluded_identity=$(printf '%s%s' 'hg' 'cyber')
excluded_identity_files=$(rg -l -i "$excluded_identity" --glob '!target/**' --glob '!.git/**' . 2>/dev/null | sort || true)
if [ -n "$excluded_identity_files" ]; then
  fail 'the excluded historical identity must not appear anywhere in the repository'
fi

if [ "$failed" -ne 0 ]; then
  exit 1
fi

printf '%s\n' 'scope-check: ok'

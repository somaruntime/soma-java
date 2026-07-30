#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

failed=0

fail() {
  printf '%s\n' "doc-check: $*" >&2
  failed=1
}

require_once() {
  file=$1
  pattern=$2
  count=$(sed -n '1,40p' "$file" | grep -E -c "$pattern" || true)
  if [ "$count" -ne 1 ]; then
    fail "$file must declare exactly one $pattern in its metadata"
  fi
}

require_indexed() {
  file=$1
  index_file=$2
  base_name=$(basename "$file")
  if ! grep -F "$base_name" "$index_file" >/dev/null 2>&1; then
    fail "$file is not indexed by $index_file"
  fi
}

markdown_files=$(find . -path './.git' -prune -o \
  -path './target' -prune -o -type f -name '*.md' -print | sort)

for file in $markdown_files; do
  if grep -n '[[:blank:]]$' "$file" >/dev/null 2>&1; then
    fail "$file contains trailing whitespace"
  fi

  fence_count=$(grep -c '^```' "$file" || true)
  if [ $((fence_count % 2)) -ne 0 ]; then
    fail "$file contains an unbalanced fenced code block"
  fi

  grep -nEo '\]\([^)]*\)' "$file" 2>/dev/null |
  while IFS=: read -r line_no raw_link; do
    target=$(printf '%s' "$raw_link" |
      sed -e 's/^](//' -e 's/)$//' -e 's/#.*$//' -e 's/^<//' -e 's/>$//')
    case "$target" in
      http://*|https://*|mailto:*|'') continue ;;
    esac
    if [ ! -e "$(dirname "$file")/$target" ]; then
      printf '%s\n' \
        "doc-check: $file:$line_no unresolved link $target" >&2
      exit 1
    fi
  done || failed=1
done

for file in $(find docs -type f -name '*.docx' -print | sort); do
  if ! unzip -tqq "$file" >/dev/null 2>&1; then
    fail "$file is not a valid OOXML archive"
    continue
  fi

  unzip -p "$file" 'word/*.xml' 'word/_rels/*.rels' 2>/dev/null |
    sed 's/<[^>]*>/ /g' |
    grep -Eo '(\.agents|assets|docs|project|guides|reports|scripts|soma-[[:alnum:]-]+|tests)/[-[:alnum:]_./#]+' |
    LC_ALL=C sort -u |
    while IFS= read -r embedded_reference; do
      case "$embedded_reference" in
        */|*.md|*.md\#*|*.docx|*.docx\#*|*.png|*.png\#*) ;;
        *) continue ;;
      esac
      embedded_target=$(printf '%s' "$embedded_reference" | sed 's/#.*$//')
      if [ ! -e "$embedded_target" ]; then
        printf '%s\n' \
          "doc-check: $file contains unresolved embedded path $embedded_reference" \
          >&2
        exit 1
      fi
    done || failed=1
done

for documentation_root in ./.agents ./.github ./*; do
  [ -d "$documentation_root" ] || continue
  case "$documentation_root" in
    ./.git|./target) continue ;;
  esac
  if ! find "$documentation_root" -type f -name '*.md' -print -quit |
      grep . >/dev/null 2>&1; then
    continue
  fi
  case "$documentation_root" in
    ./.agents|./.github|./assets|./docs|./project|./scripts|\
    ./soma-annotations|./soma-benchmarks|./soma-dataflow|./soma-examples|\
    ./soma-processor|./soma-runtime-core|./tests) ;;
    *) fail "unexpected top-level documentation root $documentation_root" ;;
  esac
done

for docs_category in docs/*; do
  [ -d "$docs_category" ] || continue
  case "$docs_category" in
    docs/architecture|docs/examples|docs/getting-started|docs/guides) ;;
    *) fail "unexpected product documentation category $docs_category" ;;
  esac
done

for project_category in project/*; do
  [ -d "$project_category" ] || continue
  case "$project_category" in
    project/blueprint|project/conformance|project/design|\
    project/implementation-map|project/modules|project/process|\
    project/reports|project/temp) ;;
    *) fail "unexpected project fact category $project_category" ;;
  esac
done

legacy_module_docs=$(find . -path './.git' -prune -o \
  -path './target' -prune -o -type d -path './soma-*/docs' -print)
if [ -n "$legacy_module_docs" ]; then
  fail "module facts must live under project/modules: $legacy_module_docs"
fi

for required_entry in \
  README.md \
  docs/README.md \
  project/README.md \
  project/blueprint/README.md \
  project/design/README.md \
  project/modules/README.md \
  project/implementation-map/README.md \
  project/conformance/README.md \
  project/process/README.md \
  project/reports/README.md; do
  if [ ! -f "$required_entry" ]; then
    fail "missing required role/fact entry $required_entry"
  fi
done

for pattern in \
  '^类型：Project Facts Entry$' \
  '^状态：正式$' \
  '^Owner：' \
  '^事实范围：' \
  '^非事实范围：' \
  '^采用框架：面向角色与场景的项目组织框架 `2\.0\.0-rc\.1`$' \
  '^最后审查日期：'; do
  require_once project/README.md "$pattern"
done

for required_role_entry in \
  'docs/getting-started/README.md' \
  'docs/guides/README.md' \
  'docs/architecture/README.md' \
  'docs/examples/README.md' \
  'project/README.md'; do
  grep -F "$required_role_entry" README.md >/dev/null 2>&1 \
    || fail "root README is missing role entry $required_role_entry"
done

for product_entry in \
  docs/README.md \
  docs/getting-started/README.md \
  docs/guides/README.md \
  docs/architecture/README.md \
  docs/examples/README.md; do
  for pattern in '^类型：' '^状态：当前$' '^Owner：' '^受众：' \
    '^输入事实源：' '^最后审查日期：'; do
    require_once "$product_entry" "$pattern"
  done
done

for product_doc in \
  docs/getting-started/java-v1-install-and-consumer-guide.md \
  docs/guides/performance-and-scale.md; do
  for pattern in '^类型：' '^状态：当前$' '^Owner：' '^受众：' \
    '^输入事实源：' '^事实范围：' '^最后审查日期：'; do
    require_once "$product_doc" "$pattern"
  done
done

for product_index in \
  getting-started/README.md \
  guides/README.md \
  architecture/README.md \
  examples/README.md; do
  grep -F "$product_index" docs/README.md >/dev/null 2>&1 \
    || fail "docs/README.md is missing product entry $product_index"
done

for onboarding_capability in \
  '## 1. 前置条件' \
  '## 2. Maven 配置' \
  '## 11. 验证安装' \
  '## 12. 真实项目试用准入、观察与退出' \
  '## 13. Upgrade、rollback 与 withdrawal' \
  '## 14. Known limitations'; do
  grep -F "$onboarding_capability" \
    docs/getting-started/java-v1-install-and-consumer-guide.md \
    >/dev/null 2>&1 \
    || fail "consumer onboarding is missing $onboarding_capability"
done

for guide_entry in \
  'soma-java-v1-application-developer-manual.docx' \
  'performance-and-scale.md'; do
  grep -F "$guide_entry" docs/guides/README.md >/dev/null 2>&1 \
    || fail "application developer journey is missing $guide_entry"
done

for selection_entry in \
  'soma-java-v1-technical-white-paper.docx' \
  '../guides/performance-and-scale.md' \
  '../../SUPPORT.md'; do
  grep -F "$selection_entry" docs/architecture/README.md >/dev/null 2>&1 \
    || fail "technical selection journey is missing $selection_entry"
done

for contributor_entry in \
  'project/design/README.md' \
  'project/modules/' \
  'project/implementation-map/README.md' \
  'project/process/' \
  './scripts/check.sh'; do
  grep -F "$contributor_entry" CONTRIBUTING.md >/dev/null 2>&1 \
    || fail "contributor journey is missing $contributor_entry"
done

current_categories='blueprint design implementation-map conformance process'

for category in $current_categories; do
  directory="project/$category"
  index_file="$directory/README.md"
  if [ ! -f "$index_file" ]; then
    fail "$directory must contain README.md"
    continue
  fi

  for file in $(find "$directory" -maxdepth 1 -type f -name '*.md' -print |
    sort); do
    require_once "$file" '^类型：'
    require_once "$file" '^状态：正式$'
    require_once "$file" '^Owner：'
    require_once "$file" '^事实范围：'
    require_once "$file" '^非事实范围：'
    require_once "$file" '^最后审查日期：'

    if [ "$file" != "$index_file" ]; then
      require_indexed "$file" "$index_file"
    fi

    if grep -nE '\]\([^)]*/temp/' "$file" >/dev/null 2>&1; then
      fail "$file links to Temporary as a formal fact source"
    fi
  done
done

for file in project/design/*.md; do
  [ "$file" = 'project/design/README.md' ] && continue
  require_once "$file" '^设计层次：`(D0|D1|D2|Q)`([[:blank:]].*)?$'
  require_once "$file" '^主要关注点：'
  require_once "$file" '^上位设计：'
  require_once "$file" '^服务(蓝图|场景)：'
done

for heading in \
  '## 1. 抽象层次' \
  '## 2. 关注点 Owner' \
  '## 3. Blueprint → Design 追踪'; do
  grep -F "$heading" project/design/README.md >/dev/null 2>&1 \
    || fail "project/design/README.md must contain $heading"
done

for file in project/blueprint/*.md; do
  [ "$file" = 'project/blueprint/README.md' ] && continue
  require_once "$file" '^设计约束入口：'
  require_indexed "$file" project/blueprint/README.md
  base_name=$(basename "$file")
  grep -F "$base_name" project/design/README.md >/dev/null 2>&1 \
    || fail "$file is not traced by project/design/README.md"

  if grep -nE '^当前实现参考：|^服务设计：|^#{2,3} .*(当前实现|当前示例|自审|风险和坏味道|待验证事项|当前判定|SOMA V[0-9]+ 可能暴露的问题)' \
      "$file" >/dev/null 2>&1; then
    fail "$file contains Implementation/Conformance content outside Blueprint"
  fi
done

design_owners=$(for file in project/design/*.md; do
  [ "$file" = 'project/design/README.md' ] && continue
  sed -n 's/^Owner：//p' "$file"
done | sort)
duplicate_design_owners=$(printf '%s\n' "$design_owners" | uniq -d)
if [ -n "$duplicate_design_owners" ]; then
  fail "Design Owner must be unique: $duplicate_design_owners"
fi

for file in project/implementation-map/*-map.md; do
  require_once "$file" '^对应 Design：'
  require_once "$file" '^最近实现核对基线：'
done

for category_index in \
  blueprint/README.md \
  design/README.md \
  modules/README.md \
  implementation-map/README.md \
  conformance/README.md \
  process/README.md \
  reports/README.md; do
  grep -F "$category_index" project/README.md >/dev/null 2>&1 \
    || fail "project/README.md is missing fact entry $category_index"
done

for module in \
  soma-annotations \
  soma-processor \
  soma-runtime-core \
  soma-dataflow \
  soma-examples \
  soma-benchmarks; do
  module_doc="project/modules/$module/README.md"
  for pattern in '^类型：Module$' '^状态：正式$' '^Owner：' \
    '^事实范围：' '^非事实范围：' '^最后审查日期：'; do
    require_once "$module_doc" "$pattern"
  done
  grep -F "$module/README.md" project/modules/README.md >/dev/null 2>&1 \
    || fail "$module_doc is not indexed by project/modules/README.md"
  grep -F "project/modules/$module/README.md" "$module/README.md" \
    >/dev/null 2>&1 \
    || fail "$module/README.md does not route to $module_doc"
done

for application in \
  industrial-dynamic-scheduler \
  grassing-individual-simulation \
  real-time-dispatch-rule-engine; do
  application_facts="project/modules/soma-examples/$application"
  application_readme="soma-examples/$application/README.md"
  for file in "$application_facts"/*.md; do
    for pattern in '^类型：应用' '^状态：当前$' \
      "^Owner：$application$" '^对 SOMA 产品规范性：否$' \
      '^最后审查日期：'; do
      require_once "$file" "$pattern"
    done
    require_indexed "$file" project/modules/soma-examples/README.md
  done
  for pattern in '^类型：' '^状态：当前$' "^Owner：$application$" \
    '^对 SOMA 产品规范性：否$' '^最后审查日期：'; do
    require_once "$application_readme" "$pattern"
  done
  grep -F "$application/README.md" docs/examples/README.md >/dev/null 2>&1 \
    || fail "$application_readme is not indexed by docs/examples/README.md"
done

if ! grep -F 'check-real-time-dispatch-rule-engine.sh' \
    project/modules/soma-examples/README.md >/dev/null 2>&1 \
    || ! grep -F 'reference-application=9' \
      project/process/benchmark-governance.md >/dev/null 2>&1; then
  fail 'three-application Gate or nine-profile ownership is not documented'
fi

product_blueprint_count=$(find project/blueprint -maxdepth 1 \
  -type f -name '*.md' ! -name README.md | wc -l | tr -d ' ')
if [ "$product_blueprint_count" -ne 1 ] \
    || [ ! -f project/blueprint/soma-java-product-blueprint.md ]; then
  fail 'project/blueprint must contain exactly one SOMA product Blueprint'
fi
if grep -E 'soma-examples/.*/blueprint' project/design/README.md \
    >/dev/null 2>&1; then
  fail 'application Blueprint must not enter SOMA Design trace'
fi

if grep -R -l '^状态：superseded$' project docs >/dev/null 2>&1; then
  fail 'historical facts must live in Git, not current-checkout tombstones'
fi

current_reference_docs=$(find \
  project/blueprint \
  project/design \
  project/modules \
  project/implementation-map \
  project/conformance \
  project/process \
  project/reports \
  docs \
  -type f -name '*.md' -print | sort)
current_reference_docs="$current_reference_docs
README.md
AGENTS.md
CONTRIBUTING.md
.github/pull_request_template.md"

for current_file in $current_reference_docs; do
  if grep -nE '(由|仍由)[[:blank:]]+active[[:blank:]]+Temporary[^。；]*(拥有|负责|裁决)' \
      "$current_file" >/dev/null 2>&1; then
    fail "$current_file treats Temporary as a current fact owner"
  fi
done

for file in $(find project/reports -maxdepth 1 -type f -name '*.md' \
  ! -name README.md -print | sort); do
  for pattern in '^类型：Report' '^状态：' '^Owner：' '^受众：' \
    '^适用版本：' '^输入事实源：' '^事实范围：' '^最后审查日期：'; do
    require_once "$file" "$pattern"
  done
  require_indexed "$file" project/reports/README.md
done

skill_dir=.agents/skills/use-soma-java
skill_file=$skill_dir/SKILL.md
skill_reference_dir=$skill_dir/references
skill_metadata=$skill_dir/agents/openai.yaml
if [ ! -f "$skill_file" ] || [ ! -f "$skill_metadata" ]; then
  fail 'canonical use-soma-java Skill or UI metadata is missing'
else
  grep -Fqx 'name: use-soma-java' "$skill_file" \
    || fail 'use-soma-java Skill name drifted'
  grep -F 'description: Use when ' "$skill_file" >/dev/null 2>&1 \
    || fail 'use-soma-java positive trigger is missing'
  grep -F 'Do not use for unrelated Java or database work' \
    "$skill_file" >/dev/null 2>&1 \
    || fail 'use-soma-java negative trigger is missing'
  grep -F 'default_prompt: "Use $use-soma-java ' \
    "$skill_metadata" >/dev/null 2>&1 \
    || fail 'use-soma-java UI invocation metadata drifted'
  if grep -E '^(dependencies|policy):' "$skill_metadata" >/dev/null 2>&1 \
      || grep -R -n '^allowed-tools:' "$skill_dir" >/dev/null 2>&1 \
      || [ -d "$skill_dir/scripts" ]; then
    fail 'use-soma-java must remain instruction-only without broad tool grants'
  fi
  if grep -R -nE '\]\((\.\./){3,}(docs|project)/' \
      "$skill_dir" >/dev/null 2>&1; then
    fail 'use-soma-java must not contain repository-relative links that break after installation'
  fi
  grep -F '上述跨仓库路径始终相对于安装时固定的 SOMA source tree' \
    "$skill_file" >/dev/null 2>&1 \
    || fail 'use-soma-java installed-source ownership boundary is missing'
fi

skill_directory_count=$(find . -path './.git' -prune -o \
  -path './target' -prune -o -type d -name use-soma-java -print |
  wc -l | tr -d ' ')
if [ "$skill_directory_count" -ne 1 ]; then
  fail "use-soma-java must have one canonical directory, got $skill_directory_count"
fi

expected_skill_references=$(printf '%s\n' \
  access-and-dataflow-routing.md \
  generated-api-workflow.md \
  lifecycle-performance-troubleshooting.md \
  modeling-and-ownership.md)
actual_skill_references=$(find "$skill_reference_dir" -maxdepth 1 \
  -type f -name '*.md' -exec basename {} \; | LC_ALL=C sort)
if [ "$actual_skill_references" != "$expected_skill_references" ]; then
  fail 'use-soma-java reference set drifted'
fi

root_version=$(sed -n \
  's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | sed -n '1p')
skill_series=$(printf '%s\n' "$root_version" |
  awk -F. '{ print $1 "." $2 ".x" }')
for skill_reference in "$skill_reference_dir"/*.md; do
  grep -F "适用 SOMA 版本：\`$skill_series\`" \
    "$skill_reference" >/dev/null 2>&1 \
    || fail "$skill_reference does not match reactor series $skill_series"
  grep -F '正式 Owner：' "$skill_reference" >/dev/null 2>&1 \
    || fail "$skill_reference does not declare formal Owners"
done

skill_protocol_reference=$skill_reference_dir/generated-api-workflow.md
for protocol_identity in \
  soma-generated-runtime-v12 \
  soma-runtime-java8-v12 \
  soma-runtime-plan-v6 \
  soma-transformation-v5 \
  soma-kernel-v6; do
  grep -F "$protocol_identity" \
    project/design/compatibility-security-and-versioning.md >/dev/null 2>&1 \
    || fail "compatibility Design is missing $protocol_identity"
  grep -F "$protocol_identity" "$skill_protocol_reference" >/dev/null 2>&1 \
    || fail "use-soma-java compatibility anchor is missing $protocol_identity"
done

for consumer_skill_entry in \
  '.agents/skills/use-soma-java/SKILL.md' \
  'project-scoped' \
  'immutable commit SHA'; do
  grep -F "$consumer_skill_entry" README.md >/dev/null 2>&1 \
    || fail "README AI Skill entry is missing $consumer_skill_entry"
done
for consumer_skill_boundary in \
  'project-scoped' \
  '升级时' \
  '卸载只删除' \
  '不执行 bundled script'; do
  grep -F "$consumer_skill_boundary" \
    docs/getting-started/java-v1-install-and-consumer-guide.md >/dev/null 2>&1 \
    || fail "Consumer Guide AI Skill boundary is missing $consumer_skill_boundary"
done

for file in \
  project/design/transformation-model.md \
  project/design/dataflow-execution-model.md \
  project/implementation-map/dataflow-map.md; do
  [ -f "$file" ] || fail "missing current Transformation/DataFlow Owner $file"
done

grep -F 'soma-generated-runtime-v12' \
  project/design/compatibility-security-and-versioning.md >/dev/null 2>&1 \
  || fail 'compatibility Design must declare generated/runtime v12'
grep -F 'soma-primary-locator-layout-v1' \
  project/design/compatibility-security-and-versioning.md >/dev/null 2>&1 \
  || fail 'compatibility Design must declare primary locator layout formula v1'

source_manifest=scripts/manifests/source-release-files.txt
if [ ! -x scripts/source-package-smoke.sh ] \
    || [ ! -f "$source_manifest" ] \
    || [ ! -f scripts/manifests/source-release-README.md ]; then
  fail 'curated source package script, manifest or README template is missing'
else
  LC_ALL=C sort -c "$source_manifest" >/dev/null 2>&1 \
    || fail 'source release manifest must be sorted and unique'
  if grep -E '^(project|tests|\.github)(/|$)' \
      "$source_manifest" >/dev/null 2>&1; then
    fail 'source release manifest includes internal project or evidence surface'
  fi
  grep -F './scripts/source-package-smoke.sh' \
    .github/workflows/release-qualification.yml >/dev/null 2>&1 \
    || fail 'release qualification does not validate curated source package'
  grep -F './mvnw -B -ntp verify' scripts/source-package-smoke.sh \
    >/dev/null 2>&1 \
    || fail 'curated source package is not rebuilt from its extracted content'
  grep -F "archiveBuildVerified=true" \
    .github/workflows/release-qualification.yml >/dev/null 2>&1 \
    || fail 'release qualification does not verify curated source archive build provenance'
  grep -F "agentSkillIncluded=false" \
    .github/workflows/release-qualification.yml >/dev/null 2>&1 \
    || fail 'release qualification does not preserve source archive Skill boundary'
fi

active_temp_topic_count=0
if [ -d project/temp ]; then
  for topic in project/temp/*; do
    [ -d "$topic" ] || continue
    active_temp_topic_count=$((active_temp_topic_count + 1))
    topic_name=$(basename "$topic")
    if [ ! -f "$topic/README.md" ]; then
      fail "$topic must contain README.md"
      continue
    fi
    grep -E '^状态：active([（[:blank:]]|$)' "$topic/README.md" \
      >/dev/null 2>&1 \
      || fail "$topic/README.md must declare an active status"
    grep -F "](temp/$topic_name/README.md)" project/README.md \
      >/dev/null 2>&1 \
      || fail "$topic must be registered by project/README.md"
    for file in $(find "$topic" -type f -name '*.md' -print | sort); do
      require_once "$file" '^类型：Temporary$'
      require_once "$file" '^状态：'
      require_once "$file" '^Owner：'
      require_once "$file" '^事实范围：'
      require_once "$file" '^非事实范围：'
      require_once "$file" '^最后审查日期：'
    done
  done
fi

if [ "$active_temp_topic_count" -eq 0 ]; then
  grep -F '当前没有 active Temporary topic' project/README.md \
    >/dev/null 2>&1 \
    || fail 'project/README.md must state that there is no active Temporary topic'
  if grep -E '\]\(temp/[^)]*/README\.md\)' project/README.md \
      >/dev/null 2>&1; then
    fail 'project/README.md registers Temporary when project/temp is empty'
  fi
elif grep -F '当前没有 active Temporary topic' project/README.md \
    >/dev/null 2>&1; then
  fail 'project/README.md must not claim there is no active Temporary topic'
fi

if [ "$failed" -ne 0 ]; then
  exit 1
fi

sh ./scripts/check-v1-scope.sh

printf '%s\n' 'doc-check: ok'

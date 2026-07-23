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
  count=$(sed -n '1,32p' "$file" | grep -E -c "$pattern" || true)
  if [ "$count" -ne 1 ]; then
    fail "$file must declare exactly one $pattern in its metadata"
  fi
}

markdown_files=$(find . -path './.git' -prune -o -type f -name '*.md' -print | sort)

for file in $markdown_files; do
  if grep -n '[[:blank:]]$' "$file" >/dev/null 2>&1; then
    fail "$file contains trailing whitespace"
  fi

  fence_count=$(grep -c '^```' "$file" || true)
  if [ $((fence_count % 2)) -ne 0 ]; then
    fail "$file contains an unbalanced fenced code block"
  fi

  grep -nEo '\]\([^)]*\.md(#[^)]*)?\)' "$file" 2>/dev/null |
  while IFS=: read -r line_no raw_link; do
    target=$(printf '%s' "$raw_link" | sed -e 's/^](//' -e 's/)$//' -e 's/#.*$//')
    case "$target" in
      http://*|https://*|'') continue ;;
    esac
    if [ ! -f "$(dirname "$file")/$target" ]; then
      printf '%s\n' "doc-check: $file:$line_no unresolved link $target" >&2
      exit 1
    fi
  done || failed=1
done

current_categories='blueprints design implementation-map conformance engineering'

for category in $current_categories; do
  directory="docs/$category"
  index_file="$directory/README.md"
  if [ ! -f "$index_file" ]; then
    fail "$directory must contain README.md"
    continue
  fi

  for file in $(find "$directory" -maxdepth 1 -type f -name '*.md' -print | sort); do
    require_once "$file" '^类型：'
    require_once "$file" '^状态：正式$'
    require_once "$file" '^Owner：'
    require_once "$file" '^事实范围：'
    require_once "$file" '^最后审查日期：'

    if [ "$file" != "$index_file" ]; then
      base_name=$(basename "$file")
      if ! grep -F "$base_name" "$index_file" >/dev/null 2>&1; then
        fail "$file is not indexed by $index_file"
      fi
    fi

    if grep -nE '\]\([^)]*(docs-temp|docs/temp)|\]\([^)]*/temp/' "$file" >/dev/null 2>&1; then
      fail "$file links to Temporary as a formal fact source"
    fi
  done
done

for file in docs/design/*.md; do
  [ "$file" = 'docs/design/README.md' ] && continue
  require_once "$file" '^设计层次：`(D0|D1|D2|Q)`([[:blank:]].*)?$'
  require_once "$file" '^主要关注点：'
  require_once "$file" '^上位设计：'
  require_once "$file" '^服务(蓝图|场景)：'
done

for heading in \
  '## 1. 抽象层次' \
  '## 2. 关注点 Owner' \
  '## 3. Blueprint → Design 追踪'; do
  if ! grep -F "$heading" docs/design/README.md >/dev/null 2>&1; then
    fail "docs/design/README.md must contain $heading"
  fi
done

for file in docs/blueprints/*.md; do
  [ "$file" = 'docs/blueprints/README.md' ] && continue
  require_once "$file" '^设计约束入口：'

  base_name=$(basename "$file")
  if ! grep -F "$base_name" docs/design/README.md >/dev/null 2>&1; then
    fail "$file is not traced by docs/design/README.md"
  fi

  if grep -nE '^当前实现参考：|^服务设计：|^#{2,3} .*(当前实现|当前示例|自审|风险和坏味道|待验证事项|当前判定|SOMA V[0-9]+ 可能暴露的问题)' "$file" >/dev/null 2>&1; then
    fail "$file contains Implementation/Conformance content outside Blueprint responsibility"
  fi
done

design_owners=$(for file in docs/design/*.md; do
  [ "$file" = 'docs/design/README.md' ] && continue
  sed -n 's/^Owner：//p' "$file"
done | sort)
duplicate_design_owners=$(printf '%s\n' "$design_owners" | uniq -d)
if [ -n "$duplicate_design_owners" ]; then
  fail "Design Owner must be unique: $duplicate_design_owners"
fi

for file in docs/implementation-map/*-map.md; do
  require_once "$file" '^对应 Design：'
  require_once "$file" '^最近实现核对基线：'
done

for category_index in \
  docs/blueprints/README.md \
  docs/design/README.md \
  docs/implementation-map/README.md \
  docs/conformance/README.md \
  docs/engineering/README.md; do
  if ! grep -F "${category_index#docs/}" docs/README.md >/dev/null 2>&1; then
    fail "$category_index is not indexed by docs/README.md"
  fi
done

superseded_docs=$(find docs soma-*/docs \
  -type f -name '*.md' -exec grep -l '^状态：superseded$' {} \; | sort)

for file in $superseded_docs; do
  require_once "$file" '^类型：历史设计$'
  require_once "$file" '^状态：superseded$'
  require_once "$file" '^Owner：'
  require_once "$file" '^当前取代者：'
  require_once "$file" '^历史正文基线：commit `[0-9a-f]{40}` 的 `[^`]+`$'

  history_reference=$(sed -n 's/^历史正文基线：commit `\([0-9a-f][0-9a-f]*\)` 的 `\([^`]*\)`$/\1 \2/p' "$file")
  history_commit=${history_reference%% *}
  history_path=${history_reference#* }
  if [ "$history_path" != "$file" ]; then
    fail "$file historical provenance path must match its current path"
  elif ! git cat-file -e "$history_commit:$history_path" 2>/dev/null; then
    fail "$file historical provenance cannot resolve $history_commit:$history_path"
  fi

  historical_heading_count=$(grep -c '^## ' "$file" || true)
  if [ "$historical_heading_count" -ne 1 ] \
      || ! grep -F '## 历史正文' "$file" >/dev/null 2>&1; then
    fail "$file must remain a thin historical tombstone"
  fi
  if ! grep -F "git show $history_commit:$history_path" "$file" >/dev/null 2>&1; then
    fail "$file must expose its Git provenance command"
  fi

  base_name=$(basename "$file")
  if grep -F "]($base_name)" docs/README.md >/dev/null 2>&1 \
      || grep -F "]($base_name#" docs/README.md >/dev/null 2>&1; then
    fail "$file appears in the current docs/README.md navigation"
  fi
done

current_reference_docs=$(find \
  docs/blueprints \
  docs/design \
  docs/implementation-map \
  docs/conformance \
  docs/engineering \
  guides \
  soma-examples/docs \
  -type f -name '*.md' -print | sort)
current_reference_docs="$current_reference_docs
README.md
AGENTS.md
CONTRIBUTING.md
.github/pull_request_template.md
reports/README.md"

for current_file in $current_reference_docs; do
  if grep -nE 'Row Pipeline|Row Cursor|Column Pipeline|\.rows\(|\.rowIndexes\(|findRowIndex\(|rowIndexOf\(|findByMachine|findByOperation|findByRoute|AbstractColumnPipeline|ColumnPipeline' "$current_file" >/dev/null 2>&1; then
    fail "$current_file contains retired current API vocabulary"
  fi

  for historical_file in $superseded_docs; do
    if grep -F "$historical_file" "$current_file" >/dev/null 2>&1; then
      fail "$current_file references superseded Design $historical_file as a current path"
    fi
  done

  grep -nEo '\]\([^)]*\.md(#[^)]*)?\)' "$current_file" 2>/dev/null |
  while IFS=: read -r line_no raw_link; do
    target=$(printf '%s' "$raw_link" | sed -e 's/^](//' -e 's/)$//' -e 's/#.*$//')
    case "$target" in
      http://*|https://*|'') continue ;;
    esac

    target_directory="$(dirname "$current_file")/$(dirname "$target")"
    [ -d "$target_directory" ] || continue
    resolved_target="$(CDPATH= cd -- "$target_directory" && pwd -P)/$(basename "$target")"

    for historical_file in $superseded_docs; do
      if [ "$resolved_target" = "$root_dir/$historical_file" ]; then
        printf '%s\n' "doc-check: $current_file:$line_no links to superseded Design $historical_file" >&2
        exit 1
      fi
    done
  done || failed=1
done

current_example_reports=$(printf '%s\n' \
  soma-examples/docs/runtime-state-schema-examples.md \
  soma-examples/docs/fjsp-runtime-state-example.md \
  soma-examples/docs/fjsp-e2e-scenario.md \
  soma-examples/docs/vrp-runtime-state-example.md \
  soma-examples/docs/simulation-runtime-state-example.md \
  soma-examples/docs/game-runtime-state-example.md)

for file in $current_example_reports; do
  for pattern in '^类型：Report /' '^状态：当前$' '^Owner：' '^受众：' '^适用版本：' '^输入事实源：' '^事实范围：' '^最后审查日期：'; do
    require_once "$file" "$pattern"
  done
  if ! grep -F "$(basename "$file")" soma-examples/docs/README.md >/dev/null 2>&1; then
    fail "$file is not indexed by soma-examples/docs/README.md"
  fi
done

for file in guides/java-v1-install-and-consumer-guide.md guides/development-guide.md; do
  for pattern in '^类型：Report /' '^状态：当前$' '^Owner：' '^受众：' '^适用版本：' '^输入事实源：' '^事实范围：' '^最后审查日期：'; do
    require_once "$file" "$pattern"
  done
  if ! grep -F "$(basename "$file")" guides/README.md >/dev/null 2>&1; then
    fail "$file is not indexed by guides/README.md"
  fi
done

for file in \
  reports/java-v1-goal-execution-status.md \
  reports/current-performance-summary.md \
  reports/2026-07-23-access-model-candidate-scan-governance-report.md \
  reports/2026-07-23-access-model-candidate-scan-performance-report.md \
  reports/2026-07-20-document-architecture-governance-report.md \
  reports/2026-07-20-documentation-framework-cutover-report.md; do
  if [ ! -f "$file" ]; then
    fail "missing current Report $file"
    continue
  fi
  for pattern in '^类型：Report' '^状态：' '^Owner：' '^受众：' '^适用版本：' '^输入事实源：' '^事实范围：' '^最后审查日期：'; do
    require_once "$file" "$pattern"
  done
  if ! grep -F "$(basename "$file")" reports/README.md >/dev/null 2>&1; then
    fail "$file is not indexed by reports/README.md"
  fi
done

if [ -e docs-temp ]; then
  fail 'docs-temp must not exist after the formal cutover'
fi

if [ -e docs/temp/row-pipeline-execution-and-lazy-plan-governance ]; then
  fail 'retired Access Model / Candidate Scan Temporary topic must not exist'
fi

active_temp_topic_count=0
if [ -d docs/temp ]; then
  for topic in docs/temp/*; do
    [ -d "$topic" ] || continue
    active_temp_topic_count=$((active_temp_topic_count + 1))
    topic_name=$(basename "$topic")
    if [ ! -f "$topic/README.md" ]; then
      fail "$topic must contain README.md"
      continue
    fi
    if ! grep -E '^状态：active([（[:blank:]]|$)' "$topic/README.md" >/dev/null 2>&1; then
      fail "$topic/README.md must declare an active status"
    fi
    if ! grep -F "](temp/$topic_name/README.md)" docs/README.md >/dev/null 2>&1; then
      fail "$topic must be registered by docs/README.md"
    fi
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
  if ! grep -F '当前没有 active Temporary topic' docs/README.md >/dev/null 2>&1; then
    fail 'docs/README.md must state that there is no active Temporary topic'
  fi
  if grep -E '\]\(temp/[^)]*/README\.md\)' docs/README.md >/dev/null 2>&1; then
    fail 'docs/README.md must not register a Temporary topic when docs/temp is empty'
  fi
elif grep -F '当前没有 active Temporary topic' docs/README.md >/dev/null 2>&1; then
  fail 'docs/README.md must not claim there is no active Temporary topic'
fi

if [ "$failed" -ne 0 ]; then
  exit 1
fi

sh ./scripts/check-v1-scope.sh

printf '%s\n' 'doc-check: ok'

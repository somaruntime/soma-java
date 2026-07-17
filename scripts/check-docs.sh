#!/bin/sh

set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root_dir"

failed=0

fail() {
  printf '%s\n' "doc-check: $*" >&2
  failed=1
}

markdown_files=$(find . -path './.git' -prune -o -type f -name '*.md' -print | sort)
formal_docs=$(find docs soma-* -type f -name '*.md' -print |
  grep -E '^(docs|soma-[^/]+/docs)/' |
  grep -v '/temp/' |
  grep -v '/README.md$' |
  sort)

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

for file in $formal_docs; do
  line_count=$(wc -l < "$file" | tr -d ' ')
  if [ "$line_count" -gt 500 ]; then
    fail "$file exceeds the 500-line formal-document limit"
  fi

  for field in '状态：正式设计文档' '事实范围：' '非事实范围：' '最后审查日期：'; do
    field_count=$(sed -n '1,12p' "$file" | grep -c "^$field" || true)
    if [ "$field_count" -ne 1 ]; then
      fail "$file must declare exactly one $field metadata field"
    fi
  done

  owner_count=$(sed -n '1,12p' "$file" | grep -c '^Owner：' || true)
  if [ "$owner_count" -ne 1 ]; then
    fail "$file must declare exactly one Owner"
  fi

  if sed -n '1,12p' "$file" | grep -nE '^Owner：.*(/| 与 |、)' >/dev/null 2>&1; then
    fail "$file declares a joint Owner"
  fi

  index_file="$(dirname "$file")/README.md"
  base_name=$(basename "$file")
  if ! grep -F "$base_name" "$index_file" >/dev/null 2>&1; then
    fail "$file is not indexed by $index_file"
  fi

  if grep -nE '\]\([^)]*docs/temp/|\]\([^)]*/temp/' "$file" >/dev/null 2>&1; then
    fail "$file links to a temporary design document"
  fi

  if grep -n '^状态：迁移说明' "$file" >/dev/null 2>&1; then
    fail "$file is a migration placeholder inside formal docs"
  fi
done

expected_blueprint_docs=$(printf '%s\n' \
  fjsp-machine-candidate-frontier-blueprint.md \
  game-runtime-frontier-blueprint.md \
  simulation-runtime-state-blueprint.md \
  vrp-runtime-frontier-blueprint.md)
actual_blueprint_docs=$(find docs/temp -maxdepth 1 -type f -name '*blueprint.md' \
  -exec basename {} \; |
  LC_ALL=C sort)
if [ "$actual_blueprint_docs" != "$expected_blueprint_docs" ]; then
  fail 'docs/temp must retain exactly the four approved long-lived research blueprints'
fi

for file in docs/temp/*blueprint.md; do
  [ -f "$file" ] || continue
  if ! grep -q '^状态：长期研究蓝图$' "$file"; then
    fail "$file must declare long-lived blueprint status"
  fi
  if ! grep -q '^正式事实源：否$' "$file"; then
    fail "$file must declare that it is not a formal fact source"
  fi
done

unexpected_top_level_temp_docs=$(find docs/temp -maxdepth 1 -type f -name '*.md' \
  ! -name 'README.md' ! -name '*blueprint.md' -print)
if [ -n "$unexpected_top_level_temp_docs" ]; then
  fail 'ordinary temporary design documents must live in a topic directory under docs/temp'
fi

if [ -f docs/temp/README.md ]; then
  if ! grep -q '^状态：临时设计索引$' docs/temp/README.md; then
    fail 'docs/temp/README.md must declare temporary-design-index status'
  fi
  if ! grep -q '^正式事实源：否$' docs/temp/README.md; then
    fail 'docs/temp/README.md must declare that it is not a formal fact source'
  fi
fi

for directory in docs/temp/*; do
  [ -d "$directory" ] || continue
  if [ ! -f "$directory/README.md" ]; then
    fail "$directory must contain README.md"
    continue
  fi
  for file in $(find "$directory" -type f -name '*.md' -print | sort); do
    if ! sed -n '1,12p' "$file" | grep -q '^状态：'; then
      fail "$file must declare temporary-design status"
    fi
    if ! sed -n '1,12p' "$file" | grep -q '^正式事实源：否$'; then
      fail "$file must declare that it is not a formal fact source"
    fi
    if ! sed -n '1,12p' "$file" | grep -q '^实施授权：无'; then
      fail "$file must declare that it grants no implementation authority"
    fi
  done
done

if [ "$failed" -ne 0 ]; then
  exit 1
fi

sh ./scripts/check-v1-scope.sh

printf '%s\n' 'doc-check: ok'

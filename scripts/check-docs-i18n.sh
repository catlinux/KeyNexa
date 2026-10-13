#!/usr/bin/env bash
set -euo pipefail

readme_en='README.md'
readme_es='README.es.md'
es_dir='docs/es'

failures=()

all_markdown() {
  find . -type f -name '*.md' \
    -not -path './.git/*' \
    -not -path './.agents/*' \
    -not -path './.agentrelay/*' \
    -not -path './third_party/*' \
    -not -path '*/build/*' \
    | sed 's|^\./||' | sort
}

is_spanish() {
  case "$1" in
    "$readme_es" | "$es_dir"/*) return 0 ;;
    *) return 1 ;;
  esac
}

expected_spanish() {
  case "$1" in
    "$readme_en") printf '%s\n' "$readme_es" ;;
    docs/*) printf '%s\n' "$es_dir/${1#docs/}" ;;
    *) printf '%s\n' "$es_dir/$1" ;;
  esac
}

has_language_line() {
  local first_lines
  first_lines=$(head -n 6 "$1")
  grep -Fq '[English](' <<<"$first_lines" && grep -Fq '[Español](' <<<"$first_lines"
}

while IFS= read -r file; do
  case "$file" in
    'AGENTS.md' | 'CLAUDE.md') continue ;;
  esac
  if is_spanish "$file"; then
    if [ "$file" = "$readme_es" ]; then
      [ -f "$readme_en" ] || failures+=("$file: no English original at '$readme_en'")
    else
      stem="${file#"$es_dir"/}"
      if [ ! -f "$stem" ] && [ ! -f "docs/$stem" ]; then
        failures+=("$file: no English original at '$stem' or 'docs/$stem'")
      fi
    fi
  else
    translation=$(expected_spanish "$file")
    [ -f "$translation" ] || failures+=("$file: no Spanish translation at '$translation'")
  fi
  has_language_line "$file" || failures+=("$file: missing a line with '[English](...)' and '[Español](...)' in the first 6 lines")
done < <(all_markdown)

if [ "${#failures[@]}" -eq 0 ]; then
  echo 'Bilingual documentation check passed.'
  exit 0
fi

echo 'Bilingual documentation check failed:'
printf '  - %s\n' "${failures[@]}"
exit 1

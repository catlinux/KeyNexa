#!/usr/bin/env bash
# Publica el bloque "What went wrong" de un log de Gradle como anotación de GitHub Actions.
# Las anotaciones se leen sin iniciar sesión, a diferencia de los logs completos.
# Uso: scripts/ci-gradle-error.sh gradle.log
set -euo pipefail

log="${1:-gradle.log}"
[ -f "$log" ] || { echo "::error title=Gradle failure::no log file at $log"; exit 0; }

msg="$(awk '/What went wrong/{f=1} f' "$log" | head -n 30 | tr -d '\r')"
[ -n "$msg" ] || msg="$(tail -n 30 "$log" | tr -d '\r')"

# Escapes que exige el comando de workflow ::error::
msg="${msg//'%'/'%25'}"
msg="${msg//$'\n'/'%0A'}"
echo "::error title=Gradle failure::${msg}"

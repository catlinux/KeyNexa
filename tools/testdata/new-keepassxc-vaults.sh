#!/usr/bin/env bash
# Genera con keepassxc-cli las bóvedas ficticias creadas por KeePassXC.
# Solo datos inventados; las contraseñas son públicas (docs/TEST_VAULTS.md).
#
# Uso: tools/testdata/new-keepassxc-vaults.sh [directorio_de_salida]
# Variable opcional: KEEPASSXC_CLI con la ruta de keepassxc-cli.
set -euo pipefail

out="${1:-$(dirname "$0")/../../testdata}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
cli="${KEEPASSXC_CLI:-keepassxc-cli}"
if ! command -v "$cli" >/dev/null 2>&1 && [ -x "/c/Program Files/KeePassXC/keepassxc-cli.exe" ]; then
  cli="/c/Program Files/KeePassXC/keepassxc-cli.exe"
fi
pw='test-password-1'
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Cada orden lee la contraseña maestra de la entrada estándar.
run() { printf '%s\n' "$pw" | "$cli" "$@" >/dev/null; }
run_with_entry_password() { # $1 = contraseña de la entrada, resto = orden
  local epw="$1"; shift
  printf '%s\n%s\n' "$pw" "$epw" | "$cli" "$@" >/dev/null
}

build_content() { # $1 = archivo, resto = opciones comunes (p. ej. -k clave)
  local db="$1"; shift
  run mkdir -q "$@" "$db" Internet
  run mkdir -q "$@" "$db" Internet/Shopping
  run mkdir -q "$@" "$db" Work
  run_with_entry_password 'correct-horse-battery-staple' add -q "$@" -p -u alice@example.org --url https://mail.example.org --notes 'Ficticious mail account.' "$db" 'Internet/Example mail'
  run_with_entry_password 's3cret-Shop!' add -q "$@" -p -u alice --url https://shop.example.org "$db" 'Internet/Shopping/Example shop'
  run_with_entry_password 'work-pass-123' add -q "$@" -p -u a.test --url https://intranet.example.org "$db" 'Work/Intranet'
  run_with_entry_password 'srv-pass-456' add -q "$@" -p -u root --url ssh://server.example.org --notes 'Server notes' "$db" 'Server'
  run_with_entry_password 'v1-password' add -q "$@" -p -u bob --url https://history.example.org --notes v1 "$db" 'Internet/Changing password'
  for v in 2 3 4; do
    run_with_entry_password "v$v-password" edit -q "$@" -p --notes "v$v" "$db" 'Internet/Changing password'
  done
  printf 'Ficticious attachment.\n' > "$tmp/readme.txt"
  head -c 65536 /dev/urandom > "$tmp/random.bin"
  run attachment-import -q "$@" "$db" 'Server' readme.txt "$tmp/readme.txt"
  run attachment-import -q "$@" "$db" 'Server' random.bin "$tmp/random.bin"
  run_with_entry_password 'old-pass' add -q "$@" -p -u old --url https://old.example.org "$db" 'Work/Deleted entry'
  run rm -q "$@" "$db" 'Work/Deleted entry'
}

# AES-KDF + AES-256 (valores por defecto de la orden db-create de esta versión).
rm -f "$out/kpxc-aeskdf.kdbx"
printf '%s\n%s\n' "$pw" "$pw" | "$cli" db-create -q -p -t 100 "$out/kpxc-aeskdf.kdbx" >/dev/null
build_content "$out/kpxc-aeskdf.kdbx"

# Contraseña + archivo de clave creado por KeePassXC.
rm -f "$out/kpxc-keyfile-password.kdbx" "$out/kpxc-keyfile.keyx"
printf '%s\n%s\n' "$pw" "$pw" | "$cli" db-create -q -p -t 100 --set-key-file "$out/kpxc-keyfile.keyx" "$out/kpxc-keyfile-password.kdbx" >/dev/null
build_content "$out/kpxc-keyfile-password.kdbx" -k "$out/kpxc-keyfile.keyx"

# Bóveda vacía.
rm -f "$out/kpxc-empty.kdbx"
printf '%s\n%s\n' "$pw" "$pw" | "$cli" db-create -q -p -t 100 "$out/kpxc-empty.kdbx" >/dev/null
echo "Bóvedas de KeePassXC escritas en $out"

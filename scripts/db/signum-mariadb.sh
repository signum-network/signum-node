#!/usr/bin/env bash
# Sets up MariaDB for Signum Node on Linux and macOS: sync tuning, database and user.
# See DB_SETUP_MARIADB.md. Safe to run again.
set -euo pipefail

DATABASE=signum
DB_USER=signumnode
DB_HOST=localhost
PASSWORD=
BUFFER_POOL=1G
CONFIG_DIR=
CLIENT=
SETUP=1
TUNING=1
RESTART=1
DRY_RUN=0
CONFIG_FILE_NAME=99-signum.cnf

usage() {
  cat <<EOF
Usage: $0 [options]

  --password PW        password for the node's database user (default: asked for)
  --database NAME      database name (default: $DATABASE; e.g. signum_testnet)
  --user NAME          database user (default: $DB_USER)
  --user-host HOST     host the node connects from (default: $DB_HOST; '%' for any, e.g. Docker)
  --buffer-pool SIZE   innodb_buffer_pool_size, up to about half the RAM (default: $BUFFER_POOL)
  --config-dir DIR     where to write $CONFIG_FILE_NAME (default: detected)
  --client CMD         command that opens an admin session (default: 'sudo mariadb' on Linux, 'mariadb' on macOS)
  --skip-tuning        only create database and user
  --skip-setup         only write the tuning settings
  --no-restart         don't restart MariaDB after writing the settings
  --dry-run            print what would be done, change nothing
EOF
}

die() { echo "Error: $*" >&2; exit 1; }
info() { echo "==> $*"; }

parse_args() {
  while [ $# -gt 0 ]; do
    case $1 in
      --password) PASSWORD=${2-}; shift ;;
      --database) DATABASE=${2-}; shift ;;
      --user) DB_USER=${2-}; shift ;;
      --user-host) DB_HOST=${2-}; shift ;;
      --buffer-pool) BUFFER_POOL=${2-}; shift ;;
      --config-dir) CONFIG_DIR=${2-}; shift ;;
      --client) CLIENT=${2-}; shift ;;
      --skip-tuning) TUNING=0 ;;
      --skip-setup) SETUP=0 ;;
      --no-restart) RESTART=0 ;;
      --dry-run) DRY_RUN=1 ;;
      -h|--help) usage; exit 0 ;;
      *) usage >&2; die "unknown option: $1" ;;
    esac
    shift
  done
  [[ $DATABASE =~ ^[A-Za-z0-9_]+$ ]] || die "--database may only contain letters, digits and _"
  [[ $DB_USER =~ ^[A-Za-z0-9_]+$ ]] || die "--user may only contain letters, digits and _"
  [[ $DB_HOST =~ ^[A-Za-z0-9_.%-]+$ ]] || die "invalid --user-host: $DB_HOST"
  [[ $BUFFER_POOL =~ ^[0-9]+[KMG]?$ ]] || die "--buffer-pool must look like 512M or 2G"
  return 0
}

ask_password() {
  [ "$SETUP" = 1 ] && [ -z "$PASSWORD" ] || return 0
  [ -t 0 ] || die "--password is required when not run in a terminal"
  read -r -s -p "Password for the database user $DB_USER: " PASSWORD; echo
  [ -n "$PASSWORD" ] || die "the password must not be empty"
}

is_macos() { [ "$(uname -s)" = Darwin ]; }

# sudo only where needed: not as root, not on macOS (Homebrew runs as the user)
as_admin() {
  if [ "$(id -u)" = 0 ] || is_macos; then "$@"; else sudo "$@"; fi
}

run() {
  if [ "$DRY_RUN" = 0 ]; then "$@"; return; fi
  if [ "$1" = as_admin ]; then
    shift
    if [ "$(id -u)" != 0 ] && ! is_macos; then set -- sudo "$@"; fi
  fi
  echo "[dry-run] $*"
}

detect_config_dir() {
  if [ -n "$CONFIG_DIR" ]; then echo "$CONFIG_DIR"; return; fi
  if is_macos; then
    command -v brew >/dev/null || die "Homebrew not found; pass --config-dir"
    echo "$(brew --prefix)/etc/my.cnf.d"; return
  fi
  local d
  for d in /etc/mysql/mariadb.conf.d /etc/my.cnf.d /etc/mysql/conf.d; do
    if [ -d "$d" ]; then echo "$d"; return; fi
  done
  die "no MariaDB config directory found; pass --config-dir"
}

tuning_config() {
  cat <<EOF
# Written by Signum Node's scripts/db/signum-mariadb.sh, see DB_SETUP_MARIADB.md.
# A power loss can lose about the last second of commits; the node syncs those blocks again.
[mariadb]
innodb_flush_log_at_trx_commit = 2
innodb_log_file_size = 1G
innodb_buffer_pool_size = $BUFFER_POOL
EOF
}

write_tuning() {
  local dir file
  dir=$(detect_config_dir)
  file="$dir/$CONFIG_FILE_NAME"
  info "Writing $file"
  if [ "$DRY_RUN" = 1 ]; then tuning_config | sed 's/^/[dry-run]   /'; return; fi
  as_admin mkdir -p "$dir"
  tuning_config | as_admin tee "$file" >/dev/null
}

restart_mariadb() {
  info "Restarting MariaDB"
  if is_macos; then
    local formula
    formula=$(brew services list | awk '$1 ~ /^mariadb/ {print $1; exit}')
    run brew services restart "${formula:-mariadb}"
  elif [ -d /run/systemd/system ]; then
    run as_admin systemctl restart mariadb
  else
    run as_admin service mariadb restart
  fi
}

client_cmd() {
  if [ -n "$CLIENT" ]; then echo "$CLIENT"
  elif is_macos || [ "$(id -u)" = 0 ]; then echo "mariadb"
  else echo "sudo mariadb"
  fi
}

sql_string() { # quote as a SQL string literal
  local s=${1//\\/\\\\}
  printf "'%s'" "${s//\'/\'\'}"
}

setup_sql() {
  local account pw
  account="'$DB_USER'@'$DB_HOST'"
  pw=$(sql_string "$PASSWORD")
  cat <<EOF
CREATE DATABASE IF NOT EXISTS \`$DATABASE\`;
CREATE USER IF NOT EXISTS $account IDENTIFIED BY $pw;
ALTER USER $account IDENTIFIED BY $pw;
GRANT ALL PRIVILEGES ON \`$DATABASE\`.* TO $account;
FLUSH PRIVILEGES;
EOF
}

admin_sql() { # admin_sql <args for the client>; SQL on stdin
  local -a cmd
  read -r -a cmd <<< "$(client_cmd)"
  "${cmd[@]}" "$@"
}

setup_database() {
  info "Creating database $DATABASE and user '$DB_USER'@'$DB_HOST'"
  if [ "$DRY_RUN" = 1 ]; then echo "[dry-run] $(client_cmd) <<< (CREATE DATABASE / CREATE USER / GRANT)"; return; fi
  setup_sql | admin_sql
}

show_settings() {
  [ "$DRY_RUN" = 1 ] && return
  # without a restart the server still runs with the old values
  [ "$TUNING" = 1 ] && [ "$RESTART" = 0 ] && return
  info "Active settings"
  echo "SHOW VARIABLES WHERE Variable_name IN ('innodb_flush_log_at_trx_commit', 'innodb_log_file_size', 'innodb_buffer_pool_size');" \
    | admin_sql --table
  if [ "$TUNING" = 1 ] && [ "$RESTART" = 1 ] \
    && [ "$(echo 'SELECT @@innodb_flush_log_at_trx_commit;' | admin_sql -N -B)" != 2 ]; then
    echo "Warning: the tuning is not active. Check that MariaDB reads $(detect_config_dir)." >&2
  fi
}

main() {
  parse_args "$@"
  ask_password
  if [ "$TUNING" = 1 ]; then
    write_tuning
    if [ "$RESTART" = 1 ]; then restart_mariadb; else info "Restart MariaDB to apply the settings"; fi
  fi
  [ "$SETUP" = 1 ] && setup_database
  show_settings
  cat <<EOF

Done. Add to conf/node.properties:
DB.Url=jdbc:mariadb://localhost:3306/$DATABASE
DB.Username=$DB_USER
DB.Password=<the password you chose>
EOF
}

main "$@"

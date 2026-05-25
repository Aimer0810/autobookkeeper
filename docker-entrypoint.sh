#!/usr/bin/env sh
set -eu

if [ "${DATABASE_URL:-}" != "" ]; then
  case "$DATABASE_URL" in
    postgresql://*)
      export DATABASE_URL="jdbc:$DATABASE_URL"
      ;;
  esac
fi

exec java -jar /app/autobookkeeper.jar "$@"

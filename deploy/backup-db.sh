#!/usr/bin/env bash
# 競馬アプリのDB(Postgres)を毎日バックアップする。cronから呼ぶ(deploy/MIGRATION.md、/etc/cron.d/keiba-db-backup)。
#
#   1. pg_dump を gzip して ~/db-backups/ に保存(14日分を残す)
#   2. ダンプが壊れていないか検証(gzipの整合性、サイズ、主要テーブルの有無)。壊れていたら残さない
#   3. Discordにファイルを添付して送る(VPSが壊れても、前日までのデータが残るようにするため)
#      送り先: .env の BACKUP_WEBHOOK_URL (専用チャンネル用)。無ければ DISCORD_WEBHOOK_URL (通知用)
#
# 背景: 2026-10-29に、AWS無料プランの終了でEC2が止まり、DBに到達できなくなった。
#       DBはサーバーの中にしか無かったので、サーバー外にも毎日コピーを置く。
# DBには秘密情報は入っていない(Webhook URLなどは .env にあり、DBには入らない)。
set -euo pipefail

REPO="${REPO:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/db-backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
MIN_BYTES=50000                       # 現在約1.1MB。これより小さければ異常とみなす

cd "$REPO"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# .env から値を取り出す(source はしない。値は表示しない)
env_value() {
  grep -E "^$1=" "$REPO/.env" 2>/dev/null | head -1 | cut -d= -f2- || true
}
WEBHOOK="$(env_value BACKUP_WEBHOOK_URL)"
[ -n "$WEBHOOK" ] || WEBHOOK="$(env_value DISCORD_WEBHOOK_URL)"

notify() {   # 本文だけをDiscordへ(失敗しても止めない)
  [ -n "$WEBHOOK" ] || return 0
  local json
  json=$(printf '{"content":"%s"}' "$1")
  curl -fsS --max-time 30 -H 'Content-Type: application/json' -d "$json" "$WEBHOOK" >/dev/null 2>&1 || true
}

STAMP="$(date +%Y%m%d-%H%M)"
OUT="$BACKUP_DIR/keibadb-$STAMP.sql.gz"
TMP="$OUT.tmp"

fail() {
  echo "$(date '+%F %T') 失敗: $1" >&2
  rm -f "$TMP"
  notify "🚨 DBバックアップに失敗しました ($STAMP): $1"
  exit 1
}

# </dev/null: docker compose exec が標準入力を読んで、呼び出し側(cron・パイプ)の入力を食べないようにする
docker compose exec -T db pg_dump -U keiba keibadb </dev/null | gzip > "$TMP" || fail "pg_dumpが失敗"
gzip -t "$TMP" 2>/dev/null || fail "gzipが壊れている"
SIZE=$(stat -c %s "$TMP")
[ "$SIZE" -ge "$MIN_BYTES" ] || fail "サイズが小さすぎる(${SIZE}バイト)"
# grep -q は最初の一致で終わり、zcatがSIGPIPEで失敗扱いになる(pipefail)ので、grep -c で最後まで読む
[ "$(zcat "$TMP" | grep -c '^COPY public.race_result_record ')" -ge 1 ] || fail "主要テーブル(race_result_record)が無い"

mv "$TMP" "$OUT"
chmod 600 "$OUT"
find "$BACKUP_DIR" -name 'keibadb-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
echo "$(date '+%F %T') OK: $OUT (${SIZE}バイト) 保存数: $(ls "$BACKUP_DIR"/keibadb-*.sql.gz | wc -l)"

if [ -n "$WEBHOOK" ]; then
  KB=$(( SIZE / 1024 ))
  MSG=$(printf '💾 DBバックアップ %s (%sKB)' "$STAMP" "$KB")
  PAYLOAD=$(printf '{"content":"%s"}' "$MSG")
  if curl -fsS --max-time 120 -F "payload_json=$PAYLOAD" -F "files[0]=@$OUT;filename=keibadb-$STAMP.sql.gz" "$WEBHOOK" >/dev/null 2>&1; then
    echo "$(date '+%F %T') Discordへ送信OK"
  else
    echo "$(date '+%F %T') Discordへの送信に失敗(ローカルには保存済み)" >&2
  fi
else
  echo "$(date '+%F %T') Webhook未設定のためDiscordへは送っていません" >&2
fi

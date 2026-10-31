# EC2 → さくらのVPS 移行手順(2026-10-31)

背景: AWS無料プランの終了でEC2が止まった(`CLAUDE.md`参照)。競馬アプリ(keiba-app-new)とDiscordボット(discord-task-bot)を、
さくらのVPS(Ubuntu 24.04、メモリ2GB、管理ユーザー`ubuntu`)に移す。外部公開は **SSH(22)だけ**。アプリ(8080)は **Tailscale経由で自分の端末からだけ**見る。

## 方針
- **開催日(土日)の出走前後は切り替えない**。お気に入りの出走が無い時間(例: 日曜の最終レースの後)に切り替える。
- **二重に動かさない**(Discord通知が重複する)。切り替えの瞬間は、必ず古いEC2側を先に止めて、最終のバックアップを取り直してから、VPS側を起動する。
- 秘密情報(`.env`、`service_account.json`)は、git・Discord・チャットに載せない。PC経由の`scp`だけで運ぶ。

## 手順

### 0. 準備(開催日でもできる。古いEC2には影響しない)
PCのPowerShellで(`<EC2のIP>`=184.198.100.197、`<VPSのIP>`はさくらの画面の値):

1. EC2の競馬アプリの`.env`(`DISCORD_WEBHOOK_URL`が入っている。バックアップに含まれていない)をPCに取る
   ```
   scp -i $HOME\.ssh\keiba_aws ec2-user@<EC2のIP>:~/keiba-app-new/.env $HOME\Documents\wksp\_aws_backup_20261031\keiba-app.env
   ```
2. セットアップスクリプトとバックアップをVPSへ送る
   ```
   scp -i $HOME\.ssh\keiba_aws deploy\setup-vps.sh ubuntu@<VPSのIP>:~/
   scp -i $HOME\.ssh\keiba_aws $HOME\Documents\wksp\_aws_backup_20261031\* ubuntu@<VPSのIP>:~/
   ```
3. VPSに入ってセットアップを流す
   ```
   ssh -i $HOME\.ssh\keiba_aws ubuntu@<VPSのIP>
   sed -i 's/\r$//' setup-vps.sh && sudo bash setup-vps.sh
   exit      # dockerグループを反映するため、入り直す
   ```
4. 入り直して、Tailscaleに参加させる(表示されるURLを、自分のPCのブラウザで開いて承認する)
   ```
   sudo tailscale up
   tailscale ip -4        # 100.x.y.z が、このサーバーのTailscale上のIP
   ```
5. 別の窓で、もう一度SSHで入れることを確認する(パスワードログインを無効にした後でも、鍵で入れること)。

### 1. アプリの配置(まだ起動しない)
```
git clone https://github.com/akio4869462-max/keiba-app-new.git ~/keiba-app-new
git clone https://github.com/akio4869462-max/discord-task-bot.git ~/discord-task-bot
```
- 競馬アプリの`.env`を作る(`~/keiba-app-new/.env`)。EC2から取った`keiba-app.env`の`DISCORD_WEBHOOK_URL`を使い、次を足す:
  ```
  POSTGRES_PASSWORD=<長いランダムな文字列。例: openssl rand -hex 24>
  APP_BIND=127.0.0.1
  ```
- ボットのデータと`.env`を展開する: `tar xzf ~/discord-bot-data.tgz -C ~`(`~/discord-task-bot/data/`と`.env`ができる)。

### 2. 切り替え(開催が無い時間に)
1. **古いEC2のアプリとボットを止める**(EC2のブラウザ端末で):
   ```
   cd ~/keiba-app-new && docker compose stop app
   cd ~/discord-task-bot && docker compose stop
   ```
2. **最終のバックアップを取り直す**(EC2で。DBはappを止めた後なので整合する):
   ```
   cd ~/keiba-app-new && docker compose exec -T db pg_dump -U keiba keibadb | gzip > ~/keibadb_final.sql.gz
   cd ~ && tar czf ~/discord-bot-data-final.tgz discord-task-bot/data discord-task-bot/.env
   ```
   PCに取り(`scp`)、VPSに送って、`~/discord-bot-data-final.tgz`を展開し直す。
3. VPSでDBを**アプリより先に**復元する(アプリが先に起動すると、テーブルが空で作られて衝突する):
   ```
   cd ~/keiba-app-new
   docker compose up -d db
   sleep 15
   gunzip -c ~/keibadb_final.sql.gz | docker compose exec -T db psql -U keiba -d keibadb
   ```
4. VPSでアプリとボットを起動する:
   ```
   cd ~/keiba-app-new && docker compose up -d --build
   cd ~/discord-task-bot && docker compose up -d --build
   ```
5. Tailscale経由で見られるようにする(`127.0.0.1:8080`をTailscaleのネットワークに出す):
   ```
   sudo tailscale serve --bg --tcp 8080 tcp://localhost:8080
   ```
6. 確認: 自分のPC(Tailscaleに参加済み)のブラウザで `http://<VPSのTailscale IP>:8080/status` を開く。
   `/predict`の最初の表示は、キャッシュが空なので数分〜十数分かかる(`CLAUDE.md`参照。連打しない)。

### 3. GitHubの設定を切り替える
両方のリポジトリ(keiba-app-new、discord-task-bot)の Secrets を更新する:
- `EC2_HOST` = VPSのIP、`EC2_USER` = `ubuntu`、`EC2_SSH_KEY` = `keiba_aws`(秘密鍵)の中身
  (秘密鍵は`$HOME\.ssh\keiba_aws`。**他人や私に見せない**。GitHubのSecret欄に貼るだけ)
- keiba-app-newの`deploy.yml`が動くことを確認する(pushするか、Actionsから再実行)。
- discord-task-botの Watchdog(15分おき)が「異常なし」になることを確認する。

### 4. 片付け
- 数日動かして問題が無ければ、AWSのEC2を**終了**し、Elastic IP・ボリュームを削除する(課金を止める)。
- (済) `CLAUDE.md`の本番URL、`eval_forward_csv.py`のURLをTailscale経由(`http://100.67.46.16:8080`)に直した。

## 注意
- さくらのVPSの2Gプランは、ストレージ200GBのオプションと併用(初回に手数料)。毎月払い。
- Dockerはufw(ファイアウォール)を迂回してポートを公開する。**ポートは必ず`127.0.0.1`にだけ公開する**(`docker-compose.yml`の`APP_BIND`、DBの`127.0.0.1:5432`)。
- 開催日の出走の直前にデプロイ・切り替えをしない(お気に入り通知が止まる)。

#!/usr/bin/env bash
# 新しいVPS(Ubuntu 24.04)の初期セットアップ。何度流しても安全(冪等)なように書いてある。
#
#   やること: パッケージ更新 / SSHの強化 / ファイアウォール(ufw) / fail2ban / 自動更新 /
#             スワップ / Docker(ログのローテーション付き) / Tailscale / タイムゾーン
#   やらないこと: アプリの配置・DBの復元(deploy/MIGRATION.md の続きの手順で行う)
#
# 使い方(VPSにSSHで入ってから):
#   sed -i 's/\r$//' setup-vps.sh      # Windowsで作った改行コードを直す
#   sudo bash setup-vps.sh
#
# 今のSSH接続は切れない。新しい設定を確認してから反映するので、鍵の登録が無ければSSHの強化はスキップする。
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "sudo で実行してください: sudo bash $0" >&2
  exit 1
fi

TARGET_USER="${SUDO_USER:-ubuntu}"
export DEBIAN_FRONTEND=noninteractive

echo "=== 1. パッケージの更新と必要なものの導入"
apt-get update -y
apt-get upgrade -y
apt-get install -y ca-certificates curl gnupg git ufw fail2ban unattended-upgrades

echo "=== 2. タイムゾーン(Asia/Tokyo)"
timedatectl set-timezone Asia/Tokyo

echo "=== 3. スワップ(2GB)。メモリ2GBでのビルド中のメモリ不足を防ぐ"
if ! swapon --show | grep -q '^/swapfile'; then
  if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
  fi
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo "vm.swappiness=10" > /etc/sysctl.d/99-swappiness.conf
sysctl -p /etc/sysctl.d/99-swappiness.conf >/dev/null

echo "=== 4. SSHの強化(パスワードログインと rootログインを禁止)"
AUTH="/home/${TARGET_USER}/.ssh/authorized_keys"
if [ -s "$AUTH" ]; then
  # OpenSSHは「最初に読んだ設定が優先」なので、cloud-initの50-*.confより先に読まれる 00- で置く
  cat > /etc/ssh/sshd_config.d/00-hardening.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
EOF
  mkdir -p /run/sshd      # Ubuntu 24.04では、sshdの設定検証(sshd -t)に必要
  sshd -t
  systemctl reload ssh
  echo "  -> パスワードログインを無効にしました(公開鍵のみ)"
else
  echo "  !! ${AUTH} に公開鍵が無いため、締め出し防止のためSSHの強化をスキップしました" >&2
fi

echo "=== 5. fail2ban(SSHへの総当たりを自動で遮断)"
cat > /etc/fail2ban/jail.d/sshd.local <<'EOF'
[sshd]
enabled = true
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable --now fail2ban
systemctl restart fail2ban

echo "=== 6. セキュリティ更新の自動適用"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF

echo "=== 7. Docker(公式リポジトリ) とログのローテーション"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
# コンテナのログが無限に増えてディスクを埋めないようにする(EC2でディスクが満杯になった反省)
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
systemctl enable --now docker
systemctl restart docker
usermod -aG docker "$TARGET_USER"

echo "=== 8. Tailscale(自分の端末からだけアプリを見るためのVPN)"
if ! command -v tailscale >/dev/null 2>&1; then
  curl -fsSL https://tailscale.com/install.sh | sh
fi
systemctl enable --now tailscaled

echo "=== 9. ファイアウォール(ufw): 外からはSSH(22)だけ。Tailscale経由は全て許可"
ufw --force reset >/dev/null
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow in on tailscale0
ufw --force enable
ufw status verbose

cat <<EOF

================ 完了 ================
次にやること:
  1) 一度ログアウトして入り直す(dockerグループの反映のため):  exit  →  再度ssh
  2) Tailscaleにこのサーバーを参加させる:
       sudo tailscale up
     表示されるURLを、自分のPCのブラウザで開いて承認する。
  3) 新しいSSH接続を別の窓で試して、入れることを確認してから、今の窓を閉じる。
  4) deploy/MIGRATION.md の続き(アプリの配置・DBの復元)へ。
注意: Dockerはufwを迂回してポートを公開する。アプリのポートは必ず 127.0.0.1 にだけ公開すること。
EOF

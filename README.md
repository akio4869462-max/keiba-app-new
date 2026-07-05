# Keiba App

## 概要

競馬の出馬表を取得し、過去走データやオッズを分析して独自予想スコアを算出する競馬予想Webアプリです。

お気に入り馬・騎手の管理機能やDiscord通知機能も実装しており、レース情報の取得から予想結果の出力までを一貫して行えます。

JavaおよびSpring Bootの学習とポートフォリオ作成を目的として開発しています。

現在はサービス分割・テストコード整備・Docker対応を進めながら継続的に改善を行っています。

---

## 工夫した点

- Yahoo!競馬へのアクセス回数削減のため30分キャッシュを実装し、レスポンス速度と負荷を改善
- 過去走データ取得結果をMapでキャッシュし、同一馬への重複スクレイピングを防止
- 結果確定データはキャッシュせず、発走時刻から一定時間経過したかを判定してから取得することで、未確定の結果を誤って保存しないようにガード
- 同時に複数のスクレイピングが走らないようAtomicBooleanで排他制御し、小規模なサーバー資源を保護

---

## 起動方法
### Docker Composeで起動

```bash
docker compose up -d
```

ブラウザで以下へアクセス

http://localhost:8080/races

## 主な機能
### 出馬表取得
- Yahoo!競馬から出馬表を取得
- レース一覧表示
- レース情報キャッシュ（30分）

### 過去走分析
- 各馬の過去3走を取得
- 着順
- 人気
- レースグレード（GI/GII/GIII/L/OP/条件戦）

を収集

### 予想機能

独自ロジックにより予想スコアを算出

評価項目例

- 前走着順
- 前走人気
- レース格
- オッズ

予想理由も表示

### 騎手データの活用

- 騎手プロフィールページから今年の勝率・連対率を取得
- 予想スコアに反映

### CSV出力

- 予想結果をCSV形式で出力

出力例
```text
レース名
馬名
予想スコア
予想順位
```

### 予想結果検証

- 開催週にレースURLを自動記録し、土日日中30分おき＋月曜早朝にレース結果を確定・DBへ蓄積
- オッズ帯別・予想スコア帯別・週別推移・回収率(ROI)で予想精度を検証
- レース単位で実際の着順と予想順位を並べて確認できる一覧ページ

### 今日のお気に入りレース

- お気に入り馬・騎手が当日出走するレースを発走時刻順に一覧表示

### 出馬表の表示

- 枠番をJRA公式カラーのバッジで表示
- 馬名・騎手名からnetkeibaの詳細ページへ遷移可能

### お気に入り馬管理
- 登録 
- 一覧表示 
- 削除 
- 重複防止

### お気に入り騎手管理
- 登録 
- 一覧表示 
- 削除 
- 重複防止

### Discord通知
- お気に入り馬の出走通知（馬番付き）
- お気に入り騎手の騎乗通知（馬番付き）
- 発走5分前を通知タイミングとして設定
- 重複通知防止 

### 定期実行
Spring Schedulerによる自動チェック（土日9〜17時のタイムゾーンを明示指定して実行）

---

## 使用技術

| 技術 | 内容 |
|--------|--------|
| Java | 21 |
| Spring Boot | 3.3 |
| Spring Data JPA | ORM |
| Thymeleaf | テンプレートエンジン |
| PostgreSQL | 本番DB |
| H2 Database | テスト・開発用 |
| Jsoup | スクレイピング |
| Maven | ビルド管理 |
| Docker | コンテナ化 |
| Docker Compose | 開発環境構築 |
| Discord Webhook | 通知機能 |
| Git / GitHub | バージョン管理 |

---

## 開発規模

- サービスクラス: 14クラス
- 単体テスト: 39件
- Docker / PostgreSQL 対応
- AWS EC2への自動デプロイ（GitHub Actions）

---

## システム構成

```text
Yahoo!競馬
      ↓
    Jsoup
      ↓
 WebScraper
      ↓
 RaceService
 ┌───────────────┬────────────────┐
 ↓               ↓                ↓
RaceParser  HorseEnrichment  RaceCache
                 ↓
          PredictionService
                 ↓
             Thymeleaf
                 ↓
             CSV出力
```

```text
お気に入り馬・騎手
        ↓
   PostgreSQL
        ↓
RaceNotificationService
        ↓
 Discord通知
```

```text
土日日中: レースURLを自動記録（TrackedRaceUrl）
        ↓
30分おき＋月曜早朝: 発走から40分経過したレースのみ結果確定を試行
        ↓
RaceResultCollectionService
        ↓
   RaceResultRecord（PostgreSQL）
        ↓
オッズ帯別・スコア帯別・週別・ROIで検証（/results, /results/races）
```

---

## テスト

JUnit5による単体テストを実施

- PredictionServiceTest
- OddsParserTest
- RaceParserServiceTest
- RaceCacheServiceTest
- HorseEnrichmentServiceTest
- HorseTest
- AiPromptServiceTest
- RaceResultStatsServiceTest
- RaceResultCollectionServiceTest

合計39テスト（全件成功）

---

## CI/CD

GitHub Actionsを利用して自動テスト・自動デプロイを実施しています。

- push時にJUnitテストを自動実行（Java 21 / Maven）
- mainブランチへのpushでAWS EC2へ自動デプロイ（SSH経由でdocker compose up --build）

---

## 画面イメージ

### レース一覧画面

![レース一覧](images/races.png)

### お気に入り管理画面

![お気に入り](images/favorite.png)

### Discord通知

![通知](images/notification.png)

---

## 学習した技術

本アプリ開発を通じて以下を学習しました。

- Java基礎 
- オブジェクト指向設計 
- Spring Boot 
- Spring Data JPA 
- HTML/CSS 
- スクレイピング（Jsoup） 
- Git/GitHub 
- Schedulerによる定期実行（タイムゾーン考慮） 
- @Asyncによる非同期処理 
- Discord Webhook連携 
- CSV出力処理

---

## 今後の改善予定
- REST API化
- オッズのみキャッシュ期間を短縮し、リアルタイム性を向上
- 予想ロジック精度向上（スコア帯別精度の検証結果をもとに継続改善）
- 騎手成績データの拡充

---

## 開発目的

JavaおよびSpring Bootの学習を目的として開発しています。

実際の競馬データを利用し、

- スクレイピング 
- データベース操作 
- Webアプリ開発 
- バッチ処理 
- 外部サービス連携

を一つのアプリケーションで経験することを目標として開発しました。
# Keiba App

## 概要

競馬の出馬表を取得し、過去走データやオッズを分析して独自予想スコアを算出する競馬予想Webアプリです。

お気に入り馬・騎手の管理機能やDiscord通知機能も実装しており、レース情報の取得から予想結果の出力までを一貫して行えます。

JavaおよびSpring Bootの学習とポートフォリオ作成を目的として開発しています。

---

## 工夫した点

- Yahoo!競馬へのアクセス回数を減らすため30分キャッシュを実装
- 過去走データ取得結果をMapで保持し同一馬への再アクセスを削減
- Discord通知の重複送信を防止
- 予想結果をCSV出力できるように実装

---

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

### CSV出力

- 予想結果をCSV形式で出力

出力例
```text
レース名
馬名
予想スコア
予想順位
```

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
- お気に入り馬の出走通知 
- お気に入り騎手の騎乗通知 
- 重複通知防止 

### 定期実行
Spring Schedulerによる自動チェック

---

## 使用技術

| 技術 | 内容 |
|--------|--------|
| Java | 21 |
| Spring Boot | 3.3 |
| Spring Data JPA | ORM |
| Thymeleaf | テンプレートエンジン |
| H2 Database | 開発用DB |
| Jsoup | スクレイピング |
| Maven | ビルド管理 |
| Discord Webhook | 通知機能 |
| Git / GitHub | バージョン管理 |

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
      ↓
 PredictionService
      ↓
 Thymeleaf画面
      ↓
 CSV出力
```

```text
お気に入り馬・騎手
        ↓
    H2 Database
        ↓
RaceNotificationService
        ↓
 Discord通知
```

---

## 画面イメージ

準備中

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
- Schedulerによる定期実行 
- Discord Webhook連携 
- CSV出力処理

---

## 今後の改善予定
- 予想ロジック精度向上 
- PostgreSQL対応
- Docker対応 
- AWSデプロイ 
- REST API化 
- テストコード追加
- RaceServiceのリファクタリング

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
# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Yahoo!スポーツ競馬をスクレイピングし、独自ロジックで予想スコアを算出する競馬予想Webアプリ（Java 21 / Spring Boot 3.3）。
単なる表示アプリではなく、**予想 → Discord通知 → レース結果の自動収集 → 予想精度の自己検証**までが自動で回るループになっている点が設計の中心。

ユーザーは日本語話者。応答・コミットメッセージ・コード内コメントはすべて日本語で書く。

## コマンド

```bash
# ビルド・テスト
./mvnw test                              # 全テスト
./mvnw test -Dtest=PredictionServiceTest # 単一テストクラス
./mvnw test -Dtest=PredictionServiceTest#calculateScore_shouldIgnoreInvalidOdds  # 単一メソッド
./mvnw -q compile                        # コンパイルのみ

# ローカル起動（DBのみDocker、アプリはホストで起動する構成が扱いやすい）
docker compose up -d db
./mvnw spring-boot:run                   # http://localhost:8080/races
docker compose down                      # 検証後は必ず片付ける

# ローカルDBに直接クエリ
docker exec keiba-postgres psql -U keiba -d keibadb -c "SELECT ..."
```

**`ApiControllerTest`は現在ApplicationContextの起動に失敗する**（未コミットの作りかけREST APIに付随するテスト。修正は保留中のタスク）。
それ以外の全テストを流したい場合は除外する:

```bash
./mvnw test -Dtest='!ApiControllerTest' -DfailIfNoTests=false
```

## デプロイ

`main`へpushすると GitHub Actions（`.github/workflows/deploy.yml`）がEC2にSSHし、`git pull` → `docker compose up -d --build` を実行する。ブランチ運用はせず main へ直接pushする方針。

- 本番URL: `http://107.22.107.166:8080`
- デプロイ結果の確認: `curl -s "https://api.github.com/repos/akio4869462-max/keiba-app-new/actions/workflows/deploy.yml/runs?per_page=1"`
- **本番DBへ直接アクセスする手段はない**。SQLでの調査が必要なときはユーザーにEC2上での`docker compose exec db psql ...`の実行を依頼する。アシスタント側から本番に触れるのはポート8080のHTTPエンドポイント（`/status`等）のみ。

## アーキテクチャ

### スクレイピングと3層のキャッシュ

```
Yahoo!スポーツ競馬 → WebScraper（静的メソッド群・サーキットブレーカー内蔵）
                        ↓
                   RaceService（取得のオーケストレーション）
                   ├── RaceParserService     … DOM解析・馬オブジェクト生成
                   ├── HorseEnrichmentService … 過去走・騎手成績の付与＋スコア算出
                   └── RaceCacheService       … レース一覧/馬詳細/騎手成績のメモリキャッシュ
```

`RaceCacheService`は3種類のキャッシュを持つ。**レース一覧のキャッシュはTTL 90分**で、`isRaceCacheValid()`（キー一致＋鮮度）と`hasCachedRaces()`（存在＋鮮度）の両方が鮮度を見る。
`hasCachedRaces()`は通知チェックの入口ガードを兼ねており、ここで鮮度を見ないと**非開催日に前回開催日の古いキャッシュで誤通知が飛ぶ**（実際に発生した不具合）。

### 予想スコアの算出経路(2026-09-19改訂)

`PredictionService.applyRaceModel(horses)`が唯一のスコア算出経路（実際の呼び出し元は`RaceService.buildHorseList()` → `HorseEnrichmentService.applyRaceModel()`）。
単勝オッズから逆算した市場確率`q`を、レース内softmax（`p = softmax(0.885 · logit(q))`）で勝率`p`に変換するだけの式で、距離適性・コース適性・枠順・騎手成績等の加点は行わない。
根拠は `keiba_score_search/analysis/MODEL_REVISION.md`（8年・約40万出走の解析）: これらの加点は市場情報を制御すると有害またはノイズと判定され、旧式（`√オッズ × 能力スコア`）は人気薄を上位に押し上げるfavorite-longshotバイアスを引き起こしていた（予想1位の62%が20倍超、実績ROI 64.7%）。

`Horse`の`predictionScore`は現在`p × 100`（モデル勝率）。あわせて`marketProbability`（市場確率`q`）・`overlay`（`p/q − 1`、妙味）・`popularity`（オッズ順の人気）・`recommended`（2〜8番人気かつoverlayが閾値以上の買い候補フラグ、参考値であり断定的な推奨ではない）を保持する。
2026-09-20時点では`MarketResidualService`（父・騎手・生産者の市場相対残差、下記）の項が加わっているため、勝率の順位は単勝人気順から多少入れ替わりうる。それ以外の距離・コース・枠順・騎手成績そのもの等の加点は行わない。

`RaceResultRecord`にも`overlay`・`popularity`・`modelVersion`（`PredictionService.MODEL_VERSION`）を記録し、将来モデルを変更した際に旧モデルの結果と混同せず比較できるようにしている。

妙味(`overlay`)を較正した`MARKET_WEIGHT=0.885`は**締切10分前オッズ**を前提にしているが、`/predict`の全体キャッシュ（`getRaces()`、TTL 90分・毎時1分の`RacePreloadService`でリフレッシュ）だけでは最大1時間近く古いオッズのままになる。
これを補うため、`RaceService.refreshOddsNearPost()`が毎分実行され、発走10分前(`RaceParserService.isWithinFinalOddsRefreshWindow`)になったレースだけそのレース1件分のdenmaページを再取得してオッズ・予想モデルを更新する。`RaceCacheService.wasOddsRefreshed`/`markOddsRefreshed`でレースごとに1日1回しかアクセスしないようガードしており、毎時10レース前後を取り直す全体リフレッシュよりアクセス負荷は小さい。

`WebScraper.getCornerLatMeans(Document)`は、`p_lat_c`(前走で外を回した度合い、`MODEL_REVISION.md` §6.1)計算用のパーサー基盤（結果ページの「コーナー通過順位」を解析し、馬番ごとの平均lat＝所属する括弧グループ内で内側から何番目かを算出）。`PastRaceInfo`にも前走のレースURL・馬番を保持するようにした。**まだどこからも呼ばれておらず、スコアには未反映**。単独導入の効果は測定誤差レベルであり、`p_hw_lat`（前走の向かい風）の実装と合わせてから`PredictionService`へ組み込む方針（`MODEL_REVISION.md` §6参照）。

`p_hw_lat`（`MODEL_REVISION.md` §6.2）は単独でも統計的に意味のある効果（CI が0を除外）だが、「任意の日付・競馬場の開催ページを動的に特定する」新規インフラが要り実装規模が明確に大きいため、2026-09-19時点では見送り。効果の絶対値（CI下限+0.0001）に対してインフラの規模が見合うか微妙、というのがドキュメント側の判断。

### 市場相対残差(2026-09-20実装、`MODEL_REVISION.md` §8)

`MarketResidualService`が父(`sire`)・騎手(`jockeyName`)・生産者(`breeder`)ごとの市場相対残差テーブルを起動時に`src/main/resources/residuals/*.json`（`keiba_score_search`の`export_residual_tables.py`が書き出したもの）から読み込み、`PredictionService.calculateWinProbabilities`のsoftmaxスコアに`weight · (residual − mean) / sd`の形で加算する。テーブルに名前が無ければ寄与0（安全側のフォールバック。外国産馬の父名がTARGET側は英語表記でYahoo側はカタカナ表記のため一致しないケースがあるが、寄与0になるだけで誤動作はしない）。
生産者名(`Horse.breeder`)は`HorseEnrichmentService.fetchHorseDetail`が前走情報のため既に取得している馬詳細ページ（`WebScraper.getBreeder`）から取るため、追加のスクレイピングは発生しない。
騎手名は出馬表の表記(`"田辺 裕信"`)とテーブルのキー(`"田辺裕信"`)でスペースの有無が違うため、`MarketResidualService.jockeyScore`内で正規化してから引く。あわせて外国人騎手はYahoo側が`"C.ルメール"`のようにイニシャル+ピリオド付きで表示するが、テーブル側は大半を姓のみ(`"ルメール"`)で保持しているため、`normalizeJockeyName`でイニシャルも除去する（`"ルメートル"`のような別人の姓まで書き換えないよう、除去するのは先頭のイニシャル+ピリオドのみ。`MODEL_REVISION.md` §8.7）。
生産者名もYahoo側が法人化された生産者に`"(有)社台コーポレーション白老ファーム"`のように法人格プレフィックスを付けて表示する一方、テーブル側は1件もプレフィックスを含まないため、`normalizeBreeder`で`(有)`等を除去してから照合する（`MODEL_REVISION.md` §8.6）。
`OVERLAY_THRESHOLD`は当初`0.342`（「市場+13シグナル+走路バイアス族」という別のリッチなモデルのoverlay分布で較正された値）だったが、実装済みの市場+3項モデルではスケールが合わず実質発火しなかった（8年・2〜8番人気198,210頭中22頭=0.011%のみ）。実装済み構成向けに再較正した`0.074`に変更済み（`MODEL_REVISION.md` §8.8、2026-09-20）。
テーブルは静的なので鮮度が落ちる。年1回程度、`keiba_score_search`側で最新データを使い`export_residual_tables.py`を再実行し、`src/main/resources/residuals/`配下のJSON4ファイルを差し替えることを推奨（`MODEL_REVISION.md` §8.5参照）。§6の`p_hw_lat`等を追加実装した際はoverlay分布が再度変わるため、`OVERLAY_THRESHOLD`もその都度再較正が必要。

### 自己検証パイプライン

```
開催日にレース一覧を取得 → TrackedRaceUrl に URL を記録（processed=false）
        ↓  RaceResultCollectionService（30分おき＋毎朝3時）
発走時刻＋40分を過ぎたものだけ結果確定を試行
        ↓
RaceResultRecord（馬ごとの予想順位 vs 実着順）＋ RacePayout（全券種の払戻金）
        ↓
RaceResultStatsService でオッズ帯別・スコア帯別・週別・回収率を集計 → /results, /results/races
```

結果確定の判定は**発走時刻からの経過時間**を主なガードにしている。サイト側が発走前でも「結果」欄に古いデータを表示することがあり、内容だけでは信用できないため。副次的にコース・距離の一致もチェックする（`isConfirmedForThisRace`）。

`getHorseDetail(url, historical=true)`は**意図的にキャッシュしない**。結果待ちのポーリングに使われるため、キャッシュすると未確定時点のデータが固定されてしまう。

回収率（ROI）は**100%が収支トントン**の「回収率」方式（`totalReturn / totalStake * 100`）。0%基準の利益率ではない。

### 定期実行の設計方針

`@Scheduled`はすべて`zone = "Asia/Tokyo"`を明示する。**`LocalDate.now()`/`LocalTime.now()`も必ず`ZoneId.of("Asia/Tokyo")`を渡す**（コンテナはUTCで動くため、これを忘れると土日判定や時間帯判定が丸ごと機能しなくなる。実際に通知が全く飛ばない不具合の原因になった）。

夏の変則開催（土日以外の開催・発走時刻のズレ）に対応するため、**曜日をcronにハードコードしない**方針。すべて毎日実行し、以下で動的に判定する:

- 開催日かどうか … `RaceService.hasRaceToday()`（トップページのタイトルに今日の日付が含まれるか）
- 表示対象レースかどうか … `RaceParserService.isRaceTimeRelevant()`（実際の発走時刻が「60分前〜180分後」の範囲か）

`RaceParserService.getRaceRangeByTime()`（壁時計からレース番号を推測する旧方式）は`fetchHistoricalRaces()`のデバッグ経路でのみ残存。新規コードでは使わない。

### 外部サイトへの配慮

- `WebScraper.getHTML()`にサーキットブレーカー。非4xxの失敗が3回続くと5分間すべてのスクレイピングを停止する。**4xx（404等）は個別URLの問題なのでブレーカーにカウントしない**（1頭分の404で全体が止まる不具合があったため）
- 馬・騎手の個別ページ取得ごとに0.5秒sleep
- `RaceResultCollectionService`は`AtomicBoolean`で多重実行を防止（EC2のメモリが小さいため）

### フォールバック

実データが取得できない場合は`DummyRaceFactory`のダミーレースを返す。**ダミーは`RaceCacheService`に書き込まない**（通知の判定に使われるため、ダミーの馬名でお気に入りが誤反応しないようにする）。「出馬表がダミーになった」場合はサーキットブレーカー作動かサイト側障害を疑い、`/status`とアプリログを確認する。

## 検証の進め方

コード変更後は原則としてローカルで実データを使って確認する:

1. `docker compose up -d db` → アプリ起動
2. `/races`・`/predict`等を開いて実際のレースデータで表示を確認
3. DBが絡む場合は`docker exec keiba-postgres psql`でテストデータを投入して確認し、**確認後に削除する**
4. `docker compose down`で片付ける

ローカルDBは**永続化ボリュームを持たない**ため、`docker compose down`のたびに中身が消える。過去の検証データは本番にしか存在しない。

## 未コミットの作業

`ApiController.java` / `ApiControllerTest.java`（＋`pom.xml`のspringdoc依存、`README.md`のAPI節）は`/api/v1`配下のREST API + Swagger UIを追加する一連の作りかけ。テストのコンテキスト起動エラーを解消してからコミットする予定。

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

**ローカルのJDKが25だと`ApiControllerTest`が失敗する**（Mockito(Byte Buddy)がJava 25未対応のため。コードの問題ではなく、CI(`.github/workflows/test.yml`)とDockerfileはJDK 21で通る）。ローカルがJDK 25の場合は除外するか、Byte Buddyの実験フラグで回避する:

```bash
./mvnw test -Dtest='!ApiControllerTest' -DfailIfNoTests=false
./mvnw test -DargLine="-Dnet.bytebuddy.experimental=true"   # JDK 25で全件流す場合
```

**CI(Linux/JDK 21/UTC)と同じ条件で確認したいとき**はDockerで実行できる（`faketime`で時刻も固定できる。時刻依存のテストは日中だけ通って夜に落ちる/WindowsとLinuxで結果が変わる、ということが過去にあった）:

```bash
tar --exclude=./target --exclude=./.git -c . | docker run --rm -i -v m2cache:/root/.m2 eclipse-temurin:21-jdk   bash -c 'mkdir /work && tar -x -C /work && cd /work && sed -i "s/
$//" mvnw && chmod +x mvnw && ./mvnw -B test'
```

**時刻に依存するテストは実時計から「現在+N分」を作らない**。日付またぎで壊れるのに加え、判定側が少し後の現在時刻と比べて分に切り捨てるため境界がずれ、Linuxでは落ちWindowsでは通る（時計の刻みが粗い）ことがある。`RaceParserService`は`Clock`を差し替えられるので、テストでは固定時計を渡す（`RaceParserServiceTest`参照）。

## デプロイ

**デプロイ後は`/status`が200になることまで必ず確認する**（Actionsが成功でも、アプリが起動に失敗していることがある）。2026-10-17、コンストラクタが2つあるBeanに`@Autowired`を付け忘れて本番が起動せず、単体テスト・`@WebMvcTest`・CIがすべて通っていた。サービス層をモックするテストでは配線ミスを検出できないため、`ApplicationContextTest`（H2でアプリ全体のコンテキストを起動）を常設した。**コンストラクタが複数あるBeanには、Spring用に`@Autowired`を付ける**（`Clock`差し替え用のpackage-privateコンストラクタを足すとき特に注意）。


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
2026-10-17時点では`MarketResidualService`（父・騎手・生産者の市場相対残差、下記）と前走の走路バイアス（`TrackBiasService`、下記）の項が加わっているため、勝率の順位は単勝人気順から多少入れ替わりうる。それ以外の距離・コース・枠順・騎手成績そのもの等の加点は行わない。

`RaceResultRecord`にも`overlay`・`popularity`・`modelVersion`（`PredictionService.MODEL_VERSION`）を記録し、将来モデルを変更した際に旧モデルの結果と混同せず比較できるようにしている。

妙味(`overlay`)を較正した`MARKET_WEIGHT=0.885`は**締切10分前オッズ**を前提にしているが、`/predict`の全体キャッシュ（`getRaces()`、TTL 90分・毎時1分の`RacePreloadService`でリフレッシュ）だけでは最大1時間近く古いオッズのままになる。
これを補うため、`RaceService.refreshOddsNearPost()`が毎分実行され、発走10分前(`RaceParserService.isWithinFinalOddsRefreshWindow`)になったレースだけそのレース1件分のdenmaページを再取得してオッズ・予想モデルを更新する。`RaceCacheService.wasOddsRefreshed`/`markOddsRefreshed`でレースごとに1日1回しかアクセスしないようガードしており、毎時10レース前後を取り直す全体リフレッシュよりアクセス負荷は小さい。

### 前走の走路バイアス(2026-10-17実装、`MODEL_REVISION.md` §8.11)

`PredictionService.trackBiasScore`が、前走の走路バイアス2項をsoftmaxスコアに加算する（`weight · clip((raw − mean)/sd, ±4)`、特徴が無い馬は寄与0）:
- `p_lat_c` … 前走で外を回した度合い（前走の自分のlat − 前走レース全馬の平均lat）
- `p_hw_lat` … 前走の向かい風。前走日の走路バイアス（前走と**同じ日・同じ場・同じ芝ダの自レースを除く全レース**から推定）×(−`p_lat_c`)。市場は着順しか見ず「馬場の流れに逆らって走らされた不運」を割り引いていない、という仮説

仕組み（`TrackBiasService`）: Yahooのレース番号は`YY+場+回+日+R`の10桁で、同じ開催日の全レースは先頭8桁が共通・末尾2桁が01〜12。そのため**開催日の一覧ページを探さず、前走のURL（`PastRaceInfo.raceUrl`）だけから全12レースの結果ページURLを組み立てられる**。各結果ページ（`WebScraper.parseResultPage`）から着順・オッズ・コーナー通過順位(lat)を取り、`TrackBiasCalculator`がレースごとの`bm`（市場で回帰した着順の残差とlatの傾き）と`se`を求め、同日のレースを精度重み(1/se²)で平均して前年の場×芝ダ平均(`residuals/bias_priors.json`)へ縮小する。計算は`keiba_score_search`の`bias_v4.py`の移植で、2026-07-12の小倉・福島の実データで**TARGET由来の値と小数4桁まで一致**を確認済み（`TrackBiasCalculatorTest`に実値を固定）。
- 過去のレース結果は不変なので`RaceBiasStat`テーブルに永続化し一度だけ取得する。初回は1開催日あたり最大12ページ（1ページ0.5秒待つ）なので、本番での最初の出馬表読み込みは長くかかる（2026-10-17の実測で`/predict`が約265秒。2回目以降は0.8秒）。以降のデプロイは統計がDBに残っているので短い。待っている間は`/predict`が応答しないだけで、サイト全体が落ちているわけではない
- 通信してよいのは`isHistorical=false`の通常経路のみ。結果収集のポーリング（`historical=true`）は取得済みの統計だけを使い、揃っていなければ寄与0
- 前走が障害戦・地方/海外（開催場コードが01〜10でない）・馬番がlatに無い・同日の他レースが一部でも取得できない場合は特徴なし（寄与0）。取得失敗は30分間再試行しない。存在しないレース(404)は印をDBに残す。当日以降のレースは結果が未確定の恐れがあるため保存しない
- 検証2023-26で現行3項に対し勝ちlog-loss改善 +0.00140 [+0.00037, +0.00246]（CIが0を除外）。**当日バイアス`today_lat`は追加効果が出ない(+0.00004)ため見送り**。重みは`PredictionService`の定数（`fit_bias_weights.py`の出力。再推定は`keiba_score_search/analysis/fit_bias_weights.py --write`）
- `MODEL_VERSION`は`2026-10-17-market-residual-bias-v2`に更新（`/results/models`で旧モデルと分けて比較できる）。`OVERLAY_THRESHOLD`は0.142に再較正（下記）

### 複勝ギャップ(2026-10-18実装、`MODEL_REVISION.md` §8.12)

締切10分前の単勝と複勝を比べ、**複勝プールで単勝より相対的に人気している馬**を拾う項（`FukuGapCalculator`、`Horse.fukuGap`）。複勝プールが先に情報を織り込み単勝があとから追いつくため、正のギャップの馬は勝率が市場の想定を上回る。検証2023-26で現行モデルに対し勝ちlog-loss +0.00280 [+0.00204, +0.00356]。
- `RaceService.refreshOddsNearPost`（発走10分前に1レースだけ再取得）の後で、`HorseEnrichmentService.enrichFukuGap`が単勝・複勝オッズページ（`/race/odds/tfw/{id}`、`WebScraper.parseWinPlaceOdds`）を**1回だけ**取得してギャップを付け、モデルを再計算して並べ替える。毎時の一覧取得ではギャップなし（寄与0）。取得失敗時もギャップなしで続行
- ギャップが付いたレースの`recommended`は`OVERLAY_THRESHOLD_WITH_FUKU_GAP`(0.166)で判定（付かなければ0.142）
- `MODEL_VERSION`は`2026-10-18-market-residual-bias-fuku-v3`。**注意**: 結果収集（`RaceResultCollectionService`）が記録する`overlay`は確定後の再取得ページ由来でギャップを含まないため、`/results/models`の版数比較は「10分前時点の予想」そのものではない
- 朝のオッズの動き（`mom`、+0.0013）は朝オッズの記録が要るため見送り。回収率は上位5%で0.99、上位2%で1.33（CI [1.00,1.70]）と点推定は良いが信頼区間が広く、100%超の根拠にはならない

### 10分前スナップショットと前向き評価（`OddsSnapshot`、`/results/forward`）

結果収集が記録する`RaceResultRecord`は確定後のページ由来で、買う時点（発走10分前）の予想ではない。そこで`RaceService.refreshOddsNearPost`が10分前の再取得・モデル再計算を終えるたびに、`OddsSnapshotService`が**馬ごとの予想（単勝オッズ・直前の一覧取得時点のオッズ・複勝ギャップ・勝率・市場確率・overlay・人気・推奨フラグ・モデル版数）を`OddsSnapshot`に保存**する（1レース1日1回、保存失敗は握りつぶして予想表示を止めない）。
`/results/forward`（`ForwardEvaluationService`）は、そのスナップショットの馬を**10分前の時点で**単勝100円ずつ買っていたら確定払戻（`RacePayout`の単勝）でどうなったかを、買い候補（推奨馬）・妙味10%以上（2〜8番人気）・2〜8番人気全部（基準）の3群で出す。後付けの絞り込みが入らないので、バックテスト（上位5%などを検証期間内で切った数字）より実力に近い。先行指標として「実現率」（的中数 ÷ 市場が見込む的中数、控除率20%を踏まえた損益分岐は1.25）も表示する。買い候補が300頭未満の間は参考値の注記を出す。
さらに、10分前の再取得時に**馬連・ワイド・馬単の板**（`/race/odds/ur`・`wide`・`ut`、各1ページ。`WebScraper.parsePairOdds`、`PoolShareCalculator`）から馬ごとのシェアを作って`umarenShare`/`wideShare`/`exactaFirstShare`に保存する（`HorseEnrichmentService.enrichPoolShares`）。確定板では単勝との食い違いが勝ちを強く予測した（+0.0136）が回収率には直結せず（`MODEL_REVISION.md` §8.14）、10分前の板は過去に遡れないため、**モデルには使わず前向きに記録して効果を確かめる段階**。`/results/forward/export.csv`が、オフライン解析用に的中・単勝払戻つきのCSVを返す（本番DBへ直接アクセスできないための取り出し口）。
**賭け金の自動算出（ケリー等）は入れない方針**（`MODEL_REVISION.md` §8.15）。買い候補をフラットで買うと回収率は0.99付近で、資金の一定割合を張る方式は優位が無いと資金がほぼ尽きる（シミュレーションで100万円→14万円）。ケリーは較正しても「ほとんど買うな」になる。実現率が1.25を安定して超え、回収率が100%を超えるまでは、画面に「目安額」を出さない。
**朝オッズの記録**（`MorningOddsService`、`MorningOdds`テーブル）: オッズの動き`mom`（朝→10分前、`MODEL_REVISION.md` §8.12）の元データ。毎日8:05〜11:55に10分おきに、当日の全レースの単勝オッズを**オッズページ（`/race/odds/tfw/{id}`）1レース1枚だけ**取得して保存する（馬の詳細ページは使わない）。全レースを記録し終えたらその日は何もしない。再起動や取得失敗で朝を逃した分も後から補うが、**`minutesToPost`（取得時点から発走まで何分あったか）を残してある**ので、発走直前に取った分は解析側で朝から除く。10分前のスナップショットには`morningWinOdds`/`morningMinutesToPost`として紐づき、CSVに`morning_win_odds`・`morning_minutes_to_post`が出る。数百レース溜まったら、動きを足して10分前のモデルを再評価する。
**評価スクリプト**: `keiba_score_search/analysis/eval_forward_csv.py`（`python eval_forward_csv.py`で本番のCSVを取得して評価）。板のシェア・朝オッズの動きが、本番モデルの勝率に上乗せで効くか（固定した式とCV再フィット）、回収率に結びつくかを、レース単位ブートCIで出す。30レース未満では評価を中止し、判断の目安は`MODEL_REVISION.md` §8.16。
`earlierWinOdds`（直前の一覧取得時点＝通常は最大1時間前のオッズ）は、朝オッズの動き`mom`（`MODEL_REVISION.md` §8.12、学習時の定義は「朝」）の代わりの参考値で、定義が違うためそのままモデルには使わない。

### 市場相対残差(2026-09-20実装、`MODEL_REVISION.md` §8)

`MarketResidualService`が父(`sire`)・騎手(`jockeyName`)・生産者(`breeder`)ごとの市場相対残差テーブルを起動時に`src/main/resources/residuals/*.json`（`keiba_score_search`の`export_residual_tables.py`が書き出したもの）から読み込み、`PredictionService.calculateWinProbabilities`のsoftmaxスコアに`weight · (residual − mean) / sd`の形で加算する。テーブルに名前が無ければ寄与0（安全側のフォールバック。外国産馬の父名がTARGET側は英語表記でYahoo側はカタカナ表記のため一致しないケースがあるが、寄与0になるだけで誤動作はしない）。
生産者名(`Horse.breeder`)は`HorseEnrichmentService.fetchHorseDetail`が前走情報のため既に取得している馬詳細ページ（`WebScraper.getBreeder`）から取るため、追加のスクレイピングは発生しない。
騎手名は出馬表の表記(`"田辺 裕信"`)とテーブルのキー(`"田辺裕信"`)でスペースの有無が違うため、`MarketResidualService.jockeyScore`内で正規化してから引く。あわせて外国人騎手はYahoo側が`"C.ルメール"`のようにイニシャル+ピリオド付きで表示するが、テーブル側は大半を姓のみ(`"ルメール"`)で保持しているため、`normalizeJockeyName`でイニシャルも除去する（`"ルメートル"`のような別人の姓まで書き換えないよう、除去するのは先頭のイニシャル+ピリオドのみ。`MODEL_REVISION.md` §8.7）。
生産者名もYahoo側が法人化された生産者に`"(有)社台コーポレーション白老ファーム"`のように法人格プレフィックスを付けて表示する一方、テーブル側は1件もプレフィックスを含まないため、`normalizeBreeder`で`(有)`等を除去してから照合する（`MODEL_REVISION.md` §8.6）。
`OVERLAY_THRESHOLD`は当初`0.342`（「市場+13シグナル+走路バイアス族」という別のリッチなモデルのoverlay分布で較正された値）だったが、実装済みの市場+3項モデルではスケールが合わず実質発火しなかった（8年・2〜8番人気198,210頭中22頭=0.011%のみ）。実装済み構成向けに再較正した`0.074`に変更済み（`MODEL_REVISION.md` §8.8、2026-09-20）。さらに前走の走路バイアス2項を足してoverlayの分布が広がったため、同じ手続き（2〜8番人気のTRAIN 95%分位）で`0.142`に再較正した（検証期間でも0.140でほぼ一致、§8.11）。`/results/betting`は保存済みの`overlay`にこの現行閾値を当てるので、旧モデル版のレコードは分布が違い該当が減る（画面のクエリ`overlayThreshold`で調整できる）。
**母・母父（`Horse.dam`/`damSire`）の市場相対残差は検証済みで不採用**（`MODEL_REVISION.md` §8.9、2026-10-04）。父と同じ仕組みで母・母父のテーブルを作って現行3項に足し、検証期間(2023-26)で評価したところ追加効果の信頼区間は全て0をまたぎ（母 −0.00037、母父 +0.00003）、母は過学習気味だった。血統は市場が織り込み済みという§3.3の結論と整合するため、`MarketResidualService`に`dam`/`damSire`テーブルは足していない。再検討する場合は`keiba_score_search`側の`fit_residual_weights.py`で再評価してから。
テーブルは静的なので鮮度が落ちる。年1回程度、`keiba_score_search`側で最新データを使い`export_residual_tables.py`を再実行し、`src/main/resources/residuals/`配下のJSON4ファイルを差し替えることを推奨（`MODEL_REVISION.md` §8.5参照）。項を追加するとoverlay分布が変わるため、`OVERLAY_THRESHOLD`もその都度再較正が必要。

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

`/results/models`（`RaceController.resultsByModel`）は`modelVersion`ごとに予想1位の的中率・回収率・オッズ帯別成績を並べて比較する画面（`RaceResultRecordRepository.findByModelVersion`/`findByModelVersionIsNull`/`findDistinctModelVersions`＋既存の`RaceResultStatsService`の集計に`buildModelStat`を繋いだもの）。`modelVersion`を記録し始める前の旧レコードはnullで、「旧モデル(版数未記録)」として表示される。各モデル`MODEL_EVAL_TARGET_RACES`(400)レース未満の間は「参考値」の注記を出す（的中率・回収率の誤差が大きく優劣を判断できないため）。

`/results`の「券種別の回収率」は確定払戻金（`RacePayout`）ベースで、予想1位を1点100円ずつ購入した場合の単勝・複勝の回収率を並べる（`RaceResultStatsService.buildBetTypeRoi`/`calculateRoiForBetType`、betTypeは引数。単勝・複勝のみ対応）。払戻金データが無いレースは購入していないものとして投資額にも含めない。
的中判定は`RaceResultRecord.umaban`（馬番、nullable。2026-10-04から記録開始）と`RacePayout.combination`（馬番）を突き合わせる。**馬番が無い旧レコードは、払戻行が結果ページの掲載順＝着順で保存されている性質を使い、k着ならk番目の払戻行とみなす**（複勝の払戻行が着順順であることは実データ20レースで確認済み。同着で払戻行が増えるケースのみ1頭分ずれうる）。複勝は払戻行の数がそのまま払戻対象頭数（8頭以上は3着以内、7頭以下は2着以内）になるため、頭数は別途見ない。

`/results/betting`（`BettingSimulationService`）は「買い候補(推奨馬)を実際に買っていたら」のシミュレーション。推奨馬は`PredictionService`の`recommended`と同じ条件（2〜8番人気・overlay≥`OVERLAY_THRESHOLD`・8頭以上）を、保存済みの`RaceResultRecord.overlay`/`popularity`から再現して判定する（これらを記録し始めた2026-09-19以降のレコードのみ対象）。券種は単勝・複勝（推奨馬1頭100円）と、推奨馬を軸に同レースの予想順位上位5頭（軸を除く）へ流す三連複（10点=1000円。1レースに推奨馬が複数なら各馬を軸に購入）。三連複の的中は「軸と相手2頭で3着以内が埋まったか」＝着順だけで判定でき、馬番の無い旧レコードでも計算できる。買い方のルールは`BettingRule`に切り出してあり、画面のクエリ（`minPopularity`/`maxPopularity`/`overlayThreshold`/`partnerCount`）で調整できる。実際の買い方に合わせて変更する場合はここを直す。

回収率（ROI）は**100%が収支トントン**の「回収率」方式（`totalReturn / totalStake * 100`）。0%基準の利益率ではない。

### 定期実行の設計方針

`@Scheduled`はすべて`zone = "Asia/Tokyo"`を明示する。**`LocalDate.now()`/`LocalTime.now()`も必ず`ZoneId.of("Asia/Tokyo")`を渡す**（コンテナはUTCで動くため、これを忘れると土日判定や時間帯判定が丸ごと機能しなくなる。実際に通知が全く飛ばない不具合の原因になった）。

夏の変則開催（土日以外の開催・発走時刻のズレ）に対応するため、**曜日をcronにハードコードしない**方針。すべて毎日実行し、以下で動的に判定する:

- 開催日かどうか … `RaceService.hasRaceToday()`（トップページのタイトルに今日の日付が含まれるか）
- 表示対象レースかどうか … `RaceParserService.isRaceTimeRelevant()`（実際の発走時刻が「60分前〜180分後」の範囲か）

`RaceParserService.getRaceRangeByTime()`（壁時計からレース番号を推測する旧方式）は`fetchHistoricalRaces()`のデバッグ経路でのみ残存。新規コードでは使わない。

### お気に入り通知（`RaceNotificationService`）

通知窓は発走2分前〜発走で、毎分のチェックの間に取りこぼすと次が無い。そのため以下を守る:

- **通知済みの判定キーは「馬(騎手)名＋開催場＋レース番号＋日付」**。レース名は同日に複数レースで同名（`サラ系2歳未勝利`等）になるため使わない（以前は騎手側がレース名キーで、同名の2レース目の通知が届かなかった）。`NotificationHistory`/`NotificationJockeyHistory`の`venue`・`raceNumber`は既存テーブルに足したカラムなので`String`/`Integer`（NULL許容）。旧レコードはNULLで、移行直後に限り当日通知済みのレースが1回だけ再通知されうる。
- 馬の通知と騎手の通知は`try/catch`で独立させる（馬側の例外で騎手側が丸ごと飛ぶのを防ぐ）。
- 送信（`trySend`）に失敗したら**通知済みとして記録しない**。通知窓の間は次の分に再試行される。
- 時刻は`Clock`注入（テストは`RaceNotificationServiceTest`で固定時計）。

### 本番の調査手段（`/status`・`/status/log`）

本番のログへ直接アクセスする手段が無いため、以下をHTTPで読めるようにしてある:
- `/status/log?lines=200` … アプリが標準出力に出した直近の行（`RecentLogBuffer`、最大500行、再起動で消える）。**ダミーレースが出たとき・取得が空のときは、まずここを読む**
- `/status` … 結果収集の状況と、「発走10分前の板（複勝・馬連・ワイド・馬単）の取得で最後に起きた問題」

2026-10-17の事例: 本番で突然ダミーレースが返るようになった。`/status/log`を見ると、Yahooのトップページが**EC2に対してだけHTTP 500**（手元からは正常）で、3回失敗でサーキットブレーカーが5分間作動し、「開催なし」と判定されていた。コードのバグではなくYahoo側の一時的な拒否で、数十分で回復した。拒否の引き金になりうるので、次を守る:
- **キャッシュが空の間（デプロイ・再起動の直後）に`/predict`を連打しない**。全件取得は数分〜十数分かかり、アクセスのたびに独立した取得が走ると負荷が積み重なる。`RaceService.getRaces()`は`raceLoadLock`で同時に1つに絞ってあるが、待っているだけでも時間はかかる
- **デプロイするとメモリのキャッシュが空になり、お気に入り通知（キャッシュ参照のみ）が数分〜十数分止まる**。デプロイ前に`/favorites/today`で直近の出走が無いことを確認する
- 開催場名は`RaceParserService.extractVenueName`で取る。以前のパターンは`(函館|...)競馬場`の`...`（任意の3文字）のせいで`" 新潟"`のように先頭に空白が付いていた（通知文面やスナップショットの開催場名が払戻金と突き合わせられない原因だった）

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

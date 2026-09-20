package org.example.keibaapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// 父・騎手・生産者の市場相対残差(MODEL_REVISION.md §8)。
// 「その父/騎手/生産者が過去に出した馬が、市場の期待(オッズ)を平均してどれだけ
// 上回って勝ってきたか」を縮小推定した値で、市場情報を制御した後も残る数少ない
// 有効なシグナル(騎手の勝率そのものは市場制御後は有害と判定済み、§3.3参照)。
//
// テーブルは keiba_score_search リポジトリの export_residual_tables.py が
// 書き出したJSON(数百〜数千エントリの静的テーブル)を起動時に読み込むだけで、
// 追加のスクレイピングは発生しない。年1回程度、最新データで再生成して
// resources/residuals/ 配下のJSONを差し替えることを想定している。
@Service
public class MarketResidualService {

    // 名前ごとの残差 + 標準化定数(mean/sd) + softmaxに掛ける重み。
    // テーブルに名前が無ければ寄与0として扱う(安全側のフォールバック。
    // 特に外国産馬の父名はTARGET側が英語表記のためYahoo側のカタカナ表記と
    // 一致しないことがあるが、寄与0になるだけで誤動作はしない)
    static final class ResidualTable {
        private final Map<String, Double> residuals;
        private final double weight;
        private final double mean;
        private final double sd;

        ResidualTable(Map<String, Double> residuals, double weight, double mean, double sd) {
            this.residuals = residuals;
            this.weight = weight;
            this.mean = mean;
            this.sd = sd;
        }

        double score(String name) {
            if (name == null || name.isBlank() || sd == 0) {
                return 0;
            }

            Double residual = residuals.get(name);

            return residual == null ? 0 : weight * (residual - mean) / sd;
        }

        static ResidualTable empty() {
            return new ResidualTable(Map.of(), 0, 0, 1);
        }
    }

    private ResidualTable sireTable = ResidualTable.empty();
    private ResidualTable jockeyTable = ResidualTable.empty();
    private ResidualTable breederTable = ResidualTable.empty();

    @PostConstruct
    void load() {
        try {
            ObjectMapper mapper = new ObjectMapper();

            JsonNode weights = mapper.readTree(
                    new ClassPathResource("residuals/residual_weights.json").getInputStream());

            sireTable = buildTable(mapper, "residuals/sire_residual.json", weights.get("sire_name"));
            jockeyTable = buildTable(mapper, "residuals/jockey_residual.json", weights.get("jockey_name"));
            breederTable = buildTable(mapper, "residuals/breeder_residual.json", weights.get("breeder"));

            System.out.println("【市場相対残差】読み込み完了: 父="
                    + sireTable.residuals.size() + "件 騎手=" + jockeyTable.residuals.size()
                    + "件 生産者=" + breederTable.residuals.size() + "件");

        } catch (IOException e) {
            System.out.println("市場相対残差テーブルの読み込みに失敗しました(寄与0として動作を継続): " + e.getMessage());
        }
    }

    private ResidualTable buildTable(
            ObjectMapper mapper, String resourcePath, JsonNode weightNode) throws IOException {

        JsonNode root = mapper.readTree(new ClassPathResource(resourcePath).getInputStream());
        Map<String, Double> residuals = new HashMap<>();

        root.fields().forEachRemaining(entry ->
                residuals.put(entry.getKey(), entry.getValue().get("residual").asDouble()));

        return new ResidualTable(
                residuals,
                weightNode.get("weight").asDouble(),
                weightNode.get("mean").asDouble(),
                weightNode.get("sd").asDouble());
    }

    public double sireScore(String sireName) {
        return sireTable.score(sireName);
    }

    public double jockeyScore(String jockeyName) {
        return jockeyTable.score(normalizeJockeyName(jockeyName));
    }

    public double breederScore(String breederName) {
        return breederTable.score(normalizeBreeder(breederName));
    }

    // 出馬表の表記は姓と名の間にスペースが入る(例:"田辺 裕信")が、テーブルの
    // キーはスペース無し(例:"田辺裕信")。また外国人騎手はYahoo側が
    // "C.ルメール"のようにイニシャル+ピリオドを付けて表示するが、TARGET側の
    // テーブルは大半を姓のみ("ルメール")で保持しているため、イニシャルも除去する
    // (MODEL_REVISION.md §8.7、2026-09-20)
    private String normalizeJockeyName(String jockeyName) {
        if (jockeyName == null) {
            return null;
        }

        String noSpace = jockeyName.replace(" ", "").replace("　", "");

        return noSpace.replaceAll("^[A-Za-zＡ-Ｚａ-ｚ][.．]", "");
    }

    // Yahoo側は法人化された生産者に"(有)社台コーポレーション白老ファーム"のように
    // 法人格プレフィックスを付けて表示するが、TARGET側のテーブルはプレフィックスを
    // 含まない(0/1,546件)ため、法人格を除去してから照合する(MODEL_REVISION.md §8.6、2026-09-20)
    private String normalizeBreeder(String breeder) {
        if (breeder == null) {
            return null;
        }

        return breeder.replaceAll("^[（(](有|株式会社|株|合資|合名|同)[）)]", "").trim();
    }

    // テストで実データファイルに依存せず既知のテーブルを注入するためだけに用意
    // (本番コードからは呼ばない)
    void setTablesForTesting(ResidualTable sire, ResidualTable jockey, ResidualTable breeder) {
        this.sireTable = sire;
        this.jockeyTable = jockey;
        this.breederTable = breeder;
    }
}

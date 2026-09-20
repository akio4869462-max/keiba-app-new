package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketResidualServiceTest {

    @Test
    void sireScore_shouldReturnZeroWhenTableIsEmpty() {
        MarketResidualService service = new MarketResidualService();

        assertEquals(0, service.sireScore("ディープインパクト"));
        assertEquals(0, service.jockeyScore("田辺 裕信"));
        assertEquals(0, service.breederScore("ノーザンファーム"));
    }

    @Test
    void sireScore_shouldReturnZeroForUnknownName() {
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                new MarketResidualService.ResidualTable(Map.of("キズナ", 0.01), 0.029, 0.0005, 0.008),
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty());

        assertEquals(0, service.sireScore("未知の父馬"));
    }

    @Test
    void sireScore_shouldApplyWeightMeanSdFormula() {
        MarketResidualService service = new MarketResidualService();

        // score = weight * (residual - mean) / sd = 0.029 * (0.01 - 0.0005) / 0.008
        service.setTablesForTesting(
                new MarketResidualService.ResidualTable(Map.of("キズナ", 0.01), 0.029, 0.0005, 0.008),
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty());

        double expected = 0.029 * (0.01 - 0.0005) / 0.008;

        assertEquals(expected, service.sireScore("キズナ"), 1e-9);
    }

    @Test
    void jockeyScore_shouldNormalizeSpaceBetweenSurnameAndGivenName() {
        MarketResidualService service = new MarketResidualService();

        // テーブルのキーはスペース無し("田辺裕信")だが、出馬表の表記は
        // スペース入り("田辺 裕信")なので正規化して一致させる
        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(Map.of("田辺裕信", 0.005), 0.026, 0.0004, 0.007),
                MarketResidualService.ResidualTable.empty());

        double expected = 0.026 * (0.005 - 0.0004) / 0.007;

        assertEquals(expected, service.jockeyScore("田辺 裕信"), 1e-9);
    }

    @Test
    void breederScore_shouldReturnZeroForNullOrBlankName() {
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(Map.of("ノーザンファーム", 0.02), 0.036, 0.00004, 0.008));

        assertEquals(0, service.breederScore(null));
        assertEquals(0, service.breederScore(""));
    }

    @Test
    void jockeyScore_shouldStripForeignJockeyInitialPrefix() {
        // Yahoo側は外国人騎手を"C.ルメール"のようにイニシャル+ピリオド付きで表示するが、
        // テーブルのキーは姓のみ("ルメール")なのでイニシャルを除去してから照合する
        // (MODEL_REVISION.md §8.7)
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(Map.of("ルメール", -0.008437), 0.026, 0.0004, 0.007),
                MarketResidualService.ResidualTable.empty());

        double expected = 0.026 * (-0.008437 - 0.0004) / 0.007;

        assertEquals(expected, service.jockeyScore("C.ルメール"), 1e-9);
    }

    @Test
    void jockeyScore_shouldNotConflateDifferentJockeysWithSimilarNames() {
        // "ルメートル"(Lemaître)と"ルメール"(Lemaire)は別人なので、
        // イニシャル除去だけで姓自体は書き換えない(取り違えない)ことを確認する
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(Map.of("ルメール", 0.01), 0.026, 0.0004, 0.007),
                MarketResidualService.ResidualTable.empty());

        assertEquals(0, service.jockeyScore("ルメートル"));
    }

    @Test
    void breederScore_shouldStripCorporatePrefix() {
        // Yahoo側は法人化された生産者に"(有)社台コーポレーション白老ファーム"のように
        // 法人格プレフィックスを付けて表示するが、テーブルのキーはプレフィックスを
        // 含まない("社台コーポレーション白老ファーム")ため除去してから照合する
        // (MODEL_REVISION.md §8.6)
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(
                        Map.of("社台コーポレーション白老ファーム", 0.006), 0.036, 0.00004, 0.008));

        double expected = 0.036 * (0.006 - 0.00004) / 0.008;

        assertEquals(expected, service.breederScore("(有)社台コーポレーション白老ファーム"), 1e-9);
    }

    @Test
    void breederScore_shouldNotAffectNamesWithoutCorporatePrefix() {
        MarketResidualService service = new MarketResidualService();

        service.setTablesForTesting(
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty(),
                new MarketResidualService.ResidualTable(Map.of("ノーザンファーム", 0.02), 0.036, 0.00004, 0.008));

        double expected = 0.036 * (0.02 - 0.00004) / 0.008;

        assertEquals(expected, service.breederScore("ノーザンファーム"), 1e-9);
    }
}

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
}

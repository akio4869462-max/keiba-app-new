package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredictionServiceTest {

    private final PredictionService predictionService =
            new PredictionService(new MarketResidualService());

    private Horse horse(String umaban, double odds) {
        return new Horse(umaban, umaban, "馬" + umaban, "騎手" + umaban, "57.0", odds);
    }

    @Test
    void applyRaceModel_shouldSumWinProbabilitiesToApproximately100() {
        List<Horse> horses = List.of(
                horse("1", 2.1), horse("2", 4.5), horse("3", 6.8), horse("4", 9.0),
                horse("5", 12.5), horse("6", 15.0), horse("7", 21.0), horse("8", 28.0)
        );

        predictionService.applyRaceModel(horses);

        double total = horses.stream().mapToDouble(Horse::getPredictionScore).sum();

        assertEquals(100.0, total, 0.01);
    }

    @Test
    void applyRaceModel_shouldRankByOddsWhenNoOtherSignalExists() {
        // 追加の特徴量が無い現行モデルでは、勝率の順位は単勝人気順と一致するはず
        // (市場確率をそのままsoftmax変換するだけの式であるため)
        Horse favorite = horse("1", 2.1);
        Horse longshot = horse("2", 50.0);

        predictionService.applyRaceModel(List.of(favorite, longshot));

        assertTrue(favorite.getPredictionScore() > longshot.getPredictionScore());
        assertEquals(1, favorite.getPopularity());
        assertEquals(2, longshot.getPopularity());
    }

    @Test
    void applyRaceModel_shouldGiveFavoriteHigherWinProbabilityThanMarketImplied() {
        // MARKET_WEIGHT(0.885) < 1 により、確率分布が市場implied(q)よりわずかに
        // フラット化される。すなわち一番人気はq<pになり、それ以外はq>pになるはず
        Horse favorite = horse("1", 2.1);
        Horse others = horse("2", 4.5);

        predictionService.applyRaceModel(List.of(favorite, others));

        assertTrue(favorite.getPredictionScore() > favorite.getMarketProbability());
        assertTrue(favorite.getOverlay() > 0);
    }

    @Test
    void applyRaceModel_shouldSetZeroScoreAndSkipForInvalidOdds() {
        Horse scratched = horse("1", 999.9);
        Horse normal = horse("2", 5.0);

        predictionService.applyRaceModel(List.of(scratched, normal));

        assertEquals(0, scratched.getPredictionScore());
        assertEquals(0, scratched.getPopularity());
        assertFalse(scratched.isRecommended());
        assertEquals(1, normal.getPopularity());
    }

    @Test
    void applyRaceModel_shouldHandleSingleValidHorseWithoutError() {
        Horse onlyValid = horse("1", 3.0);
        Horse scratched = horse("2", 0);

        predictionService.applyRaceModel(List.of(onlyValid, scratched));

        assertEquals(100.0, onlyValid.getPredictionScore(), 0.01);
        assertEquals(100.0, onlyValid.getMarketProbability(), 0.01);
        assertEquals(0, onlyValid.getOverlay(), 0.01);
        assertEquals(1, onlyValid.getPopularity());
    }

    @Test
    void applyRaceModel_shouldNotRecommendWhenFieldTooSmall() {
        // 8頭未満は妙味の推定が不安定なため、overlayが閾値を超えていても推奨しない
        List<Horse> horses = List.of(
                horse("1", 1.5), horse("2", 3.0), horse("3", 100.0), horse("4", 150.0)
        );

        predictionService.applyRaceModel(horses);

        assertTrue(horses.stream().noneMatch(Horse::isRecommended));
    }

    @Test
    void applyRaceModel_shouldRecommendMispricedMidPackHorseAboveThreshold() {
        // 上位人気が団子状態(1.1倍が2頭)で、8番人気だけが364倍まで離れている
        // ような歪んだオッズ形状では、市場確率(q)と比べてモデル勝率(p)の
        // 圧縮効果により8番人気の妙味が閾値を超えて推奨対象になる
        List<Horse> horses = List.of(
                horse("1", 1.1), horse("2", 1.1), horse("3", 1.4), horse("4", 2.0),
                horse("5", 2.1), horse("6", 2.8), horse("7", 2.9), horse("8", 364.0)
        );

        predictionService.applyRaceModel(horses);

        Horse longshot = horses.get(7);
        assertEquals(8, longshot.getPopularity());
        assertTrue(longshot.getOverlay() > PredictionService.OVERLAY_THRESHOLD);
        assertTrue(longshot.isRecommended());

        for (Horse horse : horses) {
            if (horse.isRecommended()) {
                assertTrue(horse.getPopularity() >= 2 && horse.getPopularity() <= 8);
                assertTrue(horse.getOverlay() >= PredictionService.OVERLAY_THRESHOLD);
            }
        }
    }

    @Test
    void applyRaceModel_shouldRecommendAtRecalibratedThresholdButNotAtOldThreshold() {
        // MODEL_REVISION.md §8.8: 旧閾値0.342は「市場+13シグナル+走路バイアス族」
        // という別モデルの分布で較正された値で、実装済みの市場+3項モデルでは
        // 理論最大値(+0.318)にも届かず実質発火しなかった。8番人気の妙味が
        // 0.074(新閾値)は超えるが0.342(旧閾値)は超えない値になるオッズ形状で、
        // 再較正後の閾値が実際に使われていることを確認する
        List<Horse> horses = List.of(
                horse("1", 1.5), horse("2", 2.0), horse("3", 3.0), horse("4", 6.0),
                horse("5", 9.0), horse("6", 15.0), horse("7", 30.0), horse("8", 200.0)
        );

        predictionService.applyRaceModel(horses);

        Horse eighthPopularity = horses.get(7);
        assertEquals(8, eighthPopularity.getPopularity());
        assertTrue(eighthPopularity.getOverlay() > 0.074);
        assertTrue(eighthPopularity.getOverlay() < 0.342);
        assertTrue(eighthPopularity.isRecommended());
    }

    @Test
    void applyRaceModel_shouldNotRecommendFavoriteEvenWithHighOverlay() {
        // 1番人気は妙味が高くても購入対象の人気帯(2〜8番人気)から除外される
        List<Horse> horses = List.of(
                horse("1", 1.1), horse("2", 1.1), horse("3", 1.4), horse("4", 2.0),
                horse("5", 2.1), horse("6", 2.8), horse("7", 2.9), horse("8", 364.0)
        );

        predictionService.applyRaceModel(horses);

        assertEquals(1, horses.get(0).getPopularity());
        assertFalse(horses.get(0).isRecommended());
    }

    @Test
    void applyRaceModel_shouldSetReasonWithPopularityAndOverlay() {
        Horse target = horse("1", 5.0);
        Horse other = horse("2", 8.0);

        predictionService.applyRaceModel(List.of(target, other));

        assertTrue(target.getPredictionReason().contains("番人気"));
        assertTrue(target.getPredictionReason().contains("モデル勝率"));
        assertTrue(target.getPredictionReason().contains("妙味"));
    }

    @Test
    void applyRaceModel_shouldRewardHorseWithPositiveMarketResidual() {
        // オッズが全く同じ2頭でも、市場相対残差(MODEL_REVISION.md §8)が
        // プラスの父を持つ馬の方が勝率が高くなるはず
        MarketResidualService residualService = new MarketResidualService();
        residualService.setTablesForTesting(
                new MarketResidualService.ResidualTable(
                        Map.of("優秀な父", 0.02), 0.029, 0.0005, 0.008),
                MarketResidualService.ResidualTable.empty(),
                MarketResidualService.ResidualTable.empty());

        PredictionService serviceWithResidual = new PredictionService(residualService);

        Horse strong = horse("1", 5.0);
        strong.setSire("優秀な父");
        Horse plain = horse("2", 5.0);
        plain.setSire("無名の父");

        serviceWithResidual.applyRaceModel(List.of(strong, plain));

        assertTrue(strong.getPredictionScore() > plain.getPredictionScore());
    }
}

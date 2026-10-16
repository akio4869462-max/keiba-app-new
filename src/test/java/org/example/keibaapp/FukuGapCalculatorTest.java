package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FukuGapCalculatorTest {

    private Map<Integer, Double> map(double... values) {
        Map<Integer, Double> m = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            m.put(i + 1, values[i]);
        }
        return m;
    }

    @Test
    void compute_shouldGiveHigherGapToHorseMoreSupportedInPlacePool() {
        Map<Integer, Double> win = map(2.5, 4.0, 6.0, 9.0, 14.0, 20.0, 35.0, 60.0);
        Map<Integer, Double> placeNeutral = map(1.3, 1.6, 2.0, 2.6, 3.4, 4.6, 7.0, 12.0);
        // 馬4(馬番4)だけ複勝が大きく売れている(複勝オッズが低い)
        Map<Integer, Double> placeSupported = new HashMap<>(placeNeutral);
        placeSupported.put(4, 1.8);

        Map<Integer, Double> neutral = FukuGapCalculator.compute(win, placeNeutral);
        Map<Integer, Double> supported = FukuGapCalculator.compute(win, placeSupported);

        assertEquals(8, supported.size());
        assertTrue(supported.get(4) > neutral.get(4), "複勝で売れている馬のギャップは大きくなるはず");
        // 他の馬は、4番の支持が増えた分だけ相対的に下がる
        assertTrue(supported.get(1) < neutral.get(1));
    }

    @Test
    void compute_shouldBeZeroMeanishForConsistentPools() {
        // 単勝と複勝が整合していれば、ギャップは0付近(2次式で人気薄ほど複勝が高く出る分を引いている)に収まる
        Map<Integer, Double> win = map(3.0, 5.0, 8.0, 12.0, 20.0, 30.0, 50.0, 90.0);
        Map<Integer, Double> place = map(1.4, 1.8, 2.3, 3.0, 4.2, 5.8, 9.0, 16.0);

        for (double gap : FukuGapCalculator.compute(win, place).values()) {
            assertTrue(Math.abs(gap) < 1.5, "ギャップが極端に大きい: " + gap);
        }
    }

    @Test
    void compute_shouldReturnEmptyWhenTooFewValidHorses() {
        Map<Integer, Double> win = map(2.0, 4.0, 8.0, 16.0, 32.0);
        Map<Integer, Double> place = map(1.2, 1.5, 2.0, 3.0, 5.0);
        place.put(5, 0.0);   // 複勝が無効な馬は対象外 → 4頭になり推定しない

        assertTrue(FukuGapCalculator.compute(win, place).isEmpty());
    }

    @Test
    void parseWinPlaceOdds_shouldReadWinAndPlaceMidAndSkipInvalidRows() {
        String html = "<table><tbody id=\"raceodds_table\">"
                + row(1, "74.0", "11.6", "20.9")
                + row(2, "3.5", "1.5", "2.1")
                + "<tr class=\"hr-tableValue__row\"><td>1</td><td>3</td><td>馬3</td><td>---</td><td>---</td></tr>"
                + "</tbody></table>";
        Document doc = Jsoup.parse(html);

        WebScraper.WinPlaceOdds odds = WebScraper.parseWinPlaceOdds(doc);

        assertEquals(List.of(1, 2), odds.win().keySet().stream().sorted().toList());
        assertEquals(74.0, odds.win().get(1));
        assertEquals((11.6 + 20.9) / 2, odds.placeMid().get(1), 1e-9);
        assertEquals((1.5 + 2.1) / 2, odds.placeMid().get(2), 1e-9);
    }

    private String row(int umaban, String win, String lo, String hi) {
        return "<tr class=\"hr-tableValue__row\"><td><span>1</span></td><td>" + umaban + "</td>"
                + "<td><a href=\"/keiba/directory/horse/1/\">馬" + umaban + "</a></td>"
                + "<td class=\"hr-tableValue__data--odds\"><span class=\"hr-odds__10more\">" + win + "</span></td>"
                + "<td class=\"hr-tableValue__data--odds\"><span>" + lo + "</span> - <span>" + hi + "</span></td></tr>";
    }
}

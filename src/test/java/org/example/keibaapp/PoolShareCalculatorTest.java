package org.example.keibaapp;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PoolShareCalculatorTest {

    private static String table(int first, String... rows) {
        StringBuilder sb = new StringBuilder("<table class=\"hr-tableLeftTop hr-tableLeftTop--oddsW\"><thead><tr>"
                + "<th class=\"hr-tableLeftTop__head\" scope=\"col\" colspan=\"2\">" + first + "</th></tr></thead><tbody>");
        for (String row : rows) {
            String[] kv = row.split("=", 2);
            sb.append("<tr><th class=\"hr-tableLeftTop__head\" scope=\"row\">").append(kv[0])
                    .append("</th><td class=\"hr-tableLeftTop__data\">").append(kv[1]).append("</td></tr>");
        }
        return sb.append("</tbody></table>").toString();
    }

    @Test
    void parsePairOdds_shouldReadSingleOddsAndUseMidpointForWideRanges() {
        String html = "<html><body>"
                + table(1, "2=<p><span>1112.3</span></p>", "3=<p><span class=\"hr-odds__10more\">50.0</span></p>")
                + table(2, "3=<span>10.0</span><span>-</span><span>14.0</span>")
                + "</body></html>";

        List<WebScraper.PairOdds> pairs = WebScraper.parsePairOdds(Jsoup.parse(html));

        assertEquals(3, pairs.size());
        assertEquals(new WebScraper.PairOdds(1, 2, 1112.3), pairs.get(0));
        assertEquals(new WebScraper.PairOdds(1, 3, 50.0), pairs.get(1));
        // ワイドは下限と上限の中間
        assertEquals(new WebScraper.PairOdds(2, 3, 12.0), pairs.get(2));
    }

    @Test
    void parsePairOdds_shouldSkipUnreadableCellsAndOtherTables() {
        String html = "<html><body>"
                + table(1, "2=<span>---</span>", "3=<span>30.0</span>")
                + "<table><thead><tr><th>1</th></tr></thead><tbody><tr><th>2</th><td><span>9.9</span></td></tr></tbody></table>"
                + "</body></html>";

        List<WebScraper.PairOdds> pairs = WebScraper.parsePairOdds(Jsoup.parse(html));

        assertEquals(List.of(new WebScraper.PairOdds(1, 3, 30.0)), pairs);
    }

    @Test
    void symmetricShares_shouldCreditBothHorsesAndSumToOne() {
        // 馬連: 1-2が最も売れている(オッズが低い) → 1と2のシェアが大きい
        List<WebScraper.PairOdds> pairs = List.of(
                new WebScraper.PairOdds(1, 2, 5.0),
                new WebScraper.PairOdds(1, 3, 20.0),
                new WebScraper.PairOdds(2, 3, 20.0));

        Map<Integer, Double> shares = PoolShareCalculator.symmetricShares(pairs);

        assertEquals(1.0, shares.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertTrue(shares.get(1) > shares.get(3));
        assertEquals(shares.get(1), shares.get(2), 1e-12);
    }

    @Test
    void firstShares_shouldCreditOnlyTheWinningSideOfOrderedPairs() {
        // 馬単: 1→2 と 1→3 だけ売れている。2→1 は不人気 → 1着の強さは1だけが大きい
        List<WebScraper.PairOdds> pairs = List.of(
                new WebScraper.PairOdds(1, 2, 10.0),
                new WebScraper.PairOdds(1, 3, 10.0),
                new WebScraper.PairOdds(2, 1, 100.0));

        Map<Integer, Double> shares = PoolShareCalculator.firstShares(pairs);

        assertEquals(2, shares.size());
        assertTrue(shares.get(1) > shares.get(2));
        assertNull(shares.get(3));
        assertEquals(1.0, shares.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
    }

    @Test
    void shares_shouldBeEmptyForEmptyOrInvalidPairs() {
        assertTrue(PoolShareCalculator.symmetricShares(List.of()).isEmpty());
        assertTrue(PoolShareCalculator.firstShares(List.of(new WebScraper.PairOdds(1, 2, 0))).isEmpty());
    }

    @Test
    void exportCsv_shouldIncludePoolSharesAndOutcome() {
        LocalDate date = LocalDate.of(2026, 10, 17);
        OddsSnapshot hit = new OddsSnapshot(date, "東京", 1, 5, "馬5", 12.0, 14.0, 0.2, 9.0, 7.0, 0.3, 4, true,
                "v3", date.atTime(10, 0)).withPoolShares(0.12, 0.10, 0.09);
        OddsSnapshot miss = new OddsSnapshot(date, "東京", 1, 2, "馬2", 3.0, null, null, 30.0, 28.0, 0.0, 1, false,
                "v3", date.atTime(10, 0));
        OddsSnapshot noPayoutYet = new OddsSnapshot(date, "東京", 2, 1, "馬1", 3.0, null, null, 30.0, 28.0, 0.0, 1, false,
                "v3", date.atTime(10, 30));

        String csv = new ForwardEvaluationService().exportCsv(
                List.of(hit, miss, noPayoutYet), List.of(new RacePayout(date, "東京", 1, "単勝", "5", 1250)));

        String[] lines = csv.split("\n");
        assertEquals(4, lines.length);
        assertTrue(lines[0].startsWith("race_date,venue,race_number,umaban,win_odds"));
        assertTrue(lines[1].contains(",0.12,0.1,0.09,"), lines[1]);
        assertTrue(lines[1].endsWith(",1,1250"), lines[1]);       // 的中
        assertTrue(lines[2].endsWith(",0,0"), lines[2]);          // 外れ
        assertTrue(lines[3].endsWith(",,"), lines[3]);            // 払戻データ未収集
    }
}

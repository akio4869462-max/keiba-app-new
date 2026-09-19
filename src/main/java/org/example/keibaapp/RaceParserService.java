package org.example.keibaapp;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RaceParserService {
    private static final Pattern VENUE_PATTERN = Pattern.compile("(函館|...)競馬場");

    public Set<String> getRaceUrls(Document listDoc) {
        Elements raceLinks =
                listDoc.select("a[href*=/keiba/race/index/]");

        Set<String> raceUrls = new LinkedHashSet<>();

        for (Element raceLink : raceLinks) {
            String indexUrl = raceLink.attr("abs:href");
            String raceUrl = indexUrl.replace("/index/", "/denma/");
            raceUrls.add(raceUrl);
        }

        return raceUrls;
    }

    public LocalTime parseRaceTime(Document doc) {
        return parseTimeText(WebScraper.getRaceTime(doc));
    }

    private LocalTime parseTimeText(String rawTime) {
        if (rawTime.length() == 4) {
            rawTime = "0" + rawTime;
        }

        return LocalTime.parse(rawTime);
    }

    // 開催場一覧ページ(listDoc)は、各レースのdenmaページを開かなくても
    // レース番号・発走時刻・URLが既に一覧表として載っている
    // (.hr-tableSchedule__data--date に "1R<p>9:50</p>" の形で、
    // 同じ<tr>内の.hr-tableSchedule__linkにレースURLがある)。
    // これを使えば、関連性のないレースのdenmaページまで開かずに済む
    public record RaceSchedule(String raceUrl, LocalTime raceTime) {
    }

    public List<RaceSchedule> getRaceSchedules(Document listDoc) {
        List<RaceSchedule> schedules = new ArrayList<>();

        for (Element row : listDoc.select("tr")) {
            Element dateCell = row.selectFirst(".hr-tableSchedule__data--date");

            if (dateCell == null) {
                continue;
            }

            Element linkEl = row.selectFirst(".hr-tableSchedule__link");
            Element timeEl = dateCell.selectFirst("p");

            if (linkEl == null || timeEl == null) {
                continue;
            }

            try {
                LocalTime raceTime = parseTimeText(timeEl.text().trim());
                String indexUrl = linkEl.attr("abs:href");
                String raceUrl = indexUrl.replace("/index/", "/denma/");

                schedules.add(new RaceSchedule(raceUrl, raceTime));
            } catch (Exception e) {
                System.out.println("開催場一覧のスケジュール解析に失敗: " + e.getMessage());
            }
        }

        return schedules;
    }

    public int getRaceNumber(String raceUrl) {
        String cleanUrl = raceUrl.endsWith("/")
                ? raceUrl.substring(0, raceUrl.length() - 1)
                : raceUrl;

        String raceNoText = cleanUrl.substring(cleanUrl.length() - 2);
        return Integer.parseInt(raceNoText);
    }

    public Elements getRaceRows(Document doc) {
        Elements rows = doc.select(".hr-table tbody tr");

        if (rows.isEmpty()) {
            return doc.select("table tr");
        }

        return rows;
    }

    public boolean shouldFetchRace(
            String raceUrl,
            int startRace,
            int endRace) {

        int raceNum = getRaceNumber(raceUrl);

        return raceNum >= startRace
                && raceNum <= endRace;
    }

    public Horse createHorse(Elements tds) {

        String waku = tds.get(0).text().trim();
        String umaban = tds.get(1).text().trim();

        Element horseLink = tds.get(2).selectFirst("a");

        String name = tds.get(2).text().split("\\s+")[0];
        String horseUrl = horseLink != null
                ? horseLink.attr("abs:href")
                : "";

        String jockeyData = tds.get(3).text().trim();

        String jockeyName =
                jockeyData.substring(
                        0,
                        jockeyData.lastIndexOf(' ')
                ).trim();

        String weight =
                jockeyData.substring(
                        jockeyData.lastIndexOf(' ') + 1
                );

        Element jockeyLink = tds.get(3).selectFirst("a");
        String jockeyUrl = jockeyLink != null
                ? jockeyLink.attr("abs:href")
                : "";

        double oddsValue =
                OddsParser.parse(tds.get(7).text().trim());

        Horse horse = new Horse(
                waku,
                umaban,
                name,
                jockeyName,
                weight,
                oddsValue
        );

        horse.setHorseUrl(horseUrl);
        horse.setJockeyUrl(jockeyUrl);

        parsePedigree(tds, horse);

        return horse;
    }

    // 血統セル(父馬名/母馬名/(母父馬名))を解析する。
    // 海外馬などリンクが無くテキストのみの場合もあるため、<a>の有無に関わらず
    // .text()で取得する。想定外の形式の場合は空文字のままにする防御的な実装
    private void parsePedigree(Elements tds, Horse horse) {
        if (tds.size() < 6) {
            return;
        }

        for (Element p : tds.get(5).select("p")) {
            String text = p.text().trim();

            if (text.startsWith("父：")) {
                horse.setSire(text.substring("父：".length()).trim());
            } else if (text.startsWith("母：")) {
                horse.setDam(text.substring("母：".length()).trim());
            } else if (text.startsWith("(母父：") || text.startsWith("（母父：")) {
                horse.setDamSire(text.replaceAll("[（(]母父：|[）)]", "").trim());
            }
        }
    }

    // fetchHistoricalRaces（過去データ再取得用のデバッグ経路）が引き続き使用する
    public int[] getRaceRangeByTime() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Tokyo"));
        if (now.isBefore(LocalTime.of(11, 30))) {
            return new int[]{1, 4};
        } else if (now.isBefore(LocalTime.of(14, 0))) {
            return new int[]{5, 8};
        } else {
            return new int[]{9, 12};
        }
    }

    private static final int RELEVANT_PAST_MINUTES = 60;
    private static final int RELEVANT_FUTURE_MINUTES = 180;

    // 夏の変則開催等で発走時刻が通常と大きくずれても正しく判定できるよう、
    // 壁時計からレース番号を推測する方式(getRaceRangeByTime)ではなく、
    // 実際にパースした発走時刻と現在時刻の差で「今表示する価値があるか」を判定する。
    // 予想(/predict)向け: 終わったレースの予想を出す意味は薄いため、
    // 発走から一定時間(60分)経ったら対象から外す
    public boolean isRaceTimeRelevant(LocalTime raceTime) {
        return isWithinRelevantWindow(raceTime, RELEVANT_PAST_MINUTES, RELEVANT_FUTURE_MINUTES);
    }

    // 出馬表(/races)向け: 終わったレースもその日のうちは見られるようにしたいため、
    // 過去方向には制限を設けない(常に同日内の比較のため日付をまたぐ心配はない)
    public boolean isRaceStillShowableToday(LocalTime raceTime) {
        return isWithinRelevantWindow(raceTime, Integer.MAX_VALUE, RELEVANT_FUTURE_MINUTES);
    }

    private static final int FINAL_ODDS_WINDOW_MINUTES = 10;

    // 予想(/predict)の妙味(overlay)計算に使う重みは締切10分前オッズで
    // 較正されているため(MODEL_REVISION.md §2.2)、発走10分前になったレースだけ
    // オッズの再取得対象と判定する
    public boolean isWithinFinalOddsRefreshWindow(LocalTime raceTime) {
        return isWithinRelevantWindow(raceTime, 0, FINAL_ODDS_WINDOW_MINUTES);
    }

    private boolean isWithinRelevantWindow(LocalTime raceTime, int pastMinutes, int futureMinutes) {
        if (raceTime == null) {
            return false;
        }

        LocalTime now = LocalTime.now(ZoneId.of("Asia/Tokyo"));
        long diffMinutes = java.time.Duration.between(now, raceTime).toMinutes();

        return diffMinutes >= -(long) pastMinutes
                && diffMinutes <= futureMinutes;
    }

    public String extractVenueName(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher =
                VENUE_PATTERN.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return text;
    }
}
package org.example.keibaapp;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class RaceParserService {

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
        String rawTime = WebScraper.getRaceTime(doc);

        if (rawTime.length() == 4) {
            rawTime = "0" + rawTime;
        }

        return LocalTime.parse(rawTime);
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

        return horse;
    }

    public int[] getRaceRangeByTime() {
        LocalTime now = LocalTime.now();

        if (now.isBefore(LocalTime.of(11, 30))) {
            return new int[]{1, 4};
        } else if (now.isBefore(LocalTime.of(14, 0))) {
            return new int[]{5, 8};
        } else {
            return new int[]{9, 12};
        }
    }

    public String extractVenueName(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("(函館|福島|新潟|東京|中山|中京|京都|阪神|小倉)競馬場");

        java.util.regex.Matcher matcher =
                pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return text;
    }
}
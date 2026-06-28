package org.example.keibaapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OddsParser {
    private static final Pattern ODDS_PATTERN = Pattern.compile("\\(([^)]+)\\)");

    public static double parse(String rawOdds) {
        double oddsValue = 999.9;

        Matcher matcher = ODDS_PATTERN.matcher(rawOdds);

        try {
            if (matcher.find()) {
                String match = matcher.group(1);

                if (!match.equals("****")) {
                    oddsValue = Double.parseDouble(match);
                }

            } else {
                String cleanNum = rawOdds.replaceAll("[^0-9.]", "");

                if (!cleanNum.isEmpty() && !cleanNum.equals("****")) {
                    oddsValue = Double.parseDouble(cleanNum);
                }
            }

        } catch (NumberFormatException e) {
            oddsValue = 999.9;
        }

        return oddsValue;
    }
}
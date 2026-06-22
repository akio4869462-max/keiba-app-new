package org.example.keibaapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OddsParserTest {

    @Test
    void parse_shouldReturnOddsInsideParentheses() {
        double odds = OddsParser.parse("1人気 (3.5)");

        assertEquals(3.5, odds);
    }

    @Test
    void parse_shouldReturnOddsFromPlainText() {
        double odds = OddsParser.parse("12.8");

        assertEquals(12.8, odds);
    }

    @Test
    void parse_shouldReturnDefaultValueWhenOddsIsHidden() {
        double odds = OddsParser.parse("****");

        assertEquals(999.9, odds);
    }

    @Test
    void parse_shouldReturnDefaultValueWhenTextIsInvalid() {
        double odds = OddsParser.parse("オッズ未取得");

        assertEquals(999.9, odds);
    }
}
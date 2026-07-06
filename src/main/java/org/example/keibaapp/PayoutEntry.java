package org.example.keibaapp;

public class PayoutEntry {
    private final String betType;
    private final String combination;
    private final int payoutYen;

    public PayoutEntry(String betType, String combination, int payoutYen) {
        this.betType = betType;
        this.combination = combination;
        this.payoutYen = payoutYen;
    }

    public String getBetType() {
        return betType;
    }

    public String getCombination() {
        return combination;
    }

    public int getPayoutYen() {
        return payoutYen;
    }
}

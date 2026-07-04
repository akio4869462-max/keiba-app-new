package org.example.keibaapp;

public class JockeyStats {
    private final double winRate;
    private final double rentaiRate;

    public JockeyStats(double winRate, double rentaiRate) {
        this.winRate = winRate;
        this.rentaiRate = rentaiRate;
    }

    public double getWinRate() {
        return winRate;
    }

    public double getRentaiRate() {
        return rentaiRate;
    }

    public static JockeyStats empty() {
        return new JockeyStats(0, 0);
    }
}

package cn.zedocn.tech;

public final class Balance {
    public static final int MAX_PLAYERS = 8;
    public static final int MAX_PROTECTION_RADIUS = 256;
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static int players(int value) { return Math.clamp(value, 1, MAX_PLAYERS); }
    public static int radius(int value) { return Math.clamp(value, 8, MAX_PROTECTION_RADIUS); }
    public static int trialCost(int players, boolean ominous) { return players(players) * (ominous ? 20_000 : 10_000); }
    public static int protectionCost(int radius) { int r = radius(radius); return r * r * 2; }
    private Balance() {}
}

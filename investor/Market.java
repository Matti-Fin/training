package investor;

import java.util.ArrayDeque;

public class Market {

    private static final int WINDOW = 10;

    private static final ArrayDeque<Integer> inv = new ArrayDeque<>();
    private static final ArrayDeque<Integer> nights = new ArrayDeque<>();

    private static double lastFed = 5.0;

    public static void recordTurn(GameState state, int investmentsThisTurn, int nightsThisTurn) {
        if (state == null) return;

        inv.addLast(Math.max(0, investmentsThisTurn));
        nights.addLast(Math.max(0, nightsThisTurn));

        while (inv.size() > WINDOW) inv.removeFirst();
        while (nights.size() > WINDOW) nights.removeFirst();

        // very simple placeholder FED logic:
        // more investments + more activity -> slightly higher; low activity -> slightly lower
        double invAvg = inv.stream().mapToInt(x -> x).average().orElse(0);
        double nAvg   = nights.stream().mapToInt(x -> x).average().orElse(0);

        double target = 3.0 + invAvg * 0.35 + nAvg * 0.05;   // 3% base + activity bumps
        target = Math.max(0.5, Math.min(12.0, target));

        // smooth
        double newFed = (state.fedRate * 0.85) + (target * 0.15);

        state.fedRateJustChanged = Math.abs(newFed - lastFed) >= 0.25;
        state.fedRate = newFed;
        lastFed = newFed;
    }

    // used by properties to scale “population income” based on recent activity
    public static double populationFactorFromNights(int lastRoundNights, int playerCount) {
        int pc = Math.max(1, playerCount);
        double base = 1.0;
        double perPlayer = (double) lastRoundNights / pc; // 0..?
        // cap so it doesn't explode
        return Math.max(0.5, Math.min(2.0, base + perPlayer * 0.08));
    }
}

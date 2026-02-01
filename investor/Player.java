package investor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Player {
    public String name;
    public Color color;

    // --- per circuitum trackers ---
    public long circCustomerIncome = 0;
    public long circDividendsIncome = 0;
    public long circInterestPaid = 0;

    // --- welfare/tax compliance ---
    public boolean requestedWelfareThisCirc = false;

    public boolean taxEvasionOpen = false;
    public long evadedTaxAmount = 0;
    public int taxEvasionRoundsOpen = 0;

    public boolean welfareFraudOpen = false;
    public long welfareFraudAmount = 0;

    // --- JAIL state (needed for Police/TaxAudit mechanics) ---
    // Rule: after serving jail, return to exact tile you were on when sent to jail.
    public boolean inJail = false;
    public int jailTurnsLeft = 0;     // how many turns remaining in jail
    public int jailReturnPosId = 0;   // tile to return to after jail

    // optional bookkeeping (useful for UI/debug)
    public long lastJailFine = 0;
    public long lastBackTaxesPaid = 0;

    // --- loan amortization (term-based) ---
    public long loanPrincipalRemaining = 0;
    public int loanTermRemainingTurns = 0;
    public long loanPrincipalPerStart = 0;
    public boolean missedInterestFlag = false;

    // --- risk spread penalties (optional; used by Economy.computeRiskSpread) ---
    public int extraRiskSpreadTurnsLeft = 0;
    public double extraRiskSpreadAdd = 0.0;

    // debug/last values
    public long lastComputedTax = 0;

    public long cash;
    public long debt;

    // -1 means "not on path yet" (spawn area)
    public int posId = -1;

    // --- spawn placement on the full-image grid (visual placement before first move) ---
    public boolean onSpawn = true;
    public int spawnGX = -1;
    public int spawnGY = -1;

    // casino extra risk
    public int casinoRiskExtraPct = 0;
    public int riskMarginPct = 0; // legacy / optional

    // bonds (if you use these)
    public long bondsLT1 = 0;
    public long bonds1to2 = 0;
    public long bondsGT2 = 0;

    // properties
    public List<Property> owned = new ArrayList<>();

    // turn tracking
    public long roundEarnings = 0;

    // pawn keys
    public static final String[] PAWN_KEYS = {
            "blue", "green", "orange", "pink", "purple", "red", "yellow"
    };

    // chosen in setup (never gold)
    public String pawnKeyChosen = "blue";
    // used for rendering (may become "gold")
    public String pawnKeyCurrent = "blue";

    public Player(String name, Color color, long startCash) {
        this.name = name;
        this.color = color;
        this.cash = startCash;
        this.debt = 0;
    }

    public long netWorth() {
        return cash - debt + propertiesValue();
    }

    public long propertiesValue() {
        long sum = 0;
        if (owned != null) {
            for (Property p : owned) {
                if (p != null) sum += p.lotPrice; // or your own valuation
            }
        }
        return sum;
    }

    public int totalRiskMarginPct() {
        return Math.max(0, riskMarginPct + casinoRiskExtraPct);
    }

    public static String pawnPath(String key) {
        return "sfx/pawn_" + key + ".png";
    }

    public static Color pawnColor(String key) {
        return switch (key) {
            case "blue" -> new Color(0, 160, 255);
            case "green" -> new Color(0, 200, 120);
            case "orange" -> new Color(255, 150, 40);
            case "pink" -> new Color(255, 90, 170);
            case "purple" -> new Color(160, 90, 255);
            case "red" -> new Color(255, 70, 70);
            case "yellow" -> new Color(245, 210, 40);
            case "gold" -> new Color(212, 175, 55);
            default -> new Color(120, 120, 120);
        };
    }
}

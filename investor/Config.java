package investor;

public class Config {

    // economy defaults
    public long startCash = 1_800;

    // legacy / older naming (ok to keep if used somewhere)
    public long kelaWealthThreshold = 1_000_000;
    public long kelaSupportPerRound = 20_000;

    // seat / gate price
    public long seatPrice = 1_000;

    // --- economy core ---
    public double taxRate = 0.20;               // 20%
    public long welfarePerCirc = 140;

    public long welfareNetWorthThreshold = 900;
    public long welfareCashThreshold = 150;

    // risk model
    public double riskLeverageThreshold = 0.35;

    // loan terms: short/mid/long in rounds (start pays)
    public int[] loanTermsTurns = new int[] { 2, 6, 12 };

    // ---- SFX paths (relative to project root) ----
    public String sfxDice1 = "sfx/dice1.wav";
    public String sfxDice2 = "sfx/dice2.wav";
    public String sfxFootsteps = "sfx/footsteps-on-wood-397989.wav";
    public String sfxRouletteSpin = "sfx/spinning-roulette-wheel-429832.wav";
    public String sfxYouWin = "sfx/you-win-sfx-442128.wav";
    public String sfxGoofyLaugh = "sfx/goofy-laugh-454456.wav";

    // --- Background music list (random pick on start) ---
    public String[] bgmTracks = new String[] {
            "sfx/background_1.wav"
            // lisää myöhemmin:
            //"sfx/background_2.wav",
            //"sfx/background_3.wav"
    };
}

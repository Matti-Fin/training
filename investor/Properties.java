package investor;

import java.util.ArrayList;
import java.util.List;

class BuildOption {
    public final String label;
    public final long cost;
    public final Runnable apply;

    BuildOption(String label, long cost, Runnable apply) {
        this.label = label;
        this.cost = cost;
        this.apply = apply;
    }
}

abstract class Property {

    public final String name;
    public final String description;

    public final long lotPrice;

    // legacy (vanha 3-stage tuki)
    public final long build1Cost;
    public final long build2Cost;

    public final long rate0;
    public final long rate1;
    public final long rate2;

    public final long basePopulationIncomePerRound;

    public int ownerIndex = -1;

    // stage: 0 = L1, 1 = L2, ...
    protected int stage = 0;

    protected long investedExtra = 0;

    private int nightsThisRound = 0;
    private int lastRoundNights = 0;

    // optional dynamic stages (kun >3 stagea)
    protected String[] stageNames = null;     // e.g. {"L1 ...","L2 ...",...}
    protected long[] upgradeCosts = null;     // length = stageCount-1
    protected long[] unitFees = null;         // length = stageCount

    protected Property(
            String name,
            String description,
            long lotPrice,
            long build1Cost,
            long build2Cost,
            long rate0,
            long rate1,
            long rate2,
            long basePopulationIncomePerRound
    ) {
        this.name = name;
        this.description = (description == null ? "" : description.trim());
        this.lotPrice = lotPrice;
        this.build1Cost = build1Cost;
        this.build2Cost = build2Cost;
        this.rate0 = rate0;
        this.rate1 = rate1;
        this.rate2 = rate2;
        this.basePopulationIncomePerRound = basePopulationIncomePerRound;
    }

    // dynaamiset stagit
    protected Property(
            String name,
            String description,
            long lotPrice,
            String[] stageNames,
            long[] upgradeCosts,
            long[] unitFees
    ) {
        this(name, description, lotPrice, 0, 0,
                (unitFees != null && unitFees.length > 0 ? unitFees[0] : 0),
                (unitFees != null && unitFees.length > 1 ? unitFees[1] : 0),
                (unitFees != null && unitFees.length > 2 ? unitFees[2] : 0),
                0
        );
        this.stageNames = stageNames;
        this.upgradeCosts = upgradeCosts;
        this.unitFees = unitFees;
    }

    public boolean isOwned() { return ownerIndex >= 0; }

    public long currentNightRate() {
        if (unitFees != null && unitFees.length > 0) {
            int s = Math.max(0, Math.min(stage, unitFees.length - 1));
            return unitFees[s];
        }
        return switch (stage) {
            case 0 -> rate0;
            case 1 -> rate1;
            default -> rate2;
        };
    }

    public long totalInvestedValue() {
        return lotPrice + investedExtra;
    }

    public void addPlayerNights(int n) {
        nightsThisRound += Math.max(0, n);
    }

    public int lastRoundNights() { return lastRoundNights; }

    public void rolloverRoundNights() {
        lastRoundNights = nightsThisRound;
        nightsThisRound = 0;
    }

    public String buildInfo() {
        String[] names = stageNames();
        int s = Math.max(0, Math.min(stage, names.length - 1));
        return names[s];
    }

    protected String[] stageNames() {
        if (stageNames != null && stageNames.length > 0) return stageNames;
        return legacyStageNames();
    }

    protected abstract String[] legacyStageNames();

    public List<BuildOption> buildOptions() {
        List<BuildOption> out = new ArrayList<>();

        if (upgradeCosts != null && stageNames != null) {
            if (stage >= 0 && stage < upgradeCosts.length) {
                long cost = upgradeCosts[stage];
                String nextName = stageNames[Math.min(stage + 1, stageNames.length - 1)];
                if (cost > 0) {
                    out.add(new BuildOption(nextName, cost, () -> {
                        stage = Math.min(stage + 1, stageNames.length - 1);
                        investedExtra += cost;
                    }));
                }
            }
            return out;
        }

        if (stage == 0 && build1Cost > 0) {
            String[] sn = stageNames();
            out.add(new BuildOption(sn[Math.min(1, sn.length - 1)], build1Cost, () -> {
                stage = 1;
                investedExtra += build1Cost;
            }));
        } else if (stage == 1 && build2Cost > 0) {
            String[] sn = stageNames();
            out.add(new BuildOption(sn[Math.min(2, sn.length - 1)], build2Cost, () -> {
                stage = 2;
                investedExtra += build2Cost;
            }));
        }
        return out;
    }

    public void onRoundTick(GameState state) {
        if (state == null) return;
        if (!isOwned()) return;

        double factor = Market.populationFactorFromNights(lastRoundNights(), state.players.size());
        long income = Math.round(basePopulationIncomePerRound * factor);

        if (income <= 0) return;

        Player owner = state.players.get(ownerIndex);
        owner.cash += income;
        owner.roundEarnings += income;
    }
}

/* ============================================================
   Businesses
   ============================================================ */

class SkyGateAirport extends Property {
    SkyGateAirport() {
        super(
                "SkyGate International Airport",
                "Buy runway, then build terminal. Premium traffic magnet—turn layovers into cash. “Land. Spend. Repeat.”",
                1_400,
                new String[] { "L1 Runway", "L2 Terminal" },
                new long[]   { 900 },
                new long[]   { 35, 60 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 Runway","L2 Terminal","L2 Terminal"}; }
}

class PineHavenCabinVillage extends Property {
    PineHavenCabinVillage() {
        super(
                "PineHaven Cabin Village",
                "Start with 1 cabin, expand cabin-by-cabin up to 10. Steady scaler. “Your guests bring their own problems—pay you anyway.”",
                220,
                new String[] { "2 cabins", "4 cabins", "6 cabins", "8 cabins", "10 cabins" },
                new long[]   { 220, 220, 220, 220 },
                null
        );
    }

    @Override
    public long currentNightRate() {
        int cabins = 2 + stage * 2;           // 2,4,6,8,10
        return (long) cabins * 4L;            // 8,16,24,32,40
    }

    @Override protected String[] legacyStageNames() { return stageNames; }
}

class CrestlineObservatory extends Property {
    CrestlineObservatory() {
        super(
                "Crestline Peak Observatory",
                "Build 1 telescope, upgrade to 2. Niche but classy. “Stargaze tonight—your wallet cries tomorrow.”",
                650,
                new String[] { "L1 1 Telescope", "L2 2nd Telescope" },
                new long[]   { 450 },
                new long[]   { 22, 38 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 1 Telescope","L2 2nd Telescope","L2 2nd Telescope"}; }
}

class RoadBiteRestaurant extends Property {
    RoadBiteRestaurant() {
        super(
                "RoadBite → CityBite → FranchiseBite",
                "Roadside diner → chain → franchise stake. Reliable mid-game engine. “Hungry players are paying players.”",
                350,
                new String[] { "L1 Roadside Diner", "L2 Chain Restaurant", "L3 Franchise Stake" },
                new long[]   { 450, 650 },
                new long[]   { 12, 22, 35 }
        );
    }

    @Override
    public void onRoundTick(GameState state) {
        super.onRoundTick(state);
        if (state == null || !isOwned()) return;

        if (stage >= 2) {
            Player owner = state.players.get(ownerIndex);
            owner.cash += 25;
            owner.roundEarnings += 25;
        }
    }

    @Override protected String[] legacyStageNames() { return new String[]{"L1 Roadside Diner","L2 Chain Restaurant","L3 Franchise Stake"}; }
}

class NovaWorksTech extends Property {
    NovaWorksTech() {
        super(
                "NovaWorks Technologies",
                "PC → music player → phone → tablet → video service. Late-game monster. “Upgrade fast or watch rivals stream past.”",
                900,
                new String[] { "L1 Computer Company", "L2 Music Player", "L3 Smartphone", "L4 Tablet", "L5 Video Platform" },
                new long[]   { 550, 650, 750, 900 },
                new long[]   { 30, 45, 65, 85, 110 }
        );
    }

    @Override
    public void onRoundTick(GameState state) {
        super.onRoundTick(state);
        if (state == null || !isOwned()) return;

        if (stage >= 4) {
            int others = Math.max(0, state.players.size() - 1);
            long add = 35L * others;
            Player owner = state.players.get(ownerIndex);
            owner.cash += add;
            owner.roundEarnings += add;
        }
    }

    @Override protected String[] legacyStageNames() { return new String[]{"L1 Computer Company","L2 Music Player","L3 Smartphone"}; }
}

class TriSpanBridge extends Property {
    TriSpanBridge() {
        super(
                "TriSpan Bridge",
                "1 lane → 2 lanes → 3 lanes. Infrastructure toll king. “Cross it once—pay for it forever.”",
                500,
                new String[] { "L1 1-Lane Bridge", "L2 2-Lane Bridge", "L3 3-Lane Bridge" },
                new long[]   { 350, 450 },
                new long[]   { 18, 28, 40 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 1-Lane Bridge","L2 2-Lane Bridge","L3 3-Lane Bridge"}; }
}

class BellavitaCasino extends Property {
    BellavitaCasino() {
        super(
                "Bellavita Casino & Villas",
                "Main hall → villas → pool wing → shows. Stylish high roller. “Where luck wears cologne.”",
                1_000,
                new String[] { "L1 Main Building", "L2 Villas", "L3 Pool Complex", "L4 Entertainment" },
                new long[]   { 650, 750, 900 },
                new long[]   { 32, 48, 66, 88 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 Main Building","L2 Villas","L3 Pool Complex"}; }
}

class TitanMegaCasino extends Property {
    TitanMegaCasino() {
        super(
                "Titan Mega Resort Casino",
                "Main hall → villas → pool wing → shows. Expensive, brutal payoff. “Go big—then go bigger.”",
                1_200,
                new String[] { "L1 Main Building", "L2 Villas", "L3 Pool Complex", "L4 Entertainment" },
                new long[]   { 800, 900, 1_050 },
                new long[]   { 36, 55, 78, 105 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 Main Building","L2 Villas","L3 Pool Complex"}; }
}

class ObeliskPyramidCasino extends Property {
    ObeliskPyramidCasino() {
        super(
                "Obelisk Pyramid Casino",
                "Main hall → villas → pool wing → shows. Flashy endgame spike. “Step inside—leave with less.”",
                1_100,
                new String[] { "L1 Main Building", "L2 Villas", "L3 Pool Complex", "L4 Entertainment" },
                new long[]   { 700, 850, 1_000 },
                new long[]   { 34, 52, 76, 110 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 Main Building","L2 Villas","L3 Pool Complex"}; }
}

class OrbXTheatre extends Property {
    OrbXTheatre() {
        super(
                "OrbX 360 Experience Theatre",
                "Budget 360 demo → world-class 3D. Event-driven cash burst. “Reality called—OrbX ignored it.”",
                700,
                new String[] { "L1 360 Demo", "L2 Best-in-World 3D" },
                new long[]   { 800 },
                new long[]   { 25, 55 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 360 Demo","L2 Best-in-World 3D","L2 Best-in-World 3D"}; }
}

class SantaAnnicaPier extends Property {
    SantaAnnicaPier() {
        super(
                "Santa Annica Pier",
                "View deck → restaurants → souvenir row → amusement park. Tourist vacuum. “Sunset views, premium prices.”",
                800,
                new String[] { "L1 View Deck", "L2 Restaurants", "L3 Souvenir Shops", "L4 Amusement Park" },
                new long[]   { 500, 600, 700 },
                new long[]   { 28, 40, 55, 75 }
        );
    }
    @Override protected String[] legacyStageNames() { return new String[]{"L1 View Deck","L2 Restaurants","L3 Souvenir Shops"}; }
}

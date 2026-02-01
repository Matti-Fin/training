package investor;

import java.awt.Point;
import java.util.Arrays;
import java.util.List;

public class BoardFactory {

    static void buildBoard(GameState state) {

        List<Point> pathPts = Arrays.asList(
                new Point(359, 370), // 1
                new Point(315, 366), // 2
                new Point(269, 360), // 3
                new Point(227, 353), // 4
                new Point(185, 343), // 5
                new Point(166, 323), // 6
                new Point(206, 295), // 7
                new Point(212, 266), // 8
                new Point(177, 242), // 9
                new Point(142, 222), // 10
                new Point(110, 204), // 11
                new Point(63, 179),  // 12
                new Point(35, 146),  // 13
                new Point(67, 114),  // 14
                new Point(328, 103), // 15
                new Point(386, 106), // 16
                new Point(477, 112), // 17
                new Point(565, 118), // 18
                new Point(644, 126), // 19
                new Point(722, 135), // 20
                new Point(762, 143), // 21
                new Point(794, 150), // 22
                new Point(837, 161), // 23
                new Point(885, 179), // 24
                new Point(931, 215), // 25
                new Point(927, 237), // 26
                new Point(827, 281), // 27
                new Point(792, 291), // 28
                new Point(709, 324), // 29
                new Point(671, 337), // 30
                new Point(627, 356), // 31
                new Point(590, 365), // 32
                new Point(547, 370), // 33
                new Point(459, 376)  // 34
        );

        List<Point> spawnPts = Arrays.asList(
                new Point(419, 370),
                new Point(418, 375),
                new Point(429, 377),
                new Point(438, 373),
                new Point(480, 372),
                new Point(464, 381),
                new Point(490, 379),
                new Point(500, 370),
                new Point(510, 376),
                new Point(453, 367),
                new Point(434, 370),
                new Point(418, 373),
                new Point(436, 376),
                new Point(442, 375),
                new Point(451, 374),
                new Point(512, 377),
                new Point(415, 369)
        );

        state.path.clear();
        state.infoSquares.clear();
        state.allProperties.clear();

        state.spawnPoints.clear();
        state.spawnPoints.addAll(spawnPts);

        for (int i = 0; i < pathPts.size(); i++) {
            Point p = pathPts.get(i);
            state.path.add(new Square(i, p.x, p.y, SquareType.EMPTY, ""));
        }

        // Put players to spawn mode: BEFORE square #1
        if (state.players != null) {
            for (Player pl : state.players) {
                if (pl == null) continue;
                pl.posId = -1;
                pl.onSpawn = true;
            }
        }

        // Request: NPC starts at squares #34 and #33
        // => posId 33 and 32
        state.policePosId = 33;
        state.taxAuditPosId = 32;

        // helper
        java.util.function.BiConsumer<Integer, Property> setLot = (idx, pr) -> {
            if (idx < 0 || idx >= state.path.size()) return;
            Square s = state.path.get(idx);
            s.type = SquareType.LOT;
            s.label = pr.name;
            s.property = pr;
        };

        // ------------------------------------------------------------
        // Businesses (using classes from Properties.java)
        // ------------------------------------------------------------

        // Airport: squares 1-2 => idx 0-1
        Property airport = new SkyGateAirport();
        state.allProperties.add(airport);
        setLot.accept(0, airport);
        setLot.accept(1, airport);

        // Cabins: squares 3-4 => idx 2-3
        Property cabins = new PineHavenCabinVillage();
        state.allProperties.add(cabins);
        setLot.accept(2, cabins);
        setLot.accept(3, cabins);

        // Observatory: squares 5-6 => idx 4-5
        Property obs = new CrestlineObservatory();
        state.allProperties.add(obs);
        setLot.accept(4, obs);
        setLot.accept(5, obs);

        // Bridge: squares 7-8 => idx 6-7
        Property bridge = new TriSpanBridge();
        state.allProperties.add(bridge);
        setLot.accept(6, bridge);
        setLot.accept(7, bridge);

        // Restaurant: squares 9-10 => idx 8-9
        Property rest = new RoadBiteRestaurant();
        state.allProperties.add(rest);
        setLot.accept(8, rest);
        setLot.accept(9, rest);

        // Italian Casino: squares 11-12 => idx 10-11
        Property italian = new BellavitaCasino();
        state.allProperties.add(italian);
        setLot.accept(10, italian);
        setLot.accept(11, italian);

        // Mega Casino: squares 13-14 => idx 12-13
        Property mega = new TitanMegaCasino();
        state.allProperties.add(mega);
        setLot.accept(12, mega);
        setLot.accept(13, mega);

        // Pyramid Casino: squares 15-16 => idx 14-15
        Property pyramid = new ObeliskPyramidCasino();
        state.allProperties.add(pyramid);
        setLot.accept(14, pyramid);
        setLot.accept(15, pyramid);

        // Tech: squares 17-18 => idx 16-17
        Property tech = new NovaWorksTech();
        state.allProperties.add(tech);
        setLot.accept(16, tech);
        setLot.accept(17, tech);

        // Orb Theatre: squares 19-20 => idx 18-19
        Property orb = new OrbXTheatre();
        state.allProperties.add(orb);
        setLot.accept(18, orb);
        setLot.accept(19, orb);

        // Pier moved OFF #34, because #34 is now TAX/SOCIALS.
        // Put Pier on square #33 => idx 32
        Property pier = new SantaAnnicaPier();
        state.allProperties.add(pier);
        setLot.accept(32, pier);

        // TAX / SOCIALS must be square #34 => idx 33
        {
            Square tax = state.path.get(33);
            tax.label = "TAX / SOCIALS";
            // keep type EMPTY; TurnLogic checks label while moving
        }
    }
}

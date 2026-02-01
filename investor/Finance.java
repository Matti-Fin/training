package investor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Finance {

    private static final Random RNG = new Random();

    public static void onLand(GameState state, Square sq) {
        Player pl = state.currentPlayer();
        if (pl == null || sq == null) return;

        switch (sq.type) {
            case LOT -> handleLot(state, pl, sq);
            case SEAT -> handleSeat(state, pl, sq);
            case DUAL_LOT -> handleDualLot2(state, pl, sq);
            case GATE -> handleGate(state, pl, sq);
            case CASINO -> handleCasino(state, pl);
            default -> { /* nothing */ }
        }

        UiHud.refreshTopHud(state);
        UiHud.refreshPlayersRow(state);
        if (state.boardPanel != null) state.boardPanel.repaint();
    }

    // ============================================================
    // VISIT ROLL MODE (called by UiHud when awaitingVisitRoll == true)
    // ============================================================
    public static void finishPendingVisitRoll(GameState state, int roll) {
        if (state == null) return;

        // Minimal compile-safe behavior:
        // - release visit-roll mode so player can continue
        state.awaitingVisitRoll = false;

        if (state.infoLabel != null) {
            state.infoLabel.setText("Visit roll finished: " + roll);
        }

        // If you later implement "pending visit" payment logic, you can use:
        // state.pendingVisitPlayer, state.pendingVisitProperty, state.pendingVisitCost
        // For now we clear them so state doesn't stay dirty.
        state.pendingVisitPlayer = null;
        state.pendingVisitProperty = null;
        state.pendingVisitCost = 0;
    }

    // ---------------- UNITS + BONUSES ----------------
    private static int rollD6() {
        return 1 + RNG.nextInt(6); // 1..6
    }

    // Bonus rules per your spec:
    // Pier L4: +2
    // Airport L2: +1
    // OrbXTheatre L2: +1
    private static int computeUnits(Property pr) {
        int base = rollD6();
        int bonus = 0;

        // stage: 0=L1, 1=L2, 2=L3, 3=L4 ...
        int stage = pr.stage;

        if (pr instanceof SantaAnnicaPier) {
            if (stage >= 3) bonus = 2;
        } else if (pr instanceof SkyGateAirport) {
            if (stage >= 1) bonus = 1;
        } else if (pr instanceof OrbXTheatre) {
            if (stage >= 1) bonus = 1;
        }

        return base + bonus;
    }

    // ---------------- VISIT DIALOG (2-step) ----------------
    private static void showVisitRollDialog(GameState state, Player visitor, Property pr, String unitLabel) {
        if (state == null || visitor == null || pr == null) return;
        if (!pr.isOwned()) return;
        if (pr.ownerIndex == state.currentPlayerIndex) return;

        Player owner = state.players.get(pr.ownerIndex);
        String ownerName = (owner == null ? "Unknown" : owner.name);

        long fee = pr.currentNightRate();

        // 1) Pre-roll prompt
        String msg1 = "<html><div style='width:320px;'>"
                + "<b>Visit</b><br><br>"
                + "You are visiting: <b>" + pr.name + "</b><br>"
                + "Owner: <b>" + ownerName + "</b><br>"
                + "Price: <b>$" + format(fee) + "</b> per " + unitLabel + "<br><br>"
                + "Roll the die to see how many " + unitLabel + " you will buy."
                + "</div></html>";

        showGameModal(state, "Roll the die", msg1, new String[]{"Roll"}, 0);

        // 2) Roll + charge
        int units = computeUnits(pr);
        long total = units * fee;

        if (total > 0) {
            visitor.cash = Math.max(0, visitor.cash - total);
            if (owner != null) {
                owner.cash += total;
                owner.roundEarnings += total;
            }
        }

        String msg2 = "<html><div style='width:320px;'>"
                + "<b>Result</b><br><br>"
                + "You rolled: <b>" + units + "</b><br>"
                + "That means you bought <b>" + units + "</b> " + unitLabel + "<br>"
                + "Unit price: <b>$" + format(fee) + "</b><br>"
                + "Total bill: <b>$" + format(total) + "</b><br><br>"
                + "The amount has been charged from you."
                + "</div></html>";

        showGameModal(state, "Visit charged", msg2, new String[]{"OK"}, 0);

        pushInfo(state,
                "Visit: " + pr.name + " | Units=" + units + " | Paid $" + format(total));
        UiHud.logPaysNights(state, visitor, units, fee, total, pr.name);
    }

    // A modal dialog positioned "in-game style" (centered on board panel).
    private static int showGameModal(GameState state, String title, String htmlMessage, String[] buttons, int defaultIndex) {
        Component anchor = (state != null && state.boardPanel != null) ? state.boardPanel : null;

        JOptionPane pane = new JOptionPane(
                htmlMessage,
                JOptionPane.INFORMATION_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                buttons,
                buttons[Math.max(0, Math.min(defaultIndex, buttons.length - 1))]
        );

        JDialog dlg = pane.createDialog(anchor, title);

        if (anchor != null) {
            int bw = Math.max(300, anchor.getWidth());
            int bh = Math.max(300, anchor.getHeight());

            int side = (int) Math.round(Math.min(bw, bh) * 0.55);
            side = Math.max(320, Math.min(side, 520));

            dlg.setSize(side, side);

            try {
                Point p = anchor.getLocationOnScreen();
                int x = p.x + (bw - side) / 2;
                int y = p.y + (bh - side) / 2;
                dlg.setLocation(x, y);
            } catch (Exception ignored) {
                dlg.setLocationRelativeTo(anchor);
            }
        } else {
            dlg.setSize(420, 420);
            dlg.setLocationRelativeTo(null);
        }

        dlg.setModal(true);
        dlg.setVisible(true);

        Object selected = pane.getValue();
        if (selected == null) return -1;

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].equals(selected)) return i;
        }
        return -1;
    }

    // ---------------- GATE ----------------
    private static void handleGate(GameState state, Player pl, Square sq) {
        if (sq.gateGroup == null) return;

        if (sq.gateGroup.isOwned()) {
            String owner = state.players.get(sq.gateGroup.ownerIndex).name;
            pushInfo(state, "Gate owned by: " + owner);
            return;
        }

        if (!ownsSquare2EitherSide(state, state.currentPlayerIndex)) {
            pushInfo(state, "Gate: not available for you.");
            return;
        }

        long price = state.cfg.seatPrice;
        String body = "This is a 2-tile gate.\n\n"
                + "Only owners of square #2 (inside hotel or outside restaurant) can buy it.\n\n"
                + "Price: $" + format(price);

        String[] opts = {"Skip", "Buy"};
        int r = JOptionPane.showOptionDialog(
                null, body, "Buy GATE",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opts, opts[0]
        );

        if (r == 1) {
            if (pl.cash < price) {
                pushInfo(state, "Not enough cash for gate.");
                return;
            }
            pl.cash -= price;
            sq.gateGroup.ownerIndex = state.currentPlayerIndex;
            state._investmentsThisTurn += 1;
            pushInfo(state, "Bought gate.");
        }
    }

    private static boolean ownsSquare2EitherSide(GameState state, int playerIndex) {
        if (state.path.size() <= 2) return false;
        Square s2 = state.path.get(2);
        if (s2 == null) return false;
        if (s2.insideProperty != null && s2.insideProperty.ownerIndex == playerIndex) return true;
        if (s2.outsideProperty != null && s2.outsideProperty.ownerIndex == playerIndex) return true;
        return false;
    }

    // ---------------- DUAL LOT (#2) ----------------
    private static void handleDualLot2(GameState state, Player pl, Square sq) {
        if (sq.insideProperty == null || sq.outsideProperty == null) {
            pushInfo(state, "Dual lot not configured.");
            return;
        }

        while (true) {
            Property in = sq.insideProperty;
            Property out = sq.outsideProperty;

            StringBuilder msg = new StringBuilder();
            msg.append("Square #2 has two businesses:\n\n");

            msg.append("[INSIDE] ").append(in.name).append("\n");
            msg.append("  Price: $").append(format(in.lotPrice)).append("\n");
            msg.append("  Stage: ").append(in.buildInfo()).append("\n");
            msg.append("  Rate: $").append(format(in.currentNightRate())).append(" / nights\n");
            msg.append("  Owned: ").append(in.isOwned() ? state.players.get(in.ownerIndex).name : "No").append("\n\n");

            msg.append("[OUTSIDE] ").append(out.name).append("\n");
            msg.append("  Price: $").append(format(out.lotPrice)).append("\n");
            msg.append("  Stage: ").append(out.buildInfo()).append("\n");
            msg.append("  Rate: $").append(format(out.currentNightRate())).append(" / meals\n");
            msg.append("  Owned: ").append(out.isOwned() ? state.players.get(out.ownerIndex).name : "No").append("\n");

            String[] choose = {"Skip", "Inside", "Outside"};
            int pick = JOptionPane.showOptionDialog(
                    null,
                    msg.toString(),
                    "Dual lot",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    choose,
                    choose[0]
            );

            if (pick <= 0) return;

            if (pick == 1) {
                boolean goBack = handlePropertyMenuWithReturn(state, pl, in, true);
                if (!goBack) return;
            } else if (pick == 2) {
                boolean goBack = handlePropertyMenuWithReturn(state, pl, out, false);
                if (!goBack) return;
            }
        }
    }

    private static boolean handlePropertyMenuWithReturn(GameState state, Player pl, Property pr, boolean isHotel) {
        String unit = isHotel ? "nights" : "meals";
        String rateName = isHotel ? "Night rate" : "Meal price";
        String title = isHotel ? "Inside (Hotel)" : "Outside (Restaurant)";

        if (!pr.isOwned()) {
            String body = pr.name + "\n\n"
                    + "Price: $" + format(pr.lotPrice) + "\n"
                    + "Build info: " + pr.buildInfo() + "\n"
                    + rateName + ": $" + format(pr.currentNightRate()) + " / " + unit + "\n";

            String[] opts = {"Buy", "Skip", "Return"};
            int r = JOptionPane.showOptionDialog(
                    null, body, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, opts, opts[1]
            );

            if (r == 2) return true;
            if (r != 0) return false;

            if (pl.cash < pr.lotPrice) {
                pushInfo(state, "Not enough cash to buy: " + pr.name);
                return false;
            }

            pl.cash -= pr.lotPrice;
            pr.ownerIndex = state.currentPlayerIndex;
            pl.owned.add(pr);
            state._investmentsThisTurn += 1;

            pushInfo(state, "Bought: " + pr.name);
            UiHud.logBought(state, pl, pr.name, pr.lotPrice);
            return false;
        }

        if (pr.ownerIndex == state.currentPlayerIndex) {
            List<BuildOption> opts = pr.buildOptions();

            String body = pr.name + "\n\n"
                    + "Owned by you.\n"
                    + "Build info: " + pr.buildInfo() + "\n"
                    + rateName + ": $" + format(pr.currentNightRate()) + " / " + unit + "\n";

            if (opts.isEmpty()) {
                String[] buttons = {"Skip", "Return"};
                int r = JOptionPane.showOptionDialog(
                        null, body + "\n\nNo upgrades available.", title,
                        JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                        null, buttons, buttons[0]
                );
                return (r == 1);
            }

            String[] buttons = new String[opts.size() + 2];
            buttons[0] = "Skip";
            for (int i = 0; i < opts.size(); i++) buttons[i + 1] = opts.get(i).label;
            buttons[buttons.length - 1] = "Return";

            int r = JOptionPane.showOptionDialog(
                    null, body, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, buttons, buttons[0]
            );

            if (r == buttons.length - 1) return true;
            if (r <= 0) return false;

            BuildOption bo = opts.get(r - 1);
            if (pl.cash < bo.cost) {
                pushInfo(state, "Not enough cash for upgrade.");
                return false;
            }
            pl.cash -= bo.cost;
            bo.apply.run();
            state._investmentsThisTurn += 1;

            pushInfo(state, "Upgraded: " + bo.label);
            UiHud.logBought(state, pl, bo.label, bo.cost);
            return false;
        }

        // Owned by someone else => PLAYER ROLLS (no auto roll)
        showVisitRollDialog(state, pl, pr, unit);
        return false;
    }

    // ---------------- legacy SEAT ----------------
    private static void handleSeat(GameState state, Player pl, Square sq) {
        if (sq.seat == null) return;

        String businessName = sq.seat.businessLabel;
        String body = "SALE: " + businessName + "\n"
                + "Price: $" + format(sq.seat.price) + "\n\n"
                + "This gives access/entry related to:\n"
                + sq.seat.property.name;

        String[] opts = {"Skip", "Buy"};
        int r = JOptionPane.showOptionDialog(
                null, body, "Buy SEAT",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opts, opts[0]
        );

        if (r == 1) {
            if (sq.seat.isOwned()) {
                pushInfo(state, "Seat already owned.");
                return;
            }
            if (pl.cash < sq.seat.price) {
                pushInfo(state, "Not enough cash for seat.");
                return;
            }
            pl.cash -= sq.seat.price;
            sq.seat.ownerIndex = state.currentPlayerIndex;
            state._investmentsThisTurn += 1;
            pushInfo(state, "Bought seat: " + businessName);
        }
    }

    // ---------------- legacy LOT ----------------
    private static void handleLot(GameState state, Player pl, Square sq) {
        if (sq.property == null) return;
        Property pr = sq.property;

        StringBuilder body = new StringBuilder();
        body.append(pr.name).append("\n\n");
        body.append("Lot price: $").append(format(pr.lotPrice)).append("\n");
        body.append("Build info: ").append(pr.buildInfo()).append("\n");
        body.append("Night rate now: $").append(format(pr.currentNightRate())).append("\n");
        body.append("\nOwned: ").append(pr.isOwned() ? state.players.get(pr.ownerIndex).name : "No").append("\n");

        if (!pr.isOwned()) {
            String[] opts = {"Skip", "Buy"};
            int r = JOptionPane.showOptionDialog(
                    null, body.toString(), "Property",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, opts, opts[0]
            );
            if (r == 1) {
                if (pl.cash < pr.lotPrice) {
                    pushInfo(state, "Not enough cash to buy.");
                    return;
                }
                pl.cash -= pr.lotPrice;
                pr.ownerIndex = state.currentPlayerIndex;
                pl.owned.add(pr);
                state._investmentsThisTurn += 1;
                pushInfo(state, "Bought: " + pr.name);
                UiHud.logBought(state, pl, pr.name, pr.lotPrice);
            }
            return;
        }

        if (pr.ownerIndex == state.currentPlayerIndex) {
            List<BuildOption> opts = pr.buildOptions();
            if (opts.isEmpty()) {
                pushInfo(state, "Owned by you. No upgrades.");
                return;
            }

            String[] buttons = new String[opts.size() + 1];
            buttons[0] = "Skip";
            for (int i = 0; i < opts.size(); i++) buttons[i + 1] = opts.get(i).label;

            int r = JOptionPane.showOptionDialog(
                    null, body.toString(), "Your property",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, buttons, buttons[0]
            );
            if (r <= 0) return;

            BuildOption bo = opts.get(r - 1);
            if (pl.cash < bo.cost) {
                pushInfo(state, "Not enough cash for build.");
                return;
            }
            pl.cash -= bo.cost;
            bo.apply.run();
            state._investmentsThisTurn += 1;
            pushInfo(state, "Built: " + bo.label);
            UiHud.logBought(state, pl, bo.label, bo.cost);
            return;
        }

        // Owned by someone else => PLAYER ROLLS (no auto roll)
        showVisitRollDialog(state, pl, pr, "nights");
    }

    // ---------------- CASINO ----------------
    private static void handleCasino(GameState state, Player pl) {
        JDialog dlg = new JDialog((Frame) null, "Casino Roulette", true);
        dlg.setLayout(new BorderLayout());

        JLabel top = new JLabel("Roulette (1 round). After gambling: extra risk +5%-units.", SwingConstants.CENTER);
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        dlg.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(4, 2, 8, 8));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField betField = new JTextField("10000");
        String[] betType = {"RED", "BLACK", "NUMBER"};
        JComboBox<String> typeBox = new JComboBox<>(betType);
        JTextField numberField = new JTextField("7");

        center.add(new JLabel("Bet amount ($):"));
        center.add(betField);
        center.add(new JLabel("Bet type:"));
        center.add(typeBox);
        center.add(new JLabel("If NUMBER, choose 0-36:"));
        center.add(numberField);

        JLabel result = new JLabel(" ", SwingConstants.CENTER);
        center.add(new JLabel("Result:"));
        center.add(result);

        dlg.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton skip = new JButton("Skip");
        JButton play = new JButton("Play");
        bottom.add(skip);
        bottom.add(play);
        dlg.add(bottom, BorderLayout.SOUTH);

        skip.addActionListener(e -> dlg.dispose());

        play.addActionListener(e -> {
            long bet;
            try { bet = Long.parseLong(betField.getText().trim()); }
            catch (Exception ex) { bet = 0; }

            if (bet <= 0) { result.setText("Invalid bet."); return; }
            if (pl.cash < bet) { result.setText("Not enough cash."); return; }

            if (state.soundOn && state.cfg != null && state.cfg.sfxRouletteSpin != null) {
                Audio.playExclusive("roulette", state.cfg.sfxRouletteSpin, 0.75f);
            }

            int roll = new Random().nextInt(37);
            boolean isRed = (roll != 0) && (roll % 2 == 1);
            String color = (roll == 0) ? "GREEN" : (isRed ? "RED" : "BLACK");

            String t = (String) typeBox.getSelectedItem();
            long win = 0;

            if ("RED".equals(t)) {
                if ("RED".equals(color)) win = bet * 2;
            } else if ("BLACK".equals(t)) {
                if ("BLACK".equals(color)) win = bet * 2;
            } else {
                int chosen;
                try { chosen = Integer.parseInt(numberField.getText().trim()); }
                catch (Exception ex) { chosen = -1; }
                if (chosen < 0 || chosen > 36) { result.setText("Bad number."); return; }
                if (chosen == roll) win = bet * 36;
            }

            pl.cash -= bet;
            if (win > 0) pl.cash += win;

            pl.casinoRiskExtraPct += 5;

            if (state.soundOn && state.cfg != null) {
                if (win > 0 && state.cfg.sfxYouWin != null) Audio.play(state.cfg.sfxYouWin, 0.90f);
                if (win <= 0 && state.cfg.sfxGoofyLaugh != null) Audio.play(state.cfg.sfxGoofyLaugh, 0.90f);
            }

            result.setText("Spin: " + roll + " (" + color + ") | " + (win > 0 ? "WIN $" + format(win) : "LOSE"));
        });

        dlg.setSize(440, 260);
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    private static void pushInfo(GameState state, String msg) {
        if (state != null && state.infoLabel != null) state.infoLabel.setText(msg);
    }

    private static String format(long v) {
        return String.format("%,d", v);
    }
}

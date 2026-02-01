package investor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class UiHud {

    // ============================================================
    //  Event logging public API (call these from TurnLogic/Finance)
    // ============================================================

    public static void logTurnStart(GameState state) {
        if (eventLogPanel == null || state == null) return;
        int t = state.turnNumber;
        eventLogPanel.pushLine("---------------");
        eventLogPanel.pushLine("*Turn " + t + " start*");
    }

    public static void logTurnEnd(GameState state) {
        if (eventLogPanel == null || state == null) return;
        int t = state.turnNumber;
        eventLogPanel.pushLine("*Turn " + t + " ends*");
    }

    public static void logNpcOfficials(GameState state, String message) {
        logEvent(state, "NPC Officials", message);
    }

    public static void logRollMove(GameState state, Player p, int roll, int fromPosId, int toPosId) {
        String pn = (p == null ? "Player" : p.name);

        String fromTxt;
        if (fromPosId < 0) fromTxt = "spawn";
        else fromTxt = "square " + (fromPosId + 1);

        String toTxt;
        if (toPosId < 0) toTxt = "spawn";
        else toTxt = "square " + (toPosId + 1);

        logEvent(state, pn, "roll dice, #" + roll + ", from " + fromTxt + " to " + toTxt);
    }

    public static void logVisit(GameState state, Player p, String where) {
        String pn = (p == null ? "Player" : p.name);
        logEvent(state, pn, "visits at " + where);
    }

    public static void logRollNights(GameState state, Player p, int nights) {
        String pn = (p == null ? "Player" : p.name);
        logEvent(state, pn, "roll dice, " + nights + " nights");
    }

    public static void logBought(GameState state, Player p, String what, long price) {
        String pn = (p == null ? "Player" : p.name);
        logEvent(state, pn, "bought " + what + " for $" + String.format("%,d", price));
    }

    public static void logPaysNights(GameState state, Player p, int nights, long unitFee, long total, String what) {
        String pn = (p == null ? "Player" : p.name);
        logEvent(state, pn, "pays " + nights + "*$" + String.format("%,d", unitFee)
                + ", total $" + String.format("%,d", total) + " for " + what);
    }

    public static void logEvent(GameState state, String playerName, String message) {
        if (eventLogPanel == null) return;

        String pn = playerName;
        if (pn == null || pn.isBlank()) {
            try {
                pn = state.currentPlayer().name;
            } catch (Exception ignored) {
                pn = "Player";
            }
        }

        long n = ++eventSeq;
        String line = "#" + n + " " + pn + " " + (message == null ? "" : message.trim());
        eventLogPanel.pushLine(line);
    }

    // ============================================================
    //  Overlay layout tuning
    // ============================================================

    private static final int MARGIN = 14;

    private static final int STRIP_HEIGHT = 125;
    private static final int STRIP_MAX_WIDTH = 620;
    private static final int STRIP_MIN_WIDTH = 380;

    private static final int ACTIVE_W = 420;
    private static final int ACTIVE_H = 220;

    private static final int TOP_W = 312;
    private static final int TOP_H = 64;

    private static final int LOG_W = 470;
    private static final int LOG_H = 150;

    // ============================================================
    //  Glass styling
    // ============================================================

    private static final Color GLASS_FILL = new Color(255, 255, 255, 150);
    private static final Color GLASS_EDGE = new Color(30, 30, 40, 110);
    private static final Color TEXT_HILITE = new Color(255, 255, 255, 165);

    // ============================================================
    //  Keep references
    // ============================================================

    private static JPanel overlayRoot;
    private static JPanel stripPanel;
    private static ActivePlayerPanel activePanel;
    private static final List<CompactPlayerPanel> compactPanels = new ArrayList<>();

    private static TopListPanel topListPanel;
    private static javax.swing.Timer topRotateTimer;

    private static EventLogPanel eventLogPanel;
    private static long eventSeq = 0;

    public static JPanel buildOverlayHud(GameState state) {
        overlayRoot = new JPanel(null);
        overlayRoot.setOpaque(false);

        topListPanel = new TopListPanel(state);
        overlayRoot.add(topListPanel);

        eventLogPanel = new EventLogPanel(state);
        overlayRoot.add(eventLogPanel);

        stripPanel = new GlassPanel();
        stripPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));

        compactPanels.clear();
        stripPanel.removeAll();
        for (int i = 0; i < state.players.size(); i++) {
            CompactPlayerPanel cp = new CompactPlayerPanel(state, i);
            compactPanels.add(cp);
            stripPanel.add(cp);
        }

        activePanel = new ActivePlayerPanel(state);

        overlayRoot.add(stripPanel);
        overlayRoot.add(activePanel);

        positionOverlay(state);

        overlayRoot.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                positionOverlay(state);
            }
        });

        if (topRotateTimer != null) {
            topRotateTimer.stop();
            topRotateTimer = null;
        }
        topRotateTimer = new javax.swing.Timer(15_000, e -> {
            if (topListPanel != null) {
                topListPanel.nextMode();
                topListPanel.refresh();
            }
        });
        topRotateTimer.setRepeats(true);
        topRotateTimer.start();

        refreshAll(state);
        return overlayRoot;
    }

    public static void positionOverlay(GameState state) {
        if (overlayRoot == null || stripPanel == null || activePanel == null) return;

        int w = overlayRoot.getWidth();
        int h = overlayRoot.getHeight();
        if (w <= 0 || h <= 0) return;

        if (topListPanel != null) {
            topListPanel.setBounds(MARGIN, MARGIN, TOP_W, TOP_H);
        }

        if (eventLogPanel != null) {
            int lx = Math.max(MARGIN, w - LOG_W - MARGIN);
            int ly = MARGIN;
            eventLogPanel.setBounds(lx, ly, LOG_W, LOG_H);
        }

        int ax = Math.max(MARGIN, w - ACTIVE_W - MARGIN);
        int ay = Math.max(MARGIN, h - ACTIVE_H - MARGIN);
        activePanel.setBounds(ax, ay, ACTIVE_W, ACTIVE_H);

        int stripW = ax - (MARGIN * 2);
        stripW = Math.max(STRIP_MIN_WIDTH, Math.min(stripW, STRIP_MAX_WIDTH));
        int sx = MARGIN;
        int sy = h - STRIP_HEIGHT - MARGIN;
        stripPanel.setBounds(sx, sy, stripW, STRIP_HEIGHT);
    }

    public static void refreshAll(GameState state) {
        refreshTopHud(state);
        refreshPlayersRow(state);
        if (topListPanel != null) topListPanel.refresh();
    }

    public static void refreshTopHud(GameState state) {
        if (state.marketLabel != null) {
            state.marketLabel.setText(String.format("FED: %.2f%%", state.fedRate));
        }
        if (state.infoLabel != null) {
            String cur = state.infoLabel.getText();
            if (cur == null || cur.isBlank() || cur.startsWith("Turn:")) {
                Player pl = state.currentPlayer();
                state.infoLabel.setText("Turn: " + pl.name);
            }
        }
        if (activePanel != null) activePanel.refresh();
        if (topListPanel != null) topListPanel.refresh();
    }

    public static void refreshPlayersRow(GameState state) {
        if (stripPanel != null) {
            for (CompactPlayerPanel cp : compactPanels) cp.refresh(false);

            for (CompactPlayerPanel cp : compactPanels) {
                boolean isCurrent = (cp.playerIndex == state.currentPlayerIndex);
                cp.refresh(isCurrent);
            }
        }
        if (activePanel != null) activePanel.refresh();
    }

    static class GlassPanel extends JPanel {
        GlassPanel() {
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 14;

            g2.setColor(new Color(0,0,0,35));
            g2.fillRoundRect(3, 4, w - 6, h - 6, arc, arc);

            g2.setColor(GLASS_FILL);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.setColor(GLASS_EDGE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ============================================================
    //  (The rest of your UiHud stays the same, except Roll button)
    // ============================================================

    static class TopListPanel extends GlassPanel {
        enum Mode { HIGHEST_NET, LOWEST_RISK_MARGIN, RISK_TO_GET_CAUGHT }

        private final GameState state;
        private Mode mode = Mode.RISK_TO_GET_CAUGHT;

        private final JLabel title = new JLabel();
        private final JTextArea body = new JTextArea();

        TopListPanel(GameState state) {
            this.state = state;
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            title.setFont(new Font("SansSerif", Font.BOLD, 12));
            title.setOpaque(true);
            title.setBackground(TEXT_HILITE);
            title.setBorder(new EmptyBorder(2, 4, 2, 4));
            add(title, BorderLayout.NORTH);

            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setFont(new Font("SansSerif", Font.PLAIN, 11));
            body.setOpaque(false);
            add(body, BorderLayout.CENTER);

            refresh();
        }

        void nextMode() {
            Mode[] all = Mode.values();
            int idx = 0;
            for (int i = 0; i < all.length; i++) {
                if (all[i] == mode) { idx = i; break; }
            }
            mode = all[(idx + 1) % all.length];
        }

        void refresh() {
            if (state == null || state.players == null) return;

            switch (mode) {
                case HIGHEST_NET -> renderHighestNet();
                case LOWEST_RISK_MARGIN -> renderLowestRisk();
                case RISK_TO_GET_CAUGHT -> renderCaughtRisk();
            }
        }

        private void renderHighestNet() {
            title.setText("Rank: Highest net");
            List<Player> ps = new ArrayList<>(state.players);
            ps.sort((a,b) -> Long.compare(b.netWorth(), a.netWorth()));

            StringBuilder sb = new StringBuilder();
            int n = Math.min(5, ps.size());
            for (int i = 0; i < n; i++) {
                Player p = ps.get(i);
                sb.append(String.format("%d) %s | $%,d%n", i+1, safeName(p), p.netWorth()));
            }
            body.setText(sb.toString().trim());
        }

        private void renderLowestRisk() {
            title.setText("Rank: Lowest risk");
            List<Player> ps = new ArrayList<>(state.players);
            ps.sort((a,b) -> Integer.compare(riskMarginTotal(a), riskMarginTotal(b)));

            StringBuilder sb = new StringBuilder();
            int n = Math.min(5, ps.size());
            for (int i = 0; i < n; i++) {
                Player p = ps.get(i);
                sb.append(String.format("%d) %s | %d%%%n", i+1, safeName(p), riskMarginTotal(p)));
            }
            body.setText(sb.toString().trim());
        }

        private void renderCaughtRisk() {
            title.setText("Rank: Caught risk (0–2)");
            List<Player> ps = new ArrayList<>(state.players);
            ps.sort((a,b) -> Integer.compare(caughtRiskScore(b), caughtRiskScore(a)));

            StringBuilder sb = new StringBuilder();
            int n = Math.min(5, ps.size());
            for (int i = 0; i < n; i++) {
                Player p = ps.get(i);
                int score = caughtRiskScore(p);
                String label = switch (score) {
                    case 0 -> "0 (none)";
                    case 1 -> "1 (one)";
                    default -> "2 (both)";
                };
                sb.append(String.format("%d) %s | %s%n", i+1, safeName(p), label));
            }
            body.setText(sb.toString().trim());
        }

        private String safeName(Player p) {
            try {
                return (p.name == null || p.name.isBlank()) ? "Player" : p.name;
            } catch (Exception e) {
                return "Player";
            }
        }

        private int riskMarginTotal(Player p) {
            int rm = 0;
            try { rm += p.riskMarginPct; } catch (Exception ignored) {}
            try { rm += p.casinoRiskExtraPct; } catch (Exception ignored) {}
            return Math.max(0, rm);
        }

        private int caughtRiskScore(Player p) {
            boolean tax = false, wel = false;
            try { tax = p.taxEvasionOpen; } catch (Exception ignored) {}
            try { wel = p.welfareFraudOpen; } catch (Exception ignored) {}
            int score = 0;
            if (tax) score++;
            if (wel) score++;
            return score;
        }
    }

    static class EventLogPanel extends GlassPanel {
        private final JTextArea body = new JTextArea();
        private final JScrollPane sc = new JScrollPane(body);

        private long lastUserScrollMs = 0;
        private final javax.swing.Timer autoBackTimer;

        EventLogPanel(GameState state) {
            setLayout(new BorderLayout(6, 6));
            setBorder(new EmptyBorder(6, 8, 6, 8));

            JLabel title = new JLabel("Events");
            title.setFont(new Font("SansSerif", Font.BOLD, 12));
            title.setOpaque(true);
            title.setBackground(TEXT_HILITE);
            title.setBorder(new EmptyBorder(2, 4, 2, 4));
            add(title, BorderLayout.NORTH);

            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setFont(new Font("SansSerif", Font.PLAIN, 11));
            body.setOpaque(true);
            body.setBackground(new Color(255,255,255,130));
            body.setBorder(new EmptyBorder(6, 6, 6, 6));

            sc.setOpaque(false);
            sc.getViewport().setOpaque(false);
            sc.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
            add(sc, BorderLayout.CENTER);

            JScrollBar vs = sc.getVerticalScrollBar();
            vs.addAdjustmentListener(e -> markUserScroll());
            sc.addMouseWheelListener(e -> markUserScroll());
            body.addMouseWheelListener(e -> markUserScroll());

            autoBackTimer = new javax.swing.Timer(1000, e -> {
                if (System.currentTimeMillis() - lastUserScrollMs >= 60_000) {
                    scrollToTop();
                }
            });
            autoBackTimer.setRepeats(true);
            autoBackTimer.start();
        }

        void pushLine(String line) {
            if (line == null) return;

            String cur = body.getText();
            if (cur == null || cur.isEmpty()) body.setText(line);
            else body.setText(line + "\n" + cur);

            scrollToTop();
        }

        private void markUserScroll() {
            lastUserScrollMs = System.currentTimeMillis();
        }

        private void scrollToTop() {
            SwingUtilities.invokeLater(() -> {
                try {
                    body.setCaretPosition(0);
                    sc.getVerticalScrollBar().setValue(0);
                } catch (Exception ignored) {}
            });
        }
    }

    static class CompactPlayerPanel extends JPanel {
        final GameState state;
        final int playerIndex;

        private final JLabel name = new JLabel();
        private final JLabel money = new JLabel();
        private final JLabel debt = new JLabel();
        private final JLabel net  = new JLabel();
        private final JLabel due  = new JLabel();

        CompactPlayerPanel(GameState state, int idx) {
            this.state = state;
            this.playerIndex = idx;

            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(2, 6, 2, 6));
            setPreferredSize(new Dimension(190, 92));

            name.setFont(new Font("SansSerif", Font.BOLD, 12));
            money.setFont(new Font("SansSerif", Font.PLAIN, 11));
            debt.setFont(new Font("SansSerif", Font.PLAIN, 11));
            net.setFont(new Font("SansSerif", Font.PLAIN, 11));
            due.setFont(new Font("SansSerif", Font.PLAIN, 11));
            due.setForeground(new Color(90, 90, 105));

            name.setOpaque(true);
            name.setBackground(TEXT_HILITE);
            name.setBorder(new EmptyBorder(1,3,1,3));

            add(name);
            add(money);
            add(debt);
            add(net);
            add(due);

            refresh(false);
        }

        void refresh(boolean isCurrent) {
            Player p = state.players.get(playerIndex);

            name.setText(p.name);
            money.setText("Cash: $" + fmt(p.cash));
            debt.setText("Debt: $" + fmt(p.debt));
            net.setText("Net: $" + fmt(p.netWorth()));

            due.setText("Due (this round): $" + fmt(0));

            name.setForeground(isCurrent ? new Color(20,20,25) : new Color(30,30,40));
        }

        private String fmt(long v) { return String.format("%,d", v); }
    }

    static class ActivePlayerPanel extends GlassPanel {
        final GameState state;

        private final JLabel title = new JLabel();
        private final JLabel cash  = new JLabel();
        private final JLabel debt  = new JLabel();
        private final JLabel net   = new JLabel();
        private final JLabel risk  = new JLabel();
        private final JLabel props = new JLabel();
        private final JLabel bonds = new JLabel();

        private final JTextArea ownedList = new JTextArea();
        private final JScrollPane ownedScroll = new JScrollPane(ownedList);

        private final JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        private final JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));

        private final JButton soundBtn = new JButton("🔊");

        ActivePlayerPanel(GameState state) {
            this.state = state;
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(6, 8, 6, 8));

            topRow.setOpaque(false);

            title.setFont(new Font("SansSerif", Font.BOLD, 14));
            title.setOpaque(true);
            title.setBackground(TEXT_HILITE);
            title.setBorder(new EmptyBorder(2,4,2,4));
            topRow.add(title);

            if (state.diceLabel == null) {
                state.diceLabel = new JLabel("🎲");
                state.diceLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
                state.diceLabel.setOpaque(true);
                state.diceLabel.setBackground(TEXT_HILITE);
                state.diceLabel.setBorder(new EmptyBorder(2,4,2,4));
            }
            topRow.add(state.diceLabel);

            if (state.marketLabel == null) {
                state.marketLabel = new JLabel(String.format("FED: %.2f%%", state.fedRate));
                state.marketLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
                state.marketLabel.setOpaque(true);
                state.marketLabel.setBackground(TEXT_HILITE);
                state.marketLabel.setBorder(new EmptyBorder(2,4,2,4));
            }
            topRow.add(state.marketLabel);

            add(topRow, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
            center.setOpaque(false);
            center.setBorder(new EmptyBorder(6, 2, 6, 2));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

            setSmall(cash); setSmall(debt); setSmall(net); setSmall(risk);
            setSmall(props); setSmall(bonds);

            left.add(cash);
            left.add(debt);
            left.add(net);
            left.add(risk);
            left.add(Box.createVerticalStrut(6));
            left.add(props);
            left.add(bonds);

            ownedList.setEditable(false);
            ownedList.setLineWrap(true);
            ownedList.setWrapStyleWord(true);
            ownedList.setFont(new Font("SansSerif", Font.PLAIN, 12));
            ownedList.setOpaque(true);
            ownedList.setBackground(new Color(255,255,255,130));
            ownedList.setBorder(new EmptyBorder(6, 6, 6, 6));

            ownedScroll.setOpaque(false);
            ownedScroll.getViewport().setOpaque(false);
            ownedScroll.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));

            center.add(left);
            center.add(ownedScroll);

            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            bottom.setBorder(new EmptyBorder(0, 2, 2, 2));

            if (state.infoLabel == null) state.infoLabel = new JLabel(" ");
            state.infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            state.infoLabel.setOpaque(true);
            state.infoLabel.setBackground(TEXT_HILITE);
            state.infoLabel.setBorder(new EmptyBorder(2,4,2,4));
            bottom.add(state.infoLabel, BorderLayout.WEST);

            btnRow.setOpaque(false);

            soundBtn.setFocusPainted(false);
            soundBtn.addActionListener(e -> {
                state.soundOn = !state.soundOn;
                Audio.setEnabled(state.soundOn);

                soundBtn.setText(state.soundOn ? "🔊" : "🔇");
                soundBtn.setForeground(state.soundOn ? new Color(20,20,25) : new Color(180,40,40));

                if (state.soundOn) {
                    Audio.startBgmRandom(state, 0.25f);
                    if (state.infoLabel != null) state.infoLabel.setText("Sound ON");
                } else {
                    Audio.stopBgm();
                    if (state.infoLabel != null) state.infoLabel.setText("Sound OFF");
                }
            });
            soundBtn.setText(state.soundOn ? "🔊" : "🔇");
            soundBtn.setForeground(state.soundOn ? new Color(20,20,25) : new Color(180,40,40));

            if (state.rollBtn == null) state.rollBtn = new JButton("Roll");

            state.rollBtn.addActionListener(e -> {
                if (state.isBusy) return;

                // ========================================================
                // VISIT ROLL MODE (player must roll to pay for visit)
                // ========================================================
                if (state.awaitingVisitRoll) {
                    state.isBusy = true;
                    state.rollBtn.setEnabled(false);

                    Dice.rollDiceAnimated(state, roll -> {
                        if (state.infoLabel != null) state.infoLabel.setText("Visit roll: " + roll);
                        state.isBusy = false;
                        if (state.rollBtn != null) state.rollBtn.setEnabled(true);

                        Finance.finishPendingVisitRoll(state, roll);
                    });
                    return;
                }

                // Normal turn flow
                TurnLogic.startTurn(state);

                Dice.rollDiceAnimated(state, roll -> {
                    Player pl = state.currentPlayer();
                    if (state.infoLabel != null) state.infoLabel.setText("Turn: " + pl.name + " | Rolled " + roll);

                    TurnLogic.movePlayerAnimated(state, roll, () -> TurnLogic.endTurn(state));
                });
            });

            btnRow.add(soundBtn);
            btnRow.add(state.rollBtn);

            bottom.add(btnRow, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);

            refresh();
        }

        private void setSmall(JLabel l) {
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setForeground(new Color(30, 30, 40));
            l.setOpaque(true);
            l.setBackground(new Color(255,255,255,120));
            l.setBorder(new EmptyBorder(1,3,1,3));
        }

        void refresh() {
            Player p = state.currentPlayer();
            title.setText("ACTIVE: " + p.name);

            cash.setText("Cash: $" + fmt(p.cash));
            debt.setText("Debt: $" + fmt(p.debt));
            net.setText("Net: $" + fmt(p.netWorth()));

            int totalRisk = 0;
            try { totalRisk = p.riskMarginPct + p.casinoRiskExtraPct; } catch (Exception ignored) {}
            risk.setText("Risk margin: " + totalRisk + "%");

            long pv = 0;
            try { pv = p.propertiesValue(); } catch (Exception ignored) {}

            long bondsAll = 0;
            try { bondsAll = p.bondsLT1 + p.bonds1to2 + p.bondsGT2; } catch (Exception ignored) {}

            props.setText("Businesses: $" + fmt(pv));
            bonds.setText("Bonds: $" + fmt(bondsAll));

            ownedList.setText(buildOwnedText(p));

            if (state.marketLabel != null) {
                state.marketLabel.setText(String.format("FED: %.2f%%", state.fedRate));
            }
        }

        private String buildOwnedText(Player p) {
            StringBuilder sb = new StringBuilder();
            sb.append("Businesses:\n");
            if (p.owned == null || p.owned.isEmpty()) {
                sb.append(" - (none)\n");
                return sb.toString();
            }
            for (Property pr : p.owned) {
                if (pr == null) continue;
                sb.append(" - ").append(pr.name).append("\n");
            }
            return sb.toString();
        }

        private String fmt(long v) { return String.format("%,d", v); }
    }
}

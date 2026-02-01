package investor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

public class SettingsDialogs {

    public static class SetupResult {
        public final List<Player> players;
        public SetupResult(List<Player> players) { this.players = players; }
    }

    public static SetupResult showSetupDialog(JFrame parent, GameState state) {
        if (state == null || state.cfg == null) return null;

        final JDialog dlg = new JDialog(parent, "Investor setup", true);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());

        // ---------- Card layout ----------
        CardLayout cards = new CardLayout();
        JPanel cardRoot = new JPanel(cards);

        // Shared state
        final int[] selectedPlayers = { -1 };
        final JTextField startCashField = new JTextField("" + state.cfg.startCash, 12);

        // Player form state (built after player count chosen)
        final java.util.List<JTextField> nameFields = new ArrayList<>();
        final java.util.List<JButton> pawnButtons = new ArrayList<>();
        final java.util.Map<Integer, String> pawnByPlayer = new HashMap<>();

        // ---------- Settings card ----------
        JPanel settingsCard = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        JLabel hdr = new JLabel("Setup");
        hdr.setFont(new Font("SansSerif", Font.BOLD, 14));

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2;
        settingsCard.add(hdr, gc);

        gc.gridwidth = 1;

        // Players row (buttons 2..7)
        gc.gridx = 0; gc.gridy = 1;
        settingsCard.add(new JLabel("Players (2–7)"), gc);

        JPanel playersBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        int minP = 2, maxP = 7;
        for (int n = minP; n <= maxP; n++) {
            JButton b = new JButton("" + n);
            b.setFocusPainted(false);
            final int nn = n;
            b.addActionListener(e -> {
                selectedPlayers[0] = nn;

                // Build players card now
                buildPlayersCard(state, nn, nameFields, pawnButtons, pawnByPlayer);

                cards.show(cardRoot, "PLAYERS");
                dlg.pack();
                dlg.setLocationRelativeTo(parent);
            });
            playersBtns.add(b);
        }

        gc.gridx = 1; gc.gridy = 1;
        settingsCard.add(playersBtns, gc);

        // Extra settings: Start cash
        gc.gridx = 0; gc.gridy = 2;
        settingsCard.add(new JLabel("Start cash ($)"), gc);

        gc.gridx = 1; gc.gridy = 2;
        settingsCard.add(startCashField, gc);

        JLabel hint = new JLabel("<html><i>Pick players count to continue to player & pawn selection.</i></html>");
        hint.setForeground(new Color(60, 60, 80));

        gc.gridx = 0; gc.gridy = 3; gc.gridwidth = 2;
        settingsCard.add(hint, gc);

        // ---------- Players card ----------
        JPanel playersCard = new JPanel(new BorderLayout(10, 10));
        playersCard.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel playersHdr = new JLabel("Players & pawns");
        playersHdr.setFont(new Font("SansSerif", Font.BOLD, 14));
        playersCard.add(playersHdr, BorderLayout.NORTH);

        JPanel playersFormHolder = new JPanel(new BorderLayout());
        playersCard.add(playersFormHolder, BorderLayout.CENTER);

        // This gets rebuilt by buildPlayersCard(...)
        // We attach a live view that always reflects nameFields+pawnButtons.
        playersFormHolder.add(new JScrollPane(new JPanel()), BorderLayout.CENTER);

        // Update the holder content whenever we enter this card
        cardRoot.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) {
                // no-op
            }
        });

        // We'll refresh formHolder each time we switch to players card
        // by calling a small helper:
        Runnable refreshPlayersFormUi = () -> {
            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints pc = new GridBagConstraints();
            pc.insets = new Insets(6, 6, 6, 6);
            pc.anchor = GridBagConstraints.WEST;
            pc.fill = GridBagConstraints.HORIZONTAL;
            pc.weightx = 1.0;

            pc.gridx = 0; pc.gridy = 0;
            form.add(new JLabel("Name"), pc);
            pc.gridx = 1;
            form.add(new JLabel("Pawn"), pc);

            for (int i = 0; i < nameFields.size(); i++) {
                pc.gridy = i + 1;

                pc.gridx = 0;
                form.add(nameFields.get(i), pc);

                pc.gridx = 1;
                JButton pb = pawnButtons.get(i);
                form.add(pb, pc);
            }

            form.add(Box.createVerticalGlue());

            JScrollPane sc = new JScrollPane(form);
            sc.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
            sc.getViewport().setOpaque(false);

            playersFormHolder.removeAll();
            playersFormHolder.add(sc, BorderLayout.CENTER);
            playersFormHolder.revalidate();
            playersFormHolder.repaint();
        };

        // ---------- Card root ----------
        cardRoot.add(settingsCard, "SETTINGS");
        cardRoot.add(playersCard, "PLAYERS");
        dlg.add(cardRoot, BorderLayout.CENTER);

        // ---------- Bottom buttons (only Start + Cancel) ----------
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancel = new JButton("Cancel / Quit game");
        JButton start = new JButton("Start");
        start.setEnabled(false);

        cancel.addActionListener(e -> dlg.dispose());

        // When entering PLAYERS card, enable Start only when valid
        javax.swing.Timer validateTimer = new javax.swing.Timer(200, e -> {
            boolean ok = validateSetup(selectedPlayers[0], startCashField.getText(), nameFields, pawnByPlayer);
            start.setEnabled(ok);
        });
        validateTimer.setRepeats(true);
        validateTimer.start();

        start.addActionListener(e -> {
            SetupResult res = tryBuildResult(state, selectedPlayers[0], startCashField.getText(), nameFields, pawnByPlayer);
            if (res == null) return;
            // store result into dialog client props so caller can fetch
            dlg.getRootPane().putClientProperty("SETUP_RESULT", res);
            dlg.dispose();
        });

        bottom.add(cancel);
        bottom.add(start);
        dlg.add(bottom, BorderLayout.SOUTH);

        // ---------- Pawn picking behaviour ----------
        // Each pawn button opens a picker dialog that disables already-used pawns.
        // We wire this after buildPlayersCard runs (buttons exist then).
        java.awt.event.HierarchyListener hw = e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (dlg.isShowing()) {
                    // whenever dialog shows and players card selected, refresh UI
                    if (selectedPlayers[0] > 0) refreshPlayersFormUi.run();
                }
            }
        };
        dlg.addHierarchyListener(hw);

        // We also refresh whenever switching to PLAYERS card via buttons:
        for (Component c : playersBtns.getComponents()) {
            if (c instanceof JButton) {
                ((JButton) c).addActionListener(e -> refreshPlayersFormUi.run());
            }
        }

        // Initial size/location
        dlg.pack();
        dlg.setMinimumSize(new Dimension(520, 360));
        dlg.setLocationRelativeTo(parent);

        cards.show(cardRoot, "SETTINGS");
        dlg.setVisible(true);

        validateTimer.stop();

        Object o = dlg.getRootPane().getClientProperty("SETUP_RESULT");
        if (o instanceof SetupResult) return (SetupResult) o;
        return null;
    }

    // ============================================================
    // Players card builder
    // ============================================================

    private static void buildPlayersCard(GameState state,
                                         int n,
                                         List<JTextField> nameFields,
                                         List<JButton> pawnButtons,
                                         Map<Integer, String> pawnByPlayer) {

        nameFields.clear();
        pawnButtons.clear();
        pawnByPlayer.clear();

        // seed default names
        for (int i = 0; i < n; i++) {
            JTextField tf = new JTextField("Player " + (i + 1), 16);
            nameFields.add(tf);
        }

        for (int i = 0; i < n; i++) {
            final int idx = i;

            JButton pawnBtn = new JButton("Choose pawn");
            pawnBtn.setFocusPainted(false);

            pawnBtn.addActionListener(e -> {
                String chosen = choosePawnDialog(
                        SwingUtilities.getWindowAncestor(pawnBtn),
                        "Choose pawn for " + safeFieldText(nameFields.get(idx), "Player " + (idx + 1)),
                        idx,
                        pawnByPlayer
                );

                if (chosen != null) {
                    pawnByPlayer.put(idx, chosen);
                    updatePawnButtonVisual(pawnBtn, chosen);
                }
            });

            pawnButtons.add(pawnBtn);
        }
    }

    private static String safeFieldText(JTextField tf, String fallback) {
        if (tf == null) return fallback;
        String t = tf.getText();
        if (t == null || t.trim().isEmpty()) return fallback;
        return t.trim();
    }

    // ============================================================
    // Pawn picker (icons)
    // ============================================================

    private static ImageIcon loadPawnIcon(String key, int size) {
        try {
            BufferedImage img = ImageIO.read(new File(Player.pawnPath(key)));
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    private static void updatePawnButtonVisual(JButton b, String key) {
        if (b == null) return;
        b.setText(key);
        ImageIcon ic = loadPawnIcon(key, 36);
        b.setIcon(ic);
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
    }

    private static String choosePawnDialog(Window parent, String title, int playerIndex, Map<Integer, String> pawnByPlayer) {
        // Determine which pawns are taken by other players
        Set<String> taken = new HashSet<>();
        for (Map.Entry<Integer, String> e : pawnByPlayer.entrySet()) {
            if (e.getKey() == playerIndex) continue;
            if (e.getValue() != null) taken.add(e.getValue());
        }

        final String[] picked = { null };

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (String key : Player.PAWN_KEYS) {
            JButton b = new JButton(key, loadPawnIcon(key, 64));
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);

            boolean disabled = taken.contains(key);
            b.setEnabled(!disabled);

            if (disabled) {
                b.setToolTipText("Already chosen by another player");
            }

            b.addActionListener(ev -> picked[0] = key);
            grid.add(b);
        }

        while (true) {
            picked[0] = null;

            int res = JOptionPane.showConfirmDialog(
                    parent,
                    grid,
                    title,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (res != JOptionPane.OK_OPTION) return null;

            if (picked[0] != null && !taken.contains(picked[0])) {
                return picked[0];
            }

            JOptionPane.showMessageDialog(parent,
                    "Click one pawn first, then press OK.",
                    "No selection",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ============================================================
    // Validation + result building
    // ============================================================

    private static boolean validateSetup(int n,
                                         String startCashTxt,
                                         List<JTextField> nameFields,
                                         Map<Integer, String> pawnByPlayer) {
        if (n < 2 || n > 7) return false;

        long sc;
        try { sc = Long.parseLong(cleanNum(startCashTxt)); }
        catch (Exception ex) { return false; }
        if (sc < 0) return false;

        if (nameFields.size() != n) return false;

        // names must exist
        for (int i = 0; i < n; i++) {
            String name = nameFields.get(i).getText();
            if (name == null || name.trim().isEmpty()) return false;
        }

        // all pawns chosen + unique
        if (pawnByPlayer.size() != n) return false;

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String pk = pawnByPlayer.get(i);
            if (pk == null || pk.isBlank()) return false;
            if (!seen.add(pk)) return false;
        }

        return true;
    }

    private static SetupResult tryBuildResult(GameState state,
                                             int n,
                                             String startCashTxt,
                                             List<JTextField> nameFields,
                                             Map<Integer, String> pawnByPlayer) {
        if (!validateSetup(n, startCashTxt, nameFields, pawnByPlayer)) {
            JOptionPane.showMessageDialog(null,
                    "Please complete setup: choose player count, enter names, and select unique pawns for all players.",
                    "Setup incomplete",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }

        long sc;
        try { sc = Long.parseLong(cleanNum(startCashTxt)); }
        catch (Exception ex) { sc = state.cfg.startCash; }

        state.cfg.startCash = sc;

        List<Player> players = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = nameFields.get(i).getText().trim();
            if (name.isEmpty()) name = "Player " + (i + 1);

            Player p = new Player(name, Color.GRAY, sc);
            p.cash = sc;

            String pawn = pawnByPlayer.get(i);
            p.pawnKeyChosen = pawn;
            p.pawnKeyCurrent = pawn;
            p.color = Player.pawnColor(pawn);

            players.add(p);
        }

        return new SetupResult(players);
    }

    private static String cleanNum(String s) {
        if (s == null) return "0";
        return s.trim().replace("_", "").replace(",", "");
    }

    private SettingsDialogs() {}
}

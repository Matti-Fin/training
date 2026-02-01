package investor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

public class StartupUI {

    // ------------------------------------------------------------
    // Public selections (read from Investor after dialog closes)
    // ------------------------------------------------------------
    public enum Lang { EN, FI, SV }

    private static Lang selectedLang = Lang.EN;
    private static int selectedPlayers = 2;
    private static int selectedNpcPlayers = 0;
    private static long selectedStartCash = 1_800;
    private static int selectedTimeLimitMin = 0; // 0 = off

    public static Lang getSelectedLang() { return selectedLang; }
    public static int getSelectedNpcPlayers() { return selectedNpcPlayers; }
    public static long getSelectedStartCash() { return selectedStartCash; }
    public static int getSelectedTimeLimitMin() { return selectedTimeLimitMin; }
    public static int getSelectedPlayers() { return selectedPlayers; }

    // ------------------------------------------------------------
    // Pawn keys (gold not selectable at start)
    // ------------------------------------------------------------
    private static final String[] PAWN_KEYS = {
            "blue","red","green","yellow","purple","pink","orange","cyan"
    };

    public static class PlayerInit {
        public String name;
        public String pawnKey;
        public PlayerInit(String name, String pawnKey) {
            this.name = name;
            this.pawnKey = pawnKey;
        }
    }

    // ------------------------------------------------------------
    // Start dialog
    // ------------------------------------------------------------
    public static List<PlayerInit> showStartupDialog(Component parent) {
        final JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent), "Start", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        CardLayout cards = new CardLayout();
        JPanel cardRoot = new JPanel(cards);

        // shared state
        final JTextField startCashField = new JTextField(String.valueOf(selectedStartCash), 10);
        final JSpinner npcSpin = new JSpinner(new SpinnerNumberModel(selectedNpcPlayers, 0, 50, 1));
        final JComboBox<String> timeBox = new JComboBox<>(new String[]{
                "Off", "30 min", "60 min", "90 min", "120 min", "150 min", "180 min"
        });
        timeBox.setSelectedIndex(timeLimitIndex(selectedTimeLimitMin));

        // players card state
        final java.util.List<JTextField> nameFields = new ArrayList<>();
        final java.util.Map<Integer, String> pawnByPlayer = new HashMap<>();
        final java.util.List<JButton> pawnButtons = new ArrayList<>();

        // ---------- SETTINGS CARD ----------
        JPanel settings = new JPanel();
        settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
        settings.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Start game");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(title);
        settings.add(Box.createVerticalStrut(10));

        // Language flags row
        JPanel langRow = new JPanel(new GridLayout(1, 3, 10, 0));
        langRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        langRow.setOpaque(false);

        FlagButton fi = new FlagButton("sfx/flags/fi.png", Lang.FI);
        FlagButton sv = new FlagButton("sfx/flags/se.png", Lang.SV);
        FlagButton en = new FlagButton("sfx/flags/gb.png", Lang.EN);

        ButtonGroup langGroup = new ButtonGroup();
        langGroup.add(en);
        langGroup.add(fi);
        langGroup.add(sv);

        if (selectedLang == Lang.FI) fi.setSelected(true);
        else if (selectedLang == Lang.SV) sv.setSelected(true);
        else en.setSelected(true);

        langRow.add(en);
        langRow.add(fi);
        langRow.add(sv);
        settings.add(langRow);

        settings.add(Box.createVerticalStrut(12));

        // Players row: buttons 2..7 (immediate jump to player selection)
        JPanel playersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        playersRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        playersRow.setOpaque(false);
        playersRow.add(new JLabel("Players:"));

        JPanel pButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pButtons.setOpaque(false);

        ButtonGroup pGroup = new ButtonGroup();
        java.util.List<JToggleButton> pToggles = new ArrayList<>();
        for (int i = 2; i <= 7; i++) {
            JToggleButton b = new JToggleButton(String.valueOf(i));
            b.setFocusPainted(false);
            b.setMargin(new Insets(2, 10, 2, 10));
            final int val = i;
            b.addActionListener(e -> {
                selectedPlayers = val;
                buildPlayersForm(val, nameFields, pawnButtons, pawnByPlayer);
                cards.show(cardRoot, "PLAYERS");
                dlg.pack();
                dlg.setLocationRelativeTo(parent);
            });
            pGroup.add(b);
            pToggles.add(b);
            pButtons.add(b);
        }
        int def = Math.max(2, Math.min(7, selectedPlayers));
        pToggles.get(def - 2).setSelected(true);

        playersRow.add(pButtons);
        settings.add(playersRow);

        settings.add(Box.createVerticalStrut(12));

        // Extra settings (same view, not separate options)
        JPanel extra = new JPanel(new GridBagLayout());
        extra.setAlignmentX(Component.LEFT_ALIGNMENT);
        extra.setOpaque(false);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 0, 6, 10);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;

        gc.gridx = 0; gc.gridy = 0;
        extra.add(new JLabel("Start cash ($):"), gc);
        gc.gridx = 1;
        extra.add(startCashField, gc);

        gc.gridx = 0; gc.gridy = 1;
        extra.add(new JLabel("NPC players:"), gc);
        gc.gridx = 1;
        extra.add(npcSpin, gc);

        gc.gridx = 0; gc.gridy = 2;
        extra.add(new JLabel("Time limit:"), gc);
        gc.gridx = 1;
        extra.add(timeBox, gc);

        settings.add(extra);

        settings.add(Box.createVerticalStrut(10));
        JLabel hint = new JLabel("<html><i>Pick players count to continue to names & pawns.</i></html>");
        hint.setForeground(new Color(60, 60, 80));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        settings.add(hint);

        // ---------- PLAYERS CARD ----------
        JPanel players = new JPanel(new BorderLayout(10, 10));
        players.setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel pTitle = new JLabel("Players & pawns");
        pTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        players.add(pTitle, BorderLayout.NORTH);

        JPanel formHolder = new JPanel(new BorderLayout());
        players.add(formHolder, BorderLayout.CENTER);

        Runnable refreshPlayersForm = () -> {
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
                form.add(pawnButtons.get(i), pc);
            }

            JScrollPane sc = new JScrollPane(form);
            sc.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));

            formHolder.removeAll();
            formHolder.add(sc, BorderLayout.CENTER);
            formHolder.revalidate();
            formHolder.repaint();
        };

        // Build initial form (based on default selectedPlayers)
        buildPlayersForm(def, nameFields, pawnButtons, pawnByPlayer);
        refreshPlayersForm.run();

        // wire pawn buttons
        for (int i = 0; i < pawnButtons.size(); i++) {
            final int idx = i;
            pawnButtons.get(i).addActionListener(e -> {
                String chosen = choosePawnDialog(
                        dlg,
                        "Choose pawn for " + safeFieldText(nameFields.get(idx), "Player " + (idx + 1)),
                        idx,
                        pawnByPlayer
                );
                if (chosen != null) {
                    pawnByPlayer.put(idx, chosen);
                    updatePawnButtonVisual(pawnButtons.get(idx), chosen);
                }
            });
        }

        // rebuild pawn button listeners on rebuild
        Runnable rebuildPawnListeners = () -> {
            for (int i = 0; i < pawnButtons.size(); i++) {
                for (java.awt.event.ActionListener al : pawnButtons.get(i).getActionListeners()) {
                    pawnButtons.get(i).removeActionListener(al);
                }
                final int idx = i;
                pawnButtons.get(i).addActionListener(e -> {
                    String chosen = choosePawnDialog(
                            dlg,
                            "Choose pawn for " + safeFieldText(nameFields.get(idx), "Player " + (idx + 1)),
                            idx,
                            pawnByPlayer
                    );
                    if (chosen != null) {
                        pawnByPlayer.put(idx, chosen);
                        updatePawnButtonVisual(pawnButtons.get(idx), chosen);
                    }
                });
            }
        };

        // ---------- CARD ROOT ----------
        cardRoot.add(settings, "SETTINGS");
        cardRoot.add(players, "PLAYERS");

        dlg.setLayout(new BorderLayout());
        dlg.add(cardRoot, BorderLayout.CENTER);

        // ---------- Bottom buttons: ONLY Start + Cancel/Quit ----------
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton cancel = new JButton("Cancel / Quit game");
        JButton start = new JButton("Start");
        start.setEnabled(false);

        bottom.add(cancel);
        bottom.add(start);
        dlg.add(bottom, BorderLayout.SOUTH);

        final List<PlayerInit> result = new ArrayList<>();
        final boolean[] decided = { false };

        cancel.addActionListener(e -> {
            decided[0] = true;
            result.clear();
            dlg.dispose();
        });

        // validate loop for enabling Start
        javax.swing.Timer validateTimer = new javax.swing.Timer(200, e -> {
            boolean ok = validatePlayersForm(selectedPlayers, startCashField.getText(), nameFields, pawnByPlayer);
            start.setEnabled(ok);
        });
        validateTimer.setRepeats(true);
        validateTimer.start();

        start.addActionListener(e -> {
            if (!validatePlayersForm(selectedPlayers, startCashField.getText(), nameFields, pawnByPlayer)) {
                JOptionPane.showMessageDialog(dlg, "Complete setup: names + unique pawns for all players.", "Setup incomplete", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // commit shared selections
            selectedLang = FlagButton.readSelection(langGroup);
            selectedNpcPlayers = safeInt(npcSpin.getValue(), 0);
            selectedTimeLimitMin = timeLimitFromIndex(timeBox.getSelectedIndex());
            selectedStartCash = safeLong(startCashField.getText(), selectedStartCash);

            // build result
            result.clear();
            for (int i = 0; i < selectedPlayers; i++) {
                String nm = safeFieldText(nameFields.get(i), "Player " + (i + 1));
                String pk = pawnByPlayer.get(i);
                if (pk == null || pk.isBlank()) pk = "blue";
                result.add(new PlayerInit(nm, pk));
            }

            decided[0] = true;
            dlg.dispose();
        });

        // when players count clicked -> rebuild form holder and listeners
        for (Component c : pButtons.getComponents()) {
            if (c instanceof AbstractButton ab) {
                ab.addActionListener(e -> {
                    refreshPlayersForm.run();
                    rebuildPawnListeners.run();
                });
            }
        }

        cards.show(cardRoot, "SETTINGS");

        dlg.pack();
        dlg.setMinimumSize(new Dimension(560, 380));
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);

        validateTimer.stop();

        if (!decided[0]) return new ArrayList<>();
        return result;
    }

    private static void buildPlayersForm(int nPlayers,
                                         List<JTextField> nameFields,
                                         List<JButton> pawnButtons,
                                         Map<Integer, String> pawnByPlayer) {

        nameFields.clear();
        pawnButtons.clear();
        pawnByPlayer.clear();

        // seed: unique random pawns by default
        List<String> keys = new ArrayList<>(Arrays.asList(PAWN_KEYS));
        Collections.shuffle(keys);

        for (int i = 0; i < nPlayers; i++) {
            nameFields.add(new JTextField("Player " + (i + 1), 16));

            JButton pb = new JButton("Choose pawn");
            pb.setFocusPainted(false);

            String defPawn = keys.get(i % keys.size());
            pawnByPlayer.put(i, defPawn);
            updatePawnButtonVisual(pb, defPawn);

            pawnButtons.add(pb);
        }
    }

    // ------------------------------------------------------------
    // Pawn chooser (icons), disallow duplicates
    // ------------------------------------------------------------
    private static ImageIcon loadPawnIcon(String key, int size) {
        try {
            BufferedImage img = ImageIO.read(new File("sfx/pawn_" + key + ".png"));
            Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    private static void updatePawnButtonVisual(JButton b, String key) {
        b.setText(key);
        b.setIcon(loadPawnIcon(key, 26));
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setVerticalTextPosition(SwingConstants.CENTER);
    }

    private static String choosePawnDialog(Window parent, String title, int playerIndex, Map<Integer, String> pawnByPlayer) {
        Set<String> taken = new HashSet<>();
        for (Map.Entry<Integer, String> e : pawnByPlayer.entrySet()) {
            if (e.getKey() == playerIndex) continue;
            if (e.getValue() != null) taken.add(e.getValue());
        }

        final String[] picked = { null };

        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (String key : PAWN_KEYS) {
            JButton b = new JButton(key, loadPawnIcon(key, 64));
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);

            boolean disabled = taken.contains(key);
            b.setEnabled(!disabled);
            if (disabled) b.setToolTipText("Already chosen by another player");

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

    // ------------------------------------------------------------
    // Flag button
    // ------------------------------------------------------------
    private static class FlagButton extends JToggleButton {
        final Lang lang;

        FlagButton(String imgPath, Lang lang) {
            this.lang = lang;
            setFocusPainted(false);
            setPreferredSize(new Dimension(96, 56));
            setMargin(new Insets(0,0,0,0));
            setText(null);

            ImageIcon ic = loadIcon(imgPath, 96, 56);
            if (ic != null) setIcon(ic);

            setBorder(BorderFactory.createLineBorder(new Color(120, 120, 130), 1));
        }

        static Lang readSelection(ButtonGroup g) {
            for (Enumeration<AbstractButton> e = g.getElements(); e.hasMoreElements();) {
                AbstractButton b = e.nextElement();
                if (b instanceof FlagButton fb && fb.isSelected()) return fb.lang;
            }
            return Lang.EN;
        }
    }

    private static ImageIcon loadIcon(String path, int w, int h) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            BufferedImage img = ImageIO.read(f);
            Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception ignored) {}
        return null;
    }

    // ------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------
    private static boolean validatePlayersForm(int nPlayers, String startCashTxt,
                                              List<JTextField> nameFields,
                                              Map<Integer, String> pawnByPlayer) {
        if (nPlayers < 2 || nPlayers > 7) return false;

        long sc;
        try { sc = Long.parseLong(cleanNum(startCashTxt)); }
        catch (Exception ex) { return false; }
        if (sc < 0) return false;

        if (nameFields.size() != nPlayers) return false;
        if (pawnByPlayer.size() != nPlayers) return false;

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < nPlayers; i++) {
            String nm = nameFields.get(i).getText();
            if (nm == null || nm.trim().isEmpty()) return false;

            String pk = pawnByPlayer.get(i);
            if (pk == null || pk.isBlank()) return false;
            if (!seen.add(pk)) return false;
        }

        return true;
    }

    private static String cleanNum(String s) {
        if (s == null) return "0";
        return s.trim().replace("_", "").replace(",", "").replace(" ", "");
    }

    private static int safeInt(Object v, int def) {
        try {
            if (v instanceof Integer i) return i;
            if (v instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return def;
    }

    private static long safeLong(String s, long def) {
        try { return Long.parseLong(cleanNum(s)); }
        catch (Exception ignored) { return def; }
    }

    private static String safeFieldText(JTextField tf, String fallback) {
        if (tf == null) return fallback;
        String t = tf.getText();
        if (t == null || t.trim().isEmpty()) return fallback;
        return t.trim();
    }

    private static int timeLimitIndex(int minutes) {
        return switch (minutes) {
            case 30 -> 1;
            case 60 -> 2;
            case 90 -> 3;
            case 120 -> 4;
            case 150 -> 5;
            case 180 -> 6;
            default -> 0;
        };
    }

    private static int timeLimitFromIndex(int idx) {
        return switch (idx) {
            case 1 -> 30;
            case 2 -> 60;
            case 3 -> 90;
            case 4 -> 120;
            case 5 -> 150;
            case 6 -> 180;
            default -> 0;
        };
    }
}

package investor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.List;

public class Investor {

    // Intro GIF
    private static final String INTRO_GIF_PATH = "sfx/intro.gif";
    private static final int INTRO_DURATION_MS = 6500;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> showIntroGifThen(Investor::launchGame));
    }

    // ------------------------------------------------------------
    // Intro (GIF) inside the app
    // ------------------------------------------------------------
    private static void showIntroGifThen(Runnable onDone) {
        File gif = new File(INTRO_GIF_PATH);
        if (!gif.exists()) {
            onDone.run();
            return;
        }

        final JWindow splash = new JWindow();
        splash.setAlwaysOnTop(true);
        splash.getContentPane().setBackground(Color.BLACK);

        JLabel lbl = new JLabel(new ImageIcon(gif.getPath()));
        lbl.setOpaque(true);
        lbl.setBackground(Color.BLACK);

        splash.getContentPane().setLayout(new BorderLayout());
        splash.getContentPane().add(lbl, BorderLayout.CENTER);

        splash.pack();
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        // click = skip
        lbl.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                splash.setVisible(false);
                splash.dispose();
                onDone.run();
            }
        });

        // auto-close after duration
        javax.swing.Timer close = new javax.swing.Timer(INTRO_DURATION_MS, e -> {
            splash.setVisible(false);
            splash.dispose();
            onDone.run();
        });
        close.setRepeats(false);
        close.start();
    }

    // ------------------------------------------------------------
    // Game launch
    // ------------------------------------------------------------
    private static void launchGame() {
        JFrame frame = new JFrame("Investor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 780);
        frame.setLocationRelativeTo(null);

        GameState state = new GameState();

        // defaults (safe fallback)
        state.cfg.startCash = 1_800;

        // Startup UI
        List<StartupUI.PlayerInit> inits = StartupUI.showStartupDialog(frame);
        if (inits == null || inits.isEmpty()) {
            frame.dispose();
            return;
        }

        // IMPORTANT: commit selected start cash from StartupUI
        state.cfg.startCash = StartupUI.getSelectedStartCash();

        state.players.clear();
        for (StartupUI.PlayerInit pi : inits) {
            String nm = (pi.name == null || pi.name.isBlank()) ? "Player" : pi.name.trim();
            String pawnKey = (pi.pawnKey == null || pi.pawnKey.isBlank()) ? "blue" : pi.pawnKey;

            // GOLD EI KOSKAAN alussa
            if ("gold".equalsIgnoreCase(pawnKey)) pawnKey = "blue";

            Color pawnColor = Player.pawnColor(pawnKey);

            // matchaa sun Player-konstruktoria
            Player p = new Player(nm, pawnColor, state.cfg.startCash);

            p.pawnKeyChosen = pawnKey;
            p.pawnKeyCurrent = pawnKey;

            state.players.add(p);
        }

        BoardFactory.buildBoard(state);

        state.boardPanel = new BoardPanel(state);
        JPanel overlayHud = UiHud.buildOverlayHud(state);

        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);
        layered.add(state.boardPanel, JLayeredPane.DEFAULT_LAYER);
        layered.add(overlayHud, JLayeredPane.PALETTE_LAYER);

        frame.setContentPane(layered);

        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int w = frame.getContentPane().getWidth();
                int h = frame.getContentPane().getHeight();

                state.boardPanel.setBounds(0, 0, w, h);
                overlayHud.setBounds(0, 0, w, h);

                UiHud.positionOverlay(state);
                state.boardPanel.repaint();
            }
        });

        frame.setVisible(true);

        SwingUtilities.invokeLater(() -> {
            int w = frame.getContentPane().getWidth();
            int h = frame.getContentPane().getHeight();

            state.boardPanel.setBounds(0, 0, w, h);
            overlayHud.setBounds(0, 0, w, h);

            UiHud.positionOverlay(state);
            UiHud.refreshAll(state);
            state.boardPanel.requestFocusInWindow();
        });

        if (state.soundOn) {
            Audio.startBgmRandom(state, 0.25f);
        }
    }
}

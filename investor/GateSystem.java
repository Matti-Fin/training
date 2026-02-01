package investor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GateSystem {

    // marker for drawing
    public static class GateMarker {
        public final int gx;
        public final int gy;
        public final Property targetProperty;

        public GateMarker(int gx, int gy, Property targetProperty) {
            this.gx = gx;
            this.gy = gy;
            this.targetProperty = targetProperty;
        }
    }

    // gate slots per property (tweak later if you want)
    private static final int MAX_GATES_PER_PROPERTY = 2;

    // first 50, then +10 per total gates in the whole game
    public static int currentGatePrice(GameState state) {
        int bought = (state == null) ? 0 : state.gatesBought;
        return 50 + (bought * 10);
    }

    // ============================================================
    // Offer gates on TAX/SOCIALS square: icon grid + direct select
    // ============================================================
    public static void offerGateFromTaxSocials(GameState state, Player pl) {
        if (state == null || pl == null) return;

        List<Property> owned = (pl.owned != null) ? pl.owned : new ArrayList<>();
        if (owned.isEmpty()) {
            showInfo(state, "You don't own any businesses.");
            return;
        }

        // Only gate targets: owned businesses
        Property chosen = showGateTargetChooser(state, owned);
        if (chosen == null) return;

        // If full -> just ignore (chooser already disables)
        if (isGateSlotsFull(state, chosen)) {
            showInfo(state, "No gate slots available for that business.");
            return;
        }

        int price = currentGatePrice(state);
        if (pl.cash < price) {
            showInfo(state, "Not enough cash for gate ($" + price + ").");
            return;
        }

        // No extra confirmation (per spec)
        pl.cash -= price;
        state.gatesBought++;

        // enter placement mode
        state.isPlacingGate = true;
        state.pendingGateFor = chosen;
        state.pendingGateBuyer = pl;

        // persistent popup (no 15s auto close)
        if (state.boardPanel != null) {
            state.boardPanel.showPopup("Place gate", "Click the square where you want to place the gate.", false, 0);
        }
        showInfo(state, "Click the highlighted square to place the gate.");
        if (state.boardPanel != null) state.boardPanel.repaint();
    }

    // ============================================================
    // Placement: only eligible squares are allowed
    // ============================================================
    public static void placeGateNearestEligibleSquare(GameState state, int clickGX, int clickGY) {
        if (state == null || !state.isPlacingGate || state.pendingGateFor == null) return;
        if (state.path == null || state.path.isEmpty()) return;

        List<Square> elig = getEligibleSquaresForProperty(state, state.pendingGateFor);
        if (elig.isEmpty()) {
            showInfo(state, "No eligible squares for this gate target.");
            return;
        }

        int bestGX = -1;
        int bestGY = -1;
        long bestD = Long.MAX_VALUE;

        for (Square s : elig) {
            long dx = (long) s.gx - clickGX;
            long dy = (long) s.gy - clickGY;
            long d2 = dx * dx + dy * dy;
            if (d2 < bestD) {
                bestD = d2;
                bestGX = s.gx;
                bestGY = s.gy;
            }
        }

        if (bestGX < 0) return;

        // save marker
        state.gates.add(new GateMarker(bestGX, bestGY, state.pendingGateFor));

        // exit placement mode
        state.isPlacingGate = false;
        state.pendingGateFor = null;
        state.pendingGateBuyer = null;

        showInfo(state, "Gate placed.");
        if (state.boardPanel != null) state.boardPanel.repaint();
    }

    // ============================================================
    // Eligibility helpers (used by BoardPanel highlight too)
    // ============================================================
    public static List<Square> getEligibleSquaresForProperty(GameState state, Property pr) {
        List<Square> out = new ArrayList<>();
        if (state == null || pr == null) return out;
        if (state.path == null || state.path.isEmpty()) return out;

        // If slots full, highlight nothing (prevents placing)
        if (isGateSlotsFull(state, pr)) return out;

        for (Square s : state.path) {
            if (s == null) continue;

            boolean matches =
                    (s.property == pr) ||
                    (s.insideProperty == pr) ||
                    (s.outsideProperty == pr);

            if (!matches) continue;

            // don't allow placing multiple gates on exact same square for same property
            if (hasGateMarkerAt(state, s.gx, s.gy, pr)) continue;

            out.add(s);
        }

        return out;
    }

    private static boolean hasGateMarkerAt(GameState state, int gx, int gy, Property pr) {
        if (state == null || state.gates == null) return false;
        for (GateMarker gm : state.gates) {
            if (gm == null) continue;
            if (gm.gx == gx && gm.gy == gy && gm.targetProperty == pr) return true;
        }
        return false;
    }

    private static int countGatesForProperty(GameState state, Property pr) {
        if (state == null || pr == null || state.gates == null) return 0;
        int c = 0;
        for (GateMarker gm : state.gates) {
            if (gm == null) continue;
            if (gm.targetProperty == pr) c++;
        }
        return c;
    }

    private static boolean isGateSlotsFull(GameState state, Property pr) {
        int c = countGatesForProperty(state, pr);
        return c >= MAX_GATES_PER_PROPERTY;
    }

    // ============================================================
    // UI chooser: icon grid
    // ============================================================
    private static Property showGateTargetChooser(GameState state, List<Property> owned) {
        // Layout rules (max 11): 3 cols, then 3x2, 3x3, then 4x3
        int n = Math.min(owned.size(), 11);
        int cols = 3;
        int rows = 1;

        if (n <= 3) { cols = 3; rows = 1; }
        else if (n <= 6) { cols = 3; rows = 2; }
        else if (n <= 9) { cols = 3; rows = 3; }
        else { cols = 4; rows = 3; }

        JPanel grid = new JPanel(new GridLayout(rows, cols, 10, 10));
        grid.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        final Property[] chosen = { null };

        for (int i = 0; i < rows * cols; i++) {
            if (i >= n) {
                grid.add(new JLabel()); // filler
                continue;
            }

            Property pr = owned.get(i);
            if (pr == null) {
                grid.add(new JLabel());
                continue;
            }

            boolean full = isGateSlotsFull(state, pr);
            String txt = pr.name;

            JButton b = new JButton("<html><center>" + esc(txt) + "<br>"
                    + (full ? "<span style='color:#A00;'>No gate slots available</span>" : "")
                    + "</center></html>");
            b.setFocusPainted(false);
            b.setEnabled(!full);

            b.addActionListener(e -> chosen[0] = pr);
            grid.add(b);
        }

        // No dropdown, no confirm per-business, but still need a dialog with Cancel
        int res = JOptionPane.showConfirmDialog(
                null,
                new Object[] {
                        "Choose the business you want to buy the gate for:",
                        grid
                },
                "Customer gate",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (res != JOptionPane.OK_OPTION) return null;
        return chosen[0];
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static void showInfo(GameState state, String msg) {
        if (state != null && state.infoLabel != null) state.infoLabel.setText(msg);
    }
}

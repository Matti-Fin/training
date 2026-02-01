package investor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

public class BoardPanel extends JPanel implements MouseListener {

    private final GameState state;

    private BufferedImage backgroundImage;
    private final Map<String, BufferedImage> pawnImages = new HashMap<>();
    private BufferedImage gateImage;

    private boolean showGridOverlay = false;
    private boolean backgroundContain = true;

    private static final int PADDING = 24;

    // Full-image grid resolution (must match your mapping clicks)
    private int gridCols = 960;
    private int gridRows = 544;

    private Rectangle bgRect = new Rectangle(0, 0, 1, 1);

    // ============================================================
    // Token anchor shift (your “5 squares up” rule)
    // ============================================================
    private static final int TOKEN_ANCHOR_SHIFT_GY = 5;

    // Jail square anchor (given by you):
    // CLICK grid=OO171 (gx=404 gy=170)
    private static final int JAIL_GX = 404;
    private static final int JAIL_GY = 170;

    // ============================================================
    // Popup (center message card)
    // ============================================================
    private boolean popupVisible = false;
    private String popupTitle = "";
    private String popupBody = "";
    private long popupShownAtMs = 0L;

    // IMPORTANT: explicitly Swing Timer (avoids java.util.Timer ambiguity)
    private javax.swing.Timer popupAutoHideTimer;

    // Simple top-right history (keeps popup messages)
    private final Deque<String> history = new ArrayDeque<>();
    private static final int HISTORY_MAX_LINES = 12;

    public BoardPanel(GameState state) {
        this.state = state;
        setBackground(new Color(245, 248, 252));
        addMouseListener(this);
        loadBackground();
        loadGateImage();
    }

    // ============================================================
    // Public API: show popup from anywhere (Finance/TurnLogic/etc)
    // ============================================================
    public void showPopup(String title, String body) {
        // default: auto-hide 15s
        showPopup(title, body, true, 15_000);
    }

    public void showPopup(String title, String body, boolean autoHide, int autoHideMs) {
        if (title == null) title = "";
        if (body == null) body = "";

        this.popupTitle = title.trim();
        this.popupBody = body.trim();
        this.popupVisible = true;
        this.popupShownAtMs = System.currentTimeMillis();

        pushHistoryLine((this.popupTitle.isEmpty() ? "" : (this.popupTitle + " — ")) + this.popupBody);

        if (popupAutoHideTimer != null) {
            popupAutoHideTimer.stop();
            popupAutoHideTimer = null;
        }

        if (autoHide) {
            int ms = Math.max(300, autoHideMs);
            popupAutoHideTimer = new javax.swing.Timer(ms, e -> hidePopup());
            popupAutoHideTimer.setRepeats(false);
            popupAutoHideTimer.start();
        }

        repaint();
    }

    public void hidePopup() {
        popupVisible = false;
        if (popupAutoHideTimer != null) {
            popupAutoHideTimer.stop();
            popupAutoHideTimer = null;
        }
        repaint();
    }

    private void pushHistoryLine(String text) {
        String t = (text == null) ? "" : text.trim();
        if (t.isEmpty()) return;

        if (t.length() > 160) t = t.substring(0, 157) + "...";

        history.addFirst(t);
        while (history.size() > HISTORY_MAX_LINES) history.removeLast();
    }

    private void loadBackground() {
        String[] candidates = {
                "sfx/board_bg.png",
                "sfx/board_bg.jpg",
                "sfx/board_bg.jpeg"
        };

        for (String path : candidates) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    backgroundImage = ImageIO.read(f);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private void loadGateImage() {
        try {
            File f = new File("sfx/gate.png");
            if (f.exists()) {
                gateImage = ImageIO.read(f);
            }
        } catch (Exception ignored) {}
    }

    private Rectangle getAvailRect() {
        int availW = Math.max(20, getWidth() - (PADDING * 2));
        int availH = Math.max(20, getHeight() - (PADDING * 2));
        return new Rectangle(PADDING, PADDING, availW, availH);
    }

    private Rectangle computeBackgroundRect() {
        Rectangle ar = getAvailRect();

        if (backgroundImage == null) {
            return new Rectangle(ar.x, ar.y, ar.width, ar.height);
        }

        int imgW = backgroundImage.getWidth();
        int imgH = backgroundImage.getHeight();
        if (imgW <= 0 || imgH <= 0) {
            return new Rectangle(ar.x, ar.y, ar.width, ar.height);
        }

        double scale = backgroundContain
                ? Math.min((double) ar.width / imgW, (double) ar.height / imgH)
                : Math.max((double) ar.width / imgW, (double) ar.height / imgH);

        int drawW = (int) Math.round(imgW * scale);
        int drawH = (int) Math.round(imgH * scale);

        int dx = ar.x + (ar.width  - drawW) / 2;
        int dy = ar.y + (ar.height - drawH) / 2;

        return new Rectangle(dx, dy, drawW, drawH);
    }

    private String toExcelCol(int idx) {
        StringBuilder sb = new StringBuilder();
        int n = idx;
        while (n >= 0) {
            int r = n % 26;
            sb.append((char) ('A' + r));
            n = (n / 26) - 1;
        }
        return sb.reverse().toString();
    }

    private String gridCoordString(int gx, int gy) {
        return toExcelCol(gx) + (gy + 1);
    }

    private void drawFullGrid(Graphics2D g2) {
        int cols = Math.max(1, gridCols);
        int rows = Math.max(1, gridRows);

        double cellW = (double) bgRect.width / cols;
        double cellH = (double) bgRect.height / rows;

        g2.setColor(new Color(40, 40, 50, 110));

        for (int c = 0; c <= cols; c++) {
            int x = (int) Math.round(bgRect.x + c * cellW);
            g2.drawLine(x, bgRect.y, x, bgRect.y + bgRect.height);
        }
        for (int r = 0; r <= rows; r++) {
            int y = (int) Math.round(bgRect.y + r * cellH);
            g2.drawLine(bgRect.x, y, bgRect.x + bgRect.width, y);
        }

        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        g2.setColor(new Color(20, 20, 25, 170));

        int stepX = Math.max(1, cols / 16);
        int stepY = Math.max(1, rows / 10);

        for (int c = 0; c < cols; c += stepX) {
            int x = (int) Math.round(bgRect.x + c * cellW) + 3;
            g2.drawString(toExcelCol(c), x, bgRect.y + 14);
        }
        for (int r = 0; r < rows; r += stepY) {
            int y = (int) Math.round(bgRect.y + r * cellH) + 14;
            g2.drawString("" + (r + 1), bgRect.x + 3, y);
        }
    }

    private int pxFromGX(int gx) {
        double cellW = (double) bgRect.width / Math.max(1, gridCols);
        return (int) Math.round(bgRect.x + (gx + 0.5) * cellW);
    }

    private int pyFromGY(int gy) {
        double cellH = (double) bgRect.height / Math.max(1, gridRows);
        return (int) Math.round(bgRect.y + (gy + 0.5) * cellH);
    }

    private BufferedImage loadPawnImage(String key) {
        if (key == null) return null;
        key = key.trim().toLowerCase();
        if (key.isEmpty()) return null;

        if (pawnImages.containsKey(key)) return pawnImages.get(key);

        String keyNo = key.replace("_", "").replace("-", "").replace(" ", "");

        String[] candidates = {
                "sfx/pawn_" + key + ".png",
                "sfx/pawn_" + keyNo + ".png",

                "sfx/pawn_npc_" + key + ".png",
                "sfx/pawn_npc_" + keyNo + ".png"
        };

        for (String path : candidates) {
            try {
                File f = new File(path);
                if (f.exists()) {
                    BufferedImage img = ImageIO.read(f);
                    pawnImages.put(key, img);
                    return img;
                }
            } catch (Exception ignored) {}
        }

        pawnImages.put(key, null);
        return null;
    }

    private void drawPawn(Graphics2D g2, int cx, int cy, int size, String pawnKey, Color fallbackColor, boolean isCurrent) {
        BufferedImage pawn = loadPawnImage(pawnKey);

        if (pawn != null) {
            int w = size;
            int h = size;
            int x = cx - w / 2;
            int y = cy - h / 2;

            g2.drawImage(pawn, x, y, w, h, null);

            if (isCurrent) {
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(x, y, w, h);
                g2.setStroke(new BasicStroke(1f));
            }
            return;
        }

        g2.setColor(fallbackColor != null ? fallbackColor : Color.BLUE);
        g2.fillOval(cx - size / 2, cy - size / 2, size, size);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx - size / 2, cy - size / 2, size, size);
    }

    private void drawPawn(Graphics2D g2, int cx, int cy, int size, Player pl, boolean isCurrent) {
        drawPawn(g2, cx, cy, size, pl.pawnKeyCurrent, pl.color, isCurrent);
    }

    private void drawGates(Graphics2D g2) {
        if (state == null || state.gates == null || state.gates.isEmpty()) return;
        if (gateImage == null) return;

        int sizePx = 26;

        for (GateSystem.GateMarker gm : state.gates) {
            if (gm == null) continue;

            int gx = gm.gx;
            int gy = gm.gy + 3;

            int cx = pxFromGX(gx);
            int cy = pyFromGY(gy);

            int x = cx - sizePx / 2;
            int y = cy - sizePx / 2;

            g2.drawImage(gateImage, x, y, sizePx, sizePx, null);
        }
    }

    // ============================================================
    // Gate placement highlight
    // ============================================================
    private void drawGatePlacementHighlights(Graphics2D g2) {
        if (state == null) return;
        if (!state.isPlacingGate) return;
        if (state.pendingGateFor == null) return;
        if (state.path == null || state.path.isEmpty()) return;

        // Find eligible squares for this property
        List<Square> elig = GateSystem.getEligibleSquaresForProperty(state, state.pendingGateFor);
        if (elig.isEmpty()) return;

        // draw glow circles around eligible squares
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Square sq : elig) {
            int cx = pxFromGX(sq.gx);
            int cy = pyFromGY(sq.gy - TOKEN_ANCHOR_SHIFT_GY);

            int r1 = 26;
            int r2 = 18;

            g2.setColor(new Color(255, 220, 0, 90));
            g2.fillOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

            g2.setColor(new Color(255, 80, 0, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private Point tokenOffset(int idx, int step) {
        if (idx == 0) return new Point(0, 0);

        Point[] ring = {
                new Point(step, 0),
                new Point(-step, 0),
                new Point(0, step),
                new Point(0, -step),
                new Point(step, step),
                new Point(step, -step),
                new Point(-step, step),
                new Point(-step, -step)
        };

        int i = (idx - 1) % ring.length;
        int mul = 1 + (idx - 1) / ring.length;
        return new Point(ring[i].x * mul, ring[i].y * mul);
    }

    private void drawTokens(Graphics2D g2) {
        if (state == null || state.path == null) return;

        // +20% size
        int pawnSize = 70;
        int offsetStep = 31;

        Map<Integer, List<Token>> byPos = new HashMap<>();

        if (state.players != null) {
            for (int i = 0; i < state.players.size(); i++) {
                Player p = state.players.get(i);
                if (p == null) continue;
                byPos.computeIfAbsent(p.posId, k -> new ArrayList<>()).add(Token.player(i));
            }
        }

        if (state.policePosId >= 0) {
            byPos.computeIfAbsent(state.policePosId, k -> new ArrayList<>()).add(Token.police());
        }
        if (state.taxAuditPosId >= 0) {
            byPos.computeIfAbsent(state.taxAuditPosId, k -> new ArrayList<>()).add(Token.taxAudit());
        }

        for (Map.Entry<Integer, List<Token>> e : byPos.entrySet()) {
            int sid = e.getKey();
            if (sid < 0 || sid >= state.path.size()) continue;

            Square sq = state.path.get(sid);
            if (sq == null) continue;

            // IMPORTANT: move token anchor 5 grid cells UP
            int baseCX = pxFromGX(sq.gx);
            int baseCY = pyFromGY(sq.gy - TOKEN_ANCHOR_SHIFT_GY);

            List<Token> tokens = e.getValue();
            tokens.sort(Comparator.comparingInt(t -> t.kindOrder));

            for (int k = 0; k < tokens.size(); k++) {
                Token t = tokens.get(k);
                Point off = tokenOffset(k, offsetStep);

                if (t.type == TokenType.PLAYER) {
                    Player pl = state.players.get(t.playerIndex);
                    drawPawn(g2, baseCX + off.x, baseCY + off.y, pawnSize, pl, t.playerIndex == state.currentPlayerIndex);
                } else if (t.type == TokenType.POLICE) {
                    drawPawn(g2, baseCX + off.x, baseCY + off.y, pawnSize,
                            state.policePawnKey, new Color(30, 60, 180), false);
                } else if (t.type == TokenType.TAX_AUDIT) {
                    drawPawn(g2, baseCX + off.x, baseCY + off.y, pawnSize,
                            state.taxAuditPawnKey, new Color(30, 150, 80), false);
                }
            }
        }
    }

    private enum TokenType { PLAYER, POLICE, TAX_AUDIT }

    private static class Token {
        final TokenType type;
        final int playerIndex;
        final int kindOrder;

        private Token(TokenType type, int playerIndex, int kindOrder) {
            this.type = type;
            this.playerIndex = playerIndex;
            this.kindOrder = kindOrder;
        }

        static Token player(int idx) { return new Token(TokenType.PLAYER, idx, 0); }
        static Token police() { return new Token(TokenType.POLICE, -1, 1); }
        static Token taxAudit() { return new Token(TokenType.TAX_AUDIT, -1, 2); }
    }

    private Square findTaxSocialsSquare() {
        if (state == null || state.path == null) return null;
        for (Square s : state.path) {
            if (s == null || s.label == null) continue;
            if ("TAX / SOCIALS".equalsIgnoreCase(s.label.trim())) return s;
        }
        return null;
    }

    private Rectangle computePopupRect() {
        Rectangle safe = new Rectangle(
                bgRect.x + (int) Math.round(bgRect.width * 0.20),
                bgRect.y + (int) Math.round(bgRect.height * 0.25),
                (int) Math.round(bgRect.width * 0.60),
                (int) Math.round(bgRect.height * 0.40)
        );

        if (bgRect.width < 50 || bgRect.height < 50) return safe;

        // jailY uses the same -shift as tokens, so card sits "under jail square" visually
        int jailY = pyFromGY(JAIL_GY - TOKEN_ANCHOR_SHIFT_GY);

        Square tax = findTaxSocialsSquare();
        int socialsY;
        if (tax != null) socialsY = pyFromGY(tax.gy - TOKEN_ANCHOR_SHIFT_GY);
        else socialsY = bgRect.y + bgRect.height - 40;

        int marginPx = 20;

        int top = jailY + marginPx;
        int bottom = socialsY - marginPx;

        top = Math.max(top, bgRect.y + 10);
        bottom = Math.min(bottom, bgRect.y + bgRect.height - 10);

        int minH = 140;
        if (bottom - top < minH) return safe;

        int height = bottom - top;

        int maxWidth = (int) Math.round(bgRect.width * 0.70);
        int width = Math.min(height, maxWidth);
        width = Math.max(260, Math.min(width, bgRect.width - 20));

        int x = bgRect.x + (bgRect.width - width) / 2;
        int y = top;

        return new Rectangle(x, y, width, height);
    }

    private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;

        String t = text.trim();
        if (t.isEmpty()) return lines;

        String[] words = t.split("\\s+");
        StringBuilder cur = new StringBuilder();

        for (String w : words) {
            if (cur.length() == 0) {
                cur.append(w);
                continue;
            }
            String trial = cur + " " + w;
            if (fm.stringWidth(trial) <= maxWidth) {
                cur.append(" ").append(w);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void drawPopup(Graphics2D g2) {
        if (!popupVisible) return;

        Rectangle r = computePopupRect();

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(r.x + 4, r.y + 6, r.width, r.height, 18, 18);

        g2.setColor(new Color(255, 255, 255, 235));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);

        g2.setColor(new Color(30, 30, 30, 120));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
        g2.setStroke(new BasicStroke(1f));

        int pad = 18;
        int innerX = r.x + pad;
        int innerY = r.y + pad;
        int innerW = r.width - pad * 2;

        Font titleFont = new Font("SansSerif", Font.BOLD, 18);
        g2.setFont(titleFont);
        FontMetrics tfm = g2.getFontMetrics();
        g2.setColor(new Color(10, 10, 10, 230));

        String title = (popupTitle == null ? "" : popupTitle.trim());
        if (title.isEmpty()) title = "NOTICE";

        g2.drawString(title, innerX, innerY + tfm.getAscent());
        int y = innerY + tfm.getHeight() + 10;

        Font bodyFont = new Font("SansSerif", Font.PLAIN, 15);
        g2.setFont(bodyFont);
        FontMetrics bfm = g2.getFontMetrics();

        String body = (popupBody == null) ? "" : popupBody.trim();
        List<String> lines = wrapText(body, bfm, innerW);

        g2.setColor(new Color(20, 20, 20, 210));
        int lineH = bfm.getHeight();

        for (String ln : lines) {
            if (y + bfm.getAscent() > r.y + r.height - 40) break;
            g2.drawString(ln, innerX, y + bfm.getAscent());
            y += lineH;
        }

        g2.setFont(new Font("SansSerif", Font.ITALIC, 12));
        g2.setColor(new Color(30, 30, 30, 140));

        // NOTE: gate popup is persistent now (no auto-close), so don't promise it.
        String hint = "Click anywhere to close";
        g2.drawString(hint, innerX, r.y + r.height - 16);
    }

    private void drawHistoryTopRight(Graphics2D g2) {
        if (history.isEmpty()) return;

        int pad = 10;
        int boxPad = 10;

        Font f = new Font("SansSerif", Font.PLAIN, 12);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();

        int maxW = 0;
        int lines = 0;
        for (String s : history) {
            if (s == null) continue;
            maxW = Math.max(maxW, fm.stringWidth(s));
            lines++;
            if (lines >= HISTORY_MAX_LINES) break;
        }

        int lineH = fm.getHeight();
        int boxW = Math.min(maxW + boxPad * 2, (int) (bgRect.width * 0.45));
        int boxH = Math.min(lines * lineH + boxPad * 2, (int) (bgRect.height * 0.35));

        int x = bgRect.x + bgRect.width - boxW - pad;
        int y = bgRect.y + pad;

        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(x + 2, y + 3, boxW, boxH, 14, 14);
        g2.setColor(new Color(255, 255, 255, 210));
        g2.fillRoundRect(x, y, boxW, boxH, 14, 14);

        g2.setColor(new Color(20, 20, 20, 130));
        g2.drawRoundRect(x, y, boxW, boxH, 14, 14);

        g2.setColor(new Color(20, 20, 20, 200));
        int ty = y + boxPad + fm.getAscent();
        int count = 0;
        for (String s : history) {
            if (count >= HISTORY_MAX_LINES) break;
            if (ty > y + boxH - boxPad) break;
            g2.drawString("• " + s, x + boxPad, ty);
            ty += lineH;
            count++;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        bgRect = computeBackgroundRect();

        if (backgroundImage != null) {
            g2.drawImage(backgroundImage, bgRect.x, bgRect.y, bgRect.width, bgRect.height, null);
        }

        if (showGridOverlay) drawFullGrid(g2);

        // Highlight BEFORE tokens, so pawns are on top
        drawGatePlacementHighlights(g2);

        drawGates(g2);
        drawTokens(g2);

        drawHistoryTopRight(g2);
        drawPopup(g2);

        g2.dispose();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // popup closes on ANY click
        if (popupVisible) {
            hidePopup();
            return;
        }

        int mx = e.getX();
        int my = e.getY();
        if (!bgRect.contains(mx, my)) return;

        int cols = Math.max(1, gridCols);
        int rows = Math.max(1, gridRows);

        double cellW = (double) bgRect.width / cols;
        double cellH = (double) bgRect.height / rows;

        int gx = (int) ((mx - bgRect.x) / cellW);
        int gy = (int) ((my - bgRect.y) / cellH);

        gx = Math.max(0, Math.min(cols - 1, gx));
        gy = Math.max(0, Math.min(rows - 1, gy));

        if (state != null && state.isPlacingGate) {
            GateSystem.placeGateNearestEligibleSquare(state, gx, gy);
            return;
        }

        if (!showGridOverlay) return;

        String coord = gridCoordString(gx, gy);
        System.out.println("CLICK grid=" + coord + " (gx=" + gx + " gy=" + gy + ")");

        if (state != null && state.infoLabel != null) {
            state.infoLabel.setText("Click: " + coord + " (gx=" + gx + ", gy=" + gy + ")");
        }
    }

    @Override public void mousePressed(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
}

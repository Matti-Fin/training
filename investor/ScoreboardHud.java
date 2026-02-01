package investor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScoreboardHud {

    // 0 = Highest net, 1 = Lowest risk margin, 2 = Risk to get caught
    private int mode = 0;

    private final JPanel panel;
    private final JLabel label;
    private final javax.swing.Timer tick;

    private final GameState state;

    private ScoreboardHud(GameState state) {
        this.state = state;

        panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(0, 0, 0, 150));
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        label = new JLabel();
        label.setForeground(Color.WHITE);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(label, BorderLayout.CENTER);

        // update immediately + rotate every 15s
        refresh();

        tick = new javax.swing.Timer(15_000, e -> {
            mode = (mode + 1) % 3;
            refresh();
        });
        tick.setRepeats(true);
        tick.start();

        // small refresh every 1s so numbers update even between turns
        new javax.swing.Timer(1000, e -> refresh()).start();
    }

    public static JComponent create(GameState state) {
        return new ScoreboardHud(state).panel;
    }

    private void refresh() {
        if (state == null || state.players == null || state.players.isEmpty()) {
            label.setText("<html><b>Standings</b><br>No players</html>");
            return;
        }

        List<Player> ps = new ArrayList<>(state.players);

        String title;
        if (mode == 0) {
            title = "Highest net";
            ps.sort(Comparator.comparingLong(Player::netWorth).reversed());
        } else if (mode == 1) {
            title = "Lowest risk margin";
            ps.sort(Comparator.comparingInt(Player::totalRiskMarginPct));
        } else {
            title = "Risk to get caught (0–2)";
            ps.sort(Comparator.comparingInt(ScoreboardHud::caughtRiskScore).reversed());
        }

        StringBuilder sb = new StringBuilder("<html><b>");
        sb.append(title).append("</b><br>");

        int show = Math.min(5, ps.size());
        for (int i = 0; i < show; i++) {
            Player p = ps.get(i);

            if (mode == 0) {
                sb.append(i+1).append(") ").append(escape(p.name))
                  .append(" — ").append(p.netWorth());
            } else if (mode == 1) {
                sb.append(i+1).append(") ").append(escape(p.name))
                  .append(" — ").append(p.totalRiskMarginPct()).append("%");
            } else {
                int r = caughtRiskScore(p);
                sb.append(i+1).append(") ").append(escape(p.name))
                  .append(" — ").append(r).append(" (").append(caughtRiskLabel(p)).append(")");
            }

            if (i < show - 1) sb.append("<br>");
        }

        sb.append("</html>");
        label.setText(sb.toString());
    }

    // 0 = none, 1 = one authority, 2 = both
    private static int caughtRiskScore(Player p) {
        int a = (p.taxEvasionOpen ? 1 : 0);
        int b = (p.welfareFraudOpen ? 1 : 0);
        int sum = a + b;
        if (sum <= 0) return 0;
        if (sum == 1) return 1;
        return 2;
    }

    private static String caughtRiskLabel(Player p) {
        boolean tax = p.taxEvasionOpen;
        boolean wel = p.welfareFraudOpen;
        if (!tax && !wel) return "None";
        if (tax && wel) return "Tax + Police";
        if (tax) return "Tax";
        return "Police";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}

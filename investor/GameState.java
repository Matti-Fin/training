package investor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class GameState {

    public final Config cfg = new Config();

    public List<Player> players = new ArrayList<>();

    public final List<Square> path = new ArrayList<>();
    public final List<Square> infoSquares = new ArrayList<>();
    public final List<Property> allProperties = new ArrayList<>();

    // --- spawn points (full-image grid coordinates) ---
    public final java.util.List<java.awt.Point> spawnPoints = new java.util.ArrayList<>();

    // ---- Placed Gates (your click-to-place markers) ----
    public final java.util.List<GateSystem.GateMarker> gates = new java.util.ArrayList<>();
    public int gatesBought = 0;

    public boolean isPlacingGate = false;
    public Property pendingGateFor = null;
    public Player pendingGateBuyer = null;

    // ---- Police + Tax Auditor board pieces (NPC tokens) ----
    public int policePosId = 33;    // square #34 by default
    public int taxAuditPosId = 32;  // square #33 by default

    // These keys are used by BoardPanel.loadPawnImage:
    // It tries sfx/pawn_npc_<key>.png among others.
    // Your files: pawn_npc_police.png and pawn_npc_taxaudit.png => keys: "police", "taxaudit"
    public String policePawnKey = "police";
    public String taxAuditPawnKey = "taxaudit";

    // direction: players go +1; NPC pieces go -1 (opposite)
    public int policeDir = -1;
    public int taxAuditDir = -1;

    // simple init guard (so you can lazy-init these when board is built)
    public boolean npcTokensInitialized = false;

    public int currentPlayerIndex = 0;
    public int turnNumber = 1;
    public int lastRoll = 0;

    // market / FED
    public double fedRate = 5.0;
    public boolean fedRateJustChanged = false;

    // turn metrics (Finance increments)
    public int _investmentsThisTurn = 0;
    public int _nightsThisTurn = 0;

    // SFX throttling
    public long _lastFootstepMs = 0;

    // Sound toggle
    public boolean soundOn = true;

    // UI references (set by UI code)
    public JButton rollBtn;
    public JLabel diceLabel;
    public JLabel infoLabel;
    public JLabel marketLabel;

    public BoardPanel boardPanel;

    // optional HUD containers (UiHud may populate)
    public JPanel hudTop;
    public JPanel playersRow;

    public boolean isBusy = false;

    // ============================================================
    // VISIT ROLL MODE (UiHud uses these)
    // ============================================================
    public boolean awaitingVisitRoll = false;

    public Player pendingVisitPlayer = null;
    public Property pendingVisitProperty = null;
    public long pendingVisitCost = 0;

    public Player currentPlayer() {
        if (players == null || players.isEmpty()) return null;
        if (currentPlayerIndex < 0) currentPlayerIndex = 0;
        if (currentPlayerIndex >= players.size()) currentPlayerIndex = currentPlayerIndex % players.size();
        return players.get(currentPlayerIndex);
    }
}

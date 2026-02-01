package investor;

import java.awt.Rectangle;

enum SquareType {
    EMPTY,
    START,
    CHECKPOINT_THIN,

    // off-path info squares
    INFO_INSIDE,   // Verottaja
    INFO_OUTSIDE,  // KELA

    LOT,
    SEAT,

    // new mechanics
    DUAL_LOT,
    GATE,

    BANK,
    EXCHANGE,
    CENTRAL_BANK,
    CASINO
}

enum EntrySide {
    NONE, LEFT, RIGHT, TOP, BOTTOM
}

class GateGroup {
    final String name;
    int ownerIndex = -1;

    GateGroup(String name) {
        this.name = name;
    }

    boolean isOwned() { return ownerIndex >= 0; }
}

class Square {
    final int id;
    final int gx;
    final int gy;

    SquareType type;
    String label;

    // scaling (for CHECKPOINT_THIN)
    double wScale = 1.0;
    double hScale = 1.0;

    // runtime drawing / hitboxes
    Rectangle pixelRect;

    // movement entry side (used by graphics / strips if needed)
    EntrySide entrySide = EntrySide.NONE;

    // legacy: single property + seat
    Property property;
    Seat seat;

    // dual-lot: inside/outside businesses
    Property insideProperty;
    Property outsideProperty;

    // gate ownership shared between 2 tiles
    GateGroup gateGroup;

    Square(int id, int gx, int gy, SquareType type, String label) {
        this.id = id;
        this.gx = gx;
        this.gy = gy;
        this.type = type;
        this.label = label;
    }

    void scale(double w, double h) {
        this.wScale = w;
        this.hScale = h;
    }
}

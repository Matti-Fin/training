package investor;

import javax.swing.Timer;
import java.lang.reflect.Field;

public class TurnLogic {

    public static void startTurn(GameState state) {
        state._investmentsThisTurn = 0;
        state._nightsThisTurn = 0;

        UiHud.logTurnStart(state);

        ensureNpcTokenInit(state);
    }

    public static void movePlayerAnimated(GameState state, int steps, Runnable onDone) {
        Player pl = state.currentPlayer();
        if (pl == null) {
            if (onDone != null) onDone.run();
            return;
        }

        // --- IMPORTANT FIX ---
        // If player is still in spawn mode, make sure they are attached to the path.
        if (pl.onSpawn) {
            pl.onSpawn = false;
            if (pl.posId < 0) pl.posId = 0;
        }

        state.isBusy = true;
        if (state.rollBtn != null) state.rollBtn.setEnabled(false);

        int sizeForLog = (state.path == null) ? 0 : state.path.size();
        if (sizeForLog > 0) {
            int fromPos = pl.posId;
            int toPos = simulateEndPos(fromPos, steps, sizeForLog);
            UiHud.logRollMove(state, pl, steps, fromPos, toPos);
        }

        final int[] left = { steps };
        final boolean[] didTaxSocialsThisMove = { false };

        Timer t = new Timer(250, null);
        t.addActionListener(e -> {

            int size = (state.path == null) ? 0 : state.path.size();
            if (size <= 0) {
                t.stop();
                state.isBusy = false;
                if (state.rollBtn != null) state.rollBtn.setEnabled(true);
                if (onDone != null) onDone.run();
                return;
            }

            if (left[0] <= 0) {
                t.stop();
                state.isBusy = false;
                if (state.rollBtn != null) state.rollBtn.setEnabled(true);

                if (pl.posId >= 0 && pl.posId < size) {
                    Square sq = state.path.get(pl.posId);

                    // Encounter checks only when player ENDS on same square
                    checkNpcEncountersOnPlayerLanding(state, pl);

                    Finance.onLand(state, sq);
                }

                if (onDone != null) onDone.run();
                return;
            }

            // ---- STEP FORWARD ----
            int prevPos = pl.posId;

            int next = prevPos + 1;
            if (next >= size) next = 0;

            pl.posId = next;
            left[0]--;

            // START settlement only when you pass START from the path (NOT from spawn)
            if (pl.posId == 0 && prevPos >= 0) {
                Economy.onPassOrLandStart(state, pl);
            }

            // TAX / SOCIALS triggers on PASSING (entering during movement), only once per move
            if (!didTaxSocialsThisMove[0] && isTaxSocialsSquare(state, pl.posId)) {
                didTaxSocialsThisMove[0] = true;
                GateSystem.offerGateFromTaxSocials(state, pl);
            }

            if (state.soundOn && state.cfg != null && state.cfg.sfxFootsteps != null) {
                long now = System.currentTimeMillis();
                if (now - state._lastFootstepMs >= 180) {
                    state._lastFootstepMs = now;
                    Audio.playExclusive("footsteps", state.cfg.sfxFootsteps, 0.35f);
                }
            }

            Square sq = state.path.get(pl.posId);
            if (sq.type == SquareType.CHECKPOINT_THIN) {
                applyCheckpointRules(state, pl);
            }

            if (state.boardPanel != null) state.boardPanel.repaint();
        });

        t.start();
    }

    private static void applyCheckpointRules(GameState state, Player pl) {
        long nw = pl.netWorth();

        if (nw < state.cfg.kelaWealthThreshold) {
            pl.cash += state.cfg.kelaSupportPerRound;
            pl.roundEarnings += state.cfg.kelaSupportPerRound;
            if (state.infoLabel != null) {
                state.infoLabel.setText("KELA: +" + state.cfg.kelaSupportPerRound
                        + " (net worth < " + state.cfg.kelaWealthThreshold + ")");
            }
        }

        if (pl.roundEarnings > 10_000) {
            long tax = Math.round(pl.roundEarnings * 0.30);
            pl.cash = Math.max(0, pl.cash - tax);
            if (state.infoLabel != null) state.infoLabel.setText("Tax Office: -" + tax + " (30% of round earnings)");
        }

        pl.roundEarnings = 0;
    }

    private static void updateGoldLeaderPawn(GameState state) {
        if (state == null || state.players == null || state.players.size() < 2) return;

        int top1 = -1, top2 = -1;
        long nw1 = Long.MIN_VALUE, nw2 = Long.MIN_VALUE;

        for (int i = 0; i < state.players.size(); i++) {
            long nw = state.players.get(i).netWorth();
            if (nw > nw1) {
                nw2 = nw1; top2 = top1;
                nw1 = nw;  top1 = i;
            } else if (nw > nw2) {
                nw2 = nw;  top2 = i;
            }
        }

        for (Player p : state.players) {
            p.pawnKeyCurrent = p.pawnKeyChosen;
            p.color = Player.pawnColor(p.pawnKeyCurrent);
        }

        if (top1 >= 0 && top2 >= 0 && nw2 > 0 && nw1 >= 2L * nw2) {
            Player leader = state.players.get(top1);
            leader.pawnKeyCurrent = "gold";
            leader.color = Player.pawnColor("gold");
        }
    }

    public static void endTurn(GameState state) {
        UiHud.logTurnEnd(state);

        for (Property p : state.allProperties) {
            p.rolloverRoundNights();
            p.onRoundTick(state);
        }

        Player pl = state.currentPlayer();
        if (pl != null) pl.casinoRiskExtraPct = Math.max(0, pl.casinoRiskExtraPct - 1);

        Market.recordTurn(state, state._investmentsThisTurn, state._nightsThisTurn);

        if (state.fedRateJustChanged && state.infoLabel != null) {
            state.infoLabel.setText(String.format("FED changed -> %.2f%%", state.fedRate));
        }

        int nextIndex = (state.currentPlayerIndex + 1) % state.players.size();
        state.currentPlayerIndex = nextIndex;
        state.turnNumber++;

        if (nextIndex == 0) {
            advanceNpcTokensAfterFullRound(state);
        }

        updateGoldLeaderPawn(state);

        UiHud.refreshTopHud(state);
        UiHud.refreshPlayersRow(state);
        if (state.boardPanel != null) state.boardPanel.repaint();
    }

    private static int simulateEndPos(int fromPosId, int steps, int size) {
        if (size <= 0) return fromPosId;

        int pos = fromPosId;
        for (int i = 0; i < steps; i++) {
            int next = pos + 1;
            if (next >= size) next = 0;
            pos = next;
        }
        return pos;
    }

    private static boolean isTaxSocialsSquare(GameState state, int posId) {
        try {
            if (state == null || state.path == null) return false;
            if (posId < 0 || posId >= state.path.size()) return false;
            Square sq = state.path.get(posId);
            if (sq == null || sq.label == null) return false;
            return "TAX / SOCIALS".equalsIgnoreCase(sq.label.trim());
        } catch (Exception ignored) {
            return false;
        }
    }

    // ==============================
    // NPC TOKENS (Police / TaxAudit)
    // ==============================
    private static void ensureNpcTokenInit(GameState state) {
        if (state == null) return;
        int size = (state.path == null) ? 0 : state.path.size();
        if (size <= 0) return;

        Integer policePos = getIntField(state, "policePosId");
        Integer auditPos  = getIntField(state, "taxAuditPosId");

        // Request: start them at squares #34 and #33
        // => posId 33 and 32
        if (policePos == null) setIntField(state, "policePosId", 30);
        if (auditPos  == null) setIntField(state, "taxAuditPosId", 28);
    }

    private static void advanceNpcTokensAfterFullRound(GameState state) {
        if (state == null) return;
        int size = (state.path == null) ? 0 : state.path.size();
        if (size <= 0) return;

        Integer policePos = getIntField(state, "policePosId");
        Integer auditPos  = getIntField(state, "taxAuditPosId");

        if (policePos != null) {
            int next = policePos - 1;
            if (next < 0) next = size - 1;
            setIntField(state, "policePosId", next);
            checkNpcLandingOnAnyPlayer(state, "POLICE", next);
        }

        if (auditPos != null) {
            int next = auditPos - 1;
            if (next < 0) next = size - 1;
            setIntField(state, "taxAuditPosId", next);
            checkNpcLandingOnAnyPlayer(state, "TAX AUDIT", next);
        }
    }

    private static void checkNpcEncountersOnPlayerLanding(GameState state, Player pl) {
        if (state == null || pl == null) return;

        Integer policePos = getIntField(state, "policePosId");
        Integer auditPos  = getIntField(state, "taxAuditPosId");

        if (policePos != null && pl.posId == policePos) {
            if (state.infoLabel != null) state.infoLabel.setText("POLICE encounter (landing).");
        }

        if (auditPos != null && pl.posId == auditPos) {
            if (state.infoLabel != null) state.infoLabel.setText("TAX AUDIT encounter (landing).");
        }
    }

    private static void checkNpcLandingOnAnyPlayer(GameState state, String who, int npcPosId) {
        if (state == null || state.players == null) return;
        for (Player p : state.players) {
            if (p != null && p.posId == npcPosId) {
                if (state.infoLabel != null) state.infoLabel.setText(who + " encounter (NPC landed on player).");
            }
        }
    }

    // ==============================
    // Reflection helpers
    // ==============================
    private static Integer getIntField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception ignored) { }
        return null;
    }

    private static void setIntField(Object obj, String fieldName, int value) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            if (f.getType() == int.class) f.setInt(obj, value);
            else if (f.getType() == Integer.class) f.set(obj, value);
            else if (Number.class.isAssignableFrom(f.getType())) f.set(obj, value);
        } catch (Exception ignored) { }
    }
}

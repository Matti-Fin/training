package investor;

import javax.swing.*;
import java.util.Random;
import java.util.function.Consumer;

public class Dice {

    private static final Random rnd = new Random();

    public static void rollDiceAnimated(GameState state, Consumer<Integer> onDone) {
        if (state == null || onDone == null) return;

        // play one of two dice sounds randomly
        if (state.cfg != null) {
            String pick = (rnd.nextBoolean() ? state.cfg.sfxDice1 : state.cfg.sfxDice2);
            Audio.play(pick, 0.85f);
        }

        // simple animation: show random faces for ~0.7s, then finalize
        final int[] ticks = { 0 };
        Timer t = new Timer(70, null);
        t.addActionListener(e -> {
            ticks[0]++;

            int face = 1 + rnd.nextInt(6);
            state.lastRoll = face;

            if (state.diceLabel != null) {
                state.diceLabel.setText("🎲 " + face);
            }

            if (ticks[0] >= 10) { // ~700ms
                t.stop();
                int finalRoll = 1 + rnd.nextInt(6);
                state.lastRoll = finalRoll;

                if (state.diceLabel != null) {
                    state.diceLabel.setText("🎲 " + finalRoll);
                }

                onDone.accept(finalRoll);
            }
        });
        t.start();
    }
}


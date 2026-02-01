package investor;

import javax.swing.*;

public class SetupDialog {

    public static void showStartup(GameState state) {
        if (state == null) return;

        // Unified setup (no "Quick setup" / "Choose precisely")
        SettingsDialogs.SetupResult res = SettingsDialogs.showSetupDialog(null, state);
        if (res == null) return;

        state.players = res.players;
    }
}

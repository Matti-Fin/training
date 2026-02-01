package investor;

import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Audio {

    private static boolean enabled = true;

    // one-shot SFX (let them overlap normally)
    // Exclusive SFX per key (footsteps, roulette)
    private static final Map<String, Clip> exclusive = new HashMap<>();

    // Background music single clip
    private static Clip bgmClip;

    public static void setEnabled(boolean on) {
        enabled = on;
        if (!enabled) {
            stopAll();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void stopAll() {
        stopBgm();
        stopExclusiveAll();
        // NOTE: one-shot clips started via play() are not tracked;
        // they will stop naturally, but we try to be strict by stopping exclusive + bgm.
    }

    public static void stopBgm() {
        if (bgmClip != null) {
            try {
                bgmClip.stop();
                bgmClip.close();
            } catch (Exception ignored) {}
            bgmClip = null;
        }
    }

    private static void stopExclusiveAll() {
        for (Clip c : exclusive.values()) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {}
        }
        exclusive.clear();
    }

    // Basic play (can overlap)
    public static void play(String path, float volume01) {
        if (!enabled) return;
        if (path == null || path.isBlank()) return;

        try {
            Clip clip = AudioSystem.getClip();
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path));
            clip.open(ais);
            setClipVolume(clip, volume01);
            clip.start();

            // auto-close when done
            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    try { clip.close(); } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {
            // silently ignore missing audio / unsupported format for now
        }
    }

    // Non-overlapping per key (footsteps/roulette etc)
    public static void playExclusive(String key, String path, float volume01) {
        if (!enabled) return;
        if (key == null || key.isBlank()) return;
        if (path == null || path.isBlank()) return;

        // stop previous
        Clip prev = exclusive.get(key);
        if (prev != null) {
            try {
                prev.stop();
                prev.close();
            } catch (Exception ignored) {}
            exclusive.remove(key);
        }

        try {
            Clip clip = AudioSystem.getClip();
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path));
            clip.open(ais);
            setClipVolume(clip, volume01);
            clip.start();

            exclusive.put(key, clip);

            clip.addLineListener(ev -> {
                if (ev.getType() == LineEvent.Type.STOP) {
                    try { clip.close(); } catch (Exception ignored) {}
                    // remove only if same instance
                    Clip cur = exclusive.get(key);
                    if (cur == clip) exclusive.remove(key);
                }
            });
        } catch (Exception ignored) {
        }
    }

    // Random background music (loop)
    public static void startBgmRandom(GameState state, float volume01) {
        if (!enabled) return;
        if (state == null || state.cfg == null) return;
        if (state.cfg.bgmTracks == null || state.cfg.bgmTracks.length == 0) return;

        // stop old first
        stopBgm();

        try {
            String[] tracks = state.cfg.bgmTracks;
            String pick = tracks[new Random().nextInt(tracks.length)];
            if (pick == null || pick.isBlank()) return;

            Clip clip = AudioSystem.getClip();
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(pick));
            clip.open(ais);
            setClipVolume(clip, volume01);

            clip.loop(Clip.LOOP_CONTINUOUSLY);
            clip.start();

            bgmClip = clip;
        } catch (Exception ignored) {
            bgmClip = null;
        }
    }

    private static void setClipVolume(Clip clip, float volume01) {
        try {
            if (clip == null) return;
            volume01 = Math.max(0f, Math.min(1f, volume01));

            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

            // convert 0..1 -> dB
            // 0 => -80dB (almost silent), 1 => 0dB
            float dB = (volume01 <= 0.0001f) ? -80f : (float)(20.0 * Math.log10(volume01));
            dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));

            gain.setValue(dB);
        } catch (Exception ignored) {
        }
    }
}

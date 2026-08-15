package snake.audio;

import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * 跨平台非阻塞音效，对应 Python {@code snake_audio}（winsound.Beep 的替代）。
 *
 * <p>用 {@link SourceDataLine} 合成 8 kHz 单声道方波播放指定的「频率-时长」序列。
 * 每次播放都在后台线程中进行，对应 Python 的 daemon thread——音效不阻塞游戏。
 */
public final class AudioBeep {

    private static final int SAMPLE_RATE = 8000;
    private static final AudioFormat FORMAT =
            new AudioFormat(SAMPLE_RATE, 8, 1, false, false);

    /** 音效名 → (频率Hz, 时长ms) 序列，与 Python SOUND_SEQUENCES 完全一致。 */
    private static final Map<String, int[][]> SEQUENCES = Map.of(
            "eat", new int[][] {{880, 45}, {1180, 55}},
            "break", new int[][] {{180, 35}, {520, 45}, {760, 55}},
            "death", new int[][] {{360, 120}, {240, 160}, {160, 220}});

    /** 合成回调，用于在音频不可用时回退到 GUI 响铃（对应 Python 的 fallback bell）。 */
    public interface Fallback {
        void bell();
    }

    private AudioBeep() {
    }

    /**
     * 播放指定音效；失败则调用 fallback。
     */
    public static void playSound(String kind, Fallback fallback) {
        int[][] sequence = SEQUENCES.get(kind);
        if (sequence == null) {
            return;
        }
        Thread worker = new Thread(() -> {
            try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
                line.open(FORMAT);
                line.start();
                for (int[] step : sequence) {
                    writeSquare(line, step[0], step[1]);
                }
                line.drain();
            } catch (LineUnavailableException | RuntimeException e) {
                if (fallback != null) {
                    fallback.bell();
                }
            }
        }, "snake-audio");
        worker.setDaemon(true);
        worker.start();
    }

    /** 合成并写入一个频率段（占空比 50% 的方波）。 */
    private static void writeSquare(SourceDataLine line, int frequency, int durationMs) {
        int total = SAMPLE_RATE * durationMs / 1000;
        int samplesPerCycle = frequency > 0 ? SAMPLE_RATE / frequency : total;
        byte[] buffer = new byte[total];
        for (int i = 0; i < total; i++) {
            buffer[i] = (byte) (i % samplesPerCycle < samplesPerCycle / 2 ? 40 : -40);
        }
        line.write(buffer, 0, total);
    }
}

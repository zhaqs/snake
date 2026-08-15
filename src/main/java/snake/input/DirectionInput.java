package snake.input;

import java.awt.event.KeyEvent;
import java.util.Map;

import snake.model.Point;

/**
 * 按键到方向的映射，对应 Python {@code snake_input.py}。
 * 这里直接使用 Swing/AWT 的 keyCode，避免依赖 keysym 字符串。
 */
public final class DirectionInput {

    private DirectionInput() {
    }

    /** keyCode → 方向。 */
    private static final Map<Integer, Point> CODE_TO_DIRECTION = Map.of(
            KeyEvent.VK_UP, Point.UP,
            KeyEvent.VK_W, Point.UP,
            KeyEvent.VK_DOWN, Point.DOWN,
            KeyEvent.VK_S, Point.DOWN,
            KeyEvent.VK_LEFT, Point.LEFT,
            KeyEvent.VK_A, Point.LEFT,
            KeyEvent.VK_RIGHT, Point.RIGHT,
            KeyEvent.VK_D, Point.RIGHT);

    /** 返回按键对应的方向，若非方向键返回 null。 */
    public static Point directionForCode(int keyCode) {
        return CODE_TO_DIRECTION.get(keyCode);
    }
}

package snake.rules;

import snake.model.Point;

/**
 * 纯网格规则，对应 Python {@code snake_rules.py}。无内部状态，便于单元测试。
 */
public final class SnakeRules {

    private SnakeRules() {
    }

    /** 两个方向是否相反（即不能直接掉头）。 */
    public static boolean directionsAreOppposites(int firstX, int firstY, int secondX, int secondY) {
        return firstX + secondX == 0 && firstY + secondY == 0;
    }

    /** 是否发生致命碰撞——撞自身或撞障碍，且当前无敌为否。 */
    public static boolean collisionIsFatal(boolean hitsSelf, boolean hitsObstacle, boolean invincible) {
        return (hitsSelf || hitsObstacle) && !invincible;
    }

    /** 点是否在网格内。 */
    public static boolean pointInBounds(Point point, int width, int height) {
        return point.x() >= 0 && point.x() < width && point.y() >= 0 && point.y() < height;
    }

    /** 曼哈顿距离。 */
    public static int manhattanDistance(Point first, Point second) {
        return Math.abs(first.x() - second.x()) + Math.abs(first.y() - second.y());
    }
}

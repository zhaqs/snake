package snake.rules;

import java.util.Set;

import snake.model.Point;

/**
 * 磁铁吸引的纯逻辑，对应 Python {@code attracted_position}。
 * 无状态、不依赖 GUI，便于单元测试。
 */
public final class MagnetLogic {

    private MagnetLogic() {
    }

    /**
     * 将一个点向目标方向拉近一步。优先沿距离较大的轴移动（如 x 差≥y 差则先 x 后 y），
     * 若目标格被阻挡则尝试另一轴，两步都不可行则停止。
     * 对应 Python 的 {@code attracted_position}。
     *
     * @param from        被吸引的起点（食物或道具）
     * @param target      吸引中心（蛇头）
     * @param blocked     不可落入的格子集合
     * @param steps       最多移动步数（{@code MAGNET_PULL_STEPS}）
     * @param radius      吸引生效半径（{@code MAGNET_RADIUS}）
     * @param gridWidth   网格宽度
     * @param gridHeight  网格高度
     */
    public static Point pullToward(Point from, Point target, Set<Point> blocked,
                                   int steps, int radius, int gridWidth, int gridHeight) {
        Point current = from;
        for (int step = 0; step < steps
                && SnakeRules.manhattanDistance(current, target) <= radius; step++) {
            int dx = Integer.compare(target.x(), current.x());
            int dy = Integer.compare(target.y(), current.y());
            // 优先沿距离大的轴移动
            Point[] orderedSteps = Math.abs(target.x() - current.x()) >= Math.abs(target.y() - current.y())
                    ? new Point[]{new Point(dx, 0), new Point(0, dy)}
                    : new Point[]{new Point(0, dy), new Point(dx, 0)};
            boolean moved = false;
            for (Point stepDir : orderedSteps) {
                if (stepDir.x() == 0 && stepDir.y() == 0) {
                    continue;
                }
                Point candidate = current.moved(stepDir);
                if (SnakeRules.pointInBounds(candidate, gridWidth, gridHeight) && !blocked.contains(candidate)) {
                    current = candidate;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                break;
            }
        }
        return current;
    }
}
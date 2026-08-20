package snake.rules;

import java.util.Deque;

import snake.model.Point;

/**
 * 转向队列的纯判定逻辑，对应 Python {@code _enqueue_turn} 的决策部分。
 * 无状态、不依赖时间与 GUI，便于单元测试。
 */
public final class TurnQueueLogic {

    private TurnQueueLogic() {
    }

    /** 一次转向请求的决策结果。 */
    public enum TurnDecision {
        /** 忽略：方向无效、相同、反向或与前一方向冲突。 */
        IGNORE,
        /** 立即转弯：进度足够靠前，直接应用新方向。 */
        IMMEDIATE,
        /** 替换队尾：修正队尾方向（快速掉头修正或队列已满）。 */
        REPLACE,
        /** 入队追加：将方向加入队尾等待执行。 */
        APPEND
    }

    /**
     * 计算一次转向请求的决策，对应 Python 的 {@code _enqueue_turn}。
     *
     * @param currentDirection 当前实际方向
     * @param queue            待执行转向队列（不得为 null）
     * @param requested        请求的方向
     * @param immediateAllowed 是否允许立即转弯（由调用方依据移动进度计算）
     * @param queueLimit       队列最大长度（{@code TURN_QUEUE_LIMIT}）
     */
    public static TurnDecision decide(Point currentDirection, Deque<Point> queue,
                                     Point requested, boolean immediateAllowed, int queueLimit) {
        if (queue.isEmpty()) {
            if (requested.equals(currentDirection)
                    || SnakeRules.directionsAreOppposites(requested.x(), requested.y(),
                    currentDirection.x(), currentDirection.y())) {
                return TurnDecision.IGNORE;
            }
            if (immediateAllowed) {
                return TurnDecision.IMMEDIATE;
            }
        } else {
            Point effective = queue.peekLast();
            Point previous = previous(queue, currentDirection);
            if (requested.equals(effective)) {
                return TurnDecision.IGNORE;
            }
            if (SnakeRules.directionsAreOppposites(requested.x(), requested.y(),
                    effective.x(), effective.y())) {
                if (requested.equals(previous)
                        || SnakeRules.directionsAreOppposites(requested.x(), requested.y(),
                        previous.x(), previous.y())) {
                    return TurnDecision.IGNORE;
                }
                return TurnDecision.REPLACE;
            }
        }
        if (queue.size() >= queueLimit) {
            Point prev = previous(queue, currentDirection);
            if (requested.equals(prev)
                    || SnakeRules.directionsAreOppposites(requested.x(), requested.y(),
                    prev.x(), prev.y())) {
                return TurnDecision.IGNORE;
            }
            return TurnDecision.REPLACE;
        }
        return TurnDecision.APPEND;
    }

    /** 队列倒数第二个方向；队列不足两个时回退到当前方向。 */
    private static Point previous(Deque<Point> queue, Point currentDirection) {
        if (queue.size() > 1) {
            Point[] array = queue.toArray(new Point[0]);
            return array[array.length - 2];
        }
        return currentDirection;
    }
}
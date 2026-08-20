package snake.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

import snake.model.Point;
import snake.rules.TurnQueueLogic.TurnDecision;

class TurnQueueLogicTest {

    private static final int LIMIT = 3;

    private static Deque<Point> queue(Point... items) {
        Deque<Point> q = new ArrayDeque<>();
        for (Point p : items) {
            q.addLast(p);
        }
        return q;
    }

    @Test
    void emptyQueue_sameDirection_isIgnored() {
        assertEquals(TurnDecision.IGNORE,
                TurnQueueLogic.decide(Point.RIGHT, queue(), Point.RIGHT, true, LIMIT));
    }

    @Test
    void emptyQueue_oppositeDirection_isIgnored() {
        assertEquals(TurnDecision.IGNORE,
                TurnQueueLogic.decide(Point.RIGHT, queue(), Point.LEFT, true, LIMIT));
    }

    @Test
    void emptyQueue_validWithImmediate_turnsImmediately() {
        assertEquals(TurnDecision.IMMEDIATE,
                TurnQueueLogic.decide(Point.RIGHT, queue(), Point.UP, true, LIMIT));
    }

    @Test
    void emptyQueue_validWithoutImmediate_appends() {
        assertEquals(TurnDecision.APPEND,
                TurnQueueLogic.decide(Point.RIGHT, queue(), Point.UP, false, LIMIT));
    }

    @Test
    void nonEmptyQueue_sameAsTail_isIgnored() {
        assertEquals(TurnDecision.IGNORE,
                TurnQueueLogic.decide(Point.RIGHT, queue(Point.UP), Point.UP, false, LIMIT));
    }

    @Test
    void nonEmptyQueue_oppositeTailEqualToPrevious_isIgnored() {
        // current=UP, queue=[DOWN], requested=UP：UP 反向队尾 DOWN 且 UP 与当前方向 UP 相同
        assertEquals(TurnDecision.IGNORE,
                TurnQueueLogic.decide(Point.UP, queue(Point.DOWN), Point.UP, false, LIMIT));
    }

    @Test
    void nonEmptyQueue_oppositeTailAllowed_replacesTail() {
        // current=RIGHT, queue=[DOWN], requested=UP：UP 反向队尾 DOWN，与当前方向 RIGHT 不冲突
        assertEquals(TurnDecision.REPLACE,
                TurnQueueLogic.decide(Point.RIGHT, queue(Point.DOWN), Point.UP, false, LIMIT));
    }

    @Test
    void fullQueue_equalsPrevious_isIgnored() {
        // current=RIGHT, queue=[UP, LEFT, DOWN], requested=LEFT：队尾 DOWN 不反向 LEFT；满员且 prev=LEFT 与请求相同
        assertEquals(TurnDecision.IGNORE,
                TurnQueueLogic.decide(Point.RIGHT, queue(Point.UP, Point.LEFT, Point.DOWN),
                        Point.LEFT, false, LIMIT));
    }

    @Test
    void fullQueue_valid_replacesTail() {
        // current=RIGHT, queue=[UP, LEFT, RIGHT], requested=UP：队尾 RIGHT 不反向 UP；满员且 prev=LEFT 不冲突
        assertEquals(TurnDecision.REPLACE,
                TurnQueueLogic.decide(Point.RIGHT, queue(Point.UP, Point.LEFT, Point.RIGHT),
                        Point.UP, false, LIMIT));
    }

    @Test
    void nonFullQueue_valid_appends() {
        // current=RIGHT, queue=[UP], requested=LEFT：队尾 UP 与 LEFT 不反向，队列未满
        assertEquals(TurnDecision.APPEND,
                TurnQueueLogic.decide(Point.RIGHT, queue(Point.UP), Point.LEFT, false, LIMIT));
    }
}
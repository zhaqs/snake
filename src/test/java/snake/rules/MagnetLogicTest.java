package snake.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import snake.model.Point;

class MagnetLogicTest {

    private static final int W = 24;
    private static final int H = 18;

    private static Set<Point> none() {
        return new HashSet<>();
    }

    @Test
    void pullToward_movesCloserAlongLargerAxis() {
        // 目标在右侧偏下，x 差 2 > y 差 1，先沿 x 再沿 y
        Point from = new Point(10, 10);
        Point target = new Point(12, 11);
        assertEquals(new Point(11, 10), MagnetLogic.pullToward(from, target, none(), 1, 3, W, H));
    }

    @Test
    void pullToward_stopsWhenTargetReached() {
        Point target = new Point(5, 5);
        assertEquals(target, MagnetLogic.pullToward(target, target, none(), 3, 3, W, H));
    }

    @Test
    void pullToward_avoidsBlockedCells() {
        // 优先轴被堵时改走另一轴
        Point from = new Point(5, 5);
        Point target = new Point(6, 6);
        Set<Point> blocked = new HashSet<>();
        blocked.add(new Point(6, 5));
        assertEquals(new Point(5, 6), MagnetLogic.pullToward(from, target, blocked, 1, 3, W, H));

        // 两条路都被堵则原地不动
        Set<Point> blocked2 = new HashSet<>();
        blocked2.add(new Point(6, 5));
        blocked2.add(new Point(5, 6));
        assertEquals(new Point(5, 5), MagnetLogic.pullToward(from, target, blocked2, 1, 3, W, H));
    }

    @Test
    void pullToward_doesNotLeaveGrid() {
        Point from = new Point(0, 0);
        Point target = new Point(-3, 0);
        // 目标在界外，拉向 -x 会出界 -> 保持原格
        assertEquals(new Point(0, 0), MagnetLogic.pullToward(from, target, none(), 1, 3, W, H));
    }

    @Test
    void pullToward_respectsMultipleSteps() {
        Point from = new Point(10, 10);
        Point target = new Point(13, 10);
        assertEquals(new Point(12, 10), MagnetLogic.pullToward(from, target, none(), 2, 3, W, H));
        assertEquals(new Point(13, 10), MagnetLogic.pullToward(from, target, none(), 3, 3, W, H));
    }

    @Test
    void pullToward_stopsBeyondRadius() {
        // 距离 4 > radius 3：第一轮循环条件即不满足，不动
        Point from = new Point(0, 0);
        Point target = new Point(4, 0);
        assertEquals(new Point(0, 0), MagnetLogic.pullToward(from, target, none(), 3, 3, W, H));
    }
}
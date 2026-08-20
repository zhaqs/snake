package snake.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import snake.model.Point;

class SnakeRulesTest {

    @Test
    void directionsAreOppposites_recognizesOppositePairs() {
        assertTrue(SnakeRules.directionsAreOppposites(Point.UP.x(), Point.UP.y(),
                Point.DOWN.x(), Point.DOWN.y()));
        assertTrue(SnakeRules.directionsAreOppposites(Point.LEFT.x(), Point.LEFT.y(),
                Point.RIGHT.x(), Point.RIGHT.y()));
        assertTrue(SnakeRules.directionsAreOppposites(Point.RIGHT.x(), Point.RIGHT.y(),
                Point.LEFT.x(), Point.LEFT.y()));
    }

    @Test
    void directionsAreOppposites_rejectsNonOppositePairs() {
        assertFalse(SnakeRules.directionsAreOppposites(Point.UP.x(), Point.UP.y(),
                Point.LEFT.x(), Point.LEFT.y()));
        assertFalse(SnakeRules.directionsAreOppposites(Point.RIGHT.x(), Point.RIGHT.y(),
                Point.RIGHT.x(), Point.RIGHT.y()));
        assertFalse(SnakeRules.directionsAreOppposites(Point.UP.x(), Point.UP.y(),
                Point.UP.x(), Point.UP.y()));
    }

    @Test
    void collisionIsFatal_selfOrObstacleWithoutInvincibility() {
        assertTrue(SnakeRules.collisionIsFatal(true, false, false));
        assertTrue(SnakeRules.collisionIsFatal(false, true, false));
        assertTrue(SnakeRules.collisionIsFatal(true, true, false));
    }

    @Test
    void collisionIsFatal_invincibleProtects() {
        assertFalse(SnakeRules.collisionIsFatal(true, false, true));
        assertFalse(SnakeRules.collisionIsFatal(false, true, true));
        assertFalse(SnakeRules.collisionIsFatal(true, true, true));
    }

    @Test
    void collisionIsFatal_clearPathIsSafe() {
        assertFalse(SnakeRules.collisionIsFatal(false, false, false));
        assertFalse(SnakeRules.collisionIsFatal(false, false, true));
    }

    @Test
    void pointInBounds_acceptsInsideAndEdges() {
        assertTrue(SnakeRules.pointInBounds(new Point(0, 0), 24, 18));
        assertTrue(SnakeRules.pointInBounds(new Point(23, 17), 24, 18));
        assertTrue(SnakeRules.pointInBounds(new Point(10, 9), 24, 18));
    }

    @Test
    void pointInBounds_rejectsOutside() {
        assertFalse(SnakeRules.pointInBounds(new Point(-1, 0), 24, 18));
        assertFalse(SnakeRules.pointInBounds(new Point(24, 0), 24, 18));
        assertFalse(SnakeRules.pointInBounds(new Point(0, 18), 24, 18));
    }

    @Test
    void manhattanDistance_computesCorrectly() {
        assertEquals(0, SnakeRules.manhattanDistance(new Point(3, 3), new Point(3, 3)));
        assertEquals(5, SnakeRules.manhattanDistance(new Point(0, 0), new Point(3, 2)));
        assertEquals(9, SnakeRules.manhattanDistance(new Point(10, 4), new Point(5, 8)));
        assertEquals(7, SnakeRules.manhattanDistance(new Point(0, 3), new Point(4, 6)));
    }
}
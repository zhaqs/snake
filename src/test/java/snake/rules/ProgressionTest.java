package snake.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProgressionTest {

    @Test
    void levelForScore_roundsDown() {
        assertEquals(1, Progression.levelForScore(0, 5));
        assertEquals(1, Progression.levelForScore(4, 5));
        assertEquals(2, Progression.levelForScore(5, 5));
        assertEquals(3, Progression.levelForScore(10, 5));
        assertEquals(6, Progression.levelForScore(25, 5));
    }

    @Test
    void levelForScore_customStep() {
        assertEquals(1, Progression.levelForScore(99, 100));
        assertEquals(2, Progression.levelForScore(100, 100));
    }

    @Test
    void obstacleTargetForLevel_growsPerLevel() {
        assertEquals(0, Progression.obstacleTargetForLevel(1, 4, 44));
        assertEquals(4, Progression.obstacleTargetForLevel(2, 4, 44));
        assertEquals(8, Progression.obstacleTargetForLevel(3, 4, 44));
    }

    @Test
    void obstacleTargetForLevel_capsAtMaximum() {
        assertEquals(40, Progression.obstacleTargetForLevel(11, 4, 44));
        assertEquals(44, Progression.obstacleTargetForLevel(12, 4, 44));
        assertEquals(44, Progression.obstacleTargetForLevel(20, 4, 44));
        assertEquals(5, Progression.obstacleTargetForLevel(6, 1, 5));
    }
}
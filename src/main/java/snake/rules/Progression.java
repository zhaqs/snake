package snake.rules;

/**
 * 等级与障碍推进的纯计算逻辑，对应 Python 的等级与障碍数量公式。
 * 无状态、不依赖随机数与 GUI，便于单元测试。
 */
public final class Progression {

    private Progression() {
    }

    /** 根据分数计算等级：每 {@code levelScoreStep} 分升一级（从 1 开始）。 */
    public static int levelForScore(int score, int levelScoreStep) {
        return score / levelScoreStep + 1;
    }

    /** 某等级应生成的障碍物数量：每级新增 {@code obstaclesPerLevel} 个，上限 {@code maxObstacles}。 */
    public static int obstacleTargetForLevel(int level, int obstaclesPerLevel, int maxObstacles) {
        return Math.min(maxObstacles, (level - 1) * obstaclesPerLevel);
    }
}
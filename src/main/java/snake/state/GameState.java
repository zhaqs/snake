package snake.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import snake.model.Point;
import snake.model.PowerUp;
import snake.model.PowerUpKind;
import snake.model.WallBreakEffect;

/**
 * 可变运行时状态，独立于 GUI 控件，对应 Python {@code snake_state.GameState}。
 * 时间以秒为单位（双精度），由调用方传入单调时钟读数。
 */
public class GameState {

    public List<Point> snake = new ArrayList<>();
    public Point direction = Point.RIGHT;
    public final java.util.ArrayDeque<Point> turnQueue = new java.util.ArrayDeque<>();
    public Point food = new Point(0, 0);
    public PowerUp powerUp = null;
    public int score = 0;
    public int highScore = 0;
    public int level = 1;
    public Set<Point> obstacles = new HashSet<>();
    public List<WallBreakEffect> wallBreakEffects = new ArrayList<>();
    public boolean running = false;
    public boolean paused = false;
    public boolean gameOver = false;
    public boolean boosting = false;
    public double invincibleStartedAt = 0.0;
    public double invincibleUntil = 0.0;
    public double magnetStartedAt = 0.0;
    public double magnetUntil = 0.0;
    public double levelNoticeUntil = 0.0;
    public double nextPowerUpSpawnAt = 0.0;
    public double moveProgress = 0.0;
    public double lastMoveAt = 0.0;
    public double deathStartedAt = 0.0;
    public double deathUntil = 0.0;
    public Point deathDirection = null;
    public boolean deathByWall = false;

    /**
     * 重置一轮：保留 highScore，其余字段回到默认值，并把蛇身和起始移动时间设好。
     * 对应 Python {@code reset_round} 中按 fields 重置默认值的实现。
     */
    public void resetRound(List<Point> snakeBody, double now) {
        int keepHigh = this.highScore;
        applyDefaults();
        this.snake = new ArrayList<>(snakeBody);
        this.highScore = keepHigh;
        this.lastMoveAt = now;
        // food 由调用方随后 place
    }

    /** 将全部字段复位为默认值。新增字段时必须同步在此处给出默认值。 */
    private void applyDefaults() {
        this.snake = new ArrayList<>();
        this.direction = Point.RIGHT;
        this.turnQueue.clear();
        this.food = new Point(0, 0);
        this.powerUp = null;
        this.score = 0;
        this.highScore = 0;
        this.level = 1;
        this.obstacles = new HashSet<>();
        this.wallBreakEffects = new ArrayList<>();
        this.running = false;
        this.paused = false;
        this.gameOver = false;
        this.boosting = false;
        this.invincibleStartedAt = 0.0;
        this.invincibleUntil = 0.0;
        this.magnetStartedAt = 0.0;
        this.magnetUntil = 0.0;
        this.levelNoticeUntil = 0.0;
        this.nextPowerUpSpawnAt = 0.0;
        this.moveProgress = 0.0;
        this.lastMoveAt = 0.0;
        this.deathStartedAt = 0.0;
        this.deathUntil = 0.0;
        this.deathDirection = null;
        this.deathByWall = false;
    }

    public boolean invincibleActive(double now) {
        return invincibleUntil > now;
    }

    public boolean magnetActive(double now) {
        return magnetUntil > now;
    }

    /**
     * 激活道具效果。若当前效果已过期，则记录起始时间；否则在现有结束时间上累加。
     * 对应 Python {@code activate_effect}。
     */
    public void activateEffect(PowerUpKind kind, double now, double duration) {
        if (kind == PowerUpKind.INVINCIBLE) {
            if (invincibleUntil <= now) {
                invincibleStartedAt = now;
            }
            invincibleUntil = Math.max(now, invincibleUntil) + duration;
        } else if (kind == PowerUpKind.MAGNET) {
            if (magnetUntil <= now) {
                magnetStartedAt = now;
            }
            magnetUntil = Math.max(now, magnetUntil) + duration;
        }
    }
}

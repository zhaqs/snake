package snake.state;

import java.util.ArrayList;
import java.util.List;

import snake.model.Point;
import snake.model.PowerUp;
import snake.model.PowerUpKind;
import snake.model.WallBreakEffect;

/**
 * 可变运行时状态，独立于 GUI 控件。
 * 自由移动版：蛇是连续像素坐标的珠串（{@link #body}，0 号为头），
 * 蛇头带航向角 {@link #heading}，每帧按速度前进，身体珠子做等距约束跟随。
 * 时间以秒为单位（双精度），由调用方传入单调时钟读数。
 */
public class GameState {

    /** 蛇身珠子像素坐标列表，{x, y}，索引 0 为蛇头。 */
    public List<double[]> body = new ArrayList<>();
    /** 航向角（弧度，0 = 向右，顺时针为正——屏幕 y 向下）。 */
    public double heading = 0.0;
    /** 当前移动方向向量（归一化），(0,0) 表示停止。支持斜向（两方向键协同）。 */
    public double dirX = 0.0;
    public double dirY = 0.0;
    public List<Point> foods = new ArrayList<>();
    public PowerUp powerUp = null;
    public int score = 0;
    public int highScore = 0;
    public int level = 1;
    public java.util.Set<Point> obstacles = new java.util.HashSet<>();
    public List<WallBreakEffect> wallBreakEffects = new ArrayList<>();
    public boolean running = false;
    public boolean paused = false;
    public boolean gameOver = false;
    public double invincibleStartedAt = 0.0;
    public double invincibleUntil = 0.0;
    public double magnetStartedAt = 0.0;
    public double magnetUntil = 0.0;
    public double levelNoticeUntil = 0.0;
    public double nextPowerUpSpawnAt = 0.0;
    /** 上次物理帧时间（秒）。 */
    public double lastTickAt = 0.0;
    /** 磁铁上次拉扯时间（秒），用于节流。 */
    public double lastMagnetPullAt = 0.0;
    public double deathStartedAt = 0.0;
    public double deathUntil = 0.0;
    public boolean deathByWall = false;

    /**
     * 重置一轮：保留 highScore，其余字段回到默认值，并把初始蛇身设好。
     */
    public void resetRound(List<double[]> snakeBody, double now) {
        int keepHigh = this.highScore;
        applyDefaults();
        this.body = new ArrayList<>(snakeBody);
        this.highScore = keepHigh;
        this.lastTickAt = now;
        // food 由调用方随后 place
    }

    /** 将全部字段复位为默认值。新增字段时必须同步在此处给出默认值。 */
    private void applyDefaults() {
        this.body = new ArrayList<>();
        this.heading = 0.0;
        this.dirX = 0.0;
        this.dirY = 0.0;
        this.foods = new ArrayList<>();
        this.powerUp = null;
        this.score = 0;
        this.highScore = 0;
        this.level = 1;
        this.obstacles = new java.util.HashSet<>();
        this.wallBreakEffects = new ArrayList<>();
        this.running = false;
        this.paused = false;
        this.gameOver = false;
        this.invincibleStartedAt = 0.0;
        this.invincibleUntil = 0.0;
        this.magnetStartedAt = 0.0;
        this.magnetUntil = 0.0;
        this.levelNoticeUntil = 0.0;
        this.nextPowerUpSpawnAt = 0.0;
        this.lastTickAt = 0.0;
        this.lastMagnetPullAt = 0.0;
        this.deathStartedAt = 0.0;
        this.deathUntil = 0.0;
        this.deathByWall = false;
    }

    /** 蛇头像素坐标 {x, y}。 */
    public double[] head() {
        return body.get(0);
    }

    public boolean invincibleActive(double now) {
        return invincibleUntil > now;
    }

    public boolean magnetActive(double now) {
        return magnetUntil > now;
    }

    /**
     * 激活道具效果。若当前效果已过期，则记录起始时间；否则在现有结束时间上累加。
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

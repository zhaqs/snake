package snake.constants;

import java.awt.Color;

import snake.model.PowerUpKind;

/**
 * 游戏常量、配色与主题，对应 Python 的 {@code snake_constants.py}。
 * 颜色由 Tkinter 形如 {@code "#34d399"} 的字符串转换为 {@link Color} 常量。
 */
public final class GameConfig {

    private GameConfig() {
    }

    // --- 网格与玩法 ---
    public static final int CELL_SIZE = 24;
    public static final int GRID_WIDTH = 24;
    public static final int GRID_HEIGHT = 18;
    public static final int START_LENGTH = 4;
    public static final int FRAME_DELAY_MS = 16;
    public static final int START_MOVE_INTERVAL_MS = 250;
    public static final int MIN_MOVE_INTERVAL_MS = 60;
    public static final int SPEEDUP_PER_SEGMENT_MS = 3;
    public static final int BOOST_MULTIPLIER = 2;
    public static final int TURN_QUEUE_LIMIT = 3;
    public static final double IMMEDIATE_TURN_PROGRESS_LIMIT = 0.2;
    public static final double POWER_UP_SPAWN_INTERVAL_SECONDS = 10.0;
    public static final double POWER_UP_DURATION_SECONDS = 5.0;
    public static final double INVINCIBLE_WARNING_SECONDS = 3.0;
    public static final int MAGNET_RADIUS = 3;
    public static final int MAGNET_PULL_STEPS = 3;
    public static final int MAGNET_AUTO_PICKUP_RADIUS = 2;
    public static final double WALL_BREAK_DURATION_SECONDS = 0.55;
    public static final int LEVEL_SCORE_STEP = 5;
    public static final int LEVEL_SPEEDUP_MS = 7;
    public static final double LEVEL_NOTICE_SECONDS = 1.2;
    public static final int OBSTACLES_PER_LEVEL = 4;
    public static final int MAX_OBSTACLES = 44;
    public static final int OBSTACLE_SAFE_RADIUS = 3;
    public static final double DEATH_ANIMATION_SECONDS = 0.8;

    // --- 自由移动（连续运动） ---
    /** 身体珠子间距（像素），略小于珠径以形成连贯珠串。 */
    public static final double BEAD_SPACING = CELL_SIZE * 0.66;
    /** 蛇头碰撞半径（像素）。 */
    public static final double SNAKE_HEAD_RADIUS = CELL_SIZE * 0.5;
    /** 食物/道具拾取半径（像素，头心到格心）。 */
    public static final double FOOD_PICK_RADIUS = CELL_SIZE * 0.75;
    /** 自身碰撞跳过头部起的珠子数（转弯直径内的珠子必然贴身，不判死）。 */
    public static final int SELF_COLLISION_SKIP = 5;
    /** 自身碰撞阈值：头心到身珠心距离小于此值才判死（"穿过轴线"才死，取小于身珠半径的紧值）。 */
    public static final double SELF_HIT_RADIUS = CELL_SIZE * 0.35;
    /** 最小转弯半径（像素），转向速率 = 速度 / 该半径。 */
    public static final double TURN_RADIUS_PX = CELL_SIZE * 0.9;
    /** 减速键系数。 */
    public static final double SLOW_FACTOR = 0.55;
    /** 磁铁拉扯节流间隔（秒）。 */
    public static final double MAGNET_PULL_INTERVAL = 0.15;
    /** 同时在场的食物数量（吃掉一个立即补一个，维持该数量）。 */
    public static final int FOOD_COUNT = 3;

    // --- 画布像素尺寸 ---
    public static final int CANVAS_WIDTH = GRID_WIDTH * CELL_SIZE;
    public static final int CANVAS_HEIGHT = GRID_HEIGHT * CELL_SIZE;

    /**
     * 完整配色主题，对应 Python {@code Theme @dataclass}。
     */
    public static final class Theme {
        public final Color bg;
        public final Color bgGlow;
        public final Color bgDeep;
        public final Color grid;
        public final Color snakeHead;
        public final Color snakeBody;
        public final Color snakeBodyBright;
        public final Color snakeBodyDark;
        public final Color snakeOutline;
        public final Color snakeOutlineDark;
        public final Color snakeEye;
        public final Color snakeIris;
        public final Color snakePupil;
        public final Color snakeTongue;
        public final Color[] invincibleFlash;
        public final Color invincibleOutline;
        /** Tkinter 中是 "gray50" 点纹，Swing 无对应——用半透明灰色模拟。 */
        public final Color invincibleWarningStipple;
        public final Color food;
        public final Color foodLight;
        public final Color foodCore;
        public final Color invinciblePowerUp;
        public final Color magnetPowerUp;
        public final Color magnetRing;
        public final Color magnetRingPulse;
        public final Color magnetBeam;
        public final Color magnetParticle;
        public final Color magnetTargetGlow;
        public final Color obstacle;
        public final Color obstacleEdge;
        public final Color wallBreakFlash;
        public final Color wallBreakFragment;
        public final Color wallBreakCrack;
        public final Color levelNotice;
        public final Color text;
        public final Color mutedText;
        public final Color snakeDeadBody;
        public final Color snakeDeadBodyDark;
        public final Color snakeDeadOutline;
        public final Color deathShockwave;

        public Theme() {
            // 霓光夜园：深底径向渐变（中心微亮，边缘更暗）
            this.bg = decode("#05070f");
            this.bgGlow = decode("#0b1020");
            this.bgDeep = decode("#05070f");
            this.grid = decode("#1b2440");
            // 蛇身玻璃珠：头亮 → 尾暗
            this.snakeHead = decode("#34d399");
            this.snakeBody = decode("#10b981");
            this.snakeBodyBright = decode("#6ee7b7");
            this.snakeBodyDark = decode("#047857");
            this.snakeOutline = decode("#047857");
            this.snakeOutlineDark = decode("#064e3b");
            // 眼睛 / 舌头
            this.snakeEye = decode("#f8fafc");
            this.snakeIris = decode("#22d3ee");
            this.snakePupil = decode("#0f172a");
            this.snakeTongue = decode("#fb7185");
            // 无敌：彩虹玻璃珠 + 金色光环
            this.invincibleFlash = new Color[] {
                    decode("#fde68a"), decode("#fca5a5"), decode("#a5b4fc"),
                    decode("#a7f3d0"), decode("#f0abfc"),
            };
            this.invincibleOutline = decode("#facc15");
            this.invincibleWarningStipple = withAlpha(decode("#9ca3af"), 122);
            // 食物：玻璃果实
            this.food = decode("#ef4444");
            this.foodLight = decode("#fca5a5");
            this.foodCore = decode("#fecaca");
            this.invinciblePowerUp = decode("#3b82f6");
            // 磁铁：紫罗兰玻璃珠
            this.magnetPowerUp = decode("#a855f7");
            this.magnetRing = decode("#7c3aed");
            this.magnetRingPulse = decode("#c4b5fd");
            this.magnetBeam = decode("#a78bfa");
            this.magnetParticle = decode("#f5d0fe");
            this.magnetTargetGlow = decode("#ddd6fe");
            this.obstacle = decode("#374151");
            this.obstacleEdge = decode("#6b7280");
            this.wallBreakFlash = decode("#fef3c7");
            this.wallBreakFragment = decode("#9ca3af");
            this.wallBreakCrack = decode("#facc15");
            this.levelNotice = decode("#facc15");
            this.text = decode("#f9fafc");
            this.mutedText = decode("#94a3b8");
            // 死亡：灰化玻璃珠 + 红色冲击波
            this.snakeDeadBody = decode("#6b7280");
            this.snakeDeadBodyDark = decode("#4b5563");
            this.snakeDeadOutline = decode("#374151");
            this.deathShockwave = decode("#ef4444");
        }

        /** 道具颜色映射，对应 {@code POWER_UP_COLORS}。 */
        public Color powerUpColor(PowerUpKind kind) {
            return switch (kind) {
                case INVINCIBLE -> invinciblePowerUp;
                case MAGNET -> magnetPowerUp;
            };
        }

        /** 道具符号映射，对应 {@code POWER_UP_SYMBOLS}。 */
        public String powerUpSymbol(PowerUpKind kind) {
            return switch (kind) {
                case INVINCIBLE -> "I";
                case MAGNET -> "M";
            };
        }
    }

    public static final Theme DARK_THEME = new Theme();

    /**
     * 解析 {@code #rrggbb} 形式的颜色字符串。
     */
    public static Color decode(String hex) {
        return Color.decode(hex);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}

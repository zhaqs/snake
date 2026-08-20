package snake;

import static snake.constants.GameConfig.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import snake.audio.AudioBeep;
import snake.model.Point;
import snake.model.PowerUp;
import snake.model.PowerUpKind;
import snake.model.WallBreakEffect;
import snake.render.PositionProvider;
import snake.render.SnakeRenderer;
import snake.rules.MagnetLogic;
import snake.rules.Progression;
import snake.rules.SnakeRules;
import snake.state.GameState;
import snake.storage.LeaderboardStore;

/**
 * 贪吃蛇主控制器，对应 Python 的 {@code SnakeGame} 类。
 * 管理 Swing 生命周期、游戏循环、键盘输入、道具生成、磁铁吸引、障碍物级别等所有游戏逻辑，
 * 并委托 {@link SnakeRenderer} 完成全部绘制。
 */
public final class SnakeGame implements PositionProvider {

    private final GameState state = new GameState();
    private final LeaderboardStore store;
    private final SnakeRenderer renderer = new SnakeRenderer();
    private final Random random;

    private final JFrame frame;
    private final GamePanel board;
    private final JLabel scoreLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final javax.swing.Timer gameTimer;
    private javax.swing.Timer deathTimer;

    private String playerName;
    /** 四方向键按下状态（两键协同可形成斜向移动）。 */
    private boolean upDown, downDown, leftDown, rightDown;

    /** 排行榜缓存：仅在有资格显示榜单的状态切换时刷新，避免绘制线程访问数据库。 */
    private List<LeaderboardStore.Entry> cachedLeaders = List.of();
    private LeaderboardStore.Entry cachedPlayerEntry;

    /** 异步加载的排行榜快照。 */
    private record LeaderboardSnapshot(List<LeaderboardStore.Entry> leaders,
                                       LeaderboardStore.Entry player) {}

    // ===================================================================
    // 构造
    // ===================================================================

    public SnakeGame() {
        this(new LeaderboardStore(), new Random());
    }

    SnakeGame(LeaderboardStore store, Random random) {
        this.store = Objects.requireNonNull(store);
        this.random = Objects.requireNonNull(random);
        state.highScore = store.loadHighScore();

        frame = new JFrame("Snake");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.getContentPane().setBackground(DARK_THEME.bg);
        frame.setLayout(new BorderLayout(8, 8));

        board = new GamePanel();
        frame.add(board, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(DARK_THEME.bg);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        configureLabel(scoreLabel, 14, Font.BOLD, DARK_THEME.text);
        configureLabel(statusLabel, 12, Font.PLAIN, DARK_THEME.mutedText);
        footer.add(scoreLabel, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.EAST);
        frame.add(footer, BorderLayout.SOUTH);

        installKeys();
        gameTimer = new javax.swing.Timer(FRAME_DELAY_MS, e -> tick(now()));
        gameTimer.setCoalesce(true);
        resetRound();
    }

    private void configureLabel(JLabel label, int size, int style, Color color) {
        label.setForeground(color);
        label.setFont(new Font("SansSerif", style, size));
    }

    // ===================================================================
    // 键盘
    // ===================================================================

    private void installKeys() {
        JRootPane root = frame.getRootPane();
        bind(root, KeyEvent.VK_ESCAPE, "quit", frame::dispose);
        bind(root, KeyEvent.VK_P, "pause", this::togglePause);
        // 四方向键：按住移动、松开停止。同时按两个相邻键可斜向移动。
        bindHold(root, KeyEvent.VK_UP, "dir-up",
                () -> { upDown = true; maybeStartOnInput(); }, () -> upDown = false);
        bindHold(root, KeyEvent.VK_W, "dir-up-w",
                () -> { upDown = true; maybeStartOnInput(); }, () -> upDown = false);
        bindHold(root, KeyEvent.VK_DOWN, "dir-down",
                () -> { downDown = true; maybeStartOnInput(); }, () -> downDown = false);
        bindHold(root, KeyEvent.VK_S, "dir-down-w",
                () -> { downDown = true; maybeStartOnInput(); }, () -> downDown = false);
        bindHold(root, KeyEvent.VK_LEFT, "dir-left",
                () -> { leftDown = true; maybeStartOnInput(); }, () -> leftDown = false);
        bindHold(root, KeyEvent.VK_A, "dir-left-w",
                () -> { leftDown = true; maybeStartOnInput(); }, () -> leftDown = false);
        bindHold(root, KeyEvent.VK_RIGHT, "dir-right",
                () -> { rightDown = true; maybeStartOnInput(); }, () -> rightDown = false);
        bindHold(root, KeyEvent.VK_D, "dir-right-w",
                () -> { rightDown = true; maybeStartOnInput(); }, () -> rightDown = false);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "space-down");
        root.getActionMap().put("space-down", action(this::spacePressed));
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "space-up");
        root.getActionMap().put("space-up", action(this::spaceReleased));
    }

    /** 未开局时按任意控制键直接开始。 */
    private void maybeStartOnInput() {
        if (!state.running && !state.gameOver && !state.paused) {
            startRound();
        }
    }

    /** 绑定按键的按下/抬起两个阶段。 */
    private static void bindHold(JRootPane root, int code, String name,
                                 Runnable onPress, Runnable onRelease) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(code, 0, false), name + "-down");
        root.getActionMap().put(name + "-down", action(onPress));
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(code, 0, true), name + "-up");
        root.getActionMap().put(name + "-up", action(onRelease));
    }

    private static void bind(JRootPane root, int code, String name, Runnable task) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, 0), name);
        root.getActionMap().put(name, action(task));
    }

    private static Action action(Runnable task) {
        return new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { task.run(); }
        };
    }

    // ===================================================================
    // 玩家名称
    // ===================================================================

    private void requestPlayerName() {
        while (playerName == null || playerName.isBlank()) {
            String input = JOptionPane.showInputDialog(frame, "请输入玩家昵称（最多 20 个字符）：",
                    "玩家昵称", JOptionPane.QUESTION_MESSAGE);
            if (input == null) {
                frame.dispose();
                return;
            }
            playerName = LeaderboardStore.normalizePlayerName(input);
        }
        refreshLeaderboardAsync();
    }

    // ===================================================================
    // 游戏循环
    // ===================================================================

    /**
     * 游戏主循环，由 {@link #gameTimer} 每 {@code FRAME_DELAY_MS}（16ms）调用一次。
     * <p>自由移动版：每帧按真实 dt 前进（转向 → 蛇头位移 → 身体跟随 → 碰撞/拾取）。
     */
    void tick(double now) {
        if (!state.running || state.paused || state.gameOver) return;

        double dt = Math.min(0.05, Math.max(0.0, now - state.lastTickAt));
        state.lastTickAt = now;

        // 方向向量 = 四方向键合成的归一化向量（支持斜向）；(0,0) 表示停止
        double vx = (rightDown ? 1 : 0) - (leftDown ? 1 : 0);
        double vy = (downDown ? 1 : 0) - (upDown ? 1 : 0);
        double mag = Math.sqrt(vx * vx + vy * vy);
        if (mag < 1e-6) {
            state.dirX = 0.0;
            state.dirY = 0.0;
        } else {
            state.dirX = vx / mag;
            state.dirY = vy / mag;
        }

        pruneEffects(now);
        refreshPowerUp(now);

        if (state.dirX != 0.0 || state.dirY != 0.0) {
            stepMovement(now, dt);
        }
        updateView(now);
    }

    /**
     * 自由移动一步：
     * <ol>
     *   <li>转向：角速度 = 速度 / 最小转弯半径（任何速度下转弯半径恒定）</li>
     *   <li>蛇头沿航向前进（减速键降速；加速已含在 {@link #moveIntervalMs}）</li>
     *   <li>身体珠子等距约束跟随</li>
     *   <li>撞墙 → 游戏结束（wall 死亡）</li>
     *   <li>撞障碍：无敌时撞碎（不结束），否则死亡</li>
     *   <li>吃食物 → 计分加长；拾道具 → 激活效果（均为像素距离判定）</li>
     *   <li>磁铁激活 → 节流吸引附近食物/道具，自动拾取则额外加长</li>
     *   <li>撞自身 → 死亡（无敌豁免）</li>
     * </ol>
     */
    private void stepMovement(double now, double dt) {
        if (dt <= 0 || (state.dirX == 0.0 && state.dirY == 0.0)) return;
        double speed = speedPxPerSec();
        double[] head = state.body.get(0);

        // 蛇头沿当前方向向量前进（heading 跟随方向，用于渲染朝向）
        head[0] += state.dirX * speed * dt;
        head[1] += state.dirY * speed * dt;
        state.heading = Math.atan2(state.dirY, state.dirX);

        // 3. 身体等距跟随
        followBody();

        // 4. 撞墙（头中心越过边界线才死，符合"穿过轴线"）
        if (head[0] < 0 || head[0] > CANVAS_WIDTH
                || head[1] < 0 || head[1] > CANVAS_HEIGHT) {
            endGame(now, true);
            return;
        }

        Point headCell = new Point((int) (head[0] / CELL_SIZE), (int) (head[1] / CELL_SIZE));

        // 5. 障碍：无敌撞碎，否则死亡
        if (state.obstacles.contains(headCell)) {
            if (state.invincibleActive(now)) {
                destroyObstacle(headCell, now);
            } else {
                endGame(now, false);
                return;
            }
        }

        // 6. 吃食物（头心到任一食物格心的像素距离）
        double pickR2 = FOOD_PICK_RADIUS * FOOD_PICK_RADIUS;
        for (int i = state.foods.size() - 1; i >= 0; i--) {
            Point f = state.foods.get(i);
            double fx = (f.x() + 0.5) * CELL_SIZE;
            double fy = (f.y() + 0.5) * CELL_SIZE;
            if (dist2(head[0], head[1], fx, fy) < pickR2) {
                state.foods.remove(i);
                AudioBeep.playSound("eat", Toolkit.getDefaultToolkit()::beep);
                growTail();
                addScore(now);
                placeFood();
            }
        }

        // 7. 拾取道具
        if (state.powerUp != null) {
            double px = (state.powerUp.position().x() + 0.5) * CELL_SIZE;
            double py = (state.powerUp.position().y() + 0.5) * CELL_SIZE;
            if (dist2(head[0], head[1], px, py) < FOOD_PICK_RADIUS * FOOD_PICK_RADIUS) {
                state.activateEffect(state.powerUp.kind(), now, POWER_UP_DURATION_SECONDS);
                state.powerUp = null;
            }
        }

        // 8. 磁铁吸引（节流拉格子，复用网格版拉扯逻辑）
        if (state.magnetActive(now) && now - state.lastMagnetPullAt >= MAGNET_PULL_INTERVAL) {
            state.lastMagnetPullAt = now;
            if (attractObjects(headCell)) {
                AudioBeep.playSound("eat", Toolkit.getDefaultToolkit()::beep);
                growTail();
                addScore(now);
                placeFood();
            }
        }

        // 9. 撞自身
        if (!state.invincibleActive(now) && hitsSelf()) {
            endGame(now, false);
        }
    }

    /** 身体珠子等距约束跟随：每颗珠子移动到与前一颗恰好 {@code BEAD_SPACING} 处。 */
    private void followBody() {
        for (int i = 1; i < state.body.size(); i++) {
            double[] prev = state.body.get(i - 1);
            double[] cur = state.body.get(i);
            double dx = cur[0] - prev[0];
            double dy = cur[1] - prev[1];
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < 1e-6) continue;
            double k = BEAD_SPACING / d;
            cur[0] = prev[0] + dx * k;
            cur[1] = prev[1] + dy * k;
        }
    }

    /** 尾部追加一颗珠子（复制当前尾点位置，下一帧自然拉开）。 */
    private void growTail() {
        double[] tail = state.body.get(state.body.size() - 1);
        state.body.add(new double[]{tail[0], tail[1]});
    }

    /** 头部是否撞到自身（跳过转弯直径内必然贴身的头部珠子）。 */
    /** 头部是否撞到自身：头心到某颗身体珠心的距离小于 SELF_HIT_RADIUS 才判死（"穿过轴线"才死）。 */
    private boolean hitsSelf() {
        double[] head = state.body.get(0);
        double limit = SELF_HIT_RADIUS;
        for (int i = SELF_COLLISION_SKIP; i < state.body.size(); i++) {
            double[] p = state.body.get(i);
            if (dist2(head[0], head[1], p[0], p[1]) < limit * limit) return true;
        }
        return false;
    }

    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** 当前速度（像素/秒）：由格节奏换算。 */
    double speedPxPerSec() {
        return CELL_SIZE * 1000.0 / moveIntervalMs();
    }

    // ===================================================================
    // 开始 / 重置 / 暂停
    // ===================================================================

    void resetRound() {
        stopTimers();
        double cx = CANVAS_WIDTH / 2.0;
        double cy = CANVAS_HEIGHT / 2.0;
        List<double[]> body = new ArrayList<>();
        for (int i = 0; i < START_LENGTH; i++) {
            body.add(new double[]{cx - i * BEAD_SPACING, cy});
        }
        state.resetRound(body, now());
        deathTimer = null;
        placeFood();
        refreshLeaderboardAsync();
        updateView(now());
    }

    void startRound() {
        if (state.running || state.gameOver) return;
        double now = now();
        state.running = true;
        state.paused = false;
        state.gameOver = false;
        state.lastTickAt = now;
        state.nextPowerUpSpawnAt = now + POWER_UP_SPAWN_INTERVAL_SECONDS;
        gameTimer.start();
        updateView(now);
    }

    private void spacePressed() {
        if (state.gameOver) {
            resetRound();
            startRound();
            return;
        }
        if (!state.running) {
            startRound();
        }
    }

    private void spaceReleased() {
        // 已取消加速功能，空格仅用于开始/重开
    }

    private void togglePause() {
        if (!state.running || state.gameOver) return;
        double now = now();
        state.paused = !state.paused;
        if (state.paused) {
            gameTimer.stop();
        } else {
            state.lastTickAt = now;
            gameTimer.start();
        }
        updateView(now);
    }

    // ===================================================================
    // 道具生成
    // ===================================================================

    private void refreshPowerUp(double now) {
        if (now < state.nextPowerUpSpawnAt) return;
        spawnPowerUp();
        state.nextPowerUpSpawnAt = now + POWER_UP_SPAWN_INTERVAL_SECONDS;
    }

    private void spawnPowerUp() {
        Set<Point> blocked = occupiedCells();
        blocked.addAll(state.obstacles);
        blocked.addAll(state.foods);
        Point cell = randomFreeCell(blocked);
        state.powerUp = cell == null ? null : new PowerUp(
                random.nextBoolean() ? PowerUpKind.INVINCIBLE : PowerUpKind.MAGNET, cell);
    }

    // ===================================================================
    // 食物
    // ===================================================================

    private void placeFood() {
        Set<Point> blocked = occupiedCells();
        blocked.addAll(state.obstacles);
        blocked.addAll(state.foods);
        // 墙附近不放食物：屏蔽最外圈一格，食物只在内部区域生成
        for (int x = 0; x < GRID_WIDTH; x++) {
            blocked.add(new Point(x, 0));
            blocked.add(new Point(x, GRID_HEIGHT - 1));
        }
        for (int y = 0; y < GRID_HEIGHT; y++) {
            blocked.add(new Point(0, y));
            blocked.add(new Point(GRID_WIDTH - 1, y));
        }
        if (state.powerUp != null) blocked.add(state.powerUp.position());
        while (state.foods.size() < FOOD_COUNT) {
            Point cell = randomFreeCell(blocked);
            if (cell == null) break;
            state.foods.add(cell);
            blocked.add(cell);
        }
    }

    /** 蛇身珠子当前覆盖的格子集合（用于布点避让）。 */
    private Set<Point> occupiedCells() {
        Set<Point> cells = new HashSet<>();
        for (double[] p : state.body) {
            cells.add(new Point((int) (p[0] / CELL_SIZE), (int) (p[1] / CELL_SIZE)));
        }
        return cells;
    }

    // ===================================================================
    // 分数 / 等级 / 障碍物
    // ===================================================================

    /** 增加 1 分，若超过历史最高分则同步写入 MySQL。 */
    private void addScore(double now) {
        state.score++;
        if (state.score > state.highScore) {
            state.highScore = state.score;
            store.saveHighScore(state.highScore);
        }
        updateLevel(now);
    }

    /** 每 5 分升一级：加快速度、增加障碍物、显示等级提示。 */
    private void updateLevel(double now) {
        int newLevel = Progression.levelForScore(state.score, LEVEL_SCORE_STEP);
        if (newLevel <= state.level) return;
        state.level = newLevel;
        state.levelNoticeUntil = now + LEVEL_NOTICE_SECONDS;
        growObstacles();
    }

    /** 在当前等级应生成的障碍物数量与已存在数量之间补齐，不超过 {@code MAX_OBSTACLES}。 */
    private void growObstacles() {
        int target = Progression.obstacleTargetForLevel(state.level, OBSTACLES_PER_LEVEL, MAX_OBSTACLES);
        while (state.obstacles.size() < target) {
            Set<Point> blocked = obstacleSpawnBlockers();
            Point cell = randomFreeCell(blocked);
            if (cell == null) return;
            state.obstacles.add(cell);
        }
    }

    private Set<Point> obstacleSpawnBlockers() {
        Set<Point> blocked = occupiedCells();
        blocked.addAll(state.obstacles);
        blocked.addAll(state.foods);
        double[] head = state.body.get(0);
        Point headCell = new Point((int) (head[0] / CELL_SIZE), (int) (head[1] / CELL_SIZE));
        for (int dy = -OBSTACLE_SAFE_RADIUS; dy <= OBSTACLE_SAFE_RADIUS; dy++) {
            for (int dx = -OBSTACLE_SAFE_RADIUS; dx <= OBSTACLE_SAFE_RADIUS; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= OBSTACLE_SAFE_RADIUS) {
                    Point p = new Point(headCell.x() + dx, headCell.y() + dy);
                    if (SnakeRules.pointInBounds(p, GRID_WIDTH, GRID_HEIGHT)) {
                        blocked.add(p);
                    }
                }
            }
        }
        if (state.powerUp != null) blocked.add(state.powerUp.position());
        return blocked;
    }

    // ===================================================================
    // 磁铁
    // ===================================================================

    /**
     * 磁铁吸引：将食物和道具向蛇头方向拉近 {@code MAGNET_PULL_STEPS} 步。
     * 若 {@code mayCollect} 为真且食物已被拉到自动拾取半径内，返回 true（触发额外加长）。
     * 对应 Python 的 {@code attract_objects}。
     */
    private boolean attractObjects(Point headCell) {
        // 吸引所有食物
        Set<Point> blockedForFood = occupiedCells();
        blockedForFood.addAll(state.obstacles);
        blockedForFood.addAll(state.foods);
        if (state.powerUp != null) blockedForFood.add(state.powerUp.position());
        for (int i = 0; i < state.foods.size(); i++) {
            Point f = state.foods.get(i);
            state.foods.set(i, MagnetLogic.pullToward(f, headCell, blockedForFood,
                    MAGNET_PULL_STEPS, MAGNET_RADIUS, GRID_WIDTH, GRID_HEIGHT));
        }

        // 自动拾取：任一食物进入自动拾取半径
        double[] head = state.body.get(0);
        double pickR = MAGNET_AUTO_PICKUP_RADIUS * CELL_SIZE;
        boolean collected = false;
        for (int i = state.foods.size() - 1; i >= 0; i--) {
            Point f = state.foods.get(i);
            double fx = (f.x() + 0.5) * CELL_SIZE;
            double fy = (f.y() + 0.5) * CELL_SIZE;
            if (dist2(head[0], head[1], fx, fy) < pickR * pickR) {
                state.foods.remove(i);
                collected = true;
            }
        }

        // 吸引道具
        if (state.powerUp != null) {
            Set<Point> blockedForPU = occupiedCells();
            blockedForPU.addAll(state.obstacles);
            blockedForPU.addAll(state.foods);
            Point moved = MagnetLogic.pullToward(state.powerUp.position(), headCell, blockedForPU,
                    MAGNET_PULL_STEPS, MAGNET_RADIUS, GRID_WIDTH, GRID_HEIGHT);
            state.powerUp = new PowerUp(state.powerUp.kind(), moved);
        }

        return collected;
    }

    // ===================================================================
    // 障碍物破碎
    // ===================================================================

    private void destroyObstacle(Point point, double now) {
        state.obstacles.remove(point);
        state.wallBreakEffects.add(new WallBreakEffect(point, now, random.nextInt()));
        AudioBeep.playSound("break", Toolkit.getDefaultToolkit()::beep);
    }

    private void pruneEffects(double now) {
        state.wallBreakEffects.removeIf(e -> now - e.startedAt() > WALL_BREAK_DURATION_SECONDS);
    }

    // ===================================================================
    // 游戏结束
    // ===================================================================

    /**
     * 在后台线程异步保存本局纪录，完成后刷新排行榜缓存。
     * 避免数据库写入阻塞 Swing 事件线程。
     */
    private void saveRecordAsync() {
        final String name = playerName;
        final int score = state.score;
        final int length = state.body.size();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                store.updateRecord(name, score, length);
                return null;
            }
            @Override protected void done() {
                refreshLeaderboardAsync();
            }
        }.execute();
    }

    /**
     * 在后台线程异步加载排行榜并写入缓存（{@code cachedLeaders}/{@code cachedPlayerEntry}）。
     * {@code done()} 在 EDT 上执行，随后触发重绘。
     */
    private void refreshLeaderboardAsync() {
        final String name = playerName;
        new SwingWorker<LeaderboardSnapshot, Void>() {
            @Override protected LeaderboardSnapshot doInBackground() {
                return new LeaderboardSnapshot(store.topByLength(5), store.getRecord(name));
            }
            @Override protected void done() {
                try {
                    LeaderboardSnapshot snapshot = get();
                    cachedLeaders = snapshot.leaders();
                    cachedPlayerEntry = snapshot.player();
                } catch (Exception ignored) {
                    // 保持旧缓存，重试由下一次状态切换触发
                }
                board.repaint();
            }
        }.execute();
    }

    /**
     * 游戏结束流程：
     * <ol>
     *   <li>播放死亡音效、停止游戏主计时器</li>
     *   <li>设置死亡状态参数（用于死亡动画渲染）</li>
     *   <li>将本局成绩写入 MySQL 排行榜（保留历史最好成绩）</li>
     *   <li>启动死亡动画计时器：每 16ms 刷新视图，显示能量圈扩散 + X 眼，持续 {@code DEATH_ANIMATION_SECONDS} 后自动停止</li>
     *   <li>立即刷新一次视图显示第一帧死亡动画</li>
     * </ol>
     */
    private void endGame(double now, boolean wall) {
        AudioBeep.playSound("death", Toolkit.getDefaultToolkit()::beep);
        gameTimer.stop();
        state.running = false;
        state.gameOver = true;
        state.deathStartedAt = now;
        state.deathUntil = now + DEATH_ANIMATION_SECONDS;
        state.deathByWall = wall;

        saveRecordAsync();

        // 死亡动画计时器：在死亡持续期间逐帧刷新视图
        if (deathTimer != null) deathTimer.stop();
        deathTimer = new javax.swing.Timer(FRAME_DELAY_MS, e -> {
            double n = now();
            updateView(n);
            if (n >= state.deathUntil) {
                deathTimer.stop();
                deathTimer = null;
            }
        });
        deathTimer.start();

        updateView(now);
    }

    // ===================================================================
    // 工具
    // ===================================================================

    /**
     * 计算当前移动间隔（毫秒）。
     * <p>基础间隔 = {@code START_MOVE_INTERVAL_MS} - 额外段数×3 - 等级×7，
     * 下限 {@code MIN_MOVE_INTERVAL_MS}。加速时除 2（取整，下限 1）。
     * 对应 Python 的 {@code move_interval_ms}。
     */
    int moveIntervalMs() {
        int extra = Math.max(0, state.body.size() - START_LENGTH);
        int levelBonus = Math.max(0, state.level - 1) * LEVEL_SPEEDUP_MS;
        return Math.max(MIN_MOVE_INTERVAL_MS,
                START_MOVE_INTERVAL_MS - extra * SPEEDUP_PER_SEGMENT_MS - levelBonus);
    }

    /**
     * 在网格中随机选一个未被阻挡的单元格。从随机起点开始线性扫描以保证均匀分布。
     * 若全被阻挡则返回 null。对应 Python 的 {@code random_free_cell}。
     */
    Point randomFreeCell(Set<Point> blocked) {
        int total = GRID_WIDTH * GRID_HEIGHT;
        int start = random.nextInt(total);
        for (int offset = 0; offset < total; offset++) {
            int idx = (start + offset) % total;
            Point p = new Point(idx % GRID_WIDTH, idx / GRID_WIDTH);
            if (!blocked.contains(p)) return p;
        }
        return null;
    }

    private void stopTimers() {
        if (gameTimer != null) gameTimer.stop();
        if (deathTimer != null) { deathTimer.stop(); deathTimer = null; }
    }

    // ===================================================================
    // 视图更新
    // ===================================================================

    private void updateView(double now) {
        String name = playerName == null ? "-" : playerName;
        scoreLabel.setText(String.format("玩家: %s   分数: %d   最高: %d   等级: %d   长度: %d",
                name, state.score, state.highScore, state.level, state.body.size()));

        if (state.gameOver) {
            statusLabel.setText("游戏结束 - 空格重新开始");
        } else if (state.paused) {
            statusLabel.setText("已暂停 - P 继续");
        } else {
            String effects = activeEffectText(now);
            if (!effects.isEmpty()) {
                statusLabel.setText(effects);
            } else if (state.running) {
                statusLabel.setText("按住方向键移动，松开停止");
            } else {
                statusLabel.setText("空格开始（方向键也可开始）");
            }
        }

        board.repaint();
    }

    private String activeEffectText(double now) {
        StringBuilder sb = new StringBuilder();
        if (state.invincibleActive(now)) {
            sb.append("无敌 ").append((int) (state.invincibleUntil - now + 0.999)).append("s");
        }
        if (state.magnetActive(now)) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("磁铁 ").append((int) (state.magnetUntil - now + 0.999)).append("s");
        }
        return sb.toString();
    }

    // ===================================================================
    // PositionProvider 实现
    // ===================================================================

    @Override
    public List<double[]> bodyPoints() {
        return state.body;
    }

    @Override
    public double heading() {
        return state.heading;
    }

    @Override
    public List<Point> magnetTargets() {
        List<Point> targets = new ArrayList<>();
        double[] head = state.body.get(0);
        double r2 = (double) (MAGNET_RADIUS * CELL_SIZE) * (MAGNET_RADIUS * CELL_SIZE);
        for (Point f : state.foods) {
            double fx = (f.x() + 0.5) * CELL_SIZE;
            double fy = (f.y() + 0.5) * CELL_SIZE;
            if (dist2(head[0], head[1], fx, fy) <= r2) {
                targets.add(f);
            }
        }
        if (state.powerUp != null) {
            double px = (state.powerUp.position().x() + 0.5) * CELL_SIZE;
            double py = (state.powerUp.position().y() + 0.5) * CELL_SIZE;
            if (dist2(head[0], head[1], px, py) <= r2) {
                targets.add(state.powerUp.position());
            }
        }
        return targets;
    }

    // ===================================================================
    // 启动
    // ===================================================================

    public void show() {
        requestPlayerName();
        if (!frame.isDisplayable()) return;
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SnakeGame().show());
    }

    // ===================================================================
    // 游戏面板
    // ===================================================================

    private final class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
            setBackground(DARK_THEME.bg);
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double now = now();
            boolean overlayVisible = state.gameOver || (!state.running && !state.paused);
            List<LeaderboardStore.Entry> leaders = overlayVisible ? cachedLeaders : List.of();
            LeaderboardStore.Entry playerEntry = overlayVisible ? cachedPlayerEntry : null;
            renderer.draw(g, state, now, SnakeGame.this, playerName, leaders, playerEntry);
            g.dispose();
        }
    }

    // ===================================================================
    // 时间
    // ===================================================================

    private static double now() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    // ===================================================================
    // 测试出口
    // ===================================================================

    GameState stateForTest() {
        return state;
    }
}
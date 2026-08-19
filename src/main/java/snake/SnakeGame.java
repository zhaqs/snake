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
import java.util.*;
import java.util.List;

import javax.swing.*;

import snake.audio.AudioBeep;
import snake.input.DirectionInput;
import snake.model.*;
import snake.render.PositionProvider;
import snake.render.SnakeRenderer;
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
    private boolean spaceDown;

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
        for (int code : new int[] {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT, KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D}) {
            bind(root, code, "move-" + code, () -> queueDirection(DirectionInput.directionForCode(code)));
        }
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "space-down");
        root.getActionMap().put("space-down", action(this::spacePressed));
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "space-up");
        root.getActionMap().put("space-up", action(this::spaceReleased));
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
    }

    // ===================================================================
    // 游戏循环
    // ===================================================================

    /**
     * 游戏主循环，由 {@link #gameTimer} 每 {@code FRAME_DELAY_MS}（16ms）调用一次。
     * <p>流程：
     * <ol>
     *   <li>修剪过期特效、刷新道具生成</li>
     *   <li>计算自上次移动以来经过的时间，更新移动进度（用于插值动画）</li>
     *   <li>若未达到移动间隔，只刷新视图（蛇身平滑滑动）</li>
     *   <li>若达到间隔，执行一次移动（{@link #moveOneCell}），溢出时间回滚至下一帧</li>
     * </ol>
     */
    void tick(double now) {
        if (!state.running || state.paused || state.gameOver) return;

        pruneEffects(now);
        refreshPowerUp(now);

        double interval = moveIntervalMs() / 1000.0;
        double elapsed = now - state.lastMoveAt;
        state.moveProgress = Math.min(1.0, elapsed / interval);

        if (elapsed < interval) {
            updateView(now);
            return;
        }

        // 溢出处理：超过移动间隔的部分回滚到 lastMoveAt，下一帧继续累积
        state.lastMoveAt = now - Math.max(0, elapsed - interval);
        state.moveProgress = 0.0;
        moveOneCell(now);
        updateView(now);
    }

    /**
     * 执行一次完整的"移动一格"操作，顺序与 Python 版一致：
     * <ol>
     *   <li>计算下一个蛇头位置（当前方向）</li>
     *   <li>撞墙 → 游戏结束（wall 死亡）</li>
     *   <li>无敌时撞障碍 → 撞碎障碍（不结束）</li>
     *   <li>撞自身或障碍且非无敌 → 游戏结束</li>
     *   <li>蛇头插入新位置（头部前进）</li>
     *   <li>吃到食物 → 计分 + 不删尾（蛇变长）；没吃到 → 删尾（保持长度）</li>
     *   <li>拾取道具 → 激活效果</li>
     *   <li>磁铁激活 → 吸引附近食物/道具，若自动拾取则额外加长</li>
     *   <li>应用方向队列中的下一个方向</li>
     * </ol>
     */
    private void moveOneCell(double now) {
        Point nextHead = state.snake.get(0).moved(state.direction);

        // 1. 撞墙检测
        if (!SnakeRules.pointInBounds(nextHead, GRID_WIDTH, GRID_HEIGHT)) {
            endGame(now, true);
            return;
        }

        // 2. 无敌时撞碎障碍物
        if (state.obstacles.contains(nextHead) && state.invincibleActive(now)) {
            destroyObstacle(nextHead, now);
        }

        // 3. 碰撞检测（撞自身或撞障碍，且非无敌）
        boolean hitsSelf = state.snake.subList(0, Math.max(0, state.snake.size() - 1)).contains(nextHead);
        boolean hitsObstacle = state.obstacles.contains(nextHead);
        if (SnakeRules.collisionIsFatal(hitsSelf, hitsObstacle, state.invincibleActive(now))) {
            state.moveProgress = 1.0;
            updateView(now);
            endGame(now, false);
            return;
        }

        // 4. 拾取道具
        PowerUpKind picked = null;
        if (state.powerUp != null && nextHead.equals(state.powerUp.position())) {
            picked = state.powerUp.kind();
        }

        // 5. 蛇头前进
        state.snake.add(0, nextHead);

        // 6. 吃食物：计分、刷新食物位置；没吃到则删尾
        boolean ate = nextHead.equals(state.food);
        Point removedTail = null;
        if (ate) {
            AudioBeep.playSound("eat", Toolkit.getDefaultToolkit()::beep);
            addScore(now);
            placeFood();
        } else {
            removedTail = state.snake.remove(state.snake.size() - 1);
        }

        // 7. 激活道具效果
        if (picked != null) {
            state.activateEffect(picked, now, POWER_UP_DURATION_SECONDS);
            state.powerUp = null;
        }

        // 8. 磁铁吸引力：将食物/道具拉向蛇头，若自动拾取则额外加长蛇身
        if (state.magnetActive(now)) {
            boolean collected = attractObjects(!ate);
            if (collected) {
                if (removedTail != null) state.snake.add(removedTail);
                AudioBeep.playSound("eat", Toolkit.getDefaultToolkit()::beep);
                addScore(now);
                placeFood();
            }
        }

        // 9. 应用方向队列中的下一个方向
        if (!state.turnQueue.isEmpty()) {
            state.direction = state.turnQueue.removeFirst();
        }
    }

    // ===================================================================
    // 开始 / 重置 / 暂停
    // ===================================================================

    void resetRound() {
        stopTimers();
        Point center = new Point(GRID_WIDTH / 2, GRID_HEIGHT / 2);
        List<Point> body = new ArrayList<>();
        for (int i = 0; i < START_LENGTH; i++) {
            body.add(new Point(center.x() - i, center.y()));
        }
        state.resetRound(body, now());
        deathTimer = null;
        placeFood();
        updateView(now());
    }

    void startRound() {
        if (state.running || state.gameOver) return;
        double now = now();
        state.running = true;
        state.paused = false;
        state.gameOver = false;
        state.lastMoveAt = now;
        state.moveProgress = 0.0;
        state.nextPowerUpSpawnAt = now + POWER_UP_SPAWN_INTERVAL_SECONDS;
        gameTimer.start();
        updateView(now);
    }

    /**
     * 处理方向队列逻辑，对应 Python 的 {@code queue_direction} 和 {@code _enqueue_turn}。
     * <p>规则：
     * <ul>
     *   <li>游戏未开始时，设置方向并立即开始</li>
     *   <li>队列为空时，检查是否可「立即转弯」（移动进度 ≤ 20%），否则入队</li>
     *   <li>队列非空时，检查是否与队尾反向，若是则修正队尾而非追加（支持快速掉头修正）</li>
     *   <li>队列满时（{@code TURN_QUEUE_LIMIT=3}），替换队尾而非追加</li>
     * </ul>
     */
    void queueDirection(Point requested) {
        if (requested == null || state.gameOver) return;

        if (!state.running) {
            state.direction = requested;
            startRound();
            return;
        }

        // Python 的 _enqueue_turn 逻辑
        if (state.turnQueue.isEmpty()) {
            Point effective = state.direction;
            if (requested.equals(effective)
                    || SnakeRules.directionsAreOppposites(requested.x(), requested.y(), effective.x(), effective.y())) {
                return;
            }
            // 立即转弯检查
            double now = now();
            syncMoveProgress(now);
            if (state.running && !state.paused && state.moveProgress <= IMMEDIATE_TURN_PROGRESS_LIMIT) {
                state.direction = requested;
                state.lastMoveAt = now;
                state.moveProgress = 0.0;
                updateView(now);
                return;
            }
        } else {
            Point effective = state.turnQueue.peekLast();
            Point previous = state.turnQueue.size() > 1
                    ? state.turnQueue.stream().skip(state.turnQueue.size() - 2).findFirst().orElse(state.direction)
                    : state.direction;
            if (requested.equals(effective)) return;
            if (SnakeRules.directionsAreOppposites(requested.x(), requested.y(), effective.x(), effective.y())) {
                if (requested.equals(previous)
                        || SnakeRules.directionsAreOppposites(requested.x(), requested.y(), previous.x(), previous.y())) {
                    return;
                }
                state.turnQueue.removeLast();
                state.turnQueue.addLast(requested);
                return;
            }
        }

        if (state.turnQueue.size() >= TURN_QUEUE_LIMIT) {
            Point prev = state.turnQueue.size() > 1
                    ? state.turnQueue.stream().skip(state.turnQueue.size() - 2).findFirst().orElse(state.direction)
                    : state.direction;
            if (requested.equals(prev)
                    || SnakeRules.directionsAreOppposites(requested.x(), requested.y(), prev.x(), prev.y())) {
                return;
            }
            state.turnQueue.removeLast();
            state.turnQueue.addLast(requested);
            return;
        }
        state.turnQueue.addLast(requested);
    }

    private void spacePressed() {
        if (spaceDown) return;
        spaceDown = true;
        if (state.gameOver) {
            resetRound();
            startRound();
            return;
        }
        if (!state.running) {
            startRound();
            return;
        }
        if (state.running && !state.paused && !state.boosting) {
            setBoosting(true);
        }
    }

    private void spaceReleased() {
        spaceDown = false;
        if (state.boosting) setBoosting(false);
    }

    private void setBoosting(boolean boosting) {
        if (state.boosting == boosting) return;
        double now = now();
        syncMoveProgress(now);
        double progress = state.moveProgress;
        state.boosting = boosting;
        state.lastMoveAt = now - progress * moveIntervalMs() / 1000.0;
        updateView(now);
    }

    private void togglePause() {
        if (!state.running || state.gameOver) return;
        double now = now();
        state.paused = !state.paused;
        setBoosting(false);
        if (state.paused) {
            gameTimer.stop();
        } else {
            state.lastMoveAt = now;
            state.moveProgress = 0.0;
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
        Set<Point> blocked = new HashSet<>(state.snake);
        blocked.addAll(state.obstacles);
        blocked.add(state.food);
        Point cell = randomFreeCell(blocked);
        state.powerUp = cell == null ? null : new PowerUp(
                random.nextBoolean() ? PowerUpKind.INVINCIBLE : PowerUpKind.MAGNET, cell);
    }

    // ===================================================================
    // 食物
    // ===================================================================

    private void placeFood() {
        Set<Point> blocked = new HashSet<>(state.snake);
        blocked.addAll(state.obstacles);
        if (state.powerUp != null) blocked.add(state.powerUp.position());
        Point cell = randomFreeCell(blocked);
        if (cell != null) state.food = cell;
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
        int newLevel = state.score / LEVEL_SCORE_STEP + 1;
        if (newLevel <= state.level) return;
        state.level = newLevel;
        state.levelNoticeUntil = now + LEVEL_NOTICE_SECONDS;
        growObstacles();
    }

    /** 在当前等级应生成的障碍物数量与已存在数量之间补齐，不超过 {@code MAX_OBSTACLES}。 */
    private void growObstacles() {
        int target = Math.min(MAX_OBSTACLES, (state.level - 1) * OBSTACLES_PER_LEVEL);
        while (state.obstacles.size() < target) {
            Set<Point> blocked = obstacleSpawnBlockers();
            Point cell = randomFreeCell(blocked);
            if (cell == null) return;
            state.obstacles.add(cell);
        }
    }

    private Set<Point> obstacleSpawnBlockers() {
        Set<Point> blocked = new HashSet<>(state.snake);
        blocked.addAll(state.obstacles);
        blocked.add(state.food);
        Point head = state.snake.get(0);
        for (int dy = -OBSTACLE_SAFE_RADIUS; dy <= OBSTACLE_SAFE_RADIUS; dy++) {
            for (int dx = -OBSTACLE_SAFE_RADIUS; dx <= OBSTACLE_SAFE_RADIUS; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= OBSTACLE_SAFE_RADIUS) {
                    Point p = new Point(head.x() + dx, head.y() + dy);
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
    private boolean attractObjects(boolean mayCollect) {
        Point head = state.snake.get(0);

        // 吸引食物
        Set<Point> blockedForFood = new HashSet<>(state.snake);
        blockedForFood.addAll(state.obstacles);
        if (state.powerUp != null) blockedForFood.add(state.powerUp.position());
        state.food = pullToward(state.food, head, blockedForFood);

        boolean collected = mayCollect
                && SnakeRules.manhattanDistance(state.food, head) <= MAGNET_AUTO_PICKUP_RADIUS;

        // 吸引道具
        if (state.powerUp != null) {
            Set<Point> blockedForPU = new HashSet<>(state.snake);
            blockedForPU.addAll(state.obstacles);
            if (!collected) blockedForPU.add(state.food);
            Point moved = pullToward(state.powerUp.position(), head, blockedForPU);
            state.powerUp = new PowerUp(state.powerUp.kind(), moved);
        }

        return collected;
    }

    /**
     * 将一个点向目标方向拉近一步。优先沿距离较大的轴移动（如 x 差≥y 差则先 x 后 y），
     * 若目标格被阻挡则尝试另一轴，两步都不可行则停止。
     * 对应 Python 的 {@code attracted_position}。
     */
    private Point pullToward(Point from, Point target, Set<Point> blocked) {
        Point current = from;
        for (int step = 0; step < MAGNET_PULL_STEPS
                && SnakeRules.manhattanDistance(current, target) <= MAGNET_RADIUS; step++) {
            int dx = Integer.compare(target.x(), current.x());
            int dy = Integer.compare(target.y(), current.y());
            // 优先沿距离大的轴移动
            Point[] orderedSteps = Math.abs(target.x() - current.x()) >= Math.abs(target.y() - current.y())
                    ? new Point[]{new Point(dx, 0), new Point(0, dy)}
                    : new Point[]{new Point(0, dy), new Point(dx, 0)};
            boolean moved = false;
            for (Point stepDir : orderedSteps) {
                if (stepDir.x() == 0 && stepDir.y() == 0) continue;
                Point candidate = current.moved(stepDir);
                if (SnakeRules.pointInBounds(candidate, GRID_WIDTH, GRID_HEIGHT) && !blocked.contains(candidate)) {
                    current = candidate;
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
        }
        return current;
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
        state.moveProgress = 0.0;
        state.deathStartedAt = now;
        state.deathUntil = now + DEATH_ANIMATION_SECONDS;
        state.deathDirection = state.direction;
        state.deathByWall = wall;

        store.updateRecord(playerName, state.score, state.snake.size());

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

    private void syncMoveProgress(double now) {
        if (!state.running || state.paused || state.gameOver) return;
        double elapsed = (now - state.lastMoveAt) * 1000;
        state.moveProgress = Math.min(1.0, Math.max(0.0, elapsed / moveIntervalMs()));
    }

    /**
     * 计算当前移动间隔（毫秒）。
     * <p>基础间隔 = {@code START_MOVE_INTERVAL_MS} - 额外段数×3 - 等级×7，
     * 下限 {@code MIN_MOVE_INTERVAL_MS}。加速时除 2（取整，下限 1）。
     * 对应 Python 的 {@code move_interval_ms}。
     */
    int moveIntervalMs() {
        int extra = Math.max(0, state.snake.size() - START_LENGTH);
        int levelBonus = Math.max(0, state.level - 1) * LEVEL_SPEEDUP_MS;
        int base = Math.max(MIN_MOVE_INTERVAL_MS,
                START_MOVE_INTERVAL_MS - extra * SPEEDUP_PER_SEGMENT_MS - levelBonus);
        return state.boosting ? Math.max(1, base / BOOST_MULTIPLIER) : base;
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
                name, state.score, state.highScore, state.level, state.snake.size()));

        if (state.gameOver) {
            statusLabel.setText("游戏结束 - 空格重新开始");
        } else if (state.paused) {
            statusLabel.setText("已暂停 - P 继续");
        } else if (state.boosting) {
            String effects = activeEffectText(now);
            statusLabel.setText(effects.isEmpty() ? "加速 x2 - 松开空格减速" : "加速 x2 - " + effects);
        } else {
            String effects = activeEffectText(now);
            if (!effects.isEmpty()) {
                statusLabel.setText(effects);
            } else if (state.running) {
                statusLabel.setText("方向键 / WASD 移动，按住空格加速");
            } else {
                statusLabel.setText("空格开始");
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
    public double[] interpolatedHeadXy() {
        double[] cell = interpolatedSegmentCell(0);
        return new double[]{cell[0] * CELL_SIZE, cell[1] * CELL_SIZE};
    }

    @Override
    public List<Point> magnetTargets() {
        List<Point> targets = new ArrayList<>();
        if (SnakeRules.manhattanDistance(state.food, state.snake.get(0)) <= MAGNET_RADIUS) {
            targets.add(state.food);
        }
        if (state.powerUp != null
                && SnakeRules.manhattanDistance(state.powerUp.position(), state.snake.get(0)) <= MAGNET_RADIUS) {
            targets.add(state.powerUp.position());
        }
        return targets;
    }

    @Override
    public double[] interpolatedSegmentCell(int index) {
        Point start = state.snake.get(index);
        Point target;
        if (index == 0) {
            target = start.moved(state.direction);
        } else {
            target = state.snake.get(index - 1);
        }
        double progress = Math.max(0.0, Math.min(1.0, state.moveProgress));
        double cx = start.x() + (target.x() - start.x()) * progress;
        double cy = start.y() + (target.y() - start.y()) * progress;
        return new double[]{cx, cy};
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
            List<LeaderboardStore.Entry> leaders = (state.gameOver || (!state.running && !state.paused))
                    ? store.topByLength(5) : List.of();
            LeaderboardStore.Entry playerEntry = (state.gameOver || (!state.running && !state.paused))
                    ? store.getRecord(playerName) : null;
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
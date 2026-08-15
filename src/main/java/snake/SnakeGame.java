package snake;

import static snake.constants.GameConfig.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

import snake.audio.AudioBeep;
import snake.input.DirectionInput;
import snake.model.*;
import snake.rules.SnakeRules;
import snake.state.GameState;
import snake.storage.LeaderboardStore;

/** Swing entry point for the Java port of the Snake game. */
public final class SnakeGame {
    private final GameState state = new GameState();
    private final LeaderboardStore store;
    private final Random random;
    private final JFrame frame;
    private final GamePanel board;
    private final JLabel scoreLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final javax.swing.Timer timer;
    private String playerName;
    private boolean spaceDown;

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
        configureLabel(scoreLabel, true);
        configureLabel(statusLabel, false);
        footer.add(scoreLabel, BorderLayout.WEST);
        footer.add(statusLabel, BorderLayout.EAST);
        frame.add(footer, BorderLayout.SOUTH);

        installKeys();
        timer = new javax.swing.Timer(FRAME_DELAY_MS, event -> tick(now()));
        timer.setCoalesce(true);
        resetRound();
    }

    private void configureLabel(JLabel label, boolean bold) {
        label.setForeground(bold ? DARK_THEME.text : DARK_THEME.mutedText);
        label.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, bold ? 14 : 12));
    }

    private void installKeys() {
        JRootPane root = frame.getRootPane();
        bind(root, KeyEvent.VK_ESCAPE, "quit", () -> frame.dispose());
        bind(root, KeyEvent.VK_P, "pause", this::togglePause);
        for (int code : new int[] {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT,
                KeyEvent.VK_RIGHT, KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D}) {
            bind(root, code, "move-" + code, () -> queueDirection(DirectionInput.directionForCode(code)));
        }
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "space-down");
        root.getActionMap().put("space-down", action(this::spacePressed));
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "space-up");
        root.getActionMap().put("space-up", action(this::spaceReleased));
    }

    private static void bind(JRootPane root, int code, String name, Runnable task) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(code, 0), name);
        root.getActionMap().put(name, action(task));
    }

    private static Action action(Runnable task) {
        return new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { task.run(); }
        };
    }

    private void requestPlayerName() {
        while (playerName == null || playerName.isBlank()) {
            String input = JOptionPane.showInputDialog(frame, "请输入玩家昵称（最多 20 个字符）：", "玩家昵称",
                    JOptionPane.QUESTION_MESSAGE);
            if (input == null) {
                frame.dispose();
                return;
            }
            playerName = LeaderboardStore.normalizePlayerName(input);
        }
    }

    void resetRound() {
        timerStop();
        Point center = new Point(GRID_WIDTH / 2, GRID_HEIGHT / 2);
        List<Point> body = new ArrayList<>();
        for (int i = 0; i < START_LENGTH; i++) body.add(new Point(center.x() - i, center.y()));
        state.resetRound(body, now());
        placeFood();
        updateView(now());
    }

    void startRound() {
        if (state.running || state.gameOver) return;
        double now = now();
        state.running = true;
        state.paused = false;
        state.lastMoveAt = now;
        state.nextPowerUpSpawnAt = now + POWER_UP_SPAWN_INTERVAL_SECONDS;
        timer.start();
        updateView(now);
    }

    boolean queueDirection(Point requested) {
        if (requested == null || state.gameOver) return false;
        Point effective = state.turnQueue.peekLast();
        if (effective == null) effective = state.direction;
        if (requested.equals(effective) || SnakeRules.directionsAreOppposites(
                requested.x(), requested.y(), effective.x(), effective.y())) return false;
        if (!state.running) {
            state.direction = requested;
            startRound();
            return true;
        }
        if (state.turnQueue.size() >= TURN_QUEUE_LIMIT) return false;
        state.turnQueue.addLast(requested);
        return true;
    }

    private void spacePressed() {
        if (spaceDown) return;
        spaceDown = true;
        if (state.gameOver) { resetRound(); startRound(); }
        else if (!state.running) startRound();
        state.boosting = state.running && !state.paused;
        updateView(now());
    }

    private void spaceReleased() {
        spaceDown = false;
        state.boosting = false;
        updateView(now());
    }

    private void togglePause() {
        if (!state.running || state.gameOver) return;
        state.paused = !state.paused;
        state.boosting = false;
        state.lastMoveAt = now();
        state.moveProgress = 0;
        if (state.paused) timer.stop(); else timer.start();
        updateView(now());
    }

    void tick(double now) {
        if (!state.running || state.paused || state.gameOver) return;
        pruneEffects(now);
        if (now >= state.nextPowerUpSpawnAt) {
            spawnPowerUp();
            state.nextPowerUpSpawnAt = now + POWER_UP_SPAWN_INTERVAL_SECONDS;
        }
        double interval = moveIntervalMs() / 1000.0;
        double elapsed = now - state.lastMoveAt;
        state.moveProgress = Math.min(1, elapsed / interval);
        if (elapsed < interval) { updateView(now); return; }
        state.lastMoveAt = now - Math.max(0, elapsed - interval);
        state.moveProgress = 0;
        moveOneCell(now);
        updateView(now);
    }

    private void moveOneCell(double now) {
        Point next = state.snake.get(0).moved(state.direction);
        if (!SnakeRules.pointInBounds(next, GRID_WIDTH, GRID_HEIGHT)) { endGame(now, true); return; }
        if (state.obstacles.contains(next) && state.invincibleActive(now)) destroyObstacle(next, now);
        boolean self = state.snake.subList(0, Math.max(0, state.snake.size() - 1)).contains(next);
        if (SnakeRules.collisionIsFatal(self, state.obstacles.contains(next), state.invincibleActive(now))) {
            endGame(now, false); return;
        }
        PowerUpKind picked = state.powerUp != null && state.powerUp.position().equals(next)
                ? state.powerUp.kind() : null;
        state.snake.add(0, next);
        boolean ate = next.equals(state.food);
        Point removedTail = null;
        if (ate) { AudioBeep.playSound("eat", Toolkit.getDefaultToolkit()::beep); addScore(now); placeFood(); }
        else removedTail = state.snake.remove(state.snake.size() - 1);
        if (picked != null) { state.activateEffect(picked, now, POWER_UP_DURATION_SECONDS); state.powerUp = null; }
        if (state.magnetActive(now) && attractObjects(!ate)) {
            if (removedTail != null) state.snake.add(removedTail);
            addScore(now);
            placeFood();
        }
        if (!state.turnQueue.isEmpty()) state.direction = state.turnQueue.removeFirst();
    }

    private void addScore(double now) {
        state.score++;
        if (state.score > state.highScore) { state.highScore = state.score; store.saveHighScore(state.highScore); }
        int level = state.score / LEVEL_SCORE_STEP + 1;
        if (level > state.level) {
            state.level = level;
            state.levelNoticeUntil = now + LEVEL_NOTICE_SECONDS;
            growObstacles();
        }
    }

    private void growObstacles() {
        int target = Math.min(MAX_OBSTACLES, (state.level - 1) * OBSTACLES_PER_LEVEL);
        while (state.obstacles.size() < target) {
            Set<Point> blocked = occupied();
            Point head = state.snake.get(0);
            for (int y = 0; y < GRID_HEIGHT; y++) for (int x = 0; x < GRID_WIDTH; x++) {
                Point p = new Point(x, y);
                if (SnakeRules.manhattanDistance(p, head) <= OBSTACLE_SAFE_RADIUS) blocked.add(p);
            }
            Point free = randomFreeCell(blocked);
            if (free == null) return;
            state.obstacles.add(free);
        }
    }

    private boolean attractObjects(boolean mayCollect) {
        Point head = state.snake.get(0);
        state.food = pullToward(state.food, head, occupiedWithout(state.food));
        boolean collect = mayCollect && SnakeRules.manhattanDistance(state.food, head) <= MAGNET_AUTO_PICKUP_RADIUS;
        if (state.powerUp != null) {
            Point moved = pullToward(state.powerUp.position(), head, occupiedWithout(state.powerUp.position()));
            state.powerUp = new PowerUp(state.powerUp.kind(), moved);
        }
        return collect;
    }

    private Point pullToward(Point from, Point target, Set<Point> blocked) {
        Point current = from;
        for (int i = 0; i < MAGNET_PULL_STEPS && SnakeRules.manhattanDistance(current, target) <= MAGNET_RADIUS; i++) {
            int dx = Integer.compare(target.x(), current.x());
            int dy = Integer.compare(target.y(), current.y());
            Point[] steps = Math.abs(target.x() - current.x()) >= Math.abs(target.y() - current.y())
                    ? new Point[] {new Point(dx, 0), new Point(0, dy)}
                    : new Point[] {new Point(0, dy), new Point(dx, 0)};
            boolean moved = false;
            for (Point step : steps) {
                if (step.equals(new Point(0, 0))) continue;
                Point candidate = current.moved(step);
                if (SnakeRules.pointInBounds(candidate, GRID_WIDTH, GRID_HEIGHT) && !blocked.contains(candidate)) {
                    current = candidate; moved = true; break;
                }
            }
            if (!moved) break;
        }
        return current;
    }

    private void destroyObstacle(Point point, double now) {
        state.obstacles.remove(point);
        state.wallBreakEffects.add(new WallBreakEffect(point, now, random.nextInt()));
        AudioBeep.playSound("break", Toolkit.getDefaultToolkit()::beep);
    }

    private void pruneEffects(double now) {
        state.wallBreakEffects.removeIf(effect -> now - effect.startedAt() > WALL_BREAK_DURATION_SECONDS);
    }

    private void spawnPowerUp() {
        Point cell = randomFreeCell(occupied());
        state.powerUp = cell == null ? null : new PowerUp(
                random.nextBoolean() ? PowerUpKind.INVINCIBLE : PowerUpKind.MAGNET, cell);
    }

    private void placeFood() {
        Point cell = randomFreeCell(occupiedWithout(state.food));
        if (cell != null) state.food = cell;
    }

    private Set<Point> occupied() {
        Set<Point> blocked = new HashSet<>(state.snake);
        blocked.addAll(state.obstacles);
        blocked.add(state.food);
        if (state.powerUp != null) blocked.add(state.powerUp.position());
        return blocked;
    }

    private Set<Point> occupiedWithout(Point point) {
        Set<Point> blocked = occupied();
        blocked.remove(point);
        return blocked;
    }

    Point randomFreeCell(Set<Point> blocked) {
        int total = GRID_WIDTH * GRID_HEIGHT;
        int start = random.nextInt(total);
        for (int offset = 0; offset < total; offset++) {
            int index = (start + offset) % total;
            Point point = new Point(index % GRID_WIDTH, index / GRID_WIDTH);
            if (!blocked.contains(point)) return point;
        }
        return null;
    }

    private void endGame(double now, boolean wall) {
        timer.stop();
        state.running = false;
        state.gameOver = true;
        state.deathStartedAt = now;
        state.deathUntil = now + DEATH_ANIMATION_SECONDS;
        state.deathDirection = state.direction;
        state.deathByWall = wall;
        store.updateRecord(playerName, state.score, state.snake.size());
        AudioBeep.playSound("death", Toolkit.getDefaultToolkit()::beep);
    }

    int moveIntervalMs() {
        int base = Math.max(MIN_MOVE_INTERVAL_MS, START_MOVE_INTERVAL_MS
                - Math.max(0, state.snake.size() - START_LENGTH) * SPEEDUP_PER_SEGMENT_MS
                - Math.max(0, state.level - 1) * LEVEL_SPEEDUP_MS);
        return state.boosting ? Math.max(1, base / BOOST_MULTIPLIER) : base;
    }

    private void updateView(double now) {
        String name = playerName == null ? "-" : playerName;
        scoreLabel.setText("玩家: " + name + "   分数: " + state.score + "   最高: " + state.highScore
                + "   等级: " + state.level + "   长度: " + state.snake.size());
        if (state.gameOver) statusLabel.setText("游戏结束 - 空格重新开始");
        else if (state.paused) statusLabel.setText("已暂停 - P 继续");
        else if (state.boosting) statusLabel.setText("加速 x2");
        else if (state.running) statusLabel.setText("方向键/WASD 移动，P 暂停");
        else statusLabel.setText("空格开始");
        board.repaint();
    }

    private void timerStop() { if (timer != null) timer.stop(); }
    private static double now() { return System.nanoTime() / 1_000_000_000.0; }

    public void show() {
        requestPlayerName();
        if (!frame.isDisplayable()) return;
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    GameState stateForTest() { return state; }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SnakeGame().show());
    }

    private final class GamePanel extends JPanel {
        GamePanel() {
            setPreferredSize(new Dimension(CANVAS_WIDTH, CANVAS_HEIGHT));
            setBackground(DARK_THEME.bg);
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawGrid(g);
            for (Point p : state.obstacles) drawCell(g, p, DARK_THEME.obstacle, 3);
            drawFood(g, state.food);
            if (state.powerUp != null) drawPowerUp(g, state.powerUp);
            drawSnake(g);
            if (state.levelNoticeUntil > now()) drawCentered(g, "LEVEL " + state.level, DARK_THEME.levelNotice, 34);
            if (state.gameOver) drawOverlay(g);
            g.dispose();
        }

        private void drawGrid(Graphics2D g) {
            g.setColor(DARK_THEME.grid);
            for (int x = 0; x <= CANVAS_WIDTH; x += CELL_SIZE) g.drawLine(x, 0, x, CANVAS_HEIGHT);
            for (int y = 0; y <= CANVAS_HEIGHT; y += CELL_SIZE) g.drawLine(0, y, CANVAS_WIDTH, y);
        }

        private void drawFood(Graphics2D g, Point p) {
            g.setColor(DARK_THEME.food);
            g.fillOval(p.x() * CELL_SIZE + 5, p.y() * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
        }

        private void drawPowerUp(Graphics2D g, PowerUp power) {
            g.setColor(DARK_THEME.powerUpColor(power.kind()));
            int x = power.position().x() * CELL_SIZE + 3, y = power.position().y() * CELL_SIZE + 3;
            g.fillRoundRect(x, y, CELL_SIZE - 6, CELL_SIZE - 6, 6, 6);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(DARK_THEME.powerUpSymbol(power.kind()), x + 5, y + 14);
        }

        private void drawSnake(Graphics2D g) {
            double t = now();
            Color body = state.invincibleActive(t)
                    ? DARK_THEME.invincibleFlash[(int) (t * 10) % DARK_THEME.invincibleFlash.length]
                    : DARK_THEME.snakeBody;
            for (int i = state.snake.size() - 1; i >= 0; i--) {
                drawCell(g, state.snake.get(i), i == 0 ? DARK_THEME.snakeHead : body, i == 0 ? 2 : 4);
            }
            Point head = state.snake.get(0);
            g.setColor(DARK_THEME.snakeEye);
            int hx = head.x() * CELL_SIZE, hy = head.y() * CELL_SIZE;
            g.fillOval(hx + 7, hy + 6, 4, 4); g.fillOval(hx + 14, hy + 6, 4, 4);
        }

        private void drawCell(Graphics2D g, Point p, Color color, int inset) {
            g.setColor(color);
            g.fillRoundRect(p.x() * CELL_SIZE + inset, p.y() * CELL_SIZE + inset,
                    CELL_SIZE - inset * 2, CELL_SIZE - inset * 2, 7, 7);
        }

        private void drawOverlay(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 165));
            g.fillRect(0, 0, getWidth(), getHeight());
            drawCentered(g, "游戏结束", Color.WHITE, 36);
            List<LeaderboardStore.Entry> leaders = store.topByLength(5);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(DARK_THEME.mutedText);
            int y = CANVAS_HEIGHT / 2 + 42;
            for (int i = 0; i < leaders.size(); i++) {
                LeaderboardStore.Entry e = leaders.get(i);
                String line = (i + 1) + ". " + e.name() + "  长度 " + e.bestLength() + "  分数 " + e.bestScore();
                g.drawString(line, (CANVAS_WIDTH - g.getFontMetrics().stringWidth(line)) / 2, y + i * 22);
            }
        }

        private void drawCentered(Graphics2D g, String text, Color color, int size) {
            g.setFont(new Font("SansSerif", Font.BOLD, size));
            g.setColor(color);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, (CANVAS_WIDTH - metrics.stringWidth(text)) / 2,
                    (CANVAS_HEIGHT + metrics.getAscent()) / 2 - 20);
        }
    }
}

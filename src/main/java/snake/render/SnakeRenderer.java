package snake.render;

import static snake.constants.GameConfig.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

import snake.constants.GameConfig;
import snake.model.Point;
import snake.model.PowerUp;
import snake.model.PowerUpKind;
import snake.model.WallBreakEffect;
import snake.state.GameState;
import snake.storage.LeaderboardStore;

/**
 * 完整的 Graphics2D 渲染器，对应 Python 版的 {@code SnakeRenderer}。
 * 接收 {@link PositionProvider} 回调来获取插值坐标，从而支持平滑动画。
 */
public class SnakeRenderer {

    private final GameConfig.Theme theme;
    private final int width;
    private final int height;

    public SnakeRenderer() {
        this.theme = GameConfig.DARK_THEME;
        this.width = CANVAS_WIDTH;
        this.height = CANVAS_HEIGHT;
    }

    // =========================================================================
    // 主入口
    // =========================================================================

    /**
     * 绘制当前帧的全部内容，对应 Python {@code draw()}。
     *
     * @param g      Graphics2D 上下文（已设置好剪辑区与抗锯齿）
     * @param state  游戏状态
     * @param now    当前单调时钟（秒）
     * @param pos    插值位置提供者
     * @param playerName 当前玩家昵称（用于排行榜高亮）
     * @param leaders 排行榜列表（仅在 overlay 使用）
     */
    public void draw(Graphics2D g, GameState state, double now,
                     PositionProvider pos, String playerName, List<LeaderboardStore.Entry> leaders) {
        drawGrid(g);
        if (state.magnetActive(now)) {
            drawMagnetRange(g, pos, now);
        }
        drawObstacles(g, state);
        drawWallBreakEffects(g, state, now);
        drawFood(g, state.food);
        drawPowerUp(g, state.powerUp);
        if (state.magnetActive(now)) {
            drawMagnetPullEffects(g, pos, now);
        }
        if (!state.snake.isEmpty()) {
            drawSnake(g, state, now, pos);
        }
        drawDeathAnimation(g, state, now);
        drawLevelNotice(g, state, now);
        if (!state.running || state.paused || state.gameOver) {
            drawOverlay(g, state, playerName, leaders);
        }
    }

    // =========================================================================
    // 网格
    // =========================================================================

    private void drawGrid(Graphics2D g) {
        g.setColor(theme.grid);
        for (int x = 0; x <= width; x += CELL_SIZE) {
            g.drawLine(x, 0, x, height);
        }
        for (int y = 0; y <= height; y += CELL_SIZE) {
            g.drawLine(0, y, width, y);
        }
    }

    // =========================================================================
    // 食物
    // =========================================================================

    private void drawFood(Graphics2D g, Point food) {
        g.setColor(theme.food);
        int pad = 5;
        int x = food.x() * CELL_SIZE + pad;
        int y = food.y() * CELL_SIZE + pad;
        g.fillOval(x, y, CELL_SIZE - pad * 2, CELL_SIZE - pad * 2);
    }

    // =========================================================================
    // 障碍物（矩形 + 两条对角 X 线）
    // =========================================================================

    private void drawObstacles(Graphics2D g, GameState state) {
        g.setStroke(new BasicStroke(1f));
        for (Point p : state.obstacles) {
            int pad = 3;
            int x = p.x() * CELL_SIZE + pad;
            int y = p.y() * CELL_SIZE + pad;
            int s = CELL_SIZE - pad * 2;
            g.setColor(theme.obstacle);
            g.fillRect(x, y, s, s);
            g.setColor(theme.obstacleEdge);
            g.drawRect(x, y, s, s);
            // 交叉线
            g.drawLine(x + 4, y + 5, x + s - 5, y + s - 4);
            g.setColor(theme.bg);
            g.drawLine(x + s - 4, y + 5, x + 5, y + s - 4);
        }
    }

    // =========================================================================
    // 墙体破碎特效（闪光圈 + 裂纹 + 碎片）
    // =========================================================================

    private void drawWallBreakEffects(Graphics2D g, GameState state, double now) {
        for (WallBreakEffect effect : state.wallBreakEffects) {
            double progress = (now - effect.startedAt()) / WALL_BREAK_DURATION_SECONDS;
            if (progress < 0 || progress > 1) continue;

            double cx = (effect.position().x() + 0.5) * CELL_SIZE;
            double cy = (effect.position().y() + 0.5) * CELL_SIZE;

            // 闪光圈
            double flashRadius = CELL_SIZE * (0.42 + progress * 0.55);
            int flashWidth = Math.max(1, (int) (4 - progress * 3));
            g.setStroke(new BasicStroke(flashWidth));
            g.setColor(theme.wallBreakFlash);
            g.drawOval((int) (cx - flashRadius), (int) (cy - flashRadius),
                    (int) (flashRadius * 2), (int) (flashRadius * 2));

            // 裂纹（6 条方向）
            double crackLength = CELL_SIZE * (0.22 + progress * 0.36);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(theme.wallBreakCrack);
            double[][] crackDirs = {
                    {-1.0, -0.25}, {-0.3, -1.0}, {0.7, -0.75},
                    {1.0, 0.25}, {0.25, 1.0}, {-0.8, 0.65}
            };
            for (double[] dir : crackDirs) {
                g.drawLine((int) cx, (int) cy,
                        (int) (cx + dir[0] * crackLength),
                        (int) (cy + dir[1] * crackLength));
            }

            // 碎片（6 个飞散矩形）
            double[][] fragDirs = {
                    {-1.0, -0.45}, {-0.55, -1.0}, {0.58, -0.95},
                    {1.0, 0.08}, {0.55, 0.95}, {-0.78, 0.76}
            };
            g.setColor(theme.wallBreakFragment);
            g.setStroke(new BasicStroke(1f));
            for (int idx = 0; idx < fragDirs.length; idx++) {
                double travel = (7 + ((effect.seed() + idx * 3) % 6) * 2.5) * progress;
                double size = Math.max(2.0, 5.5 * (1.0 - progress));
                double fx = cx + fragDirs[idx][0] * travel;
                double fy = cy + fragDirs[idx][1] * travel;
                g.fillRect((int) (fx - size), (int) (fy - size), (int) (size * 2), (int) (size * 2));
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    // =========================================================================
    // 道具（矩形 + 符号 I/M）
    // =========================================================================

    private void drawPowerUp(Graphics2D g, PowerUp powerUp) {
        if (powerUp == null) return;
        int pad = 4;
        int x = powerUp.position().x() * CELL_SIZE + pad;
        int y = powerUp.position().y() * CELL_SIZE + pad;
        int s = CELL_SIZE - pad * 2;
        g.setColor(theme.powerUpColor(powerUp.kind()));
        g.fillRoundRect(x, y, s, s, 6, 6);
        g.setColor(theme.text);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        String sym = theme.powerUpSymbol(powerUp.kind());
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (s - fm.stringWidth(sym)) / 2;
        int ty = y + (s + fm.getAscent()) / 2 - 2;
        g.drawString(sym, tx, ty);
    }

    // =========================================================================
    // 磁铁范围（双重脉冲圆环）
    // =========================================================================

    private void drawMagnetRange(Graphics2D g, PositionProvider pos, double now) {
        double[] head = pos.interpolatedHeadXy();
        int cx = (int) (head[0] + CELL_SIZE / 2.0);
        int cy = (int) (head[1] + CELL_SIZE / 2.0);
        int radius = MAGNET_RADIUS * CELL_SIZE;
        double pulse = (now * 3.5) % 1.0;

        g.setStroke(new BasicStroke(2f));
        g.setColor(theme.magnetRing);
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        double pulseR = radius * (0.72 + pulse * 0.28);
        g.setStroke(new BasicStroke(1f));
        g.setColor(theme.magnetRingPulse);
        g.drawOval((int) (cx - pulseR), (int) (cy - pulseR),
                (int) (pulseR * 2), (int) (pulseR * 2));
    }

    // =========================================================================
    // 磁铁吸引效果（高亮 + 光束 + 粒子）
    // =========================================================================

    private void drawMagnetPullEffects(Graphics2D g, PositionProvider pos, double now) {
        double[] head = pos.interpolatedHeadXy();
        int hx = (int) (head[0] + CELL_SIZE / 2.0);
        int hy = (int) (head[1] + CELL_SIZE / 2.0);

        for (Point target : pos.magnetTargets()) {
            int tx = target.x() * CELL_SIZE + CELL_SIZE / 2;
            int ty = target.y() * CELL_SIZE + CELL_SIZE / 2;
            drawTargetGlow(g, tx, ty, now);
            drawBeam(g, tx, ty, hx, hy, now);
        }
    }

    private void drawTargetGlow(Graphics2D g, int cx, int cy, double now) {
        double pulse = (now * 5.0) % 1.0;
        int radius = (int) (CELL_SIZE * (0.52 + pulse * 0.18));
        g.setStroke(new BasicStroke(2f));
        g.setColor(theme.magnetTargetGlow);
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawBeam(Graphics2D g, int tx, int ty, int hx, int hy, double now) {
        int dx = hx - tx;
        int dy = hy - ty;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 2) return;

        // 虚线束
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 4f}, 0f));
        g.setColor(theme.magnetBeam);
        g.drawLine(tx, ty, hx, hy);

        // 箭头（画三角形）
        double norm = dist;
        double ux = dx / norm;
        double uy = dy / norm;
        int arrowLen = 8;
        int arrowWid = 4;
        int ax = tx + (int) (ux * arrowLen);
        int ay = ty + (int) (uy * arrowLen);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(ax, ay);
        arrow.lineTo(ax + (int) (-ux * arrowWid - uy * arrowWid),
                ay + (int) (-uy * arrowWid + ux * arrowWid));
        arrow.lineTo(ax + (int) (-ux * arrowWid + uy * arrowWid),
                ay + (int) (-uy * arrowWid - ux * arrowWid));
        arrow.closePath();
        g.setColor(theme.magnetBeam);
        g.fill(arrow);

        // 粒子（5 个沿路径移动的点）
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 5; i++) {
            double p = (now * 2.8 + i * 0.2) % 1.0;
            int px = (int) (tx + dx * p);
            int py = (int) (ty + dy * p);
            int r = (int) (2.0 + p * 2.2);
            g.setColor(theme.magnetParticle);
            g.fillOval(px - r, py - r, r * 2, r * 2);
        }
    }

    // =========================================================================
    // 蛇身（连接线 + 方块 + 蛇头 + 蛇尾 + 舌头）
    // =========================================================================

    private void drawSnake(Graphics2D g, GameState state, double now, PositionProvider pos) {
        int n = state.snake.size();
        double[][] cells = new double[n][];
        for (int i = 0; i < n; i++) {
            cells[i] = pos.interpolatedSegmentCell(i);
        }

        // 视觉风格选择
        boolean invincible = state.invincibleActive(now);
        double effectProgress = invincible ? effectProgress(state.invincibleStartedAt, state.invincibleUntil, now) : 0;
        int thicknessBonus = invincible ? 1 + (int) (effectProgress * 4) : 0;
        double remaining = state.invincibleUntil - now;
        boolean warning = invincible && remaining <= INVINCIBLE_WARNING_SECONDS;
        boolean blink = warning && (int) (now * 10) % 2 == 0;

        Color headColor, bodyColor, outlineColor;
        if (invincible && !blink) {
            int phase = (int) (now * 13) % theme.invincibleFlash.length;
            headColor = theme.invincibleFlash[phase];
            bodyColor = theme.invincibleFlash[(phase + 2) % theme.invincibleFlash.length];
            outlineColor = theme.invincibleOutline;
        } else {
            headColor = theme.snakeHead;
            bodyColor = warning && blink ? theme.invincibleWarningStipple : theme.snakeBody;
            outlineColor = theme.snakeOutline;
        }

        int connectorWidth = CELL_SIZE - 8 + thicknessBonus * 2;
        int bodyInset = Math.max(1, 4 - thicknessBonus);
        int headInset = Math.max(1, 4 - thicknessBonus);
        int eyeBonus = thicknessBonus;

        // 连接线（从后到前）
        for (int i = n - 1; i > 0; i--) {
            Point corner = state.snake.get(i - 1);
            drawConnector(g, cells[i], corner, cells[i - 1], bodyColor, connectorWidth);
        }

        // 蛇尾
        if (n > 1) {
            drawTail(g, state, cells[n - 1], cells[n - 2], bodyColor, thicknessBonus);
        }

        // 蛇身方块
        for (int i = n - 2; i > 0; i--) {
            double cx = cells[i][0] * CELL_SIZE + bodyInset;
            double cy = cells[i][1] * CELL_SIZE + bodyInset;
            int s = CELL_SIZE - bodyInset * 2;
            g.setColor(bodyColor);
            g.fillRoundRect((int) cx, (int) cy, s, s, 6, 6);
        }

        // 蛇头
        drawHead(g, state, cells[0], headColor, outlineColor, thicknessBonus);
    }

    private void drawConnector(Graphics2D g, double[] current, Point corner, double[] prev,
                               Color color, int width) {
        double cx = cellCenter(current[0], current[1])[0];
        double cy = cellCenter(current[0], current[1])[1];
        double px = cellCenter(corner.x(), corner.y())[0];
        double py = cellCenter(corner.x(), corner.y())[1];
        double qx = cellCenter(prev[0], prev[1])[0];
        double qy = cellCenter(prev[0], prev[1])[1];

        // 隧道展开（与 Python _unwrap_pixel_near 逻辑一致）
        px = unwrapNear(px, cx, width);
        py = unwrapNear(py, cy, height);
        qx = unwrapNear(qx, px, width);
        qy = unwrapNear(qy, py, height);

        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(color);
        Path2D path = new Path2D.Double();
        path.moveTo(cx, cy);
        path.lineTo(px, py);
        path.lineTo(qx, qy);
        g.draw(path);
    }

    private void drawTail(Graphics2D g, GameState state, double[] tail, double[] next,
                          Color color, int thicknessBonus) {
        double cx = cellCenter(tail[0], tail[1])[0];
        double cy = cellCenter(tail[0], tail[1])[1];

        double dx = next[0] - tail[0];
        double dy = next[1] - tail[1];

        double tx, ty;
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) > 0.001) {
            tx = dx > 0 ? 1 : -1;
            ty = 0;
        } else if (Math.abs(dy) > 0.001) {
            tx = 0;
            ty = dy > 0 ? 1 : -1;
        } else {
            tx = state.direction.x();
            ty = state.direction.y();
        }

        double tipX = cx - tx * CELL_SIZE * 0.48;
        double tipY = cy - ty * CELL_SIZE * 0.48;
        double baseX = cx + tx * CELL_SIZE * 0.36;
        double baseY = cy + ty * CELL_SIZE * 0.36;
        double halfW = CELL_SIZE * 0.36 + thicknessBonus * 1.2;
        double px = -ty, py = tx;

        g.setColor(color);
        Path2D tailShape = new Path2D.Double();
        tailShape.moveTo(tipX, tipY);
        tailShape.lineTo(baseX + px * halfW, baseY + py * halfW);
        tailShape.lineTo(baseX + tx * CELL_SIZE * 0.12, baseY + ty * CELL_SIZE * 0.12);
        tailShape.lineTo(baseX - px * halfW, baseY - py * halfW);
        tailShape.closePath();
        g.fill(tailShape);
    }

    private void drawHead(Graphics2D g, GameState state, double[] cell,
                          Color color, Color outlineColor, int thicknessBonus) {
        double cx = cellCenter(cell[0], cell[1])[0];
        double cy = cellCenter(cell[0], cell[1])[1];
        int dx = state.direction.x();
        int dy = state.direction.y();
        int px = -dy;
        int py = dx;
        double sizeBonus = thicknessBonus * 0.8;

        double noseX = cx + dx * (CELL_SIZE * 0.55 + sizeBonus);
        double noseY = cy + dy * (CELL_SIZE * 0.55 + sizeBonus);
        double backX = cx - dx * (CELL_SIZE * 0.42 + sizeBonus * 0.5);
        double backY = cy - dy * (CELL_SIZE * 0.42 + sizeBonus * 0.5);
        double cheek = CELL_SIZE * 0.45 + sizeBonus;
        double backW = CELL_SIZE * 0.30 + sizeBonus * 0.7;

        // 头部多边形
        Path2D head = new Path2D.Double();
        head.moveTo(noseX, noseY);
        head.lineTo(cx + dx * CELL_SIZE * 0.12 + px * cheek,
                cy + dy * CELL_SIZE * 0.12 + py * cheek);
        head.lineTo(backX + px * backW, backY + py * backW);
        head.lineTo(backX, backY);
        head.lineTo(backX - px * backW, backY - py * backW);
        head.lineTo(cx + dx * CELL_SIZE * 0.12 - px * cheek,
                cy + dy * CELL_SIZE * 0.12 - py * cheek);
        head.closePath();

        g.setStroke(new BasicStroke(2 + thicknessBonus / 2));
        g.setColor(color);
        g.fill(head);
        g.setColor(outlineColor);
        g.draw(head);

        // 舌头
        drawTongue(g, noseX, noseY, dx, dy, px, py);

        // 眼睛
        double eyeFwd = CELL_SIZE * 0.18;
        double eyeSide = CELL_SIZE * 0.23;
        double pupilFwd = CELL_SIZE * 0.05;
        for (int side : new int[]{-1, 1}) {
            double ex = cx + dx * eyeFwd + px * eyeSide * side;
            double ey = cy + dy * eyeFwd + py * eyeSide * side;
            g.setColor(theme.snakeEye);
            g.fillOval((int) (ex - 3.8), (int) (ey - 3.8), 8, 8);
            g.setColor(theme.snakePupil);
            g.fillOval((int) (ex + dx * pupilFwd - 1.7), (int) (ey + dy * pupilFwd - 1.7), 4, 4);
        }

        // 鼻孔
        double nostFwd = CELL_SIZE * 0.40;
        double nostSide = CELL_SIZE * 0.12;
        for (int side : new int[]{-1, 1}) {
            double nx = cx + dx * nostFwd + px * nostSide * side;
            double ny = cy + dy * nostFwd + py * nostSide * side;
            g.setColor(theme.snakePupil);
            g.fillOval((int) (nx - 1.3), (int) (ny - 1.3), 3, 3);
        }
    }

    private void drawTongue(Graphics2D g, double noseX, double noseY, double dx, double dy,
                            double px, double py) {
        double startX = noseX + dx * 1.0;
        double startY = noseY + dy * 1.0;
        double forkX = noseX + dx * CELL_SIZE * 0.38;
        double forkY = noseY + dy * CELL_SIZE * 0.38;
        double tipX = noseX + dx * CELL_SIZE * 0.55;
        double tipY = noseY + dy * CELL_SIZE * 0.55;

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.snakeTongue);
        g.drawLine((int) startX, (int) startY, (int) forkX, (int) forkY);
        for (int side : new int[]{-1, 1}) {
            g.drawLine((int) forkX, (int) forkY,
                    (int) (forkX + px * CELL_SIZE * 0.10 * side),
                    (int) (forkY + py * CELL_SIZE * 0.10 * side));
        }
    }

    // =========================================================================
    // 死亡动画（X 眼 + 舌头 + 能量圈 + 闪烁框）
    // =========================================================================

    private void drawDeathAnimation(Graphics2D g, GameState state, double now) {
        if (state.deathUntil <= now || state.snake.isEmpty()) return;
        double progress = Math.min(1, Math.max(0, (now - state.deathStartedAt) / DEATH_ANIMATION_SECONDS));
        Point head = state.snake.get(0);
        double cx = (head.x() + 0.5) * CELL_SIZE;
        double cy = (head.y() + 0.5) * CELL_SIZE;

        if (state.deathByWall && state.deathDirection != null) {
            drawDeadWallFace(g, cx, cy, state.deathDirection);
        }

        // 能量圈
        double radius = CELL_SIZE * (0.35 + progress * 0.85);
        int strokeWidth = Math.max(1, (int) (5 - progress * 4));
        g.setStroke(new BasicStroke(strokeWidth));
        g.setColor(theme.wallBreakFlash);
        g.drawOval((int) (cx - radius), (int) (cy - radius),
                (int) (radius * 2), (int) (radius * 2));

        // 闪烁框
        if ((int) (now * 14) % 2 == 0) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(decode("#fef08a"));
            g.drawRect(head.x() * CELL_SIZE + 2, head.y() * CELL_SIZE + 2,
                    CELL_SIZE - 4, CELL_SIZE - 4);
        }
    }

    private void drawDeadWallFace(Graphics2D g, double cx, double cy, Point direction) {
        int dx = direction.x();
        int dy = direction.y();
        int px = -dy, py = dx;

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int side : new int[]{-1, 1}) {
            double ex = cx + dx * 3 + px * 5 * side;
            double ey = cy + dy * 3 + py * 5 * side;
            // X 眼
            g.setColor(theme.snakeHead);
            g.fillOval((int) (ex - 4), (int) (ey - 4), 8, 8);
            g.setColor(theme.snakePupil);
            g.drawLine((int) (ex - 3), (int) (ey - 3), (int) (ex + 3), (int) (ey + 3));
            g.drawLine((int) (ex + 3), (int) (ey - 3), (int) (ex - 3), (int) (ey + 3));
        }

        // 舌头
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(theme.snakeTongue);
        double tongueStartX = cx + dx * 5;
        double tongueStartY = cy + dy * 5;
        double forkX = cx + dx * 9;
        double forkY = cy + dy * 9;
        g.drawLine((int) tongueStartX, (int) tongueStartY, (int) forkX, (int) forkY);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int side : new int[]{-1, 1}) {
            g.drawLine((int) forkX, (int) forkY,
                    (int) (forkX - dx * 2 + px * 3 * side),
                    (int) (forkY - dy * 2 + py * 3 * side));
        }
    }

    // =========================================================================
    // 等级提示
    // =========================================================================

    private void drawLevelNotice(Graphics2D g, GameState state, double now) {
        if (state.levelNoticeUntil <= now || !state.running) return;
        drawCenteredText(g, "Level " + state.level, theme.levelNotice, 16, 28);
    }

    // =========================================================================
    // 覆盖层（暂停 / 欢迎 / 游戏结束 + 排行榜）
    // =========================================================================

    private void drawOverlay(Graphics2D g, GameState state, String playerName, List<LeaderboardStore.Entry> leaders) {
        // 半透明遮罩
        g.setColor(new Color(0, 0, 0, 165));
        g.fillRect(0, 0, width, height);

        String message, hint;
        if (state.gameOver) {
            message = "游戏结束";
            hint = "分数 " + state.score + "  最高 " + state.highScore + "  空格重新开始";
        } else if (state.paused) {
            message = "已暂停";
            hint = "P 继续";
        } else {
            message = "Snake";
            hint = "空格开始";
        }

        // 标题
        drawCenteredText(g, message, theme.text, 26, height / 2 - 20);
        drawCenteredText(g, hint, theme.mutedText, 12, height / 2 + 18);

        // 排行榜（欢迎页 + 游戏结束页都显示）
        if (!state.running && !state.paused) {
            int startY = height / 2 + 62;
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(theme.levelNotice);
            String title = "体型榜单";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, (width - fm.stringWidth(title)) / 2, startY);

            if (leaders == null || leaders.isEmpty()) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g.setColor(theme.mutedText);
                String empty = "暂无纪录，开始第一局吧！";
                fm = g.getFontMetrics();
                g.drawString(empty, (width - fm.stringWidth(empty)) / 2, startY + 25);
            } else {
                // 找出当前玩家是否在 top 5 中
                boolean currentInTop5 = false;
                for (int i = 0; i < Math.min(leaders.size(), 5); i++) {
                    if (leaders.get(i).name().equals(playerName)) {
                        currentInTop5 = true;
                        break;
                    }
                }

                // 合并列表：top 5 + 当前玩家（若不在 top 5 中）
                List<LeaderboardStore.Entry> displayList = new java.util.ArrayList<>();
                for (int i = 0; i < Math.min(leaders.size(), 5); i++) {
                    displayList.add(leaders.get(i));
                }
                if (!currentInTop5 && playerName != null && !playerName.isEmpty()) {
                    // 查找当前玩家的记录
                    for (LeaderboardStore.Entry e : leaders) {
                        if (e.name().equals(playerName)) {
                            displayList.add(e);
                            break;
                        }
                    }
                }

                for (int i = 0; i < displayList.size(); i++) {
                    LeaderboardStore.Entry entry = displayList.get(i);
                    String prefix = (i < 5 ? (i + 1) : "-") + ". ";
                    String line = prefix + entry.name() + "  长度 " + entry.bestLength()
                            + "  分数 " + entry.bestScore();
                    boolean isCurrent = entry.name().equals(playerName);
                    g.setFont(new Font("SansSerif", isCurrent ? Font.BOLD : Font.PLAIN, 11));
                    g.setColor(isCurrent ? theme.text : theme.mutedText);
                    fm = g.getFontMetrics();
                    g.drawString(line, (width - fm.stringWidth(line)) / 2, startY + 23 * (i + 1));
                }
            }
        }
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private void drawCenteredText(Graphics2D g, String text, Color color, int fontSize, int yOffset) {
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2, yOffset + fm.getAscent());
    }

    private double[] cellCenter(double cellX, double cellY) {
        return new double[]{(cellX + 0.5) * CELL_SIZE, (cellY + 0.5) * CELL_SIZE};
    }

    private double unwrapNear(double value, double reference, double size) {
        if (value - reference > size / 2) return value - size;
        if (value - reference < -size / 2) return value + size;
        return value;
    }

    private double effectProgress(double startedAt, double until, double now) {
        if (startedAt <= 0 || until <= startedAt) return 1.0;
        return Math.min(1.0, Math.max(0.0, (now - startedAt) / POWER_UP_DURATION_SECONDS));
    }

    private static Color decode(String hex) {
        return Color.decode(hex);
    }
}
package snake.render;

import static snake.constants.GameConfig.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
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

    /** 缓存常用绘制资源，避免每帧重新分配。 */
    private static final Font FONT_BOLD_26 = new Font("SansSerif", Font.BOLD, 26);
    private static final Font FONT_BOLD_16 = new Font("SansSerif", Font.BOLD, 16);
    private static final Font FONT_BOLD_13 = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_BOLD_12 = new Font("SansSerif", Font.BOLD, 12);
    private static final Font FONT_BOLD_11 = new Font("SansSerif", Font.BOLD, 11);
    private static final Font FONT_PLAIN_11 = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font FONT_PLAIN_10 = new Font("SansSerif", Font.PLAIN, 10);

    private static final BasicStroke STROKE_1 = new BasicStroke(1f);
    private static final BasicStroke STROKE_2 = new BasicStroke(2f);
    private static final BasicStroke STROKE_ROUND_2 =
            new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke STROKE_ROUND_3 =
            new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke STROKE_BEAM =
            new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{5f, 4f}, 0f);

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
                     PositionProvider pos, String playerName,
                     List<LeaderboardStore.Entry> leaders,
                     LeaderboardStore.Entry currentPlayerEntry) {
        drawBackground(g);
        drawGrid(g);
        if (state.magnetActive(now)) {
            drawMagnetRange(g, pos, now);
        }
        drawObstacles(g, state);
        drawWallBreakEffects(g, state, now);
        for (Point f : state.foods) drawFood(g, f);
        drawPowerUp(g, state.powerUp);
        if (state.magnetActive(now)) {
            drawMagnetPullEffects(g, pos, now);
        }
        if (!state.body.isEmpty()) {
            drawSnake(g, state, now, pos);
        }
        drawDeathAnimation(g, state, now);
        drawLevelNotice(g, state, now);
        if (!state.running || state.paused || state.gameOver) {
            drawOverlay(g, state, playerName, leaders, currentPlayerEntry);
        }
    }

    // =========================================================================
    // 背景（霓光夜园：径向渐变深底）
    // =========================================================================

    private void drawBackground(Graphics2D g) {
        Point2D center = new Point2D.Double(width / 2.0, height / 2.0);
        float radius = (float) Math.max(width, height) * 0.75f;
        RadialGradientPaint bg = new RadialGradientPaint(
                center, radius,
                new float[]{0f, 1f},
                new Color[]{theme.bgGlow, theme.bgDeep});
        g.setPaint(bg);
        g.fillRect(0, 0, width, height);
        g.setPaint(null);
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
        int pad = 4;
        int size = CELL_SIZE - pad * 2;
        int x = food.x() * CELL_SIZE + pad;
        int y = food.y() * CELL_SIZE + pad;
        int cx = x + size / 2;
        int cy = y + size / 2;
        // 玻璃果实：亮心 → 暗边 的径向渐变 + 高光点
        RadialGradientPaint gp = new RadialGradientPaint(
                new Point2D.Double(cx - size * 0.22, cy - size * 0.24), size * 0.95f,
                new float[]{0f, 1f},
                new Color[]{theme.foodLight, theme.food});
        g.setPaint(gp);
        g.fillOval(x, y, size, size);
        g.setPaint(null);
        g.setColor(new Color(255, 255, 255, 170));
        g.fillOval((int) (cx - size * 0.28), (int) (cy - size * 0.3), size / 4, size / 4);
    }

    // =========================================================================
    // 障碍物（矩形 + 两条对角 X 线）
    // =========================================================================

    private void drawObstacles(Graphics2D g, GameState state) {
        g.setStroke(STROKE_1);
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
            g.setStroke(STROKE_ROUND_2);
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
            g.setStroke(STROKE_1);
            for (int idx = 0; idx < fragDirs.length; idx++) {
                double travel = (7 + Math.floorMod(effect.seed() + idx * 3, 6) * 2.5) * progress;
                double size = Math.max(2.0, 5.5 * (1.0 - progress));
                double fx = cx + fragDirs[idx][0] * travel;
                double fy = cy + fragDirs[idx][1] * travel;
                g.fillRect((int) (fx - size), (int) (fy - size), (int) (size * 2), (int) (size * 2));
            }
        }
        g.setStroke(STROKE_1);
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
        g.setFont(FONT_BOLD_11);
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
        double[] head = pos.bodyPoints().get(0);
        int cx = (int) head[0];
        int cy = (int) head[1];
        int radius = MAGNET_RADIUS * CELL_SIZE;
        double pulse = (now * 3.5) % 1.0;

        // 外发光晕（霓光）
        g.setStroke(new BasicStroke(7));
        g.setColor(withAlpha(theme.magnetRing, 55));
        g.drawOval(cx - radius - 3, cy - radius - 3, (radius + 3) * 2, (radius + 3) * 2);

        g.setStroke(STROKE_2);
        g.setColor(theme.magnetRing);
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);

        double pulseR = radius * (0.72 + pulse * 0.28);
        g.setStroke(STROKE_1);
        g.setColor(theme.magnetRingPulse);
        g.drawOval((int) (cx - pulseR), (int) (cy - pulseR),
                (int) (pulseR * 2), (int) (pulseR * 2));
    }

    // =========================================================================
    // 磁铁吸引效果（高亮 + 光束 + 粒子）
    // =========================================================================

    private void drawMagnetPullEffects(Graphics2D g, PositionProvider pos, double now) {
        double[] head = pos.bodyPoints().get(0);
        int hx = (int) head[0];
        int hy = (int) head[1];

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
        g.setStroke(STROKE_2);
        g.setColor(theme.magnetTargetGlow);
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawBeam(Graphics2D g, int tx, int ty, int hx, int hy, double now) {
        int dx = hx - tx;
        int dy = hy - ty;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 2) return;

        // 虚线束
        g.setStroke(STROKE_BEAM);
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
        g.setStroke(STROKE_1);
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
        java.util.List<double[]> pts = pos.bodyPoints();
        int n = pts.size();
        if (n == 0) return;

        boolean invincible = state.invincibleActive(now);
        int thicknessBonus = invincible ? 2 : 0;
        double remaining = state.invincibleUntil - now;
        boolean warning = invincible && remaining <= INVINCIBLE_WARNING_SECONDS;
        boolean blink = warning && (int) (now * 10) % 2 == 0;
        boolean dead = state.deathUntil > now;
        boolean magnet = state.magnetActive(now) && !invincible;

        // 颜色方案
        Color headColor, bodyColor, outlineColor;
        if (dead) {
            headColor = theme.snakeDeadBody;
            bodyColor = theme.snakeDeadBody;
            outlineColor = theme.snakeDeadOutline;
        } else if (invincible && !blink) {
            int phase = (int) (now * 13) % theme.invincibleFlash.length;
            headColor = theme.invincibleFlash[phase];
            bodyColor = theme.invincibleFlash[(phase + 2) % theme.invincibleFlash.length];
            outlineColor = theme.invincibleOutline;
        } else if (magnet) {
            headColor = theme.magnetPowerUp;
            bodyColor = theme.magnetPowerUp;
            outlineColor = theme.magnetRing;
        } else {
            headColor = theme.snakeHead;
            bodyColor = theme.snakeBody;
            outlineColor = theme.snakeOutlineDark;
        }

        // 渐变端点：头亮 → 尾暗
        Color bodyBright, bodyDark;
        if (dead) {
            bodyBright = theme.snakeDeadBody;
            bodyDark = theme.snakeDeadBodyDark;
        } else if (magnet) {
            bodyBright = theme.magnetRingPulse;
            bodyDark = theme.magnetRing;
        } else if (invincible && !blink) {
            bodyBright = headColor;
            bodyDark = bodyColor;
        } else {
            bodyBright = theme.snakeBodyBright;
            bodyDark = theme.snakeBodyDark;
        }

        int beadSize = CELL_SIZE - 6 + thicknessBonus * 2;

        // 蛇身玻璃珠：从尾到头画（头压在最上）；尾部 2 颗渐小形成尾尖
        for (int i = n - 1; i >= 1; i--) {
            double t = (double) i / Math.max(1, n - 1);
            Color c = lerp(bodyBright, bodyDark, t);
            double[] p = pts.get(i);
            int size = beadSize;
            if (i == n - 1) {
                size = (int) (beadSize * 0.6);
            } else if (i == n - 2) {
                size = (int) (beadSize * 0.8);
            }
            drawBead(g, p[0], p[1], size, c, outlineColor);
        }

        // 蛇头
        drawHead(g, state, pts.get(0), headColor, outlineColor, thicknessBonus, dead);
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

    /** 绘制蛇头玻璃珠 + 眼睛 + 鼻孔 + 舌头。朝向由 state.heading 决定；dead 时画 X 眼。 */
    private void drawHead(Graphics2D g, GameState state, double[] cell,
                          Color color, Color outlineColor, int thicknessBonus, boolean dead) {
        double cx = cell[0];
        double cy = cell[1];
        double dx = Math.cos(state.heading);
        double dy = Math.sin(state.heading);
        double px = -dy, py = dx;
        double sizeBonus = thicknessBonus * 0.8;
        int headSize = CELL_SIZE + (int) (sizeBonus * 2);
        double r = headSize / 2.0;

        // 头部玻璃珠（描边底 + 主体 + 高光）
        g.setColor(outlineColor);
        g.fillOval((int) (cx - r - 1.5), (int) (cy - r - 1.5), headSize + 3, headSize + 3);
        g.setColor(color);
        g.fillOval((int) (cx - r), (int) (cy - r), headSize, headSize);
        g.setColor(new Color(255, 255, 255, 120));
        g.fillOval((int) (cx - r * 0.42), (int) (cy - r * 0.46),
                (int) (headSize * 0.34), (int) (headSize * 0.28));

        // 舌头（从头部前方伸出）
        double noseX = cx + dx * r;
        double noseY = cy + dy * r;
        drawTongue(g, noseX, noseY, dx, dy, px, py);

        if (dead) {
            // X 形眼睛（暗底圆 + 白色交叉线）
            double eyeFwd = r * 0.25, eyeSide = r * 0.46;
            for (int side : new int[]{-1, 1}) {
                double ex = cx + dx * eyeFwd + px * eyeSide * side;
                double ey = cy + dy * eyeFwd + py * eyeSide * side;
                g.setColor(theme.snakeDeadOutline);
                g.fillOval((int) (ex - 4), (int) (ey - 4), 8, 8);
                g.setColor(theme.text);
                g.drawLine((int) (ex - 3), (int) (ey - 3), (int) (ex + 3), (int) (ey + 3));
                g.drawLine((int) (ex + 3), (int) (ey - 3), (int) (ex - 3), (int) (ey + 3));
            }
        } else {
            // 活眼：白底 + 青色虹膜 + 黑瞳 + 白色 catchlight
            double eyeFwd = r * 0.18, eyeSide = r * 0.46;
            double pupilFwd = r * 0.05;
            for (int side : new int[]{-1, 1}) {
                double ex = cx + dx * eyeFwd + px * eyeSide * side;
                double ey = cy + dy * eyeFwd + py * eyeSide * side;
                g.setColor(theme.snakeEye);
                g.fillOval((int) (ex - 4.5), (int) (ey - 4.5), 9, 9);
                g.setColor(theme.snakeIris);
                g.fillOval((int) (ex - 3.2), (int) (ey - 3.2), 7, 7);
                g.setColor(theme.snakePupil);
                g.fillOval((int) (ex + dx * pupilFwd - 1.8), (int) (ey + dy * pupilFwd - 1.8), 4, 4);
                g.setColor(new Color(255, 255, 255, 220));
                g.fillOval((int) (ex - 1.5), (int) (ey - 1.8), 2, 2);
            }
            // 鼻孔
            double nostFwd = r * 0.55, nostSide = r * 0.28;
            for (int side : new int[]{-1, 1}) {
                double nx = cx + dx * nostFwd + px * nostSide * side;
                double ny = cy + dy * nostFwd + py * nostSide * side;
                g.setColor(outlineColor);
                g.fillOval((int) (nx - 1.3), (int) (ny - 1.3), 3, 3);
            }
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

        g.setStroke(STROKE_ROUND_2);
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
        if (state.deathUntil <= now || state.body.isEmpty()) return;
        double progress = Math.min(1, Math.max(0, (now - state.deathStartedAt) / DEATH_ANIMATION_SECONDS));
        double[] head = state.body.get(0);
        double cx = head[0];
        double cy = head[1];

        // 红色冲击波环（向外扩散、描边渐细）
        double radius = CELL_SIZE * (0.4 + progress * 0.9);
        int strokeWidth = Math.max(1, (int) (6 - progress * 5));
        g.setStroke(new BasicStroke(strokeWidth));
        g.setColor(theme.deathShockwave);
        g.drawOval((int) (cx - radius), (int) (cy - radius),
                (int) (radius * 2), (int) (radius * 2));

        // 闪烁框（围绕头部所在格）
        if ((int) (now * 14) % 2 == 0) {
            g.setStroke(STROKE_2);
            g.setColor(theme.deathShockwave);
            int hx = (int) (head[0] / CELL_SIZE) * CELL_SIZE;
            int hy = (int) (head[1] / CELL_SIZE) * CELL_SIZE;
            g.drawRect(hx + 2, hy + 2, CELL_SIZE - 4, CELL_SIZE - 4);
        }
    }

    private void drawDeadWallFace(Graphics2D g, double cx, double cy, Point direction) {
        int dx = direction.x();
        int dy = direction.y();
        int px = -dy, py = dx;

        g.setStroke(STROKE_ROUND_2);
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
        g.setStroke(STROKE_ROUND_3);
        g.setColor(theme.snakeTongue);
        double tongueStartX = cx + dx * 5;
        double tongueStartY = cy + dy * 5;
        double forkX = cx + dx * 9;
        double forkY = cy + dy * 9;
        g.drawLine((int) tongueStartX, (int) tongueStartY, (int) forkX, (int) forkY);
        g.setStroke(STROKE_ROUND_2);
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

    private void drawOverlay(Graphics2D g, GameState state, String playerName,
                             List<LeaderboardStore.Entry> leaders,
                             LeaderboardStore.Entry currentPlayerEntry) {
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
            g.setFont(FONT_BOLD_13);
            g.setColor(theme.levelNotice);
            String title = "体型榜单";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, (width - fm.stringWidth(title)) / 2, startY);

            if (leaders == null || leaders.isEmpty()) {
                // 无纪录时仍显示当前玩家
                if (currentPlayerEntry != null) {
                    showSingleEntry(g, startY, 1, currentPlayerEntry, playerName, true);
                } else {
                    g.setFont(FONT_PLAIN_10);
                    g.setColor(theme.mutedText);
                    String empty = "暂无纪录，开始第一局吧！";
                    fm = g.getFontMetrics();
                    g.drawString(empty, (width - fm.stringWidth(empty)) / 2, startY + 25);
                }
            } else {
                // 判断当前玩家是否在 top 5 中
                boolean currentInTop5 = false;
                for (int i = 0; i < Math.min(leaders.size(), 5); i++) {
                    if (leaders.get(i).name().equals(playerName)) {
                        currentInTop5 = true;
                        break;
                    }
                }

                // 显示 top 5
                int row = 1;
                for (int i = 0; i < Math.min(leaders.size(), 5); i++, row++) {
                    LeaderboardStore.Entry entry = leaders.get(i);
                    boolean isCurrent = entry.name().equals(playerName);
                    showSingleEntry(g, startY, row, entry, playerName, isCurrent);
                }

                // 当前玩家不在 top 5 时追加显示
                if (!currentInTop5 && currentPlayerEntry != null) {
                    // 检查是否与 top 5 最后一条重复（避免 MySQL 延迟导致重复）
                    boolean alreadyShown = false;
                    for (int i = 0; i < Math.min(leaders.size(), 5); i++) {
                        if (leaders.get(i).name().equals(currentPlayerEntry.name())) {
                            alreadyShown = true;
                            break;
                        }
                    }
                    if (!alreadyShown) {
                        String prefix = "-. ";
                        String line = prefix + currentPlayerEntry.name()
                                + "  长度 " + currentPlayerEntry.bestLength()
                                + "  分数 " + currentPlayerEntry.bestScore();
                        g.setFont(FONT_BOLD_11);
                        g.setColor(theme.text);
                        fm = g.getFontMetrics();
                        g.drawString(line, (width - fm.stringWidth(line)) / 2, startY + 23 * row);
                    }
                }
            }
        }
    }

    private void showSingleEntry(Graphics2D g, int startY, int row,
                                  LeaderboardStore.Entry entry, String playerName, boolean isCurrent) {
        String line = row + ". " + entry.name() + "  长度 " + entry.bestLength()
                + "  分数 " + entry.bestScore();
        g.setFont(isCurrent ? FONT_BOLD_11 : FONT_PLAIN_11);
        g.setColor(isCurrent ? theme.text : theme.mutedText);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(line, (width - fm.stringWidth(line)) / 2, startY + 23 * row);
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /** 在画布水平居中位置绘制文字，yOffset 为相对于画布顶部的偏移。 */
    private void drawCenteredText(Graphics2D g, String text, Color color, int fontSize, int yOffset) {
        g.setFont(fontForSize(fontSize));
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2, yOffset + fm.getAscent());
    }

    /** 返回缓存的加粗字体；未缓存的字号临时创建。 */
    private static Font fontForSize(int size) {
        return switch (size) {
            case 26 -> FONT_BOLD_26;
            case 16 -> FONT_BOLD_16;
            case 13 -> FONT_BOLD_13;
            case 12 -> FONT_BOLD_12;
            case 11 -> FONT_BOLD_11;
            default -> new Font("SansSerif", Font.BOLD, size);
        };
    }

    /** 返回网格单元格中心点的像素坐标。 */
    private double[] cellCenter(double cellX, double cellY) {
        return new double[]{(cellX + 0.5) * CELL_SIZE, (cellY + 0.5) * CELL_SIZE};
    }

    /**
     * 隧道展开：当两个像素坐标跨越画布边界时，将其中一个平移一个画布宽度/高度，
     * 使连接线正确显示而非横跨整个画布。对应 Python {@code _unwrap_pixel_near}。
     */
    private double unwrapNear(double value, double reference, double size) {
        if (value - reference > size / 2) return value - size;
        if (value - reference < -size / 2) return value + size;
        return value;
    }

    /** 计算道具效果从开始到现在的进度 [0, 1]，用于无敌时的加粗动画。 */
    private double effectProgress(double startedAt, double until, double now) {
        if (startedAt <= 0 || until <= startedAt) return 1.0;
        return Math.min(1.0, Math.max(0.0, (now - startedAt) / POWER_UP_DURATION_SECONDS));
    }

    /** 绘制一颗玻璃珠：描边底 + 主体 + 左上玻璃高光。 */
    private void drawBead(Graphics2D g, double cx, double cy, int size, Color color, Color outline) {
        double r = size / 2.0;
        g.setColor(outline);
        g.fillOval((int) (cx - r - 1), (int) (cy - r - 1), size + 2, size + 2);
        g.setColor(color);
        g.fillOval((int) (cx - r), (int) (cy - r), size, size);
        g.setColor(new Color(255, 255, 255, 110));
        g.fillOval((int) (cx - r * 0.45), (int) (cy - r * 0.5),
                (int) (size * 0.34), (int) (size * 0.28));
    }

    /** 线性插值两个颜色，t∈[0,1]。 */
    private static Color lerp(Color a, Color b, double t) {
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int gg = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, gg, bl);
    }

    /** 给颜色叠加 alpha（0–255）。 */
    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color decode(String hex) {
        return Color.decode(hex);
    }
}
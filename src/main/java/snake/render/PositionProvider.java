package snake.render;

import java.util.List;

import snake.model.Point;

/**
 * 渲染时从游戏控制器拉取连续坐标的回调集合（自由移动版）。
 * 蛇身为像素珠串，每帧即真实位置，无需插值。
 */
public interface PositionProvider {

    /** 蛇身珠子像素坐标列表（{x, y}，索引 0 为蛇头），尾到头顺序不限但 0 必须是头。 */
    List<double[]> bodyPoints();

    /** 蛇头航向角（弧度，0 = 向右，屏幕坐标顺时针为正）。 */
    double heading();

    /** 磁铁吸引目标（食物 / 道具）的网格点列表。 */
    List<Point> magnetTargets();
}

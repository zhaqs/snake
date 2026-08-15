package snake.render;

import snake.model.Point;

/**
 * 渲染时从游戏控制器拉取插值位置的回调集合，对应 Python {@code SnakeRenderer.draw}
 * 接收的几个 Callable 参数（interpolated_head_xy / magnet_targets / interpolated_segment_cell）。
 */
public interface PositionProvider {

    /** 蛇头插值后的像素坐标 {px, py}。 */
    double[] interpolatedHeadXy();

    /** 磁铁吸引目标（食物 / 道具）的网格点列表。 */
    java.util.List<Point> magnetTargets();

    /** 第 index 段蛇身插值后的像素索引坐标 {cx, cy}。 */
    double[] interpolatedSegmentCell(int index);
}

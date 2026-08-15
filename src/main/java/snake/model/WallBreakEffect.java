package snake.model;

/**
 * 墙体破碎特效，对应 Python {@code WallBreakEffect}。
 * {@code seed} 用于决定碎片的确定性位移，避免逐帧抖动。
 */
public record WallBreakEffect(Point position, double startedAt, int seed) {
}

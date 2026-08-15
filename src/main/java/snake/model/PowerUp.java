package snake.model;

/** 场上道具，对应 Python {@code PowerUp}。 */
public record PowerUp(PowerUpKind kind, Point position) {
}

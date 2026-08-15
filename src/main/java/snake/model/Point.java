package snake.model;

/**
 * 不可变的网格坐标点，对应 Python {@code @dataclass(frozen=True) Point}。
 * 作为 record 自动获得 equals/hashCode，因此可作为 HashSet 元素和 Map 键。
 */
public record Point(int x, int y) {

    /** 沿给定方向移动一步，返回新点。 */
    public Point moved(Point direction) {
        return new Point(this.x + direction.x, this.y + direction.y);
    }

    /** 方向上：屏幕坐标系，y 向下增加。 */
    public static final Point UP = new Point(0, -1);
    public static final Point DOWN = new Point(0, 1);
    public static final Point LEFT = new Point(-1, 0);
    public static final Point RIGHT = new Point(1, 0);
}

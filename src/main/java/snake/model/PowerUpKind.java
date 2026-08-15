package snake.model;

/** 道具种类，对应 Python {@code PowerUpKind} 枚举。 */
public enum PowerUpKind {
    /** 无敌：可穿过墙体和自身，并撞碎障碍物。 */
    INVINCIBLE("invincible"),
    /** 磁铁：将范围内食物/道具吸向蛇头。 */
    MAGNET("magnet");

    private final String wireName;

    PowerUpKind(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}

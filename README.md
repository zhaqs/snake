# Snake Game

一个使用 Java Swing + Graphics2D 开发的桌面贪吃蛇游戏。采用「霓光夜园 / Neon Garden」深色视觉（径向渐变背景 + 玻璃质感蛇珠 + 青色发光眼），蛇以连续坐标沿 8 个方向（含斜向）移动——按住方向键才前进、松开即停。支持等级成长、障碍物、道具、多食物、排行榜和最高分记录。数据通过本地 MySQL 持久化，构建产物为包含驱动的可执行 fat jar，`java -jar` 即可运行。

## 功能

### 玩法
- 方向键或 `WASD` 控制移动；**按住才前进，松开即停止**（已取消自动前进与加速功能）
- 同时按住两个相邻方向键可**斜向移动**（8 个方向，如 ↑+→ 走右上 45°）；两对向键同按则抵消停止
- 空格用于开始 / 重开，`P` 暂停或继续，`Esc` 退出
- 随分数提升等级、速度和障碍数量（每 5 分 +1 级，每级 +4 障碍）

### 道具
- **无敌（I）**：5 秒内可穿过墙体和自身，并撞碎障碍物（触发破碎特效）
- **磁铁（M）**：5 秒内将 3 格范围内的食物和道具吸向蛇头
- 每 10 秒随机生成一个道具

### 视觉效果（霓光夜园 / Neon Garden）
- 径向渐变深色背景 + 柔和蓝紫网格，营造空间纵深
- 蛇身为**玻璃珠串**：每节圆珠带左上高光，从头到尾由亮到暗渐变；蛇尾最后两颗渐小成尖
- 蛇头为加大的圆珠 + 暗色勾边，眼睛为白底 + **青色虹膜** + 黑瞳 + 白色 catchlight，舌头为玫瑰红分叉
- 食物为**玻璃果实**（径向渐变球 + 高光点）；同时在场的食物维持 3 个，且不在墙边一圈生成
- 无敌状态：彩虹玻璃珠闪烁 + 金色描边，临近结束时闪烁警示
- 磁铁状态：紫罗兰玻璃珠 + 双重脉冲光环（带霓光晕）+ 光束 + 流动粒子 + 目标高亮
- 墙体破碎：闪光圈 + 6 条裂纹 + 6 个飞散碎片
- 死亡动画：灰化蛇身 + X 形眼睛 + 吐舌 + **红色冲击波环**扩散 + 闪烁框
- 等级提示、欢迎页排行榜（体型榜单 top 5）

### 碰撞判定
- **撞自身**：判定阈值收紧——蛇头要真正越过身体珠子的中心（`SELF_HIT_RADIUS`，小于身珠半径）才算撞，可以擦身而过
- **撞墙**：蛇头中心越过边界线才死，蛇可贴墙滑行（头珠探出一半仍存活）
- 无敌状态下撞自身 / 墙体不死，撞障碍则击碎

### 音效
- `javax.sound.sampled` 合成方波，无需外部音频文件
- 吃食物（eat）、撞碎障碍（break）、死亡（death）三种音效
- 后台线程播放，不阻塞游戏循环

### 持久化
- MySQL 存储全局最高分和每玩家排行榜
- 连接失败时自动降级为内存态，游戏仍可玩（退出后成绩不保存）

## 环境要求

- JDK 21 或更高版本
- Maven 3.9 或更高版本
- MySQL 8+（可选，不可用时降级为内存存储）

## 构建

```bash
mvn clean package
```

构建完成后，可执行 JAR 位于：

```text
target/snake-game-1.0.0.jar
```

Maven 会通过 `maven-shade-plugin` 把 MySQL Connector/J 驱动打进 jar，运行时无需额外配置 classpath。

## 运行

```bash
java -jar target/snake-game-1.0.0.jar
```

游戏需要图形桌面环境。启动后会弹窗要求输入玩家昵称（最多 20 字符），随后进入欢迎界面，按空格开始游戏。

## MySQL 配置

数据库连接通过环境变量配置，默认值已设为本机常用配置：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SNAKE_DB_HOST` | `localhost` | MySQL 主机 |
| `SNAKE_DB_PORT` | `3306` | MySQL 端口 |
| `SNAKE_DB_NAME` | `snake_game` | 数据库名（不存在则自动创建） |
| `SNAKE_DB_USER` | `root` | 用户名 |
| `SNAKE_DB_PASSWORD` | `1234` | 密码 |

PowerShell 配置示例：

```powershell
$env:SNAKE_DB_PASSWORD = Read-Host "MySQL password" -MaskInput
java -jar target/snake-game-1.0.0.jar
```

数据库和两张表（`high_score` 单行最高分、`leaderboard` 每玩家一行）会在启动时自动创建。如果连接失败，游戏仍可正常运行，但成绩只保存在当前进程内。环境变量只在本地设置，不要提交包含真实凭据的脚本或配置文件。

## 项目结构

```text
src/main/java/snake/
├── SnakeGame.java              # 游戏入口、Swing 界面、连续移动游戏循环、方向键状态、所有玩法逻辑
├── audio/
│   └── AudioBeep.java          # javax.sound 方波合成音效
├── constants/
│   └── GameConfig.java         # 游戏常量 + Theme 配色（java.awt.Color）
├── input/
│   └── DirectionInput.java     # 按键到方向的映射（8方向版改用直接跟踪，此模块保留未用）
├── model/
│   ├── Point.java              # 不可变网格坐标（record）
│   ├── PowerUp.java            # 道具
│   ├── PowerUpKind.java        # 道具种类枚举
│   └── WallBreakEffect.java   # 墙体破碎特效
├── render/
│   ├── SnakeRenderer.java     # Graphics2D 全部绘制（对应 Python snake_renderer）
│   └── PositionProvider.java  # 渲染器拉取连续坐标（珠串 + 航向）的回调接口
├── rules/
│   └── SnakeRules.java        # 纯函数：方向相反、边界、曼哈顿距离（部分 8方向版不再调用）
├── state/
│   └── GameState.java         # 可变运行时状态
└── storage/
    └── LeaderboardStore.java  # MySQL 排行榜与最高分持久化（JDBC）
```

## 架构

- **渲染解耦**：`SnakeGame` 实现 `PositionProvider` 接口向 `SnakeRenderer` 提供连续像素坐标（珠串 `bodyPoints` + 航向 `heading`），渲染器不依赖 GUI 控件，可独立测试。
- **游戏循环**：`javax.swing.Timer(16ms)` 驱动 `tick()`，按真实 `dt` 前进（连续坐标）；方向由四方向键状态合成归一化向量得到（支持斜向），`stepMovement()` 完成头位移 → 身体等距跟随 → 碰撞 / 拾取。
- **状态隔离**：`GameState` 持有全部可变状态（蛇身珠串、航向、方向向量、食物 / 道具 / 障碍等），`SnakeRules` 是无状态纯函数，`GameConfig` 是静态常量。
- **降级容错**：`LeaderboardStore` 在 JDBC 不可用时退化为内存 `HashMap`，保证游戏可玩性。

## 验证

```bash
mvn clean package
java -jar target/snake-game-1.0.0.jar
```

当前项目尚未添加 Java 自动化测试，Maven 命令用于验证源码编译和打包是否正常完成。端到端验证可通过实际运行游戏并检查 MySQL 数据确认入库：

```sql
SELECT * FROM snake_game.high_score;
SELECT player_name, best_score, best_length FROM snake_game.leaderboard
ORDER BY best_length DESC, best_score DESC LIMIT 5;
```

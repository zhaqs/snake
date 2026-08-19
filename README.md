# Snake Game

一个使用 Java Swing + Graphics2D 开发的桌面贪吃蛇游戏，支持平滑插值动画、等级成长、障碍物、道具、加速、排行榜和最高分记录。数据通过本地 MySQL 持久化，构建产物为包含驱动的可执行 fat jar，`java -jar` 即可运行。

## 功能

### 玩法
- 方向键或 `WASD` 控制移动
- 按住空格加速（移动间隔减半），松开减速
- 游戏结束后按空格重新开始，`P` 暂停或继续，`Esc` 退出
- 随分数提升等级、速度和障碍数量（每 5 分 +1 级，每级 +4 障碍）

### 道具
- **无敌（I）**：5 秒内可穿过墙体和自身，并撞碎障碍物（触发破碎特效）
- **磁铁（M）**：5 秒内将 3 格范围内的食物和道具吸向蛇头
- 每 10 秒随机生成一个道具

### 视觉效果
- 基于移动进度（`moveProgress`）的逐帧插值，蛇身平滑滑动而非逐格跳变
- 蛇头带眼睛、瞳孔、鼻孔和分叉舌头，朝向随移动方向变化
- 蛇尾渐变收尖，蛇身用圆角方块 + 连接线渲染
- 无敌状态：彩虹色闪烁 + 加粗轮廓，临近结束时闪烁警示
- 磁铁状态：双重脉冲光环、虚线光束、流动粒子、目标高亮
- 墙体破碎：闪光圈 + 6 条裂纹 + 6 个飞散碎片
- 死亡动画：X 形眼睛 + 吐舌 + 能量圈扩散 + 闪烁框
- 等级提示、欢迎页排行榜（体型榜单 top 5）

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
├── SnakeGame.java              # 游戏入口、Swing 界面、游戏循环、键盘输入、所有玩法逻辑
├── audio/
│   └── AudioBeep.java          # javax.sound 方波合成音效
├── constants/
│   └── GameConfig.java         # 游戏常量 + Theme 配色（java.awt.Color）
├── input/
│   └── DirectionInput.java     # 按键到方向的映射
├── model/
│   ├── Point.java              # 不可变网格坐标（record）
│   ├── PowerUp.java            # 道具
│   ├── PowerUpKind.java        # 道具种类枚举
│   └── WallBreakEffect.java   # 墙体破碎特效
├── render/
│   ├── SnakeRenderer.java     # Graphics2D 全部绘制（对应 Python snake_renderer）
│   └── PositionProvider.java  # 渲染器拉取插值坐标的回调接口
├── rules/
│   └── SnakeRules.java        # 纯函数：方向相反、碰撞致命、边界、曼哈顿距离
├── state/
│   └── GameState.java         # 可变运行时状态
└── storage/
    └── LeaderboardStore.java  # MySQL 排行榜与最高分持久化（JDBC）
```

## 架构

- **渲染解耦**：`SnakeGame` 实现 `PositionProvider` 接口向 `SnakeRenderer` 提供插值坐标，渲染器不依赖 GUI 控件，可独立测试。
- **游戏循环**：`javax.swing.Timer(16ms)` 驱动 `tick()`，基于 `System.nanoTime()` 单调时钟计算移动进度，到点触发 `moveOneCell()`。
- **状态隔离**：`GameState` 持有全部可变状态，`SnakeRules` 是无状态纯函数，`GameConfig` 是静态常量。
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

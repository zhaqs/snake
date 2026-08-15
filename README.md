# Snake Game

一个使用 Java Swing 开发的桌面贪吃蛇游戏，支持等级成长、障碍物、道具、加速、排行榜和最高分记录。

## 功能

- 方向键或 `WASD` 控制移动
- 按住空格加速，游戏结束后按空格重新开始
- `P` 暂停或继续，`Esc` 退出
- 随分数提升等级、速度和障碍数量
- 无敌与磁铁道具
- MySQL 排行榜和最高分持久化
- MySQL 不可用时自动降级为内存存储

## 环境要求

- JDK 21 或更高版本
- Maven 3.9 或更高版本
- MySQL 8（可选）

## 构建

```powershell
mvn clean package
```

构建完成后，可执行 JAR 位于：

```text
target/snake-game-1.0.0.jar
```

## 运行

```powershell
java -jar target/snake-game-1.0.0.jar
```

游戏需要图形桌面环境。启动后会要求输入玩家昵称。

## MySQL 配置

数据库连接通过环境变量配置，不要把真实账号或密码写入源码：

```text
SNAKE_DB_HOST       默认 localhost
SNAKE_DB_PORT       默认 3306
SNAKE_DB_NAME       默认 snake_game
SNAKE_DB_USER       默认 root
SNAKE_DB_PASSWORD   默认空值
```

PowerShell 配置示例：

```powershell
$env:SNAKE_DB_HOST = "localhost"
$env:SNAKE_DB_USER = "your-user"
$env:SNAKE_DB_PASSWORD = Read-Host "MySQL password" -MaskInput
java -jar target/snake-game-1.0.0.jar
```

数据库和数据表会在启动时自动创建。如果连接失败，游戏仍可正常运行，但成绩只保存在当前进程内。环境变量只在本地设置，不要提交包含真实凭据的脚本或配置文件。

## 项目结构

```text
src/main/java/snake/
├── SnakeGame.java          # 游戏入口和 Swing 界面
├── audio/                  # 音效
├── constants/              # 游戏配置与主题
├── input/                  # 键盘输入映射
├── model/                  # 数据模型
├── render/                 # 渲染接口
├── rules/                  # 游戏规则
├── state/                  # 游戏状态
└── storage/                # 排行榜与最高分存储
```

## 验证

```powershell
mvn test
```

当前项目尚未添加 Java 自动化测试，Maven 命令用于验证源码编译和测试阶段是否正常完成。

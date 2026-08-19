package snake.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL 排行榜与最高分持久化层。
 *
 * <p>启动时自动创建库 {@code snake_game} 与两张表（{@code high_score} 全局单行、
 * {@code leaderboard} 每玩家一行持久最好成绩与最长蛇身）。
 * 连接失败时降级为内存态，游戏仍可玩，但退出后成绩不保存。
 *
 * <p>连接信息通过 {@code SNAKE_DB_HOST}、{@code SNAKE_DB_PORT}、
 * {@code SNAKE_DB_NAME}、{@code SNAKE_DB_USER} 和 {@code SNAKE_DB_PASSWORD} 配置。
 */
public class LeaderboardStore {

    private static final String HOST = env("SNAKE_DB_HOST", "localhost");
    private static final int PORT = envInt("SNAKE_DB_PORT", 3306);
    private static final String DATABASE = env("SNAKE_DB_NAME", "snake_game");
    private static final String USER = env("SNAKE_DB_USER", "root");
    private static final String PASSWORD = env("SNAKE_DB_PASSWORD", "1234");

    private static final String SETUP_URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int envInt(String name, int fallback) {
        try {
            return Integer.parseInt(env(name, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** 排行榜的一行：玩家名、历史最好分数、历史最长蛇身。 */
    public record Entry(String name, int bestScore, int bestLength) {}

    private boolean memoryFallback = false;
    private final Map<String, Entry> memory = new HashMap<>();
    private int memoryHigh = 0;

    public LeaderboardStore() {
        init();
    }

    private void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[LeaderboardStore] MySQL 驱动未找到，降级为内存态：" + e.getMessage());
            memoryFallback = true;
            return;
        }
        try (Connection conn = DriverManager.getConnection(SETUP_URL, USER, PASSWORD)) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DATABASE
                        + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 无法建库，降级为内存态：" + e.getMessage());
            memoryFallback = true;
            return;
        }
        try (Connection conn = openConn();
             Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS high_score (
                        id INT PRIMARY KEY DEFAULT 1,
                        score INT NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS leaderboard (
                        player_name VARCHAR(20) PRIMARY KEY,
                        best_score INT NOT NULL DEFAULT 0,
                        best_length INT NOT NULL DEFAULT 0
                    )""");
            // 保证单行最高分存在
            st.executeUpdate("INSERT IGNORE INTO high_score (id, score) VALUES (1, 0)");
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 无法建表，降级为内存态：" + e.getMessage());
            memoryFallback = true;
        }
    }

    private Connection openConn() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }

    /** 加载全局最高分（单行，id=1）。 */
    public int loadHighScore() {
        if (memoryFallback) {
            return memoryHigh;
        }
        try (Connection conn = openConn();
             PreparedStatement ps = conn.prepareStatement("SELECT score FROM high_score WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Math.max(0, rs.getInt("score"));
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 读取最高分失败：" + e.getMessage());
            return memoryHigh;
        }
    }

    /** 更新最高分为历史最好（取较大值）。 */
    public void saveHighScore(int score) {
        int best = Math.max(0, score);
        if (memoryFallback) {
            memoryHigh = Math.max(memoryHigh, best);
            return;
        }
        String sql = "INSERT INTO high_score (id, score) VALUES (1, ?) "
                + "ON DUPLICATE KEY UPDATE score = GREATEST(score, VALUES(score))";
        try (Connection conn = openConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, best);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 写入最高分失败：" + e.getMessage());
            memoryHigh = Math.max(memoryHigh, best);
        }
    }

    /**
     * 更新某玩家的排行纪录（取历史最好值），返回是否发生变化。
     * 对应 Python {@code update_player_record}。
     */
    public boolean updateRecord(String rawName, int score, int length) {
        String name = normalizePlayerName(rawName);
        if (name.isEmpty()) {
            return false;
        }
        int bestScore = Math.max(0, score);
        int bestLength = Math.max(0, length);
        if (memoryFallback) {
            Entry prev = memory.getOrDefault(name, new Entry(name, 0, 0));
            boolean changed = bestScore > prev.bestScore || bestLength > prev.bestLength || !memory.containsKey(name);
            memory.put(name, new Entry(name,
                    Math.max(prev.bestScore, bestScore),
                    Math.max(prev.bestLength, bestLength)));
            return changed;
        }
        String sql = "INSERT INTO leaderboard (player_name, best_score, best_length) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "best_score = GREATEST(best_score, VALUES(best_score)), "
                + "best_length = GREATEST(best_length, VALUES(best_length))";
        try (Connection conn = openConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, bestScore);
            ps.setInt(3, bestLength);
            ps.executeUpdate();
            return true; // 粗粒度判定，避免额外查询；写即视为可能变化
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 写入排行失败：" + e.getMessage());
            Entry prev = memory.getOrDefault(name, new Entry(name, 0, 0));
            boolean changed = bestScore > prev.bestScore || bestLength > prev.bestLength || !memory.containsKey(name);
            memory.put(name, new Entry(name,
                    Math.max(prev.bestScore, bestScore),
                    Math.max(prev.bestLength, bestLength)));
            return changed;
        }
    }

    /**
     * 按 蛇身长度/分数/名字 取时间序返回前 limit 名，对应 Python {@code top_by_length}。
     */
    public List<Entry> topByLength(int limit) {
        int cap = Math.max(0, limit);
        if (memoryFallback) {
            List<Entry> all = new ArrayList<>(memory.values());
            all.sort((a, b) -> {
                int c = Integer.compare(b.bestLength, a.bestLength);
                if (c != 0) return c;
                c = Integer.compare(b.bestScore, a.bestScore);
                if (c != 0) return c;
                return a.name.compareToIgnoreCase(b.name);
            });
            return all.size() > cap ? new ArrayList<>(all.subList(0, cap)) : all;
        }
        String sql = "SELECT player_name, best_score, best_length FROM leaderboard "
                + "ORDER BY best_length DESC, best_score DESC, player_name ASC LIMIT ?";
        List<Entry> out = new ArrayList<>();
        try (Connection conn = openConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cap);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("player_name"),
                            Math.max(0, rs.getInt("best_score")),
                            Math.max(0, rs.getInt("best_length"))));
                }
            }
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 读取排行失败：" + e.getMessage());
        }
        return out;
    }

    /**
     * 获取某玩家的个人纪录，不存在则返回 null。
     */
    public Entry getRecord(String rawName) {
        String name = normalizePlayerName(rawName);
        if (name.isEmpty()) return null;
        if (memoryFallback) {
            return memory.get(name);
        }
        String sql = "SELECT player_name, best_score, best_length FROM leaderboard WHERE player_name = ?";
        try (Connection conn = openConn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Entry(rs.getString("player_name"),
                            Math.max(0, rs.getInt("best_score")),
                            Math.max(0, rs.getInt("best_length")));
                }
            }
        } catch (SQLException e) {
            System.err.println("[LeaderboardStore] 读取玩家纪录失败：" + e.getMessage());
        }
        return null;
    }

    /**
     * 规范化玩家昵称：去掉首尾空白、折叠内部连续空白、截断到 20 字符。
     * 对应 Python {@code normalize_player_name}。
     */
    public static String normalizePlayerName(String name) {
        if (name == null) {
            return "";
        }
        String collapsed = name.strip().replaceAll("\\s+", " ");
        return collapsed.length() > 20 ? collapsed.substring(0, 20) : collapsed;
    }
}

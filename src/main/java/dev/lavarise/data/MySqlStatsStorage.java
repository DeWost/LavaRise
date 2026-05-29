package dev.lavarise.data;

import dev.lavarise.core.LavaRisePlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Network-wide MySQL stats backend (pure JDK {@code java.sql} — the driver is
 * provided at runtime via {@code plugin.yml libraries:}). An in-memory cache is
 * the read source (lazy-loaded per player); mutations are batched and UPSERTed
 * on {@link #flush()} (driven periodically + on disable). Leaderboards query the
 * database directly so they reflect the whole network. All connection access is
 * serialised on a lock and reconnects on failure.
 *
 * @author DeWost
 */
public final class MySqlStatsStorage implements StatsStorage {

    private final LavaRisePlugin plugin;
    private final String table;
    private final String url;
    private final String user;
    private final String pass;

    private final Object lock = new Object();
    private Connection connection;

    private final Map<UUID, Row> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private static final class Row {
        String name = "?";
        int wins, games, kills, deaths;
        long bestTime;
    }

    public MySqlStatsStorage(LavaRisePlugin plugin) throws SQLException {
        this.plugin = plugin;
        final var cfg = plugin.getConfigManager();
        this.table = cfg.getMysqlTablePrefix() + "stats";
        this.url = "jdbc:mysql://" + cfg.getMysqlHost() + ":" + cfg.getMysqlPort() + "/" + cfg.getMysqlDatabase()
                + "?useSSL=" + cfg.isMysqlSsl() + "&autoReconnect=true&characterEncoding=utf8";
        this.user = cfg.getMysqlUser();
        this.pass = cfg.getMysqlPassword();

        loadDriver();
        connect();
        createTable();
        plugin.getLogger().info("MySQL stats connected: " + cfg.getMysqlHost() + "/" + cfg.getMysqlDatabase());
    }

    private void loadDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException modern) {
            try {
                Class.forName("com.mysql.jdbc.Driver"); // legacy fallback
            } catch (ClassNotFoundException legacy) {
                throw new SQLException("MySQL JDBC driver not on classpath");
            }
        }
    }

    private void connect() throws SQLException {
        synchronized (lock) {
            connection = DriverManager.getConnection(url, user, pass);
        }
    }

    private Connection conn() throws SQLException {
        synchronized (lock) {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connect();
            }
            return connection;
        }
    }

    private void createTable() throws SQLException {
        final String sql = "CREATE TABLE IF NOT EXISTS `" + table + "` ("
                + "uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), "
                + "wins INT NOT NULL DEFAULT 0, games INT NOT NULL DEFAULT 0, "
                + "kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, "
                + "best_time BIGINT NOT NULL DEFAULT 0)";
        synchronized (lock) {
            try (Statement st = conn().createStatement()) {
                st.executeUpdate(sql);
            }
        }
    }

    private Row load(UUID id) {
        Row cached = cache.get(id);
        if (cached != null) return cached;

        final Row row = new Row();
        final String sql = "SELECT name,wins,games,kills,deaths,best_time FROM `" + table + "` WHERE uuid=?";
        synchronized (lock) {
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        row.name = rs.getString("name");
                        row.wins = rs.getInt("wins");
                        row.games = rs.getInt("games");
                        row.kills = rs.getInt("kills");
                        row.deaths = rs.getInt("deaths");
                        row.bestTime = rs.getLong("best_time");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "MySQL load failed for " + id, e);
            }
        }
        cache.put(id, row);
        return row;
    }

    @Override public void recordGamePlayed(UUID id, String name) { Row r = load(id); r.name = name; r.games++; dirty.add(id); }
    @Override public void recordWin(UUID id, String name)        { Row r = load(id); r.name = name; r.wins++; dirty.add(id); }
    @Override public void recordKill(UUID id, String name)       { Row r = load(id); r.name = name; r.kills++; dirty.add(id); }
    @Override public void recordDeath(UUID id, String name)      { Row r = load(id); r.name = name; r.deaths++; dirty.add(id); }

    @Override
    public void recordSurvivalTime(UUID id, String name, long seconds) {
        Row r = load(id);
        r.name = name;
        if (seconds > r.bestTime) { r.bestTime = seconds; dirty.add(id); }
    }

    @Override public int getWins(UUID id)     { return load(id).wins; }
    @Override public int getGames(UUID id)    { return load(id).games; }
    @Override public int getKills(UUID id)    { return load(id).kills; }
    @Override public int getDeaths(UUID id)   { return load(id).deaths; }
    @Override public long getBestTime(UUID id) { return load(id).bestTime; }
    @Override public String getName(UUID id)  { return load(id).name; }

    @Override
    public List<StatsManager.Entry> top(String stat, int limit) {
        // Whitelist the column (never interpolate user input into SQL).
        final String col = switch (stat) {
            case "wins" -> "wins";
            case "kills" -> "kills";
            case "games" -> "games";
            case "deaths" -> "deaths";
            default -> "best_time";
        };
        final List<StatsManager.Entry> out = new ArrayList<>();
        final String sql = "SELECT name," + col + " AS v FROM `" + table + "` ORDER BY v DESC LIMIT ?";
        synchronized (lock) {
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(new StatsManager.Entry(rs.getString("name"), rs.getLong("v")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "MySQL leaderboard query failed", e);
            }
        }
        return out;
    }

    @Override
    public void flush() {
        if (dirty.isEmpty()) return;
        final List<UUID> batch = new ArrayList<>(dirty);
        dirty.clear();
        final String sql = "INSERT INTO `" + table + "` (uuid,name,wins,games,kills,deaths,best_time) "
                + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                + "name=VALUES(name),wins=VALUES(wins),games=VALUES(games),kills=VALUES(kills),"
                + "deaths=VALUES(deaths),best_time=VALUES(best_time)";
        synchronized (lock) {
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                for (UUID id : batch) {
                    final Row r = cache.get(id);
                    if (r == null) continue;
                    ps.setString(1, id.toString());
                    ps.setString(2, r.name);
                    ps.setInt(3, r.wins);
                    ps.setInt(4, r.games);
                    ps.setInt(5, r.kills);
                    ps.setInt(6, r.deaths);
                    ps.setLong(7, r.bestTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "MySQL flush failed; re-queuing", e);
                dirty.addAll(batch);
            }
        }
    }

    @Override
    public void close() {
        flush();
        synchronized (lock) {
            try {
                if (connection != null && !connection.isClosed()) connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}

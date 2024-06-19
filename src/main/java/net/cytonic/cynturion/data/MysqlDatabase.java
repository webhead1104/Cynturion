package net.cytonic.cynturion.data;

import net.cytonic.cynturion.Cynturion;
import net.cytonic.cynturion.CynturionSettings;
import net.cytonic.cynturion.utils.BanData;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MysqlDatabase {

    private final Cynturion plugin;
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean ssl;
    private Connection connection;

    public MysqlDatabase(Cynturion plugin) {
        this.plugin = plugin;
        this.host = CynturionSettings.DATABASE_HOST;
        this.port = CynturionSettings.DATABASE_PORT;
        this.database = CynturionSettings.DATABASE_NAME;
        this.username = CynturionSettings.DATABASE_USER;
        this.password = CynturionSettings.DATABASE_PASSWORD;
        this.ssl = CynturionSettings.DATABASE_USE_SSL;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Error failed to load database driver " + e);
        }
    }

    /**
     * Checks if the database is connected
     *
     * @return if the database is connected
     */
    public boolean isConnected() {
        return (connection != null);
    }

    /**
     * connects to the database
     *
     * @return a future that completes when the connection is successful
     */
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        worker.submit(() -> {
            if (!isConnected()) {
                try {
                    connection = DriverManager.getConnection("jdbc:mysql://"+host+":"+port+"/"+database+"?useSSL="+ssl+"&autoReconnect=true&allowPublicKeyRetrieval=true", username, password);
                    System.out.println("Successfully connected to the MySQL Database!");
                    future.complete(null);
                } catch (SQLException e) {
                    System.out.println("Error invalid Database Credentials! " + e);
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }


    /**
     * Disconnects from the database server
     */
    public void disconnect() {
        worker.submit(() -> {
            if (isConnected()) {
                try {
                    connection.close();
                    System.out.println("Database connection closed!");
                } catch (SQLException e) {
                    System.out.println("An error occurred whilst disconnecting from the database. Please report the following stacktrace to CytonicMC: " + e);
                }
            }
        });
    }


    /**
     * Gets the connection
     *
     * @return the connection to the database
     */
    private Connection getConnection() {
        return connection;
    }

    public void createTables() {
        createBansTable();
    }

    /**
     * Creates the bans table
     */
    private void createBansTable() {
        worker.submit(() -> {
            if (isConnected()) {
                PreparedStatement ps;
                try {
                    ps = getConnection().prepareStatement("CREATE TABLE IF NOT EXISTS cytonic_bans (uuid VARCHAR(36), to_expire VARCHAR(100), reason TINYTEXT, PRIMARY KEY(uuid))");
                    ps.executeUpdate();
                } catch (SQLException e) {
                    System.out.println("An error occoured whilst creating the `cytonic_bans` table." + e);
                }
            }
        });
    }

    /**
     * Unbans a player
     *
     * @param uuid the player to unban
     * @return a future that completes when the player is unbanned
     */
    public CompletableFuture<Void> unbanPlayer(UUID uuid) {
        if (!isConnected()) throw new IllegalStateException("The database must be connected.");
        CompletableFuture<Void> future = new CompletableFuture<>();
        worker.submit(() -> {
            try {
                PreparedStatement ps = getConnection().prepareStatement("DELETE FROM cytonic_bans WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                System.out.println("An error occurred whilst unbanning the player " + uuid + "." + e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }


    /**
     * The concurrent friendly way to fetch a player's ban status
     *
     * @param uuid THe player to check
     * @return The CompletableFuture that holds the player's ban status
     */
    public CompletableFuture<BanData> isBanned(UUID uuid) {
        if (!isConnected()) throw new IllegalStateException("The database must be connected.");
        CompletableFuture<BanData> future = new CompletableFuture<>();
        worker.submit(() -> {
            try {
                PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM cytonic_bans WHERE uuid = ?");
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Instant expiry = Instant.parse(rs.getString("to_expire"));
                    if (expiry.isBefore(Instant.now())) {
                        future.complete(new BanData(null, null, false));
                        unbanPlayer(uuid);
                    } else {
                        try {
                            BanData banData = new BanData(rs.getString("reason"), expiry, true);
                            future.complete(banData);
                        } catch (Exception e) {
                            System.out.println("An error occurred whilst determining if the player " + uuid + " is banned." + e);
                            future.complete(new BanData(null, null, true));
                        }
                    }
                } else {
                    future.complete(new BanData(null, null, false));
                }
            } catch (SQLException e) {
                System.out.println("An error occurred whilst determining if the player " + uuid + " is banned." + e);
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}

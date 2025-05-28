package com.pharmacyapp.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseService {

    private static final String DEFAULT_DB_URL = "jdbc:h2:./data/pharmacy_db;AUTO_SERVER=TRUE";
    private String dbUrl;

    public DatabaseService(String dbUrl) {
        this.dbUrl = dbUrl;
        // Ensure the directory for the database file exists
        // H2 will create the file itself, but not the directory structure.
        // This is more relevant for file-based DBs not in the root.
        // For "./data/pharmacy_db", we need to ensure "data" directory exists.
        File dbFile = new File(this.dbUrl.substring(this.dbUrl.indexOf(':') + 3, this.dbUrl.indexOf(';')));
        if (dbFile.getParentFile() != null) {
            dbFile.getParentFile().mkdirs();
        }
    }

    public DatabaseService() {
        this(DEFAULT_DB_URL);
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS medications (" +
                     "id INT AUTO_INCREMENT PRIMARY KEY," +
                     "name VARCHAR(255) NOT NULL," +
                     "dosage VARCHAR(255)," +
                     "quantity INT," +
                     "expiration_date DATE," +
                     "manufacturer VARCHAR(255)," +
                     "lot_number VARCHAR(255)" +
                     ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Database initialized (medications table created or already exists).");
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace(); // Consider a proper logging framework
        }
    }

    // Helper method for tests to get a fresh in-memory DB connection
    public static Connection getInMemoryConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:test_pharmacy_db;DB_CLOSE_DELAY=-1");
    }

    // Helper method for tests to initialize schema on an in-memory DB
    public static void initInMemoryDb(Connection conn) throws SQLException {
         String sql = "CREATE TABLE IF NOT EXISTS medications (" +
                     "id INT AUTO_INCREMENT PRIMARY KEY," +
                     "name VARCHAR(255) NOT NULL," +
                     "dosage VARCHAR(255)," +
                     "quantity INT," +
                     "expiration_date DATE," +
                     "manufacturer VARCHAR(255)," +
                     "lot_number VARCHAR(255)" +
                     ");" +
                     "DELETE FROM medications;"; // Clear table for fresh test

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}

// Need to ensure the java.io.File is imported for the directory creation.
// The code above misses this import if it's in a separate block.
// Let me fix that by regenerating the file with the import.
import java.io.File;

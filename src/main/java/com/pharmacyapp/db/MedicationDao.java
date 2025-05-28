package com.pharmacyapp.db;

import com.pharmacyapp.model.Medication;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MedicationDao {

    private DatabaseService databaseService;

    public MedicationDao(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void addMedication(Medication medication) throws SQLException {
        String sql = "INSERT INTO medications (name, dosage, quantity, expiration_date, manufacturer, lot_number) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, medication.getName());
            pstmt.setString(2, medication.getDosage());
            pstmt.setInt(3, medication.getQuantity());
            if (medication.getExpirationDate() != null) {
                pstmt.setDate(4, Date.valueOf(medication.getExpirationDate()));
            } else {
                pstmt.setNull(4, Types.DATE);
            }
            pstmt.setString(5, medication.getManufacturer());
            pstmt.setString(6, medication.getLotNumber());
            
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        medication.setId(generatedKeys.getInt(1)); // Set the generated ID back on the object
                    } else {
                        throw new SQLException("Creating medication failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public Medication getMedication(int id) throws SQLException {
        String sql = "SELECT * FROM medications WHERE id = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapRowToMedication(rs);
            }
        }
        return null;
    }

    public List<Medication> getAllMedications() throws SQLException {
        List<Medication> medications = new ArrayList<>();
        String sql = "SELECT * FROM medications ORDER BY name";
        try (Connection conn = databaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                medications.add(mapRowToMedication(rs));
            }
        }
        return medications;
    }

    public boolean updateMedication(Medication medication) throws SQLException {
        // Assumes Medication object has an ID, which it currently does not.
        // This method needs the ID to update the correct record.
        // For this to work, Medication class needs an int id field and getId() method.
        // Let's proceed as if it had one, and it would be passed or accessible.
        // For now, this method signature is problematic without an ID.
        // A common approach is medication.getId() or passing id separately.
        // I will assume for now that we update based on name and lot_number for uniqueness
        // if ID is not available, which is not ideal.
        // A better DAO would require an ID on the Medication object.
        // For now, I'll write it assuming an ID exists on the Medication object.
        if (medication.getId() == 0) { // Assuming ID 0 means it's not persisted or ID is invalid
            throw new SQLException("Medication ID must be set and valid for update operation.");
        }
        String sql = "UPDATE medications SET name = ?, dosage = ?, quantity = ?, expiration_date = ?, manufacturer = ?, lot_number = ? " +
                     "WHERE id = ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, medication.getName());
            pstmt.setString(2, medication.getDosage());
            pstmt.setInt(3, medication.getQuantity());
            if (medication.getExpirationDate() != null) {
                pstmt.setDate(4, Date.valueOf(medication.getExpirationDate()));
            } else {
                pstmt.setNull(4, Types.DATE);
            }
            pstmt.setString(5, medication.getManufacturer());
            pstmt.setString(6, medication.getLotNumber());
            pstmt.setInt(7, medication.getId()); // Use ID in WHERE clause

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean deleteMedication(int id) throws SQLException {
        String sql = "DELETE FROM medications WHERE id = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    private Medication mapRowToMedication(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String dosage = rs.getString("dosage");
        int quantity = rs.getInt("quantity");
        Date expDateSql = rs.getDate("expiration_date");
        LocalDate expirationDate = (expDateSql != null) ? expDateSql.toLocalDate() : null;
        String manufacturer = rs.getString("manufacturer");
        String lotNumber = rs.getString("lot_number");

        // Use the constructor that accepts an ID
        Medication med = new Medication(id, name, dosage, quantity, expirationDate, manufacturer, lotNumber);
        return med;
    }

    public List<Medication> searchMedications(String searchTerm) throws SQLException {
        List<Medication> medications = new ArrayList<>();
        // Using LOWER for case-insensitive search, standard SQL practice.
        // H2 is case-insensitive by default for string comparisons unless specified otherwise,
        // but using LOWER() makes it explicit and portable.
        String sql = "SELECT * FROM medications WHERE LOWER(name) LIKE LOWER(?) OR LOWER(manufacturer) LIKE LOWER(?) OR LOWER(dosage) LIKE LOWER(?) ORDER BY name";
        
        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String likeTerm = "%" + searchTerm + "%";
            pstmt.setString(1, likeTerm);
            pstmt.setString(2, likeTerm);
            pstmt.setString(3, likeTerm);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                medications.add(mapRowToMedication(rs));
            }
        }
        return medications;
    }
}

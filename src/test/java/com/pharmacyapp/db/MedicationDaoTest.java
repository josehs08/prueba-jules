package com.pharmacyapp.db;

import com.pharmacyapp.model.Medication;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS) // To allow non-static @BeforeAll and @AfterAll
public class MedicationDaoTest {

    private DatabaseService testDatabaseService;
    private MedicationDao medicationDao;
    private static final String H2_MEM_URL = "jdbc:h2:mem:test_pharmacy_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @BeforeAll
    void setUpAll() throws SQLException {
        testDatabaseService = new DatabaseService(H2_MEM_URL);
        medicationDao = new MedicationDao(testDatabaseService);
        try (Connection conn = testDatabaseService.getConnection()) {
            DatabaseService.initInMemoryDb(conn);
        }
    }

    @BeforeEach
    void setUpEach() throws SQLException {
        try (Connection conn = testDatabaseService.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM medications");
            // Optional: Reset auto-increment if needed, though H2 typically handles this for empty tables.
            // stmt.executeUpdate("ALTER TABLE medications ALTER COLUMN id RESTART WITH 1");
        }
    }

    @Test
    void testAddAndGetMedication() throws SQLException {
        Medication medToAdd = new Medication("TestMed1", "10mg", 100, LocalDate.now().plusYears(1), "TestManu", "LOT1");
        medicationDao.addMedication(medToAdd); // ID should be set on medToAdd by this call

        assertTrue(medToAdd.getId() > 0, "ID should be set on the medication object after adding.");

        Medication retrievedMed = medicationDao.getMedication(medToAdd.getId());
        assertNotNull(retrievedMed);
        assertEquals(medToAdd.getId(), retrievedMed.getId());
        assertEquals("TestMed1", retrievedMed.getName());
        assertEquals("10mg", retrievedMed.getDosage());
        assertEquals(100, retrievedMed.getQuantity());
        assertEquals(LocalDate.now().plusYears(1), retrievedMed.getExpirationDate());
        assertEquals("TestManu", retrievedMed.getManufacturer());
        assertEquals("LOT1", retrievedMed.getLotNumber());
    }
    
    @Test
    void testAddMedication_setsIdOnObject() throws SQLException {
        Medication med = new Medication("TestForIdSet", "5mg", 50, LocalDate.now().plusMonths(6), "IdSetterCo", "LOTID");
        medicationDao.addMedication(med);
        assertTrue(med.getId() > 0, "ID should be set by addMedication method.");
    }

    @Test
    void testGetAllMedications_empty() throws SQLException {
        List<Medication> medications = medicationDao.getAllMedications();
        assertNotNull(medications);
        assertTrue(medications.isEmpty());
    }

    @Test
    void testGetAllMedications_multiple() throws SQLException {
        Medication medA = new Medication("MedA", "1mg", 10, LocalDate.now().plusDays(10), "MFGA", "LOTA");
        Medication medB = new Medication("MedB", "2mg", 20, LocalDate.now().plusDays(20), "MFGB", "LOTB");
        medicationDao.addMedication(medA);
        medicationDao.addMedication(medB);
        
        List<Medication> medications = medicationDao.getAllMedications();
        assertNotNull(medications);
        assertEquals(2, medications.size());
        assertTrue(medications.stream().anyMatch(m -> m.getId() == medA.getId() && m.getName().equals("MedA")));
        assertTrue(medications.stream().anyMatch(m -> m.getId() == medB.getId() && m.getName().equals("MedB")));
    }

    @Test
    void testUpdateMedication() throws SQLException {
        Medication med = new Medication("UpdatableMed", "InitialDose", 50, LocalDate.now().plusYears(2), "OriginalManu", "OLDLOT");
        medicationDao.addMedication(med); // ID is set here
        assertTrue(med.getId() > 0);

        // Create a new Medication object or modify the existing one for update
        Medication medToUpdate = new Medication(
            med.getId(), // Critical: Use the same ID
            "UpdatableMed", // Name can be updated if desired, or kept same
            "UpdatedDose", 
            75, 
            LocalDate.now().plusYears(3), 
            "NewManu", 
            "NEWLOT"
        );

        boolean updated = medicationDao.updateMedication(medToUpdate);
        assertTrue(updated, "Update operation should return true for existing medication.");

        Medication updatedMedFromDb = medicationDao.getMedication(med.getId());
        assertNotNull(updatedMedFromDb, "Updated medication should be found in DB.");
        assertEquals("UpdatedDose", updatedMedFromDb.getDosage());
        assertEquals(75, updatedMedFromDb.getQuantity());
        assertEquals(LocalDate.now().plusYears(3), updatedMedFromDb.getExpirationDate());
        assertEquals("NewManu", updatedMedFromDb.getManufacturer());
        assertEquals("NEWLOT", updatedMedFromDb.getLotNumber());
    }
    
    @Test
    void testUpdateMedication_nonExistent() throws SQLException {
        Medication nonExistentMed = new Medication(-1, "NonExistent", "1mg", 10, LocalDate.now(), "NoManu", "NOLOT");
        // ID -1 (or any ID not in DB)
        boolean updated = medicationDao.updateMedication(nonExistentMed);
        assertFalse(updated, "Update operation should return false for non-existent medication.");
    }

    @Test
    void testDeleteMedication() throws SQLException {
        Medication medToDelete = new Medication("DeletableMed", "5mg", 30, LocalDate.now().plusMonths(1), "TempManu", "DELLOT");
        medicationDao.addMedication(medToDelete);
        assertTrue(medToDelete.getId() > 0, "ID should be set before attempting delete.");

        boolean deleted = medicationDao.deleteMedication(medToDelete.getId());
        assertTrue(deleted, "Delete operation should return true for existing medication ID.");

        Medication shouldBeNull = medicationDao.getMedication(medToDelete.getId());
        assertNull(shouldBeNull, "Medication should be null after deletion.");
        
        List<Medication> remainingMeds = medicationDao.getAllMedications();
        assertTrue(remainingMeds.isEmpty(), "Medication list should be empty after deleting the only one.");
    }

    @Test
    void testDeleteMedication_nonExistent() throws SQLException {
        boolean deleted = medicationDao.deleteMedication(-99); // Any non-existent ID
        assertFalse(deleted, "Delete operation should return false for non-existent medication ID.");
    }

    // --- Tests for searchMedications ---

    @Test
    void testSearchMedications_byName() throws SQLException {
        medicationDao.addMedication(new Medication("Aspirin", "81mg", 100, LocalDate.now(), "Bayer", "LOTASP"));
        medicationDao.addMedication(new Medication("Amoxicillin", "250mg", 50, LocalDate.now(), "GenericCo", "LOTAMX"));
        
        List<Medication> results = medicationDao.searchMedications("Aspirin");
        assertEquals(1, results.size());
        assertEquals("Aspirin", results.get(0).getName());

        results = medicationDao.searchMedications("asp"); // Case-insensitive partial
        assertEquals(1, results.size());
        assertEquals("Aspirin", results.get(0).getName());
    }

    @Test
    void testSearchMedications_byManufacturer() throws SQLException {
        medicationDao.addMedication(new Medication("Lipitor", "10mg", 30, LocalDate.now(), "Pfizer", "LOTLIP"));
        medicationDao.addMedication(new Medication("Metformin", "500mg", 60, LocalDate.now(), "GenericCo", "LOTMET"));

        List<Medication> results = medicationDao.searchMedications("Pfizer");
        assertEquals(1, results.size());
        assertEquals("Lipitor", results.get(0).getName());

        results = medicationDao.searchMedications("gen"); // Case-insensitive partial
        assertEquals(1, results.size()); // Should find GenericCo
        assertEquals("Metformin", results.get(0).getName());
    }
    
    @Test
    void testSearchMedications_byDosage() throws SQLException {
        medicationDao.addMedication(new Medication("DrugA", "100 mg", 30, LocalDate.now(), "ManuA", "LOTA"));
        medicationDao.addMedication(new Medication("DrugB", "20 mcg", 60, LocalDate.now(), "ManuB", "LOTB"));
        medicationDao.addMedication(new Medication("DrugC", "100mg", 60, LocalDate.now(), "ManuC", "LOTC"));


        List<Medication> results = medicationDao.searchMedications("100 m"); // Partial dosage
        assertEquals(2, results.size()); // DrugA and DrugC
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("DrugA")));
        assertTrue(results.stream().anyMatch(m -> m.getName().equals("DrugC")));

        results = medicationDao.searchMedications("mcg");
        assertEquals(1, results.size());
        assertEquals("DrugB", results.get(0).getName());
    }


    @Test
    void testSearchMedications_multipleFieldsMatch() throws SQLException {
        medicationDao.addMedication(new Medication("CommonDrug", "50mg", 100, LocalDate.now(), "CommonManu", "LOTCOMMON"));
        List<Medication> results = medicationDao.searchMedications("Common");
        assertEquals(1, results.size()); // Matches both name and manufacturer, but should only return one instance
    }

    @Test
    void testSearchMedications_noMatch() throws SQLException {
        medicationDao.addMedication(new Medication("SpecificMed", "1mg", 10, LocalDate.now(), "SpecificManu", "LOTSPEC"));
        List<Medication> results = medicationDao.searchMedications("Xyzzy");
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchMedications_emptySearchTerm() throws SQLException {
        medicationDao.addMedication(new Medication("Med1", "1mg", 10, LocalDate.now(), "M1", "L1"));
        medicationDao.addMedication(new Medication("Med2", "2mg", 20, LocalDate.now(), "M2", "L2"));
        // The SQL `LIKE '%%'` (which is what `LIKE '%' + '' + '%'` becomes) should match all rows.
        List<Medication> results = medicationDao.searchMedications("");
        assertEquals(2, results.size());
    }
    
    @Test
    void testSearchMedications_caseInsensitive() throws SQLException {
        medicationDao.addMedication(new Medication("UPPERCASEDRUG", "10MG", 10, LocalDate.now(), "UPPERMANU", "LOTUP"));
        
        List<Medication> resultsByName = medicationDao.searchMedications("uppercasedrug");
        assertEquals(1, resultsByName.size());
        
        List<Medication> resultsByManu = medicationDao.searchMedications("uppermanu");
        assertEquals(1, resultsByManu.size());

        List<Medication> resultsByDosage = medicationDao.searchMedications("10mg");
        assertEquals(1, resultsByDosage.size());
    }


    @AfterAll
    void tearDownAll() throws SQLException {
        try (Connection conn = testDatabaseService.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
    }
}

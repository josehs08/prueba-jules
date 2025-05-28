package com.pharmacyapp.gui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.pharmacyapp.db.DatabaseService;
import com.pharmacyapp.db.MedicationDao;
import com.pharmacyapp.model.Medication;
import com.pharmacyapp.service.PdfParserService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainApp extends Application {

    private TableView<Medication> medicationTable;
    private final ObservableList<Medication> medicationData = FXCollections.observableArrayList();

    // Detail view fields
    private TextField nameDetailField;
    private TextField dosageDetailField;
    private TextField quantityDetailField;
    private TextField expiryDetailField;
    private TextField manufacturerDetailField;
    private TextField lotNumberDetailField;

    private DatabaseService databaseService;
    private MedicationDao medicationDao;
    private PdfParserService pdfParserService;

    @Override
    public void init() throws Exception {
        super.init();
        // Initialize services
        databaseService = new DatabaseService(); // Uses default file-based DB
        databaseService.initDatabase();
        medicationDao = new MedicationDao(databaseService);
        pdfParserService = new PdfParserService();
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Futuristic Pharmacy Management System");

        // Main layout
        BorderPane rootLayout = new BorderPane();
        rootLayout.getStyleClass().add("root"); // Apply .root style from CSS

        // Top: Title
        Label titleLabel = new Label("Pharmacy Inventory Dashboard");
        titleLabel.setId("titleLabel");
        HBox titleBox = new HBox(titleLabel);
        titleBox.getStyleClass().add("title-box"); // For CSS padding if needed
        rootLayout.setTop(titleBox);

        // Center: TableView for medications
        setupMedicationTable();
        rootLayout.setCenter(medicationTable);

        // Right: Details section for selected medication
        VBox detailsSection = setupDetailsSection();
        rootLayout.setRight(detailsSection);

        // Bottom: Buttons
        Button addPdfButton = new Button("Load from PDF");
        addPdfButton.setOnAction(e -> handleLoadPdfAction(primaryStage));

        Button addManualButton = new Button("Add Manually");
        addManualButton.setOnAction(e -> handleAddManualAction(primaryStage));

        Button editButton = new Button("Edit Selected");
        editButton.setOnAction(e -> handleEditAction(primaryStage));
        // Disable edit button if no item is selected
        editButton.disableProperty().bind(medicationTable.getSelectionModel().selectedItemProperty().isNull());

        Button deleteButton = new Button("Delete Selected");
        deleteButton.setOnAction(e -> handleDeleteAction(primaryStage));
        // Disable delete button if no item is selected (already bound in previous step)
        deleteButton.disableProperty().bind(medicationTable.getSelectionModel().selectedItemProperty().isNull());

        // Search field and button
        TextField searchField = new TextField();
        searchField.setPromptText("Search by name, manufacturer, dosage...");
        searchField.getStyleClass().add("text-field");
        HBox.setHgrow(searchField, Priority.ALWAYS); // Allow search field to grow

        Button searchButton = new Button("Search");
        searchButton.setOnAction(e -> handleSearchAction(searchField.getText()));
        
        Button clearSearchButton = new Button("Clear");
        clearSearchButton.setOnAction(e -> {
            searchField.clear();
            handleSearchAction(""); // Effectively reloads all
        });

        HBox searchBox = new HBox(5, searchField, searchButton, clearSearchButton);
        searchBox.setPadding(new Insets(0,0,0,5)); // Add some left padding to separate from other buttons if they are on same conceptual line
        
        HBox bottomControls = new HBox(30, buttonBar, searchBox); // Main container for all bottom controls
        bottomControls.setAlignment(Pos.CENTER_LEFT); // Align items


        HBox buttonBar = new HBox(15);
        // buttonBar.setPadding(new Insets(15, 0, 0, 0)); // Padding handled by CSS
        buttonBar.getStyleClass().add("button-bar");
        buttonBar.getChildren().addAll(addPdfButton, addManualButton, editButton, deleteButton);
        // rootLayout.setBottom(buttonBar); // Replaced by bottomControls
        rootLayout.setBottom(bottomControls);


        // Load initial data
        // populateSampleData(); // Remove sample data population
        loadMedicationsFromDb(); // Load from DB on startup

        // Scene and Stage
        Scene scene = new Scene(rootLayout, 1000, 700);
        try {
            String cssPath = getClass().getResource("/styles.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (NullPointerException e) {
            System.err.println("Error loading stylesheet. Make sure styles.css is in src/main/resources.");
            e.printStackTrace();
        }
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupMedicationTable() {
        medicationTable = new TableView<>();
        medicationTable.setItems(medicationData);
        medicationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Medication, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());

        TableColumn<Medication, String> dosageCol = new TableColumn<>("Dosage");
        dosageCol.setCellValueFactory(cellData -> cellData.getValue().dosageProperty());

        TableColumn<Medication, Integer> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(cellData -> cellData.getValue().quantityProperty().asObject());

        TableColumn<Medication, LocalDate> expiryCol = new TableColumn<>("Expiration Date");
        expiryCol.setCellValueFactory(cellData -> cellData.getValue().expirationDateProperty());
        expiryCol.setCellFactory(column -> new TableCell<Medication, LocalDate>() {
            private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatter.format(item));
                }
            }
        });

        medicationTable.getColumns().addAll(nameCol, dosageCol, quantityCol, expiryCol);
        medicationTable.setPlaceholder(new Label("No medications in inventory. Click 'Load from PDF' to add."));

        // Listener for selection changes
        medicationTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showMedicationDetails(newValue));
    }

    private VBox setupDetailsSection() {
        VBox detailsSection = new VBox(10); // Spacing
        // detailsSection.setPadding(new Insets(10)); // Padding handled by CSS
        detailsSection.setId("detailsSection"); // ID for CSS styling
        detailsSection.setMinWidth(320); // Increased min width slightly for padding

        Label detailsTitle = new Label("Medication Details");
        // detailsTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #00E5FF;"); // Style handled by CSS

        nameDetailField = createReadOnlyTextField();
        dosageDetailField = createReadOnlyTextField();
        quantityDetailField = createReadOnlyTextField();
        expiryDetailField = createReadOnlyTextField();
        manufacturerDetailField = createReadOnlyTextField();
        lotNumberDetailField = createReadOnlyTextField();

        // Using VBox for each label-field pair to ensure consistent spacing if needed, or direct add.
        // Labels in detailsSection will be styled via CSS rule #detailsSection .label
        detailsSection.getChildren().addAll(
                detailsTitle,
                new Label("Name:"), nameDetailField,
                new Label("Dosage:"), dosageDetailField,
                new Label("Quantity:"), quantityDetailField,
                new Label("Expiry Date:"), expiryDetailField,
                new Label("Manufacturer:"), manufacturerDetailField,
                new Label("Lot Number:"), lotNumberDetailField
        );
        return detailsSection;
    }

    private TextField createReadOnlyTextField() {
        TextField textField = new TextField();
        textField.setEditable(false);
        // Styling will be handled by CSS .text-field and #detailsSection .text-field
        return textField;
    }

    private void showMedicationDetails(Medication medication) {
        if (medication != null) {
            nameDetailField.setText(medication.getName());
            dosageDetailField.setText(medication.getDosage());
            quantityDetailField.setText(String.valueOf(medication.getQuantity()));
            if (medication.getExpirationDate() != null) {
                expiryDetailField.setText(medication.getExpirationDate().format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            } else {
                expiryDetailField.setText("");
            }
            manufacturerDetailField.setText(medication.getManufacturer());
            lotNumberDetailField.setText(medication.getLotNumber());
        } else {
            // Clear fields if no medication is selected
            nameDetailField.clear();
            dosageDetailField.clear();
            quantityDetailField.clear();
            expiryDetailField.clear();
            manufacturerDetailField.clear();
            lotNumberDetailField.clear();
        }
    }

    private void loadMedicationsFromDb() {
        try {
            List<Medication> medsFromDb = medicationDao.getAllMedications();
            medicationData.clear();
            medicationData.addAll(medsFromDb);
            System.out.println("Loaded " + medsFromDb.size() + " medications from database.");
        } catch (SQLException e) {
            System.err.println("Error loading medications from database: " + e.getMessage());
            e.printStackTrace();
            showErrorDialog("Database Error", "Could not load medications from the database.");
        }
    }

    private void handleLoadPdfAction(Stage ownerStage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open PDF Prescription/Invoice");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );
        File selectedFile = fileChooser.showOpenDialog(ownerStage);

        if (selectedFile != null) {
            String pdfFilePath = selectedFile.getAbsolutePath();
            String extractedText = pdfParserService.extractTextFromFile(pdfFilePath);

            if (extractedText == null || extractedText.trim().isEmpty()) {
                showErrorDialog("PDF Parsing Error", "Could not extract any text from the selected PDF.");
                return;
            }

            Medication parsedMedication = pdfParserService.parseMedicationDetails(extractedText);

            if (parsedMedication == null || parsedMedication.getName() == null || parsedMedication.getName().trim().isEmpty()) {
                showErrorDialog("Parsing Error", "Could not extract valid medication data (e.g., name is missing) from the PDF content.");
                return;
            }

            try {
                medicationDao.addMedication(parsedMedication);
                loadMedicationsFromDb(); // Refresh table
                showSuccessDialog("Medication Added", "Medication '" + parsedMedication.getName() + "' successfully added from PDF.");
            } catch (SQLException e) {
                System.err.println("Error adding medication to database: " + e.getMessage());
                e.printStackTrace();
                showErrorDialog("Database Error", "Could not save the extracted medication to the database: " + e.getMessage());
            }
        }
    }

    private void showSuccessDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Removed populateSampleData() as we now load from DB.

    private void handleAddManualAction(Stage ownerStage) {
        MedicationEntryDialog dialog = new MedicationEntryDialog(null); // null for adding new
        // dialog.initOwner(ownerStage); // Not strictly necessary as modality is APPLICATION_MODAL

        Optional<Medication> result = dialog.showAndWait();

        result.ifPresent(newMedication -> {
            try {
                medicationDao.addMedication(newMedication);
                loadMedicationsFromDb(); // Refresh table
                showSuccessDialog("Medication Added", "Medication '" + newMedication.getName() + "' successfully added manually.");
            } catch (SQLException e) {
                System.err.println("Error adding medication manually to database: " + e.getMessage());
                e.printStackTrace();
                showErrorDialog("Database Error", "Could not save the new medication to the database: " + e.getMessage());
            }
        });
    }

    private void handleEditAction(Stage ownerStage) {
        Medication selectedMedication = medicationTable.getSelectionModel().getSelectedItem();

        if (selectedMedication == null) {
            // This case should ideally not be reached if button is disabled correctly
            showErrorDialog("No Selection", "Please select a medication to edit.");
            return;
        }

        MedicationEntryDialog dialog = new MedicationEntryDialog(selectedMedication);
        // dialog.initOwner(ownerStage);

        Optional<Medication> result = dialog.showAndWait();

        result.ifPresent(editedMedication -> {
            // The dialog's result converter should have already updated the fields
            // of the original selectedMedication object (if medicationToEdit was passed and handled that way)
            // or returned a new object with the original ID.
            // The MedicationEntryDialog processSave() handles this: if medicationToEdit is not null, it updates it.
            // So, 'editedMedication' here is the same instance as 'selectedMedication' but with updated fields.
            try {
                boolean success = medicationDao.updateMedication(editedMedication); // DAO uses ID from the object
                if (success) {
                    loadMedicationsFromDb(); // Refresh table
                    showSuccessDialog("Medication Updated", "Medication '" + editedMedication.getName() + "' successfully updated.");
                } else {
                    showErrorDialog("Update Failed", "Could not update the medication in the database. It might have been deleted by another user.");
                }
            } catch (SQLException e) {
                System.err.println("Error updating medication in database: " + e.getMessage());
                e.printStackTrace();
                showErrorDialog("Database Error", "Could not update the medication: " + e.getMessage());
            }
        });
    }

    private void handleDeleteAction(Stage ownerStage) {
        Medication selectedMedication = medicationTable.getSelectionModel().getSelectedItem();

        if (selectedMedication == null) {
            // Should not be reached if button disable logic is correct
            showErrorDialog("No Selection", "Please select a medication to delete.");
            return;
        }

        Alert confirmationDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationDialog.setTitle("Confirm Deletion");
        confirmationDialog.setHeaderText("Delete Medication: " + selectedMedication.getName());
        confirmationDialog.setContentText("Are you sure you want to delete this medication? This action cannot be undone.");
        // Apply styling to the alert dialog
        DialogPane alertPane = confirmationDialog.getDialogPane();
        try {
             String cssPath = getClass().getResource("/styles.css").toExternalForm();
             if (cssPath != null) {
                alertPane.getStylesheets().add(cssPath);
             } else {
                System.err.println("Alert CSS not found, continuing without it.");
             }
        } catch (Exception e) {
            System.err.println("Error loading CSS for alert: " + e.getMessage());
        }
        alertPane.getStyleClass().add("dialog-pane");
        // Ensure button-bar styling is applied to the alert's button container
        alertPane.lookupAll(".button-bar").forEach(node -> node.getStyleClass().add("dialog-button-bar"));


        Optional<ButtonType> result = confirmationDialog.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean success = medicationDao.deleteMedication(selectedMedication.getId());
                if (success) {
                    loadMedicationsFromDb(); // Refresh table
                    // Clear details view as the item is gone
                    showMedicationDetails(null); 
                    showSuccessDialog("Medication Deleted", "Medication '" + selectedMedication.getName() + "' successfully deleted.");
                } else {
                    showErrorDialog("Deletion Failed", "Could not delete the medication from the database. It might have already been deleted.");
                }
            } catch (SQLException e) {
                System.err.println("Error deleting medication from database: " + e.getMessage());
                e.printStackTrace();
                showErrorDialog("Database Error", "Could not delete the medication: " + e.getMessage());
            }
        }
    }

    private void handleSearchAction(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            loadMedicationsFromDb(); // Reload all if search term is empty
        } else {
            try {
                List<Medication> searchResults = medicationDao.searchMedications(searchTerm.trim());
                medicationData.clear();
                medicationData.addAll(searchResults);
                if (searchResults.isEmpty()) {
                    medicationTable.setPlaceholder(new Label("No medications found matching your search."));
                }
                System.out.println("Found " + searchResults.size() + " medications matching '" + searchTerm + "'.");
            } catch (SQLException e) {
                System.err.println("Error searching medications: " + e.getMessage());
                e.printStackTrace();
                showErrorDialog("Database Error", "Error while searching medications.");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

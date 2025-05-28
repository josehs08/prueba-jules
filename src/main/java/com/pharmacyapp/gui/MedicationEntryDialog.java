package com.pharmacyapp.gui;

import com.pharmacyapp.model.Medication;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.Optional;

public class MedicationEntryDialog extends Dialog<Medication> {

    private TextField nameField;
    private TextField dosageField;
    private Spinner<Integer> quantitySpinner;
    private DatePicker expirationDatePicker;
    private TextField manufacturerField;
    private TextField lotNumberField;

    private Medication medicationToEdit; // Used if editing an existing medication

    public MedicationEntryDialog(Medication medicationToEdit) {
        this.medicationToEdit = medicationToEdit;

        setTitle(medicationToEdit == null ? "Add New Medication" : "Edit Medication");
        initModality(Modality.APPLICATION_MODAL); // Block other windows

        // Get the DialogPane and apply styling
        DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        dialogPane.getStyleClass().add("dialog-pane"); // General dialog styling
        dialogPane.lookup(".button-bar").getStyleClass().add("dialog-button-bar");


        setupFields();
        dialogPane.setContent(createGridPane());

        // Add Save and Cancel buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Style the buttons
        Button saveButton = (Button) dialogPane.lookupButton(saveButtonType);
        saveButton.getStyleClass().add("dialog-button");
        Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        cancelButton.getStyleClass().add("dialog-button");


        if (medicationToEdit != null) {
            prefillFields(medicationToEdit);
        }

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return processSave();
            }
            return null; // Cancel or close
        });
    }

    private void setupFields() {
        nameField = new TextField();
        nameField.setPromptText("e.g., Amoxicillin");
        nameField.getStyleClass().add("text-field"); // Ensure CSS is applied

        dosageField = new TextField();
        dosageField.setPromptText("e.g., 250mg");
        dosageField.getStyleClass().add("text-field");

        quantitySpinner = new Spinner<>(0, 9999, 0); // Min, Max, Initial
        quantitySpinner.setEditable(true); // Allow typing
        quantitySpinner.getStyleClass().add("spinner");
        // Workaround for Spinner styling if direct .spinner class doesn't work well:
        quantitySpinner.getEditor().getStyleClass().add("text-field");


        expirationDatePicker = new DatePicker();
        expirationDatePicker.setPromptText("Select date");
        expirationDatePicker.getStyleClass().add("date-picker");
        // Ensure the text field part of DatePicker is also styled
        expirationDatePicker.getEditor().getStyleClass().add("text-field");


        manufacturerField = new TextField();
        manufacturerField.setPromptText("e.g., PharmaCo");
        manufacturerField.getStyleClass().add("text-field");

        lotNumberField = new TextField();
        lotNumberField.setPromptText("e.g., LOT12345");
        lotNumberField.getStyleClass().add("text-field");
    }

    private GridPane createGridPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10)); // TRBL
        grid.getStyleClass().add("dialog-grid");

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        grid.add(new Label("Dosage:"), 0, 1);
        grid.add(dosageField, 1, 1);

        grid.add(new Label("Quantity:"), 0, 2);
        grid.add(quantitySpinner, 1, 2);

        grid.add(new Label("Expiration Date:"), 0, 3);
        grid.add(expirationDatePicker, 1, 3);

        grid.add(new Label("Manufacturer:"), 0, 4);
        grid.add(manufacturerField, 1, 4);

        grid.add(new Label("Lot Number:"), 0, 5);
        grid.add(lotNumberField, 1, 5);
        
        // Apply .label style to labels in the grid
        grid.getChildren().filtered(node -> node instanceof Label).forEach(node -> node.getStyleClass().add("dialog-label"));

        return grid;
    }

    private void prefillFields(Medication med) {
        nameField.setText(med.getName());
        dosageField.setText(med.getDosage());
        quantitySpinner.getValueFactory().setValue(med.getQuantity());
        if (med.getExpirationDate() != null) {
            expirationDatePicker.setValue(med.getExpirationDate());
        }
        manufacturerField.setText(med.getManufacturer());
        lotNumberField.setText(med.getLotNumber());
    }

    private Medication processSave() {
        String name = nameField.getText();
        String dosage = dosageField.getText();
        Integer quantity = quantitySpinner.getValue();
        LocalDate expiryDate = expirationDatePicker.getValue();
        String manufacturer = manufacturerField.getText();
        String lotNumber = lotNumberField.getText();

        // Validation
        StringBuilder errors = new StringBuilder();
        if (name == null || name.trim().isEmpty()) {
            errors.append("Name cannot be empty.\n");
        }
        if (quantity == null || quantity < 0) {
            errors.append("Quantity must be a non-negative number.\n");
        }
        if (expiryDate == null) {
            errors.append("Expiration date must be selected.\n");
        } else if (expiryDate.isBefore(LocalDate.now())) {
            errors.append("Expiration date cannot be in the past.\n");
        }

        if (errors.length() > 0) {
            showValidationError(errors.toString());
            return null; // Validation failed, do not close dialog / return null
        }

        if (medicationToEdit == null) { // Creating new medication
            return new Medication(name.trim(), dosage.trim(), quantity, expiryDate, manufacturer.trim(), lotNumber.trim());
        } else { // Updating existing medication
            medicationToEdit.setName(name.trim());
            medicationToEdit.setDosage(dosage.trim());
            medicationToEdit.setQuantity(quantity);
            medicationToEdit.setExpirationDate(expiryDate);
            medicationToEdit.setManufacturer(manufacturer.trim());
            medicationToEdit.setLotNumber(lotNumber.trim());
            return medicationToEdit;
        }
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Please correct the following errors:");
        alert.setContentText(message);
        // Apply styling to the alert dialog as well
        DialogPane alertPane = alert.getDialogPane();
        alertPane.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        alertPane.getStyleClass().add("dialog-pane");
        alertPane.lookup(".button-bar").getStyleClass().add("dialog-button-bar");
        alert.showAndWait();
    }
}

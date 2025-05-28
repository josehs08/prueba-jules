package com.pharmacyapp.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import java.time.LocalDate;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class Medication {
    private final SimpleIntegerProperty id; // New field
    private final SimpleStringProperty name;
    private final SimpleStringProperty dosage;
    private final SimpleIntegerProperty quantity;
    private final ObjectProperty<LocalDate> expirationDate;
    private final SimpleStringProperty manufacturer;
    private final SimpleStringProperty lotNumber;

    // Constructor for new medications (ID not yet known, defaults to 0 or a convention)
    public Medication(String name, String dosage, int quantity, LocalDate expirationDate, String manufacturer, String lotNumber) {
        this(0, name, dosage, quantity, expirationDate, manufacturer, lotNumber); // Default ID to 0
    }

    // Constructor for medications retrieved from DB (ID is known)
    public Medication(int id, String name, String dosage, int quantity, LocalDate expirationDate, String manufacturer, String lotNumber) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.dosage = new SimpleStringProperty(dosage);
        this.quantity = new SimpleIntegerProperty(quantity);
        this.expirationDate = new SimpleObjectProperty<>(expirationDate);
        this.manufacturer = new SimpleStringProperty(manufacturer);
        this.lotNumber = new SimpleStringProperty(lotNumber);
    }

    // ID
    public int getId() {
        return id.get();
    }
    public SimpleIntegerProperty idProperty() {
        return id;
    }
    public void setId(int id) {
        this.id.set(id);
    }

    // Name
    public String getName() {
        return name.get();
    }
    public SimpleStringProperty nameProperty() {
        return name;
    }
    public void setName(String name) {
        this.name.set(name);
    }

    // Dosage
    public String getDosage() {
        return dosage.get();
    }
    public SimpleStringProperty dosageProperty() {
        return dosage;
    }
    public void setDosage(String dosage) {
        this.dosage.set(dosage);
    }

    // Quantity
    public int getQuantity() {
        return quantity.get();
    }
    public SimpleIntegerProperty quantityProperty() {
        return quantity;
    }
    public void setQuantity(int quantity) {
        this.quantity.set(quantity);
    }

    // Expiration Date
    public LocalDate getExpirationDate() {
        return expirationDate.get();
    }
    public ObjectProperty<LocalDate> expirationDateProperty() {
        return expirationDate;
    }
    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate.set(expirationDate);
    }

    // Manufacturer
    public String getManufacturer() {
        return manufacturer.get();
    }
    public SimpleStringProperty manufacturerProperty() {
        return manufacturer;
    }
    public void setManufacturer(String manufacturer) {
        this.manufacturer.set(manufacturer);
    }

    // Lot Number
    public String getLotNumber() {
        return lotNumber.get();
    }
    public SimpleStringProperty lotNumberProperty() {
        return lotNumber;
    }
    public void setLotNumber(String lotNumber) {
        this.lotNumber.set(lotNumber);
    }
}

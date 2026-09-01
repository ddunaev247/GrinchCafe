package com.example.deviceinfo.model;

public class RestaurantTable {
    public static final String DEFAULT_LABEL = "Стол";

    private long id;
    private int number;
    private String label;
    private String description;
    private TableStatus status;
    private int openOrderNumber;
    private int printVersion;

    public RestaurantTable() {
        status = TableStatus.FREE;
        label = DEFAULT_LABEL;
        openOrderNumber = 0;
        printVersion = 0;
    }

    public RestaurantTable(long id, int number, String description, TableStatus status) {
        this(id, number, description, status, 0, 0);
    }

    public RestaurantTable(long id, int number, String description, TableStatus status,
                           int openOrderNumber) {
        this(id, number, description, status, openOrderNumber, 0);
    }

    public RestaurantTable(long id, int number, String description, TableStatus status,
                           int openOrderNumber, int printVersion) {
        this.id = id;
        this.number = number;
        this.label = DEFAULT_LABEL;
        this.description = description;
        this.status = status;
        this.openOrderNumber = openOrderNumber;
        this.printVersion = printVersion;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDisplayLabel() {
        if (label == null || label.trim().isEmpty()) {
            return DEFAULT_LABEL;
        }
        return label.trim();
    }

    public String formatCardTitle() {
        return getDisplayLabel() + " №" + number;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public int getOpenOrderNumber() {
        return openOrderNumber;
    }

    public void setOpenOrderNumber(int openOrderNumber) {
        this.openOrderNumber = openOrderNumber;
    }

    public int getPrintVersion() {
        return printVersion;
    }

    public void setPrintVersion(int printVersion) {
        this.printVersion = printVersion;
    }
}

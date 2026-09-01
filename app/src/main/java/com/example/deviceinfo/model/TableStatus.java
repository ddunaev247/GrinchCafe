package com.example.deviceinfo.model;

public enum TableStatus {
    FREE("Свободен"),
    BUSY("Занят"),
    RESERVED("Резерв");

    private final String displayName;

    TableStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TableStatus fromString(String value) {
        if (value == null) {
            return FREE;
        }
        for (TableStatus status : values()) {
            if (status.name().equals(value) || status.displayName.equals(value)) {
                return status;
            }
        }
        return FREE;
    }

    public static String[] displayNames() {
        TableStatus[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}

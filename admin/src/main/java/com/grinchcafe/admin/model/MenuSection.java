package com.grinchcafe.admin.model;

public enum MenuSection {
    LUNCH("Обеденное"),
    MAIN("Основное"),
    BAR("Бар");

    private final String displayName;

    MenuSection(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MenuSection fromString(String value) {
        if (value == null) {
            return MAIN;
        }
        for (MenuSection section : values()) {
            if (section.name().equals(value) || section.displayName.equals(value)) {
                return section;
            }
        }
        return MAIN;
    }

    public static String[] displayNames() {
        MenuSection[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    /** Разделы, в которых сейчас ведётся меню (Бар пока без позиций). */
    public static MenuSection[] editableSections() {
        return new MenuSection[]{LUNCH, MAIN};
    }

    public static String[] editableDisplayNames() {
        MenuSection[] sections = editableSections();
        String[] names = new String[sections.length];
        for (int i = 0; i < sections.length; i++) {
            names[i] = sections[i].displayName;
        }
        return names;
    }

    public static int editableIndex(MenuSection section) {
        MenuSection[] sections = editableSections();
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == section) {
                return i;
            }
        }
        return 1; // MAIN
    }

    /** Default section for legacy items based on print category. */
    public static MenuSection fromCategory(MenuCategory category) {
        if (category == MenuCategory.DRINK) {
            return BAR;
        }
        return MAIN;
    }
}

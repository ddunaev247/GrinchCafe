package com.grinchcafe.waiter.model;

public enum MenuCategory {
    SOUP("Суп", 1),
    SECOND("Горячее", 2),
    SALAD("Салат", 3),
    BREAD("Хлеб", 4),

    MAIN("Основное блюдо", 1),
    APPETIZER("Закуски", 4),

    DRINK("Напитки", 5),
    OWN_DRINKS("Напитки собственного производства", 5),
    OTHER("Дополнительно", 6);

    private final String displayName;
    private final int printPriority;

    MenuCategory(String displayName, int printPriority) {
        this.displayName = displayName;
        this.printPriority = printPriority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPrintPriority() {
        return printPriority;
    }

    public static MenuCategory fromString(String value) {
        if (value == null) {
            return OTHER;
        }
        for (MenuCategory category : values()) {
            if (category.name().equals(value) || category.displayName.equals(value)) {
                return category;
            }
        }
        return OTHER;
    }

    public static String[] displayNames() {
        MenuCategory[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}

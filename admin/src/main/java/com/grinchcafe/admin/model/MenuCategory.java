package com.grinchcafe.admin.model;

public enum MenuCategory {
    // Обеденные категории (в "Комплекс" участвуют только SOUP/SECOND/SALAD)
    SOUP("Суп", 1),
    SECOND("Горячее", 2),
    SALAD("Салат", 3),
    BREAD("Хлеб", 4),

    // Основное меню
    MAIN("Основное блюдо", 1),
    APPETIZER("Закуски", 4),

    // Напитки / дополнительно
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

    /** Категории обеденного меню (в "Комплекс" участвуют только первые 3). */
    public static MenuCategory[] lunchCategories() {
        return new MenuCategory[]{SOUP, SECOND, SALAD, BREAD, OWN_DRINKS, OTHER};
    }

    public static String[] lunchDisplayNames() {
        return new String[]{"Суп", "Горячее", "Салат", "Хлеб",
                "Напитки собственного производства", "Дополнительно"};
    }

    public static int lunchIndex(MenuCategory category) {
        MenuCategory[] values = lunchCategories();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == category) {
                return i;
            }
        }
        return 0;
    }

    public static MenuCategory[] mainMenuCategories() {
        return new MenuCategory[]{MAIN, SECOND, SALAD, BREAD, APPETIZER, DRINK, OWN_DRINKS, OTHER};
    }

    public static String[] mainMenuDisplayNames() {
        MenuCategory[] values = mainMenuCategories();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    public static int mainMenuIndex(MenuCategory category) {
        MenuCategory[] values = mainMenuCategories();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == category) {
                return i;
            }
        }
        return 0;
    }

    private static boolean containsAny(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) {
                return true;
            }
        }
        return false;
    }

    public static MenuCategory fromImport(String value, MenuSection section) {
        String text = value == null ? "" : value.trim().toLowerCase().replace('ё', 'е');

        if (containsAny(text, "хлеб")) {
            return BREAD;
        }

        if (section == MenuSection.LUNCH) {
            if (containsAny(text, "напит") && containsAny(text, "собствен")) {
                return OWN_DRINKS;
            }
            if (containsAny(text, "дополн")) {
                return OTHER;
            }
            if (containsAny(text, "салат")) {
                return SALAD;
            }
            if (containsAny(text, "горяч", "втор", "гарнир")) {
                return SECOND;
            }
            if (containsAny(text, "суп")) {
                return SOUP;
            }
            return SOUP;
        }

        // MAIN / BAR
        if (containsAny(text, "напит") && containsAny(text, "собствен")) {
            return OWN_DRINKS;
        }
        if (containsAny(text, "дополн")) {
            return OTHER;
        }
        if (containsAny(text, "суп")) {
            return SOUP;
        }
        if (containsAny(text, "основн")) {
            return MAIN;
        }
        if (containsAny(text, "салат")) {
            return SALAD;
        }
        if (containsAny(text, "горяч", "втор", "гарнир")) {
            return SECOND;
        }
        if (containsAny(text, "закуск")) {
            return APPETIZER;
        }
        if (containsAny(text, "напит", "пиво", "вино", "коктейл")) {
            return DRINK;
        }
        return OTHER;
    }

    public static MenuCategory[] barCategories() {
        // Бар пока может редактировать напитки/закуски/дополнительно
        return new MenuCategory[]{DRINK, OWN_DRINKS, APPETIZER, OTHER};
    }

    public static String[] barDisplayNames() {
        MenuCategory[] values = barCategories();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }

    public static int barIndex(MenuCategory category) {
        MenuCategory[] values = barCategories();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == category) {
                return i;
            }
        }
        return 0;
    }

    public static boolean isBarCategory(MenuCategory category) {
        if (category == null) {
            return false;
        }
        for (MenuCategory barCategory : barCategories()) {
            if (barCategory == category) {
                return true;
            }
        }
        return false;
    }
}

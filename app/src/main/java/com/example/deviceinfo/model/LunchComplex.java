package com.example.deviceinfo.model;

import java.util.ArrayList;
import java.util.List;

public final class LunchComplex {

    public static final long MENU_ITEM_ID = -1L;
    public static final String NAME_PREFIX = "Комплекс: ";

    public enum Slot {
        SOUP("Суп"),
        HOT("Горячее"),
        SALAD("Салат");

        private final String title;

        Slot(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    private LunchComplex() {
    }

    public static boolean matches(MenuItem item, Slot slot) {
        if (item == null || item.isComplex() || item.getSection() != MenuSection.LUNCH) {
            return false;
        }
        MenuCategory category = item.getCategory();
        switch (slot) {
            case SOUP:
                return category == MenuCategory.SOUP;
            case HOT:
                return category == MenuCategory.SECOND;
            case SALAD:
                return category == MenuCategory.SALAD;
            default:
                return false;
        }
    }

    public static List<MenuItem> filter(List<MenuItem> items, Slot slot) {
        List<MenuItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (MenuItem item : items) {
            if (matches(item, slot)) {
                result.add(item);
            }
        }
        return result;
    }

    public static String formatCheckName(MenuItem soup, MenuItem hot, MenuItem salad) {
        return NAME_PREFIX + soup.getName() + ", " + hot.getName() + ", " + salad.getName();
    }

    public static boolean isComboLine(OrderLine line) {
        if (line == null) {
            return false;
        }
        if (line.getMenuItemId() == MENU_ITEM_ID) {
            return true;
        }
        return line.getName() != null && line.getName().startsWith(NAME_PREFIX);
    }
}

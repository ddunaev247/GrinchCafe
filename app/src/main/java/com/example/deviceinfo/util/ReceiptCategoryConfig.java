package com.example.deviceinfo.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.deviceinfo.model.MenuCategory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Какие категории меню попадают в барный и кухонный чеки. */
public final class ReceiptCategoryConfig {

    public enum Target {
        BAR,
        KITCHEN
    }

    private static final String PREFS = "receipt_category_prefs";
    private static final String KEY_BAR = "bar_categories";
    private static final String KEY_KITCHEN = "kitchen_categories";
    private static final String KEY_BAR_KNOWN = "bar_known_categories";
    private static final String KEY_KITCHEN_KNOWN = "kitchen_known_categories";

    private static final Comparator<MenuCategory> CATEGORY_ORDER = new Comparator<MenuCategory>() {
        @Override
        public int compare(MenuCategory left, MenuCategory right) {
            int byPriority = Integer.compare(left.getPrintPriority(), right.getPrintPriority());
            if (byPriority != 0) {
                return byPriority;
            }
            return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
        }
    };

    private ReceiptCategoryConfig() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Печатать ли категорию на выбранном принтере.
     * Если ситуация неоднозначна (неизвестная/новая категория) — печатаем, чтобы не потерять позиции.
     */
    public static boolean isEnabled(Context context, Target target, MenuCategory category) {
        if (category == null) {
            return true;
        }

        Set<String> enabled = readStored(context, target);
        if (enabled == null) {
            return defaultEnabled(target, category);
        }
        if (enabled.contains(category.name())) {
            return true;
        }

        Set<String> known = readKnown(context, target);
        if (known == null || !known.contains(category.name())) {
            return true;
        }
        return false;
    }

    public static Set<MenuCategory> getEnabledCategories(Context context, Target target,
                                                         List<MenuCategory> allCategories) {
        Set<String> stored = readStored(context, target);
        Set<String> known = readKnown(context, target);
        Set<MenuCategory> result = new HashSet<>();
        for (MenuCategory category : allCategories) {
            if (isCategoryEnabled(stored, known, target, category)) {
                result.add(category);
            }
        }
        return result;
    }

    public static Set<MenuCategory> getDefaultCategories(List<MenuCategory> allCategories, Target target) {
        Set<MenuCategory> result = new HashSet<>();
        for (MenuCategory category : allCategories) {
            if (defaultEnabled(target, category)) {
                result.add(category);
            }
        }
        return result;
    }

    public static void save(Context context, Target target, Set<MenuCategory> enabled,
                            List<MenuCategory> allCategories) {
        String[] enabledNames = categoryNames(enabled);
        Arrays.sort(enabledNames);

        String[] knownNames = categoryNames(new HashSet<>(allCategories));
        Arrays.sort(knownNames);

        prefs(context).edit()
                .putString(prefKey(target), TextUtils.join(",", enabledNames))
                .putString(knownPrefKey(target), TextUtils.join(",", knownNames))
                .apply();
    }

    public static void resetToDefaults(Context context, Target target) {
        prefs(context).edit()
                .remove(prefKey(target))
                .remove(knownPrefKey(target))
                .apply();
    }

    public static void sortCategories(List<MenuCategory> categories) {
        Collections.sort(categories, CATEGORY_ORDER);
    }

    private static boolean isCategoryEnabled(Set<String> stored, Set<String> known,
                                             Target target, MenuCategory category) {
        if (stored == null) {
            return defaultEnabled(target, category);
        }
        if (stored.contains(category.name())) {
            return true;
        }
        if (known == null || !known.contains(category.name())) {
            return true;
        }
        return false;
    }

    private static boolean defaultEnabled(Target target, MenuCategory category) {
        if (target == Target.BAR) {
            return true;
        }
        return !MenuCategory.isBarCategory(category);
    }

    private static String[] categoryNames(Set<MenuCategory> categories) {
        String[] names = new String[categories.size()];
        int index = 0;
        for (MenuCategory category : categories) {
            names[index++] = category.name();
        }
        return names;
    }

    private static String prefKey(Target target) {
        return target == Target.BAR ? KEY_BAR : KEY_KITCHEN;
    }

    private static String knownPrefKey(Target target) {
        return target == Target.BAR ? KEY_BAR_KNOWN : KEY_KITCHEN_KNOWN;
    }

    private static Set<String> readStored(Context context, Target target) {
        return readCategorySet(prefs(context).getString(prefKey(target), null));
    }

    private static Set<String> readKnown(Context context, Target target) {
        return readCategorySet(prefs(context).getString(knownPrefKey(target), null));
    }

    private static Set<String> readCategorySet(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return null;
        }
        Set<String> result = new HashSet<>();
        for (String part : raw.split(",")) {
            if (part.trim().length() > 0) {
                result.add(part.trim());
            }
        }
        return result;
    }
}

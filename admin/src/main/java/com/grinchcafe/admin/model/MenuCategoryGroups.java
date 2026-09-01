package com.grinchcafe.admin.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuCategoryGroups {

    private MenuCategoryGroups() {
    }

    public static Map<String, List<MenuItem>> groupByCategoryText(List<MenuItem> items) {
        LinkedHashMap<String, List<MenuItem>> groups = new LinkedHashMap<>();
        if (items == null) {
            return groups;
        }
        for (MenuItem item : items) {
            String key = item.getCategoryText();
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<MenuItem>());
            }
            groups.get(key).add(item);
        }
        return groups;
    }
}

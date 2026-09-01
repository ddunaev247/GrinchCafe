package com.grinchcafe.waiter.util;

import com.grinchcafe.waiter.model.MenuCategory;
import com.grinchcafe.waiter.model.OrderLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class CategorySorter {

    private CategorySorter() {
    }

    public static List<OrderLine> sortForPrint(List<OrderLine> lines) {
        List<OrderLine> sorted = new ArrayList<>(lines);
        Collections.sort(sorted, new Comparator<OrderLine>() {
            @Override
            public int compare(OrderLine a, OrderLine b) {
                int priorityCompare = Integer.compare(
                        a.getCategory().getPrintPriority(),
                        b.getCategory().getPrintPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return sorted;
    }
}

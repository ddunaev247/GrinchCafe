package com.example.deviceinfo.model;

public class OrderLine {
    private long id;
    private long tableId;
    private long menuItemId;
    private String name;
    private double itemQuantity;
    private String unit;
    private MenuCategory category;
    private double price;
    private int count;
    private int printedCount;

    public OrderLine() {
        count = 1;
        printedCount = 0;
        category = MenuCategory.OTHER;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTableId() {
        return tableId;
    }

    public void setTableId(long tableId) {
        this.tableId = tableId;
    }

    public long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getItemQuantity() {
        return itemQuantity;
    }

    public void setItemQuantity(double itemQuantity) {
        this.itemQuantity = itemQuantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public void setCategory(MenuCategory category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getPrintedCount() {
        return printedCount;
    }

    public void setPrintedCount(int printedCount) {
        this.printedCount = printedCount;
    }

    public double getLineTotal() {
        return price * count;
    }

    public String formatReceiptLine(int index) {
        if (LunchComplex.isComboLine(this)) {
            return index + ". " + name + " " + count + "шт";
        }
        String qty = count + "шт/" + formatItemAmount();
        return index + ". " + name + " " + qty;
    }

    private String formatItemAmount() {
        if (itemQuantity == 0 && unit != null && unit.trim().length() > 0) {
            return unit.trim();
        }
        String amount = itemQuantity == (long) itemQuantity
                ? String.valueOf((long) itemQuantity)
                : String.valueOf(itemQuantity);
        return amount + unit;
    }
}

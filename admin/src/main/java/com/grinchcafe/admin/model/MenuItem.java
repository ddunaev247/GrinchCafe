package com.grinchcafe.admin.model;

public class MenuItem {
    private long id;
    private String name;
    private String description;
    private double quantity;
    private String unit;
    private MenuCategory category;
    // Категория как в Excel (отображение и группировка).
    private String categoryText;
    private String amountText;
    private MenuSection section;
    private boolean complex;
    private double price;

    public MenuItem() {
        category = MenuCategory.OTHER;
        categoryText = null;
        amountText = null;
        section = MenuSection.MAIN;
        unit = "шт";
        quantity = 1;
        complex = false;
    }

    public MenuItem(long id, String name, String description, double quantity, String unit,
                    MenuCategory category, double price) {
        this(id, name, description, quantity, unit, category,
                MenuSection.fromCategory(category), false, price);
    }

    public MenuItem(long id, String name, String description, double quantity, String unit,
                    MenuCategory category, MenuSection section, boolean complex, double price) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.category = category;
        this.section = section != null ? section : MenuSection.MAIN;
        this.complex = complex;
        this.price = price;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
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

    public String getCategoryText() {
        if (categoryText != null && categoryText.trim().length() > 0) {
            return categoryText.trim();
        }
        return category != null ? category.getDisplayName() : "";
    }

    public void setCategoryText(String categoryText) {
        this.categoryText = categoryText;
    }

    public String getAmountText() {
        return amountText;
    }

    public void setAmountText(String amountText) {
        this.amountText = amountText;
    }

    public MenuSection getSection() {
        return section;
    }

    public void setSection(MenuSection section) {
        this.section = section != null ? section : MenuSection.MAIN;
    }

    public boolean isComplex() {
        return complex;
    }

    public void setComplex(boolean complex) {
        this.complex = complex;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String formatAmount() {
        if (amountText != null && amountText.trim().length() > 0) {
            return amountText.trim();
        }
        String qty = quantity == (long) quantity ? String.valueOf((long) quantity) : String.valueOf(quantity);
        return qty + " " + unit;
    }
}

package com.grinchcafe.waiter.model;

public class OrderHistory {
    private long id;
    private long paidAt;
    private int tableNumber;
    private int orderNumber;
    private String itemsText;
    private double totalAmount;
    private int itemCount;

    public OrderHistory() {
    }

    public OrderHistory(long id, long paidAt, int tableNumber, String itemsText,
                        double totalAmount, int itemCount, int orderNumber) {
        this.id = id;
        this.paidAt = paidAt;
        this.tableNumber = tableNumber;
        this.itemsText = itemsText;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
        this.orderNumber = orderNumber;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(long paidAt) {
        this.paidAt = paidAt;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(int orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getItemsText() {
        return itemsText;
    }

    public void setItemsText(String itemsText) {
        this.itemsText = itemsText;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }
}

package com.bforbank.sample.domain;

public class Account {
    private String id;
    private double balance;
    private boolean active;

    public Account(String id, double balance, boolean active) {
        this.id = id;
        this.balance = balance;
        this.active = active;
    }

    public String getId() { return id; }
    public double getBalance() { return balance; }
    public boolean isActive() { return active; }

    public void withdraw(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdraw amount must be positive");
        }
        if (this.balance < amount) {
            throw new InsufficientFundsException("Insufficient balance for account: " + id);
        }
        this.balance -= amount;
    }

    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        this.balance += amount;
    }
}

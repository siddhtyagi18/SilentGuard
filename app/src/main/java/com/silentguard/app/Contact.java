package com.silentguard.app;

public class Contact {
    String name;
    String phone;
    String relation;

    public Contact(String name, String phone, String relation) {
        this.name = name;
        this.phone = phone;
        this.relation = relation;
    }

    public Contact(String name, String phone) {
        this(name, phone, "");
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getRelation() {
        return relation;
    }
}

package com.lingpets.model;

import java.util.UUID;

public class Pet {
    public String id;
    public String headId;
    public double x;
    public double y;
    public double size; // diameter in pixels

    public Pet() {}

    public Pet(String headId, double x, double y, double size) {
        this.id = UUID.randomUUID().toString();
        this.headId = headId;
        this.x = x;
        this.y = y;
        this.size = size;
    }
}

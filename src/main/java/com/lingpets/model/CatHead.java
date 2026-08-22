package com.lingpets.model;

import java.util.UUID;

public class CatHead {
    public String id;
    public String fileName;   // e.g. "abc123.png" under ~/.catpets/heads/
    public String sourceName; // original uploaded file name

    public CatHead() {}

    public CatHead(String fileName, String sourceName) {
        this.id = UUID.randomUUID().toString();
        this.fileName = fileName;
        this.sourceName = sourceName;
    }
}
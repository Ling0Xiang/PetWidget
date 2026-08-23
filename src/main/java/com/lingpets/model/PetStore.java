package com.lingpets.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth. All disk I/O goes through here.
 * Layout: ~/.catpets/pets.json + ~/.catpets/heads/<fileName>
 */
public class PetStore {

    private static final Path ROOT = Path.of(System.getProperty("user.home"), ".catpets");
    private static final Path HEADS_DIR = ROOT.resolve("heads");
    private static final Path JSON_FILE = ROOT.resolve("pets.json");
    private static final double DEFAULT_SIZE = 120;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<CatHead> heads = new ArrayList<>();
    private final List<Pet> pets = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Lifecycle

    public PetStore() {
        try {
            Files.createDirectories(HEADS_DIR);
            load();
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialise ~/.catpets", e);
        }
    }

    // -------------------------------------------------------------------------
    // Heads

    /** Saves the cropped head image to disk and registers it in the store. */
    public CatHead addHead(BufferedImage headImage, String sourceName) throws IOException {
        CatHead head = new CatHead(java.util.UUID.randomUUID() + ".png", sourceName);
        ImageIO.write(headImage, "PNG", HEADS_DIR.resolve(head.fileName).toFile());
        heads.add(head);
        save();
        return head;
    }

    /**
     * Removes a head and all pets that reference it.
     * Also deletes the head PNG from disk.
     */
    public void removeHead(String headId) throws IOException {
        CatHead head = findHead(headId).orElseThrow(() ->
                new IllegalArgumentException("Unknown headId: " + headId));

        pets.removeIf(p -> p.headId.equals(headId));
        heads.remove(head);

        Files.deleteIfExists(HEADS_DIR.resolve(head.fileName));
        save();
    }

    public Optional<CatHead> findHead(String headId) {
        return heads.stream().filter(h -> h.id.equals(headId)).findFirst();
    }

    public List<CatHead> getHeads() {
        return List.copyOf(heads);
    }

    public Path headImagePath(CatHead head) {
        return HEADS_DIR.resolve(head.fileName);
    }

    // -------------------------------------------------------------------------
    // Pets

    /** Places a new pet widget using the given head at the given screen position. */
    public Pet addPet(String headId, double x, double y) throws IOException {
        Pet pet = new Pet(headId, x, y, DEFAULT_SIZE);
        pets.add(pet);
        save();
        return pet;
    }

    public void updatePosition(String petId, double x, double y) throws IOException {
        findPet(petId).ifPresent(p -> { p.x = x; p.y = y; });
        save();
    }

    public void updateSize(String petId, double size) throws IOException {
        findPet(petId).ifPresent(p -> p.size = size);
        save();
    }

    public void updateHead(String petId, String headId) throws IOException {
        findPet(petId).ifPresent(p -> p.headId = headId);
        save();
    }

    public void removePet(String petId) throws IOException {
        pets.removeIf(p -> p.id.equals(petId));
        save();
    }

    public Optional<Pet> findPet(String petId) {
        return pets.stream().filter(p -> p.id.equals(petId)).findFirst();
    }

    public List<Pet> getPets() {
        return List.copyOf(pets);
    }

    // -------------------------------------------------------------------------
    // Persistence

    private record Store(List<CatHead> heads, List<Pet> pets) {}

    private void save() throws IOException {
        try (Writer w = Files.newBufferedWriter(JSON_FILE)) {
            GSON.toJson(new Store(heads, pets), w);
        }
    }

    private void load() throws IOException {
        if (!Files.exists(JSON_FILE)) return;
        try (Reader r = Files.newBufferedReader(JSON_FILE)) {
            Store s = GSON.fromJson(r, Store.class);
            if (s == null) return;
            if (s.heads() != null) heads.addAll(s.heads());
            if (s.pets() != null) pets.addAll(s.pets());
        }
    }
}

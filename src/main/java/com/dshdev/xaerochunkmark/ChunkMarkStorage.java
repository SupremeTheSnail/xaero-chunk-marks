package com.dshdev.xaerochunkmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-world, per-dimension storage for client-side chunk marks.
 *
 * <p>This class deliberately contains no Minecraft or Forge types so that the storage and the JSON format can be
 * exercised outside of the game. It is written to be read from the map processing thread (which renders the
 * highlight colours) while being modified from the client thread (which handles the right-click menu).</p>
 */
public final class ChunkMarkStorage {
    public static final int FORMAT_VERSION = 1;

    /** A chunk is identified by a single long: chunkX in the high 32 bits, chunkZ in the low 32 bits. */
    public static long pack(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    public static int unpackZ(long key) {
        return (int) key;
    }

    /** Xaero's world map regions are 32x32 chunks. */
    public static int regionOf(int chunkCoordinate) {
        return chunkCoordinate >> 5;
    }

    private final Path directory;

    private volatile WorldMarks marks = WorldMarks.empty();
    private boolean dirty;
    private long dirtySince;
    private volatile String lastError;

    public ChunkMarkStorage(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return this.directory;
    }

    /** The key of the world that is currently loaded, or an empty string if nothing was loaded yet. */
    public String currentWorldKey() {
        return this.marks.key;
    }

    /** Loads (or creates) the marks of the given world, saving the previously loaded world first. */
    public synchronized void open(String worldKey) {
        flushBlocking();
        this.marks = load(worldKey);
        this.dirty = false;
    }

    public boolean isMarked(String dimension, int chunkX, int chunkZ) {
        Set<Long> chunks = this.marks.dimensions.get(dimension);
        return chunks != null && chunks.contains(pack(chunkX, chunkZ));
    }

    public boolean regionHasMarks(String dimension, int regionX, int regionZ) {
        Set<Long> chunks = this.marks.dimensions.get(dimension);
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }
        int startX = regionX << 5;
        int startZ = regionZ << 5;
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                if (chunks.contains(pack(startX + x, startZ + z))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A content hash for one region. It is stable for the same set of marks (independent of iteration order), so
     * Xaero's map only rebuilds a region's highlight texture when something actually changed.
     */
    public int regionHash(String dimension, int regionX, int regionZ) {
        int hash = 17;
        hash = hash * 31 + dimension.hashCode();
        Set<Long> chunks = this.marks.dimensions.get(dimension);
        if (chunks != null && !chunks.isEmpty()) {
            int startX = regionX << 5;
            int startZ = regionZ << 5;
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    if (chunks.contains(pack(startX + x, startZ + z))) {
                        hash = hash * 31 + (x * 32 + z + 1);
                    }
                }
            }
        }
        return hash;
    }

    public int countMarked(String dimension, int left, int top, int right, int bottom) {
        Set<Long> chunks = this.marks.dimensions.get(dimension);
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int x = left; x <= right; x++) {
            for (int z = top; z <= bottom; z++) {
                if (chunks.contains(pack(x, z))) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean allMarked(String dimension, int left, int top, int right, int bottom) {
        long area = (long) (right - left + 1) * (long) (bottom - top + 1);
        return area > 0 && countMarked(dimension, left, top, right, bottom) == area;
    }

    /**
     * Marks or unmarks every chunk of the given chunk-coordinate rectangle.
     *
     * @return the number of chunks whose state actually changed
     */
    public int applyRect(String dimension, int left, int top, int right, int bottom, boolean marked) {
        long area = (long) (right - left + 1) * (long) (bottom - top + 1);
        if (area <= 0 || area > 65536) {
            return 0;
        }
        WorldMarks current = this.marks;
        Set<Long> chunks = current.dimensions.computeIfAbsent(dimension, key -> ConcurrentHashMap.newKeySet());
        int changed = 0;
        for (int x = left; x <= right; x++) {
            for (int z = top; z <= bottom; z++) {
                long key = pack(x, z);
                if (marked ? chunks.add(key) : chunks.remove(key)) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            markDirty();
        }
        return changed;
    }

    public void markDirty() {
        if (!this.dirty) {
            this.dirty = true;
            this.dirtySince = System.currentTimeMillis();
        }
    }

    /** Called from the client tick; writes at most once per second so that rapid marking does not hammer the disk. */
    public void flushIfDirty() {
        if (this.dirty && System.currentTimeMillis() - this.dirtySince >= 1000L) {
            flushBlocking();
        }
    }

    public synchronized void flushBlocking() {
        if (!this.dirty) {
            return;
        }
        WorldMarks current = this.marks;
        if (current.key.isEmpty()) {
            this.dirty = false;
            return;
        }
        try {
            write(current);
            this.dirty = false;
        } catch (Throwable t) {
            this.lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /** Returns the last I/O error message and clears it, or null if the last write succeeded. */
    public String pollError() {
        String error = this.lastError;
        this.lastError = null;
        return error;
    }

    public Path fileFor(String worldKey) {
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < worldKey.length() && safe.length() < 48; i++) {
            char c = worldKey.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_') {
                safe.append(c);
            } else {
                safe.append('_');
            }
        }
        if (safe.length() == 0) {
            safe.append("world");
        }
        return this.directory.resolve(safe + "-" + Integer.toHexString(worldKey.hashCode()) + ".json");
    }

    private WorldMarks load(String worldKey) {
        WorldMarks result = new WorldMarks(worldKey);
        Path file = fileFor(worldKey);
        if (!Files.isRegularFile(file)) {
            return result;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IOException("root is not a JSON object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement dimensions = root.get("dimensions");
            if (dimensions != null && dimensions.isJsonObject()) {
                for (Map.Entry<String, JsonElement> dimension : dimensions.getAsJsonObject().entrySet()) {
                    if (!dimension.getValue().isJsonArray()) {
                        continue;
                    }
                    Set<Long> chunks = ConcurrentHashMap.newKeySet();
                    for (JsonElement element : dimension.getValue().getAsJsonArray()) {
                        if (!element.isJsonArray()) {
                            continue;
                        }
                        JsonArray pair = element.getAsJsonArray();
                        if (pair.size() < 2) {
                            continue;
                        }
                        chunks.add(pack(pair.get(0).getAsInt(), pair.get(1).getAsInt()));
                    }
                    if (!chunks.isEmpty()) {
                        result.dimensions.put(dimension.getKey(), chunks);
                    }
                }
            }
            result.loaded = true;
        } catch (Throwable t) {
            this.lastError = "Failed to read " + file + ": " + t.getMessage();
        }
        return result;
    }

    private void write(WorldMarks world) throws IOException {
        Files.createDirectories(this.directory);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("world", world.key);
        JsonObject dimensions = new JsonObject();
        for (Map.Entry<String, Set<Long>> entry : world.dimensions.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<Long> keys = new ArrayList<>(entry.getValue());
            Collections.sort(keys);
            JsonArray array = new JsonArray();
            for (long key : keys) {
                JsonArray pair = new JsonArray();
                pair.add(unpackX(key));
                pair.add(unpackZ(key));
                array.add(pair);
            }
            dimensions.add(entry.getKey(), array);
        }
        root.add("dimensions", dimensions);

        Path target = fileFor(world.key);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveUnsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The mutable state of one world. Replaced as a whole when another world is opened. */
    static final class WorldMarks {
        final String key;
        final ConcurrentHashMap<String, Set<Long>> dimensions = new ConcurrentHashMap<>();
        boolean loaded;

        WorldMarks(String key) {
            this.key = key;
        }

        static WorldMarks empty() {
            return new WorldMarks("");
        }
    }
}

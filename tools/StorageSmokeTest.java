import com.dshdev.xaerochunkmark.ChunkMarkStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class StorageSmokeTest {
    private static int failures;

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0
                ? Files.createDirectories(Path.of(args[0]))
                : Files.createTempDirectory("chunkmark-test");
        try {
            run(root);
        } finally {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        if (failures > 0) {
            System.out.println("FAILED: " + failures + " check(s)");
            System.exit(1);
        }
        System.out.println("OK: all checks passed");
    }

    private static void run(Path root) throws Exception {
        ChunkMarkStorage storage = new ChunkMarkStorage(root);
        storage.open("Singleplayer_My World|mw1");

        check("empty world has no marks", !storage.isMarked("minecraft:overworld", 0, 0));
        check("empty world region has no marks", !storage.regionHasMarks("minecraft:overworld", 0, 0));

        int changed = storage.applyRect("minecraft:overworld", -1, -1, 2, 2, true);
        check("rect marks 16 chunks, changed=" + changed, changed == 16);
        check("chunk inside rect is marked", storage.isMarked("minecraft:overworld", -1, 2));
        check("chunk outside rect is not marked", !storage.isMarked("minecraft:overworld", 3, 2));
        check("region -1,-1 has marks", storage.regionHasMarks("minecraft:overworld", -1, -1));
        check("region 0,0 has marks", storage.regionHasMarks("minecraft:overworld", 0, 0));
        check("allMarked over the rect", storage.allMarked("minecraft:overworld", -1, -1, 2, 2));
        check("allMarked over a bigger rect", !storage.allMarked("minecraft:overworld", -2, -2, 2, 2));
        check("count in bigger rect", storage.countMarked("minecraft:overworld", -2, -2, 2, 2) == 16);

        int hashBefore = storage.regionHash("minecraft:overworld", -1, -1);
        int hashOther = storage.regionHash("minecraft:overworld", 0, 0);
        check("different regions hash differently", hashBefore != hashOther);

        storage.applyRect("minecraft:overworld", 5, 5, 5, 5, true);
        check("adding a mark changes the region hash",
                storage.regionHash("minecraft:overworld", 0, 0) != hashOther);
        check("adding a mark leaves other region hash alone",
                storage.regionHash("minecraft:overworld", -1, -1) == hashBefore);

        storage.applyRect("minecraft:overworld", 5, 5, 5, 5, false);
        check("removing the mark restores the region hash",
                storage.regionHash("minecraft:overworld", 0, 0) == hashOther);

        check("other dimension is empty", !storage.isMarked("minecraft:the_nether", -1, -1));
        storage.applyRect("minecraft:the_nether", 0, 0, 0, 0, true);
        check("nether mark stored", storage.isMarked("minecraft:the_nether", 0, 0));

        storage.applyRect("minecraft:overworld", -1, -1, 2, 0, false);
        check("half unmarked", storage.countMarked("minecraft:overworld", -1, -1, 2, 2) == 8);
        int hashAfterUnmark = storage.regionHash("minecraft:overworld", 0, 0);

        storage.markDirty();
        storage.flushBlocking();
        String error = storage.pollError();
        check("no write error: " + error, error == null);

        Path file = storage.fileFor("Singleplayer_My World|mw1");
        check("file created: " + file, Files.isRegularFile(file));
        String json = Files.readString(file);
        check("json has format", json.contains("\"format\":1"));
        check("json has world key", json.contains("\"world\":\"Singleplayer_My World|mw1\""));
        check("json has overworld", json.contains("\"minecraft:overworld\""));
        check("json has nether", json.contains("\"minecraft:the_nether\""));

        ChunkMarkStorage reloaded = new ChunkMarkStorage(root);
        reloaded.open("Singleplayer_My World|mw1");
        check("marks survive a reload", reloaded.countMarked("minecraft:overworld", -1, -1, 2, 2) == 8);
        check("nether mark survives a reload", reloaded.isMarked("minecraft:the_nether", 0, 0));
        check("reloaded hash matches", reloaded.regionHash("minecraft:overworld", 0, 0) == hashAfterUnmark);

        reloaded.open("Multiplayer_play.example.com|");
        check("different world starts empty", !reloaded.isMarked("minecraft:overworld", -1, -1));
        reloaded.open("Singleplayer_My World|mw1");
        check("switching back restores marks", reloaded.isMarked("minecraft:overworld", 1, 1));

        Path worldFile = reloaded.fileFor("Broken|");
        Files.writeString(worldFile, "{ this is not json");
        ChunkMarkStorage broken = new ChunkMarkStorage(root);
        broken.open("Broken|");
        check("corrupt file yields empty storage", !broken.isMarked("minecraft:overworld", 0, 0));
        check("corrupt file reports an error", broken.pollError() != null);
    }

    private static void check(String what, boolean condition) {
        if (condition) {
            System.out.println("  ok   " + what);
        } else {
            failures++;
            System.out.println("  FAIL " + what);
        }
    }
}
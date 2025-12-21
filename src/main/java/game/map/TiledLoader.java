package main.java.game.map;

import com.google.gson.*;
import main.java.game.ResourcePathResolver;
import main.java.game.physics.Collider;
import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class TiledLoader {

    // Mask off flip/rotation bits from Tiled GIDs
    private static final int GID_MASK = 0x1FFFFFFF;

    // Submission default: OFF
    private static final boolean DEBUG = false;

    // Layer name conventions (centralized)
    private static final String LAYER_COLLISION = "Collision";
    private static final String LAYER_ONEWAY_1 = "OneWay";
    private static final String LAYER_ONEWAY_2 = "OneWayCollision";
    private static final String LAYER_GOAL = "Gameplay";

    private static final String OBJ_COLLIDERS = "Colliders";
    private static final String OBJ_TRAPS_1 = "Traps";
    private static final String OBJ_TRAPS_2 = "Hazards";
    private static final String OBJ_GOAL = "Goal";

    public static TiledMap loadJsonMap(String resource) {
        try (InputStream in = TiledLoader.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Missing resource: " + resource);

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            int width = root.get("width").getAsInt();
            int height = root.get("height").getAsInt();
            int tileW = root.get("tilewidth").getAsInt();
            int tileH = root.get("tileheight").getAsInt();

            TiledMap map = new TiledMap(width, height, tileW, tileH);

            loadTilesets(root, resource, map);
            loadLayers(root, map);

            return map;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to load map: " + resource, ex);
        }
    }

    // ---------------- Tilesets ----------------

    private static void loadTilesets(JsonObject root, String mapResource, TiledMap map) throws Exception {
        JsonArray tilesets = root.getAsJsonArray("tilesets");
        if (tilesets == null) return;

        for (JsonElement tse : tilesets) {
            JsonObject tsEntry = tse.getAsJsonObject();
            int firstGid = tsEntry.get("firstgid").getAsInt();

            String imagePath;
            int columns;

            if (tsEntry.has("source")) {
                // External tileset JSON
                String source = tsEntry.get("source").getAsString();

                String mapFolder = folderOf(mapResource);
                String tilesetRes = ResourcePathResolver.resolve(mapFolder, source);
                tilesetRes = ensureLeadingSlash(tilesetRes);

                try (InputStream tsIn = TiledLoader.class.getResourceAsStream(tilesetRes)) {
                    if (tsIn == null) throw new IllegalArgumentException("Missing tileset resource: " + tilesetRes);

                    JsonObject tsRoot = JsonParser.parseReader(new InputStreamReader(tsIn, StandardCharsets.UTF_8))
                            .getAsJsonObject();

                    columns = tsRoot.get("columns").getAsInt();

                    String tilesetFolder = folderOf(tilesetRes);
                    imagePath = ResourcePathResolver.resolve(tilesetFolder, tsRoot.get("image").getAsString());
                }
            } else {
                // Embedded tileset
                columns = tsEntry.get("columns").getAsInt();
                imagePath = tsEntry.get("image").getAsString();
            }

            imagePath = ensureLeadingSlash(imagePath);

            BufferedImage tilesetImage;
            try (InputStream imgIn = TiledLoader.class.getResourceAsStream(imagePath)) {
                if (imgIn == null) throw new IllegalArgumentException("Missing tileset image: " + imagePath);
                tilesetImage = ImageIO.read(imgIn);
            }

            map.addTileset(new TiledMap.Tileset(firstGid, columns, tilesetImage));
        }
    }

    // ---------------- Layers ----------------

    private static void loadLayers(JsonObject root, TiledMap map) {
        JsonArray layers = root.getAsJsonArray("layers");
        if (layers == null) return;

        for (JsonElement le : layers) {
            JsonObject lay = le.getAsJsonObject();
            String type = lay.get("type").getAsString();
            String name = lay.has("name") ? lay.get("name").getAsString() : "";

            if ("tilelayer".equals(type)) {
                loadTileLayer(lay, name, map);
            } else if ("objectgroup".equals(type)) {
                loadObjectLayer(lay, name, map);
            }
        }
    }

    private static void loadTileLayer(JsonObject lay, String name, TiledMap map) {
        JsonArray arr = lay.getAsJsonArray("data");
        if (arr == null) return;

        int width = map.width;
        int height = map.height;

        int[] data = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            int raw = arr.get(i).getAsInt();
            data[i] = raw & GID_MASK;
        }

        // Collision layers (not rendered)
        if (isGoalLayer(name)) {
            addTileCollidersMergedHorizontally(map, data, Collider.Type.GOAL);
            return;
        }

        if (isSolidLayer(name)) {
            addTileCollidersMergedHorizontally(map, data, Collider.Type.SOLID);
            return;
        }

        if (isOneWayLayer(name)) {
            addTileCollidersMergedHorizontally(map, data, Collider.Type.ONE_WAY);
            return;
        }

        // Renderable layers
        map.layers.add(data);
    }

    /**
     * Performance: merge adjacent non-zero tiles horizontally into a single Rect per run.
     * This drastically reduces collider count vs per-tile rectangles.
     */
    private static void addTileCollidersMergedHorizontally(TiledMap map, int[] data, Collider.Type type) {
        int width = map.width;
        int height = map.height;
        int tileW = map.tileWidth;
        int tileH = map.tileHeight;

        for (int ty = 0; ty < height; ty++) {
            int tx = 0;
            while (tx < width) {
                int idx = ty * width + tx;

                if (data[idx] == 0) {
                    tx++;
                    continue;
                }

                // start run
                int startX = tx;
                int endX = tx;

                while (endX + 1 < width && data[ty * width + (endX + 1)] != 0) {
                    endX++;
                }

                int px = startX * tileW;
                int py = ty * tileH;
                int pw = (endX - startX + 1) * tileW;
                int ph = tileH;

                map.colliders.add(new Collider(new Rect(px, py, pw, ph), type));

                tx = endX + 1;
            }
        }

        if (DEBUG) {
            System.out.println("[TILE-COLLIDERS] " + type + " => total colliders now: " + map.colliders.size());
        }
    }

    private static void loadObjectLayer(JsonObject lay, String name, TiledMap map) {
        JsonArray objs = lay.getAsJsonArray("objects");
        if (objs == null) return;

        if (isColliderObjectLayer(name)) {
            loadSolidAndOneWayObjects(objs, map);
            return;
        }

        if (isTrapObjectLayer(name)) {
            loadTrapObjects(objs, map);
        }

        if (isGoalObjectLayer(name)) {
            loadGoalObjects(objs, map);
        }
    }

    private static void loadGoalObjects(JsonArray objs, TiledMap map) {
        if (objs == null || map == null) return;

        int count = 0;

        for (JsonElement oe : objs) {
            JsonObject o = oe.getAsJsonObject();

            String oname = o.has("name") ? o.get("name").getAsString() : "";
            String type  = o.has("type") ? o.get("type").getAsString() : "";

            if (!OBJ_GOAL.equalsIgnoreCase(oname) && !OBJ_GOAL.equalsIgnoreCase(type)) continue;

            int x = (int) Math.round(o.get("x").getAsDouble());
            int y = (int) Math.round(o.get("y").getAsDouble());
            int w = o.has("width") ? (int) Math.round(o.get("width").getAsDouble()) : 0;
            int h = o.has("height") ? (int) Math.round(o.get("height").getAsDouble()) : 0;

            if (w <= 0) w = map.tileWidth;
            if (h <= 0) h = map.tileHeight;

            map.colliders.add(new Collider(new Rect(x, y, w, h), Collider.Type.GOAL));
            count++;
        }

        if (DEBUG) System.out.println("[GOAL] loaded " + count + " goal objects; total colliders now: " + map.colliders.size());
    }

    private static void loadSolidAndOneWayObjects(JsonArray objs, TiledMap map) {
        for (JsonElement oe : objs) {
            JsonObject o = oe.getAsJsonObject();

            int x = (int) Math.round(o.get("x").getAsDouble());
            int y = (int) Math.round(o.get("y").getAsDouble());
            int w = (int) Math.round(o.get("width").getAsDouble());
            int h = (int) Math.round(o.get("height").getAsDouble());

            Collider.Type ctype = Collider.Type.SOLID;

            if (o.has("properties")) {
                JsonArray props = o.getAsJsonArray("properties");
                for (JsonElement pe : props) {
                    JsonObject p = pe.getAsJsonObject();
                    String pname = p.get("name").getAsString();

                    if ("collision".equalsIgnoreCase(pname)) {
                        String v = p.get("value").getAsString();
                        if ("one_way".equalsIgnoreCase(v) || "oneway".equalsIgnoreCase(v)) {
                            ctype = Collider.Type.ONE_WAY;
                        } else {
                            ctype = Collider.Type.SOLID;
                        }
                    }
                }
            }

            map.colliders.add(new Collider(new Rect(x, y, w, h), ctype));
        }

        if (DEBUG) {
            System.out.println("[OBJ-COLLIDERS] loaded Colliders objects, total colliders now: " + map.colliders.size());
        }
    }

    private static void loadTrapObjects(JsonArray objs, TiledMap map) {
        int trapCount = 0;

        for (JsonElement oe : objs) {
            JsonObject o = oe.getAsJsonObject();

            int x = (int) Math.round(o.get("x").getAsDouble());
            int y = (int) Math.round(o.get("y").getAsDouble());
            int w = (int) Math.round(o.get("width").getAsDouble());
            int h = (int) Math.round(o.get("height").getAsDouble());

            String tag = "";
            int damage = 1;

            if (o.has("type")) {
                tag = o.get("type").getAsString();
            }

            if (o.has("properties")) {
                JsonArray props = o.getAsJsonArray("properties");
                for (JsonElement pe : props) {
                    JsonObject p = pe.getAsJsonObject();
                    String pname = p.get("name").getAsString();

                    if ("trapType".equalsIgnoreCase(pname) && tag.isEmpty()) {
                        tag = p.get("value").getAsString();
                    } else if ("damage".equalsIgnoreCase(pname)) {
                        try {
                            damage = p.get("value").getAsInt();
                        } catch (Exception ignored) {
                            damage = Integer.parseInt(p.get("value").getAsString());
                        }
                    }
                }
            }

            if (tag.isEmpty()) tag = "trap";
            map.colliders.add(new Collider(new Rect(x, y, w, h), Collider.Type.TRAP, tag, damage));
            trapCount++;
        }

        if (DEBUG) {
            System.out.println("[TRAPS] loaded " + trapCount + " trap objects; total colliders now: " + map.colliders.size());
        }
    }

    // ---------------- Layer classifiers ----------------

    private static boolean isSolidLayer(String name) {
        return name != null && name.equalsIgnoreCase(LAYER_COLLISION);
    }

    private static boolean isOneWayLayer(String name) {
        if (name == null) return false;
        return name.equalsIgnoreCase(LAYER_ONEWAY_1) || name.equalsIgnoreCase(LAYER_ONEWAY_2);
    }

    private static boolean isGoalLayer(String name) {
        return name != null && name.equalsIgnoreCase(LAYER_GOAL);
    }

    private static boolean isColliderObjectLayer(String name) {
        return name != null && name.equalsIgnoreCase(OBJ_COLLIDERS);
    }

    private static boolean isTrapObjectLayer(String name) {
        if (name == null) return false;
        return name.equalsIgnoreCase(OBJ_TRAPS_1) || name.equalsIgnoreCase(OBJ_TRAPS_2);
    }

    private static boolean isGoalObjectLayer(String name) {
        if (name == null) return false;
        return name.equalsIgnoreCase(LAYER_GOAL) || name.equalsIgnoreCase(OBJ_GOAL);
    }


    // ---------------- Path helpers ----------------

    private static String folderOf(String resourcePath) {
        if (resourcePath == null) return "";
        int i = resourcePath.lastIndexOf('/');
        return (i >= 0) ? resourcePath.substring(0, i + 1) : "";
    }

    private static String ensureLeadingSlash(String p) {
        if (p == null || p.isEmpty()) return p;
        return p.startsWith("/") ? p : "/" + p;
    }
}

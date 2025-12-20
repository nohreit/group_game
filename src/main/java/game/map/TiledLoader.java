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
    private static final boolean DEBUG = true;

    public static TiledMap loadJsonMap(String resource) {
        try (InputStream in = TiledLoader.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("Missing resource: " + resource);

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            int width = root.get("width").getAsInt();
            int height = root.get("height").getAsInt();
            int tileW = root.get("tilewidth").getAsInt();
            int tileH = root.get("tileheight").getAsInt();

            // ✅ Your new constructor
            TiledMap map = new TiledMap(width, height, tileW, tileH);

            // --- Load ALL tilesets declared in the map ---
            JsonArray tilesets = root.getAsJsonArray("tilesets");
            for (JsonElement tse : tilesets) {
                JsonObject tsEntry = tse.getAsJsonObject();
                int firstGid = tsEntry.get("firstgid").getAsInt();

                // Two forms:
                // 1) external tileset: { firstgid, source }
                // 2) embedded tileset: { firstgid, image, columns, ... }
                String imagePath;
                int columns;

                if (tsEntry.has("source")) {
                    String source = tsEntry.get("source").getAsString();

                    // Resolve tileset JSON relative to the map json
                    String mapFolder = resource.substring(0, resource.lastIndexOf('/') + 1);
                    String tilesetRes = ResourcePathResolver.resolve(mapFolder, source);
                    if (!tilesetRes.startsWith("/")) tilesetRes = "/" + tilesetRes;

                    try (InputStream tsIn = TiledLoader.class.getResourceAsStream(tilesetRes)) {
                        if (tsIn == null) throw new IllegalArgumentException("Missing tileset resource: " + tilesetRes);

                        JsonObject tsRoot = JsonParser.parseReader(new InputStreamReader(tsIn, StandardCharsets.UTF_8))
                                .getAsJsonObject();

                        columns = tsRoot.get("columns").getAsInt();

                        // Resolve image relative to the tileset JSON location
                        String tilesetFolder = tilesetRes.substring(0, tilesetRes.lastIndexOf('/') + 1);
                        imagePath = ResourcePathResolver.resolve(tilesetFolder, tsRoot.get("image").getAsString());
                    }
                } else {
                    // Embedded tileset
                    columns = tsEntry.get("columns").getAsInt();
                    imagePath = tsEntry.get("image").getAsString();
                }

                if (!imagePath.startsWith("/")) imagePath = "/" + imagePath;

                // Load tileset image
                BufferedImage tilesetImage;
                try (InputStream imgIn = TiledLoader.class.getResourceAsStream(imagePath)) {
                    if (imgIn == null) throw new IllegalArgumentException("Missing tileset image: " + imagePath);
                    tilesetImage = ImageIO.read(imgIn);
                }

                map.addTileset(new TiledMap.Tileset(firstGid, columns, tilesetImage));
            }

            // --- Layers ---
            JsonArray layers = root.getAsJsonArray("layers");
            for (JsonElement le : layers) {
                JsonObject lay = le.getAsJsonObject();
                String type = lay.get("type").getAsString();
                String name = lay.has("name") ? lay.get("name").getAsString() : "";

                if ("tilelayer".equals(type)) {
                    JsonArray arr = lay.getAsJsonArray("data");
                    int[] data = new int[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        int raw = arr.get(i).getAsInt();
                        data[i] = raw & GID_MASK;
                    }

                    boolean isSolidCollision = name.equalsIgnoreCase("Collision");
                    boolean isOneWayCollision = name.equalsIgnoreCase("OneWay")
                            || name.equalsIgnoreCase("OneWayCollision");

                    // If you want Hazards to render, do NOT treat it as collision here.
                    if (isSolidCollision || isOneWayCollision) {
                        Collider.Type ctype =
                                isSolidCollision ? Collider.Type.SOLID : Collider.Type.ONE_WAY;

                        // Build per-tile colliders from non-zero gids
                        for (int ty = 0; ty < height; ty++) {
                            for (int tx = 0; tx < width; tx++) {
                                int idx = ty * width + tx;
                                if (data[idx] != 0) {
                                    map.colliders.add(new Collider(
                                            new Rect(tx * tileW, ty * tileH, tileW, tileH),
                                            ctype
                                    ));
                                }
                            }
                        }

                        // do not render collision layers
                        continue;
                    }

                    // Render layer
                    map.layers.add(data);
                } else if ("objectgroup".equals(type)) {

                    // --- SOLID / ONE_WAY object colliders (existing behavior) ---
                    if (name.equalsIgnoreCase("Colliders")) {
                        JsonArray objs = lay.getAsJsonArray("objects");
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
                    }

                    // --- TRAPS object layer (NEW) ---
                    else if (name.equalsIgnoreCase("Traps") || name.equalsIgnoreCase("Hazards")) {
                        JsonArray objs = lay.getAsJsonArray("objects");
                        for (JsonElement oe : objs) {
                            JsonObject o = oe.getAsJsonObject();

                            int x = (int) Math.round(o.get("x").getAsDouble());
                            int y = (int) Math.round(o.get("y").getAsDouble());
                            int w = (int) Math.round(o.get("width").getAsDouble());
                            int h = (int) Math.round(o.get("height").getAsDouble());

                            // Tiled supports both "type" (preferred) and custom properties
                            String tag = "";
                            int damage = 1;

                            if (o.has("type")) {
                                tag = o.get("type").getAsString();  // e.g. "spike"
                            }

                            if (o.has("properties")) {
                                JsonArray props = o.getAsJsonArray("properties");
                                for (JsonElement pe : props) {
                                    JsonObject p = pe.getAsJsonObject();
                                    String pname = p.get("name").getAsString();

                                    if ("trapType".equalsIgnoreCase(pname) && tag.isEmpty()) {
                                        tag = p.get("value").getAsString();
                                    } else if ("damage".equalsIgnoreCase(pname)) {
                                        // Tiled stores numbers as JSON number, but Gson gives it as JsonPrimitive
                                        try {
                                            damage = p.get("value").getAsInt();
                                        } catch (Exception ignored) {
                                            // if it's saved as string for some reason
                                            damage = Integer.parseInt(p.get("value").getAsString());
                                        }
                                    }
                                }
                            }

                            if (tag.isEmpty()) tag = "trap";

                            map.colliders.add(new Collider(new Rect(x, y, w, h), Collider.Type.TRAP, tag, damage));

                            // debug once so you KNOW it loaded:
                            if (DEBUG)
                                System.out.println("[TRAP-LOAD] " + tag + " x=" + x + " y=" + y + " w=" + w + " h=" + h + " dmg=" + damage);
                        }
                    }
                }


                // Gameplay objectgroup etc can be handled elsewhere (spawn points)
            }

            return map;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to load map: " + resource, ex);
        }
    }
}

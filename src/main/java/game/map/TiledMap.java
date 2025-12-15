package main.java.game.map;

import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import main.java.game.gfx.Camera;

public class TiledMap {
    public final int width, height, tileWidth, tileHeight;
    public final int pixelW, pixelH;
    private final List<int[]> layers = new ArrayList<>();


    // Single-image tileset for simplicity
    private BufferedImage tileset;
    private int firstGid = 1;
    private int tilesetColumns = 0;


    public final List<Rect> colliders = new ArrayList<>();


    public TiledMap(int width, int height, int tileWidth, int tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.pixelW = width * tileWidth;
        this.pixelH = height * tileHeight;
    }


    public int getPixelWidth() {
        return pixelW;
    }

    public int getPixelHeight() {
        return pixelH;
    }


    void setTileset(String imagePath, int firstGid, int columns) {
        try (var in = getClass().getResourceAsStream(imagePath)) {
            if (in == null) throw new IllegalArgumentException("Missing tileset image: " + imagePath);
            tileset = ImageIO.read(in);
            if (tileset == null) throw new IllegalArgumentException("Unsupported/invalid image: " + imagePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load tileset image: " + imagePath, e);
        }

        this.firstGid = firstGid;
        this.tilesetColumns = columns;
    }


    void addLayer(int[] data) {
        layers.add(data);
    }


    public void addCollider(Rect r) {
        colliders.add(r);
    }


    public void draw(Graphics2D g, Camera cam) {
        if (tileset == null) return;
        if (tilesetColumns <= 0) return;

        int startX = Math.max(0, (int) (cam.x / tileWidth));
        int startY = Math.max(0, (int) (cam.y / tileHeight));
        int endX = Math.min(width - 1, (int) ((cam.x + cam.viewW) / tileWidth) + 1);
        int endY = Math.min(height - 1, (int) ((cam.y + cam.viewH) / tileHeight) + 1);

        for (int[] layer : layers) {
            for (int ty = startY; ty <= endY; ty++) {
                for (int tx = startX; tx <= endX; tx++) {
                    int raw = layer[ty * width + tx];
                    int gid = raw & 0x1FFFFFFF; // mask out flip bits
                    if (gid == 0) continue;
                    int local = gid - firstGid;
                    if (local < 0) continue;
                    int sx = (local % tilesetColumns) * tileWidth;
                    int sy = (local / tilesetColumns) * tileHeight;
                    g.drawImage(tileset, (int) (tx * tileWidth - cam.x), (int) (ty * tileHeight - cam.y),
                            (int) (tx * tileWidth - cam.x) + tileWidth, (int) (ty * tileHeight - cam.y) + tileHeight,
                            sx, sy, sx + tileWidth, sy + tileHeight, null);
                }
            }
        }
    }

    public boolean isWalkable(float worldX, float worldY) { // Not using it for now since the whole map is bounded.
        // Convert world pixel coords to tile coords
        int tileX = (int) (worldX / tileWidth);
        int tileY = (int) (worldY / tileHeight);

        // Outside the map = not walkable (treat as pit)
        if (tileX < 0 || tileY < 0 || tileX >= width || tileY >= height) {
            return false;
        }

        // No layers? be safe and treat as walkable
        if (layers.isEmpty()) {
            return true;
        }

        // We treat the first tile layer as the ground
        int[] ground = layers.getFirst();  // <-- index 0 = Ground. getFirst() on Java 21+ (Project uses Java 23)

        int index = tileY * width + tileX;
        if (index < 0 || index >= ground.length) {
            // Safety check, though it shouldn't happen
            return false;
        }

        // 0 = no tile, >0 = some tile from terrain set
        int raw = ground[index];
        int gid = raw & 0x1FFFFFFF;

        // No tile on ground layer = pit/void
        return gid != 0;
    }


}

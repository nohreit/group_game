package main.java.game.map;

import main.java.game.gfx.Camera;
import main.java.game.physics.Collider;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TiledMap {

    public record Tileset(int firstGid, int columns, BufferedImage tilesetImage) {
    }

    public final int width, height;           // map size in tiles
    public final int tileWidth, tileHeight;   // tile size in pixels

    // Render layers only (filtered by loader)
    public final List<int[]> layers = new ArrayList<>();

    // Collision objects
    public final List<Collider> colliders = new ArrayList<>();

    private final List<Tileset> tilesets = new ArrayList<>();

    public TiledMap(int width, int height, int tileWidth, int tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public void addTileset(Tileset ts) {
        tilesets.add(ts);
        tilesets.sort(Comparator.comparingInt(Tileset::firstGid));
    }

    public int getPixelWidth() {
        return width * tileWidth;
    }

    public int getPixelHeight() {
        return height * tileHeight;
    }

    /**
     * Tileset lookup in O(log N): find the largest tileset.firstGid <= gid
     */
    private Tileset tilesetForGid(int gid) {
        if (tilesets.isEmpty()) return null;

        int lo = 0, hi = tilesets.size() - 1;
        Tileset best = null;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Tileset ts = tilesets.get(mid);

            if (ts.firstGid() <= gid) {
                best = ts;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    public void draw(Graphics2D g2d, Camera camera) {
        if (tilesets.isEmpty() || layers.isEmpty()) return;

        // ---- Visible tile bounds (culling) ----
        // Assumes Camera has x,y (world) and w,h (viewport size in pixels).
        int startX = Math.max(0, (int) (camera.x / tileWidth));
        int startY = Math.max(0, (int) (camera.y / tileHeight));

        int endX = Math.min(width, startX + (camera.viewW / tileWidth) + 2);
        int endY = Math.min(height, startY + (camera.viewH / tileHeight) + 2);

        int camX = (int) camera.x;
        int camY = (int) camera.y;

        for (int[] layer : layers) {
            for (int y = startY; y < endY; y++) {
                int row = y * width;
                int dy = y * tileHeight - camY;

                for (int x = startX; x < endX; x++) {
                    int gid = layer[row + x];
                    if (gid == 0) continue;

                    Tileset ts = tilesetForGid(gid);
                    if (ts == null) continue;

                    int localId = gid - ts.firstGid();
                    if (localId < 0) continue;

                    int sx = (localId % ts.columns()) * tileWidth;
                    int sy = (localId / ts.columns()) * tileHeight;

                    int dx = x * tileWidth - camX;

                    g2d.drawImage(
                            ts.tilesetImage(),
                            dx, dy, dx + tileWidth, dy + tileHeight,
                            sx, sy, sx + tileWidth, sy + tileHeight,
                            null
                    );
                }
            }
        }
    }
}

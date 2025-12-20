package main.java.game.map;

import main.java.game.gfx.Camera;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import main.java.game.physics.Collider;

public class TiledMap {

    public record Tileset(int firstGid, int columns, BufferedImage tilesetImage) {
    }

    public TiledMap(int width, int height, int tileWidth, int tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public final List<Tileset> tilesets = new ArrayList<>();


    public int width, height;       // map in tiles
    public int tileWidth, tileHeight;

    public final List<int[]> layers = new ArrayList<>();  // render layers only (already filtered in loader)

    // Keep your colliders list as-is
    public final List<Collider> colliders = new ArrayList<>();

    public void addTileset(Tileset ts) {
        tilesets.add(ts);
        tilesets.sort(Comparator.comparingInt(t -> t.firstGid));
    }

    private Tileset tilesetForGid(int gid) {
        // choose the tileset with the largest firstGid <= gid
        Tileset best = null;
        for (Tileset ts : tilesets) {
            if (ts.firstGid <= gid) best = ts;
            else break;
        }
        return best;
    }

    public int getPixelWidth() {
        return width * tileWidth;
    }

    public int getPixelHeight() {
        return height * tileHeight;
    }


    public void draw(Graphics2D g2d, Camera camera) {
        if (tilesets.isEmpty()) return;

        for (int[] layer : layers) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int gid = layer[y * width + x];
                    if (gid == 0) continue;

                    Tileset ts = tilesetForGid(gid);
                    if (ts == null) continue;

                    int localId = gid - ts.firstGid;
                    if (localId < 0) continue; // safety

                    int sx = (localId % ts.columns) * tileWidth;
                    int sy = (localId / ts.columns) * tileHeight;

                    int dx = (int) (x * tileWidth - camera.x);
                    int dy = (int) (y * tileHeight - camera.y);

                    g2d.drawImage(
                            ts.tilesetImage,
                            dx, dy, dx + tileWidth, dy + tileHeight,
                            sx, sy, sx + tileWidth, sy + tileHeight,
                            null
                    );
                }
            }
        }
    }
}

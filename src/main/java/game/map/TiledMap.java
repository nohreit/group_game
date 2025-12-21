package main.java.game.map;

import main.java.game.gfx.Camera;
import main.java.game.physics.Collider;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TiledMap {

    public record Tileset(int firstGid, int columns, BufferedImage tilesetImage) {
    }

    private static final int GID_MASK = 0x1FFFFFFF;
    private static final int FLIP_H = 0x80000000;
    private static final int FLIP_V = 0x40000000;
    private static final int FLIP_D = 0x20000000;


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
     * Tileset lookup to find the largest tileset.firstGid <= gid
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

        int startX = Math.max(0, (int) (camera.x / tileWidth));
        int startY = Math.max(0, (int) (camera.y / tileHeight));

        int endX = Math.min(width, startX + (camera.viewW / tileWidth) + 2);
        int endY = Math.min(height, startY + (camera.viewH / tileHeight) + 2);

        int camX = (int) camera.x;
        int camY = (int) camera.y;

        AffineTransform old = g2d.getTransform();

        for (int[] layer : layers) {
            for (int y = startY; y < endY; y++) {
                int row = y * width;
                int dy = y * tileHeight - camY;

                for (int x = startX; x < endX; x++) {
                    int raw = layer[row + x];
                    int gid = raw & GID_MASK;
                    if (gid == 0) continue;

                    boolean fh = (raw & FLIP_H) != 0;
                    boolean fv = (raw & FLIP_V) != 0;
                    boolean fd = (raw & FLIP_D) != 0;

                    Tileset ts = tilesetForGid(gid);
                    if (ts == null) continue;

                    int localId = gid - ts.firstGid();
                    if (localId < 0) continue;

                    int sx = (localId % ts.columns()) * tileWidth;
                    int sy = (localId / ts.columns()) * tileHeight;

                    int dx = x * tileWidth - camX;

                    // Fast path: no transform flags
                    if (!fh && !fv && !fd) {
                        g2d.drawImage(
                                ts.tilesetImage(),
                                dx, dy, dx + tileWidth, dy + tileHeight,
                                sx, sy, sx + tileWidth, sy + tileHeight,
                                null
                        );
                        continue;
                    }

                    // Transform path (Tiled flip/rotate)
                    AffineTransform at = new AffineTransform();
                    at.translate(dx, dy);

                    // Diagonal flip: swap axes; combined with H/V encodes rotations
                    if (fd) {
                        at.translate(0, tileHeight);
                        at.rotate(-Math.PI / 2.0);
                        boolean tmp = fh;
                        fh = fv;
                        fv = tmp;
                    }

                    if (fh) {
                        at.translate(tileWidth, 0);
                        at.scale(-1, 1);
                    }
                    if (fv) {
                        at.translate(0, tileHeight);
                        at.scale(1, -1);
                    }

                    g2d.setTransform(at);
                    g2d.drawImage(
                            ts.tilesetImage(),
                            0, 0, tileWidth, tileHeight,
                            sx, sy, sx + tileWidth, sy + tileHeight,
                            null
                    );
                    g2d.setTransform(old);
                }
            }
        }
    }

}

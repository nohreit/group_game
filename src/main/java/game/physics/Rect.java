package main.java.game.physics;

/**
 * Integer axis-aligned bounding box (AABB).
 * x,y is top-left; w,h are size in pixels.
 */
public class Rect {
    public int x, y, w, h;

    public Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public boolean intersects(float px, float py, int pw, int ph) {
        if (w <= 0 || h <= 0 || pw <= 0 || ph <= 0) return false;
        return px < x + w && px + pw > x && py < y + h && py + ph > y;
    }

    public boolean intersects(Rect other) {
        if (other == null) return false;
        if (w <= 0 || h <= 0 || other.w <= 0 || other.h <= 0) return false;
        return other.x < x + w && other.x + other.w > x
                && other.y < y + h && other.y + other.h > y;
    }
}

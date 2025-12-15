package main.java.game.physics;

public class Rect { // integer AABB (Axis-Aligned Bounding Box) for collisions
    public int x, y, w, h;

    public Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    // For the collision detection.
    public boolean intersects(float px, float py, int pw, int ph) {
        return px < x + w && px + pw > x && py < y + h && py + ph > y;
    }
}

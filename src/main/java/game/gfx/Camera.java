package main.java.game.gfx;

/**
 * Camera represents a viewport into world space.
 * (x, y) is the top-left corner of the view in world coordinates.
 */
public class Camera {

    // World-space position of the top-left corner (in pixels)
    public float x, y;

    // Viewport size in pixels
    public final int viewW, viewH;

    // World size in pixels
    private final int worldW, worldH;

    public Camera(float x, float y, int viewW, int viewH, int worldW, int worldH) {
        this.x = x;
        this.y = y;
        this.viewW = viewW;
        this.viewH = viewH;
        this.worldW = worldW;
        this.worldH = worldH;
    }

    /**
     * Center the camera on a world-space point (px, py).
     */
    public void centerOn(float px, float py) {
        x = px - viewW / 2f;
        y = py - viewH / 2f;
        clamp();
    }

    /**
     * Explicitly set camera position (top-left) and clamp to world bounds.
     */
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        clamp();
    }

    /**
     * Clamp camera to world bounds.
     */
    public void clamp() {
        if (worldW <= viewW) {
            x = 0;
        } else {
            if (x < 0) x = 0;
            if (x > worldW - viewW) x = worldW - viewW;
        }

        if (worldH <= viewH) {
            y = 0;
        } else {
            if (y < 0) y = 0;
            if (y > worldH - viewH) y = worldH - viewH;
        }
    }

    // Optional semantic getters (nice for future refactors)
    public int getViewWidth() {
        return viewW;
    }

    public int getViewHeight() {
        return viewH;
    }
}

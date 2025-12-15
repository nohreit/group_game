package main.java.game.gfx;


public class Camera {
    public float x, y; // top-left
    public final int viewW, viewH;
    private final int worldW, worldH;


    public Camera(float x, float y, int viewW, int viewH, int worldW, int worldH) {
        this.x = x;
        this.y = y;
        this.viewW = viewW;
        this.viewH = viewH;
        this.worldW = worldW;
        this.worldH = worldH;
    }


    public void centerOn(float px, float py) {
        x = px - viewW / 2f;
        y = py - viewH / 2f;
        clamp();
    }

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
}
package main.java.game.gfx;

import java.awt.image.BufferedImage;

public class Animation {
    // Note that animation are frame-based, not time-based
    private final BufferedImage[] frames;
    private final int frameDelay;  // how many update() calls per frame
    private int tick = 0;
    private int index = 0;

    public Animation(BufferedImage[] frames, int frameDelay) {
        if (frames == null || frames.length == 0) {
            throw new IllegalArgumentException("Animation needs at least one frame");
        }
        this.frames = frames;
        this.frameDelay = Math.max(1, frameDelay);
    }

    public int getFrameDelay() {
        return frameDelay;
    }

    public int getFrameCount() {
        return frames.length;
    }

    public void update() {
        tick++;
        if (tick >= frameDelay) {
            tick = 0;
            index = (index + 1) % frames.length;
        }
    }

    public BufferedImage getFrame() {
        return frames[index];
    }

    public void reset() {
        tick = 0;
        index = 0;
    }
}

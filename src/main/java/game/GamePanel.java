package main.java.game;

import main.java.game.gfx.Camera;
import main.java.game.input.Input;
import main.java.game.map.TiledLoader;
import main.java.game.map.TiledMap;
import main.java.game.entity.Player;
import main.java.game.physics.Collider;
import main.java.game.physics.Rect;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import main.java.game.entity.EnemyWarrior;

public class GamePanel extends JPanel implements Runnable {

    private final int vw;
    private final int vh;
    private Thread loopThread;
    private volatile boolean running;

    private GameState state = GameState.PLAYING;

    private BufferedImage backbuffer;
    private Graphics2D g2d;

    private Input input;
    private TiledMap map;
    private Camera camera;
    private Player player;
    private final List<EnemyWarrior> enemies = new ArrayList<>();
    private final Object renderLock = new Object();

    int TILE = 16;

    public enum GameState {
        PLAYING,
        GAME_OVER,
        WIN
    }

    public GamePanel(int virtualW, int virtualH, int scale) {
        this.vw = virtualW;
        this.vh = virtualH;
        setPreferredSize(new Dimension(vw * scale, vh * scale));
        setFocusable(true);
        requestFocusInWindow();
    }

    public void init() {
        backbuffer = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_ARGB);
        String mapResourcePath = "/main/assets/maps/map0.json";

        g2d = backbuffer.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        input = new Input();
        addKeyListener(input);

        try {
            map = TiledLoader.loadJsonMap(mapResourcePath);

            int solid = 0, oneWay = 0, trap = 0;
            for (Collider c : map.colliders) {
                if (c.type == Collider.Type.SOLID) solid++;
                else if (c.type == Collider.Type.ONE_WAY) oneWay++;
                else if (c.type == Collider.Type.TRAP) trap++;
            }
            System.out.println("Colliders => SOLID=" + solid + " ONE_WAY=" + oneWay + " HAZARD=" + trap);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + e.getMessage(), e);
        }

        camera = new Camera(0, 0, vw, vh, map.getPixelWidth(), map.getPixelHeight());

        spawnPlayerTile(4, 8);
//        spawnEnemies();
    }

    @Override
    protected void paintComponent(Graphics g) {
/*        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );
        g2d.drawImage(backbuffer, 0, 0, getWidth(), getHeight(), null);*/
        super.paintComponent(g);
        if (backbuffer == null) return;
        synchronized (renderLock) {
            g.drawImage(backbuffer, 0, 0, getWidth(), getHeight(), null);
        }
    }

    private void spawnEnemies() {
        enemies.clear();
        spawnEnemyTile(8, 7);
        spawnEnemyTile(11, 4);
        spawnEnemyTile(12, 10);
        spawnEnemyTile(15, 7);
        spawnEnemyTile(15, 7);
        spawnEnemyTile(20, 5);
    }

    // Helper to spawn in tile coordinates.
    private void spawnPlayerTile(int tileX, int tileY) {
        String playerBase = "/main/assets/sprites/player/Main_Characters/Virtual_Guy/";
        player = new Player(tileX * TILE + TILE / 2f, tileY * TILE + TILE / 2f, playerBase);
        player.clampToWorld(map);
    }

    private void spawnEnemyTile(int tileX, int tileY) {
        String redBase = "/main/assets/sprites/player/Red_Units/Warrior/";
        enemies.add(new EnemyWarrior(tileX * TILE + TILE / 2f, tileY * TILE + TILE / 2f, redBase));
    }

    public void startLoop() {
        if (loopThread != null) return;
        running = true;
        loopThread = new Thread(this, "game-loop");
        loopThread.start();
    }

    @Override
    public void run() {
        final double targetFps = 60.0;
        final double nsPerUpdate = 1_000_000_000.0 / targetFps;
        long last = System.nanoTime();
        double acc = 0.0;

        while (running) {
            long now = System.nanoTime();
            acc += (now - last) / nsPerUpdate;
            last = now;

            while (acc >= 1.0) {
                update(1.0 / targetFps);
                acc -= 1.0;
            }
            render();
            Toolkit.getDefaultToolkit().sync(); // for smoother Linux rendering
        }
    }

    private void update(double dt) {

        if (state == GameState.WIN || state == GameState.GAME_OVER) {
            if (input.isRestart()) restart();
            return;
        }

        float dx = 0f, speed = 120f;
        if (input.isLeft()) dx -= (float) (speed * dt);
        if (input.isRight()) dx += (float) (speed * dt);

        // Jump
        boolean jumpPressed = input.isJumpPressed();    // edge: false->true this frame
        boolean jumpReleased = input.isJumpReleased();  // edge: true->false this frame
        boolean downHeld = input.isDown();


        player.tick(dt);
        player.update(map, dx, jumpPressed, jumpReleased, downHeld, (float) dt);

        camera.centerOn(player.x, player.y);

        input.endFrame();
    }

    private void restart() {
        state = GameState.PLAYING;
        spawnPlayerTile(4, 8);
//        spawnEnemies();
        camera.centerOn(player.x, player.y);
    }

    boolean DEBUG = false;

    private void render() {

        if (DEBUG) for (Collider c : map.colliders) g2d.fillRect(c.rect.x, c.rect.y, c.rect.w, c.rect.h);

        synchronized (renderLock) {
            // clear
            g2d.setColor(new Color(24, 26, 29));
            g2d.fillRect(0, 0, vw, vh);

            // draw map (background + main layers only)
            map.draw(g2d, camera);

            // draw enemies if alive
            for (EnemyWarrior e : enemies) {
                if (!e.isRemoved()) e.draw(g2d, camera);
            }

            // draw player
            player.draw(g2d, camera);

            // HUD (debug)
            g2d.setColor(Color.WHITE);
            g2d.drawString("pos:" + (int) player.x + "," + (int) player.y, 4, 12);

            if (state == GameState.GAME_OVER) {
                g2d.setColor(new Color(0, 0, 0, 180));
                g2d.fillRect(0, 0, vw, vh);

                g2d.setColor(Color.RED);
                g2d.setFont(new Font("Arial", Font.BOLD, 48));
                g2d.drawString("GAME OVER", vw / 2 - 150, vh / 2);

                g2d.setFont(new Font("Arial", Font.PLAIN, 18));
                g2d.setColor(Color.WHITE);
                g2d.drawString("Press R to Restart", vw / 2 - 95, vh / 2 + 35);
            }

            if (state == GameState.WIN) {
                g2d.setColor(new Color(0, 0, 0, 180));
                g2d.fillRect(0, 0, vw, vh);

                g2d.setColor(new Color(60, 220, 120));
                g2d.setFont(new Font("Arial", Font.BOLD, 48));
                g2d.drawString("YOU WIN!", vw / 2 - 135, vh / 2);

                g2d.setFont(new Font("Arial", Font.PLAIN, 18));
                g2d.setColor(Color.WHITE);
                g2d.drawString("Press R to Restart", vw / 2 - 95, vh / 2 + 35);
            }

            if (DEBUG) {
                graphicDebugging();
//                debugDrawPlayerCollider(g2d, camera);
                debugDrawColliders(g2d, camera);
                // If you re-enable enemies later, you can add their debug drawing here.
            }
        }
        repaint();
    }

    // ----- DEBUGGING -----

    private void graphicDebugging() {
        // Draw map colliders in translucent red
        g2d.setColor(new Color(255, 0, 0, 100));
        for (Collider c : map.colliders) {
            int sx = (int) (c.rect.x - camera.x);
            int sy = (int) (c.rect.y - camera.y);
            g2d.fillRect(sx, sy, c.rect.w, c.rect.h);
        }

        // Draw player collision box in cyan
        debugDrawPlayerCollider(g2d, camera);
    }

    private void debugDrawPlayerCollider(Graphics2D g, Camera cam) {
        if (player == null) return;
        Rect hb = player.getHurtbox();
        int sx = (int) (hb.x - cam.x);
        int sy = (int) (hb.y - cam.y);
        g.setColor(new Color(0, 255, 255, 200));
        g.drawRect(sx, sy, hb.w, hb.h);
    }

    private void debugDrawColliders(Graphics2D g, Camera cam) {
        if (map == null || map.colliders == null) return;

        for (Collider c : map.colliders) {
            Rect r = c.rect;
            int sx = (int) (r.x - cam.x);
            int sy = (int) (r.y - cam.y);

            if (c.type == Collider.Type.SOLID) {
                g.setColor(new Color(255, 0, 0, 110));   // red
                g.fillRect(sx, sy, r.w, r.h);
                g.setColor(new Color(255, 0, 0, 200));
                g.drawRect(sx, sy, r.w, r.h);
            } else if (c.type == Collider.Type.ONE_WAY) {
                g.setColor(new Color(0, 200, 255, 110)); // cyan/blue
                g.fillRect(sx, sy, r.w, r.h);
                g.setColor(new Color(0, 200, 255, 220));
                g.drawRect(sx, sy, r.w, r.h);

                // Optional: draw the “top line” (the only collidable part)
                g.drawLine(sx, sy, sx + r.w, sy);
            } else if (c.type == Collider.Type.TRAP) {
                g.setColor(new Color(255, 255, 0, 110)); // yellow
                g.fillRect(sx, sy, r.w, r.h);
                g.setColor(new Color(255, 255, 0, 220));
                g.drawRect(sx, sy, r.w, r.h);
            }

        }
    }

}

package main.java.game;

import main.java.game.gfx.Camera;
import main.java.game.input.Input;
import main.java.game.map.TiledLoader;
import main.java.game.map.TiledMap;
import main.java.game.entity.Player;
import main.java.game.physics.Rect;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import main.java.game.entity.EnemyWarrior;

public class GamePanel extends JPanel implements Runnable {

    private final int vw;
    private final int vh;
    private Thread loopThread;
    private volatile boolean running;

    private GameState state = GameState.PLAYING;

    private BufferedImage backbuffer;
    private Graphics2D g;

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

        g = backbuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        input = new Input();
        addKeyListener(input);

        try {
            map = TiledLoader.loadJsonMap(mapResourcePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + e.getMessage(), e);
        }

        camera = new Camera(0, 0, vw, vh, map.getPixelWidth(), map.getPixelHeight());

        spawnPlayerTile(4, 8);
//        spawnEnemies();
    }

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        if (backbuffer == null) return;
        synchronized (renderLock) {
            gg.drawImage(backbuffer, 0, 0, getWidth(), getHeight(), null);
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
        boolean jumpPressed = input.isJumpPressed();   // edge: false->true this frame
        boolean jumpReleased = input.isJumpReleased();  // edge: true->false this frame

        player.tick(dt);
        player.updatePlatformer(map, dx, jumpPressed, jumpReleased, (float) dt);
        camera.centerOn(player.x, player.y);

//        player.update(dx, dy, input.isAttack(), input.isGuard());

        input.endFrame(); // Reset isJumpPressed()

        /*if (player.isDead()) {
            state = GameState.GAME_OVER;
        }

        Iterator<EnemyWarrior> it = enemies.iterator();

        while (it.hasNext()) {
            EnemyWarrior e = it.next();

            // Update AI unless fully removed
            if (!e.isRemoved()) {
                e.updateAI(map, player, dt);
                // Enemy hits and player blocks
                if (!player.isDead() && !e.isDead() && !e.isRemoved()) {
                    Rect ehb = e.getAttackHitbox();
                    if (ehb != null) {
                        Rect phb = player.getHurtbox();
                        if (phb.intersects(ehb.x, ehb.y, ehb.w, ehb.h)) {

                            if (player.isGuarding()) {
                                // Block: no damage but push player back
                                player.applyKnockbackFrom(e.x, e.y, 240f, 8);

                                // stop enemy from "grinding" on the guard
                                e.cancelAttackAndStartCooldown();
                            } else {
                                // Not guarding: take damage
                                player.takeHit(10);
                            }
                        }
                    }
                }

                if (!player.isDead() && !e.isDead()) {
                    Rect pb = player.getHurtbox(); // pb = player box
                    Rect eb = e.getHurtbox(); // eb = enemy box

                    if (eb.intersects(pb.x, pb.y, pb.w, pb.h)) player.takeHit(10);
                }
            }

            if (!e.isDead() && player.isAttackActive()) {
                Rect hitbox = player.getAttackHitbox();
                if (hitbox != null) {
                    Rect enemyBox = e.getHurtbox();

                    if (enemyBox.intersects(hitbox.x, hitbox.y, hitbox.w, hitbox.h)) {
                        e.takeHit(10, player.getAttackId(), player.x, player.y);
                    }
                }
            }

            // Enemy hits player
            if (!e.isDead() && !e.isRemoved()) {
                Rect ehb = e.getAttackHitbox();
                if (ehb != null) {
                    Rect phb = player.getHurtbox();
                    if (phb.intersects(ehb.x, ehb.y, ehb.w, ehb.h)) {
                        player.takeHit(12); // tune
                    }
                }
            }

            // Clean up after the fade
            if (e.isRemoved()) {
                it.remove();
            }
        }

        if (enemies.isEmpty()) {
            state = GameState.WIN;
            return;
        }

        if (player.isDead()) {
            state = GameState.GAME_OVER;
        }*/
    }

    private void restart() {
        state = GameState.PLAYING;
        spawnPlayerTile(4, 8);
//        spawnEnemies();
        camera.centerOn(player.x, player.y);
    }

    boolean DEBUG = true;

    private void render() {
        synchronized (renderLock) {
            // clear
            g.setColor(new Color(24, 26, 29));
            g.fillRect(0, 0, vw, vh);

            // draw map (background + main layers only)
            map.draw(g, camera);

            // draw enemies if alive
            for (EnemyWarrior e : enemies) {
                if (!e.isRemoved()) e.draw(g, camera);
            }

            // draw player
            player.draw(g, camera);

            // HUD (debug)
            g.setColor(Color.WHITE);
            g.drawString("pos:" + (int) player.x + "," + (int) player.y, 4, 12);

            if (state == GameState.GAME_OVER) {
                g.setColor(new Color(0, 0, 0, 180));
                g.fillRect(0, 0, vw, vh);

                g.setColor(Color.RED);
                g.setFont(new Font("Arial", Font.BOLD, 48));
                g.drawString("GAME OVER", vw / 2 - 150, vh / 2);

                g.setFont(new Font("Arial", Font.PLAIN, 18));
                g.setColor(Color.WHITE);
                g.drawString("Press R to Restart", vw / 2 - 95, vh / 2 + 35);
            }

            if (state == GameState.WIN) {
                g.setColor(new Color(0, 0, 0, 180));
                g.fillRect(0, 0, vw, vh);

                g.setColor(new Color(60, 220, 120));
                g.setFont(new Font("Arial", Font.BOLD, 48));
                g.drawString("YOU WIN!", vw / 2 - 135, vh / 2);

                g.setFont(new Font("Arial", Font.PLAIN, 18));
                g.setColor(Color.WHITE);
                g.drawString("Press R to Restart", vw / 2 - 95, vh / 2 + 35);
            }

            // DEBUGGING
            if (DEBUG) {
                graphicDebugging();
//                debugDrawAttackHitbox(g, camera);
//                player.debugDrawAttackHitbox(g, camera);
                player.debugDrawCollision(g, camera);

                for (EnemyWarrior e : enemies) {
                    e.debugDrawCollision(g, camera);
                    e.debugDrawAttackHitbox(g, camera);
                }
            }
        }
        repaint();
    }

    // ----- DEBUGGING -----

    private void graphicDebugging() {
        // Draw map colliders in translucent red
        g.setColor(new Color(255, 0, 0, 100));
        for (Rect r : map.colliders) {
            int sx = (int) (r.x - camera.x);
            int sy = (int) (r.y - camera.y);
            g.fillRect(sx, sy, r.w, r.h);
        }

        // Draw player collision box in cyan
        player.debugDrawCollision(g, camera);

        // Logs for debugging
    }

/*    public void debugDrawAttackHitbox(Graphics2D g, Camera cam) {
        Rect hb = player.getAttackHitbox();
        if (hb == null) return;

        int sx = (int) (hb.x - cam.x);
        int sy = (int) (hb.y - cam.y);

        g.setColor(new Color(255, 0, 0, 120));
        g.drawRect(sx, sy, hb.w, hb.h);
    }*/

}

package main.java.game;

import main.java.game.entity.EnemyWarrior;
import main.java.game.entity.Player;
import main.java.game.gfx.Camera;
import main.java.game.input.Input;
import main.java.game.map.TiledLoader;
import main.java.game.map.TiledMap;
import main.java.game.physics.Collider;
import main.java.game.physics.Rect;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class GamePanel extends JPanel implements Runnable {

    // ---- Config / constants ----
    private static final int TILE_SIZE = 16;
    private static final double TARGET_FPS = 60.0;
    private static final double DT = 1.0 / TARGET_FPS;
    private static final int MAX_CATCHUP_STEPS = 5;

    // Stages
    private int stage = 0;

    private static final String MAP_STAGE_1 = "/main/assets/maps/map0.json";
    private static final String MAP_STAGE_2 = "/main/assets/maps/map1.json";

    // Toggle for drawing colliders / hurtboxes, and printing debug info.
    private static final boolean DEBUG = false;

    private static final Color CLEAR_COLOR = new Color(24, 26, 29);
    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 180);
    private static final Font BIG_FONT = new Font("Arial", Font.BOLD, 48);
    private static final Font SMALL_FONT = new Font("Arial", Font.PLAIN, 18);

    private static final String MAP_RESOURCE_PATH = "/main/assets/maps/map0.json";
    private static final String PLAYER_BASE = "/main/assets/sprites/player/Main_Characters/Virtual_Guy/";
    private static final String ENEMY_BASE = "/main/assets/sprites/player/Red_Units/Warrior/";

    // ---- View ----
    private final int vw;
    private final int vh;

    // ---- Loop ----
    private Thread loopThread;
    private volatile boolean running;

    // ---- State ----
    private GameState state = GameState.PLAYING;

    private BufferedImage backbuffer;
    private final Object renderLock = new Object();

    private Input input;
    private TiledMap map;
    private Camera camera;
    private Player player;
    private final List<EnemyWarrior> enemies = new ArrayList<>();

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
    }

    @Override
    public void addNotify() {
        super.addNotify();
        requestFocusInWindow();
    }

    public void init() {
        backbuffer = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_ARGB);

        input = new Input();
        addKeyListener(input);

        try {
            map = TiledLoader.loadJsonMap(MAP_STAGE_1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + MAP_RESOURCE_PATH, e);
        }

        if (DEBUG && map != null && map.colliders != null) {
            int solid = 0, oneWay = 0, trap = 0, goal = 0;
            for (Collider c : map.colliders) {
                if (c.type == Collider.Type.SOLID) solid++;
                else if (c.type == Collider.Type.ONE_WAY) oneWay++;
                else if (c.type == Collider.Type.TRAP) trap++;
                else if (c.type == Collider.Type.GOAL) goal++;
            }
            System.out.println("Colliders => SOLID=" + solid + " ONE_WAY=" + oneWay + " TRAP=" + trap + " GOAL=" + goal);
        }

        loadStage(0);

        spawnPlayerTile(2, 9);
        // spawnEnemies(); // future implementation
    }

    // Stage loader
    private void loadStage(int newStage) {
        stage = newStage;

        String res = (stage == 0) ? MAP_STAGE_1 : MAP_STAGE_2;
        map = TiledLoader.loadJsonMap(res);

        camera = new Camera(
                0, 0,
                vw, vh,
                map.getPixelWidth(),
                map.getPixelHeight()
        );

        // TODO: upgrade later to PlayerSpawn object; for now keep tiles
        if (stage == 0) spawnPlayerTile(2, 9);
        else if (stage == 1) spawnPlayerTile(2, 9);

        // Reset runtime state between stages
        player.reset();
        player.clampToWorld(map);

        camera.centerOn(player.x, player.y);
    }


    // ---- Public loop control ----

    public void startLoop() {
        if (loopThread != null) return;
        running = true;
        loopThread = new Thread(this, "game-loop");
        loopThread.start();
    }

    // ---- Swing paint ----

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backbuffer == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );

        synchronized (renderLock) {
            g2.drawImage(backbuffer, 0, 0, getWidth(), getHeight(), null);
        }
    }

    // ---- Game loop ----

    @Override
    public void run() {
        final double nsPerUpdate = 1_000_000_000.0 / TARGET_FPS;

        long last = System.nanoTime();
        double acc = 0.0;

        while (running) {
            long now = System.nanoTime();
            acc += (now - last) / nsPerUpdate;
            last = now;

            int steps = 0;
            while (acc >= 1.0 && steps < MAX_CATCHUP_STEPS) {
                update(DT);
                acc -= 1.0;
                steps++;
            }

            // Prevent spiral if we are falling behind badly
            if (acc > 2.0) acc = 0.0;

            render();
            Toolkit.getDefaultToolkit().sync();

            // Avoid busy spin (polite CPU usage)
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void update(double dt) {
        if (player == null || map == null) return;

        if (state == GameState.WIN || state == GameState.GAME_OVER) {
            if (input.isRestart()) restart();
            input.endFrame();
            return;
        }

        // Movement
        float dx = 0f;
        final float speed = 120f;
        if (input.isLeft()) dx -= (float) (speed * dt);
        if (input.isRight()) dx += (float) (speed * dt);

        // Jump edges
        boolean jumpPressed = input.isJumpPressed();
        boolean jumpReleased = input.isJumpReleased();
        boolean downHeld = input.isDown();

//        player.tick(dt);
        player.update(map, dx, jumpPressed, jumpReleased, downHeld, (float) dt);

        if (player.isDead()) {
            state = GameState.GAME_OVER;
        } else if (player.isLevelComplete()) {
            if (stage == 0) {
                loadStage(1);
            } else {
                state = GameState.WIN;
            }
        }


        camera.centerOn(player.x, player.y);

        input.endFrame();
    }


    private void render() {
        if (backbuffer == null || map == null || camera == null || player == null) return;

        synchronized (renderLock) {
            Graphics2D g = backbuffer.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Clear
                g.setColor(CLEAR_COLOR);
                g.fillRect(0, 0, vw, vh);

                // Map
                map.draw(g, camera);

                // Enemies (if enabled)
                /* for (EnemyWarrior e : enemies) {
                    if (!e.isRemoved()) e.draw(g, camera);
                }*/

                // Player
                player.draw(g, camera);

                // HUD
                drawHUD(g);

                // End screens
                if (state == GameState.GAME_OVER) {
                    drawCenteredOverlay(g, "GAME OVER", Color.RED);
                } else if (state == GameState.WIN) {
                    drawCenteredOverlay(g, "YOU WIN!", new Color(60, 220, 120));
                }

                // Debug overlays
                if (DEBUG) {
                    debugDrawPlayerCollider(g, camera);
                    debugDrawColliders(g, camera);
                }

            } finally {
                g.dispose();
            }
        }

        repaint();
    }

    private void drawHUD(Graphics2D g) {
        final int hudHeight = 28;
        final int y = vh - hudHeight;

        // Background bar
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, y, vw, hudHeight);

        // HP text
        g.setFont(SMALL_FONT);
        g.setColor(Color.WHITE);
        g.drawString("HP", 8, y + 18);

        // HP hearts / blocks (no sprite for that yet)
        int hp = player.getHp();
        int maxHp = Player.MAX_HP;

        // Stage number
        g.drawString("Stage " + (stage + 1), vw - 80, vh - 10);

        int barX = 36;
        int barY = y + 8;
        int barW = 12;
        int barH = 12;
        int gap = 4;

        for (int i = 0; i < maxHp; i++) {
            if (i < hp) {
                g.setColor(new Color(220, 60, 60)); // alive
            } else {
                g.setColor(new Color(90, 90, 90));  // empty
            }
            g.fillRect(barX + i * (barW + gap), barY, barW, barH);
        }
    }


    private void drawCenteredOverlay(Graphics2D g, String title, Color titleColor) {
        g.setColor(OVERLAY_COLOR);
        g.fillRect(0, 0, vw, vh);

        g.setFont(BIG_FONT);
        g.setColor(titleColor);

        // quick centering: approximate; for perfect, use FontMetrics
        int x = vw / 2 - (title.length() * 14); // cheap estimate
        int y = vh / 2;
        g.drawString(title, x, y);

        g.setFont(SMALL_FONT);
        g.setColor(Color.WHITE);
        g.drawString("Press R to Restart", vw / 2 - 95, y + 35);
    }

    private void restart() {
        state = GameState.PLAYING;
        loadStage(0);
    }

    // ---- Spawning helpers ----

    private void spawnEnemies() {
        enemies.clear();
        spawnEnemyTile(8, 7);
        spawnEnemyTile(11, 4);
        spawnEnemyTile(12, 10);
        spawnEnemyTile(15, 7);
        spawnEnemyTile(20, 5);
    }

    private void spawnPlayerTile(int tileX, int tileY) {
        float px = tileX * TILE_SIZE + TILE_SIZE / 2f;
        float py = tileY * TILE_SIZE + TILE_SIZE / 2f;
        player = new Player(px, py, PLAYER_BASE);
        player.clampToWorld(map);
    }

    private void spawnEnemyTile(int tileX, int tileY) {
        float px = tileX * TILE_SIZE + TILE_SIZE / 2f;
        float py = tileY * TILE_SIZE + TILE_SIZE / 2f;
        enemies.add(new EnemyWarrior(px, py, ENEMY_BASE));
    }

    // ---- Debug drawing ----

    private void debugDrawPlayerCollider(Graphics2D g, Camera cam) {
        Rect hb = player.getHurtbox();
        int sx = (int) (hb.x - cam.x);
        int sy = (int) (hb.y - cam.y);
        g.setColor(new Color(0, 255, 255, 200));
        g.drawRect(sx, sy, hb.w, hb.h);
    }

    private void debugDrawColliders(Graphics2D g, Camera cam) {
        if (map.colliders == null) return;

        for (Collider c : map.colliders) {
            Rect r = c.rect;
            int sx = (int) (r.x - cam.x);
            int sy = (int) (r.y - cam.y);

            switch (c.type) {
                case SOLID -> {
                    g.setColor(new Color(255, 0, 0, 110));
                    g.fillRect(sx, sy, r.w, r.h);
                    g.setColor(new Color(255, 0, 0, 200));
                    g.drawRect(sx, sy, r.w, r.h);
                }
                case ONE_WAY -> {
                    g.setColor(new Color(0, 200, 255, 110));
                    g.fillRect(sx, sy, r.w, r.h);
                    g.setColor(new Color(0, 200, 255, 220));
                    g.drawRect(sx, sy, r.w, r.h);
                    g.drawLine(sx, sy, sx + r.w, sy);
                }
                case TRAP -> {
                    g.setColor(new Color(255, 255, 0, 110));
                    g.fillRect(sx, sy, r.w, r.h);
                    g.setColor(new Color(255, 255, 0, 220));
                    g.drawRect(sx, sy, r.w, r.h);
                }
                case GOAL -> {
                    g.setColor(new Color(60, 220, 120, 110));
                    g.fillRect(sx, sy, r.w, r.h);
                    g.setColor(new Color(60, 220, 120, 220));
                    g.drawRect(sx, sy, r.w, r.h);
                }
            }
        }
    }
}

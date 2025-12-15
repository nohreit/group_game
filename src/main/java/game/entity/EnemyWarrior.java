package main.java.game.entity;

import main.java.game.gfx.Animation;
import main.java.game.gfx.Camera;
import main.java.game.map.TiledMap;
import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class EnemyWarrior {

    private static final boolean DEBUG = true;
    public float x, y;

    // Same “feet collider” idea as Player
    private static final int COLLIDER_W = 16;
    private static final int COLLIDER_H = 10;
    private static final int FOOT_OFFSET_Y = 24;

    // --- Combat hitbox constants (pixels, world-space) ---
    private static final int HITBOX_W = 60;
    private static final int HITBOX_H = 90;

    // Offset distance from feet collider (more like belly collider) to hitbox
    private static final int HITBOX_X_OFFSET = 0;  // forward from collider edge
    private static final int HITBOX_Y_OFFSET = 40;  // downward from feet collider top

    // --- Knockback ---
    private float kbVx = 0f, kbVy = 0f;
    private int kbTicks = 0;

    private static final int KB_TICKS_ON_HIT = 10;     // short pop
    private static final int KB_TICKS_ON_GUARD = 8;    // slightly shorter
    private static final float KB_SPEED_ON_HIT = 260f;
    private static final float KB_SPEED_ON_GUARD = 220f;


    private boolean facingLeft = false;

    // Simple AI tuning
    private static final float SPEED = 90f;          // px/sec
    private static final float AGGRO_RANGE = 220f;    // start chasing
    private static final float STOP_RANGE = 44f;     // stop near player

    //  i-frames to prevent damage every tick (invuln => invulnerability)
    private int invulnTicks = 0;
    private static final int INVULN_TICKS_ON_HIT = 18; // ~0.3s at 60fps

    // prevent multiple hits from the same swing
    private int lastHitAttackId = -1;

    private static final int MAX_HP = 30;
    private int hp = MAX_HP;

    private boolean dead = false;

    // Fade-out (ticks at 60 FPS)
    private int fadeTicks = 0;
    private static final int FADE_DURATION_TICKS = 36; // ~0.6s
    private boolean removed = false; // fully faded, safe to stop drawing/updating

    // Enemy attack control
    private boolean attackPlaying = false;
    private int attackTicks = 0;
    private int attackDurationTicks = 30;     // will set from animation if you want
    private int attackCooldownTicks = 0;

    // Tune to make enemy slower than player or to satisfactory delay.
    private static final int ATTACK_COOLDOWN_TICKS = 75; // ~1.25s at 60fps
    private static final int ATTACK_WINDUP_TICKS = 8;    // small windup before active frames

    // Animation state
    private enum AnimationType {IDLE, RUN, ATTACK}

    private enum MoveType {UP, DOWN, LEFT, RIGHT, NONE}

    private AnimationType currentAnimType = AnimationType.IDLE;
    private MoveType currentMoveType = MoveType.DOWN;

    private Animation idleDownAnim, idleUpAnim, idleLeftAnim, idleRightAnim, attackAnim;
    private Animation runDownAnim, runUpAnim, runLeftAnim, runRightAnim;

    private Animation currentAnimation;


    // Base folder where the RED warrior sprites live
    // "/main/resources/sprites/player/Red_Units/Warrior/"
    private final String spriteBasePath;

    public EnemyWarrior(float x, float y, String spriteBasePath) {
        this.x = x;
        this.y = y;
        this.spriteBasePath = spriteBasePath.endsWith("/") ? spriteBasePath : (spriteBasePath + "/");
        initAnimations();
    }

    private void initAnimations() {
        try {
            BufferedImage idleSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Idle.png"),
                    "Missing enemy idle sprite sheet: " + spriteBasePath + "Warrior_Idle.png"
            ));

            BufferedImage runSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Run.png"),
                    "Missing enemy run sprite sheet: " + spriteBasePath + "Warrior_Run.png"
            ));

            int frameWidth = 192;
            int frameHeight = 192;

            int idleFramesCount = 8;
            int runFramesCount = 6;

            BufferedImage[] idleFrames = new BufferedImage[idleFramesCount];
            for (int i = 0; i < idleFramesCount; i++) {
                idleFrames[i] = idleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            BufferedImage[] runFrames = new BufferedImage[runFramesCount];
            for (int i = 0; i < runFramesCount; i++) {
                runFrames[i] = runSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Reuse same frames for all directions for now
            idleDownAnim = new Animation(idleFrames, 8);
            idleUpAnim = new Animation(idleFrames, 8);
            idleLeftAnim = new Animation(idleFrames, 8);
            idleRightAnim = new Animation(idleFrames, 8);

            runDownAnim = new Animation(runFrames, 6);
            runUpAnim = new Animation(runFrames, 6);
            runLeftAnim = new Animation(runFrames, 6);
            runRightAnim = new Animation(runFrames, 6);

            // Load attack and guard animations
            attackAnim = loadAnimation(spriteBasePath + "Warrior_Attack1.png", 4, 6);
            attackDurationTicks = attackAnim.getFrameCount() * attackAnim.getFrameDelay();

            currentAnimation = idleDownAnim;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load Enemy Warrior sprite sheets", e);
        }
    }

    private Animation loadAnimation(String path, int frameCount, int frameDelay) {
        try {
            BufferedImage sheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(path)
            ));
            int frameWidth = sheet.getWidth() / frameCount;
            int frameHeight = sheet.getHeight();

            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = sheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }
            return new Animation(frames, frameDelay);
        } catch (IOException e) {
            e.printStackTrace();
            // Fallback: 1x1 dummy frame to avoid crashes
            BufferedImage dummy = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = dummy.createGraphics();
            g2.setColor(Color.MAGENTA);
            g2.fillRect(0, 0, 16, 16);
            g2.dispose();
            return new Animation(new BufferedImage[]{dummy}, frameDelay);
        }
    }

    // --- collider helpers (same idea as Player) ---
    public float getColX() {
        return x - COLLIDER_W / 2f;
    }

    public float getColY() {
        return y + FOOT_OFFSET_Y - COLLIDER_H;
    }

    // --- Movement with collision (axis-by-axis resolution) ---
    public void move(TiledMap map, float dx, float dy) {
        if (dx != 0f) {
            float newX = x + dx;
            float colX = newX - COLLIDER_W / 2f;
            float colY = getColY();

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, COLLIDER_W, COLLIDER_H)) {
                    if (dx > 0) newX = r.x - COLLIDER_W / 2f;
                    else newX = r.x + r.w + COLLIDER_W / 2f;
                    colX = newX - COLLIDER_W / 2f;
                }
            }
            x = newX;
        }

        if (dy != 0f) {
            float newY = y + dy;
            float colX = getColX();
            float colY = newY + FOOT_OFFSET_Y - COLLIDER_H;

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, COLLIDER_W, COLLIDER_H)) {
                    if (dy > 0) newY = r.y - FOOT_OFFSET_Y;
                    else newY = r.y + r.h - FOOT_OFFSET_Y + COLLIDER_H;
                    colY = newY + FOOT_OFFSET_Y - COLLIDER_H;
                }
            }
            y = newY;
        }
    }

    // --- AI update: chase player in range, idle otherwise ---
    // TODO: implement AI for attack and guard bases on current state and predictability player next move.
    public void updateAI(TiledMap map, Player player, double dt) {
        if (removed) return;

        if (invulnTicks > 0) invulnTicks--;

        // Knockback takes priority over AI/movement
        if (kbTicks > 0) {
            float dx = kbVx * (float) dt;
            float dy = kbVy * (float) dt;

            move(map, dx, dy);

            kbTicks--;

            // Show idle or run while sliding
            updateAnimation(dx, dy); // uses dt-based movement direction
            return;
        }


        // Set tick cooldown for attacks.
        if (attackCooldownTicks > 0) attackCooldownTicks--;

        // If dead, just fade out (no movement)
        if (dead) {
            fadeTicks++;
            if (fadeTicks >= FADE_DURATION_TICKS) {
                removed = true;
            }
            return;
        }

        float px = player.x;
        float py = player.y;

        float vx = px - x;
        float vy = py - y;

        float distSq = vx * vx + vy * vy;
        float aggroSq = AGGRO_RANGE * AGGRO_RANGE;
        float stopSq = STOP_RANGE * STOP_RANGE;

        float dx = 0f, dy = 0f;

        boolean inAggro = distSq <= aggroSq;
        boolean inStop = distSq <= stopSq;

        // Decide facing toward player which helps hitbox direction
        if (Math.abs(vx) >= Math.abs(vy)) {
            facingLeft = vx < 0f;
        }

        // If currently attacking: do not move, just advance attack
        if (attackPlaying) {
            attackTicks++;
            updateAttackAnimation();

            if (attackTicks >= attackDurationTicks) {
                attackPlaying = false;
                attackTicks = 0;
                attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
                setAnimation(AnimationType.IDLE, currentMoveType);
            }
            return;
        }

        // Attack if close enough and off cooldown
        if (inAggro && inStop && attackCooldownTicks <= 0) {
            attackPlaying = true;
            attackTicks = 0;
            startAttackAnimation();
            return;
        }

        // Chase if in aggro range but not close enough to attack
        if (inAggro && !inStop) {
            float dist = (float) Math.sqrt(distSq);
            if (dist > 0.0001f) { // prevents divide-by-zero
                float nx = vx / dist;
                float ny = vy / dist;

                dx = nx * SPEED * (float) dt;
                dy = ny * SPEED * (float) dt;
            }
        }

        // Move + animate aggro-ed enemy based on dx/dy
        move(map, dx, dy);
        updateAnimation(dx, dy);
    }

    private void updateAttackAnimation() {
        if (currentAnimation != null) currentAnimation.update();
    }

    private void startAttackAnimation() {
        setAnimation(AnimationType.ATTACK, currentMoveType);
        attackTicks = 0;

    }

    // Enemy hurtbox = feet collider
    public Rect getHurtbox() {
        return new Rect(
                Math.round(x - COLLIDER_W / 2f),
                Math.round(y + FOOT_OFFSET_Y - COLLIDER_H),
                COLLIDER_W,
                COLLIDER_H
        );
    }

    public boolean isAttackActive() {
        if (!attackPlaying) return false;

        int start = ATTACK_WINDUP_TICKS;
        int end = (int) (attackDurationTicks * 0.70f);

        return attackTicks >= start && attackTicks <= end;
    }


    public boolean isDead() {
        return dead;
    }

    public boolean isRemoved() {
        return removed;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return MAX_HP;
    }

    public void takeHit(int dmg, int attackId, float fromX, float fromY) {
        if (dead || removed) return;
        if (invulnTicks > 0) return;
        if (attackId == lastHitAttackId) return; // assumes each attack is unique.

        lastHitAttackId = attackId;
        invulnTicks = INVULN_TICKS_ON_HIT;
        hp -= dmg;

        applyKnockbackFrom(fromX, fromY, KB_SPEED_ON_HIT, KB_TICKS_ON_HIT);


        if (DEBUG) System.out.println("Enemy hit! HP = " + hp);

        if (hp <= 0) {
            hp = 0;
            dead = true;
            fadeTicks = 0;
            if (DEBUG) System.out.println("Enemy defeated");
        }
    }


    private void updateAnimation(float dx, float dy) {
        boolean isMoving = (dx != 0f || dy != 0f);

        AnimationType animType = isMoving ? AnimationType.RUN : AnimationType.IDLE;
        MoveType moveType = currentMoveType;

        if (isMoving) {
            if (Math.abs(dx) > Math.abs(dy)) {
                moveType = (dx > 0) ? MoveType.RIGHT : MoveType.LEFT;
            } else {
                moveType = (dy > 0) ? MoveType.DOWN : MoveType.UP;
            }
        }

        setAnimation(animType, moveType);

        if (currentAnimation != null) currentAnimation.update();
    }

    public void cancelAttackAndStartCooldown() {
        if (!attackPlaying) return;
        attackPlaying = false;
        attackTicks = 0;
        attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
        setAnimation(AnimationType.IDLE, currentMoveType);
    }


    private Animation getAnimation(AnimationType type, MoveType move) {
        if (type == AnimationType.ATTACK) {
            return (attackAnim != null) ? attackAnim : idleDownAnim;
        }

        if (type == AnimationType.IDLE) {
            return switch (move) {
                case UP -> idleUpAnim;
                case LEFT -> idleLeftAnim;
                case RIGHT -> idleRightAnim;
                case DOWN, NONE -> idleDownAnim;
            };
        } else {
            return switch (move) {
                case UP -> runUpAnim;
                case LEFT -> runLeftAnim;
                case RIGHT -> runRightAnim;
                case DOWN, NONE -> runDownAnim;
            };
        }
    }

    private void setAnimation(AnimationType type, MoveType move) {
        if (type == currentAnimType && move == currentMoveType && currentAnimation != null) return;

        currentAnimType = type;
        currentMoveType = move;
        currentAnimation = getAnimation(type, move);
        if (currentAnimation != null) currentAnimation.reset();
    }

    public void draw(Graphics2D g, Camera cam) {
        if (removed) return;

        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        Animation anim = (currentAnimation != null) ? currentAnimation : idleDownAnim;
        BufferedImage frame = anim.getFrame();

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        // --- Fade alpha ---
        float alpha = 1.0f;
        if (dead) {
            alpha = 1.0f - (fadeTicks / (float) FADE_DURATION_TICKS);
            if (alpha < 0f) alpha = 0f;
        }

        Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        if (facingLeft) {
            g.drawImage(frame, drawX + fw, drawY, -fw, fh, null);
        } else {
            g.drawImage(frame, drawX, drawY, null);
        }

        g.setComposite(oldComp);

        // --- HP bar (won’t show when fully dead) ---
        if (!dead) {
            drawHpBar(g, cam, fw, fh);
        }
    }

    private void drawHpBar(Graphics2D g, Camera cam, int frameW, int frameH) {
        // Bar size
        int barW = 42;
        int barH = 6;

        // Screen position: above the head
        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        int barX = sx - barW / 2;
        int barY = sy - frameH / 2 - 12;

        // Background
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(barX, barY, barW, barH);

        // Fill
        float pct = (MAX_HP <= 0) ? 0f : (hp / (float) MAX_HP);
        int fillW = (int) (barW * pct);

        g.setColor(new Color(200, 50, 50, 220));
        g.fillRect(barX, barY, fillW, barH);

        // Border
        g.setColor(new Color(255, 255, 255, 200));
        g.drawRect(barX, barY, barW, barH);
    }

    public Rect getAttackHitbox() {
        if (!isAttackActive()) return null;

        Rect hb = getHurtbox();
        int w = 54;
        int h = 54;

        int hbX = facingLeft ? (hb.x - w) : (hb.x + hb.w);
        int hbY = hb.y + hb.h / 2 - h / 2;

        return new Rect(hbX, hbY, w, h);
    }

    public void applyKnockbackFrom(float fromX, float fromY, float kbSpeed, int ticks) {
        // Direction: away from player
        float vx = x - fromX;
        float vy = y - fromY;

        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 0.0001f) { // avoid NaN
            vx = 1f;
            vy = 0f;
            len = 1f;
        }

        float nx = vx / len;
        float ny = vy / len;

        kbVx = nx * kbSpeed;
        kbVy = ny * kbSpeed;
        kbTicks = Math.max(kbTicks, ticks); // keep strongest/longest if already active
    }


    //----- DEBUGGING -----
    public void debugDrawCollision(Graphics2D g, Camera cam) {
        float colX = getColX();
        float colY = getColY();

        int sx = (int) (colX - cam.x);
        int sy = (int) (colY - cam.y);

        g.setColor(new Color(0, 255, 255, 120));
        g.drawRect(sx, sy, COLLIDER_W, COLLIDER_H);
    }

    public void debugDrawAttackHitbox(Graphics2D g, Camera cam) {
        Rect hb = getAttackHitbox();
        if (hb == null) return;

        int sx = hb.x - (int) cam.x;
        int sy = hb.y - (int) cam.y;

        g.setColor(new Color(255, 0, 0, 150));
        g.drawRect(sx, sy, hb.w, hb.h);
    }

}

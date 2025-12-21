package main.java.game.entity;

import main.java.game.gfx.Animation;
import main.java.game.gfx.Camera;
import main.java.game.map.TiledMap;
import main.java.game.physics.Collider;
import main.java.game.physics.Rect;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class Player {

    // World position in pixels (treated as center of the sprite)
    public float x;
    public float y;

    // Submission default: OFF
    private static final boolean DEBUG = false;

    // ---- Platformer physics ----
    private float vx = 0f, vy = 0f;
    private boolean onGround = false;

    private boolean levelComplete = false;

    // Tunnable values based on preferences
    private static final float MOVE_SPEED = 120f;   // px/sec
    private static final float GRAVITY = 520f;      // px/sec^2
    private static final float JUMP_VEL = -220f;    // px/sec (negative = up)
    private static final float MAX_FALL = 520f;     // px/sec

    // Jump feel helpers
    private static final float COYOTE_TIME = 0.08f;
    private static final float JUMP_BUFFER = 0.10f;

    private float coyoteTimer = 0f;
    private float jumpBufferTimer = 0f;

    private static final int MAX_JUMPS = 2;
    private int jumpsLeft = MAX_JUMPS;

    // One-way drop-through
    private boolean dropping = false;
    private float dropTimer = 0f;
    private static final float DROP_TIME = 0.18f;
    private static final float DROP_PUSH = 2f;

    private static final float ONE_WAY_EDGE_PAD = 2f;

    // Player current state
    public static final int MAX_HP = 3;

    private int hp = MAX_HP;
    private boolean dead = false;

    // Hurt / knockback
    private float invulnTimer = 0f;
    private static final float INVULN_TIME = 0.50f;

    private static final float HIT_KNOCKBACK_X = 140f;
    private static final float HIT_KNOCKBACK_Y = -180f;
    private static final float HIT_LOCK_TIME = 0.18f;
    private static final float HIT_ANIM_TIME = 0.22f;

    private float hitLockTimer = 0f;
    private float hitAnimTimer = 0f;

    // Collider (smaller than sprite)
    private static final int COLLIDER_W = 12;
    private static final int COLLIDER_H = 18;
    private static final int COLLIDER_OFFSET_Y = 6;

    // Facing
    private boolean facingLeft = false;

    // Animations
    private enum AnimState {IDLE, RUN, JUMP, FALL, HIT}

    private AnimState state = AnimState.IDLE;

    private Animation idleAnim;
    private Animation runAnim;
    private Animation jumpAnim;
    private Animation fallAnim;
    private Animation hitAnim;

    private Animation currentAnim;

    private final String spriteBasePath;

    public boolean isLevelComplete() {
        return levelComplete;
    }


    public Player(float x, float y, String spriteBasePath) {
        this.x = x;
        this.y = y;
        this.spriteBasePath = spriteBasePath.endsWith("/") ? spriteBasePath : (spriteBasePath + "/");
        initAnimations();
        setAnim(AnimState.IDLE);
    }

    public void clampToWorld(TiledMap map) {
        // prevent top-of-screen spawn
        float minY = (COLLIDER_H / 2f) - COLLIDER_OFFSET_Y + 2f;
        if (y < minY) y = minY;

        float minX = COLLIDER_W / 2f + 2f;
        if (x < minX) x = minX;
    }

    /**
     * dx is intended horizontal displacement for this frame (px).
     * Player converts it back into velocity for collision movement.
     */
    public void update(TiledMap map, float dx, boolean jumpPressed, boolean jumpReleased, boolean downHeld, float dt) {
        if (map == null) {
            x += vx * dt;
            y += vy * dt;
            return;
        }

        boolean wasOnGround = onGround;
        boolean jumpedThisFrame = false;
        boolean didDropThisFrame = false;

        // Timers
        if (onGround) coyoteTimer = COYOTE_TIME;
        else coyoteTimer = Math.max(0f, coyoteTimer - dt);

        dropTimer = Math.max(0f, dropTimer - dt);
        if (dropTimer <= 0f) dropping = false;

        invulnTimer = Math.max(0f, invulnTimer - dt);
        hitLockTimer = Math.max(0f, hitLockTimer - dt);
        hitAnimTimer = Math.max(0f, hitAnimTimer - dt);

        // Horizontal intent (only if not locked by hit)
        if (hitLockTimer <= 0f) {
            float targetVx = (dt > 0f) ? (dx / dt) : 0f;
            if (targetVx > MOVE_SPEED) targetVx = MOVE_SPEED;
            if (targetVx < -MOVE_SPEED) targetVx = -MOVE_SPEED;

            vx = targetVx;

            if (vx < -0.01f) facingLeft = true;
            else if (vx > 0.01f) facingLeft = false;
        }

        // Drop through one-way (down + jump on ground)
        if (downHeld && jumpPressed && onGround) {
            dropping = true;
            dropTimer = DROP_TIME;
            onGround = false;
            y += DROP_PUSH;

            jumpBufferTimer = 0f;
            coyoteTimer = 0f;
            didDropThisFrame = true;

            if (DEBUG) System.out.println("[DROP] one-way drop");
        }

        // Jump buffer
        if (!didDropThisFrame) {
            if (jumpPressed) jumpBufferTimer = JUMP_BUFFER;
            else jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        } else {
            jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        }

        // Consume buffered jump if allowed
        if (jumpBufferTimer > 0f) {
            // Ground/coyote
            if (coyoteTimer > 0f && jumpsLeft > 0) {
                vy = JUMP_VEL;
                onGround = false;

                jumpsLeft--;
                jumpedThisFrame = true;

                coyoteTimer = 0f;
                jumpBufferTimer = 0f;
            }
            // Air jump
            else if (!onGround && jumpsLeft > 0) {
                vy = JUMP_VEL * 0.9f;
                jumpsLeft--;
                jumpedThisFrame = true;

                jumpBufferTimer = 0f;
            }
        }

        // Variable jump height
        if (jumpReleased && vy < 0f) vy *= 0.45f;

        // Gravity
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;

        // Move + collide
        moveAndCollide(map, dt);

        // If we just left ground without jumping, we’ve consumed the “ground jump”
        if (wasOnGround && !onGround && !jumpedThisFrame) {
            jumpsLeft = Math.min(jumpsLeft, MAX_JUMPS - 1);
        }

        // Gameplay checks
        checkTraps(map);
        checkGoal(map);

        // Animations
        updateAnimation();
        if (currentAnim != null) currentAnim.update();
    }

    public void tick(double dt) { /* reserved for future use */ }

    public Rect getHurtbox() {
        return new Rect((int) colX(), (int) colY(), COLLIDER_W, COLLIDER_H);
    }

    public void draw(Graphics2D g, Camera cam) {
        Animation anim = (currentAnim != null) ? currentAnim : idleAnim;
        BufferedImage frame = anim.getFrame();

        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        int fw = frame.getWidth();
        int fh = frame.getHeight();

        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        if (facingLeft) g.drawImage(frame, drawX + fw, drawY, -fw, fh, null);
        else g.drawImage(frame, drawX, drawY, null);
    }

    // -------- Internals --------

    private void initAnimations() {
        idleAnim = loadStrip(spriteBasePath + "Idle.png", 11, 8);
        runAnim = loadStrip(spriteBasePath + "Run.png", 12, 4);
        hitAnim = loadStrip(spriteBasePath + "Hit.png", 7, 1);

        jumpAnim = loadStripOptional(spriteBasePath + "Jump.png", 1, 8);
        fallAnim = loadStripOptional(spriteBasePath + "Fall.png", 1, 8);

        if (jumpAnim == null) jumpAnim = idleAnim;
        if (fallAnim == null) fallAnim = idleAnim;

        currentAnim = idleAnim;
    }

    private void updateAnimation() {
        if (hitAnimTimer > 0f) {
            currentAnim = hitAnim;
            return;
        }

        AnimState next;
        if (!onGround) next = (vy < 0f) ? AnimState.JUMP : AnimState.FALL;
        else if (Math.abs(vx) > 1f) next = AnimState.RUN;
        else next = AnimState.IDLE;

        setAnim(next);
    }

    private void setAnim(AnimState next) {
        if (next == state && currentAnim != null) return;

        state = next;
        currentAnim = switch (next) {
            case IDLE -> idleAnim;
            case RUN -> runAnim;
            case JUMP -> jumpAnim;
            case FALL -> fallAnim;
            case HIT -> hitAnim;
        };

        if (currentAnim != null) currentAnim.reset();
    }

    private void checkGoal(TiledMap map) {
        if (levelComplete) return;

        float cx = colX();
        float cy = colY();

        for (Collider c : map.colliders) {
            if (c.type != Collider.Type.GOAL) continue;

            if (c.rect.intersects(cx, cy, COLLIDER_W, COLLIDER_H)) {
                levelComplete = true;
                vx = 0f;
                vy = 0f;
                break;
            }
        }
    }

    private void checkTraps(TiledMap map) {
        if (invulnTimer > 0f) return;

        float cx = colX();
        float cy = colY();

        for (Collider c : map.colliders) {
            if (c.type != Collider.Type.TRAP) continue;

            if (c.rect.intersects(cx, cy, COLLIDER_W, COLLIDER_H)) {
                takeHit(c.damage);
                if (DEBUG) System.out.println("[TRAP] hit " + c.tag + " dmg=" + c.damage);
                break;
            }
        }
    }

    public void reset() {
        resetHp();

        // Clear per-run flags
        levelComplete = false;
         invulnTimer = 0f;
         vx = 0f; vy = 0f;
         onGround = false;
    }


    public int getHp() {
        return hp;
    }

    public boolean isDead() {
        return dead;
    }

    public void resetHp() {
        hp = MAX_HP;
        dead = false;
    }

    public void takeHit(int dmg) {
        if (dead) return;
        if (invulnTimer > 0f) return;
        hp -= Math.max(0, dmg);
        if (hp <= 0) {
            hp = 0;
            dead = true;
        }

        invulnTimer = INVULN_TIME;
        hitLockTimer = HIT_LOCK_TIME;
        hitAnimTimer = HIT_ANIM_TIME;

        if (hitAnim != null) hitAnim.reset();

        // Jump-back opposite of facing
        float dir = facingLeft ? 1f : -1f;
        vx = dir * HIT_KNOCKBACK_X;
        vy = HIT_KNOCKBACK_Y;
        onGround = false;
    }

    // Collider position in world-space (top-left of hurtbox)
    private float colX() {
        return x - COLLIDER_W / 2f;
    }

    private float colY() {
        return (y - COLLIDER_H / 2f) + COLLIDER_OFFSET_Y;
    }

    private void moveAndCollide(TiledMap map, float dt) {
        // ---- Horizontal ----
        float newX = x + vx * dt;
        float cx = newX - COLLIDER_W / 2f;
        float cy = colY();

        if (vx != 0f) {
            for (Collider c : map.colliders) {
                if (c.type == Collider.Type.ONE_WAY) continue;
                if (c.type == Collider.Type.TRAP) continue;
                if (c.type == Collider.Type.GOAL) continue;

                if (c.rect.intersects(cx, cy, COLLIDER_W, COLLIDER_H)) {
                    if (vx > 0f) newX = c.rect.x - COLLIDER_W / 2f;
                    else newX = c.rect.x + c.rect.w + COLLIDER_W / 2f;
                    cx = newX - COLLIDER_W / 2f;
                }
            }
        }
        x = newX;

        // ---- Vertical ----
        float prevColTop = colY();
        float prevColBottom = prevColTop + COLLIDER_H;

        float newY = y + vy * dt;
        float newColTop = (newY - COLLIDER_H / 2f) + COLLIDER_OFFSET_Y;
        float newColBottom = newColTop + COLLIDER_H;

        boolean landed = false;

        if (vy != 0f) {
            float testX = colX();

            for (Collider c : map.colliders) {
                if (c.type == Collider.Type.TRAP) continue;
                if (c.type == Collider.Type.GOAL) continue;

                if (!c.rect.intersects(testX, newColTop, COLLIDER_W, COLLIDER_H)) continue;

                if (vy > 0f) {
                    if (c.type == Collider.Type.ONE_WAY) {
                        if (dropping) continue;

                        float platformTop = c.rect.y;
                        boolean wasAbove = prevColBottom <= platformTop + 0.5f;
                        boolean nowCrossed = newColBottom >= platformTop;

                        float playerLeft = testX;
                        float playerRight = testX + COLLIDER_W;

                        float platLeft = c.rect.x + ONE_WAY_EDGE_PAD;
                        float platRight = c.rect.x + c.rect.w - ONE_WAY_EDGE_PAD;

                        boolean overlapsHoriz = playerRight > platLeft && playerLeft < platRight;
                        if (!(wasAbove && nowCrossed && overlapsHoriz)) continue;
                    }

                    // land on top
                    float desiredColTop = c.rect.y - COLLIDER_H;
                    newY = (desiredColTop - COLLIDER_OFFSET_Y) + (COLLIDER_H / 2f);
                    vy = 0f;
                    landed = true;
                } else {
                    // Moving up: ONE_WAY should be pass-through
                    if (c.type == Collider.Type.ONE_WAY) continue;

                    // hit head
                    float desiredColTop = c.rect.y + c.rect.h;
                    newY = (desiredColTop - COLLIDER_OFFSET_Y) + (COLLIDER_H / 2f);
                    vy = 0f;
                }

                newColTop = (newY - COLLIDER_H / 2f) + COLLIDER_OFFSET_Y;
                newColBottom = newColTop + COLLIDER_H;
            }
        }

        y = newY;

        if (landed) {
            onGround = true;
            jumpsLeft = MAX_JUMPS;
        } else if (vy != 0f) {
            onGround = false;
        }
    }

    private Animation loadStrip(String path, int frameCount, int frameDelay) {
        try {
            BufferedImage sheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(path),
                    "Missing sprite sheet: " + path
            ));

            int fw = sheet.getWidth() / frameCount;
            int fh = sheet.getHeight();

            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = sheet.getSubimage(i * fw, 0, fw, fh);
            }
            return new Animation(frames, frameDelay);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load sprite sheet: " + path, e);
        }
    }

    private Animation loadStripOptional(String path, int frameCount, int frameDelay) {
        try {
            if (getClass().getResource(path) == null) return null;

            BufferedImage sheet = ImageIO.read(Objects.requireNonNull(getClass().getResource(path)));
            if (sheet == null) return null;

            int fw = sheet.getWidth() / frameCount;
            int fh = sheet.getHeight();

            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int i = 0; i < frameCount; i++) {
                frames[i] = sheet.getSubimage(i * fw, 0, fw, fh);
            }
            return new Animation(frames, frameDelay);

        } catch (Exception ignored) {
            return null;
        }
    }
}

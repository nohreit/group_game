package main.java.game.entity;

import main.java.game.gfx.Animation;
import main.java.game.gfx.Camera;
import main.java.game.map.TiledMap;
import main.java.game.physics.Rect;
import main.java.game.physics.Collider;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

public class Player {

    // World position in pixels (treated as center of the sprite)
    public float x;
    public float y;

    private static final boolean DEBUG = true;

    // ---- Platformer physics ----
    private float vx = 0f, vy = 0f; // Velocity
    private boolean onGround = false; // Check if player is on the ground

    // Tune these
    private static final float MOVE_SPEED = 120f;  // px/sec
    private static final float GRAVITY = 520f;  // px/sec^2
    private static final float JUMP_VEL = -220f; // px/sec (negative = up)
    private static final float MAX_FALL = 520f;  // px/sec

    // Jump feel helpers
    private float coyoteTimer = 0f;
    private float jumpBufferTimer = 0f;
    private static final int MAX_JUMPS = 2;
    private int jumpsLeft = MAX_JUMPS; // Number of jumps we have left (default = 2)

    // One-way drop-through
    private boolean dropping = false;
    private static final float DROP_PUSH = 2f;        // px: nudge down to clear the top surface


    // COYOTE_TIME: Short grace period after leaving the ground during which the player is still allowed to jump. (From the Looney Tones cartoon. Meep Meep :)
    private static final float COYOTE_TIME = 0.08f; // seconds
    private static final float JUMP_BUFFER = 0.10f; // seconds; countdown timer on how lo

    // Hurt timer helper
    private float hurtTimer = 0f;
    private float hurtCooldown = 0.5f;

    // Knockback constants
    private static final float HIT_KNOCKBACK_X = 140f;  // px/sec
    private static final float HIT_KNOCKBACK_Y = -180f; // px/sec (negative = up)
    private static final float HIT_LOCK_TIME = 0.18f; // optional: brief control lock
    private static final float HIT_ANIM_TIME = 0.22f;

    private float hitLockTimer = 0f;
    private float hitAnimTimer = 0f;


    // Collider (smaller than sprite)
    private static final int COLLIDER_W = 12;
    private static final int COLLIDER_H = 18;
    private static final int COLLIDER_OFFSET_Y = 6; // pushes collider down inside sprite

    private static final float ONE_WAY_EDGE_PAD = 2f;

    // Facing
    private boolean facingLeft = false;

    private float dropTimer = 0f;
    private static final float DROP_TIME = 0.18f; // seconds you can fall through one-way


    // Animations
    private enum AnimState {IDLE, RUN, JUMP, FALL, HIT}

    private AnimState state = AnimState.IDLE;

    private Animation idleAnim;
    private Animation runAnim;
    private Animation jumpAnim; // optional, falls back to idle
    private Animation fallAnim; // optional, falls back to idle
    private Animation hitAnim;

    private Animation currentAnim;

    // Base folder where sprites live
    private final String spriteBasePath;

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


    public void update(TiledMap map, float dx, boolean jumpPressed, boolean jumpReleased, boolean downHeld, float dt) {
        boolean wasOnGround = onGround;
        boolean jumpedThisFrame = false;
        boolean didDrop = false;


        // Update jump timers
        if (onGround) coyoteTimer = COYOTE_TIME;
        else coyoteTimer = Math.max(0f, coyoteTimer - dt);

        dropTimer = Math.max(0f, dropTimer - dt);
        if (dropTimer <= 0f) dropping = false;
        hurtTimer = Math.max(0f, hurtTimer - dt);
        hitLockTimer = Math.max(0f, hitLockTimer - dt);
        hitAnimTimer = Math.max(0f, hitAnimTimer - dt);

        if (hitLockTimer <= 0f) {
            float targetVx = (dt > 0f) ? (dx / dt) : 0f;
            if (targetVx > MOVE_SPEED) targetVx = MOVE_SPEED; // Move to the right (positive movement speed)
            if (targetVx < -MOVE_SPEED) targetVx = -MOVE_SPEED; // Move to the left (negative movement speed)

            vx = targetVx; // Keep the last speed value before idle

            // Facing based on movement intent
            if (vx < -0.01f) facingLeft = true; // For the player idleSprite to face left
            else if (vx > 0.01f) facingLeft = false; // For the player idleSprite to face right (default)
        }

        if (downHeld && jumpPressed && onGround) {
            System.out.println("Drop down");
            dropping = true;
            dropTimer = DROP_TIME;
            onGround = false;
            y += DROP_PUSH;

            jumpBufferTimer = 0f;
            coyoteTimer = 0f;
            didDrop = true;
        }

        if (!didDrop) {
            if (jumpPressed) jumpBufferTimer = JUMP_BUFFER;
            else jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        } else {
            jumpBufferTimer = Math.max(0f, jumpBufferTimer - dt);
        }

// Consume buffered jump if allowed (ground/coyote OR air double-jump)
        if (jumpBufferTimer > 0f) {

            // 1) Ground / coyote jump
            if (coyoteTimer > 0f && jumpsLeft > 0) {
                vy = JUMP_VEL;
                onGround = false;

                jumpsLeft--;              // ✅ consume jump
                jumpedThisFrame = true;

                coyoteTimer = 0f;
                jumpBufferTimer = 0f;
            }
            // 2) Air jump (double jump)
            else if (!onGround && jumpsLeft > 0) {
                vy = JUMP_VEL * 0.9f;            // you can do *0.9f for a weaker 2nd jump if you want
                jumpsLeft--;              // ✅ consume jump
                jumpedThisFrame = true;

                jumpBufferTimer = 0f;
            }
        }


        // Variable jump: releasing jump early cuts upward velocity
        if (jumpReleased && vy < 0f) {
            vy *= 0.45f;
        }

        // Gravity
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) vy = MAX_FALL;

        // Move + collide
        moveAndCollide(map, dt);

        // If we just left the ground without jumping, count that as "using" the ground jump
        if (wasOnGround && !onGround && !jumpedThisFrame) {
            jumpsLeft = Math.min(jumpsLeft, MAX_JUMPS - 1); // usually becomes 1
        }

        checkTraps(map);

        // Update anim state and tick frames
        updateAnimation();
        if (currentAnim != null) currentAnim.update();
    }

    // Optional (kept because your GamePanel calls tick(dt); safe no-op)
    public void tick(double dt) { /* no timers needed here for now */ }

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

        if (facingLeft) {
            g.drawImage(frame, drawX + fw, drawY, -fw, fh, null);
        } else {
            g.drawImage(frame, drawX, drawY, null);
        }
    }

    // -------- Internals --------

    private void initAnimations() {
        // Virtual Guy strips are horizontal. Use exact frame counts:
        idleAnim = loadStrip(spriteBasePath + "Idle.png", 11, 8);
        runAnim = loadStrip(spriteBasePath + "Run.png", 12, 4);

        hitAnim = loadStrip(spriteBasePath + "Hit.png", 7, 1);

        // Optional sheets (if you don't have them, we fall back to idle)
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

    // Collider position in world-space
    private float colX() {
        return x - COLLIDER_W / 2f;
    }

    private float colY() {
        return (y - COLLIDER_H / 2f) + COLLIDER_OFFSET_Y;
    }

    private void moveAndCollide(TiledMap map, float dt) {
        if (map == null) {
            x += vx * dt;
            y += vy * dt;
            return;
        }

        // ---- Horizontal ----
        float newX = x + vx * dt;
        float cx = newX - COLLIDER_W / 2f;
        float cy = colY();

        if (vx != 0f) {
            for (Collider c : map.colliders) {

                if (c.rect.intersects(cx, cy, COLLIDER_W, COLLIDER_H)) {
                    if (c.type == Collider.Type.ONE_WAY) continue;
                    if (c.type == Collider.Type.TRAP) continue;

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
                // broad-phase AABB check using new position
                if (!c.rect.intersects(testX, newColTop, COLLIDER_W, COLLIDER_H)) continue;

                if (vy > 0f) {
                    if (c.type == Collider.Type.ONE_WAY) {
                        // ignore one-way platforms while dropping
                        if (dropping) continue;

                        float platformTop = c.rect.y;

                        // Only allow landing when we were above the top and are crossing it while falling
                        boolean wasAbove = prevColBottom <= platformTop + 0.5f;
                        boolean nowCrossed = newColBottom >= platformTop;

                        // extra horizontal guard (prevents edge-snags)
                        float playerLeft = testX;
                        float playerRight = testX + COLLIDER_W;

                        float platLeft = c.rect.x + ONE_WAY_EDGE_PAD;
                        float platRight = c.rect.x + c.rect.w - ONE_WAY_EDGE_PAD;

                        boolean overlapsHorizontally = playerRight > platLeft && playerLeft < platRight;

                        if (!(wasAbove && nowCrossed && overlapsHorizontally)) continue;
                    }


                    if (c.type == Collider.Type.TRAP) continue;


                    // land on top
                    float desiredColTop = c.rect.y - COLLIDER_H;
                    newY = (desiredColTop - COLLIDER_OFFSET_Y) + (COLLIDER_H / 2f);
                    vy = 0f;
                    landed = true;

                    // recompute for remaining checks (optional)

                } else {
                    // Moving up: only SOLID stops you; ONE_WAY should be pass-through
                    if (c.type == Collider.Type.ONE_WAY) continue;
                    if (c.type == Collider.Type.TRAP) continue;


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
        } else if (vy != 0f) onGround = false;
    }

    private void checkTraps(TiledMap map) {
        if (hurtTimer > 0f) return;

        float cx = x - COLLIDER_W / 2f;
        float cy = colY();

        for (Collider c : map.colliders) {
            if (c.type != Collider.Type.TRAP) continue;

            if (c.rect.intersects(cx, cy, COLLIDER_W, COLLIDER_H)) {
                takeHit(c.damage);
                hurtTimer = hurtCooldown;
                if (DEBUG) System.out.println("[TRAP] hit " + c.tag + " dmg=" + c.damage);
                break;
            }
        }
    }

    public void takeHit(int dmg) {
        // hp -= dmg; (optional for now)
        if (hurtTimer > 0f) return;

        hurtTimer = 0.40f; // Invulnerability window
        hitLockTimer = HIT_LOCK_TIME;
        hitAnimTimer = HIT_ANIM_TIME;

        if (hitAnim != null) hitAnim.reset();

        // Jump-back opposite of facing
        float dir = facingLeft ? 1f : -1f;

        vx = dir * HIT_KNOCKBACK_X;
        vy = HIT_KNOCKBACK_Y;

        onGround = false;
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

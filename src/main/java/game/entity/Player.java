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

public class Player {

    // World position in pixels (treated as center of the sprite)
    public float x;
    public float y;

    // Tunable collision box
    private static final int COLLIDER_W = 16;
    private static final int COLLIDER_H = 10;
    private static final int FOOT_OFFSET_Y = 24; // feet-anchored collider for top-down sprites

    // --- Combat hitbox constants (pixels, world-space) ---
    private static final int HITBOX_W = 60;
    private static final int HITBOX_H = 90;

    // Offset distance from feet collider (more like belly collider) to hitbox
    private static final int HITBOX_X_OFFSET = 0;  // forward from collider edge
    private static final int HITBOX_Y_OFFSET = 40;  // downward from feet collider top

    // --- Knockback ---
    private float kbVx = 0f, kbVy = 0f;  // px/sec
    private int kbTicks = 0;

    private static final int KB_TICKS_ON_GUARD = 8;
    private static final float KB_SPEED_ON_GUARD = 240f;


    private boolean facingLeft = false;

    private float invulnTimer = 0f;
    private static final float INVULN_TIME = 0.40f; // 400ms

    private static final int MAX_HP = 100;
    private int hp = MAX_HP;

    private boolean dead = false;

    // Approximate combo window: ~1000 ms at 60 FPS
    private static final int WINDOW_TICKS = 60;
    private boolean guarding = false;


    public void setGuarding(boolean guarding) {
        this.guarding = guarding;
    }

    // High-level animation state
    private enum AnimationType {
        IDLE, RUN, ATTACK, GUARD
    }

    // Direction for idle/run
    private enum MoveType {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    // Attack phase
    private enum AttackPhase {
        NONE, ATTACK1, ATTACK2
    }

    private AnimationType currentAnimType = AnimationType.IDLE;
    private MoveType currentMoveType = MoveType.DOWN;
    private AttackPhase attackPhase = AttackPhase.NONE;

    // Animation sets
    private Animation idleDownAnim;
    private Animation idleUpAnim;
    private Animation idleLeftAnim;
    private Animation idleRightAnim;

    private Animation runDownAnim;
    private Animation runUpAnim;
    private Animation runLeftAnim;
    private Animation runRightAnim;

    private Animation attackAnimation;   // Attack 1
    private Animation attack2Animation;  // Attack 2 (combo)
    private Animation guardAnimation;

    private Animation currentAnimation;

    // Attack playback control
    private boolean attackPlaying = false;
    private int attackTicks = 0;
    private int attackDurationTicks1 = 0;
    private int attackDurationTicks2 = 0;
    private boolean lastAttackPressed = false;
    private int attackId = 0;        // increments each time an attack starts

    // Post-attack combo window (for Attack2)
    private boolean inComboWindow = false;
    private int comboWindowTicksRemaining = 0;

    // Base folder where the PLAYER warrior sprites live
    // "/main/resources/sprites/player/Black_Units/Warrior/"
    private final String spriteBasePath;

    public Player(float x, float y, String spriteBasePath) {
        this.x = x;
        this.y = y;
        this.spriteBasePath = spriteBasePath.endsWith("/") ? spriteBasePath : (spriteBasePath + "/");
        initAnimations();
    }

    // Initialize animations for the player
    private void initAnimations() {
        try {
            BufferedImage idleSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Idle.png"),
                    "Missing sprite sheet: " + spriteBasePath + "Warrior_Idle.png"
            ));

            BufferedImage runSheet = ImageIO.read(Objects.requireNonNull(
                    getClass().getResource(spriteBasePath + "Warrior_Run.png"),
                    "Missing sprite sheet: " + spriteBasePath + "Warrior_Run.png"
            ));

            int frameWidth = 192;
            int frameHeight = 192;
            int idleFramesCount = 8;
            int runFramesCount = 6;


            // Slice idle
            BufferedImage[] idleFrames = new BufferedImage[idleFramesCount];
            for (int i = 0; i < idleFramesCount; i++) {
                idleFrames[i] = idleSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Slice run
            BufferedImage[] runFrames = new BufferedImage[runFramesCount];
            for (int i = 0; i < runFramesCount; i++) {
                runFrames[i] = runSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }


            // For now, all directions reuse same frames
            idleDownAnim = new Animation(idleFrames, 8);
            idleUpAnim = new Animation(idleFrames, 8);
            idleLeftAnim = new Animation(idleFrames, 8);
            idleRightAnim = new Animation(idleFrames, 8);

            runDownAnim = new Animation(runFrames, 6); // slightly faster
            runUpAnim = new Animation(runFrames, 6);
            runLeftAnim = new Animation(runFrames, 6);
            runRightAnim = new Animation(runFrames, 6);


            // Load attack and guard animations
            attackAnimation = loadAnimation(spriteBasePath + "Warrior_Attack1.png", 4, 6);
            attack2Animation = loadAnimation(spriteBasePath + "Warrior_Attack2.png", 4, 6);
            guardAnimation = loadAnimation(spriteBasePath + "Warrior_Guard.png", 6, 10);


            // Pre-compute attack durations in ticks (frames * frameDelay)
            if (attackAnimation != null) {
                attackDurationTicks1 = attackAnimation.getFrameCount() * attackAnimation.getFrameDelay();
            }
            if (attack2Animation != null) {
                attackDurationTicks2 = attack2Animation.getFrameCount() * attack2Animation.getFrameDelay();
            }

            // Start in idle facing down
            currentAnimType = AnimationType.IDLE;
            currentMoveType = MoveType.DOWN;
            currentAnimation = idleDownAnim;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Warrior sprite sheets", e);
        }
    }

    public void takeHit(int dmg) {
        if (dead) return;

        // Guard blocks damage (for now)
        if (guarding) return;

        // Invulnerability window to prevent 60 hits/sec
        if (invulnTimer > 0f) return;

        hp -= dmg;
        invulnTimer = INVULN_TIME;

        if (hp <= 0) {
            hp = 0;
            dead = true;

            // stop combat state immediately
            attackPlaying = false;
            attackPhase = AttackPhase.NONE;
            attackTicks = 0;
            inComboWindow = false;
            comboWindowTicksRemaining = 0;
        }
    }

    private void die() {
        dead = true;
        // stop movement / input
        // trigger death animation or fade
    }

    public boolean isDead() {
        return dead;
    }

    public float getHpPercent() {
        return hp / (float) MAX_HP;
    }

    // For timers
    public void tick(double dt) {
        if (invulnTimer > 0f) {
            invulnTimer -= (float) dt;
            if (invulnTimer < 0f) invulnTimer = 0f;
        }
    }

    // Player hitbox to get damage from enemies
    public Rect getHurtbox() {
        return new Rect((int) getColX(), (int) getColY(), COLLIDER_W, COLLIDER_H);
    }

    public boolean isGuarding() {
        return guarding;
    }

    public void applyKnockbackFrom(float fromX, float fromY, float kbSpeed, int ticks) {
        float vx = x - fromX;
        float vy = y - fromY;

        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 0.0001f) {
            vx = 1f;
            vy = 0f;
            len = 1f;
        }

        float nx = vx / len;
        float ny = vy / len;

        kbVx = nx * kbSpeed;
        kbVy = ny * kbSpeed;
        kbTicks = Math.max(kbTicks, ticks);
    }


    private float getColX() {
        return x - COLLIDER_W / 2f;
    }

    private float getColY() {
        // place the box near the feet, not at the center
        return y + FOOT_OFFSET_Y - COLLIDER_H;
    }

    // Axis-by-axis movement with collider resolution
    public void move(TiledMap map, float dx, float dy) {
        // Knockback overrides input movement while active
        if (kbTicks > 0) {
            dx = kbVx * (1f / WINDOW_TICKS);
            dy = kbVy * (1f / WINDOW_TICKS);
            kbTicks--;
        }


        // --- Horizontal movement ---
        if (dx != 0) {
            float newX = x + dx;

            // compute new collision box for proposed X
            float colX = newX - COLLIDER_W / 2f;
            float colY = getColY(); // uses current y

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, COLLIDER_W, COLLIDER_H)) {
                    if (dx > 0) {
                        // moving right → place feet just to the left of collider
                        newX = r.x - COLLIDER_W / 2f;
                    } else if (dx < 0) {
                        // moving left → place feet just to the right of collider
                        newX = r.x + r.w + COLLIDER_W / 2f;
                    }
                    // recompute colX for any further checks
                    colX = newX - COLLIDER_W / 2f;
                }
            }

            x = newX;
        }

        // --- Vertical movement ---
        if (dy != 0) {
            float newY = y + dy;

            float colX = getColX(); // uses current x
            float colY = newY + FOOT_OFFSET_Y - COLLIDER_H; // same logic as getColY() but with newY

            for (Rect r : map.colliders) {
                if (r.intersects(colX, colY, COLLIDER_W, COLLIDER_H)) {
                    if (dy > 0) {
                        // moving down → feet hit top of cliff
                        newY = r.y - FOOT_OFFSET_Y;
                    } else if (dy < 0) {
                        // moving up → head hits something above (rare on cliffs)
                        newY = r.y + r.h - FOOT_OFFSET_Y + COLLIDER_H;
                    }
                    colY = newY + FOOT_OFFSET_Y - COLLIDER_H;
                }
            }

            y = newY;
        }
    }

    public void update(float dx, float dy, boolean attackPressed, boolean guardPressed) {
        if (dead) {
            // optionally play a death animation, or just idle
            // setAnimation(AnimationType.IDLE, currentMoveType);
            if (currentAnimation != null) currentAnimation.update();
            lastAttackPressed = attackPressed;
            return;
        }

        AnimationType animType;
        MoveType moveType = currentMoveType;

        // Edge detection for attack button
        boolean attackJustPressed = attackPressed && !lastAttackPressed;

        // Update guarding state from input (hold-to-guard)
        guarding = guardPressed;

        // If guarding, block starting attacks + clear combo window
        if (guarding) {
            attackJustPressed = false;          // prevents new attacks
            inComboWindow = false;              // optional: don’t allow Attack2 window while guarding
            comboWindowTicksRemaining = 0;

            // Guard animation
            if (!attackPlaying && guardAnimation != null) {
                animType = AnimationType.GUARD;
                setAnimation(animType, moveType);
                if (currentAnimation != null) currentAnimation.update();
                lastAttackPressed = attackPressed;
                return; // stop here so nothing overrides guard
            }
            // If an attack is currently playing, attack will continue (attack has priority)
        }

        // 1. Handle attack start and combo window usage
        if (attackJustPressed) {
            if (!attackPlaying) {
                if (inComboWindow && attack2Animation != null) {
                    // Start Attack2 from the combo window
                    attackPlaying = true;
                    attackPhase = AttackPhase.ATTACK2;
                    attackTicks = 0;
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                    attackId++;
                } else if (attackAnimation != null) {
                    // Fresh Attack1
                    attackPlaying = true;
                    attackPhase = AttackPhase.ATTACK1;
                    attackTicks = 0;
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                    attackId++;
                }
            }
            // If an attack is already playing (ATTACK1 or ATTACK2), ignore extra presses
        }

        // 2. If an attack is playing, it overrides other states
        if (attackPlaying) {
            animType = AnimationType.ATTACK;
            attackTicks++;

            int currentDuration = (attackPhase == AttackPhase.ATTACK2)
                    ? attackDurationTicks2
                    : attackDurationTicks1;

            if (attackTicks >= currentDuration) {
                AttackPhase finishedPhase = attackPhase;
                attackPlaying = false;
                attackPhase = AttackPhase.NONE;
                attackTicks = 0;

                if (finishedPhase == AttackPhase.ATTACK1 && attack2Animation != null) {
                    inComboWindow = true;
                    comboWindowTicksRemaining = WINDOW_TICKS;
                } else {
                    inComboWindow = false;
                    comboWindowTicksRemaining = 0;
                }
            }
        } else {
            // 3. Normal movement-based states (RUN / IDLE)
            boolean isMoving = (dx != 0 || dy != 0);

            if (isMoving) {
                animType = AnimationType.RUN;

                // Choose facing based on dominant axis
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) {
                        moveType = MoveType.RIGHT;
                        facingLeft = false;
                    } else {
                        moveType = MoveType.LEFT;
                        facingLeft = true;
                    }
                } else {
                    moveType = (dy > 0) ? MoveType.DOWN : MoveType.UP;
                }
            } else {
                animType = AnimationType.IDLE;
                // keep last moveType to idle facing last direction
            }
        }

        // 4. Tick down the combo window if active
        if (inComboWindow) {
            comboWindowTicksRemaining--;
            if (comboWindowTicksRemaining <= 0) {
                inComboWindow = false;
                comboWindowTicksRemaining = 0;
            }
        }

        // 5. Apply animation change (only resets if anim type or direction changed)
        setAnimation(animType, moveType);

        // 6. Advance current animation frame
        if (currentAnimation != null) {
            currentAnimation.update();
        }

        // Remember previous attack state for edge detection
        lastAttackPressed = attackPressed;
    }

    public int getAttackId() {
        return attackId;
    }

    // Only “active frames” deal damage (avoid hitting on windup/recovery)
    public boolean isAttackActive() {
        if (!attackPlaying) return false;

        int currentDuration = (attackPhase == AttackPhase.ATTACK2)
                ? attackDurationTicks2
                : attackDurationTicks1;

        if (currentDuration <= 0) return false;

        int start = (int) (currentDuration * 0.30f);
        int end = (int) (currentDuration * 0.70f);

        return attackTicks >= start && attackTicks <= end;
    }


    // Get attack hitbox which toggles between attacks.
    public Rect getAttackHitbox() {
        if (!isAttackActive()) return null;

        float colX = getColX();
        float colY = getColY();

        // place hitbox vertically relative to feet collider (downward offset)
        int hbY = (int) (colY + HITBOX_Y_OFFSET - HITBOX_H);

        // place hitbox in front of the feet collider
        int hbX = facingLeft
                ? (int) (colX - HITBOX_X_OFFSET - HITBOX_W)
                : (int) (colX + COLLIDER_W + HITBOX_X_OFFSET);

        return new Rect(hbX, hbY, HITBOX_W, HITBOX_H);
    }


    // Helper to choose animation based on {AnimationType, MoveType}
    private Animation getAnimation(AnimationType type, MoveType move) {
        // Attack / guard ignore direction for now. We just flip with facingLeft
        if (type == AnimationType.ATTACK) {
            if (attackPhase == AttackPhase.ATTACK2 && attack2Animation != null) {
                return attack2Animation;
            }
            return (attackAnimation != null) ? attackAnimation : idleDownAnim;
        }
        if (type == AnimationType.GUARD) {
            return (guardAnimation != null) ? guardAnimation : idleDownAnim;
        }

        if (type == AnimationType.IDLE) {
            return switch (move) {
                case UP -> idleUpAnim;
                case LEFT -> idleLeftAnim;
                case RIGHT -> idleRightAnim;
                case DOWN, NONE -> idleDownAnim;
            };
        } else { // RUN
            return switch (move) {
                case UP -> (runUpAnim != null ? runUpAnim : idleUpAnim);
                case DOWN -> (runDownAnim != null ? runDownAnim : idleDownAnim);
                case LEFT -> (runLeftAnim != null ? runLeftAnim : idleLeftAnim);
                case RIGHT -> (runRightAnim != null ? runRightAnim : idleRightAnim);
                case NONE -> idleDownAnim;
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
        // blink while invulnerable.
        if (invulnTimer > 0f) {
            if (((int) (invulnTimer * 20)) % 2 == 0) return;
        }

        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);

        Animation anim = (currentAnimation != null) ? currentAnimation : idleDownAnim;
        BufferedImage frame = anim.getFrame();
        int fw = frame.getWidth();
        int fh = frame.getHeight();

        // Draw centered
        int drawX = sx - fw / 2;
        int drawY = sy - fh / 2;

        if (facingLeft) {
            // Flip horizontally player sprite
            g.drawImage(
                    frame, drawX + fw, drawY, -fw, fh, null
            );
        } else {
            // Normal drawing
            g.drawImage(frame, drawX, drawY, null);
        }

        // --- HP bar (won’t show when fully dead) ---
        if (!dead) {
            drawHpBar(g, cam, fw, fh);
        }
    }

    // Loads animations
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

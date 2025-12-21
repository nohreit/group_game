package main.java.game.physics;

public class Collider {

    public enum Type {SOLID, ONE_WAY, TRAP, GOAL}

    public final Rect rect;
    public final Type type;

    // Optional metadata (primarily for TRAP)
    public final String tag;   // e.g., "spike"
    public final int damage;   // e.g., 1

    public Collider(Rect rect, Type type) {
        this(rect, type, "", (type == Type.TRAP ? 1 : 0));
    }

    public Collider(Rect rect, Type type, String tag, int damage) {
        if (rect == null) throw new IllegalArgumentException("rect cannot be null");
        this.rect = rect;
        this.type = type;
        this.tag = (tag == null) ? "" : tag;
        this.damage = damage;
    }

    public static Collider trap(Rect rect, String tag, int damage) {
        return new Collider(rect, Type.TRAP, tag, damage);
    }
}

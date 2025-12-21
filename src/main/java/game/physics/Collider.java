package main.java.game.physics;

public class Collider {
    public enum Type {SOLID, ONE_WAY, TRAP, GOAL}

    public final Rect rect;
    public final Type type;

    // optional metadata (used for TRAP)
    public final String tag;   // e.g. "spike"
    public final int damage;   // e.g. 1

    public Collider(Rect rect, Type type) {
        this(rect, type, "", 0);
    }

    public Collider(Rect rect, Type type, String tag, int damage) {
        this.rect = rect;
        this.type = type;
        this.tag = tag;
        this.damage = damage;
    }
}

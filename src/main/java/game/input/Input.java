package main.java.game.input;


import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


public class Input extends KeyAdapter {
    private boolean up, down, left, right, attack, guard, restart;


    @Override
    public void keyPressed(KeyEvent e) {
        toggle(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        toggle(e.getKeyCode(), false);
    }


    private void toggle(int code, boolean on) {
        switch (code) {
            case KeyEvent.VK_W, KeyEvent.VK_UP -> up = on;
            case KeyEvent.VK_S, KeyEvent.VK_DOWN -> down = on;
            case KeyEvent.VK_A, KeyEvent.VK_LEFT -> left = on;
            case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> right = on;
            case KeyEvent.VK_J -> attack = on;
            case KeyEvent.VK_K -> guard = on;
            case KeyEvent.VK_R -> restart = on;
        }
    }


    public boolean isUp() {
        return up;
    }

    public boolean isDown() {
        return down;
    }

    public boolean isLeft() {
        return left;
    }

    public boolean isRight() {
        return right;
    }

    public boolean isAttack() {
        return attack;
    }

    public boolean isGuard() {
        return guard;
    }

    public boolean isRestart() {
        return restart;
    }
}
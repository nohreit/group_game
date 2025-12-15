# Top-Down Warrior Game (Solo Project)

## Overview

This project is a solo-built top-down 2D action game inspired by classic Zelda-like combat.  
The goal was to implement a **complete, playable gameplay loop** within a limited time frame, focusing on combat feel,
animation state management, and basic AI.

The game features player movement, enemy AI, combat interactions, health systems, and win/lose conditions.

---

## Core Features

- **Player movement & collision**
    - Tile-based map with solid colliders
    - Feet-anchored collision for top-down sprites
- **Combat system**
    - Player attacks with active hit frames
    - Enemy attacks with cooldowns and telegraphed swings
    - Guard mechanic that blocks damage and applies knockback to the player
    - Knockback on successful hits
- **Enemy AI**
    - Aggro radius and stop distance
    - Chase → attack behavior
    - Attack cooldown longer than player for fairness
- **Health & Death**
    - HP bars for player and enemies
    - Enemy fade-out on death
    - Player death triggers game over
- **Game states**
    - PLAYING
    - GAME OVER (player defeated)
    - WIN (all enemies defeated)

---

## Controls

- **WASD / Arrow Keys** — Move
- **Attack key** — Attack
- **Guard key (hold)** — Block attacks (pushes player back)
- **R** — Restart after win or game over

---

## Build & Runtime Configuration

- **Language:** Java
- **Java Version:** Java 17 (OpenJDK / Oracle JDK)
- **Rendering:** Java Swing / AWT
- **Game Loop:** Fixed timestep (60 ticks per second)
- **Platform:** Desktop (Windows / Linux / macOS)

### Running the Game

1. Open the project in an IDE (IntelliJ IDEA recommended)
2. Ensure the project SDK is set to **Java 17**
3. Run the main entry point:

No external game engine or third-party libraries are required.

---

## Tools Used

- **Tiled Map Editor** — used to design tile maps and export them as JSON for use in the game  
  https://www.mapeditor.org/

---

## Assets & Credits

All sprite and environment assets used in this project come from the following free asset pack:

**Tiny Swords — Pixel Frog**  
https://pixelfrog-assets.itch.io/tiny-swords

Assets are used for educational purposes.

---

## Project Status

This project is **feature-complete for its intended scope**.  
There are many possible extensions (additional enemy types, stamina, sound effects, polish), but the current version
demonstrates a full gameplay loop and core systems.

---

## Notes on Scope

This project was completed as a **solo effort**.  
A separate group project exists but remains partial due to time and coordination constraints; this solo project
represents the most complete and polished work.

---

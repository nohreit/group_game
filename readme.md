# Platformer Game Project

A 2D platformer built in **Java (Swing)** using **custom rendering, physics, and collision systems**.
Levels are designed in **Tiled** and loaded from JSON at runtime.
The game features hazards, one-way platforms, multi-stage progression, HP, and win/lose states.

---

## 🛠️ Tech Stack

* **Language:** Java
* **Java Version:** **Java SDK 23**
* **UI / Rendering:** Java Swing + `Graphics2D`
* **Level Editor:** Tiled (JSON export)
* **Architecture:**

    * Custom fixed-step game loop
    * Tile-based rendering with camera culling
    * Custom collision system (SOLID, ONE_WAY, TRAP, GOAL)

---

## ⚠️ Java Version Note

This project was developed and tested using **Java SDK 23**.

If compiling or running with an older Java version, compatibility is **not guaranteed**.
It is recommended to use **Java 23 or newer** when building or running this project.

---

## 🎮 Gameplay Overview

* **Movement:** Left / Right, Jump
* **Hazards:** Spikes and traps reduce HP
* **HP System:** Player starts with 3 HP
* **Stages:**

    * Stage 1 → reach goal to advance
    * Stage 2 → reach goal to win
* **Game States:** Playing, Game Over, Win
* **Restart:** Press **R**

---

## 🛠️ Tech Stack

* **Language:** Java
* **UI / Rendering:** Java Swing + `Graphics2D`
* **Level Editor:** Tiled (JSON export)
* **Architecture:**

    * Custom game loop
    * Tile-based rendering with camera culling
    * Custom collision system (SOLID, ONE_WAY, TRAP, GOAL)

---

## 🗺️ Level Design

Levels are created in **Tiled** using:

* Tile layers: `Background`, `Main`, `Collision`, `OneWay`
* Object layers:

    * `Gameplay` (PlayerSpawn, Goal)
    * `Traps`

Tile transformations (rotation / flipping) are fully supported.

---

## 🎨 Asset Credits

### Player Character

**Virtual Guy**
From *Pixel Adventure* asset pack
Created by **Pixel Frog**

* Source: [https://pixelfrog-assets.itch.io/pixel-adventure-1](https://pixelfrog-assets.itch.io/pixel-adventure-1)
* License: Free for commercial and non-commercial use
* Modifications: Used as-is, sliced and animated in code

---

### Environment Tiles & Platforms

From *Pixel Adventure* asset pack
Created by **Pixel Frog**

* Terrain tiles
* One-way platforms
* Background tiles

---

### Traps / Spikes

From *Pixel Adventure* asset pack
Created by **Pixel Frog**

* Spike tiles (including rotated/flipped variants)

---

### Assets Source

[https://pixelfrog-assets.itch.io/pixel-adventure-1](https://pixelfrog-assets.itch.io/pixel-adventure-1)

---

## 📦 Tools Used

* **Tiled Map Editor**
  [https://www.mapeditor.org/](https://www.mapeditor.org/)

* **Java SE (Swing, AWT)**
  [https://www.oracle.com/java/](https://www.oracle.com/java/)

---

## 📜 License & Disclaimer

This project is for **educational purposes**.
All third-party assets belong to their respective creators and are used under the terms provided by the original
authors.

No ownership is claimed over third-party art assets.

---
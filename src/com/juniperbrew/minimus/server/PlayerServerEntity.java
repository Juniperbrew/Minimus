package com.juniperbrew.minimus.server;

import java.util.HashMap;

/**
 * Created by Juniperbrew on 24.9.2015.
 */
public class PlayerServerEntity extends ServerEntity {

    int lives;
    float respawnTimer;

    public PlayerServerEntity(int id, float x, float y, float width, float height, int maxHealth, int team, String image, HashMap<Integer, Boolean> weapons, HashMap<String, Integer> ammo, float velocity, float vision, EntityChangeListener listener) {
        super(id, x, y, width, height, maxHealth, team, image, weapons, ammo, velocity, vision, listener);
    }

    public float getRespawnTimer() {
        return respawnTimer;
    }

    public void reduceRespawnTimer(float amount){
        respawnTimer -= amount;
    }

    public void setRespawnTimer(float respawnTimer) {
        this.respawnTimer = respawnTimer;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {

        this.lives = lives;
    }
}

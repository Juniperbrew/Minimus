package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class NetworkEntity {
    public int id;
    public float x;
    public float y;
    public float width;
    public float height;
    public int health;
    public int maxHealth;
    public int rotation;
    public int slot1Weapon = 0;
    public int slot2Weapon = 1;
    public int team;
    public String image;

    public NetworkEntity(){
        this(-1,-1,-1,-1);
    }

    public NetworkEntity(NetworkEntity e){
        id = e.id;
        x = e.x;
        y = e.y;
        width = e.width;
        height = e.height;
        health = e.health;
        maxHealth = e.maxHealth;
        rotation = e.rotation;
        slot1Weapon = e.slot1Weapon;
        slot2Weapon = e.slot2Weapon;
        team = e.team;
        image = e.image;
    }

    public NetworkEntity(int id, float x, float y, float width, float height, int health, int maxHealth, int team, String image){
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.health = health;
        this.maxHealth = maxHealth;
        this.team = team;
        this.image = image;
    }

    public NetworkEntity(int id, float x, float y, int team) {
        this(id,x,y,team,100);
    }

    public NetworkEntity(int id, float x, float y, int team, int health) {
        this.id = id;
        this.x = x;
        this.y = y;
        width = 50;
        height = 50;
        this.health = health;
        maxHealth = health;
        rotation = -90;
        this.team = team;
    }


    @Override
    public String toString() {
        return "NetworkEntity{" +
                "id=" + id +
                ", x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                ", health=" + health +
                ", maxHealth=" + maxHealth +
                ", rotation=" + rotation +
                ", slot1Weapon=" + slot1Weapon +
                ", slot2Weapon=" + slot2Weapon +
                ", team=" + team +
                ", image='" + image + '\'' +
                '}';
    }
}

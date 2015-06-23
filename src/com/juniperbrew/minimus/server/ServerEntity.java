package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class ServerEntity {
    public int id;
    private float x;
    private float y;
    public int width;
    public int height;
    private int health;
    public int maxHealth;
    private Enums.Heading heading;
    EntityChangeListener listener;

    public ServerEntity(ServerEntity e){
        id = e.id;
        x = e.x;
        y = e.y;
        width = e.width;
        height = e.height;
        health = e.health;
        maxHealth = e.maxHealth;
        heading = e.heading;
        this.listener = e.listener;
    }

    public ServerEntity(int id, float x, float y, EntityChangeListener listener) {
        this.id = id;
        this.x = x;
        this.y = y;
        width = 50;
        height = 50;
        health = 100;
        maxHealth = 100;
        heading = Enums.Heading.SOUTH;
        this.listener = listener;
    }

    public Entity getEntity(){
        return new Entity(id,x,y,width,height,health,maxHealth,heading);
    }

    public float getX(){
        return x;
    }

    public float getY(){
        return y;
    }

    public int getHealth(){
        return health;
    }

    public void restoreMaxHealth(){
        health = maxHealth;
    }

    public void setHealth(int health){
        this.health = health;
    }

    public Enums.Heading getHeading(){
        return heading;
    }

    public void setHeading(Enums.Heading heading){
        this.heading = heading;
        listener.headingChanged(id);
    }
    public void moveTo(float newX, float newY){
        x = newX;
        y = newY;
        listener.positionChanged(id);
    }

    public void reduceHealth(int healthReduction){
        health -= healthReduction;
        listener.healthChanged(id);
    }

    public Rectangle2D.Double getBounds(){
        return new Rectangle2D.Double(x,y,width,height);
    }
}

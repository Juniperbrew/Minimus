package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class ServerEntity extends Entity {

    EntityChangeListener listener;

    public ServerEntity(int id, float x, float y, EntityChangeListener listener) {
        super(id,x,y);
        this.listener = listener;
    }

    public Entity getNetworkEntity(){
        Entity e = new Entity(id,getX(),getY(),width,height,getHealth(),maxHealth,getHeading());
        return e;
    }

    public void restoreMaxHealth(){
        super.setHealth(maxHealth);
    }

    public void setHealth(int health){
        super.setHealth(health);
        listener.healthChanged(id);
    }

    public void setHeading(Enums.Heading heading){
        super.setHeading(heading);
        listener.headingChanged(id);
    }
    public void moveTo(float newX, float newY){
        super.moveTo(newX, newY);
        listener.positionChanged(id);
    }

    public void reduceHealth(int healthReduction){
        super.setHealth(getHealth() - healthReduction);
        listener.healthChanged(id);
    }
}

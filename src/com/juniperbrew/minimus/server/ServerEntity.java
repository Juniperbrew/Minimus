package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.Tools;

import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class ServerEntity extends Entity {

    EntityChangeListener listener;
    long lastDamageTaken;
    boolean invulnerable;

    public ServerEntity(int id, float x, float y, int team, EntityChangeListener listener) {
        super(id,x,y, team);
        this.listener = listener;
    }

    public Entity getNetworkEntity(){
        Entity e = new Entity(id,getX(),getY(),width,height,getHealth(),maxHealth,getHeading(), getTeam());
        return e;
    }

    public void restoreMaxHealth(){
        super.setHealth(maxHealth);
        listener.healthChanged(id);
    }

    public void setHealth(int health){
        super.setHealth(health);
        listener.healthChanged(id);
    }

    public void setRotation(int degrees){
        super.setRotation(degrees);
        listener.rotationChanged(id);
    }

    public void setHeading(Enums.Heading heading){
        super.setHeading(heading);
        listener.headingChanged(id);
        System.out.println(id+": heading updated");
    }
    public void moveTo(float newX, float newY){
        super.moveTo(newX, newY);
        listener.positionChanged(id);
    }

    public void reduceHealth(int healthReduction, int sourceID){
        if(!invulnerable){
            super.setHealth(getHealth() - healthReduction);
            listener.healthChanged(id);
            if(getHealth()<=0){
                listener.entityDied(id, sourceID);
            }
        }
    }

    public void addHealth(int healing){
        super.setHealth(getHealth() + healing);
        listener.healthChanged(id);
    }

    public void setTeam(int team) {
        super.setTeam(team);
        listener.teamChanged(id);
    }
}

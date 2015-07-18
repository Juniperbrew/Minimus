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

    public ServerEntity(int id, float x, float y, int team, EntityChangeListener listener) {
        super(id,x,y, team);
        this.listener = listener;
    }

    public Entity getNetworkEntity(){
        Entity e = new Entity(id,getX(),getY(),width,height,getHealth(),maxHealth,getHeading(), team);
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
    public boolean isInvulnerable(){
        if(System.nanoTime()-lastDamageTaken> Tools.secondsToNano(MinimusServer.conVars.getDouble("sv_invulnerability_timer"))){
            return false;
        }else{
            System.out.println(id + " is still invulnerable for " + (System.nanoTime()-lastDamageTaken));
            return true;
        }
    }

    public void contactDamage(int healthReduction, int sourceID){
        if(!isInvulnerable()) {
            lastDamageTaken = System.nanoTime();
            reduceHealth(healthReduction,sourceID);
        }
    }

    public void reduceHealth(int healthReduction, int sourceID){
        super.setHealth(getHealth() - healthReduction);
        listener.healthChanged(id);
        if(getHealth()<=0){
            listener.entityDied(id, sourceID);
        }
    }
}

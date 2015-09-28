package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.G;

import java.util.HashMap;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public abstract class ServerEntity extends Entity {

    EntityChangeListener listener;
    boolean invulnerable;
    float velocity;
    float chargeMeter;

    public ServerEntity(int id, float x, float y, float width, float height, int maxHealth, int team, String image, float velocity, EntityChangeListener listener){
        super(id, x, y, width, height, maxHealth, team, image);
        this.listener = listener;
        this.velocity = velocity;
    }

    public void restoreMaxHealth() {
        setHealth(getMaxHealth());
    }

    public void setHealth(int health) {
        super.setHealth(health);
        listener.healthChanged(getID());
    }

    public void setMaxHealth(int health){
        super.setMaxHealth(health);
        listener.maxHealthChanged(getID());
    }

    public void setRotation(float degrees) {
        super.setRotation(degrees);
        listener.rotationChanged(getID());
    }

    public void move(double deltaX, double deltaY) {
        super.move(deltaX, deltaY);
        listener.positionChanged(getID());
    }

    public void moveTo(double x, double y){
        super.moveTo(x, y);
        listener.positionChanged(getID());
    }

    public void reduceHealth(int healthReduction, int sourceID){
        if(!invulnerable&&getHealth()>0){
            super.setHealth(getHealth() - healthReduction);
            listener.healthChanged(getID());
            if(getHealth()<=0){
                listener.entityDied(getID(), sourceID);
            }
        }
    }

    public void addHealth(int healing) {
        super.setHealth(getHealth() + healing);
        listener.healthChanged(getID());
    }

    public void setTeam(int team) {
        super.setTeam(team);
        listener.teamChanged(getID());
    }

    public abstract void chargeWeapon(int weaponID, float delta);

    public abstract boolean canShoot(int weaponID);

    public abstract void updateCooldowns(double delta);

    public abstract void setWeaponCooldown(int weaponID, double cooldown);

    public abstract void hasfired(int weaponID);

    public abstract boolean isChargingWeapon(int weaponID);
}

package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.GlobalVars;
import com.juniperbrew.minimus.NetworkEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public class ServerEntity extends Entity {

    EntityChangeListener listener;
    long lastContactDamageTaken;
    boolean invulnerable;
    HashMap<Integer,Double> weaponCooldowns = new HashMap<>();
    HashMap<Integer,Boolean> weapons = new HashMap<>();
    HashMap<String,Integer> ammo = new HashMap<>();
    public float chargeMeter;
    public int chargeWeapon;
    float velocity;

    public ServerEntity(int id, float x, float y, float width, float height, int maxHealth, int team, String image, HashMap<Integer,Boolean> weapons, HashMap<String,Integer> ammo, float velocity, EntityChangeListener listener){
        super(id, x, y, width, height, maxHealth, team, image);
        this.listener = listener;
        this.weapons = weapons;
        this.ammo = ammo;
        this.velocity = velocity;
        for(int weapon : weapons.keySet()){
            weaponCooldowns.put(weapon,-1d);
        }
    }

    public void restoreMaxHealth(){
        setHealth(getMaxHealth());
    }

    public void setHealth(int health){
        super.setHealth(health);
        listener.healthChanged(getID());
    }

    public void setRotation(int degrees){
        super.setRotation(degrees);
        listener.rotationChanged(getID());
    }

    public void move(double deltaX, double deltaY){
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

    public void addHealth(int healing){
        super.setHealth(getHealth() + healing);
        listener.healthChanged(getID());
    }

    public void setTeam(int team) {
        super.setTeam(team);
        listener.teamChanged(getID());
    }

    public void updateCooldowns(double delta){
        for(int weaponslot : weaponCooldowns.keySet()){
            double cd = weaponCooldowns.get(weaponslot);
            cd -= (delta);
            weaponCooldowns.put(weaponslot,cd);
        }
    }

    public void addAmmo(String ammoType, int amount){
        int weaponAmmo = ammo.get(ammoType);
        weaponAmmo += amount;
        ammo.put(ammoType,weaponAmmo);
    }

    public void addAmmoToWeapon(int weapon, int amount){
        String ammoType = GlobalVars.weaponList.get(weapon).ammo;
        addAmmo(ammoType, amount);
    }

    public void setWeapon(int weapon, boolean state){
        weapons.put(weapon,state);
    }

    public boolean hasWeapon(int weapon){
        return weapons.get(weapon);
    }

    public boolean weaponCooldown(int weapon){
        return weaponCooldowns.get(weapon)>0;
    }

    public void setWeaponCooldown(int weapon, double cooldown){
        weaponCooldowns.put(weapon,cooldown);
    }

    public boolean hasAmmo(String ammoType){
        if(ammo.containsKey(ammoType)){
            return ammo.get(ammoType)>0;
        }else{
            return true;
        }
    }
}

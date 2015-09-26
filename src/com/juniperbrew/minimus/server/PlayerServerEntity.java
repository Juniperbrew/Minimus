package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.Weapon;

import java.util.HashMap;

/**
 * Created by Juniperbrew on 24.9.2015.
 */
public class PlayerServerEntity extends ServerEntity {

    int lives;
    float respawnTimer;
    int gold;
    HashMap<Integer,Double> weaponCooldowns = new HashMap<>();
    HashMap<Integer,Boolean> weapons = new HashMap<>();
    HashMap<String,Integer> ammo = new HashMap<>();
    public int chargeWeapon;

    public PlayerServerEntity(int id, float x, float y, float width, float height, int maxHealth, int team, String image, HashMap<Integer, Boolean> weapons, HashMap<String, Integer> ammo, float velocity, float vision, EntityChangeListener listener) {
        super(id, x, y, width, height, maxHealth, team, image, velocity, listener);
        this.weapons = weapons;
        this.ammo = ammo;
        for(int weapon : weapons.keySet()){
            weaponCooldowns.put(weapon,-1d);
        }
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public void addGold(int gold){
        this.gold += gold;
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

    public boolean hasWeapon(int weaponID) {
        return weapons.get(weaponID);
    }

    public boolean hasAmmo(String ammoType) {
        if(ammo.containsKey(ammoType)){
            return ammo.get(ammoType)>0;
        }else{
            return true;
        }
    }

    public boolean isWeaponOnCooldown(int weapon) {
        return weaponCooldowns.get(weapon)>0;
    }

    @Override
    public void setWeaponCooldown(int weapon, double cooldown) {
        weaponCooldowns.put(weapon,cooldown);
    }

    @Override
    public void hasfired(int weaponID) {
        Weapon weapon = G.weaponList.get(weaponID);
        setWeaponCooldown(weaponID, weapon.cooldown);
        if(weapon.ammo!=null){
            addAmmo(weapon.ammo, -1);
        }
    }

    @Override
    public boolean isChargingWeapon(int weaponID) {
        if(chargeMeter>0&&chargeWeapon==weaponID){
            return true;
        }else{
            return false;
        }
    }

    public void setSlot1Weapon(int weaponID){
        super.setSlot1Weapon(weaponID);
        listener.slot1WeaponChanged(getID());
    }

    public void setSlot2Weapon(int secondarySlot){
        super.setSlot2Weapon(secondarySlot);
        listener.slot2WeaponChanged(getID());
    }

    public void addAmmo(String ammoType, int amount){
        int weaponAmmo = ammo.get(ammoType);
        weaponAmmo += amount;
        ammo.put(ammoType, weaponAmmo);
    }

    public void addAmmoToWeapon(int weapon, int amount){
        String ammoType = G.weaponList.get(weapon).ammo;
        addAmmo(ammoType, amount);
    }

    public void setWeapon(int weapon, boolean state){
        weapons.put(weapon, state);
    }

    @Override
    public void chargeWeapon(int weaponID, float delta) {
        chargeWeapon = weaponID;
        chargeMeter += delta;
    }

    @Override
    public boolean canShoot(int weaponID) {
        if(hasWeapon(weaponID)&&hasAmmo(G.weaponList.get(weaponID).ammo)&&!isWeaponOnCooldown(weaponID)){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void updateCooldowns(double delta) {
        for(int weaponslot : weaponCooldowns.keySet()){
            double cd = weaponCooldowns.get(weaponslot);
            cd -= (delta);
            weaponCooldowns.put(weaponslot,cd);
        }
    }
}

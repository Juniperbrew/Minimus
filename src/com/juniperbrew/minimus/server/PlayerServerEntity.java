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
    int cash;
    HashMap<Integer,Double> weaponCooldowns = new HashMap<>();
    HashMap<Integer,Boolean> weapons = new HashMap<>();
    HashMap<String,Integer> ammo = new HashMap<>();
    public int chargeWeapon;
    Game.WorldChangeListener worldChangeListener;

    public PlayerServerEntity(int id, float x, float y, float width, float height, int maxHealth, int team, String image, HashMap<Integer, Boolean> weapons, HashMap<String, Integer> ammo, float velocity, float vision, EntityChangeListener listener, Game.WorldChangeListener worldChangeListener) {
        super(id, x, y, width, height, maxHealth, team, image, velocity, listener);
        this.weapons = weapons;
        this.ammo = ammo;
        for(int weapon : weapons.keySet()){
            weaponCooldowns.put(weapon,-1d);
        }
        this.worldChangeListener = worldChangeListener;
    }

    public int getCash() {
        return cash;
    }

    public void setCash(int cash) {
        this.cash = cash;
        worldChangeListener.playerCashChanged(getID(),this.cash);
    }

    public void changeCash(int cash){
        this.cash += cash;
        worldChangeListener.playerCashChanged(getID(),this.cash);
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

    public int getAmmo(String ammoType){
        if(ammo.containsKey(ammoType)){
            return ammo.get(ammoType);
        }else{
            return 0;
        }
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
            changeAmmo(weapon.ammo, -1);
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

    public void addAmmoToWeapon(int weapon, int amount){
        String ammoType = G.weaponList.get(weapon).ammo;
        changeAmmo(ammoType, amount);
    }

    public void changeAmmo(String ammoType, int amount){
        int weaponAmmo = ammo.get(ammoType);
        weaponAmmo += amount;
        ammo.put(ammoType, weaponAmmo);
        worldChangeListener.playerAmmoChanged(getID(), ammoType, ammo.get(ammoType));
    }

    public void setWeapon(int weaponID, boolean state){
        weapons.put(weaponID, state);
        worldChangeListener.playerWeaponChanged(getID(),weaponID, state);
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

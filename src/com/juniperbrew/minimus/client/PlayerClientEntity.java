package com.juniperbrew.minimus.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Juniperbrew on 4.9.2015.
 */
public class PlayerClientEntity extends ClientEntity{

    public Map<Integer,Double> cooldowns = new HashMap<>();
    Map<String,Integer> ammo;
    Map<Integer,Boolean> weapons;
    public int id;
    public float chargeMeter;
    public int chargeWeapon;
    public int gold;

    public PlayerClientEntity(int id, Map<Integer, Boolean> weapons, Map<String, Integer> ammo) {
        super();
        this.id = id;
        this.ammo = ammo;
        this.weapons = weapons;
        for(int weaponID : weapons.keySet()){
            cooldowns.put(weaponID,-1d);
        }
    }

    public void updateCooldowns(float delta){
        for(int weaponslot : cooldowns.keySet()){
            double cd = cooldowns.get(weaponslot);
            cd -= delta;
            cooldowns.put(weaponslot,cd);
        }
    }

    public boolean hasWeapon(int weapon){
        return weapons.get(weapon);
    }

    public boolean hasAmmo(String ammoType){
        if(ammo.containsKey(ammoType)){
            return ammo.get(ammoType)>0;
        }else{
            return true;
        }
    }
}

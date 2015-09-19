package com.juniperbrew.minimus.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Juniperbrew on 4.9.2015.
 */
public class PlayerClientEntity extends ClientEntity{

    public Map<Integer,Double> cooldowns = new HashMap<>();
    public Map<Integer,Integer> ammo;
    public Map<Integer,Boolean> weapons;
    public int id;
    public float chargeMeter;
    public int chargeWeapon;

    public PlayerClientEntity(int id, Map<Integer, Boolean> weapons, Map<Integer, Integer> ammo) {
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
}

package com.juniperbrew.minimus.server;

import com.juniperbrew.minimus.EnemyDefinition;
import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.Weapon;

/**
 * Created by Juniperbrew on 25.9.2015.
 */
public class NpcServerEntity extends ServerEntity{

    float vision;
    int bounty;
    int weapon;
    double weaponCooldown;

    public NpcServerEntity(int id, float x, float y, float width, float height, int team, EnemyDefinition def, EntityChangeListener listener) {
        super(id, x, y, width, height, def.health, team, def.image, def.velocity, listener);
        this.vision = def.vision;
        this.bounty = def.bounty;
        this.weapon = def.weapon;
    }

    @Override
    public void chargeWeapon(int weaponID, float delta) {
        if(weaponID!=weapon){
            return;
        }
        chargeMeter +=delta;
    }

    @Override
    public boolean canShoot(int weaponID) {
        if(weaponID==weapon&&weaponCooldown<=0){
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void updateCooldowns(double delta) {
        weaponCooldown -= delta;
    }

    @Override
    public void setWeaponCooldown(int weapon, double cooldown) {
        weaponCooldown = cooldown;
    }

    @Override
    public void hasfired(int weaponID) {
        Weapon weapon = G.weaponList.get(weaponID);
        setWeaponCooldown(weaponID, weapon.cooldown);
    }

    @Override
    public boolean isChargingWeapon(int weaponID) {
        if(chargeMeter>0&&weapon==weaponID){
            return true;
        }else{
            return false;
        }
    }
}

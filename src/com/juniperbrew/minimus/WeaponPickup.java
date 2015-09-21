package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 20.9.2015.
 */
public class WeaponPickup extends Powerup{
    public int weaponID;

    public WeaponPickup() {
    }

    public WeaponPickup(float x, float y, float width, float height, int weaponID){
        super(x,y,width,height);
        this.weaponID = weaponID;

    }

    @Override
    public String toString() {
        return "WeaponPickup{" +
                "weaponID=" + weaponID +
                ", weaponName=" + GlobalVars.weaponList.get(weaponID).name +
                '}';
    }
}

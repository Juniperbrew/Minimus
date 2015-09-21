package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 20.9.2015.
 */
public class AmmoPickup extends Powerup {
    public String ammoType;

    public AmmoPickup() {
    }

    public int value;
    public AmmoPickup(float x, float y, float width, float height, String ammoType, int value){
        super(x,y,width,height);
        this.ammoType = ammoType;
        this.value = value;
    }

    @Override
    public String toString() {
        return "AmmoPickup{" +
                "ammoType='" + ammoType + '\'' +
                ", value=" + value +
                '}';
    }
}

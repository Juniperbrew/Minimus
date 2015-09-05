package com.juniperbrew.minimus.client;

import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.NetworkEntity;

/**
 * Created by Juniperbrew on 4.9.2015.
 */
public class ClientEntity extends Entity {

    public float animationState;
    public double fireAnimationTimer;
    public boolean aimingWeapon;

    public ClientEntity(NetworkEntity e) {
        super(e);
    }

    public ClientEntity() {
        super();
    }

    public void setNetworkedState(NetworkEntity e){
        if(getX()==e.x&&getY()==e.y){
            animationState = 0;
        }
        super.setNetworkedState(e);
    }
}

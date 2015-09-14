package com.juniperbrew.minimus.client;

import com.badlogic.gdx.math.Vector2;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.NetworkEntity;

/**
 * Created by Juniperbrew on 4.9.2015.
 */
public class ClientEntity extends Entity {

    public float animationState;
    public double fireAnimationTimer;
    public boolean aimingWeapon;
    public float bleedTimer;

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

    public void update(float delta){
        fireAnimationTimer -= delta;
        bleedTimer -= delta;
        animationState += delta;
    }

    public void setPosition(Vector2 p){
        networkEntity.x = p.x;
        networkEntity.y = p.y;
    }

    public void setPosition(float x, float y){
        networkEntity.x = x;
        networkEntity.y = y;
    }
}

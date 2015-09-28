package com.juniperbrew.minimus.server;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public interface EntityChangeListener {

    public void positionChanged(int id);
    public void healthChanged(int id);
    public void maxHealthChanged(int id);
    public void rotationChanged(int id);
    public void teamChanged(int id);
    public void entityDied(int id, int sourceID);
    public void slot1WeaponChanged(int id);
    public void slot2WeaponChanged(int id);
}

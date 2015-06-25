package com.juniperbrew.minimus.server;

/**
 * Created by Juniperbrew on 22/06/15.
 */
public interface EntityChangeListener {

    public void positionChanged(int id);
    public void healthChanged(int id);
    public void headingChanged(int id);
}

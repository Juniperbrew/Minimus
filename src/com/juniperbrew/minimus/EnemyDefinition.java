package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 29.8.2015.
 */
public class EnemyDefinition {
    public int weapon;
    public String image;
    public int health;

    @Override
    public String toString() {
        return "EnemyDefinition{" +
                "weapon=" + weapon +
                ", image='" + image + '\'' +
                ", health=" + health +
                '}';
    }
}
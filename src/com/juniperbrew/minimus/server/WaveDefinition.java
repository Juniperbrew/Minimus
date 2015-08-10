package com.juniperbrew.minimus.server;

import java.util.ArrayList;

/**
 * Created by Juniperbrew on 26.7.2015.
 */
public class WaveDefinition {

    ArrayList<EnemyDefinition> enemies = new ArrayList<>();
    public int healthPackCount;
    String map;

    public void addEnemy(String aiType, int weapon, int count){
        enemies.add(new EnemyDefinition(aiType, weapon, count));
    }

    public class EnemyDefinition{
        public String aiType;
        public int weapon;
        public int count;

        public EnemyDefinition(String aiType, int weapon, int count) {
            this.aiType = aiType;
            this.weapon = weapon;
            this.count = count;
        }
    }
}

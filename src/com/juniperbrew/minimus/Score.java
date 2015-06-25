package com.juniperbrew.minimus;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class Score {

    HashMap<Integer,PlayerScore> score = new HashMap<>();
    ScoreChangeListener listener;

    public Score(ScoreChangeListener listener){
        this.listener = listener;
    }

    public HashMap<Integer,PlayerScore> getScore(){
        return score;
    }

    public ArrayList<Integer> getPlayers(){
        return new ArrayList<>(score.keySet());
    }

    public int getDeaths(int id){
        return score.get(id).deaths;
    }
    public int getNpcKills(int id){
        return score.get(id).npcKills;
    }
    public int getPlayerKills(int id){
        return score.get(id).playerKills;
    }


    public void addDeath(int id){
        score.get(id).deaths++;
        listener.scoreChanged();
    }
    public void addPlayerKill(int id){
        score.get(id).playerKills++;
        listener.scoreChanged();
    }
    public void addNpcKill(int id){
        score.get(id).npcKills++;
        listener.scoreChanged();
    }

    public void addPlayer(int id){
        score.put(id,new PlayerScore());
        listener.scoreChanged();
    }
    public void removePlayer(int id){
        score.remove(Integer.valueOf(id));
        listener.scoreChanged();
    }

    private class PlayerScore{
        int playerKills;
        int npcKills;
        int deaths;
    }
    public static abstract interface ScoreChangeListener{
        public void scoreChanged();
    }
}

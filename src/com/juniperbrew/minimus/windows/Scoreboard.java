package com.juniperbrew.minimus.windows;

import com.juniperbrew.minimus.Score;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class Scoreboard extends JFrame {

    Score score;
    JTextArea text;

    public Scoreboard(Score score){
        super("Scoreboard");
        this.score = score;
        setLayout(new MigLayout());
        text = new JTextArea();
        text.setEditable(false);
        add(text, "grow,push");
        updateScoreboard();
    }

    public void updateScoreboard(){
        StringBuilder builder = new StringBuilder();
        for(int id: score.getPlayers()){
            String kdRatio = String.format("%.2f",(float)score.getPlayerKills(id)/score.getDeaths(id));
            builder.append(id+"> Player kills:"+ score.getPlayerKills(id)+" NPC kills:"+score.getNpcKills(id)+" Deaths:"+score.getDeaths(id)+" KD ratio:"+kdRatio+"\n");
        }
        System.out.println(builder.toString());
        text.setText(builder.toString());
        pack();
    }
}

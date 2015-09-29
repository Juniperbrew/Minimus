package com.juniperbrew.minimus;

import java.util.ArrayList;

/**
 * Created by Juniperbrew on 28.9.2015.
 */
public class DialogueNode {

    String message;
    ArrayList<String> choices;

    public DialogueNode(String message) {
        this.message = message;
    }

    public void addChoice(String choice){
        choices.add(choice);
    }

    public ArrayList<String> getChoices(){
        return choices;
    }
}

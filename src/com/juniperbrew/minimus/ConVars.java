package com.juniperbrew.minimus;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConVars {

    private TreeMap<String,Double> vars = new TreeMap<>();

    public ConVars(){
        readVars();
    }

    public boolean has(String varName){
        return vars.containsKey(varName);
    }

    public String getVarDump(){
        StringBuilder list = new StringBuilder();
        for(String varName:vars.keySet()){
            list.append(varName + " = " + vars.get(varName)+"\n");
        }
        return list.toString();
    }

    public ArrayList<String> getVarList(){
        return new ArrayList<>(vars.keySet());
    }

    public void set(String varName, double varValue){
        vars.put(varName,varValue);
    }

    public void set(String varName, boolean varValue){
        if(varValue){
            vars.put(varName,1d);
        }else{
            vars.put(varName,0d);
        }
    }

    public double get(String varName){
        return vars.get(varName);
    }

    public int getInt(String varName){
        return vars.get(varName).intValue();
    }

    public boolean getBool(String varName){
        if(vars.get(varName).intValue() == 1){
            return true;
        }else{
            return false;
        }
    }

    public void toggleVar(String varName){
        boolean value = getBool(varName);
        set(varName,!value);
    }

    public void addToVar(String varName,double add){
        double value = get(varName);
        value += add;
        set(varName,value);
    }

    private void printVars(){
        for(String varName:vars.keySet()){
            System.out.println(varName + " = " + vars.get(varName));
        }
    }

    private void readVars(){
        try(BufferedReader reader = new BufferedReader(new FileReader("conVars.txt"))){
            String line;
            while((line = reader.readLine()) != null){
                String[] splits = line.split(" ");
                vars.put(splits[0], Double.valueOf(splits[1]));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        printVars();
    }
}

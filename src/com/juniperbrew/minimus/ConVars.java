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

    private TreeMap<String,String> vars = new TreeMap<>();

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
    public ArrayList<String> getVarListWithValues(){
        ArrayList<String> varListWithValues = new ArrayList<>();
        for(String varName : vars.keySet()){
            varListWithValues.add(varName+" "+vars.get(varName));
        }
        return varListWithValues;
    }

    public void set(String varName, String varValue){
        vars.put(varName,varValue);
    }

    public void set(String varName, double varValue){
        vars.put(varName,String.valueOf(varValue));
    }

    public void set(String varName, boolean varValue){
        if(varValue){
            vars.put(varName,"1");
        }else{
            vars.put(varName,"0");
        }
    }

    public String get(String varName){
        return vars.get(varName);
    }

    public double getDouble(String varName){
        return Double.valueOf(vars.get(varName));
    }

    public float getFloat(String varName){
        return Float.valueOf(vars.get(varName));
    }

    public int getInt(String varName){
        return (int) getDouble(varName);
    }

    public boolean getBool(String varName){
        if(getInt(varName) == 1){
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
        double value = getDouble(varName);
        value += add;
        set(varName,value);
    }

    private void printVars(){
        for(String varName:vars.keySet()){
            System.out.println(varName + " = " + vars.get(varName));
        }
    }

    private void readVars(){
        try(BufferedReader reader = new BufferedReader(new FileReader("resources\\conVars.txt"))){
            String line;
            while((line = reader.readLine()) != null){
                String[] splits = line.split(" ");
                vars.put(splits[0], splits[1]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        printVars();
    }
}

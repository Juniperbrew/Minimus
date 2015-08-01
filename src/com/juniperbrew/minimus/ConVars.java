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
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConVars {

    private static ConcurrentSkipListMap<String,String> vars = new ConcurrentSkipListMap<>();
    private static ArrayList<ConVarChangeListener> listeners = new ArrayList<>();

    static{
        readVars();
    }

    public static void addListener(ConVarChangeListener listener){
        listeners.add(listener);
    }

    public static boolean has(String varName){
        return vars.containsKey(varName);
    }

    public static String getVarDump(){
        StringBuilder list = new StringBuilder();
        for(String varName:vars.keySet()){
            list.append(varName + " = " + vars.get(varName)+"\n");
        }
        return list.toString();
    }

    public static ArrayList<String> getVarList(){
        return new ArrayList<>(vars.keySet());
    }
    public static ArrayList<String> getVarListWithValues(){
        ArrayList<String> varListWithValues = new ArrayList<>();
        for(String varName : vars.keySet()){
            varListWithValues.add(varName+" "+vars.get(varName));
        }
        return varListWithValues;
    }

    public static void set(String varName, String varValue){
        vars.put(varName,varValue);
        notifyConVarChanged(varName, varValue);
    }

    private static void notifyConVarChanged(String varName,String varValue){
        for(ConVarChangeListener listener:listeners){
            listener.conVarChanged(varName, varValue);
        }
    }

    public static void set(String varName, double varValue){
        set(varName,String.valueOf(varValue));
    }

    public static void set(String varName, boolean varValue) {
        if(varValue){
            set(varName,"1");
        }else{
            set(varName,"0");
        }
    }

    public static String get(String varName){
        return vars.get(varName);
    }

    public static double getDouble(String varName){
        return Double.valueOf(vars.get(varName));
    }

    public static float getFloat(String varName){
        return Float.valueOf(vars.get(varName));
    }

    public static int getInt(String varName){
        return (int) getDouble(varName);
    }

    public static boolean getBool(String varName){
        if(getInt(varName) == 1){
            return true;
        }else{
            return false;
        }
    }

    public static void toggleVar(String varName){
        boolean value = getBool(varName);
        set(varName,!value);
    }

    public static void addToVar(String varName,double add){
        double value = getDouble(varName);
        value += add;
        set(varName,value);
    }

    public static void printVars(){
        for(String varName:vars.keySet()){
            System.out.println(varName + " = " + vars.get(varName));
        }
    }

    private static void readVars(){
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
    }

    public interface ConVarChangeListener{
        public void conVarChanged(String varName, String varValue);
    }
}

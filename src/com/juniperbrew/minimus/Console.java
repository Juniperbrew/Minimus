package com.juniperbrew.minimus;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 07/06/16.
 */
public class Console {

    ConsoleListener listener;
    public Commands commands;

    public ArrayList<String> commandHistory = new ArrayList<>();
    public int historyIndex = 0;
    final public int maxHistory = 10;

    public Console(Commands commands){
        this(commands,null);
    }

    public Console(Commands commands, ConsoleListener listener){
        this.commands = commands;
        this.listener = listener;
        runAutoexec();
    }

    public void log(String message){
        addLine(message);
    }

    private void addLine(String line){
        if(listener!=null){
            listener.log(line);
        }else{
            System.out.println(line);
        }
    }

    public void parseCommand(String command){
        String[] splits = command.split(" ");
        if(ConVars.has(splits[0])){
            if(splits.length>1){
                ConVars.set(splits[0], splits[1]);
                addLine("set "+ splits[0] + " = " + splits[1]);
            }else{
                addLine(splits[0]+ " = " + ConVars.get(splits[0]));
            }
            return;
        }
        if(splits[0].equals("dumphistory")){
            addLine(dumpCommandHistory());
            return;
        }
        if(splits[0].equals("cvarlist")){
            addLine(ConVars.getVarDump());
            return;
        }
        if(splits[0].equals("help")){
            showHelp();
            return;
        }
        if(splits[0].equals("send")){
            if(commands instanceof ServerCommands){
                String sendCommand = command.split(" ", 2)[1];
                addLine("Sending command ["+sendCommand+"] to all clients.");
                ((ServerCommands) commands).sendCommand(sendCommand);
                parseCommand(sendCommand);
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("start")){
            if(commands instanceof ServerCommands){
                ((ServerCommands) commands).startWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("stop")){
            if(commands instanceof ServerCommands){
                ((ServerCommands) commands).stopWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("team")){
            if(commands instanceof ClientCommands){
                ((ClientCommands) commands).changeTeam(Integer.parseInt(splits[1]));
            }else{
                addLine("The " + splits[0] + " command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("ammo")){
            if(commands instanceof ServerCommands){
                ((ServerCommands) commands).addAmmo(Integer.parseInt(splits[1]), splits[2], Integer.parseInt(splits[3]));
            }else{
                addLine("The " + splits[0] + " command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("idkfa")){
            if(commands instanceof ServerCommands){
                if(splits.length==1){
                    ((ServerCommands) commands).giveEveryoneAllWeapons();
                    ((ServerCommands) commands).fillEveryonesAmmo();
                }else{
                    ((ServerCommands) commands).fillAmmo(Integer.parseInt(splits[1]));
                    ((ServerCommands) commands).giveAllWeapons(Integer.parseInt(splits[1]));
                }
            }else{
                addLine("The " + splits[0] + " command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("respawn")){
            if(commands instanceof ClientCommands){
                ((ClientCommands) commands).requestRespawn();
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("reset")){
            if(commands instanceof ServerCommands){
                ((ServerCommands) commands).resetWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("weapon")){
            if(commands instanceof ClientCommands){
                ((ClientCommands) commands).selectWeapon(Integer.parseInt(splits[1]));
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("buy")){
            if(commands instanceof ClientCommands){
                if(splits.length>2){
                    ((ClientCommands) commands).buyItem(Integer.parseInt(splits[1]), Integer.parseInt(splits[2]));
                }else{
                    ((ClientCommands) commands).buyItem(Integer.parseInt(splits[1]), 1);
                }
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("sell")){
            if(commands instanceof ClientCommands){
                if(splits.length>2){
                    ((ClientCommands) commands).sellItem(Integer.parseInt(splits[1]), Integer.parseInt(splits[2]));
                }else{
                    ((ClientCommands) commands).sellItem(Integer.parseInt(splits[1]), 1);
                }
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("shop")){
            for(int id : G.shoplist.keySet()){
                addLine(id+":"+G.shoplist.get(id));
            }
            return;
        }
        if(splits[0].equals("verkkokauppa")){
            if(commands instanceof ClientCommands){
                ((ClientCommands) commands).openShop();
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        addLine(command);
    }

    public void giveCommand(String command){
        addLine("External command:"+ command);
        parseCommand(command);
    }

    public int getHistoryIndex(){
        int a = historyIndex;
        int b = commandHistory.size();
        return (a % b + b) % b;
    }

    private String dumpCommandHistory(){
        StringBuilder b = new StringBuilder();
        b.append("#Command history#\n");
        b.append("Index:" + historyIndex+"\n");
        if(!commandHistory.isEmpty())b.append("Mod index:" + getHistoryIndex() + "\n");
        for (int i = 0; i < commandHistory.size(); i++) {
            String s = commandHistory.get(i);
            b.append(i + ":");
            if(i==getHistoryIndex()){
                b.append(">");
            }else{
                b.append(" ");
            }
            b.append(s+"\n");
        }
        return b.toString();
    }

    public void showHelp(){
        File file = null;
        if(commands instanceof ServerCommands){
            file = new File("resources"+File.separator+"serverHelp.txt");
        }else{
            file = new File("resources"+File.separator+"clientHelp.txt");
        }
        try(BufferedReader reader = new BufferedReader(new FileReader(file))){
            String line;
            while((line = reader.readLine())!=null){
                addLine(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void storeCommandHistory(String command){
        commandHistory.add(command);
        if(commandHistory.size()>maxHistory){
            commandHistory.remove(0);
        }
    }

    public void runAutoexec(){
        addLine("Running autoexec..");
        String autoexecPath = Tools.getUserDataDirectory()+"autoexec.txt";
        File file = new File(autoexecPath);
        if(!file.exists()){
            addLine("autoexec.txt doesn't exist, creating default autoexec.txt");
            try(PrintWriter writer = new PrintWriter(new FileWriter(file))){
                for(String line : ConVars.getVarListWithValues()){
                    writer.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            runConsoleScript(autoexecPath);
        }
    }

    public void runConsoleScript(String filePath){
        File file = new File(filePath);
        if(file.exists()){
            try(BufferedReader reader = new BufferedReader(new FileReader(file))){
                String line;
                while((line = reader.readLine()) != null){
                    if(!line.isEmpty()&&line.charAt(0)!='#'){
                        parseCommand(line);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            addLine("Can't find script " + filePath);
        }
        addLine("");
    }

    public interface ConsoleListener{
        void log(String message);
    }

    public interface Commands{

    }

    public interface ServerCommands extends Commands{
        public void sendCommand(String command);
        public void startWaves();
        public void stopWaves();
        public void resetWaves();
        public void addAmmo(int id, String ammoType, int amount);
        public void giveEveryoneAllWeapons();
        public void fillEveryonesAmmo();
        public void fillAmmo(int playerID);
        public void giveAllWeapons(int playerID);
    }

    public interface ClientCommands extends Commands{
        public void changeTeam(int team);
        public void requestRespawn();
        public void selectWeapon(int weapon);
        public void buyItem(int id, int amount);
        public void sellItem(int id, int amount);
        public void openShop();
    }
}

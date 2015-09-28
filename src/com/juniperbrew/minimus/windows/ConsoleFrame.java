package com.juniperbrew.minimus.windows;

import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.client.MinimusClient;
import com.juniperbrew.minimus.server.MinimusServer;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConsoleFrame extends JFrame {

    JTextArea textArea = new JTextArea();
    ArrayList<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;
    int maxHistory = 10;

    MinimusServer minimusServer;
    MinimusClient minimusClient;
    private static final String COMMIT_ACTION = "commit";

    public ConsoleFrame(MinimusServer minimusServer){
        this();
        setTitle("Console[SERVER]");
        this.minimusServer = minimusServer;
    }

    public ConsoleFrame(MinimusClient minimusClient){
        this();
        setTitle("Console[CLIENT]");
        this.minimusClient = minimusClient;
    }

    private ConsoleFrame(){
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLayout(new MigLayout("wrap"));
        setPreferredSize(new Dimension(800, 400));


        textArea.setEditable(false);
        DefaultCaret caret = (DefaultCaret)textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, "grow,push");
        final JTextField textField = new JTextField();

        Autocomplete autoComplete = new Autocomplete(textField, ConVars.getVarList());
        textField.getDocument().addDocumentListener(autoComplete);

        // Without this, cursor always leaves text field
        textField.setFocusTraversalKeysEnabled(false);

        // Maps the tab key to the commit action, which finishes the autocomplete
    // when given a suggestion
        textField.getInputMap().put(KeyStroke.getKeyStroke("TAB"), COMMIT_ACTION);
        textField.getActionMap().put(COMMIT_ACTION, autoComplete.new CommitAction());

        textField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(!textField.getText().isEmpty()){
                    parseCommand(textField.getText());
                    storeCommandHistory(textField.getText());
                    textField.setText("");
                }
            }
        });
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (!commandHistory.isEmpty()) {
                        historyIndex++;
                        textField.setText(commandHistory.get(getHistoryIndex()));
                    }
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!commandHistory.isEmpty()) {
                        historyIndex--;
                        textField.setText(commandHistory.get(getHistoryIndex()));
                    }
                }
            }
        });
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                historyIndex = 0;
            }
        });
        add(textField, "growx,pushx");
        pack();
        runAutoexec();
    }

    private void storeCommandHistory(String command){
        commandHistory.add(command);
        if(commandHistory.size()>maxHistory){
            commandHistory.remove(0);
        }
    }

    private int getHistoryIndex(){
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

    public void giveCommand(String command){
        addLine("External command:"+ command);
        parseCommand(command);
    }

    public void addLine(String line){
        textArea.append(line + "\n");
    }

    public void runAutoexec(){
        addLine("Running autoexec..");
        File file = new File(Tools.getUserDataDirectory()+"autoexec.txt");
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
            addLine("autoexec.txt doesn't exist, creating default autoexec.txt");
            try(PrintWriter writer = new PrintWriter(new FileWriter(file))){
                for(String line : ConVars.getVarListWithValues()){
                    writer.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        addLine(ConVars.getVarDump());
    }

    private void parseCommand(String command){
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
            if(minimusServer!=null){
                String sendCommand = command.split(" ", 2)[1];
                addLine("Sending command ["+sendCommand+"] to all clients.");
                minimusServer.sendCommand(sendCommand);
                parseCommand(sendCommand);
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("start")){
            if(minimusServer!=null){
                minimusServer.startWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("stop")){
            if(minimusServer!=null){
                minimusServer.stopWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("team")){
            if(minimusClient!=null){
                minimusClient.changeTeam(Integer.parseInt(splits[1]));
            }else{
                addLine("The " + splits[0] + " command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("ammo")){
            if(minimusServer!=null){
                minimusServer.addAmmo(Integer.parseInt(splits[1]), splits[2], Integer.parseInt(splits[3]));
            }else{
                addLine("The " + splits[0] + " command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("idkfa")){
            if(minimusServer!=null){
                if(splits.length==1){
                    minimusServer.giveEveryoneAllWeapons();
                    minimusServer.fillEveryonesAmmo();
                }else{
                    minimusServer.fillAmmo(Integer.parseInt(splits[1]));
                    minimusServer.giveAllWeapons(Integer.parseInt(splits[1]));
                }
            }else{
                addLine("The " + splits[0] + " command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("respawn")){
            if(minimusClient!=null){
                minimusClient.requestRespawn();
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("reset")){
            if(minimusServer!=null){
                minimusServer.resetWaves();
            }else{
                addLine("The "+splits[0]+" command can only be used on server");
            }
            return;
        }
        if(splits[0].equals("weapon")){
            if(minimusClient!=null){
                minimusClient.selectWeapon(Integer.parseInt(splits[1]));
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("buy")){
            if(minimusClient!=null){
                if(splits.length>2){
                    minimusClient.buyItem(Integer.parseInt(splits[1]),Integer.parseInt(splits[2]));
                }else{
                    minimusClient.buyItem(Integer.parseInt(splits[1]),1);
                }
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        if(splits[0].equals("sell")){
            if(minimusClient!=null){
                if(splits.length>2){
                    minimusClient.sellItem(Integer.parseInt(splits[1]), Integer.parseInt(splits[2]));
                }else{
                    minimusClient.sellItem(Integer.parseInt(splits[1]), 1);
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
            if(minimusClient!=null){
                minimusClient.openShop();
            }else{
                addLine("The "+splits[0]+" command can only be used on client");
            }
            return;
        }
        addLine(command);
    }

    public void showHelp(){
        File file = null;
        if(minimusServer!=null){
            file = new File("resources\\serverHelp.txt");
        }else{
            file = new File("resources\\clientHelp.txt");
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
}

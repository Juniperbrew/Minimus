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
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConsoleFrame extends JFrame {

    JTextArea textArea = new JTextArea();
    ConVars conVars;
    ArrayList<String> commandHistory = new ArrayList<>();
    int historyIndex = 0;
    int maxHistory = 20;
    final String HELP = "Commands:\n" +
                            "help:\t display this message\n" +
                            "cvarlist:\t list all vars\n" +
                            "send:\t send command to all clients";

    MinimusServer minimusServer;

    public ConsoleFrame(ConVars conVars, MinimusServer minimusServer){
        this(conVars);
        setTitle("Console[SERVER]");
        this.minimusServer = minimusServer;
    }

    private void storeCommandHistory(String command){
        commandHistory.add(command);
        if(commandHistory.size()>maxHistory){
            commandHistory.remove(0);
        }
    }

    public ConsoleFrame(ConVars conVars){
        super("Console");
        this.conVars = conVars;
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLayout(new MigLayout("wrap"));
        setPreferredSize(new Dimension(800, 400));

        textArea.setEditable(false);
        DefaultCaret caret = (DefaultCaret)textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, "grow,push");
        final JTextField textField = new JTextField();
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
                        textField.setText(commandHistory.get(historyIndex % commandHistory.size()));
                        historyIndex++;
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
    }

    public void giveCommand(String command){
        addLine("External command:"+ command);
        parseCommand(command);
    }

    public void addLine(String line){
        textArea.append(line+"\n");
    }

    private void parseCommand(String command){
        String[] splits = command.split(" ");
        if(conVars.has(splits[0])){
            if(splits.length>1){
                try {
                    conVars.set(splits[0], Float.valueOf(splits[1]));
                    addLine("set "+ splits[0] + " = " + splits[1]);
                }catch(NumberFormatException e){
                    addLine("ERROR: "+ splits[1] + " is not a number");
                }
            }else{
                addLine(splits[0]+ " = " + conVars.get(splits[0]));
            }
            return;
        }
        if(splits[0].equals("cvarlist")){
            addLine(conVars.getVarList());
            return;
        }
        if(splits[0].equals("help")){
            addLine(HELP);
            return;
        }
        if(splits[0].equals("send")){
            if(minimusServer!=null){
                String sendCommand = command.split(" ", 2)[1];
                addLine("Sending command ["+sendCommand+"] to all clients.");
                minimusServer.sendCommand(sendCommand);
            }else{
                addLine("The send command can only be used on server");
            }
            return;
        }
        addLine(command);
    }

    public static void main(String[] args) {
        new ConsoleFrame(new ConVars()).setVisible(true);
    }
}

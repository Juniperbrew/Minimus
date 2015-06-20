import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConsoleFrame extends JFrame {

    JTextArea textArea = new JTextArea();
    ConVars conVars;
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

    public ConsoleFrame(ConVars conVars){
        super("Console");
        this.conVars = conVars;
        setLayout(new MigLayout("wrap"));
        setPreferredSize(new Dimension(800, 400));
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        add(scrollPane, "grow,push");
        final JTextField textField = new JTextField();
        textField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(!textField.getText().isEmpty()){
                    parseCommand(textField.getText());
                    textField.setText("");
                }
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
}

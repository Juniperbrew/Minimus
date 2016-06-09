package com.juniperbrew.minimus.windows;

import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Console;
import com.juniperbrew.minimus.G;
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

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class ConsoleFrame extends JFrame implements Console.ConsoleListener{

    JTextArea textArea = new JTextArea();
    Console console;

    private static final String COMMIT_ACTION = "commit";

    public ConsoleFrame(Console.Commands commands){

        console = new Console(commands, this);
        G.console = console;
        if(console.commands instanceof Console.ServerCommands){
            setTitle("Console[SERVER]");
        }else if(console.commands instanceof Console.ClientCommands){
            setTitle("Console[CLIENT]");
        }

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
                if (!textField.getText().isEmpty()) {
                    console.parseCommand(textField.getText());
                    console.storeCommandHistory(textField.getText());
                    textField.setText("");
                }
            }
        });
        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (!console.commandHistory.isEmpty()) {
                        console.historyIndex++;
                        textField.setText(console.commandHistory.get(console.getHistoryIndex()));
                    }
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (!console.commandHistory.isEmpty()) {
                        console.historyIndex--;
                        textField.setText(console.commandHistory.get(console.getHistoryIndex()));
                    }
                }
            }
        });
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                super.focusLost(e);
                console.historyIndex = 0;
            }
        });
        add(textField, "growx,pushx");
        pack();
    }

    @Override
    public void log(String message) {
        textArea.append(message + "\n");
    }
}

package com.juniperbrew.minimus.windows;

import com.juniperbrew.minimus.ErrorLogSender;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.client.ClientLauncher;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class ServerSelector extends JFrame implements ErrorLogSender.ErrorLogSenderListener{

    final String SERVER_LIST = "serverlist.txt";
    JComboBox dropDownMenu;
    ClientLauncher launcher;
    String[] serverlist;
    JButton sendErrorsButton;
    ErrorLogSender errorLogSender;

    public ServerSelector(final ClientLauncher launcher) throws IOException{
        super("Select server");
        this.launcher = launcher;
        errorLogSender = new ErrorLogSender(this);
        setLayout(new MigLayout());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosed(e);
                dispose();
                launcher.exit();
            }
        });
        serverlist = getServers();
        dropDownMenu = new JComboBox(serverlist);
        dropDownMenu.setEditable(true);
        dropDownMenu.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e){
                if(e.getActionCommand().equals("comboBoxEdited")){
                    serverSelected();
                }
            }
        });
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                serverSelected();
            }
        });
        sendErrorsButton = new JButton("Send error logs");
        sendErrorsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String ip = dropDownMenu.getSelectedItem().toString();
                errorLogSender.sendErrorLogs(ip);
            }
        });
        if(ErrorLogSender.getErrorLogs().isEmpty()){
            sendErrorsButton.setEnabled(false);
        }
        add(dropDownMenu);
        add(connectButton);
        add(sendErrorsButton);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void serverSelected(){
        String ip = dropDownMenu.getSelectedItem().toString();
        boolean connected = launcher.connect(ip);
        dispose();
        if(connected) {
            if (!Arrays.asList(serverlist).contains(ip)){
                addServer(ip);
            }
        }else{
            System.exit(0);
        }
    }

    private void addServer(String server){
        try(PrintWriter out = new PrintWriter(new FileWriter(Tools.getUserDataDirectory()+SERVER_LIST, true))) {
            out.println(server);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] getServers() throws IOException {
        ArrayList<String> serverList = new ArrayList<>();
        File file = new File(Tools.getUserDataDirectory()+SERVER_LIST);
        file.createNewFile();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while((line = reader.readLine())!=null){
            serverList.add(line);
        }

        return serverList.toArray(new String[serverList.size()]);
    }

    @Override
    public void allFilesReceived() {
        sendErrorsButton.setEnabled(true);
    }

    @Override
    public void transferFailed() {

    }
}

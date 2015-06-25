package com.juniperbrew.minimus.windows;

import com.juniperbrew.minimus.client.ClientLauncher;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class ServerSelector extends JFrame {

    final String SERVER_LIST = "serverlist.txt";

    public ServerSelector(final ClientLauncher launcher) throws IOException {
        super("Select server");
        setLayout(new MigLayout());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosed(e);
                dispose();
                launcher.exit();
            }
        });
        final JComboBox dropDownMenu = new JComboBox(getServers());
        dropDownMenu.setEditable(true);
        JButton connectButton = new JButton("Connect");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String ip = dropDownMenu.getSelectedItem().toString();
                boolean connected = launcher.connect(ip);
                dispose();
                if(connected){
                    addServer(ip);
                }else{
                    System.exit(0);
                }
            }
        });
        add(dropDownMenu);
        add(connectButton);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void addServer(String server){
        try(PrintWriter out = new PrintWriter(new FileWriter(SERVER_LIST, true))) {
            out.println(server);
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String[] getServers() throws IOException {
        ArrayList<String> serverList = new ArrayList<>();
        File file = new File(SERVER_LIST);
        file.createNewFile();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while((line = reader.readLine())!=null){
            serverList.add(line);
        }

        return serverList.toArray(new String[serverList.size()]);
    }
}

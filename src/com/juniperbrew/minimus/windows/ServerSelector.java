package com.juniperbrew.minimus.windows;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.juniperbrew.minimus.Network;
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class ServerSelector extends JFrame {

    final String SERVER_LIST = "serverlist.txt";
    final String errorFolderPath = Tools.getUserDataDirectory()+File.separator+"logs"+File.separator+"errors"+File.separator+"client"+File.separator;
    JComboBox dropDownMenu;
    ClientLauncher launcher;
    String[] serverlist;
    JButton sendErrorsButton;

    public ServerSelector(final ClientLauncher launcher) throws IOException {
        super("Select server");
        this.launcher = launcher;
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
                sendErrorLogs();
            }
        });
        if(getErrorLogs().isEmpty()){
            sendErrorsButton.setEnabled(false);
        }
        add(dropDownMenu);
        add(connectButton);
        add(sendErrorsButton);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void sendErrorLogs(){
        String ip = dropDownMenu.getSelectedItem().toString();
        final ArrayList<String> sentFiles = new ArrayList<>();
        int writeBuffer = 8192; //Default 8192
        int objectBuffer = 8192; //Default 2048
        final Client client = new Client(writeBuffer,objectBuffer);
        Network.register(client);
        Listener listener = new Listener() {

            public void received(Connection connection, Object object) {
                if (object instanceof Network.FileReceived) {
                    Network.FileReceived fileReceived = (Network.FileReceived) object;
                    String fileName = fileReceived.fileName;
                    System.out.println(fileName + " has been received.");
                    sentFiles.remove(fileName);
                    File sentFolder = new File(errorFolderPath+"sent"+File.separator);
                    sentFolder.mkdirs();
                    Path source = Paths.get(errorFolderPath+fileName);
                    Path target = Paths.get(errorFolderPath+"sent"+File.separator+fileName);
                    System.out.println("Moving "+source+" to "+target);
                    try {
                        Files.move(source,target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if(getErrorLogs().isEmpty()){
                        sendErrorsButton.setEnabled(false);
                    }
                }
            }
        };
        client.addListener(listener);
        client.start();
        try {
            client.connect(5000, ip, Network.portTCP, Network.portUDP);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(File file: getErrorLogs()){
            System.out.println("Sending file: "+file);
            sendFile(client,file);
            sentFiles.add(file.getName());
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.nanoTime();
                //Timeout after 5 sec
                int timeout = 5;
                while(!sentFiles.isEmpty()){
                    if(Tools.nanoToSeconds(System.nanoTime()-startTime) > timeout){
                        System.out.println("Sending error logs was not successful");
                        System.out.println("Filetransfer timed out ("+timeout+" seconds)");
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                client.close();
            }
        }).start();
    }

    private ArrayList<File> getErrorLogs(){
        ArrayList<File> files = new ArrayList<>();
        File errorFolder = new File(errorFolderPath);
        if(errorFolder.exists()){
            for (final File fileEntry : errorFolder.listFiles()) {
                if (!fileEntry.isDirectory()) {
                    files.add(fileEntry);
                }
            }
        }
        return files;
    }

    private boolean sendFile(Client client, File file){

        byte[] b = new byte[(int) file.length()];
        try (FileInputStream fileInputStream = new FileInputStream(file);){
            fileInputStream.read(b);
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found.");
            e.printStackTrace();
            return false;
        }
        catch (IOException e1) {
            System.out.println("Error Reading The File.");
            e1.printStackTrace();
            return false;
        }

        Network.SendFile sendFile = new Network.SendFile();
        sendFile.fileName = file.getName();
        sendFile.data = b;
        client.sendTCP(sendFile);
        return true;
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
}

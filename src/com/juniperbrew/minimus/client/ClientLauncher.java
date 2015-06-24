package com.juniperbrew.minimus.client;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.juniperbrew.minimus.windows.ServerSelector;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class ClientLauncher {

    static boolean useJFrame = false;

    public void connect(String ip){
        final LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
        cfg.vSyncEnabled = false; //vsync wastes cpu cycles for some reason
        //cfg.foregroundFPS = 0;
        //cfg.backgroundFPS = 0;

        final MinimusClient minimusClient = new MinimusClient(ip);

        if(useJFrame) {

            final JFrame frame = new JFrame();
            frame.setTitle(minimusClient.getClass().getSimpleName());
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setResizable(false);

            JMenuBar menuBar = new JMenuBar();
            menuBar.add(new JMenu("File"));
            JMenu windowsMenu = new JMenu("Windows");
            JMenuItem consoleItem = new JMenuItem("Console");
            consoleItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    minimusClient.showConsoleWindow();
                }
            });
            JMenuItem serverStatusItem = new JMenuItem("Client status");
            serverStatusItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    minimusClient.showStatusWindow();
                }
            });
            windowsMenu.add(consoleItem);
            windowsMenu.add(serverStatusItem);
            menuBar.add(windowsMenu);
            frame.setJMenuBar(menuBar);


            final Canvas canvas = new Canvas();
            canvas.setSize(cfg.width, cfg.height);
            frame.add(canvas);

            new LwjglApplication(minimusClient, cfg, canvas);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setLocation(frame.getX(), 0);
            frame.setVisible(true);
        }else{
            new LwjglApplication(minimusClient ,cfg);
        }
    }

    public void exit(){
        System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        new ServerSelector(new ClientLauncher());
    }
}

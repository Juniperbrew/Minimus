package com.juniperbrew.minimus.server;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.juniperbrew.minimus.ExceptionLogger;
import com.juniperbrew.minimus.SharedMethods;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.server.MinimusServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class ServerLauncher {

    static boolean useJFrame = false;

    public static void main(String[] args) {

        //Create data directory if it doesn't exist
        File file = new File(Tools.getUserDataDirectory());
        file.mkdirs();

        final MinimusServer minimusServer = new MinimusServer();
        final LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
        cfg.vSyncEnabled = false; //vsync wastes cpu cycles for some reason
        cfg.title = minimusServer.getClass().getSimpleName();
        //cfg.foregroundFPS = 0;
        //cfg.backgroundFPS = 0;

        if(useJFrame) {

            final JFrame frame = new JFrame();
            frame.setTitle(minimusServer.getClass().getSimpleName());
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setResizable(false);

            JMenuBar menuBar = new JMenuBar();
            menuBar.add(new JMenu("File"));
            JMenu windowsMenu = new JMenu("Windows");
            JMenuItem consoleItem = new JMenuItem("Console");
            consoleItem.addActionListener(e -> minimusServer.showConsoleWindow());
            JMenuItem serverStatusItem = new JMenuItem("Server status");
            serverStatusItem.addActionListener(e -> minimusServer.showServerStatusWindow());
            windowsMenu.add(consoleItem);
            windowsMenu.add(serverStatusItem);
            menuBar.add(windowsMenu);
            frame.setJMenuBar(menuBar);


            final Canvas canvas = new Canvas();
            canvas.setSize(cfg.width, cfg.height);
            frame.add(canvas);

            new LwjglApplication(minimusServer, cfg, canvas);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setLocation(frame.getX(), 0);
            frame.setVisible(true);
        }else{
            new LwjglApplication(new MinimusServer() ,cfg);
        }
    }
}

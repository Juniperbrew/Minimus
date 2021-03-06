package com.juniperbrew.minimus.client;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.windows.ServerSelector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class ClientLauncher {

    static boolean useJFrame = false;

    public boolean connect(String ip){
        final LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
        cfg.vSyncEnabled = false; //vsync wastes cpu cycles for some reason
        cfg.foregroundFPS = ConVars.getInt("cl_fps_max");
        cfg.backgroundFPS = ConVars.getInt("cl_fps_background_max");

        MinimusClient client = null;
        try{
            client = new MinimusClient(ip);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        final MinimusClient minimusClient = client;
        cfg.title = minimusClient.getClass().getSimpleName();

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
        return true;
    }

    public void exit(){
        System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        //Create data directory if it doesn't exist
        File file = new File(Tools.getUserDataDirectory());
        file.mkdirs();
        new ServerSelector(new ClientLauncher());
    }
}

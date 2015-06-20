import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * Created by Juniperbrew on 20.6.2015.
 */
public class DesktopClass {
    public static class CloseListener implements WindowListener {
        @Override
        public void windowActivated(final WindowEvent e) {}

        @Override
        public void windowClosed(final WindowEvent e) {}

        @Override
        public void windowClosing(final WindowEvent e) {}

        @Override
        public void windowDeactivated(final WindowEvent e) {}

        @Override
        public void windowDeiconified(final WindowEvent e) {}

        @Override
        public void windowIconified(final WindowEvent e) {}

        @Override
        public void windowOpened(final WindowEvent e) {}
    }

    public static void main(final String[] args) {
        final LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.useGL30 = false;

        config.width = 480;
        config.height = 320;
        config.resizable = true;

        final JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new CloseListener());
        frame.setResizable(false);

        final Canvas canvas = new Canvas();
        canvas.setSize(config.width, config.height);
        frame.add(canvas);

        new LwjglApplication(new SSCCEApplication(), config, canvas);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setLocation(frame.getX(), 0);
        frame.setVisible(true);
    }
}
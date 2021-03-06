import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;

import javax.swing.*;
import java.awt.*;

/**
 * Created by timorasanen on 20/06/15.
 */
public class SSCCELauncher {

    static boolean useJFrame = true;

    public static void main(String[] args) {

        if(!useJFrame){
            LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
            new LwjglApplication(new SSCCEApplication() ,cfg);
        }else {
            final LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();

            final JFrame frame = new JFrame();
            final SSCCEApplication application = new SSCCEApplication();
            frame.setTitle(application.getClass().getSimpleName());

            final Canvas canvas = new Canvas();
            canvas.setSize(cfg.width,cfg.height);
            frame.add(canvas);

            new LwjglApplication(application, cfg, canvas);

            frame.pack();
            frame.setVisible(true);
        }
    }
}

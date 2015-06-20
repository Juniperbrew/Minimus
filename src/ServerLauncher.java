import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl.LwjglGraphics;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class ServerLauncher {

    public static void main(String[] args) {
        LwjglApplicationConfiguration cfg = new LwjglApplicationConfiguration();
        cfg.vSyncEnabled = false; //vsync wastes cpu cycles for some reason
        //cfg.foregroundFPS = 0;
        //cfg.backgroundFPS = 0;
        new LwjglApplication(new MinimusServer() ,cfg);
    }
}

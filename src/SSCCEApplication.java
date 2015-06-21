import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import javax.swing.*;
import java.awt.*;

/**
 * Created by timorasanen on 20/06/15.
 */
public class SSCCEApplication implements ApplicationListener, InputProcessor {

    ShapeRenderer shapeRenderer;
    JFrame testWindow;

    @Override
    public void create() {
        shapeRenderer = new ShapeRenderer();
        testWindow = new JFrame("Empty test window");
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(400, 400));
        testWindow.add(content);
        testWindow.pack();
        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void resize(int width, int height) {

    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1, 0, 0, 1);
        shapeRenderer.rect(100, 100, 50, 50);
        shapeRenderer.end();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void dispose() {

    }

    @Override
    public boolean keyDown(int keycode) {
        System.out.println("KeyDown:"+keycode);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        System.out.println("KeyUp:"+keycode);
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        if(character == '1'){
            testWindow.setVisible(true);
        }
        System.out.println("KeyTyped:"+character);
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }
}

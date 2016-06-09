package com.juniperbrew.minimus.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.juniperbrew.minimus.F;

/**
 * Created by Juniperbrew on 09/06/16.
 */
public class ServerGraphs implements ApplicationListener, InputProcessor {

    GameServer server;
    ShapeRenderer shapeRenderer;
    OrthographicCamera camera;
    SpriteBatch batch;
    BitmapFont font;


    float graphX = 50;
    float graphY = 50;

    final float graphHeight = 100;
    final float graphWidth = 150;
    final float graphSpacing = graphWidth + 50;

    public ServerGraphs(){
        server = new GameServer();
    }

    @Override
    public void create() {
        Gdx.input.setInputProcessor(this);
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        font = new BitmapFont();

        Gdx.graphics.setDisplayMode(800,200,false);
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    @Override
    public void render() {

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
        float x = graphX;
        float y = graphY;
        F.drawLog("Fps", "fps", server.fpsLog, shapeRenderer, batch, font, x, y, graphWidth, graphHeight, 1, 60, 20);
        x += graphSpacing;
        //F.drawLog("Delta", "ms", server.deltaLog, shapeRenderer, batch, font, 250, 100, 150, 100, 4, (1000 / 60f), (1000 / 20f));
        F.drawLog("Logic", "ms", server.logicLog, shapeRenderer,batch,font, x, y, graphWidth, graphHeight, 20, 3,10);
        x += graphSpacing;
        //F.drawLog("Render", "ms", server.renderLog, shapeRenderer,batch,font, 650, 100, 150, 100, 20, 5,(1000/60f));
        //F.drawLog("FrameTime", "ms", server.frameTimeLog, shapeRenderer, batch, font, 850, 100, 150, 100, 20, 5, (1000 / 60f));
        F.drawLog("Download", "kB/s", server.serverData.kiloBytesPerSecondReceivedLog, shapeRenderer, batch, font, x, y, graphWidth, graphHeight, 25, 2, 10);
        x += graphSpacing;
        F.drawLog("Upload", "kB/s", server.serverData.kiloBytesPerSecondSentLog, shapeRenderer,batch,font, x, y, graphWidth, graphHeight, 2, 10,100);
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
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
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

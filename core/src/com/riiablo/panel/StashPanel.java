package com.riiablo.panel;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.loader.DC6Loader;
import com.riiablo.screen.GameScreen;
import com.riiablo.widget.Button;

public class StashPanel extends WidgetGroup implements Disposable {
  private static final String TAG = "StashPanel";

  final AssetDescriptor<DC6> TradeStashDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\TradeStash.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion TradeStash;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class);
  Button btnExit;

  final GameScreen gameScreen;
  final Inventory.Entry inventory;

  public StashPanel(GameScreen gameScreen) {
    this.gameScreen = gameScreen;

    Riiablo.assets.load(TradeStashDescriptor);
    Riiablo.assets.finishLoadingAsset(TradeStashDescriptor);
    TradeStash = Riiablo.assets.get(TradeStashDescriptor).getTexture();
    setSize(TradeStash.getRegionWidth(), TradeStash.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    btnExit = new Button(new Button.ButtonStyle() {{
      Riiablo.assets.load(buysellbtnDescriptor);
      Riiablo.assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(Riiablo.assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    btnExit.setPosition(272, 15);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        setVisible(false);
        StashPanel.this.gameScreen.inventoryPanel.setVisible(false);
      }
    });
    addActor(btnExit);

    inventory = Riiablo.files.inventory.get("Big Bank Page 1");

    ItemGrid grid = new ItemGrid(this.gameScreen, inventory);
    //grid.populate(gameScreen.player.getStash());
    grid.setPosition(
        inventory.gridLeft - inventory.invLeft,
        getHeight() - inventory.gridTop - grid.getHeight());
    addActor(grid);

    setDebug(true, true);
  }

  @Override
  public void dispose() {
    btnExit.dispose();
    Riiablo.assets.unload(TradeStashDescriptor.fileName);
    Riiablo.assets.unload(buysellbtnDescriptor.fileName);
  }

  @Override
  public void draw(Batch batch, float a) {
    batch.draw(TradeStash, getX(), getY());
    super.draw(batch, a);
  }
}

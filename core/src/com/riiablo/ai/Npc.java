package com.riiablo.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.entity.Entity;
import com.riiablo.entity.Monster;
import com.riiablo.map.DS1;
import com.riiablo.screen.GameScreen;
import com.riiablo.widget.NpcDialogBox;
import com.riiablo.widget.NpcMenu;

import org.apache.commons.lang3.ArrayUtils;

public class Npc extends AI {
  private static final String TAG = "Npc";

  static final IntSet TALKERS    = new IntSet();
  static final IntSet REPAIRERS  = new IntSet();
  static final IntSet TRADERS    = new IntSet();
  static final IntSet GAMBLERS   = new IntSet();
  static final IntSet HIRERERS   = new IntSet();
  static {
    TALKERS.addAll(146, 147, 148, 150, 154, 155);
    REPAIRERS.addAll(154);
    TRADERS.addAll(147, 148, 154);
    GAMBLERS.addAll(147);
    HIRERERS.addAll(150);
  }

  int targetId = ArrayUtils.INDEX_NOT_FOUND;
  float actionTimer = 0;
  boolean actionPerformed = false;
  NpcMenu menu;
  Entity lookAt;

  public Npc(Monster entity) {
    super(entity);
  }

  @Override
  public void interact(final GameScreen gameScreen) {
    // TODO: need some kind of static method that can take in some state params, e.g., character
    //       class, player mode and spit out the proper file index.
    //       I.e., akara_act1_intro -> akara_act1_intro_sor automatically if it exists
    String name = entity.name().toLowerCase();
    String id = name + "_greeting_1";
    Audio.Instance instance = Riiablo.audio.play(id, false);
    if (instance == null) {
      id = name + "_greeting_inactive_1";
      Riiablo.audio.play(id, false);
    }

    actionTimer = Float.POSITIVE_INFINITY;
    actionPerformed = false;
    entity.setMode(Monster.MODE_NU);
    entity.target().set(entity.position());
    lookAt = gameScreen.player;
    entity.lookAt(lookAt);
    entity.update(0);

    if (menu == null) {
      menu = new NpcMenu(entity, gameScreen, entity.name());

      final int entType = entity.monstats.hcIdx;
      if (TALKERS.contains(entType)) {
        // talk
        menu.addItem(3381, new NpcMenu(3381)
            // introduction
            .addItem(3399, new ClickListener() {
              @Override
              public void clicked(InputEvent event, float x, float y) {
                String name = entity.name().toLowerCase();
                String id = name + "_act1_intro";
                gameScreen.setDialog(new NpcDialogBox(id, new NpcDialogBox.DialogCompletionListener() {
                  @Override
                  public void onCompleted(NpcDialogBox d) {
                    gameScreen.setDialog(null);
                  }
                }));
              }
            })
            // gossip
            .addItem(3395, new ClickListener() {
              @Override
              public void clicked(InputEvent event, float x, float y) {
                String name = entity.name().toLowerCase();
                String id = name + "_act1_gossip_1";
                gameScreen.setDialog(new NpcDialogBox(id, new NpcDialogBox.DialogCompletionListener() {
                  @Override
                  public void onCompleted(NpcDialogBox d) {
                    gameScreen.setDialog(null);
                  }
                }));
              }
            })
            .addCancel(null)
            .build());
      }

      if (REPAIRERS.contains(entType)) {
        menu.addItem(3334, new ClickListener()); // trade/repair
      } else if (TRADERS.contains(entType)) {
        menu.addItem(3396, new ClickListener()); // trade
      }

      if (HIRERERS.contains(entType)) {
        menu.addItem(3397, new ClickListener()); // gamble
      }

      if (GAMBLERS.contains(entType)) {
        menu.addItem(3398, new ClickListener()); // gamble
      }

      menu.addCancel(new NpcMenu.CancellationListener() {
          @Override
          public void onCancelled() {
            actionTimer = 4;
            entity.target().setZero();
          }
        })
        .build();
    }

    gameScreen.setMenu(menu, entity);
  }

  public void update(float delta) {
    Vector2 target = entity.target();
    if (target.isZero() || (entity.position().epsilonEquals(target) && !entity.targets().hasNext())) {
      DS1.Path path = entity.object.path;
      if (targetId == ArrayUtils.INDEX_NOT_FOUND) {
        targetId = 0;
      } else if (actionTimer > 0) {
        if (lookAt != null) {
          if (entity.position().dst(lookAt.position()) <= 10) {
            entity.lookAt(lookAt);
          } else
            actionTimer = -1;
        }

        actionTimer -= delta;
        actionPerformed = actionTimer < 0;

        return;
      } else if (actionPerformed) {
        actionPerformed = false;
        lookAt = null;
        targetId = MathUtils.random(path.numPoints - 1);
      } else {
        entity.setMode(Monster.MODE_NU);
        actionTimer = action(path.points[targetId].action);
        actionPerformed = actionTimer < 0;
        return;
      }

      //entity.setMode("WL");
      DS1.Path.Point dst = path.points[targetId];
      entity.setPath(entity.map, new Vector2(dst.x, dst.y));
    }
  }

  private float action(int action) {
    // path.actions == look at nearest player, chill, hold time, quest?
    // 1 = 4 second hold
    // 2 = 6 second hold
    // 3 = 4 second hold
    // 4 = special action at end of 10 second warriv jamella spell
    // 4 = special action at end of 8  second charsi jamella book
    // 4? fara = 5 seconds
    // 4 == S1
    // 5 == S2
    // etc

    switch (action) {
      case 1:
      case 3:
        return 4;
      case 2:
        return 6;
      // TODO: play anim only once, after timer, ending the action
      case 4:
        entity.setMode(Monster.MODE_S1);
        return 10;
      case 5:
        entity.setMode(Monster.MODE_S2);
        return 10;
      default:
        Gdx.app.error(TAG, "Unknown action index: " + action);
        return 4;
    }
  }
}

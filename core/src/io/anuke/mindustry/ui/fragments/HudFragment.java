package io.anuke.mindustry.ui.fragments;

import io.anuke.arc.Core;
import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.input.KeyCode;
import io.anuke.arc.math.Interpolation;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.scene.Element;
import io.anuke.arc.scene.Group;
import io.anuke.arc.scene.actions.Actions;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.Image;
import io.anuke.arc.scene.ui.ImageButton;
import io.anuke.arc.scene.ui.TextButton;
import io.anuke.arc.scene.ui.layout.Stack;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.arc.scene.ui.layout.Unit;
import io.anuke.arc.scene.utils.Elements;
import io.anuke.arc.util.Align;
import io.anuke.arc.util.Scaling;
import io.anuke.arc.util.Time;
import io.anuke.arc.util.Tmp;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.game.EventType.StateChangeEvent;
import io.anuke.mindustry.game.UnlockableContent;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Pal;
import io.anuke.mindustry.input.Binding;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Packets.AdminAction;
import io.anuke.mindustry.ui.Bar;
import io.anuke.mindustry.ui.IntFormat;
import io.anuke.mindustry.ui.dialogs.FloatingDialog;

import static io.anuke.mindustry.Vars.*;

public class HudFragment extends Fragment{
    public final PlacementFragment blockfrag = new PlacementFragment();

    private ImageButton flip;
    private Table lastUnlockTable;
    private Table lastUnlockLayout;
    private boolean shown = true;
    private float dsize = 59;
    private float isize = 40;

    private float coreAttackTime;
    private float lastCoreHP;
    private float coreAttackOpacity = 0f;

    public void build(Group parent){

        //menu at top left
        parent.fill(cont -> {
            cont.top().left().visible(() -> !state.is(State.menu));

            if(mobile){

                {
                    Table select = new Table(){
                        @Override
                        public void act(float delta){
                            setSize(getPrefWidth(), getPrefHeight());
                            setPosition(0, 0, Align.topLeft);
                            super.act(delta);
                        }

                        @Override
                        public float getPrefWidth(){
                            return Unit.dp.scl(dsize*4 + 3);
                        }

                        @Override
                        public float getPrefHeight(){
                            return Unit.dp.scl(dsize);
                        }
                    };

                    select.left();
                    select.defaults().size(dsize).left();

                    select.addImageButton("icon-menu", "clear", isize, ui.paused::show);
                    flip = select.addImageButton("icon-arrow-up", "clear", isize, this::toggleMenus).get();

                    select.addImageButton("icon-pause", "clear", isize, () -> {
                        if(Net.active()){
                            ui.listfrag.toggle();
                        }else{
                            state.set(state.is(State.paused) ? State.playing : State.paused);
                        }
                    }).update(i -> {
                        if(Net.active()){
                            i.getStyle().imageUp = Core.scene.skin.getDrawable("icon-players");
                        }else{
                            i.setDisabled(false);
                            i.getStyle().imageUp = Core.scene.skin.getDrawable(state.is(State.paused) ? "icon-play" : "icon-pause");
                        }
                    }).get();

                    select.addImageButton("icon-settings", "clear", isize, () -> {
                        if(Net.active() && mobile){
                            if(ui.chatfrag.chatOpen()){
                                ui.chatfrag.hide();
                            }else{
                                ui.chatfrag.toggle();
                            }
                        }else if(world.isZone()){
                            ui.tech.show();
                        }else{
                            ui.database.show();
                        }
                    }).update(i -> {
                        if(Net.active() && mobile){
                            i.getStyle().imageUp = Core.scene.skin.getDrawable("icon-chat");
                        }else{
                            i.getStyle().imageUp = Core.scene.skin.getDrawable("icon-database-small");
                        }
                    }).get();

                    select.addImage("blank").color(Pal.accent).width(3f).fillY();
                    cont.add(select).prefSize(dsize*4 + 3, dsize).left();
                }

                cont.row();
                cont.addImage("blank").height(3f).color(Pal.accent).fillX();
                cont.row();
            }

            cont.update(() -> {
                if(!Core.input.keyDown(Binding.gridMode) && Core.input.keyTap(Binding.toggle_menus) && !ui.chatfrag.chatOpen()){
                    toggleMenus();
                }
            });

            cont.table(stuff -> {
                stuff.left();
                Stack stack = new Stack();
                TextButton waves = new TextButton("", "wave");
                Table btable = new Table().margin(0);

                stack.add(waves);
                stack.add(btable);

                addWaveTable(waves);
                addPlayButton(btable);
                stuff.add(stack).width(dsize * 4 + 3f);
                stuff.row();
                stuff.table("button", t -> t.margin(10f).add(new Bar("boss.health", Pal.health, () -> state.boss() == null ? 0f : state.boss().healthf()).blink(Color.WHITE))
                    .grow()).fillX().visible(() -> state.rules.waves && state.boss() != null).height(60f).get();
                stuff.row();
            }).visible(() -> shown);
        });


        //fps display
        parent.fill(info -> {
            info.top().right().margin(4).visible(() -> Core.settings.getBool("fps") && !state.is(State.menu));
            IntFormat fps = new IntFormat("fps");
            IntFormat ping = new IntFormat("ping");
            info.label(() -> fps.get(Core.graphics.getFramesPerSecond())).right();
            info.row();
            info.label(() -> ping.get(Net.getPing())).visible(Net::client).right();
        });

        //spawner warning
        parent.fill(t -> {
            t.touchable(Touchable.disabled);
            t.visible(() -> !state.is(State.menu));
            t.table("flat", c -> c.add("$nearpoint")
            .update(l -> l.setColor(Tmp.c1.set(Color.WHITE).lerp(Color.SCARLET, Mathf.absin(Time.time(), 10f, 1f))))
            .get().setAlignment(Align.center, Align.center))
            .margin(6).update(u -> u.color.a = Mathf.lerpDelta(u.color.a, Mathf.num(world.spawner.playerNear()), 0.1f)).get().color.a = 0f;
        });

        //out of bounds warning
        parent.fill(t -> {
            t.touchable(Touchable.disabled);
            t.visible(() -> !state.is(State.menu));
            t.table("flat", c -> c.add("")
            .update(l ->{
                l.setColor(Tmp.c1.set(Color.WHITE).lerp(Color.SCARLET, Mathf.absin(Time.time(), 10f, 1f)));
                l.setText(Core.bundle.format("outofbounds", (int)((boundsCountdown - players[0].destructTime) / 60f)));
            }).get().setAlignment(Align.center, Align.center)).margin(6).update(u -> {
                u.color.a = Mathf.lerpDelta(u.color.a, Mathf.num(players[0].isOutOfBounds()), 0.1f);
            }).get().color.a = 0f;
        });

        parent.fill(t -> {
            t.visible(() -> netServer.isWaitingForPlayers() && !state.is(State.menu));
            t.table("button", c -> c.add("$waiting.players"));
        });

        //'core is under attack' table
        parent.fill(t -> {
            float notifDuration = 240f;

            Events.on(StateChangeEvent.class, event -> {
                if(event.to == State.menu || event.from == State.menu){
                    coreAttackTime = 0f;
                    lastCoreHP = Float.NaN;
                }
            });

            t.top().visible(() -> {
                if(state.is(State.menu) || state.teams.get(players[0].getTeam()).cores.size == 0 ||
                state.teams.get(players[0].getTeam()).cores.first().entity == null){
                    coreAttackTime = 0f;
                    return false;
                }

                float curr = state.teams.get(players[0].getTeam()).cores.first().entity.health;
                if(!Float.isNaN(lastCoreHP) && curr < lastCoreHP){
                    coreAttackTime = notifDuration;
                }
                lastCoreHP = curr;

                t.getColor().a = coreAttackOpacity;
                if(coreAttackTime > 0){
                    coreAttackOpacity = Mathf.lerpDelta(coreAttackOpacity, 1f, 0.1f);
                }else{
                    coreAttackOpacity = Mathf.lerpDelta(coreAttackOpacity, 0f, 0.1f);
                }

                coreAttackTime -= Time.delta();

                return coreAttackOpacity > 0;
            });
            t.table("button", top -> top.add("$coreattack").pad(2)
                .update(label -> label.getColor().set(Color.ORANGE).lerp(Color.SCARLET, Mathf.absin(Time.time(), 2f, 1f))));
        });

        //launch button
        parent.fill(t -> {
            t.top().visible(() -> !state.is(State.menu));
            TextButton[] testb = {null};

            TextButton button = Elements.newButton("$launch", () -> {
                FloatingDialog dialog = new FloatingDialog("$launch");
                dialog.update(() -> {
                    if(!testb[0].isVisible()){
                        dialog.hide();
                    }
                });
                dialog.cont.add("$launch.confirm").width(500f).wrap().pad(4f).get().setAlignment(Align.center, Align.center);
                dialog.buttons.defaults().size(200f, 54f).pad(2f);
                dialog.setFillParent(false);
                dialog.buttons.addButton("$cancel", dialog::hide);
                dialog.buttons.addButton("$ok", () -> {
                    dialog.hide();
                    Call.launchZone();
                });
                dialog.keyDown(KeyCode.ESCAPE, dialog::hide);
                dialog.keyDown(KeyCode.BACK, dialog::hide);
                dialog.show();

            });

            testb[0] = button;

            button.getStyle().disabledFontColor = Color.WHITE;
            button.visible(() ->
                world.isZone() &&
                world.getZone().metCondition() &&
                !Net.client() &&
                state.wave % world.getZone().launchPeriod == 0 && !world.spawner.isSpawning());

            button.update(() -> {
                if(world.getZone() == null){
                    button.setText("");
                    return;
                }

                button.setText(state.enemies() > 0 ? Core.bundle.format("launch.unable", state.enemies()) : Core.bundle.get("launch") + "\n" +
                    Core.bundle.format("launch.next", state.wave + world.getZone().launchPeriod));

                button.getLabel().setColor(Tmp.c1.set(Color.WHITE).lerp(state.enemies() > 0 ? Color.WHITE : Pal.accent,
                    Mathf.absin(Time.time(), 7f, 1f)));
            });

            button.setDisabled(() -> state.enemies() > 0);

            button.getLabelCell().left().get().setAlignment(Align.left, Align.left);

            t.add(button).size(350f, 80f);
        });

        //paused table
        parent.fill(t -> {
            t.top().visible(() -> state.is(State.paused) && !Net.active());
            t.table("button", top -> top.add("$paused").pad(6f));
        });

        //'saving' indicator
        parent.fill(t -> {
            t.bottom().visible(() -> !state.is(State.menu) && control.saves.isSaving());
            t.add("$saveload");
        });

        blockfrag.build(Core.scene.root);
    }

    public void showToast(String text){
        Table table = new Table("button");
        table.update(() -> {
            if(state.is(State.menu)){
                table.remove();
            }
        });
        table.margin(12);
        table.addImage("icon-check").size(16 * 2).pad(3);
        table.add(text).wrap().width(280f).get().setAlignment(Align.center, Align.center);
        table.pack();

        //create container table which will align and move
        Table container = Core.scene.table();
        container.top().add(table);
        container.setTranslation(0, table.getPrefHeight());
        container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interpolation.fade), Actions.delay(4f),
        //nesting actions() calls is necessary so the right prefHeight() is used
        Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interpolation.fade), Actions.remove())));
    }

    public boolean shown(){
        return shown;
    }

    /** Show unlock notification for a new recipe. */
    public void showUnlock(UnlockableContent content){
        //some content may not have icons... yet
        if(content.getContentIcon() == null) return;

        //if there's currently no unlock notification...
        if(lastUnlockTable == null){
            Table table = new Table("button");
            table.update(() -> {
                if(state.is(State.menu)){
                    table.remove();
                    lastUnlockLayout = null;
                    lastUnlockTable = null;
                }
            });
            table.margin(12);

            Table in = new Table();

            //create texture stack for displaying
            Image image = new Image(content.getContentIcon());
            image.setScaling(Scaling.fit);

            in.add(image).size(48f).pad(2);

            //add to table
            table.add(in).padRight(8);
            table.add("$unlocked");
            table.pack();

            //create container table which will align and move
            Table container = Core.scene.table();
            container.top().add(table);
            container.setTranslation(0, table.getPrefHeight());
            container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interpolation.fade), Actions.delay(4f),
            //nesting actions() calls is necessary so the right prefHeight() is used
            Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interpolation.fade), Actions.run(() -> {
                lastUnlockTable = null;
                lastUnlockLayout = null;
            }), Actions.remove())));

            lastUnlockTable = container;
            lastUnlockLayout = in;
        }else{
            //max column size
            int col = 3;
            //max amount of elements minus extra 'plus'
            int cap = col * col - 1;

            //get old elements
            Array<Element> elements = new Array<>(lastUnlockLayout.getChildren());
            int esize = elements.size;

            //...if it's already reached the cap, ignore everything
            if(esize > cap) return;

            //get size of each element
            float size = 48f / Math.min(elements.size + 1, col);

            lastUnlockLayout.clearChildren();
            lastUnlockLayout.defaults().size(size).pad(2);

            for(int i = 0; i < esize && i <= cap; i++){
                lastUnlockLayout.add(elements.get(i));

                if(i % col == col - 1){
                    lastUnlockLayout.row();
                }
            }

            //if there's space, add it
            if(esize < cap){

                Image image = new Image(content.getContentIcon());
                image.setScaling(Scaling.fit);

                lastUnlockLayout.add(image);
            }else{ //else, add a specific icon to denote no more space
                lastUnlockLayout.addImage("icon-add");
            }

            lastUnlockLayout.pack();
        }
    }

    public void showLaunch(){
        Image image = new Image("white");
        image.getColor().a = 0f;
        image.setFillParent(true);
        image.actions(Actions.fadeIn(40f / 60f));
        image.update(() -> {
            if(state.is(State.menu)){
                image.remove();
            }
        });
        Core.scene.add(image);
    }

    private void toggleMenus(){
        if(flip != null){
            flip.getStyle().imageUp = Core.scene.skin.getDrawable(shown ? "icon-arrow-down" : "icon-arrow-up");
        }

        shown = !shown;
    }

    private void addWaveTable(TextButton table){

        IntFormat wavef = new IntFormat("wave");
        IntFormat enemyf = new IntFormat("wave.enemy");
        IntFormat enemiesf = new IntFormat("wave.enemies");
        IntFormat waitingf = new IntFormat("wave.waiting");

        table.clearChildren();
        table.touchable(Touchable.enabled);

        StringBuilder builder = new StringBuilder();

        table.labelWrap(() -> {
            builder.setLength(0);
            builder.append(wavef.get(state.wave));
            builder.append("\n");

            if(state.enemies() > 0 && !state.rules.waveTimer){
                if(state.enemies() == 1){
                    builder.append(enemyf.get(state.enemies()));
                }else{
                    builder.append(enemiesf.get(state.enemies()));
                }
            }else if(state.rules.waveTimer){
                builder.append(waitingf.get((int)(state.wavetime/60)));
            }else{
                builder.append(Core.bundle.get("waiting"));
            }

            return builder;
        }).growX().pad(8f);

        table.setDisabled(true);
        table.visible(() -> state.rules.waves);
    }

    private void addPlayButton(Table table){
        table.right().addImageButton("icon-play", "right", 30f, () -> {
            if(Net.client() && players[0].isAdmin){
                Call.onAdminRequest(players[0], AdminAction.wave);
            }else{
                state.wavetime = 0f;
            }
        }).growY().fillX().right().width(40f)
        .visible(() -> state.rules.waves && ((Net.server() || players[0].isAdmin) || !Net.active()) && state.enemies() == 0
        && (!world.spawner.isSpawning() || !state.rules.waveTimer));
    }
}

package net.runelite.client.plugins.microbot.drohunter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class BirdHunterPlugin extends Plugin {

    public final static String version = "1.0.3";

    @Inject
    private Client client;

    @Inject
    private DroHunterConfig config;

    @Inject
    private BirdHunterScript birdHunterScript;

    @Inject
    private BirdHunterOverlay birdHunterOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Getter
    private final Map<WorldPoint, HunterTrap> traps = new HashMap<>();
    private WorldPoint lastTickLocalPlayerLocation;

    @Override
    protected void startUp() {
        // Seed lastTickLocalPlayerLocation on the client thread before the
        // script loop can fire a layBirdSnare — otherwise the first snare's
        // GameObjectSpawned event sees a null baseline, the trap is never
        // recorded as owned, and the filter/setTrap path puts the bot in an
        // infinite movePlayerOffObject loop on its own untracked trap.
        // startUp runs on the AWT EDT; reading player location from there
        // throws "must be called on client thread".
        lastTickLocalPlayerLocation = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player lp = client.getLocalPlayer();
            return lp != null ? lp.getWorldLocation() : null;
        }).orElse(null);

        birdHunterScript.run(config, this);
        this.overlayManager.add(this.birdHunterOverlay);
    }

    @Override
    protected void shutDown() {
        this.overlayManager.remove(this.birdHunterOverlay);
        birdHunterScript.shutdown();
        traps.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getKey().equals("huntingRadiusValue")) {
            birdHunterScript.updateHuntingArea(config);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        final GameObject go = event.getGameObject();
        final WorldPoint trapLocation = go.getWorldLocation();
        final HunterTrap myTrap = traps.get(trapLocation);

        switch (go.getId()) {
            // Empty placed snare — ownership decision point. Player location is
            // updated before this event fires, so we compare the spawn tile to
            // the PREVIOUS tick's player location. distance == 0 means the snare
            // spawned on the exact tile the player stood on last tick, i.e. ours.
            case ObjectID.HUNTING_OJIBWAY_TRAP:
                if (lastTickLocalPlayerLocation != null
                        && trapLocation.distanceTo(lastTickLocalPlayerLocation) == 0) {
                    traps.put(trapLocation, new HunterTrap(go));
                }
                break;

            case ObjectID.HUNTING_OJIBWAY_TRAP_FULL_JUNGLE:
            case ObjectID.HUNTING_OJIBWAY_TRAP_FULL_POLAR:
            case ObjectID.HUNTING_OJIBWAY_TRAP_FULL_DESERT:
            case ObjectID.HUNTING_OJIBWAY_TRAP_FULL_WOODLAND:
            case ObjectID.HUNTING_OJIBWAY_TRAP_FULL_COLOURED:
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.FULL);
                    myTrap.resetTimer();
                }
                break;

            case ObjectID.HUNTING_OJIBWAY_TRAP_BROKEN:
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.EMPTY);
                    myTrap.resetTimer();
                }
                break;

            case ObjectID.HUNTING_OJIBWAY_TRAP_FAILING:
            case ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_JUNGLE:
            case ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_COLOURED:
            case ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_DESERT:
            case ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_WOODLAND:
            case ObjectID.HUNTING_OJIBWAY_TRAP_TRAPPING_POLAR:
                if (myTrap != null) {
                    myTrap.setState(HunterTrap.State.TRANSITION);
                }
                break;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        Iterator<Map.Entry<WorldPoint, HunterTrap>> it = traps.entrySet().iterator();
        Tile[][][] tiles = client.getScene().getTiles();
        Instant expire = Instant.now().minus(HunterTrap.TRAP_TIME.multipliedBy(2));

        while (it.hasNext()) {
            Map.Entry<WorldPoint, HunterTrap> entry = it.next();
            HunterTrap trap = entry.getValue();
            WorldPoint world = entry.getKey();
            LocalPoint local = LocalPoint.fromWorld(client, world);

            if (local == null) {
                if (trap.getPlacedOn().isBefore(expire)) it.remove();
                continue;
            }

            GameObject[] objects = tiles[world.getPlane()][local.getSceneX()][local.getSceneY()].getGameObjects();
            boolean anyObject = false;
            for (GameObject o : objects) {
                if (o != null) { anyObject = true; break; }
            }
            if (!anyObject) it.remove();
        }

        Player lp = client.getLocalPlayer();
        if (lp != null) lastTickLocalPlayerLocation = lp.getWorldLocation();
    }
}

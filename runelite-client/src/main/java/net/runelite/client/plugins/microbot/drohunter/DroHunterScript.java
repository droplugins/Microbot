package net.runelite.client.plugins.microbot.drohunter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DroHunterScript extends Script {

    private static final long MIN_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(25);
    private static final long MIN_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long MAX_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(7);
    private static final long MIN_MISCLICK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_MISCLICK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int MIN_INPUT_DELAY_MS = 25;
    private static final int MAX_INPUT_DELAY_MS = 200;
    private static final int[][] MISCLICK_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1}, {-1, 0},
            {1, 0}, {-1, 1}, {0, 1}, {1, 1}
    };

    private enum State {
        IDLE,
        CATCHING,
        DROPPING,
        LAYING
    }

    private boolean oneRun;
    private final List<WorldPoint> boxTiles = new ArrayList<>();
    private final List<Integer> trapIds = Arrays.asList(
            ItemID.BOX_TRAP,
            ObjectID.BOX_TRAP,
            ObjectID.BOX_TRAP_9385,
            ObjectID.BOX_TRAP_9380,
            ObjectID.SHAKING_BOX_9384,
            ObjectID.SHAKING_BOX_9383,
            ObjectID.SHAKING_BOX_9382,
            ObjectID.SHAKING_BOX
    );
    private State currentState = State.IDLE;
    private boolean sessionInitialized;
    private long nextRestAt = Long.MAX_VALUE;
    private long restUntil;
    private long nextMisclickAt = Long.MAX_VALUE;

    public boolean run(DroHunterConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!initializeSession()) return;
                if (handleScheduledRest()) return;
                if (handleScheduledMisclick()) return;

                Microbot.status = currentState.name();
                handleBreaks();

                switch (currentState) {
                    case IDLE:
                        handleIdleState();
                        break;
                    case DROPPING:
                        handleDroppingState(config);
                        break;
                    case CATCHING:
                        handleCatchingState(config);
                        break;
                    case LAYING:
                        handleLayingState(config);
                        break;
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        currentState = State.IDLE;
        oneRun = false;
        boxTiles.clear();
        sessionInitialized = false;
        nextRestAt = Long.MAX_VALUE;
        restUntil = 0L;
        nextMisclickAt = Long.MAX_VALUE;
        super.shutdown();
    }

    private boolean initializeSession() {
        if (sessionInitialized) {
            return true;
        }
        boolean playerReady = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null).orElse(false);
        if (!playerReady) {
            return false;
        }

        long now = System.currentTimeMillis();
        nextRestAt = now + randomBetween(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
        nextMisclickAt = now + randomBetween(MIN_MISCLICK_INTERVAL_MS, MAX_MISCLICK_INTERVAL_MS);
        sessionInitialized = true;
        return true;
    }

    private boolean handleScheduledRest() {
        long now = System.currentTimeMillis();
        if (restUntil > now) {
            Microbot.status = "Resting";
            return true;
        }

        if (restUntil != 0L) {
            restUntil = 0L;
            nextRestAt = now + randomBetween(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
            log.info("Rest complete; next rest in {} seconds",
                    TimeUnit.MILLISECONDS.toSeconds(nextRestAt - now));
        }

        if (now < nextRestAt
                || isBreakCleanupDue()
                || Rs2Dialogue.isInDialogue()
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating()) {
            return false;
        }

        long duration = randomBetween(MIN_REST_DURATION_MS, MAX_REST_DURATION_MS);
        restUntil = now + duration;
        nextRestAt = Long.MAX_VALUE;
        Microbot.status = "Resting";
        log.info("Starting scheduled rest for {} ms", duration);
        pauseBeforeInput();
        Rs2Antiban.moveMouseOffScreen();
        return true;
    }

    private boolean handleScheduledMisclick() {
        long now = System.currentTimeMillis();
        if (now < nextMisclickAt
                || isBreakCleanupDue()
                || Rs2Dialogue.isInDialogue()
                || Rs2Player.isAnimating()) {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            nextMisclickAt = now + TimeUnit.SECONDS.toMillis(30);
            return false;
        }

        int startDirection = ThreadLocalRandom.current().nextInt(MISCLICK_OFFSETS.length);
        for (int i = 0; i < MISCLICK_OFFSETS.length; i++) {
            int[] offset = MISCLICK_OFFSETS[(startDirection + i) % MISCLICK_OFFSETS.length];
            WorldPoint misclickPoint = playerLocation.dx(offset[0]).dy(offset[1]);
            if (!Rs2Walker.isWalkableInCollisionMap(misclickPoint)) {
                continue;
            }

            pauseBeforeInput();
            if (Rs2Walker.walkFastCanvas(misclickPoint, Rs2Player.isRunEnabled())) {
                nextMisclickAt = System.currentTimeMillis()
                        + randomBetween(MIN_MISCLICK_INTERVAL_MS, MAX_MISCLICK_INTERVAL_MS);
                sleep(400, 1200);
                return true;
            }
        }

        nextMisclickAt = now + TimeUnit.SECONDS.toMillis(30);
        return false;
    }

    private boolean isBreakCleanupDue() {
        return BreakHandlerScript.breakIn > 0 && BreakHandlerScript.breakIn <= 60;
    }

    private long randomBetween(long minimum, long maximum) {
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
    }

    private void pauseBeforeInput() {
        sleep(ThreadLocalRandom.current().nextInt(MIN_INPUT_DELAY_MS, MAX_INPUT_DELAY_MS + 1));
    }

    private void handleIdleState() {
        try {
            pauseBeforeInput();
            if (Microbot.getRs2TileItemCache().query().withId(ItemID.BOX_TRAP).within(4).interact("lay")) {
                currentState = State.LAYING;
                return;
            }

            if (Rs2Inventory.emptySlotCount() <= 1 && Rs2Inventory.contains(ItemID.FERRET)) {
                while (Rs2Inventory.contains(ItemID.FERRET)) {
                    pauseBeforeInput();
                    Rs2Inventory.interact(ItemID.FERRET, "Release");
                    sleep(0, 750);
                    if (!Rs2Inventory.contains(ItemID.FERRET)) {
                        break;
                    }
                }
                currentState = State.DROPPING;
                return;
            }

            pauseBeforeInput();
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9384).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            pauseBeforeInput();
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9383).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            pauseBeforeInput();
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX_9382).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }
            // Black chinchompa shaking box
            pauseBeforeInput();
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.SHAKING_BOX).within(4).interact("reset")) {
                currentState = State.CATCHING;
                return;
            }

            pauseBeforeInput();
            if (Microbot.getRs2TileObjectCache().query().withId(ObjectID.BOX_TRAP_9385).within(4).interact("reset")) {
                currentState = State.CATCHING;
            }
        } catch (Exception ex) {
            log.warn("Hunter state action failed", ex);
            currentState = State.CATCHING;
        }
    }

    private void handleDroppingState(DroHunterConfig config) {
        sleep(config.chinMinSleepAfterLay(), config.chinMaxSleepAfterLay());
        currentState = State.IDLE;
    }

    private void handleCatchingState(DroHunterConfig config) {
        sleep(config.chinMinSleepAfterCatch(), config.chinMaxSleepAfterCatch());
        currentState = State.IDLE;
    }

    private void handleLayingState(DroHunterConfig config) {
        sleep(config.chinMinSleepAfterLay(), config.chinMaxSleepAfterLay());
        currentState = State.IDLE;
    }

    private void handleBreaks() {
        int secondsUntilBreak = BreakHandlerScript.breakIn;

        if (secondsUntilBreak > 61 && secondsUntilBreak < 200) {
            if (!boxTiles.isEmpty()) {
                boxTiles.clear();
            }
        }

        if (secondsUntilBreak > 0 && secondsUntilBreak <= 60) {
            for (int trapId : trapIds) {
                var gameObjects = Microbot.getRs2TileObjectCache().query().withId(trapId).toList();
                for (Rs2TileObjectModel gameObject : gameObjects) {
                    WorldPoint location = gameObject.getWorldLocation();
                    if (Rs2Player.getWorldLocation().distanceTo(location) > 5) {
                        continue;
                    }
                    if (!boxTiles.contains(location)) {
                        boxTiles.add(location);
                    }
                }
            }

            for (WorldPoint oldTile : boxTiles) {
                if (Microbot.getRs2TileObjectCache().query().within(oldTile, 0).first() != null) {
                    if (Rs2Player.getWorldLocation().distanceTo(oldTile) > 5) {
                        continue;
                    }
                    while (Microbot.getRs2TileObjectCache().query().within(oldTile, 0).first() != null) {
                        if (Rs2Player.getWorldLocation().distanceTo(oldTile) > 5) {
                            break;
                        }
                        pauseBeforeInput();
                        if (Microbot.getRs2TileObjectCache().query().within(oldTile, 0).interact("Dismantle")) {
                            sleep(1000, 3000);
                            break;
                        }
                        pauseBeforeInput();
                        if (Microbot.getRs2TileObjectCache().query().within(oldTile, 0).interact("Reset")) {
                            sleep(1000, 3000);
                            break;
                        }
                    }
                }
            }
            oneRun = true;
        }

        if (secondsUntilBreak > 60 && oneRun) {
            if (!boxTiles.isEmpty()) {
                for (WorldPoint trapTile : boxTiles) {
                    if (Microbot.getRs2TileObjectCache().query().within(trapTile, 0).first() == null) {
                        if (!Rs2Player.getWorldLocation().equals(trapTile)) {
                            while (!Rs2Player.getWorldLocation().equals(trapTile)) {
                                Microbot.log("Walking to trap tile");
                                pauseBeforeInput();
                                Rs2Walker.walkTo(trapTile, 0);
                                sleep(1000, 3000);
                            }
                        }
                        Microbot.log("Placing trap");
                        int maxTries = 0;
                        while (Microbot.getRs2TileObjectCache().query().within(trapTile, 0).first() == null) {
                            if (Microbot.getRs2TileItemCache().query().withName("Box trap").within(6).count() == 0) {
                                if (Rs2Inventory.contains("Box trap")) {
                                    pauseBeforeInput();
                                    Rs2Inventory.interact("Box trap", "Lay");
                                    sleep(4000, 6000);
                                }
                            } else {
                                pauseBeforeInput();
                                Microbot.getRs2TileItemCache().query().withName("Box trap").within(6).interact("Take");
                                sleep(4000, 6000);
                            }
                            if (maxTries >= 3) {
                                Microbot.log("Failed, placing the trap");
                                break;
                            }
                            maxTries++;
                        }
                    }
                }
            }
            oneRun = false;
        }
    }
}

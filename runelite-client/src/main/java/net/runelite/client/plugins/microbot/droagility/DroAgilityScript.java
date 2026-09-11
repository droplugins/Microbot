package net.runelite.client.plugins.microbot.droagility;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.droagility.courses.DroAgilityCourseHandler;
import net.runelite.client.plugins.microbot.droagility.enums.DroAgilityCourse;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.EventQueue;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DroAgilityScript extends Script
{

    final DroAgilityPlugin plugin;
    final DroAgilityConfig config;

    WorldPoint startPoint = null;
    int lastAgilityXp = 0;
    long lastTimeoutWarning = 0;
    private DroAgilityCourse activeCourse = null;
    private DroAgilityCourseHandler activeHandler = null;
    private static final long MAIN_LOOP_DELAY_MS = 250;
    private static final long MARK_OF_GRACE_SCAN_INTERVAL_MS = 750;
    private static final int MARK_OF_GRACE_SEARCH_DISTANCE = 30;
    private static final int MARK_OF_GRACE_PICKUP_TIMEOUT = 5000;
    private volatile int currentObstacleIndex = -1;
    private WorldPoint pendingMarkOfGraceLocation = null;
    private int pendingMarkOfGraceCount = 0;
    private long pendingMarkOfGraceStartedAt = 0;
    private long lastMarkOfGraceScanAt = 0;
    private WorldPoint alchDecisionObstacleLocation = null;
    private int alchDecisionObstacleId = -1;
    private boolean alchDecisionShouldAlch = false;
    private final DroAgilitySupplyManager supplyManager;
    private volatile boolean shuttingDown = false;
    private volatile boolean sessionInitialized = false;

    // Increased zoom to maximum distance
    private static final int STARTUP_CAMERA_ZOOM = 100;

    // Custom Antiban Timers & Scheduled Rest Constants
    private long nextMisclickTime = 0;
    private long nextShortDelayTime = 0;
    private long nextLongPauseTime = 0;

    private static final long MIN_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(25);
    private static final long MIN_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long MAX_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(7);
    private static final int MIN_RUN_ENABLE_PERCENT = 18;
    private static final int MAX_RUN_ENABLE_PERCENT = 100;
    private static final int MIN_INPUT_DELAY_MS = 67;
    private static final int MAX_INPUT_DELAY_MS = 333;

    private long restUntil = 0L;
    private long nextRestAt = 0L;
    private int runEnablePercent = 50;

    @Inject
    public DroAgilityScript(DroAgilityPlugin plugin, DroAgilityConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        this.supplyManager = new DroAgilitySupplyManager(plugin, config, this::isShuttingDown, this::shutdown);
    }

    @Override
    public void shutdown()
    {
        shuttingDown = true;
        sessionInitialized = false;
        if (activeHandler != null)
        {
            activeHandler.reset();
        }
        activeCourse = null;
        activeHandler = null;
        startPoint = null;
        initialPlayerLocation = null;
        currentObstacleIndex = -1;
        supplyManager.reset();
        clearPendingMarkOfGrace();
        clearAlchDecision();
        restUntil = 0L;
        nextRestAt = Long.MAX_VALUE;

        super.shutdown();
        clearWalkingRouteForShutdown();
    }

    private void clearWalkingRouteForShutdown()
    {
        if (!EventQueue.isDispatchThread())
        {
            Rs2Walker.clearWalkingRoute("droagility:shutdown");
            return;
        }

        Thread cleanupThread = new Thread(() -> Rs2Walker.clearWalkingRoute("droagility:shutdown"), "DroAgilityScript-shutdown-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    public boolean run()
    {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone())
        {
            return true;
        }
        shuttingDown = false;
        sessionInitialized = false;
        Microbot.enableAutoRunOn = true;

        // Configure Microbot Antiban Parameters
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyAgilitySetup();

        // Enforce user-requested behavioral variability
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.naturalMouse = true;

        // Main Loop
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try
            {
                if (!Microbot.isLoggedIn())
                {
                    return;
                }
                if (!super.run())
                {
                    return;
                }
                if (!initializeSession())
                {
                    return;
                }

                // Handle Scheduled Micro-Rests
                if (handleScheduledRest())
                {
                    return;
                }

                DroAgilityCourseHandler courseHandler = getActiveHandler();
                final WorldPoint playerWorldLocation = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    if (Microbot.getClient().getLocalPlayer() == null)
                    {
                        return null;
                    }
                    return Microbot.getClient().getLocalPlayer().getWorldLocation();
                }).orElse(null);
                if (playerWorldLocation == null)
                {
                    return;
                }

                if (startPoint == null)
                {
                    Microbot.log("Early return: Start point is null");
                    Microbot.showMessage("Agility course: " + config.agilityCourse().getTooltip() + " is not supported.");
                    sleep(10000);
                    return;
                }

                boolean hasRequiredLevel = plugin.hasRequiredLevel(courseHandler);
                currentObstacleIndex = courseHandler.getCurrentObstacleIndex();
                DroAgilitySupplyManager.InventorySnapshot inventorySnapshot = supplyManager.createInventorySnapshot();
                if (supplyManager.handleSummerPies(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot))
                {
                    return;
                }

                if (supplyManager.handleFoodOrHealthSafety(inventorySnapshot))
                {
                    return;
                }

                if (supplyManager.handlePreLevelCheck(courseHandler, playerWorldLocation, currentObstacleIndex, hasRequiredLevel, inventorySnapshot))
                {
                    return;
                }

                if (supplyManager.handleBeforeObstacle(courseHandler, playerWorldLocation, currentObstacleIndex, inventorySnapshot))
                {
                    return;
                }

                if (!hasRequiredLevel)
                {
                    if (supplyManager.shouldWalkToCourseStartForSummerPie(courseHandler, playerWorldLocation, currentObstacleIndex)
                            && courseHandler.handleCourseActions(playerWorldLocation))
                    {
                        return;
                    }
                    plugin.notifyUser("Your Agility level is too low for " + config.agilityCourse().getTooltip() + ". Select another course, or enable summer pies if a +5 boost is enough.");
                    shutdown();
                    return;
                }

                if (!courseHandler.hasRequiredCourseItems())
                {
                    Microbot.showMessage(courseHandler.getMissingRequiredCourseItemsMessage());
                    shutdown();
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive)
                {
                    return;
                }
                final int currentAgilityXp = getAgilityXp();

                if (lootMarksOfGrace(courseHandler))
                {
                    return;
                }

                if (courseHandler.handleCourseActions(playerWorldLocation))
                {
                    return;
                }
                final int agilityExp = currentAgilityXp;

                TileObject gameObject = courseHandler.getCurrentObstacle();

                if (gameObject == null)
                {
                    return;
                }

                if (!Rs2Camera.isTileOnScreen(gameObject))
                {
                    Rs2Walker.walkMiniMap(gameObject.getWorldLocation());
                }

                if (!courseHandler.shouldClickObstacle(currentAgilityXp, lastAgilityXp))
                {
                    return;
                }

                if (currentAgilityXp > lastAgilityXp)
                {
                    lastAgilityXp = currentAgilityXp;
                }

                if (shouldPerformAlch(gameObject))
                {
                    Optional<String> alchItem = getAlchItem();
                    if (alchItem.isPresent())
                    {
                        if (config.skipInefficient())
                        {
                            if (gameObject.getWorldLocation().distanceTo(playerWorldLocation) >= 5)
                            {
                                if (config.efficientAlching())
                                {
                                    if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
                                    {
                                        return;
                                    }
                                }
                                else
                                {
                                    if (performNormalAlch(alchItem.get()))
                                    {
                                        return;
                                    }
                                }
                            }
                        }
                        else
                        {
                            if (config.efficientAlching())
                            {
                                if (performEfficientAlch(gameObject, alchItem.get(), agilityExp))
                                {
                                    return;
                                }
                            }
                            if (performNormalAlch(alchItem.get()))
                            {
                                return;
                            }
                        }
                    }
                }

                // Execute Custom Antiban Delays & Misclicks before main interaction
                if (handleCustomAntibanDelays(gameObject)) {
                    return; // Yield tick if a delay or action occurred
                }

                // Normal obstacle interaction
                if (interactWithObstacle(gameObject)) {
                    boolean completed = courseHandler.waitForCompletion(agilityExp, playerWorldLocation.getPlane());

                    if (!completed) {
                        long now = System.currentTimeMillis();
                        if (now - lastTimeoutWarning > 30000) {
                            Microbot.log("Obstacle completion timed out - retrying on next iteration");
                            lastTimeoutWarning = now;
                        }
                        return;
                    }

                    clearAlchDecision();

                    if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                    }
                }
            }
            catch (Exception ex)
            {
                if (isExpectedShutdownInterrupt(ex))
                {
                    return;
                }
                Microbot.log("An error occurred: " + ex.getMessage(), ex);
            }
        }, 0, MAIN_LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean initializeSession()
    {
        if (sessionInitialized)
        {
            return true;
        }
        boolean playerReady = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null).orElse(false);
        if (!playerReady)
        {
            return false;
        }

        Rs2Camera.setZoom(STARTUP_CAMERA_ZOOM);

        int randomPitch = ThreadLocalRandom.current().nextInt(2200, 2850);
        Rs2Camera.setPitch(randomPitch);

        runEnablePercent = ThreadLocalRandom.current().nextInt(MIN_RUN_ENABLE_PERCENT, MAX_RUN_ENABLE_PERCENT + 1);
        nextRestAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);

        // Initialize Custom Antiban Timers
        long now = System.currentTimeMillis();
        nextMisclickTime = now + ThreadLocalRandom.current().nextLong(300_000, 600_000); // 5-10 mins
        nextShortDelayTime = now + ThreadLocalRandom.current().nextLong(180_000, 240_000); // 3-4 mins
        nextLongPauseTime = now + ThreadLocalRandom.current().nextLong(600_000, 900_000); // 10-15 mins

        DroAgilityCourseHandler initialHandler = getActiveHandler();
        if (initialHandler == null)
        {
            return false;
        }
        startPoint = initialHandler.getStartPoint();
        lastAgilityXp = getAgilityXp();
        sessionInitialized = true;
        return true;
    }

    /**
     * Handles scheduled rest periods to mimic human behavior intervals.
     */
    private boolean handleScheduledRest()
    {
        long now = System.currentTimeMillis();
        if (restUntil > now)
        {
            return true;
        }

        if (restUntil != 0L)
        {
            restUntil = 0L;
            nextRestAt = now + ThreadLocalRandom.current().nextLong(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
            Microbot.log("[DroAgilityScript] Rest complete; next rest in " + TimeUnit.MILLISECONDS.toSeconds(nextRestAt - now) + " seconds");
        }

        if (now < nextRestAt
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating())
        {
            return false;
        }

        long duration = ThreadLocalRandom.current().nextLong(MIN_REST_DURATION_MS, MAX_REST_DURATION_MS);
        restUntil = now + duration;
        nextRestAt = Long.MAX_VALUE;
        Microbot.log("[DroAgilityScript] Starting scheduled rest for " + duration + " ms");
        Rs2Antiban.moveMouseOffScreen();
        return true;
    }

    /**
     * Handles random delays and intentional safe misclicks
     * @return true if a delay or action was taken, skipping the current tick
     */
    private boolean handleCustomAntibanDelays(TileObject gameObject)
    {
        long now = System.currentTimeMillis();

        // Long Pause (Every 10-15 mins for 30-50 secs)
        if (now > nextLongPauseTime) {
            int pauseDuration = ThreadLocalRandom.current().nextInt(30_000, 50_000);
            Microbot.log("Antiban: Simulating distraction. Pausing for " + (pauseDuration / 1000) + "s.");
            sleep(pauseDuration);
            nextLongPauseTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(600_000, 900_000);
            return true;
        }

        // Short Delay (Every 3-4 mins for 1-4 secs)
        if (now > nextShortDelayTime) {
            int delayDuration = ThreadLocalRandom.current().nextInt(1_000, 4_000);
            sleep(delayDuration);
            nextShortDelayTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(180_000, 240_000);
            return true;
        }

        // Safe Misclick (Every 5-10 mins)
        if (now > nextMisclickTime) {
            performSafeMisclick(gameObject);
            nextMisclickTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(300_000, 600_000);
        }

        return false;
    }

    /**
     * Simulates a misclick by clicking a valid adjacent tile to the target obstacle
     */
    private void performSafeMisclick(TileObject target)
    {
        WorldPoint targetLoc = target.getWorldLocation();
        if (targetLoc != null) {
            int dx = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            int dy = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            WorldPoint misclickPoint = targetLoc.dx(dx).dy(dy);

            Rs2Walker.walkFastCanvas(misclickPoint);
            sleep(400, 1200);
        }
    }

    private int getAgilityXp()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getSkillExperience(Skill.AGILITY)).orElse(lastAgilityXp);
    }

    public boolean isShuttingDown()
    {
        return shuttingDown;
    }

    public int getCurrentObstacleIndex()
    {
        return currentObstacleIndex;
    }

    private boolean isExpectedShutdownInterrupt(Exception ex)
    {
        if (!shuttingDown)
        {
            return false;
        }

        if (Thread.currentThread().isInterrupted())
        {
            return true;
        }

        Throwable current = ex;
        while (current != null)
        {
            if (current instanceof InterruptedException)
            {
                return true;
            }
            if (current.getMessage() != null && current.getMessage().contains("Interrupted waiting for client thread"))
            {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private Optional<String> getAlchItem()
    {
        String itemsInput = config.itemsToAlch().trim();
        if (itemsInput.isEmpty())
        {
            return Optional.empty();
        }

        List<String> itemsToAlch = Arrays.stream(itemsInput.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (itemsToAlch.isEmpty())
        {
            return Optional.empty();
        }

        for (String itemName : itemsToAlch)
        {
            if (Rs2Inventory.hasItem(itemName))
            {
                return Optional.of(itemName);
            }
        }

        return Optional.empty();
    }

    private DroAgilityCourseHandler getActiveHandler()
    {
        DroAgilityCourse selectedCourse = config.agilityCourse();
        if (activeHandler == null || activeCourse != selectedCourse)
        {
            if (activeHandler != null)
            {
                activeHandler.reset();
            }

            activeCourse = selectedCourse;
            activeHandler = selectedCourse.getHandler();
            activeHandler.reset();
            startPoint = activeHandler.getStartPoint();
            lastAgilityXp = getAgilityXp();
            currentObstacleIndex = -1;
            supplyManager.reset();
            clearAlchDecision();
        }
        return activeHandler;
    }

    private boolean lootMarksOfGrace(DroAgilityCourseHandler courseHandler)
    {
        if (shuttingDown)
        {
            clearPendingMarkOfGrace();
            return false;
        }

        if (pendingMarkOfGraceLocation != null)
        {
            if (markPickupResolved() || System.currentTimeMillis() - pendingMarkOfGraceStartedAt > MARK_OF_GRACE_PICKUP_TIMEOUT)
            {
                clearPendingMarkOfGrace();
            }
            else if (Rs2Player.isMoving() || Rs2Player.isAnimating())
            {
                return true;
            }
            else
            {
                clearPendingMarkOfGrace();
            }
        }

        if (Rs2Inventory.isFull() && !Rs2Inventory.contains(ItemID.GRACE))
        {
            return false;
        }

        WorldPoint playerLocation = courseHandler.getPlayerWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastMarkOfGraceScanAt < MARK_OF_GRACE_SCAN_INTERVAL_MS)
        {
            return false;
        }
        lastMarkOfGraceScanAt = now;

        Rs2TileItemModel markOfGrace = Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .withId(ItemID.GRACE)
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> item.getWorldLocation() != null && item.getWorldLocation().getPlane() == playerLocation.getPlane())
                .where(item -> item.getWorldLocation().distanceTo(playerLocation) <= MARK_OF_GRACE_SEARCH_DISTANCE)
                .where(item -> Rs2Walker.canReach(item.getWorldLocation()))
                .toList()
                .stream()
                .min(Comparator.comparingInt(item -> item.getWorldLocation().distanceTo(playerLocation)))
                .orElse(null);

        if (markOfGrace == null)
        {
            return false;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating())
        {
            return true;
        }

        WorldPoint markLocation = markOfGrace.getWorldLocation();
        var markLocalLocation = markOfGrace.getLocalLocation();
        if (markLocation == null || markLocalLocation == null)
        {
            return false;
        }

        if (!Rs2Camera.isTileOnScreen(markLocalLocation))
        {
            Rs2Camera.turnTo(markLocalLocation);
            sleep(300, 600);
            return true;
        }

        int markCount = Rs2Inventory.itemQuantity(ItemID.GRACE);
        if (!pickupMarkOfGrace(markOfGrace))
        {
            return false;
        }
        pendingMarkOfGraceLocation = markLocation;
        pendingMarkOfGraceCount = markCount;
        pendingMarkOfGraceStartedAt = System.currentTimeMillis();

        sleepUntil(() -> shuttingDown || markPickupResolved() || Rs2Player.isMoving(), 1800);
        if (markPickupResolved() || shuttingDown)
        {
            clearPendingMarkOfGrace();
        }
        return true;
    }

    private boolean markPickupResolved()
    {
        return pendingMarkOfGraceLocation != null
                && (Rs2Inventory.itemQuantity(ItemID.GRACE) > pendingMarkOfGraceCount
                || !hasLootableMarkAt(pendingMarkOfGraceLocation));
    }

    private void clearPendingMarkOfGrace()
    {
        pendingMarkOfGraceLocation = null;
        pendingMarkOfGraceCount = 0;
        pendingMarkOfGraceStartedAt = 0;
        lastMarkOfGraceScanAt = 0;
    }

    private boolean hasLootableMarkAt(WorldPoint markLocation)
    {
        return Microbot.getRs2TileItemCache().query()
                .fromWorldView()
                .withId(ItemID.GRACE)
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> markLocation.equals(item.getWorldLocation()))
                .first() != null;
    }

    private boolean pickupMarkOfGrace(Rs2TileItemModel markOfGrace)
    {
        try
        {
            if (markOfGrace == null || markOfGrace.getLocalLocation() == null)
            {
                return false;
            }
            return markOfGrace.click("Take");
        }
        catch (Exception ex)
        {
            Microbot.log("Failed to pick up Mark of grace: " + ex.getMessage());
            return false;
        }
    }

    private boolean shouldPerformAlch(TileObject gameObject)
    {
        if (!config.alchemy())
        {
            clearAlchDecision();
            return false;
        }

        WorldPoint obstacleLocation = gameObject.getWorldLocation();
        if (gameObject.getId() == alchDecisionObstacleId && obstacleLocation.equals(alchDecisionObstacleLocation))
        {
            return alchDecisionShouldAlch;
        }

        alchDecisionObstacleId = gameObject.getId();
        alchDecisionObstacleLocation = obstacleLocation;
        alchDecisionShouldAlch = ThreadLocalRandom.current().nextInt(100) >= config.alchSkipChance();
        return alchDecisionShouldAlch;
    }

    private boolean performEfficientAlch(TileObject gameObject, String alchItem, int agilityExp)
    {
        WorldPoint playerLocation = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null)
            {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getWorldLocation();
        }).orElse(null);
        if (playerLocation == null)
        {
            return false;
        }

        if (gameObject.getWorldLocation().distanceTo(playerLocation) >= 5)
        {
            if (interactWithObstacle(gameObject))
            {
                sleep(100, 200);
                Rs2Magic.alch(alchItem, 50, 75);
                interactWithObstacle(gameObject);
                boolean completed = getActiveHandler().waitForCompletion(agilityExp, playerLocation.getPlane());

                if (!completed) {
                    long now = System.currentTimeMillis();
                    if (now - lastTimeoutWarning > 30000) {
                        Microbot.log("Obstacle completion timed out during efficient alching");
                        lastTimeoutWarning = now;
                    }
                    return false;
                }

                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
                lastAgilityXp = getAgilityXp();
                clearAlchDecision();
                return true;
            }
        }
        return false;
    }

    private boolean performNormalAlch(String alchItem)
    {
        int initialCount = Rs2Inventory.itemQuantity(alchItem);
        Rs2Magic.alch(alchItem, 50, 75);
        sleepUntil(() -> shuttingDown || Rs2Inventory.itemQuantity(alchItem) < initialCount, 1200);
        alchDecisionShouldAlch = false;
        return true;
    }

    private void clearAlchDecision()
    {
        alchDecisionObstacleLocation = null;
        alchDecisionObstacleId = -1;
        alchDecisionShouldAlch = false;
    }

    private boolean interactWithObstacle(TileObject gameObject)
    {
        return Rs2GameObject.interact(gameObject);
    }
}
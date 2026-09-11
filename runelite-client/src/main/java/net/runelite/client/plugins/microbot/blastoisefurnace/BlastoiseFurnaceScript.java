package net.runelite.client.plugins.microbot.blastoisefurnace;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.Bars;
import net.runelite.client.plugins.microbot.blastoisefurnace.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.ObjectID.*;
import static net.runelite.api.gameval.VarbitID.*;

@Slf4j
public class BlastoiseFurnaceScript extends Script {
    private static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");
    static final int coalBag = 12019;
    private static final int MAX_ORE_PER_INTERACTION = 27;
    private static final int MAX_ORE_PER_HYBRID_INTERACTION = 26;
    private static final int STARTUP_CAMERA_ZOOM = 100;
    private static final long MIN_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(25);
    private static final long MIN_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(1);
    private static final long MAX_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(7);
    private static final long MIN_AMBIENT_ACTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long MAX_AMBIENT_ACTION_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int MIN_CAMERA_YAW_PERCENT = 3;
    private static final int MAX_CAMERA_YAW_PERCENT = 5;
    private static final int CAMERA_YAW_UNITS = 2048;
    private static final int MIN_RUN_ENABLE_PERCENT = 18;
    private static final int MAX_RUN_ENABLE_PERCENT = 100;
    private static final int MIN_STAMINA_DRINK_PERCENT = 30;
    private static final int MAX_STAMINA_DRINK_PERCENT = 70;
    private static final int MIN_INPUT_DELAY_MS = 25;
    private static final int MAX_INPUT_DELAY_MS = 200;
    private static final List<String> STAMINA_POTION_VARIANTS = Arrays.asList(
            "Extended stamina potion",
            "Stamina potion"
    );
    private static final int[][] MISCLICK_OFFSETS = {
            {-1, -1}, {0, -1}, {1, -1}, {-1, 0},
            {1, 0}, {-1, 1}, {0, 1}, {1, 1}
    };
    public static State state = State.BANKING;
    static boolean coalBagEmpty;
    static boolean primaryOreEmpty;
    static boolean secondaryOreEmpty;
    private boolean timerStarted = false;
    private volatile boolean timeIsUp;
    private boolean init = false;
    private volatile boolean sessionInitialized;
    private long nextRestAt = Long.MAX_VALUE;
    private long restUntil;
    private long nextMisclickAt = Long.MAX_VALUE;
    private long nextCameraYawAt = Long.MAX_VALUE;
    private int runEnablePercent = MIN_RUN_ENABLE_PERCENT;
    private int staminaDrinkPercent = MIN_STAMINA_DRINK_PERCENT;

    private final BlastoiseFurnacePlugin plugin;
    private final BlastoiseFurnaceConfig config;

    private boolean hasRequiredOresForSmithing() {
        int primaryOre = config.getBars().getPrimaryOre();
        int secondaryOre = config.getBars().getSecondaryOre() == null ? -1 : config.getBars().getSecondaryOre();
        boolean hasPrimaryOre = Rs2Bank.hasItem(primaryOre);
        boolean hasSecondaryOre = secondaryOre != -1 && Rs2Bank.hasItem(secondaryOre);
        return hasPrimaryOre && hasSecondaryOre;
    }

    @Inject
    public BlastoiseFurnaceScript(BlastoiseFurnacePlugin plugin, BlastoiseFurnaceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        state = State.BANKING;
        primaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getPrimaryOre());
        secondaryOreEmpty = !Rs2Inventory.hasItem(config.getBars().getSecondaryOre());
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (!super.run()) {
                    return;
                }

                if (!initializeSession()) {
                    return;
                }

                // Keep this ahead of all furnace actions so a due rest has priority.
                if (handleScheduledRest()) {
                    return;
                }

                if (handleScheduledMisclick() || handleScheduledCameraYaw()) {
                    return;
                }

                enableRunWhenReady();

                if (!Rs2GameObject.exists(BLAST_FURNACE_DISPENSER)) {
                    if (Rs2Player.isAnimating()) {
                        return;
                    }
                    pauseBeforeInput();
                    Rs2Walker.walkTo(new WorldPoint(2931, 10197, 0));
                    pauseBeforeInput();
                    Microbot.getRs2TileObjectCache().query().interact(DWARF_KELDAGRIM_FACTORY_STAIRS);
                    return;
                }

                if (!init) {
                    int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
                    int req = evaluateCofferDeposit();
                    if (inCoffer >= req) {
                        init = true;
                        return;
                    }
                    checkAndTopOffCoffer();
                    init = true;
                    return;
                }

                if (!fullCoffer()) {
                    checkAndTopOffCoffer();
                }

                switch (state) {
                    case BANKING:
                        Microbot.status = "Banking";
                        if (!Rs2Bank.isOpen()) {
                            pauseBeforeInput();
                            Rs2Bank.openBank();
                            sleepUntil(Rs2Bank::isOpen, 20000);
                        }

                        if (config.getBars().isRequiresCoalBag() && !Rs2Inventory.contains(coalBag)) {
                            if (!Rs2Bank.hasItem(coalBag)) {
                                Microbot.showMessage("No coal bag found in inventory and bank.");
                                Microbot.stopPlugin(plugin);
                                return;
                            }
                            pauseBeforeInput();
                            Rs2Bank.withdrawItem(coalBag);
                        }

                        if (config.getBars().isRequiresGoldsmithGloves()) {
                            if (!ensureGoldsmithGauntletsEquipped()) {
                                return;
                            }
                        } else {
                            pauseBeforeInput();
                            Rs2Bank.depositAll(GAUNTLETS_OF_GOLDSMITHING);
                        }

                        if (Rs2Inventory.hasItem("bar")) {
                            pauseBeforeInput();
                            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                            if (Rs2Inventory.isFull()) {
                                // Wait so our player avoids drinking potions with a full inventory
                                if (!Rs2Inventory.waitForInventoryChanges(1800)) {
                                    sleepUntil(() -> !Rs2Inventory.isFull(), 1200);
                                }
                            }
                        }

                        if (!hasRequiredOresForSmithing()) {
                            log.warn("Out of ores. Walking you out for coffer safety");
                            pauseBeforeInput();
                            Rs2Walker.walkTo(new WorldPoint(2930, 10196, 0));
                            pauseBeforeInput();
                            Rs2Player.logout();
                            Microbot.stopPlugin(plugin);
                        }

                        if (Rs2Player.getRunEnergy() <= staminaDrinkPercent) {
                            useStaminaPotions();
                        }

                        // Check here if dispenser contains bars. If so we need to clean-up
                        if (dispenserContainsBars()) {
                            pauseBeforeInput();
                            Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
                            handleDispenserLooting();
                            return;
                        } else {
                            retrieveItemsForCurrentFurnaceInteraction();
                            state = State.SMITHING;
                        }
                        break;
                    case SMITHING:
                        log.info("clicking conveyor");
                        if (barsInDispenser(config.getBars()) > 0) {
                            handleDispenserLooting();
                        }

                        state = State.BANKING;
                        break;
                }
            } catch (Exception ex) {
                log.trace("Error in main loop: {} - ", ex.getMessage(), ex);
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
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

        pauseBeforeInput();
        Rs2Camera.setZoom(STARTUP_CAMERA_ZOOM);
        pauseBeforeInput();
        Rs2Camera.setPitch(ThreadLocalRandom.current().nextInt(2200, 2850));
        runEnablePercent = ThreadLocalRandom.current()
                .nextInt(MIN_RUN_ENABLE_PERCENT, MAX_RUN_ENABLE_PERCENT + 1);
        staminaDrinkPercent = ThreadLocalRandom.current()
                .nextInt(MIN_STAMINA_DRINK_PERCENT, MAX_STAMINA_DRINK_PERCENT + 1);
        long now = System.currentTimeMillis();
        nextRestAt = now + randomBetween(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
        nextMisclickAt = now + randomBetween(MIN_AMBIENT_ACTION_INTERVAL_MS, MAX_AMBIENT_ACTION_INTERVAL_MS);
        nextCameraYawAt = now + randomBetween(MIN_AMBIENT_ACTION_INTERVAL_MS, MAX_AMBIENT_ACTION_INTERVAL_MS);
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
        if (now < nextMisclickAt || !isSafeForMisclick()) {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            nextMisclickAt = now + TimeUnit.SECONDS.toMillis(30);
            return false;
        }

        int startDirection = ThreadLocalRandom.current().nextInt(8);
        for (int i = 0; i < 8; i++) {
            int direction = (startDirection + i) % 8;
            int dx = MISCLICK_OFFSETS[direction][0];
            int dy = MISCLICK_OFFSETS[direction][1];

            WorldPoint misclickPoint = playerLocation.dx(dx).dy(dy);
            if (!Rs2Walker.isWalkableInCollisionMap(misclickPoint)) {
                continue;
            }

            pauseBeforeInput();
            if (Rs2Walker.walkFastCanvas(misclickPoint, Rs2Player.isRunEnabled())) {
                nextMisclickAt = System.currentTimeMillis()
                        + randomBetween(MIN_AMBIENT_ACTION_INTERVAL_MS, MAX_AMBIENT_ACTION_INTERVAL_MS);
                sleep(400, 1200);
                return true;
            }
        }

        nextMisclickAt = now + TimeUnit.SECONDS.toMillis(30);
        return false;
    }

    private boolean handleScheduledCameraYaw() {
        long now = System.currentTimeMillis();
        if (now < nextCameraYawAt || !isSafeForCameraYaw()) {
            return false;
        }

        int percent = ThreadLocalRandom.current()
                .nextInt(MIN_CAMERA_YAW_PERCENT, MAX_CAMERA_YAW_PERCENT + 1);
        int delta = (int) Math.round(CAMERA_YAW_UNITS * (percent / 100.0));
        if (ThreadLocalRandom.current().nextBoolean()) {
            delta = -delta;
        }

        int currentYaw = Microbot.getClientThread()
                .runOnClientThreadOptional(Rs2Camera::getYaw)
                .orElse(0);
        int targetYaw = Math.floorMod(currentYaw + delta, CAMERA_YAW_UNITS);
        pauseBeforeInput();
        Rs2Camera.setYaw(targetYaw);
        nextCameraYawAt = System.currentTimeMillis()
                + randomBetween(MIN_AMBIENT_ACTION_INTERVAL_MS, MAX_AMBIENT_ACTION_INTERVAL_MS);
        return true;
    }

    private boolean isSafeForMisclick() {
        return !Rs2Bank.isOpen()
                && !Rs2Dialogue.isInDialogue()
                && !Rs2Player.isAnimating();
    }

    private boolean isSafeForCameraYaw() {
        return !Rs2Bank.isOpen() && !Rs2Dialogue.isInDialogue();
    }

    private long randomBetween(long minimum, long maximum) {
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
    }

    private void pauseBeforeInput() {
        sleep(ThreadLocalRandom.current().nextInt(MIN_INPUT_DELAY_MS, MAX_INPUT_DELAY_MS + 1));
    }

    private void enableRunWhenReady() {
        if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() >= runEnablePercent) {
            pauseBeforeInput();
            Rs2Player.toggleRunEnergy(true);
        }
    }

    private void handleTax() {
        log.info("Paying noob smithing tax");
        if (!Rs2Bank.isOpen()) {
            pauseBeforeInput();
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
        }
        pauseBeforeInput();
        Rs2Bank.depositOne(config.getBars().getPrimaryOre());
        sleep(500, 1200);
        pauseBeforeInput();
        Rs2Bank.depositOne(COAL);
        sleep(500, 1200);
        pauseBeforeInput();
        Rs2Bank.withdrawX(COINS, 2500);
        sleep(500, 1200);
        pauseBeforeInput();
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        var blastie = Microbot.getRs2NpcCache().query().withName("Blast Furnace Foreman").nearestOnClientThread();
        if (blastie != null) {
            pauseBeforeInput();
            blastie.click("Pay");
        }
        sleepUntil(Rs2Dialogue::isInDialogue, 10000);
        if (Rs2Dialogue.hasSelectAnOption()) {
            pauseBeforeInput();
            Rs2Dialogue.clickOption("Yes");
            sleep(1000, 1850);
            pauseBeforeInput();
            Rs2Dialogue.clickContinue();
            sleepUntil(()-> !Rs2Dialogue.isInDialogue(), Rs2Random.between(750,1500));
            if(!Rs2Dialogue.isInDialogue()){
                setTenMinuteTimer();
            }
        }
    }

    private void handleDispenserLooting() {
        if (!Rs2Inventory.isFull()) {
            if (!dispenserContainsBars()) {
                sleepUntil(this::dispenserContainsBars, Rs2Random.between(3000, 5000));
            }

            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = equipIceGloves();
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    pauseBeforeInput();
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }
            }

            pauseBeforeInput();
            Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_DISPENSER, "Take");

            sleepUntil(() ->
                    Rs2Widget.hasWidget("What would you like to take?") ||
                            Rs2Widget.hasWidget("How many would you like") ||
                            Rs2Widget.hasWidget("The bars are still molten!"), 5000);

            sleepUntil(() ->
                    Rs2Widget.hasWidget("What would you like to take?") ||
                            Rs2Widget.hasWidget("How many would you like"), 3000);

            boolean multipleBarTypes = Rs2Widget.hasWidget("What would you like to take?");
            boolean canLootBar = Rs2Widget.hasWidget("How many would you like");

            if (super.run()) {
                if (canLootBar || multipleBarTypes) {
                    pauseBeforeInput();
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                }
                Rs2Inventory.waitForInventoryChanges(5000);
                pauseBeforeInput();
                Rs2Bank.openBank();
                equipGoldSmithGauntlets();
            }
        }

        state = State.BANKING;
    }

    private void retrievePrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            pauseBeforeInput();
            Rs2Bank.withdrawAll(ore);
            return;
        }
        doOreRun(false, false);
    }

    private void retrieveDoubleCoal() {
        if (!Rs2Inventory.hasItem(COAL)) {
            pauseBeforeInput();
            Rs2Bank.withdrawAll(COAL);
            return;
        }
        pauseBeforeInput();
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;
        depositOre();
    }

    private void retrieveCoalAndPrimary() {
        int ore = config.getBars().getPrimaryOre();
        if (!Rs2Inventory.hasItem(ore)) {
            pauseBeforeInput();
            Rs2Bank.withdrawAll(ore);
            sleep(500, 1200);
            return;
        }
        pauseBeforeInput();
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;

        sleep(500, 1200);
        pauseBeforeInput();
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        depositOre();
        doOreRun(false, false);
    }

    private void retrieveCoalAndGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            pauseBeforeInput();
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        pauseBeforeInput();
        if (!Rs2Inventory.interact(coalBag, "Fill"))
            return;

        sleep(500, 1200);
        pauseBeforeInput();
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        depositOre();
        doOreRun(true, true);
    }

    private void retrieveGold() {
        if (!Rs2Inventory.hasItem(GOLD_ORE)) {
            pauseBeforeInput();
            Rs2Bank.withdrawAll(GOLD_ORE);
            return;
        }
        depositOre();
        doOreRun(true, true);
    }

    private void doOreRun(boolean useIceGloves, boolean waitInventoryChange) {
        pauseBeforeInput();
        Rs2Walker.walkFastCanvas(new WorldPoint(1940, 4962, 0));
        sleep(3400);
        sleepUntil(() -> barsInDispenser(config.getBars()) > 0, 10000);

        if (useIceGloves) {
            if (!Rs2Equipment.isWearing(ICE_GLOVES) && !Rs2Equipment.isWearing(SMITHING_UNIFORM_GLOVES_ICE)) {
                boolean equipped = equipIceGloves();
                if (!equipped) {
                    Microbot.showMessage("Ice gloves or smith gloves required to loot the hot bars.");
                    pauseBeforeInput();
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }
            }
            if (waitInventoryChange)
                Rs2Inventory.waitForInventoryChanges(2000);
        } else {
            sleep(400, 700);
        }
    }

    private void retrieveItemsForCurrentFurnaceInteraction() {
        final Bars bar = config.getBars();
        final int coal = Microbot.getVarbitValue(
                bar == Bars.GOLD_BAR ? BLAST_FURNACE_GOLD_ORE : BLAST_FURNACE_COAL
        );
        final int divisor = isHybrid(bar) ? MAX_ORE_PER_HYBRID_INTERACTION : MAX_ORE_PER_INTERACTION;
        final int batch = coal / divisor;

        switch (bar) {
            case GOLD_BAR:
                retrieveGold();
                break;

            case STEEL_BAR:
            case MITHRIL_BAR:
                dispatchStandard(batch, 0, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case ADAMANTITE_BAR:
            case RUNITE_BAR:
                dispatchStandard(batch, 2, retrieveDoubleCoal, retrieveCoalAndPrimary, retrievePrimary);
                break;

            case HYBRID_MITHRIL_BAR:
                dispatchHybrid(batch, 0, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_ADAMANTITE_BAR:
                dispatchHybrid(batch, 2, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            case HYBRID_RUNITE_BAR:
                dispatchHybrid(batch, 3, retrieveCoalAndGold, retrieveCoalAndPrimary);
                break;

            default:
                assert false : "unhandled bar type: " + bar;
        }
    }

    private boolean isHybrid(Bars bar) {
        return bar.name().startsWith("HYBRID");
    }

    private final Runnable retrievePrimary = this::retrievePrimary;
    private final Runnable retrieveCoalAndPrimary = this::retrieveCoalAndPrimary;
    private final Runnable retrieveCoalAndGold = this::retrieveCoalAndGold;
    private final Runnable retrieveDoubleCoal = this::retrieveDoubleCoal;

    private void dispatchStandard(int batch, int doubleCoalMax, Runnable doubleCoal, Runnable coalAndPrimary, Runnable primary) {
        if (batch <= doubleCoalMax) {
            doubleCoal.run();
        } else if (batch <= 6) {
            coalAndPrimary.run();
        } else {
            primary.run();
        }
    }

    private void dispatchHybrid(int batch, int goldThreshold, Runnable coalAndGold, Runnable coalAndPrimary) {
        if (batch <= goldThreshold) {
            coalAndGold.run();
        } else {
            coalAndPrimary.run();
        }
    }

    private void useStaminaPotions() {
        if (!Rs2Bank.isOpen()) return;

        boolean hasStaminaPotion = Rs2Bank.hasItem(STAMINA_POTION_VARIANTS);
        boolean hasEnergyPotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());


        if (!Rs2Player.hasStaminaBuffActive() && hasStaminaPotion) {
            String potionName = getLowestDosePotionName(STAMINA_POTION_VARIANTS);
            if (potionName != null) {
                withdrawAndDrink(potionName);
                return;
            }
        }
        if (hasEnergyPotion) {
            String potionName = getLowestDosePotionName(Rs2Potion.getRestoreEnergyPotionsVariants());
            if (potionName != null) {
                withdrawAndDrink(potionName);
            }
        }
    }

    private String getLowestDosePotionName(List<String> variants) {
        return Rs2Bank.getAll(item -> variants.stream().anyMatch(variant -> item.getName().toLowerCase().contains(variant.toLowerCase())))
                .min(Comparator.comparingInt(item -> getDoseFromName(item.getName())))
                .map(Rs2ItemModel::getName)
                .orElse(null);
    }

    private int getDoseFromName(String potionItemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(potionItemName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(2));
        }
        return 0;
    }

    private String getBaseName(String itemName) {
        Matcher matcher = ITEM_NAME_SUFFIX_PATTERN.matcher(itemName);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return itemName;
    }

    private void withdrawAndDrink(String potionItemName) {
        String baseName = getBaseName(potionItemName);
        pauseBeforeInput();
        boolean withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
        if (!withdrewPotion) {
            if (Rs2Inventory.isFull()) {
                // Wait a full tick for safety
                if (!Rs2Inventory.waitForInventoryChanges(600) && Rs2Inventory.isFull()) {
                    log.debug("Inventory remained full while attempting to withdraw {}", potionItemName);
                    return;
                }
                pauseBeforeInput();
                withdrewPotion = Rs2Bank.withdrawOne(potionItemName);
            }
            if (!withdrewPotion) {
                log.debug("Failed to withdraw potion {} from the bank", potionItemName);
                return;
            }
        }
        Rs2Inventory.waitForInventoryChanges(1800);
        pauseBeforeInput();
        Rs2Inventory.interact(potionItemName, "drink");
        Rs2Inventory.waitForInventoryChanges(1800);
        if (Rs2Inventory.hasItem(baseName)) {
            pauseBeforeInput();
            Rs2Bank.depositOne(baseName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
        if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY)) {
            pauseBeforeInput();
            Rs2Bank.depositOne(ItemID.VIAL_EMPTY);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }

    private int getInventoryOreCount() {
        return Rs2Inventory.count("ore") + Rs2Inventory.count("coal");
    }

    private boolean putOreOnConveyorBelt() {
        final int oreCount = getInventoryOreCount();
        if (oreCount <= 0) {
            log.error("No ore in Inventory");
            return false;
        }

        if (config.getBars().isRequiresGoldsmithGloves() && !ensureGoldsmithGauntletsEquipped()) {
            log.warn("Goldsmith gauntlets are not equipped; skipping conveyor interaction");
            return false;
        }

        if (!closeBankBeforeConveyor()) {
            return false;
        }

        pauseBeforeInput();
        if (!Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, "Put-ore-on")) {
            log.error("Failed to interact with conveyor belt");
            return false;
        }
        sleepUntil(() -> Rs2Dialogue.isInDialogue() || getInventoryOreCount() < oreCount, 10_000);
        if (Rs2Widget.hasWidget("You must ask the foreman's")) {
            log.info("Need to pay the noob tax");
            if(timerStarted && !timeIsUp){
                return putOreOnConveyorBelt();
            }

            handleTax();
            return putOreOnConveyorBelt();
        }
        return true;
    }

    private boolean closeBankBeforeConveyor() {
        if (!Rs2Bank.isOpen()) {
            return true;
        }

        // Escape is the fastest path. If that setting is disabled, use the bank's X button.
        pauseBeforeInput();
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleepUntil(() -> !Rs2Bank.isOpen(), 600);
        if (!Rs2Bank.isOpen()) {
            return true;
        }

        pauseBeforeInput();
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
        if (Rs2Bank.isOpen()) {
            log.warn("Bank remained open; skipping conveyor interaction");
            return false;
        }
        return true;
    }

    public void setTenMinuteTimer(){
        if(timerStarted) return;

        Timer timer = new Timer();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Microbot.log("Time's up! 10 minutes have passed.");
                timeIsUp = true;
                timerStarted = false;
                timer.cancel();
            }
        };

        long delay = 10 * 60 * 1000; // 10 minutes in ms
        Microbot.log("Timer started for 10 minutes...");

        timer.schedule(task, delay);

        timerStarted = true;
    }

    private void depositOre() {
        if (!putOreOnConveyorBelt()) {
            return;
        }
        if (config.getBars().isRequiresCoalBag()) {
            pauseBeforeInput();
            if (Rs2Inventory.interact(coalBag, "Empty")) Rs2Inventory.waitForInventoryChanges(3_000);
            else log.error("Failed to empty coal bag 1");
            if (!putOreOnConveyorBelt()) {
                return;
            }
        }
        if (config.getBars().isRequiresCoalBag() &&
                (Rs2Inventory.hasItem(SMITHING_UNIFORM_GLOVES_ICE)
                        || Rs2Inventory.hasItem(GAUNTLETS_OF_GOLDSMITHING)
                        || Rs2Inventory.hasItem(ICE_GLOVES))) {
            pauseBeforeInput();
            if (Rs2Inventory.interact(coalBag, "Empty")) Rs2Inventory.waitForInventoryChanges(3_000);
            else log.error("Failed to empty coal bag 2");
            putOreOnConveyorBelt();
        }
    }


    public int barsInDispenser(Bars bar) {
        switch (bar) {
            case GOLD_BAR:
                return getBars(BLAST_FURNACE_GOLD_BARS);
            case STEEL_BAR:
                return getBars(BLAST_FURNACE_STEEL_BARS);
            case MITHRIL_BAR:
                return getBars(BLAST_FURNACE_MITHRIL_BARS);
            case ADAMANTITE_BAR:
                return getBars(BLAST_FURNACE_ADAMANTITE_BARS);
            case RUNITE_BAR:
                return getBars(BLAST_FURNACE_RUNITE_BARS);
            case HYBRID_MITHRIL_BAR:
                return fallbackBar(
                        BLAST_FURNACE_MITHRIL_BARS
                );
            case HYBRID_ADAMANTITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_ADAMANTITE_BARS
                );
            case HYBRID_RUNITE_BAR:
                return fallbackBar(
                        BLAST_FURNACE_RUNITE_BARS
                );
            default:
                return -1;
        }
    }

    private int fallbackBar(int primary) {
        int value = Microbot.getVarbitValue(primary);
        return value > 0 ? value : Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.BLAST_FURNACE_GOLD_BARS);
    }

    private int getBars(int varbitId) {
        return Microbot.getVarbitValue(varbitId);
    }

    public boolean dispenserContainsBars() {
        return Arrays.stream(new int[]{
                BLAST_FURNACE_IRON_BARS,
                BLAST_FURNACE_STEEL_BARS,
                BLAST_FURNACE_GOLD_BARS,
                BLAST_FURNACE_MITHRIL_BARS,
                BLAST_FURNACE_ADAMANTITE_BARS,
                BLAST_FURNACE_RUNITE_BARS
        }).anyMatch(id -> Microbot.getVarbitValue(id) > 0);
    }

    private void equipGoldSmithGauntlets() {
        if (config.getBars().isRequiresGoldsmithGloves()) {
            ensureGoldsmithGauntletsEquipped();
        }
    }

    private boolean ensureGoldsmithGauntletsEquipped() {
        if (Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING)) {
            return true;
        }

        if (!Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING)) {
            if (!Rs2Bank.isOpen()) {
                log.warn("Goldsmith gauntlets are not available in inventory while the bank is closed");
                return false;
            }
            if (!Rs2Bank.hasItem(GAUNTLETS_OF_GOLDSMITHING)) {
                Microbot.showMessage("No goldsmith gauntlets found.");
                Microbot.stopPlugin(plugin);
                return false;
            }

            pauseBeforeInput();
            if (!Rs2Bank.withdrawItem(GAUNTLETS_OF_GOLDSMITHING)) {
                log.warn("Failed to withdraw goldsmith gauntlets");
                return false;
            }
            Rs2Inventory.waitForInventoryChanges(1800);
        }

        if (!Rs2Inventory.contains(GAUNTLETS_OF_GOLDSMITHING)) {
            log.warn("Goldsmith gauntlets were not observed in inventory after withdrawal");
            return false;
        }

        pauseBeforeInput();
        if (!Rs2Inventory.interact(GAUNTLETS_OF_GOLDSMITHING, "Wear")) {
            log.warn("Failed to interact with goldsmith gauntlets");
            return false;
        }
        sleepUntil(() -> Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING), 1800);
        return Rs2Equipment.isWearing(GAUNTLETS_OF_GOLDSMITHING);
    }

    private boolean equipIceGloves() {
        pauseBeforeInput();
        if (Rs2Inventory.interact(ICE_GLOVES, "Wear")) {
            return true;
        }
        pauseBeforeInput();
        return Rs2Inventory.interact(SMITHING_UNIFORM_GLOVES_ICE, "Wear");
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.devDebug = true;
    }

    public void shutdown() {
        init = false;
        sessionInitialized = false;
        nextRestAt = Long.MAX_VALUE;
        restUntil = 0L;
        nextMisclickAt = Long.MAX_VALUE;
        nextCameraYawAt = Long.MAX_VALUE;
        state = State.BANKING;
        primaryOreEmpty = false;
        secondaryOreEmpty = false;
        super.shutdown();
    }

    public int evaluateCofferDeposit() {
        if (BreakHandlerScript.breakIn > 0) {
            return BreakHandlerScript.breakIn * 20;
        }

        return 72000;
    }

    public void checkAndTopOffCoffer() {
        int lvl = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        if (lvl < 60) return;

        int inCoffer = Microbot.getVarbitValue(BLAST_FURNACE_COFFER);
        int required = evaluateCofferDeposit();
        int delta = required - inCoffer;

        if (delta == 0) return;

        boolean underfilled = delta > 0;
        int amount = Math.abs(delta);

        if (Rs2Inventory.isFull()) {
            if (!Rs2Bank.isOpen()) {
                pauseBeforeInput();
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                pauseBeforeInput();
                Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            }
        }

        if (underfilled) {
            if (!Rs2Bank.isOpen()) {
                pauseBeforeInput();
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
            }

            if (!Rs2Bank.hasBankItem(COINS, amount)) {
                Microbot.stopPlugin(plugin);
                return;
            }

            pauseBeforeInput();
            Rs2Bank.withdrawX(COINS, amount);
            sleep(600, 900);
            pauseBeforeInput();
            Rs2Bank.closeBank();
            sleepUntil(() -> !Rs2Bank.isOpen());
        }

        pauseBeforeInput();
        Microbot.getRs2TileObjectCache().query().interact(BLAST_FURNACE_AUTOMATA_COFFER, "use");
        Rs2Player.waitForWalking(2400);

        if (underfilled && Rs2Dialogue.hasDialogueOption("deposit", false)) {
            pauseBeforeInput();
            Rs2Widget.clickWidget("deposit");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Deposit how much?"), 2400);
            pauseBeforeInput();
            Rs2Keyboard.typeString(String.valueOf(amount));
            pauseBeforeInput();
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
        } else if (!underfilled && Rs2Dialogue.hasDialogueOption("withdraw", false)) {
            pauseBeforeInput();
            Rs2Widget.clickWidget("withdraw");
            sleepUntil(() -> Rs2Dialogue.hasQuestion("Withdraw how much?"), 2400);
            pauseBeforeInput();
            Rs2Keyboard.typeString(String.valueOf(amount));
            pauseBeforeInput();
            Rs2Keyboard.enter();
            Rs2Inventory.waitForInventoryChanges(1200);
            if (!Rs2Bank.isOpen()) {
                pauseBeforeInput();
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                pauseBeforeInput();
                Rs2Bank.depositAllExcept(coalBag, GAUNTLETS_OF_GOLDSMITHING, ICE_GLOVES, SMITHING_UNIFORM_GLOVES_ICE);
            }
        } else {
            log.warn("Unexpected coffer dialogue state. delta = {}", delta);
            pauseBeforeInput();
            Rs2Dialogue.clickContinue();
        }
    }

    public boolean fullCoffer() {
        int coffer = Microbot.getVarbitValue(BLAST_FURNACE_COINSINCOFFER);
        return coffer == 1;
    }
}

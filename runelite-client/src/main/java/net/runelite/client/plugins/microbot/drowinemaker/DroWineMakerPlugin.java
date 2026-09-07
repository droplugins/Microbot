package net.runelite.client.plugins.microbot.drowinemaker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "[Dro] Wine Maker",
        description = "Automated wine maker using post-merge Rs2 utility framework with comprehensive randomization.",
        tags = {"wine", "cooking", "microbot", "antiban", "dro"},
        enabledByDefault = true
)
@Slf4j
public class DroWineMakerPlugin extends Plugin {

    @Inject
    private DroWineMakerScript script;

    @Override
    protected void startUp() throws Exception {
        script.run();
    }

    @Override
    protected void shutDown() {
        if (script != null) {
            script.shutdown();
        }
    }
}

@Slf4j
class DroWineMakerScript extends Script {

    private static final int WATER_JUG_ID = 1937;
    private static final int GRAPES_ID = 1987;

    private Instant lastMicroPause = Instant.now();
    private int nextPauseDelay = ThreadLocalRandom.current().nextInt(120, 300);
    private boolean lastWithdrewWaterFirst = true;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) {
                    return;
                }

                if (Rs2Player.isAnimating()) {
                    Microbot.status = "Making wine...";
                    sleep(600, 1000);
                    return;
                }

                if (Instant.now().isAfter(lastMicroPause.plusSeconds(nextPauseDelay))) {
                    int sleepDuration = ThreadLocalRandom.current().nextInt(3000, 10000);
                    log.info("Anti-ban: Natural micro-pause for {} ms", sleepDuration);
                    Microbot.status = "Taking micro-pause...";

                    if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                        try {
                            Microbot.getMouse().move(-ThreadLocalRandom.current().nextInt(50, 200), ThreadLocalRandom.current().nextInt(100, 400));
                        } catch (Exception ignored) {}
                    }

                    sleep(sleepDuration);
                    lastMicroPause = Instant.now();
                    nextPauseDelay = ThreadLocalRandom.current().nextInt(120, 300);
                }

                if (Rs2Inventory.count(WATER_JUG_ID) >= 14 && Rs2Inventory.count(GRAPES_ID) >= 14) {
                    if (Rs2Bank.isOpen()) {
                        Microbot.status = "Closing bank...";
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
                        sleep(ThreadLocalRandom.current().nextInt(80, 250));
                    }

                    Microbot.status = "Combining ingredients...";

                    boolean waterFirst = ThreadLocalRandom.current().nextBoolean();
                    boolean combined;

                    if (waterFirst) {
                        combined = Rs2Inventory.combine(WATER_JUG_ID, GRAPES_ID);
                    } else {
                        combined = Rs2Inventory.combine(GRAPES_ID, WATER_JUG_ID);
                    }

                    if (combined) {
                        // Grapes first uses optimal 1-1.5s delay. Water/jug first adds 0.6-1s more random delay (totaling 1600-2500ms).
                        if (!lastWithdrewWaterFirst) {
                            sleep(ThreadLocalRandom.current().nextInt(1000, 1501));
                        } else {
                            sleep(ThreadLocalRandom.current().nextInt(1600, 2501));
                        }

                        // Send space bar action with randomized multi-press and hold duration
                        int pressCount = ThreadLocalRandom.current().nextInt(1, 4);
                        for (int i = 0; i < pressCount; i++) {
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            int holdDuration = ThreadLocalRandom.current().nextInt(50, 251);
                            sleep(holdDuration);
                            if (i < pressCount - 1) {
                                sleep(ThreadLocalRandom.current().nextInt(40, 90));
                            }
                        }

                        sleepUntil(Rs2Player::isAnimating, 4000);
                        sleep(ThreadLocalRandom.current().nextInt(600, 1200));
                    }
                    return;
                }

                Microbot.status = "Opening bank...";
                if (!Rs2Bank.isOpen()) {
                    sleep(ThreadLocalRandom.current().nextInt(100, 450));

                    // Uses framework's standard Rs2Bank.openBank() which handles clicking the nearest bank booth, chest, or NPC
                    Rs2Bank.openBank();
                    boolean opened = sleepUntil(Rs2Bank::isOpen, 3000);

                    if (!opened) {
                        return;
                    }
                    sleep(ThreadLocalRandom.current().nextInt(100, 300));
                }

                Microbot.status = "Depositing items...";
                if (!Rs2Inventory.isEmpty()) {
                    Rs2Bank.depositAll();
                    sleepUntil(Rs2Inventory::isEmpty, 2000);
                    sleep(ThreadLocalRandom.current().nextInt(80, 200));
                }

                // Check if bank lacks sufficient supplies to prevent infinite loops, then logout using Rs2Player
                if (Rs2Bank.count(WATER_JUG_ID) < 14 || Rs2Bank.count(GRAPES_ID) < 14 ||
                        !Rs2Bank.hasItem(WATER_JUG_ID) || !Rs2Bank.hasItem(GRAPES_ID)) {
                    log.error("Out of materials! Less than 14 Water Jugs or Grapes remaining in bank. Logging out and stopping script.");
                    Microbot.status = "Out of ingredients! Logging out...";
                    try {
                        Rs2Player.logout();
                    } catch (Exception ignored) {}
                    shutdown();
                    return;
                }

                Microbot.status = "Withdrawing ingredients...";
                lastWithdrewWaterFirst = ThreadLocalRandom.current().nextBoolean();

                if (lastWithdrewWaterFirst) {
                    Rs2Bank.withdrawX(WATER_JUG_ID, 14);
                    sleep(ThreadLocalRandom.current().nextInt(120, 320));
                    Rs2Bank.withdrawX(GRAPES_ID, 14);
                } else {
                    Rs2Bank.withdrawX(GRAPES_ID, 14);
                    sleep(ThreadLocalRandom.current().nextInt(120, 320));
                    Rs2Bank.withdrawX(WATER_JUG_ID, 14);
                }

                boolean withdrawSuccess = sleepUntil(() ->
                        Rs2Inventory.count(WATER_JUG_ID) >= 14 && Rs2Inventory.count(GRAPES_ID) >= 14, 3000);

                if (!withdrawSuccess) {
                    log.error("Withdrawal verification failed due to insufficient stock. Logging out and stopping script.");
                    Microbot.status = "Out of ingredients! Logging out...";
                    try {
                        Rs2Player.logout();
                    } catch (Exception ignored) {}
                    shutdown();
                    return;
                }

                if (Rs2Bank.isOpen()) {
                    sleep(ThreadLocalRandom.current().nextInt(60, 200));
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen(), 2000);

                    if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                        try {
                            Microbot.getMouse().move(ThreadLocalRandom.current().nextInt(800, 950), ThreadLocalRandom.current().nextInt(100, 500));
                        } catch (Exception ignored) {}
                    }
                }

            } catch (Exception ex) {
                log.error("Error in DroWineMakerScript: ", ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Microbot.status = "Wine Maker stopped.";
        super.shutdown();
    }
}
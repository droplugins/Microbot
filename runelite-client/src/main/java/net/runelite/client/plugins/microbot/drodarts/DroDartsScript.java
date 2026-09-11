package net.runelite.client.plugins.microbot.drodarts;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class DroDartsScript extends Script
{
    private static final int FEATHER_ID = 314;

    /*
     * How frequently the scheduler checks the state machine.
     *
     * This is NOT the fletching delay. It just determines how quickly
     * we notice that the previous state has completed.
     */
    private static final long POLL_MS = 10;

    /*
     * Small fallback delay after an interaction.
     *
     * We normally advance as soon as the client reports the expected
     * selection state, rather than blindly sleeping for this amount.
     */
    private static final long INTERACTION_TIMEOUT_MS = 250;

    private final AtomicBoolean queued = new AtomicBoolean();

    private volatile boolean active;

    @Getter
    private volatile long dartsFletched;

    @Getter
    private volatile String status = "Starting";

    private DroDartsConfig.DartTip activeType;

    private State state = State.IDLE;

    private long stateStartedNanos;

    private long previousDarts;

    private boolean baseline;

    private enum State
    {
        IDLE,
        SELECTING_TIPS,
        USING_FEATHERS,
        WAITING_FOR_RESULT
    }

    public boolean run(DroDartsConfig config)
    {
        if (active)
        {
            return false;
        }

        active = true;

        dartsFletched = 0;
        activeType = null;

        state = State.IDLE;
        stateStartedNanos = 0;

        previousDarts = 0;
        baseline = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                () ->
                {
                    try
                    {
                        if (!active)
                        {
                            return;
                        }

                        if (!queued.compareAndSet(false, true))
                        {
                            return;
                        }

                        Microbot.getClientThread().invokeLater(
                                () ->
                                {
                                    try
                                    {
                                        if (active)
                                        {
                                            tick(config);
                                        }
                                    }
                                    catch (Exception ex)
                                    {
                                        fail(ex);
                                    }
                                    finally
                                    {
                                        queued.set(false);
                                    }
                                });
                    }
                    catch (Exception ex)
                    {
                        queued.set(false);
                        fail(ex);
                    }
                },
                0,
                POLL_MS,
                TimeUnit.MILLISECONDS);

        return true;
    }

    private void tick(DroDartsConfig config)
    {
        if (!active)
        {
            return;
        }

        if (!Microbot.isLoggedIn())
        {
            resetState();

            baseline = false;
            status = "Waiting for login";

            return;
        }

        if (Microbot.pauseAllScripts.get())
        {
            status = "Paused";
            return;
        }

        DroDartsConfig.DartTip type = config.dartType();

        /*
         * If the user changes dart type while the script is running,
         * restart the interaction state cleanly.
         */
        if (activeType != type)
        {
            activeType = type;
            resetState();
            baseline = false;
        }

        /*
         * Keep the dart counter updated.
         */
        updateDartCounter(type);

        switch (state)
        {
            case IDLE:
                beginSelection(type);
                break;

            case SELECTING_TIPS:
                checkTipSelection(type);
                break;

            case USING_FEATHERS:
                useFeathers();
                break;

            case WAITING_FOR_RESULT:
                checkResult(type, config);
                break;
        }
    }

    private void beginSelection(DroDartsConfig.DartTip type)
    {
        if (!Rs2Inventory.hasItem(FEATHER_ID))
        {
            status = "No Feathers";
            return;
        }

        if (!Rs2Inventory.hasItem(type.getItemId()))
        {
            status = "No Dart Tips";
            return;
        }

        /*
         * If something else is selected, clear that selection first.
         */
        if (Rs2Inventory.isItemSelected())
        {
            status = "Clearing selection";

            Rs2Inventory.deselect();

            stateStartedNanos = System.nanoTime();
            state = State.SELECTING_TIPS;

            return;
        }

        /*
         * Select the dart tips.
         *
         * IMPORTANT:
         *
         * Rs2Inventory.use() returning true only means that the
         * interaction was dispatched. It does NOT guarantee that
         * RuneScape has processed the selection yet.
         */
        if (!Rs2Inventory.use(type.getItemId()))
        {
            status = "Tip selection failed";

            stateStartedNanos = System.nanoTime();
            return;
        }

        stateStartedNanos = System.nanoTime();
        state = State.SELECTING_TIPS;

        status = "Selecting Tips";
    }

    private void checkTipSelection(DroDartsConfig.DartTip type)
    {
        /*
         * This is the critical synchronization point.
         *
         * Don't send the feather interaction until the client actually
         * reports that an item/widget is selected.
         */
        if (Rs2Inventory.isItemSelected())
        {
            state = State.USING_FEATHERS;
            stateStartedNanos = System.nanoTime();
            status = "Tips Selected";

            return;
        }

        /*
         * If selection hasn't happened yet, wait.
         */
        if (!timedOut())
        {
            status = "Waiting for Tip Selection";
            return;
        }

        /*
         * Selection failed/timed out. Start over rather than hammering
         * the inventory with hundreds of duplicate interactions.
         */
        state = State.IDLE;
        stateStartedNanos = System.nanoTime();

        status = "Retrying Tip Selection";
    }

    private void useFeathers()
    {
        /*
         * We should only arrive here while the tips are actually
         * selected.
         */
        if (!Rs2Inventory.isItemSelected())
        {
            state = State.IDLE;
            status = "Selection Lost";
            return;
        }

        if (!Rs2Inventory.hasItem(FEATHER_ID))
        {
            state = State.IDLE;
            status = "No Feathers";
            return;
        }

        /*
         * Use the selected dart tips on feathers.
         */
        if (!Rs2Inventory.use(FEATHER_ID))
        {
            state = State.IDLE;
            status = "Feather Interaction Failed";
            return;
        }

        state = State.WAITING_FOR_RESULT;
        stateStartedNanos = System.nanoTime();

        status = "Fletching";
    }

    private void checkResult(
            DroDartsConfig.DartTip type,
            DroDartsConfig config)
    {
        /*
         * Once the game processes the combine, the selection should
         * disappear and/or the inventory dart count will change.
         */
        int currentDarts =
                Rs2Inventory.count(type.getDartId());

        if (currentDarts > previousDarts)
        {
            previousDarts = currentDarts;

            /*
             * The inventory count may jump by multiple darts because
             * the game produces them in batches.
             *
             * Count the actual increase.
             */
            if (baseline)
            {
                dartsFletched++;
            }

            baseline = true;

            state = State.IDLE;

            status = "Fletching";

            scheduleNextAction(config);

            return;
        }

        /*
         * Selection disappearing is also a useful indication that the
         * interaction was accepted, even if the inventory update has
         * not reached us yet.
         */
        if (!Rs2Inventory.isItemSelected() && timedOut())
        {
            state = State.IDLE;

            status = "Fletching";

            scheduleNextAction(config);

            return;
        }

        /*
         * Don't wait forever if the game/client rejected the action.
         */
        if (timedOut())
        {
            state = State.IDLE;
            status = "Retrying";

            scheduleNextAction(config);
        }
    }

    private void updateDartCounter(DroDartsConfig.DartTip type)
    {
        int darts = Rs2Inventory.count(type.getDartId());

        if (!baseline)
        {
            previousDarts = darts;
            baseline = true;
            return;
        }

        /*
         * Don't increment the displayed counter here because the result
         * state handles successful production.
         */
    }

    private boolean timedOut()
    {
        return System.nanoTime() - stateStartedNanos
                >= TimeUnit.MILLISECONDS.toNanos(
                INTERACTION_TIMEOUT_MS);
    }

    private void resetState()
    {
        state = State.IDLE;
        stateStartedNanos = System.nanoTime();
    }

    private void scheduleNextAction(DroDartsConfig config)
    {
        int min = Math.max(0, config.minDelay());
        int max = Math.max(min, config.maxDelay());

        int delay;

        if (min == 0 && max == 0)
        {
            /*
             * No artificial delay.
             *
             * The state machine still prevents duplicate interactions.
             */
            delay = 0;
        }
        else if (min == max)
        {
            delay = min;
        }
        else
        {
            delay = ThreadLocalRandom.current()
                    .nextInt(min, max + 1);
        }

        if (delay == 0)
        {
            stateStartedNanos = System.nanoTime();
        }
        else
        {
            stateStartedNanos =
                    System.nanoTime()
                            + TimeUnit.MILLISECONDS.toNanos(delay);
        }
    }

    private void fail(Exception ex)
    {
        if (!active)
        {
            return;
        }

        status =
                "Error: "
                        + ex.getClass().getSimpleName();

        resetState();

        Microbot.log(
                "DroDarts Error: "
                        + ex.getClass().getSimpleName()
                        + ": "
                        + ex.getMessage());
    }

    @Override
    public void shutdown()
    {
        active = false;

        if (mainScheduledFuture != null)
        {
            mainScheduledFuture.cancel(true);
        }

        super.shutdown();

        status = "Stopped";
    }
}
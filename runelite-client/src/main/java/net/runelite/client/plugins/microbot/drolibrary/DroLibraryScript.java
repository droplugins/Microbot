package net.runelite.client.plugins.microbot.drolibrary;

import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FlashBangPhats Kourend Library script.
 *
 * Important behavior:
 *  - The customer is locked as soon as an assignment is parsed.
 *  - The exact requested book item ID is used to decide when the book is held.
 *  - The solver/bookcase logic is retained; floor navigation is handled here.
 *  - Staircases are addressed by the exact tiles supplied for FlashBangPhats,
 *    rather than asking getGameObject(id) to choose between several identical
 *    stair objects.
 */
@Singleton
public class DroLibraryScript extends Script
{
    private static final Logger logger = LoggerFactory.getLogger(DroLibraryScript.class);

    private static final WorldPoint LIBRARY_CENTER = new WorldPoint(1629, 3801, 0);

    private static final int NPC_VILLIA = 7047;
    private static final int NPC_PROFESSOR_GRACKLEBONE = 7048;
    private static final int NPC_SAM = 7049;
    private static final int BOOK_OF_ARCANE_KNOWLEDGE = 13513;

    public static final String VERSION = "1.02";
    private static final int MIN_STARTUP_ZOOM = 95;
    private static final int MAX_STARTUP_ZOOM = 105;
    private static final int MIN_STARTUP_PITCH = 3000;
    private static final int MAX_STARTUP_PITCH = 3064;
    private static final int NORTH_YAW = 0;

    private static final int LIBRARY_MIN_X = 1605;
    private static final int LIBRARY_MAX_X = 1660;
    private static final int LIBRARY_MIN_Y = 3782;
    private static final int LIBRARY_MAX_Y = 3833;

    private static final long MIN_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long MAX_REST_INTERVAL_MS = TimeUnit.MINUTES.toMillis(4);
    private static final long MIN_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(3);
    private static final long MAX_REST_DURATION_MS = TimeUnit.SECONDS.toMillis(13);
    private static final long STAIR_RETRY_DELAY_MS = 1200L;
    private static final int MIN_PATH_BOOK_RADIUS = 10;
    private static final int MAX_PATH_BOOK_RADIUS = 18;
    private static final int MIN_RUN_ENABLE_PERCENT = 25;
    private static final int MAX_RUN_ENABLE_PERCENT = 40;
    private static final int MIN_INPUT_DELAY_MS = 50;
    private static final int MAX_INPUT_DELAY_MS = 260;

    private static final int[] CUSTOMER_IDS =
            {NPC_VILLIA, NPC_PROFESSOR_GRACKLEBONE, NPC_SAM};

    /*
     * FlashBangPhats staircase map.
     *
     * Plane 0 = bottom/ground floor
     * Plane 1 = middle floor
     * Plane 2 = top floor
     *
     * The same staircase often has a different object ID on the other side.
     * We therefore store both the ID and the exact tile, but interact with the
     * exact tile so duplicate IDs cannot make getGameObject() choose the wrong
     * staircase.
     */
    private static final Stair[] BOTTOM_UP_STAIRS =
            {
                    // Bottom NW
                    new Stair(27854, new WorldPoint(1615, 3827, 0), 1, "Bottom NW up"),
                    new Stair(27853, new WorldPoint(1615, 3825, 0), 1, "Bottom NW up"),

                    // Bottom NE
                    new Stair(27854, new WorldPoint(1645, 3820, 0), 1, "Bottom NE up"),
                    new Stair(27853, new WorldPoint(1643, 3820, 0), 1, "Bottom NE up"),

                    // Bottom South
                    new Stair(27854, new WorldPoint(1615, 3796, 0), 1, "Bottom South up"),
                    new Stair(27853, new WorldPoint(1615, 3794, 0), 1, "Bottom South up")
            };

    private static final Stair[] MIDDLE_UP_STAIRS =
            {
                    // South staircase: middle -> top
                    new Stair(27851, new WorldPoint(1621, 3793, 1), 2, "Middle South up"),

                    // NW staircase: middle -> top
                    new Stair(27851, new WorldPoint(1610, 3818, 1), 2, "Middle NW up"),

                    // NE staircase: middle -> top
                    new Stair(27851, new WorldPoint(1645, 3828, 1), 2, "Middle NE up"),

                    // Central room staircase: middle -> top
                    new Stair(27851, new WorldPoint(1638, 3808, 1), 2, "Middle Central up")
            };

    private static final Stair[] MIDDLE_DOWN_STAIRS =
            {
                    // South staircase: middle -> bottom
                    new Stair(27855, new WorldPoint(1612, 3794, 1), 0, "Middle South down"),
                    new Stair(27856, new WorldPoint(1612, 3796, 1), 0, "Middle South down"),

                    // NW staircase: middle -> bottom
                    new Stair(27855, new WorldPoint(1612, 3825, 1), 0, "Middle NW down"),
                    new Stair(27856, new WorldPoint(1612, 3827, 1), 0, "Middle NW down"),

                    // NE staircase: middle -> bottom
                    new Stair(27855, new WorldPoint(1645, 3822, 1), 0, "Middle NE down"),
                    new Stair(27856, new WorldPoint(1645, 3822, 1), 0, "Middle NE down")
            };

    private static final Stair[] TOP_DOWN_STAIRS =
            {
                    // South: top -> middle
                    new Stair(27852, new WorldPoint(1621, 3795, 2), 1, "Top South down"),

                    // Central: top -> middle central room
                    new Stair(27852, new WorldPoint(1638, 3805, 2), 1, "Top Central down"),

                    // NE: top -> middle
                    new Stair(27852, new WorldPoint(1647, 3828, 2), 1, "Top NE down"),

                    // NW: top -> middle
                    new Stair(27852, new WorldPoint(1610, 3818, 2), 1, "Top NW down")
            };

    /*
     * Do not schedule a second loop if Plugin.startUp is called twice.
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Inject
    private DroLibraryConfig config;

    @Inject
    private Library library;

    private volatile int currentCustomerId = -1;
    private volatile Book currentTargetBook = null;
    private volatile boolean customerLocked = false;
    private int customerSearchIndex = 0;

    /* Customer who actually opened the current dialogue.  This is critical
     * because Villia and Professor are close enough that selecting the first
     * visible NPC is not a valid way to identify the dialogue speaker. */
    private int dialogueCustomerId = -1;
    private boolean rotateCustomerAfterDialogue = false;

    private volatile String currentStateStr = "Initializing";
    private volatile String lastAction = "Starting";
    private volatile String lastSearchResult = "None";
    private long lastInteractionTime = 0L;

    /* Prevent repeatedly clicking the same staircase while the plane change is pending. */
    private long lastStairInteraction = 0L;
    private WorldPoint lastStairTile = null;

    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> observations =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<WorldPoint, Long> retryAfter = new HashMap<>();
    private long lastWalkTime;
    private boolean turnInPending;
    private boolean closingCompletedDialogue;
    private int turnInBookCount;
    private int turnInRewardCount;
    private long lastTurnInAttempt;
    private long lastStaminaAttempt;
    private boolean sessionInitialized;
    private long nextRestAt = Long.MAX_VALUE;
    private long restUntil;
    private long lastCameraAttempt;
    private long lastNpcCameraAttempt;
    private int pathBookRadius = 14;
    private int runEnablePercent = 32;

    private long randomBetween(long minimum, long maximum)
    {
        return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
    }

    private void pauseBeforeInput()
    {
        sleep(ThreadLocalRandom.current().nextInt(
                MIN_INPUT_DELAY_MS, MAX_INPUT_DELAY_MS + 1));
    }

    private void clickContinue()
    {
        pauseBeforeInput();
        Rs2Dialogue.clickContinue();
    }

    private void initializeSession()
    {
        if (sessionInitialized)
            return;

        int startupZoom = ThreadLocalRandom.current()
                .nextInt(MIN_STARTUP_ZOOM, MAX_STARTUP_ZOOM + 1);
        int startupPitch = ThreadLocalRandom.current()
                .nextInt(MIN_STARTUP_PITCH, MAX_STARTUP_PITCH + 1);
        pathBookRadius = ThreadLocalRandom.current()
                .nextInt(MIN_PATH_BOOK_RADIUS, MAX_PATH_BOOK_RADIUS + 1);
        runEnablePercent = ThreadLocalRandom.current()
                .nextInt(MIN_RUN_ENABLE_PERCENT, MAX_RUN_ENABLE_PERCENT + 1);
        Microbot.getClientThread().invoke(() ->
        {
            Microbot.getClient().setCameraPitchTarget(startupPitch);
            Microbot.getClient().setCameraYawTarget(NORTH_YAW);
        });
        Rs2Camera.setZoom(startupZoom);
        nextRestAt = System.currentTimeMillis()
                + randomBetween(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
        sessionInitialized = true;
        logger.info("[DroLibraryScript] Session initialized: version={} zoom={} rawPitch={} yaw={} pathBookRadius={} runAt={} next rest in {} seconds",
                VERSION, startupZoom, startupPitch, NORTH_YAW, pathBookRadius, runEnablePercent,
                TimeUnit.MILLISECONDS.toSeconds(nextRestAt - System.currentTimeMillis()));
    }

    /**
     * Start breaks only while fully idle. This keeps a due break from interrupting
     * a walk, staircase transition, animation, or dialogue.
     */
    private boolean handleScheduledRest()
    {
        long now = System.currentTimeMillis();
        if (restUntil > now)
        {
            currentStateStr = "RESTING";
            lastAction = "Resting for " + Math.max(1L,
                    TimeUnit.MILLISECONDS.toSeconds(restUntil - now)) + "s";
            return true;
        }

        if (restUntil != 0L)
        {
            restUntil = 0L;
            nextRestAt = now + randomBetween(MIN_REST_INTERVAL_MS, MAX_REST_INTERVAL_MS);
            logger.info("[DroLibraryScript] Rest complete; next rest in {} seconds",
                    TimeUnit.MILLISECONDS.toSeconds(nextRestAt - now));
        }

        if (now < nextRestAt
                || Rs2Dialogue.isInDialogue()
                || Rs2Player.isMoving()
                || Rs2Player.isAnimating())
            return false;

        long duration = randomBetween(MIN_REST_DURATION_MS, MAX_REST_DURATION_MS);
        restUntil = now + duration;
        nextRestAt = Long.MAX_VALUE;
        currentStateStr = "RESTING";
        lastAction = "Starting " + TimeUnit.MILLISECONDS.toSeconds(duration) + "s rest";
        logger.info("[DroLibraryScript] Starting scheduled rest for {} ms", duration);
        pauseBeforeInput();
        Rs2Antiban.moveMouseOffScreen();
        return true;
    }

    private int itemCount(int id)
    {
        return Rs2Inventory.items().filter(i -> i != null && i.getId() == id)
                .mapToInt(i -> i.getQuantity()).sum();
    }

    private static boolean completionText(String text)
    {
        if (text == null) return false;
        String clean = text.replaceAll("<[^>]*>", " ").toLowerCase();
        return clean.contains("thank you for finding my book")
                || clean.contains("thank you for finding the book")
                || clean.contains("thank you for returning my book")
                || clean.contains("thanks for finding my book")
                || clean.contains("thanks for finding the book")
                || clean.contains("please accept a token of my thanks")
                || clean.contains("thanks, i'll get on with reading it");
    }

    private void beginTurnInTracking()
    {
        if (turnInPending || !customerLocked || currentTargetBook == null) return;
        turnInBookCount = itemCount(currentTargetBook.getItem());
        turnInRewardCount = itemCount(BOOK_OF_ARCANE_KNOWLEDGE);
        turnInPending = true;
    }

    private void checkTurnInCompletion()
    {
        if (!customerLocked) return;
        boolean consumed = turnInPending && itemCount(currentTargetBook.getItem()) < turnInBookCount;
        boolean rewarded = turnInPending && itemCount(BOOK_OF_ARCANE_KNOWLEDGE) > turnInRewardCount;
        boolean thanked = Rs2Dialogue.isInDialogue()
                && completionText(Rs2Dialogue.getDialogueText())
                && (identifyDialogueCustomer() == currentCustomerId
                || (turnInPending && dialogueCustomerId == currentCustomerId));
        if (consumed || rewarded || thanked)
        {
            logger.info("[DroLibraryScript] ASSIGNMENT COMPLETE customer={} consumed={} reward={} thanked={}",
                    currentCustomerId, consumed, rewarded, thanked);
            resetAssignment();
            closingCompletedDialogue = Rs2Dialogue.isInDialogue();
        }
    }

    void observeShelf(WorldPoint tile, Book book)
    {
        observations.add(() -> {
            library.mark(tile, book);
            retryAfter.remove(tile);
            lastSearchResult = (book == null ? "Empty" : book.getShortName()) + " @ " + tile;
        });
    }

    void observeReset()
    {
        observations.add(() -> { library.reset(); retryAfter.clear(); });
    }

    private void drainObservations()
    {
        Runnable observation;
        while ((observation = observations.poll()) != null)
            observation.run();
    }

    public enum State
    {
        USE_ARCANE_BOOK,
        TURN_IN_BOOK,
        GET_ASSIGNMENT,
        NAVIGATE_FLOORS,
        SEARCH_SHELF
    }

    private static final class Stair
    {
        private final int id;
        private final WorldPoint tile;
        private final int destinationPlane;
        private final String name;

        private Stair(int id, WorldPoint tile, int destinationPlane, String name)
        {
            this.id = id;
            this.tile = tile;
            this.destinationPlane = destinationPlane;
            this.name = name;
        }
    }

    @Override
    public boolean run()
    {
        if (!started.compareAndSet(false, true))
        {
            logger.warn("[DroLibraryScript] run() called while already running - ignoring duplicate start");
            return true;
        }

        // Book locations can change between sessions/world state. Never reuse a stale solve.
        observations.clear();
        retryAfter.clear();
        library.reset();

        logger.info("[DroLibraryScript] ========================================");
        logger.info("[DroLibraryScript] STARTING instance={}", System.identityHashCode(this));
        logger.info("[DroLibraryScript] Library center={}", LIBRARY_CENTER);
        logger.info("[DroLibraryScript] Customer IDs: Villia={}, Professor Gracklebone={}, Sam={}",
                NPC_VILLIA, NPC_PROFESSOR_GRACKLEBONE, NPC_SAM);
        logger.info("[DroLibraryScript] Arcane Knowledge item ID={}", BOOK_OF_ARCANE_KNOWLEDGE);
        logger.info("[DroLibraryScript] Stair map loaded: bottom->middle={}, middle->top={}, top->middle={}, middle->bottom={}",
                BOTTOM_UP_STAIRS.length, MIDDLE_UP_STAIRS.length, TOP_DOWN_STAIRS.length, MIDDLE_DOWN_STAIRS.length);
        logger.info("[DroLibraryScript] ========================================");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn())
                    return;

                if (!super.run())
                    return;

                initializeSession();
                drainObservations();
                checkTurnInCompletion();
                if (handleScheduledRest())
                    return;
                State state = getState();
                currentStateStr = state.name();
                logStatus(state);

                switch (state)
                {
                    case GET_ASSIGNMENT:
                        handleGetAssignment();
                        break;
                    case NAVIGATE_FLOORS:
                        handleNavigateFloors();
                        break;
                    case SEARCH_SHELF:
                        handleSearchShelf();
                        break;
                    case TURN_IN_BOOK:
                        handleTurnIn();
                        break;
                    case USE_ARCANE_BOOK:
                        handleArcaneBook();
                        break;
                }
            }
            catch (Exception ex)
            {
                logger.error("[DroLibraryScript] Error in main loop", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown()
    {
        logger.info("[DroLibraryScript] SHUTDOWN instance={}", System.identityHashCode(this));
        started.set(false);
        turnInPending = false;
        closingCompletedDialogue = false;

        currentCustomerId = -1;
        currentTargetBook = null;
        customerLocked = false;
        customerSearchIndex = 0;
        dialogueCustomerId = -1;
        rotateCustomerAfterDialogue = false;
        lastStairTile = null;
        lastStairInteraction = 0L;
        sessionInitialized = false;
        nextRestAt = Long.MAX_VALUE;
        restUntil = 0L;
        lastCameraAttempt = 0L;
        lastNpcCameraAttempt = 0L;

        super.shutdown();
    }

    private State getState()
    {
        if (closingCompletedDialogue) return State.GET_ASSIGNMENT;
        if (turnInPending) return State.TURN_IN_BOOK;
        /*
         * If the assignment book is physically in the inventory, this always
         * wins. Do this before any solver/floor logic.
         */
        if (hasTargetBookInInventory())
        {
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player != null && player.getPlane() != 0)
                return State.NAVIGATE_FLOORS;
            return State.TURN_IN_BOOK;
        }

        /* Recovery after a plugin restart: if we already hold any library book but
         * no assignment is locked, get back to the ground floor first. Once there,
         * asking the customers will rediscover which book/customer is active. */
        if (!customerLocked && findKnownBookInInventory() != null)
        {
            WorldPoint player = Rs2Player.getWorldLocation();
            if (player != null && player.getPlane() != 0)
                return State.NAVIGATE_FLOORS;
        }

        String dialogueText = Rs2Dialogue.getDialogueText();
        boolean arcanePrompt = dialogueText != null
                && (dialogueText.contains("What kind of knowledge will you learn?")
                || Rs2Dialogue.hasSelectAnOption());

        if (Rs2Inventory.contains(BOOK_OF_ARCANE_KNOWLEDGE)
                || (Rs2Dialogue.isInDialogue() && arcanePrompt))
            return State.USE_ARCANE_BOOK;

        /* Never search or switch NPCs while a dialogue is open. */
        if (Rs2Dialogue.isInDialogue())
            return State.GET_ASSIGNMENT;

        if (customerLocked && currentTargetBook != null)
        {
            Bookcase targetCase = getTargetBookcase();
            WorldPoint player = Rs2Player.getWorldLocation();

            if (targetCase != null && player != null
                    && (player.getPlane() != targetCase.getLocation().getPlane()
                    || (player.getPlane() == 1 && middleRoom(player) != middleRoom(targetCase.getLocation()))))
                return State.NAVIGATE_FLOORS;

            return State.SEARCH_SHELF;
        }

        return State.GET_ASSIGNMENT;
    }

    private void handleGetAssignment()
    {
        if (closingCompletedDialogue)
        {
            if (Rs2Dialogue.isInDialogue()) clickContinue();
            else closingCompletedDialogue = false;
            return;
        }
        /* This script intentionally does NOT travel to the library.  Starting
         * at the bank, GE, teleport spot, etc. simply leaves it idle until the
         * player is actually inside the library. */
        if (!isInLibrary())
        {
            lastAction = "Waiting for library";
            return;
        }

        if (Rs2Dialogue.isInDialogue())
        {
            if (customerLocked)
                handleLockedCustomerDialogue();
            else
                processAssignmentDialogue();
            return;
        }

        /* A busy/already-completed customer has finished talking. Rotate
         * BEFORE selecting another visible NPC so we cannot immediately click
         * the same customer again. */
        if (rotateCustomerAfterDialogue && dialogueCustomerId != -1)
        {
            customerSearchIndex = (customerIndexForId(dialogueCustomerId) + 1) % CUSTOMER_IDS.length;
            logger.info("[DroLibraryScript] Rotating customer search to {}",
                    customerName(CUSTOMER_IDS[customerSearchIndex]));
            dialogueCustomerId = -1;
            rotateCustomerAfterDialogue = false;
        }

        if (customerLocked && currentTargetBook != null)
            return;

        if (Rs2Player.isAnimating() || Rs2Player.isMoving())
            return;

        NPC npc = findNextVisibleCustomer();
        if (npc == null)
        {
            lastAction = "Returning to customer area";
            returnToCustomers();
            return;
        }

        int npcId = npc.getId();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
            return;

        customerSearchIndex = customerIndexForId(npcId);

        /* Do not use Rs2Walker.walkTo here.  The current FlashBangPhats
         * walker can bootstrap the bank cache and physically send the player
         * to the bank before making a short local walk.  walkFastCanvas() is the
         * plain local pathing API and does not do banked-transport routing. */
        if (player.distanceTo(npc.getWorldLocation()) > 3)
        {
            lastAction = "Walking to " + customerName(npcId);
            localWalk(npc.getWorldLocation());
            return;
        }

        if (System.currentTimeMillis() - lastInteractionTime < 1200)
            return;

        dialogueCustomerId = npcId;
        rotateCustomerAfterDialogue = false;

        logger.info("[DroLibraryScript] Attempting assignment from {} (id={})",
                customerName(npcId), npcId);

        if (interactCustomer(npc, "Help"))
        {
            lastInteractionTime = System.currentTimeMillis();
            lastAction = "Talking to " + customerName(npcId);
            sleepUntil(Rs2Dialogue::isInDialogue, 3000);
        }
    }

    private void processAssignmentDialogue()
    {
        int speakerId = identifyDialogueCustomer();
        if (speakerId != -1)
            dialogueCustomerId = speakerId;

        /* If the script was started while a dialogue was already open, never
         * guess that Villia is speaking just because she is first in the list.
         * Wait for the NPC-interacting relationship to identify the speaker. */
        if (dialogueCustomerId == -1)
        {
            lastAction = "Identifying dialogue customer";
            logger.info("[DroLibraryScript] Dialogue open but speaker is not yet identifiable; refusing to switch NPCs");
            return;
        }

        String text = Rs2Dialogue.getDialogueText();

        if (rotateCustomerAfterDialogue)
        {
            lastAction = "Finishing customer dialogue";
            if (text != null && !text.trim().isEmpty())
                logger.info("[DroLibraryScript] Closing non-assignment dialogue with {}: {}",
                        customerName(dialogueCustomerId), text);

            clickContinue();
            if (!Rs2Dialogue.isInDialogue())
            {
                customerSearchIndex = (customerIndexForId(dialogueCustomerId) + 1) % CUSTOMER_IDS.length;
                dialogueCustomerId = -1;
                rotateCustomerAfterDialogue = false;
            }
            return;
        }

        if (text == null || text.trim().isEmpty())
        {
            clickContinue();
            return;
        }

        logger.info("[DroLibraryScript] Assignment dialogue from {}({}): {}",
                customerName(dialogueCustomerId), dialogueCustomerId, text);

        /* A customer who has just been helped does NOT assign another book.
         * Rotate to the next customer instead of repeatedly clicking the same
         * NPC forever. */
        if (isCompletedCustomerDialogue(text))
        {
            rotateCustomerAfterDialogue = true;
            lastAction = customerName(dialogueCustomerId) + " already completed - trying next customer";
            clickContinue();
            return;
        }

        if (isBusyCustomerDialogue(text))
        {
            rotateCustomerAfterDialogue = true;
            lastAction = customerName(dialogueCustomerId) + " is busy";
            clickContinue();
            return;
        }

        Book assignedBook = findBookInDialogue(text);
        if (assignedBook != null)
        {
            currentCustomerId = dialogueCustomerId;
            currentTargetBook = assignedBook;
            customerLocked = true;

            library.setCustomer(currentCustomerId, currentTargetBook);
            // A held book may be accepted while continuing this very dialogue.
            if (hasTargetBookInInventory()) beginTurnInTracking();

            lastAction = "Assigned " + assignedBook.getShortName()
                    + " by " + customerName(currentCustomerId);

            logger.info("[DroLibraryScript] ASSIGNMENT ACQUIRED: customer={}({}) book={} itemId={}",
                    customerName(currentCustomerId), currentCustomerId,
                    assignedBook.getShortName(), assignedBook.getItem());

            /* Keep dialogueCustomerId tied to the locked customer until the
             * assignment dialogue has completely closed. */
            clickContinue();
            return;
        }

        clickContinue();
    }

    private void handleLockedCustomerDialogue()
    {
        if (!Rs2Dialogue.isInDialogue())
            return;

        String text = Rs2Dialogue.getDialogueText();
        if (text != null)
            logger.info("[DroLibraryScript] Locked-customer dialogue with {}: {}",
                    customerName(currentCustomerId), text);

        clickContinue();
    }

    private NPC findNextVisibleCustomer()
    {
        for (int i = 0; i < CUSTOMER_IDS.length; i++)
        {
            int index = (customerSearchIndex + i) % CUSTOMER_IDS.length;
            NPC npc = Rs2Npc.getNpc(CUSTOMER_IDS[index]);
            if (npc != null)
            {
                customerSearchIndex = index;
                return npc;
            }
        }
        return null;
    }

    private int customerIndexForId(int id)
    {
        for (int i = 0; i < CUSTOMER_IDS.length; i++)
        {
            if (CUSTOMER_IDS[i] == id)
                return i;
        }
        return 0;
    }

    /** Identify the NPC currently interacting with the local player. */
    private int identifyDialogueCustomer()
    {
        if (!Rs2Dialogue.isInDialogue())
            return -1;

        NPC interacting = null;
        for (int id : CUSTOMER_IDS)
        {
            NPC npc = Rs2Npc.getNpc(id);
            if (npc != null && npc.getInteracting() == Microbot.getClient().getLocalPlayer())
            {
                interacting = npc;
                break;
            }
        }

        if (interacting != null)
        {
            logger.info("[DroLibraryScript] Dialogue speaker identified as {}({})",
                    customerName(interacting.getId()), interacting.getId());
            return interacting.getId();
        }

        /* Fallback only when exactly one customer is very close.  Never choose
         * the first visible NPC when two customers are nearby. */
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
            return -1;

        int found = -1;
        int count = 0;
        for (int id : CUSTOMER_IDS)
        {
            NPC npc = Rs2Npc.getNpc(id);
            if (npc != null && player.distanceTo(npc.getWorldLocation()) <= 2)
            {
                found = id;
                count++;
            }
        }
        return count == 1 ? found : -1;
    }

    private boolean isCompletedCustomerDialogue(String text)
    {
        return completionText(text);
    }

    private boolean isBusyCustomerDialogue(String text)
    {
        String normalized = text.toLowerCase();
        return normalized.contains("assisting another customer")
                || normalized.contains("helping someone else")
                || normalized.contains("currently helping")
                || normalized.contains("already helping")
                || normalized.contains("busy helping");
    }

    private Book findBookInDialogue(String text)
    {
        if (text == null || text.trim().isEmpty())
            return null;

        /*
         * FlashBangPhats inserts HTML <br> tags into the dialogue text.
         * For example the Professor sends:
         *
         *   'Speech of King Byrne I, on the occasion of his<br>coronation.'
         *
         * The old parser compared the raw string with Book.getName(), so the
         * <br> made an otherwise exact assignment impossible to recognise.
         * The stock Kourend Library plugin explicitly strips these tags before
         * looking the book up; do the same here, but keep a few extra fallbacks.
         */
        String clean = text
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();

        /* First try the quoted book title used by the RSPS dialogue. */
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("'([^']+)'", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(clean);
        while (matcher.find())
        {
            String quoted = matcher.group(1).trim();
            for (Book book : Book.values())
            {
                if (book.getName() != null && book.getName().equalsIgnoreCase(quoted))
                    return book;
            }
        }

        String normalized = clean.toLowerCase();
        for (Book book : Book.values())
        {
            if (book.getShortName() != null
                    && normalized.contains(book.getShortName().toLowerCase()))
                return book;
            if (book.getName() != null
                    && normalized.contains(book.getName().toLowerCase()))
                return book;
        }

        return null;
    }

    /**
     * Exact item-ID check. This is deliberately not a name comparison.
     */
    private boolean hasTargetBookInInventory()
    {
        if (!customerLocked || currentTargetBook == null)
            return false;

        final int targetItemId = currentTargetBook.getItem();

        return Rs2Inventory.items().anyMatch(item ->
                item != null && item.getId() == targetItemId);
    }

    /**
     * Finds the exact known library book represented by an inventory item.
     * This is used after a successful Search so the solver receives the actual
     * book ID rather than an arbitrary book name.
     */
    private Book findKnownBookInInventory()
    {
        return Rs2Inventory.items()
                .filter(item -> item != null)
                .map(item -> Book.byId(item.getId()))
                .filter(book -> book != null)
                .findFirst()
                .orElse(null);
    }

    private Map<Integer, Integer> snapshotBookCounts()
    {
        Map<Integer, Integer> counts = new HashMap<>();
        Rs2Inventory.items()
                .filter(item -> item != null && Book.byId(item.getId()) != null)
                .forEach(item -> counts.merge(item.getId(), 1, Integer::sum));
        return counts;
    }

    private Book findNewKnownBook(Map<Integer, Integer> before)
    {
        return Rs2Inventory.items()
                .filter(item -> item != null && Book.byId(item.getId()) != null)
                .filter(item -> before.getOrDefault(item.getId(), 0) <
                        Rs2Inventory.items().filter(i -> i != null && i.getId() == item.getId()).count())
                .map(item -> Book.byId(item.getId()))
                .filter(book -> book != null)
                .findFirst()
                .orElse(null);
    }

    private boolean bookInventoryChanged(Map<Integer, Integer> before)
    {
        Map<Integer, Integer> now = snapshotBookCounts();
        return !now.equals(before);
    }

    // The central middle room has no ground-floor exit. Route by source room,
    // including same-plane destinations and restart/turn-in recovery.
    private int middleRoom(WorldPoint point)
    {
        if (point.getY() > 3815)
            return point.getX() < 1625 ? 1 : 2;
        return point.getX() < 1625 ? 0 : 3;
    }

    private Stair roomStair(Stair[] stairs, int room)
    {
        String name = new String[]{"South", "NW", "NE", "Central"}[room];
        for (Stair stair : stairs)
            if (stair.name.contains(name)) return stair;
        return null;
    }

    private Stair nearestGroundReturnStair(WorldPoint player)
    {
        Stair nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Stair stair : TOP_DOWN_STAIRS)
        {
            // The central middle room has no route to the ground floor.
            if (stair.name.contains("Central"))
                continue;

            int distance = player.distanceTo(stair.tile);
            if (distance < nearestDistance)
            {
                nearest = stair;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void handleNavigateFloors()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return;
        boolean returning = hasTargetBookInInventory() || !customerLocked;
        Bookcase target = returning ? null : getTargetBookcase();
        if (!returning && target == null) return;
        int goalPlane = returning ? 0 : target.getLocation().getPlane();
        int goalRoom = returning ? 0 : middleRoom(target.getLocation());
        int plane = player.getPlane();
        int room = middleRoom(player);
        if (plane == goalPlane && (plane != 1 || room == goalRoom)) return;

        Stair stair;
        if (plane == 0)
            stair = roomStair(BOTTOM_UP_STAIRS, goalRoom == 3 ? 0 : goalRoom);
        else if (plane == 1)
        {
            // Exit through the staircase in the room we actually occupy.
            stair = goalPlane == 0 && room != 3
                    ? roomStair(MIDDLE_DOWN_STAIRS, room)
                    : roomStair(MIDDLE_UP_STAIRS, room);
        }
        else
            stair = goalPlane == 0
                    ? nearestGroundReturnStair(player)
                    : roomStair(TOP_DOWN_STAIRS, goalRoom);
        if (stair != null)
            moveToAndUseStair(stair, stair.destinationPlane > plane ? "Climb-up" : "Climb-down");
    }

    private void returnToCustomers()
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || !isInLibrary()) return;
        if (player.getPlane() != 0) handleNavigateFloors();
        else if (player.distanceTo(LIBRARY_CENTER) > 3) localWalk(LIBRARY_CENTER);
    }

    private boolean localWalk(WorldPoint destination)
    {
        if (destination == null)
            return false;

        if (!isInsideLibraryBounds(destination))
        {
            lastAction = "Blocked walk outside library: " + destination;
            logger.warn("[DroLibraryScript] Refusing out-of-library local walk from {} to {}",
                    Rs2Player.getWorldLocation(), destination);
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || player.getPlane() != destination.getPlane())
            return false;

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()
                || System.currentTimeMillis() - lastWalkTime < 1800) return false;
        lastWalkTime = System.currentTimeMillis();
        if (Rs2Player.getRunEnergy() < 40 && !Rs2Player.hasStaminaBuffActive()
                && !Rs2Dialogue.isInDialogue()
                && System.currentTimeMillis() - lastStaminaAttempt > 15000)
        {
            var potion = Rs2Inventory.items().filter(i -> i != null && !i.isNoted()
                    && i.getName().toLowerCase().startsWith("stamina potion(")).findFirst().orElse(null);
            if (potion != null)
            {
                lastStaminaAttempt = System.currentTimeMillis();
                pauseBeforeInput();
                Rs2Inventory.interact(potion.getId(), "Drink");
                return false;
            }
        }
        if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() >= runEnablePercent)
        {
            pauseBeforeInput();
            Rs2Player.toggleRunEnergy(true);
            runEnablePercent = ThreadLocalRandom.current()
                    .nextInt(MIN_RUN_ENABLE_PERCENT, MAX_RUN_ENABLE_PERCENT + 1);
        }
        WorldPoint step = destination;
        // Leave the NE ground wing through the corridor before heading south.
        if (player.getPlane() == 0 && player.getX() >= 1642 && player.getY() >= 3814
                && (destination.getX() < 1642 || destination.getY() < 3814))
            step = new WorldPoint(1636, 3816, 0);
        int dx = step.getX() - player.getX(), dy = step.getY() - player.getY();
        int span = Math.max(Math.abs(dx), Math.abs(dy));
        if (span > 10)
            step = new WorldPoint(player.getX() + (int)Math.round(dx * 10.0 / span),
                    player.getY() + (int)Math.round(dy * 10.0 / span), player.getPlane());
        if (!isInsideLibraryBounds(step))
        {
            lastAction = "Blocked unsafe local step: " + step;
            logger.warn("[DroLibraryScript] Refusing unsafe local step {}", step);
            return false;
        }
        pauseBeforeInput();
        boolean clicked = Rs2Walker.walkFastCanvas(step);
        if (!clicked)
            logger.warn("[DroLibraryScript] LOCAL WALK failed from {} to {}", player, destination);
        return clicked;
    }

    private void moveToAndUseStair(Stair stair, String action)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null || stair == null || player.getPlane() != stair.tile.getPlane())
            return;

        if (player.distanceTo(stair.tile) > 2)
        {
            lastAction = "Walking to " + stair.name;
            localWalk(stair.tile);
            return;
        }

        interactStair(stair, action);
    }

    private void interactStair(Stair stair, String action)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
            return;

        if (player.getPlane() != stair.tile.getPlane())
            return;

        int distance = player.distanceTo(stair.tile);
        if (distance > 2)
        {
            lastAction = "Walking to " + stair.name;
            localWalk(stair.tile);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()
                || System.currentTimeMillis() - lastStairInteraction < STAIR_RETRY_DELAY_MS)
            return;

        lastAction = action + " " + stair.name;
        logger.info("[DroLibraryScript] STAIRS: floor={} targetFloor={} id={} tile={} action={}",
                player.getPlane(), stair.destinationPlane, stair.id, stair.tile, action);

        if (interactExactStair(stair, action))
        {
            lastStairInteraction = System.currentTimeMillis();
            lastStairTile = stair.tile;
            sleepUntil(() ->
                            Rs2Player.getWorldLocation() != null
                                    && Rs2Player.getWorldLocation().getPlane() == stair.destinationPlane,
                    6000);

            WorldPoint after = Rs2Player.getWorldLocation();
            logger.info("[DroLibraryScript] STAIRS: after interaction player={} expectedPlane={}",
                    after, stair.destinationPlane);
        }
        else
        {
            // A rejected/off-canvas click gets a short retry without slowing a
            // correctly visible staircase interaction.
            lastStairInteraction = System.currentTimeMillis();
            logger.info("[DroLibraryScript] STAIRS: interaction returned false id={} tile={}",
                    stair.id, stair.tile);
        }
    }

    /**
     * Interact with the staircase object itself, never a generic tile point.
     * A clipped or tiny clickbox is brought into view first and retried on the
     * next loop; no speculative click is sent to the canvas.
     */
    private boolean interactExactStair(Stair stair, String action)
    {
        // The supplied map is authoritative. Tile interaction correctly
        // resolves the paired stair objects used by this game.
        pauseBeforeInput();
        return Rs2GameObject.interact(stair.tile, action);
    }

    private boolean hasSafeClickbox(TileObject object)
    {
        if (object == null)
            return false;

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            java.awt.Shape clickbox = object.getClickbox();
            if (clickbox == null)
                return false;

            java.awt.Rectangle bounds = clickbox.getBounds();
            int canvasWidth = Microbot.getClient().getCanvasWidth();
            int canvasHeight = Microbot.getClient().getCanvasHeight();
            java.awt.Rectangle safeCanvas = new java.awt.Rectangle(
                    4, 4, Math.max(0, canvasWidth - 8), Math.max(0, canvasHeight - 8));
            return bounds.width >= 6 && bounds.height >= 6 && safeCanvas.contains(bounds);
        }).orElse(false);
    }

    /**
     * Reject missing, clipped, and tiny NPC clickboxes before sending a mouse
     * click. This prevents the fallback point from landing on the scene behind
     * the customer when models overlap or are partly outside the canvas.
     */
    private boolean hasSafeNpcClickbox(NPC npc)
    {
        if (npc == null)
            return false;

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            java.awt.Rectangle bounds = Rs2UiHelper.getActorClickbox(npc);
            if (bounds == null)
                return false;

            int canvasWidth = Microbot.getClient().getCanvasWidth();
            int canvasHeight = Microbot.getClient().getCanvasHeight();
            java.awt.Rectangle safeCanvas = new java.awt.Rectangle(
                    4, 4, Math.max(0, canvasWidth - 8), Math.max(0, canvasHeight - 8));
            return bounds.width >= 6 && bounds.height >= 6 && safeCanvas.contains(bounds);
        }).orElse(false);
    }

    private boolean interactCustomer(NPC npc, String action)
    {
        if (!hasSafeNpcClickbox(npc))
        {
            long now = System.currentTimeMillis();
            if (now - lastNpcCameraAttempt >= 1000L)
            {
                lastNpcCameraAttempt = now;
                pauseBeforeInput();
                Rs2Camera.turnTo(npc);
            }
            lastAction = "Centering " + customerName(npc.getId()) + " before clicking";
            return false;
        }

        pauseBeforeInput();
        return Rs2Npc.interact(npc, action);
    }

    private void handleSearchShelf()
    {
        if (!customerLocked || currentTargetBook == null)
            return;

        /* The exact requested item ID is the hard stop for searching. */
        if (hasTargetBookInInventory())
        {
            lastAction = "BOOK ID " + currentTargetBook.getItem()
                    + " FOUND - returning to " + customerName(currentCustomerId);
            logger.info("[DroLibraryScript] INVENTORY: requested book detected id={} name={}",
                    currentTargetBook.getItem(), currentTargetBook.getShortName());
            return;
        }

        Bookcase targetCase = getTargetBookcase();
        if (targetCase == null)
        {
            lastAction = "No target bookcase";
            return;
        }

        WorldPoint targetTile = targetCase.getLocation();
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
            return;

        if (player.getPlane() != targetTile.getPlane())
            return;

        if (player.distanceTo(targetTile) > 2)
        {
            lastAction = "Walking to bookcase";
            localWalk(targetTile);
            return;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving())
            return;

        lastAction = "Searching bookcase";
        logger.info("[DroLibraryScript] Searching bookcase at {} for {}",
                targetTile, currentTargetBook.getShortName());

        if (Rs2Inventory.isFull())
        {
            lastAction = "Inventory full - make room for a book";
            return;
        }
        // Only a confirmed result event may mark a shelf empty. A click or
        // inventory timeout is not evidence (duplicates show an object dialog).
        retryAfter.put(targetTile, System.currentTimeMillis() + 30000);
        pauseBeforeInput();
        if (!Rs2GameObject.interact(targetTile, "Search"))
        {
            lastAction = "Search interaction failed";
            return;
        }
        sleepUntil(() -> !observations.isEmpty() || hasTargetBookInInventory(), 6000);
        drainObservations();
    }

    private boolean isBookshelfVisible(WorldPoint tile)
    {
        return Rs2GameObject.getAll(o -> o != null
                        && tile.equals(o.getWorldLocation()))
                .stream()
                .anyMatch(this::hasSafeClickbox);
    }

    /** Distance from a shelf to the forward route segment, or infinity when behind/beyond it. */
    private double distanceToRoute(WorldPoint shelf, WorldPoint from, WorldPoint destination)
    {
        if (shelf == null || from == null || destination == null
                || shelf.getPlane() != from.getPlane()
                || destination.getPlane() != from.getPlane())
            return Double.POSITIVE_INFINITY;

        double routeX = destination.getX() - from.getX();
        double routeY = destination.getY() - from.getY();
        double lengthSquared = routeX * routeX + routeY * routeY;
        if (lengthSquared < 1.0)
            return Double.POSITIVE_INFINITY;

        double projection = ((shelf.getX() - from.getX()) * routeX
                + (shelf.getY() - from.getY()) * routeY) / lengthSquared;
        if (projection < 0.0 || projection > 1.0)
            return Double.POSITIVE_INFINITY;

        double routePointX = from.getX() + projection * routeX;
        double routePointY = from.getY() + projection * routeY;
        return Math.hypot(shelf.getX() - routePointX, shelf.getY() - routePointY);
    }

    private Bookcase getTargetBookcase()
    {
        if (currentTargetBook == null)
            return null;

        WorldPoint player = Rs2Player.getWorldLocation();
        synchronized (library)
        {
            Bookcase best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Bookcase shelf : library.getBookcases())
            {
                if (retryAfter.getOrDefault(shelf.getLocation(), 0L) > System.currentTimeMillis())
                    continue;
                boolean exact = shelf.getBook() == currentTargetBook;
                boolean candidate = shelf.getPossibleBooks().contains(currentTargetBook);
                if (!exact && shelf.isBookSet()) continue;
                if (!exact && library.getState() != SolvedState.NO_DATA
                        && shelf.getPossibleBooks().isEmpty()) continue;
                int priority = exact ? 0 : candidate ? 1 : 2;
                WorldPoint tile = shelf.getLocation();
                int distance = player == null ? 0 : Math.abs(player.getX()-tile.getX())
                        + Math.abs(player.getY()-tile.getY())
                        + (player.getPlane() == tile.getPlane() ? 0 : 100)
                        + (player.getPlane() == 1 && tile.getPlane() == 1
                        && middleRoom(player) != middleRoom(tile) ? 200 : 0);
                int score = priority * 10000 + distance;
                if (score < bestScore) { best = shelf; bestScore = score; }
            }
            if (library.getState() == SolvedState.COMPLETE
                    && player != null && best != null && Rs2Inventory.items().count() < 27
                    && best.getLocation().getPlane() == player.getPlane()
                    && (player.getPlane() != 1
                    || middleRoom(player) == middleRoom(best.getLocation())))
            {
                Bookcase nearbyBookcase = null;
                double nearbyScore = Double.POSITIVE_INFINITY;
                for (Bookcase shelf : library.getBookcases())
                {
                    Book known = shelf.getBook();
                    if (known == null && library.getState() == SolvedState.COMPLETE
                            && shelf.getPossibleBooks().size() == 1)
                        known = shelf.getPossibleBooks().iterator().next();
                    if (known == null || known == currentTargetBook || Rs2Inventory.contains(known.getItem())) continue;
                    if (retryAfter.getOrDefault(shelf.getLocation(), 0L) > System.currentTimeMillis()) continue;
                    WorldPoint tile = shelf.getLocation();
                    if (tile.getPlane() != player.getPlane()
                            || (player.getPlane() == 1 && middleRoom(player) != middleRoom(tile))) continue;
                    double routeDistance = distanceToRoute(tile, player, best.getLocation());
                    if (routeDistance > pathBookRadius || !isBookshelfVisible(tile)) continue;

                    // Prefer the smallest diversion, then the closest visible shelf.
                    double score = routeDistance * 100.0 + player.distanceTo(tile);
                    if (score < nearbyScore)
                    {
                        nearbyBookcase = shelf;
                        nearbyScore = score;
                    }
                }
                if (nearbyBookcase != null)
                {
                    logger.info("[DroLibraryScript] PATH BOOK: collecting {} at {} within {}-tile route radius",
                            nearbyBookcase.getBook() == null ? "known book" : nearbyBookcase.getBook().getShortName(),
                            nearbyBookcase.getLocation(), pathBookRadius);
                    return nearbyBookcase;
                }
            }
            return best;
        }
    }

    private void handleTurnIn()
    {
        if (!customerLocked || currentCustomerId == -1 || currentTargetBook == null)
            return;

        checkTurnInCompletion();
        if (!customerLocked) return;
        if (hasTargetBookInInventory()) beginTurnInTracking();

        /* If the item vanished, do not assume completion; wait for the dialogue. */
        NPC npc = Rs2Npc.getNpc(currentCustomerId);
        if (npc == null)
        {
            lastAction = "Returning to customer area for " + customerName(currentCustomerId);
            returnToCustomers();
            return;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null)
            return;

        if (Rs2Dialogue.isInDialogue())
        {
            String text = Rs2Dialogue.getDialogueText();
            if (text != null)
                logger.info("[DroLibraryScript] Turn-in dialogue with {}: {}",
                        customerName(currentCustomerId), text);

            checkTurnInCompletion();
            clickContinue();
            sleep(500);

            checkTurnInCompletion();
            return;
        }

        if (player.distanceTo(npc.getWorldLocation()) > 3)
        {
            lastAction = "Returning to " + customerName(currentCustomerId);
            localWalk(npc.getWorldLocation());
            return;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving())
            return;

        lastAction = "Turning in to " + customerName(currentCustomerId);
        logger.info("[DroLibraryScript] TURN IN: book={} itemId={} customer={}({})",
                currentTargetBook.getShortName(), currentTargetBook.getItem(),
                customerName(currentCustomerId), currentCustomerId);

        if (System.currentTimeMillis() - lastTurnInAttempt < 4000) return;
        beginTurnInTracking();
        dialogueCustomerId = currentCustomerId;
        lastTurnInAttempt = System.currentTimeMillis();
        if (interactCustomer(npc, "Help"))
            sleepUntil(Rs2Dialogue::isInDialogue, 3000);
        checkTurnInCompletion();
    }

    private void resetAssignment()
    {
        turnInPending = false;
        int completedCustomerIndex = customerIndexForId(currentCustomerId);
        currentCustomerId = -1;
        currentTargetBook = null;
        customerLocked = false;
        customerSearchIndex = (completedCustomerIndex + 1) % CUSTOMER_IDS.length;
        dialogueCustomerId = -1;
        rotateCustomerAfterDialogue = false;
        lastStairTile = null;
        lastStairInteraction = 0L;
        library.setCustomer(-1, null);
        lastAction = "Assignment complete";
    }

    private void handleArcaneBook()
    {
        String dialogueText = Rs2Dialogue.getDialogueText();
        boolean prompt = dialogueText != null
                && (dialogueText.contains("What kind of knowledge will you learn?")
                || Rs2Dialogue.hasSelectAnOption());

        if (prompt)
        {
            String skill = config.rewardType() == DroLibraryConfig.RewardType.MAGIC
                    ? "Magic" : "Runecraft";
            pauseBeforeInput();
            boolean selected = Rs2Dialogue.clickOption(skill);
            lastAction = selected ? "Selected " + skill + " reward" : "Waiting for " + skill + " option";
            logger.info("[DroLibraryScript] REWARD skill={} selected={} options={}", skill, selected,
                    Rs2Dialogue.getDialogueOptions().stream().map(w -> w.getText())
                            .collect(java.util.stream.Collectors.toList()));
            sleep(1000);
            return;
        }

        if (Rs2Inventory.contains(BOOK_OF_ARCANE_KNOWLEDGE))
        {
            lastAction = "Reading Arcane Knowledge";
            pauseBeforeInput();
            Rs2Inventory.interact(BOOK_OF_ARCANE_KNOWLEDGE, "Read");
            sleep(1000);
        }
    }

    private boolean isInLibrary()
    {
        return isInsideLibraryBounds(Rs2Player.getWorldLocation());
    }

    private boolean isInsideLibraryBounds(WorldPoint point)
    {
        return point != null
                && point.getX() >= LIBRARY_MIN_X
                && point.getX() <= LIBRARY_MAX_X
                && point.getY() >= LIBRARY_MIN_Y
                && point.getY() <= LIBRARY_MAX_Y
                && point.getPlane() >= 0
                && point.getPlane() <= 2;
    }

    private String customerName(int id)
    {
        switch (id)
        {
            case NPC_VILLIA:
                return "Villia";
            case NPC_PROFESSOR_GRACKLEBONE:
                return "Professor Gracklebone";
            case NPC_SAM:
                return "Sam";
            default:
                return "Unknown (" + id + ")";
        }
    }

    private void logStatus(State state)
    {
        WorldPoint player = Rs2Player.getWorldLocation();
        logger.info("[DroLibraryScript] STATE={} | player={} | customer={}({}) | locked={} | target={} | targetId={} | action={}",
                state,
                player,
                customerName(currentCustomerId),
                currentCustomerId,
                customerLocked,
                currentTargetBook == null ? "NONE" : currentTargetBook.getShortName(),
                currentTargetBook == null ? -1 : currentTargetBook.getItem(),
                lastAction);
    }

    public Book getCurrentTargetBook()
    {
        return currentTargetBook;
    }

    public String getCurrentState()
    {
        return currentStateStr;
    }

    public String getSolverState()
    {
        return library != null && library.getState() != null
                ? library.getState().toString()
                : "UNKNOWN";
    }

    public int getSearchedBookcaseCount()
    {
        if (library == null || library.getBookcases() == null)
            return 0;

        int count = 0;
        for (Bookcase bookcase : library.getBookcases())
        {
            if (bookcase.isBookSet())
                count++;
        }
        return count;
    }

    public Bookcase getDebugTargetBookcase()
    {
        return getTargetBookcase();
    }

    public String getLastSearchResult()
    {
        return lastSearchResult;
    }

    public String getPlayerLocationString()
    {
        WorldPoint location = Rs2Player.getWorldLocation();
        return location == null ? "UNKNOWN" : location.toString();
    }

    public int getCurrentCustomerId()
    {
        return currentCustomerId;
    }

    public String getCurrentCustomerName()
    {
        return customerName(currentCustomerId);
    }

    public boolean isCustomerLocked()
    {
        return customerLocked;
    }

    public String getLastAction()
    {
        return lastAction;
    }
}

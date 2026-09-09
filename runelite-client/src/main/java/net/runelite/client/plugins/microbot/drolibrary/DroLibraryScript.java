package net.runelite.client.plugins.microbot.drolibrary;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.plugins.kourendlibrary.KourendLibraryPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class DroLibraryScript extends Script {

    private static final int NPC_VILLIA = 7047;
    private static final int NPC_PROFESSOR_GRACKLEBONE = 7048;
    private static final int NPC_SAM = 7049;

    private static final int BOOK_OF_ARCANE_KNOWLEDGE = 13513;
    private static final int[] BOOKSHELF_IDS = { 27991, 27996, 28197, 28199, 28195, 28111 };
    private static final int[] STAIRCASE_IDS = { 27834, 27840, 27842, 27843, 27846, 27847, 27850 };

    private static final Pattern BOOK_NAME_PATTERN = Pattern.compile("'(.*?)'");

    private String currentTargetBook = null;
    private int currentTargetNpcId = -1;
    private final int[] customerIds = { NPC_VILLIA, NPC_PROFESSOR_GRACKLEBONE, NPC_SAM };
    private int customerIndex = 0;
    private long lastCustomerSwitchTime = 0;
    private long lastSearchTime = 0;
    private KourendLibraryPlugin libraryPlugin = null;

    @Inject
    private DroLibraryConfig config;

    public enum State {
        USE_ARCANE_BOOK,
        TURN_IN_BOOK,
        GET_ASSIGNMENT,
        NAVIGATE_FLOORS,
        SEARCH_SHELF
    }

    public boolean run() {
        try {
            this.libraryPlugin = RuneLite.getInjector().getInstance(KourendLibraryPlugin.class);
        } catch (Exception e) {
            log.error("Failed to inject KourendLibraryPlugin", e);
            this.libraryPlugin = null;
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                // Check inventory for any valid library books to turn in proactively
                checkInventoryForBooks();
                checkForActiveAssignment();

                State state = getState();

                switch (state) {
                    case USE_ARCANE_BOOK:
                        handleArcaneBook();
                        break;

                    case TURN_IN_BOOK:
                        handleTurnIn();
                        break;

                    case GET_ASSIGNMENT:
                        handleGetAssignment();
                        break;

                    case NAVIGATE_FLOORS:
                        WorldPoint targetTile = getPluginBookTile(currentTargetBook);
                        if (targetTile != null) {
                            navigateToFloorAndRegion(targetTile);
                        }
                        break;

                    case SEARCH_SHELF:
                        WorldPoint shelfTile = getPluginBookTile(currentTargetBook);
                        if (shelfTile != null) {
                            if (Rs2Player.getWorldLocation().distanceTo(shelfTile) > 2) {
                                Rs2Walker.walkTo(shelfTile);
                                lastSearchTime = 0;
                            } else {
                                if (System.currentTimeMillis() - lastSearchTime < 1800) {
                                    return; // Wait for search action / animation
                                }
                                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                                    return;
                                }
                                TileObject shelf = Rs2GameObject.getTileObject(shelfTile);
                                if (shelf != null && containsId(BOOKSHELF_IDS, shelf.getId())) {
                                    if (Rs2GameObject.interact(shelf)) {
                                        lastSearchTime = System.currentTimeMillis();
                                        sleep(600);
                                    }
                                } else {
                                    // Fallback to searching nearby bookshelf object if exact tile object is null
                                    TileObject nearbyShelf = Rs2GameObject.getGameObject(r -> containsId(BOOKSHELF_IDS, r.getId()));
                                    if (nearbyShelf != null) {
                                        if (Rs2GameObject.interact(nearbyShelf)) {
                                            lastSearchTime = System.currentTimeMillis();
                                            sleep(600);
                                        }
                                    }
                                }
                            }
                        } else {
                            WorldPoint center = new WorldPoint(1629, 3801, 0);
                            if (Rs2Player.getWorldLocation().distanceTo(center) > 10) {
                                Rs2Walker.walkTo(center);
                            } else {
                                TileObject randomShelf = Rs2GameObject.getGameObject(r -> containsId(BOOKSHELF_IDS, r.getId()));
                                if (randomShelf != null && !Rs2Player.isAnimating()) {
                                    Rs2GameObject.interact(randomShelf);
                                    sleep(600);
                                }
                            }
                        }
                        break;
                }

            } catch (Exception ex) {
                log.error("[DroLibraryScript] Unexpected error: ", ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean isInLibrary() {
        WorldPoint p = Rs2Player.getWorldLocation();
        return p.getX() >= 1590 && p.getX() <= 1675 && p.getY() >= 3770 && p.getY() <= 3865;
    }

    private void checkInventoryForBooks() {
        if (libraryPlugin == null) return;
        try {
            java.lang.reflect.Method getLibraryMethod = libraryPlugin.getClass().getMethod("getLibrary");
            Object library = getLibraryMethod.invoke(libraryPlugin);
            if (library != null) {
                var items = Rs2Inventory.items().collect(Collectors.toList());
                for (var item : items) {
                    if (item == null) continue;
                    String itemName = item.getName();
                    if (itemName == null || itemName.equals("Book of arcane knowledge")) continue;

                    java.lang.reflect.Method getBookMethod = library.getClass().getMethod("getBook", String.class);
                    Object book = getBookMethod.invoke(library, itemName);
                    if (book != null) {
                        if (currentTargetBook == null || !currentTargetBook.equals(itemName)) {
                            currentTargetBook = itemName;
                            log.info("Found requested/library book in inventory: {}", itemName);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to check inventory for books", e);
        }
    }

    private void checkForActiveAssignment() {
        if (currentTargetBook != null && Rs2Inventory.contains(currentTargetBook)) return;

        String dialogueText = Rs2Dialogue.getDialogueText();
        if (dialogueText != null) {
            Matcher matcher = BOOK_NAME_PATTERN.matcher(dialogueText);
            if (matcher.find()) {
                currentTargetBook = matcher.group(1);
            }
        }
    }

    private State getState() {
        WorldPoint currentPos = Rs2Player.getWorldLocation();

        String dialogueText = Rs2Dialogue.getDialogueText();
        boolean hasArcanePrompt = dialogueText != null && (dialogueText.contains("What kind of knowledge will you learn?") || Rs2Dialogue.hasSelectAnOption());

        if (Rs2Inventory.contains(BOOK_OF_ARCANE_KNOWLEDGE) || (Rs2Dialogue.isInDialogue() && hasArcanePrompt)) {
            return State.USE_ARCANE_BOOK;
        }

        if (currentTargetBook != null && Rs2Inventory.contains(currentTargetBook)) {
            return State.TURN_IN_BOOK;
        }

        if (currentTargetBook == null || !isInLibrary()) {
            return State.GET_ASSIGNMENT;
        }

        WorldPoint targetTile = getPluginBookTile(currentTargetBook);
        if (targetTile != null) {
            int playerPlane = Rs2Player.getWorldLocation().getPlane();
            if (playerPlane != targetTile.getPlane() || isIsolatedMiddleRoomTransition(Rs2Player.getWorldLocation(), targetTile)) {
                return State.NAVIGATE_FLOORS;
            }
            return State.SEARCH_SHELF;
        }

        return State.SEARCH_SHELF;
    }

    private WorldPoint getPluginBookTile(String bookName) {
        if (bookName == null) return null;

        if (libraryPlugin == null) {
            try {
                this.libraryPlugin = RuneLite.getInjector().getInstance(KourendLibraryPlugin.class);
            } catch (Exception e) {
                log.warn("Re-hooking KourendLibraryPlugin failed");
            }
        }

        if (libraryPlugin != null) {
            try {
                java.lang.reflect.Method getLibraryMethod = libraryPlugin.getClass().getMethod("getLibrary");
                Object library = getLibraryMethod.invoke(libraryPlugin);
                if (library != null) {
                    java.lang.reflect.Method getBookMethod = library.getClass().getMethod("getBook", String.class);
                    Object book = getBookMethod.invoke(library, bookName);

                    if (book != null) {
                        java.lang.reflect.Method getLocationMethod = book.getClass().getMethod("getLocation");
                        return (WorldPoint) getLocationMethod.invoke(book);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch book location via reflection", e);
            }
        }
        return null;
    }

    private void navigateToFloorAndRegion(WorldPoint target) {
        WorldPoint current = Rs2Player.getWorldLocation();

        if (target.getPlane() == 1 && isIsolatedMiddleRoom(target)) {
            if (current.getPlane() == 1 && !isInSameIsolatedRoom(current, target)) {
                useNearestStaircase("Climb-down");
                return;
            }
        }

        if (current.getPlane() < target.getPlane()) {
            useNearestStaircase("Climb-up");
        } else if (current.getPlane() > target.getPlane()) {
            useNearestStaircase("Climb-down");
        } else {
            Rs2Walker.walkTo(target);
        }
    }

    private void useNearestStaircase(String action) {
        for (int id : STAIRCASE_IDS) {
            TileObject stairs = Rs2GameObject.getGameObject(id);
            if (stairs != null) {
                Rs2GameObject.interact(stairs, action);
                sleep(1000);
                break;
            }
        }
    }

    private boolean isIsolatedMiddleRoom(WorldPoint point) {
        if (point.getPlane() != 1) return false;
        boolean inNW = (point.getX() < 1625 && point.getY() > 3815);
        boolean inNE = (point.getX() > 1638 && point.getY() > 3815);
        return inNW || inNE;
    }

    private boolean isInSameIsolatedRoom(WorldPoint p1, WorldPoint p2) {
        return (p1.getX() < 1625 && p2.getX() < 1625) || (p1.getX() > 1638 && p2.getX() > 1638);
    }

    private boolean isIsolatedMiddleRoomTransition(WorldPoint current, WorldPoint target) {
        return current.getPlane() == 1 && target.getPlane() == 1 && isIsolatedMiddleRoom(target) && !isInSameIsolatedRoom(current, target);
    }

    private void handleArcaneBook() {
        String dialogueText = Rs2Dialogue.getDialogueText();
        if ((dialogueText != null && dialogueText.contains("What kind of knowledge will you learn?")) || Rs2Dialogue.hasSelectAnOption()) {
            if (config.rewardType() == DroLibraryConfig.RewardType.MAGIC) {
                Rs2Dialogue.clickOption("1"); // Option 1 for Magic
            } else {
                Rs2Dialogue.clickOption("2"); // Option 2 for Runecrafting
            }
            sleep(600);
            return;
        }
        if (Rs2Inventory.contains(BOOK_OF_ARCANE_KNOWLEDGE)) {
            Rs2Inventory.interact(BOOK_OF_ARCANE_KNOWLEDGE, "Read");
            sleep(600);
        }
    }

    private void handleTurnIn() {
        int npcIdToUse = currentTargetNpcId != -1 ? currentTargetNpcId : NPC_PROFESSOR_GRACKLEBONE;
        Rs2NpcModel npc = Microbot.getRs2NpcCache().getStream()
                .filter(n -> n.getId() == npcIdToUse)
                .findFirst()
                .orElse(null);

        if (npc != null) {
            if (Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation()) > 3) {
                Rs2Walker.walkTo(npc.getWorldLocation());
                return;
            }

            if (Rs2Npc.interact(npc.getId(), "Help")) {
                sleep(600);
                if (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    currentTargetBook = null;
                    currentTargetNpcId = -1;
                }
            }
        } else {
            Rs2Walker.walkTo(getNpcDefaultLocation(npcIdToUse));
        }
    }

    private void handleGetAssignment() {
        if (!isInLibrary()) {
            Rs2Walker.walkTo(new WorldPoint(1629, 3801, 0));
            return;
        }

        int targetNpcId = customerIds[customerIndex];
        Rs2NpcModel npc = Microbot.getRs2NpcCache().getStream()
                .filter(n -> n.getId() == targetNpcId)
                .findFirst()
                .orElse(null);

        if (npc != null) {
            if (Rs2Player.getWorldLocation().distanceTo(npc.getWorldLocation()) > 3) {
                Rs2Walker.walkTo(npc.getWorldLocation());
                return;
            }

            if (Rs2Npc.interact(npc.getId(), "Help")) {
                sleep(1000);
                String dialogueText = Rs2Dialogue.getDialogueText();
                if (dialogueText != null) {
                    if (dialogueText.contains("helping someone else")) {
                        Rs2Dialogue.clickContinue();
                        customerIndex = (customerIndex + 1) % customerIds.length;
                        return;
                    }

                    Matcher matcher = BOOK_NAME_PATTERN.matcher(dialogueText);
                    if (matcher.find()) {
                        currentTargetBook = matcher.group(1);
                        currentTargetNpcId = targetNpcId;
                        Rs2Dialogue.clickContinue();
                        return;
                    }
                }
                Rs2Dialogue.clickContinue();
            }
        } else {
            Rs2Walker.walkTo(getNpcDefaultLocation(targetNpcId));
            return;
        }

        if (System.currentTimeMillis() - lastCustomerSwitchTime > 2500) {
            customerIndex = (customerIndex + 1) % customerIds.length;
            lastCustomerSwitchTime = System.currentTimeMillis();
        }
    }

    private WorldPoint getNpcDefaultLocation(int npcId) {
        switch (npcId) {
            case NPC_VILLIA:
                return new WorldPoint(1611, 3801, 0);
            case NPC_SAM:
                return new WorldPoint(1648, 3801, 0);
            case NPC_PROFESSOR_GRACKLEBONE:
            default:
                return new WorldPoint(1629, 3801, 0);
        }
    }

    private boolean containsId(int[] array, int id) {
        for (int i : array) {
            if (i == id) return true;
        }
        return false;
    }

    public String getCurrentTargetBook() {
        return currentTargetBook;
    }
}
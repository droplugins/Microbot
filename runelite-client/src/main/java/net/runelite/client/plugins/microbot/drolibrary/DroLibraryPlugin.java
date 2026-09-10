package net.runelite.client.plugins.microbot.drolibrary;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = "[Dro] Library",
        description = "Native automated Kourend Library assistant plugin utilizing the internal solver",
        tags = {"kourend", "library", "microbot", "dro"},
        enabledByDefault = false
)
@Slf4j
public class DroLibraryPlugin extends Plugin {

    @Inject
    private DroLibraryConfig config;

    @Provides
    DroLibraryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DroLibraryConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DroLibraryOverlay overlay;

    @Inject
    private DroLibraryScript droLibraryScript;

    @Inject
    private net.runelite.api.Client client;

    private net.runelite.api.coords.WorldPoint clickedShelf;
    private net.runelite.api.coords.WorldPoint animatedShelf;
    private int searchTick;

    private void clearSearch()
    {
        clickedShelf = null;
        animatedShelf = null;
    }

    @net.runelite.client.eventbus.Subscribe
    public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
    {
        if (event.getMenuAction() == net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION
                && event.getMenuTarget().contains("Bookshelf"))
        {
            clearSearch();
            clickedShelf = net.runelite.api.coords.WorldPoint.fromScene(client,
                    event.getParam0(), event.getParam1(), client.getPlane());
            searchTick = client.getTickCount();
        }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onAnimationChanged(net.runelite.api.events.AnimationChanged event)
    {
        if (event.getActor() == client.getLocalPlayer()
                && event.getActor().getAnimation() == net.runelite.api.gameval.AnimationID.HUMAN_PICKUPTABLE
                && clickedShelf != null && client.getTickCount() - searchTick <= 12
                && client.getLocalPlayer().getWorldLocation().distanceTo(clickedShelf) <= 2)
        {
            animatedShelf = clickedShelf;
            clickedShelf = null;
            searchTick = client.getTickCount();
        }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onGameTick(net.runelite.api.events.GameTick event)
    {
        if (client.getTickCount() - searchTick > 12) clearSearch();
        if (animatedShelf == null) return;
        net.runelite.api.widgets.Widget result = client.getWidget(
                net.runelite.api.gameval.InterfaceID.Objectbox.ITEM);
        if (result != null && !result.isHidden())
        {
            Book book = Book.byId(result.getItemId());
            if (book != null)
            {
                droLibraryScript.observeShelf(animatedShelf, book);
                clearSearch();
            }
        }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onChatMessage(net.runelite.api.events.ChatMessage event)
    {
        if (event.getType() != net.runelite.api.ChatMessageType.GAMEMESSAGE) return;
        String text = event.getMessage().replaceAll("<[^>]*>", "");
        if (text.contains("You hear the shifting of books"))
        {
            clearSearch();
            droLibraryScript.observeReset();
        }
        else if (animatedShelf != null && client.getTickCount() - searchTick <= 12
                && text.equals("You don't find anything useful here."))
        {
            droLibraryScript.observeShelf(animatedShelf, null);
            clearSearch();
        }
    }

    @net.runelite.client.eventbus.Subscribe
    public void onGameStateChanged(net.runelite.api.events.GameStateChanged event)
    {
        if (event.getGameState() == net.runelite.api.GameState.HOPPING
                || event.getGameState() == net.runelite.api.GameState.LOGIN_SCREEN)
        {
            clearSearch();
            droLibraryScript.observeReset();
        }
    }

    @Override
    protected void startUp() throws Exception {
        clearSearch();
        overlayManager.add(overlay);
        droLibraryScript.run();
    }

    @Override
    protected void shutDown() throws Exception {
        clearSearch();
        overlayManager.remove(overlay);
        droLibraryScript.shutdown();
    }
}
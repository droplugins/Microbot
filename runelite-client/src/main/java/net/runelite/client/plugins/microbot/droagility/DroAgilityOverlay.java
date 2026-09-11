package net.runelite.client.plugins.microbot.droagility;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class DroAgilityOverlay extends OverlayPanel
{
	final DroAgilityPlugin plugin;
	final DroAgilityConfig config;

	@Inject
	DroAgilityOverlay(DroAgilityPlugin plugin, DroAgilityConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.getDroAgilityScript().isShuttingDown() || !plugin.getDroAgilityScript().isRunning())
		{
			return null;
		}

		try
		{
			panelComponent.setPreferredSize(new Dimension(200, 300));
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("DroAgility V" + DroAgilityPlugin.version)
				.color(Color.GREEN)
				.build());

			panelComponent.getChildren().add(LineComponent.builder().build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Agility Exp")
				.right(Integer.toString(Microbot.getClient().getSkillExperience(Skill.AGILITY)))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Current Obstacle")
				.right(Integer.toString(plugin.getDroAgilityScript().getCurrentObstacleIndex()))
				.build());

		}
		catch (Exception ex)
		{
			Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
		}
		return super.render(graphics);
	}
}

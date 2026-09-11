package net.runelite.client.plugins.microbot.drolibrary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("drolibrary")
@ConfigInformation(
        "Kourend Library Script.<br/>" +
                "<b>NOTES:</b><br/>" +
                "- Start script INSIDE the Kourend Library.<br/><br/>" +
                "- The bot does not have a resume searching function, so it will reset if you stop and start the script.<br/><br/>" +
                "- It will automatically consume staminas if you bring them in your inventory.<br/>"
)
public interface DroLibraryConfig extends Config
{
    enum RewardType
    {
        MAGIC,
        RUNECRAFTING
    }

    @ConfigItem(
            keyName = "rewardType",
            name = "Book Reward",
            description = "Choose whether to spend Arcane Knowledge books on Magic or Runecrafting",
            position = 1
    )
    default RewardType rewardType()
    {
        return RewardType.RUNECRAFTING;
    }
}
package net.runelite.client.plugins.microbot.drolibrary;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("drolibrary")
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
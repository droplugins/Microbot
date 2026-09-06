package net.runelite.client.plugins.microbot.divinepotion;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("divinepotion")
public interface DivinePotionConfig extends Config {
    @ConfigItem(
            keyName = "sipMelee",
            name = "Sip Melee Potions",
            description = "Sips Divine Super Combat or Divine Attack/Strength potions when boosted stats expire",
            position = 1
    )
    default boolean sipMelee() { return false; }

    @ConfigItem(
            keyName = "sipRanged",
            name = "Sip Ranged Potions",
            description = "Sips Divine Ranging potions when boost expires",
            position = 2
    )
    default boolean sipRanged() { return false; }

    @ConfigItem(
            keyName = "sipMagic",
            name = "Sip Magic Potions",
            description = "Sips Divine Magic potions when boost expires",
            position = 3
    )
    default boolean sipMagic() { return false; }
}
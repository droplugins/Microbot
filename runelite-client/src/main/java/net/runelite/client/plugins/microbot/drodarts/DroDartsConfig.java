package net.runelite.client.plugins.microbot.drodarts;

import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("drodarts")
public interface DroDartsConfig extends Config
{
    enum DartTip
    {
        IRON(820, 807),
        STEEL(821, 808),
        MITHRIL(822, 809),
        ADAMANT(823, 810),
        RUNE(824, 811),
        DRAGON(11232, 11230);

        @Getter
        private final int itemId;

        @Getter
        private final int dartId;

        DartTip(int itemId, int dartId)
        {
            this.itemId = itemId;
            this.dartId = dartId;
        }
    }

    @ConfigItem(
            keyName = "dartType",
            name = "Dart Type",
            description = "Select the dart tip to fletch",
            position = 1
    )
    default DartTip dartType()
    {
        return DartTip.MITHRIL;
    }

    @ConfigItem(
            keyName = "minDelay",
            name = "Min Delay (ms)",
            description = "Minimum delay between action pairs; 0 means no added delay.",
            position = 2
    )
    default int minDelay()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "maxDelay",
            name = "Max Delay (ms)",
            description = "Maximum delay between action pairs; actual timing depends on the client.",
            position = 3
    )
    default int maxDelay()
    {
        return 0;
    }
}
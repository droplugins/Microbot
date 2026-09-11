package net.runelite.client.plugins.microbot.droagility.enums;

public enum DroAgilityEquipmentOption
{
	NONE("None"),
	GRACEFUL("Graceful"),
	GRACEFUL_WITH_AGILITY_CAPE("Graceful + agility cape");

	private final String displayName;

	DroAgilityEquipmentOption(String displayName)
	{
		this.displayName = displayName;
	}

	public boolean isNone()
	{
		return this == NONE;
	}

	public boolean useAgilityCape()
	{
		return this == GRACEFUL_WITH_AGILITY_CAPE;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}

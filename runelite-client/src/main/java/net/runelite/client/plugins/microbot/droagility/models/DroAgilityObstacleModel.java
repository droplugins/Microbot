package net.runelite.client.plugins.microbot.droagility.models;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.misc.Operation;

public class DroAgilityObstacleModel
{
	@Getter
	int objectID;

	@Getter
	int requiredX = -1;
	@Getter
	int requiredY = -1;
	@Getter
	Operation operationX = Operation.GREATER;
	@Getter
	Operation operationY = Operation.GREATER;

	public DroAgilityObstacleModel(int objectID)
	{
		this.objectID = objectID;
	}

	public DroAgilityObstacleModel(int objectID, int x, int y, Operation operationX, Operation operationY)
	{
		this.objectID = objectID;
		this.requiredX = x;
		this.requiredY = y;
		this.operationX = operationX;
		this.operationY = operationY;
	}
}

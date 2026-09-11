package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroCanafisCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3507, 3489, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_START_TREE),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_JUMP),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_JUMP_2, 3498, -1, Operation.GREATER_EQUAL, Operation.GREATER),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_JUMP_5),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_JUMP_3),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_POLEVAULT),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_JUMP_4),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_CANIFIS_LEAPDOWN)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 40;
	}
}

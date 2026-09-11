package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroRellekkaCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2625, 3677, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_1, -1, 3672, Operation.GREATER, Operation.GREATER),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_1),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_2),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_GAP_3, -1, 3653, Operation.GREATER, Operation.LESS_EQUAL),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_3),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_RELLEKKA_DROPOFF)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 80;
	}
}

package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroArdougneCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2673, 3298, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_PLANK),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_2, -1, 3318, Operation.GREATER, Operation.GREATER_EQUAL),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_3, -1, 3310, Operation.GREATER, Operation.GREATER_EQUAL),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_WALLCROSSING),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_ARDY_JUMP_4)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 90;
	}

	@Override
	public int getLootDistance() {
		return 2;
	}
}

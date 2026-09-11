package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroDraynorCourse implements DroAgilityCourseHandler
{

	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3103, 3279, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_1),// 3102,3279
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_2),// 3090,3276
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLCROSSING,-1,3266,Operation.GREATER,Operation.GREATER_EQUAL), // 3092,3266
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_WALLSCRAMBLE, -1, 3261, Operation.GREATER, Operation.GREATER_EQUAL),// 3088,3261
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_LEAPDOWN, -1, 3255, Operation.GREATER, Operation.LESS_EQUAL),// 3088 3255
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_DRAYNOR_CRATE) // 3096,3256
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 1;
	}
}

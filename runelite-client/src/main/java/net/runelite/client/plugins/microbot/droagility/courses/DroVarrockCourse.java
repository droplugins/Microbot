package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroVarrockCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3221, 3414, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_CLOTHESLINE),//3219,3414
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPTORUINS),//3208,3414
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLSWING),//3197,3416
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_WALLSCRAMBLE, -1, 3402, Operation.GREATER, Operation.GREATER_EQUAL), // 3192, 3406
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPTOBALCONY), // 3193,3398
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_LEAPDOWN, -1, 3402, Operation.GREATER, Operation.LESS_EQUAL), // 3218,3399
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_STEPUPROOF, -1, 3408, Operation.GREATER, Operation.LESS_EQUAL), //3236,3403
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_VARROCK_FINISH)//3236,3410
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 30;
	}
}

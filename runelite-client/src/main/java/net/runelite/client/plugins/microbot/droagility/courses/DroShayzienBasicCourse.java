package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroShayzienBasicCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(1551, 3632, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_START_LADDER),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_ROPE_CLIMB),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_BOTH_ROPE_WALK, -1, 3635, Operation.GREATER, Operation.LESS_EQUAL),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_BAR_CLIMB),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_ROPE_WALK_1),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_ROPE_WALK_2, -1, 3643, Operation.GREATER, Operation.GREATER_EQUAL),
			new DroAgilityObstacleModel(ObjectID.SHAYZIEN_AGILITY_LOW_END_JUMP)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 1;
	}
}

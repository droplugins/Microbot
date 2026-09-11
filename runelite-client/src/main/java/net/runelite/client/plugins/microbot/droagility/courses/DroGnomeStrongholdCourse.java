package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.misc.Operation;

import java.util.List;

public class DroGnomeStrongholdCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2474, 3436, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.GNOME_LOG_BALANCE1, -1, 3436, Operation.GREATER, Operation.GREATER_EQUAL),
			new DroAgilityObstacleModel(ObjectID.OBSTICAL_NET2, 2476, 3426, Operation.LESS_EQUAL, Operation.GREATER_EQUAL),
			new DroAgilityObstacleModel(ObjectID.CLIMBING_BRANCH),
			new DroAgilityObstacleModel(ObjectID.BALANCING_ROPE, 2477, -1, Operation.LESS_EQUAL, Operation.GREATER),
			new DroAgilityObstacleModel(ObjectID.CLIMBING_TREE),
			new DroAgilityObstacleModel(ObjectID.OBSTICAL_NET3, 2483, 3425, Operation.GREATER_EQUAL, Operation.LESS_EQUAL),
			new DroAgilityObstacleModel(ObjectID.OBSTICAL_PIPE3_1, -1, 3430, Operation.GREATER, Operation.LESS_EQUAL)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 1;
	}

	@Override
	public boolean handleCourseActions(WorldPoint playerWorldLocation)
	{
		return false;
	}
}

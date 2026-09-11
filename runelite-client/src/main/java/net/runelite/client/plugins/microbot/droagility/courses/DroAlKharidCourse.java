package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;

import java.util.List;

public class DroAlKharidCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(3273, 3195, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_TIGHTROPE_1),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_ROPE_SWING),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_SLIDE_SIDE),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_BAMBOO_TREE_TOP),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_WALLCLIMB_2),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_TIGHTROPE_4),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_KHARID_LEAPDOWN)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 20;
	}
}

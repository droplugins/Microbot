package net.runelite.client.plugins.microbot.droagility.courses;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.droagility.models.DroAgilityObstacleModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;

public class DroSeersCourse implements DroAgilityCourseHandler
{
	@Override
	public WorldPoint getStartPoint()
	{
		return new WorldPoint(2729, 3486, 0);
	}

	@Override
	public List<DroAgilityObstacleModel> getObstacles()
	{
		return List.of(
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_WALLCLIMB),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_TIGHTROPE),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_1),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_JUMP_2),
			new DroAgilityObstacleModel(ObjectID.ROOFTOPS_SEERS_LEAPDOWN)
		);
	}

	@Override
	public Integer getRequiredLevel()
	{
		return 60;
	}

	@Override
	public boolean handleWalkToStart(WorldPoint playerWorldLocation)
	{
		if (getClientPlane() != 0)
		{
			return false;
		}
		if (getCurrentObstacleIndex() > 0)
		{
			return false;
		}

		if (getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1
			&& Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT)
			&& playerWorldLocation.distanceTo(getStartPoint()) > 12)

		{
			Rs2Magic.cast(Rs2Spells.CAMELOT_TELEPORT, "Seers'", 2);
			return Global.sleepUntil(() -> {
				WorldPoint currentLocation = getPlayerWorldLocation();
				return currentLocation.distanceTo(getStartPoint()) <= 12;
			}, 5000);
		}

		if (playerWorldLocation.distanceTo(getStartPoint()) > 12)
		{
			Microbot.log("Going back to course's starting point");
			Rs2Walker.walkTo(getStartPoint(), 2);
			return true;
		}
		return false;
	}
}

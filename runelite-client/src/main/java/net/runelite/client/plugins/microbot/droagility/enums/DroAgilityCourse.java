package net.runelite.client.plugins.microbot.droagility.enums;

import lombok.Getter;
import net.runelite.client.plugins.microbot.droagility.courses.*;

@Getter
public enum DroAgilityCourse
{
	AGILITY_PYRAMID("Agility Pyramid", new DroPyramidCourse()),
	AL_KHARID_ROOFTOP_COURSE("Al Kharid Rooftop Course", new DroAlKharidCourse()),
	APE_ATOLL_AGILITY_COURSE("Ape Atoll Agility Course", new DroApeAtollCourse()),
	ARDOUGNE_ROOFTOP_COURSE("Ardougne Rooftop Course", new DroArdougneCourse()),
	BRIMHAVEN_SPIKE_COURSE("Brimhaven Spike Course", new DroBrimhavenSpikeCourse()),
	CANIFIS_ROOFTOP_COURSE("Canifis Rooftop Course", new DroCanafisCourse()),
	COLOSSAL_WYRM_ADVANCED_COURSE("Colossal Wyrm Advanced Course", new DroColossalWyrmAdvancedCourse()),
	COLOSSAL_WYRM_BASIC_COURSE("Colossal Wyrm Basic Course", new DroColossalWyrmBasicCourse()),
	DRAYNOR_VILLAGE_ROOFTOP_COURSE("Draynor Village Rooftop Course", new DroDraynorCourse()),
	FALADOR_ROOFTOP_COURSE("Falador Rooftop Course", new DroFaladorCourse()),
	GNOME_STRONGHOLD_AGILITY_COURSE("Gnome Stronghold Agility Course", new DroGnomeStrongholdCourse()),
	POLLNIVNEACH_ROOFTOP_COURSE("Pollnivneach Rooftop Course", new DroPollnivneachCourse()),
	PRIFDDINAS_AGILITY_COURSE("Prifddinas Agility Course", new DroPrifddinasCourse()),
	RELLEKKA_ROOFTOP_COURSE("Rellekka Rooftop Course", new DroRellekkaCourse()),
	SEERS_VILLAGE_ROOFTOP_COURSE("Seers' Village Rooftop Course", new DroSeersCourse()),
	SHAYZIEN_ADVANCED_COURSE("Shayzien Advanced Agility Course", new DroShayzienAdvancedCourse()),
	SHAYZIEN_BASIC_COURSE("Shayzien Basic Agility Course", new DroShayzienBasicCourse()),
	VARROCK_ROOFTOP_COURSE("Varrock Rooftop Course", new DroVarrockCourse()),
	WEREWOLF_COURSE("Werewolf Agility Course", new DroWerewolfCourse()),
	;


	private final String tooltip;
	private final boolean rooftopCourse;
	private final DroAgilityCourseHandler handler;

	DroAgilityCourse(String tooltip, DroAgilityCourseHandler handler)
	{
		this.tooltip = tooltip;
		this.handler = handler;
		this.rooftopCourse = this.name().contains("ROOFTOP_COURSE");
	}
}

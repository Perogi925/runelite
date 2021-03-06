/*
 * Copyright (c) 2017, Cameron <moberg@tuta.io>
 * Copyright (c) 2018, Levi <me@levischuck.com>
 * Copyright (c) 2020, Anthony <https://github.com/while-loop>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.xptracker;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

@Slf4j
class XpStateSingle
{
	private final Skill skill;
	private final Map<XpActionType, XpAction> actions = new HashMap<>();

	@Getter
	@Setter
	private long startXp;

	@Getter
	private int xpGained = 0;

	@Setter
	private XpActionType actionType = XpActionType.EXPERIENCE;

	private long skillTime = 0;
	private int startLevelExp = 0;
	private int endLevelExp = 0;

	XpStateSingle(Skill skill, long startXp)
	{
		this.skill = skill;
		this.startXp = startXp;
	}

	XpAction getXpAction(final XpActionType type)
	{
		actions.putIfAbsent(type, new XpAction());
		return actions.get(type);
	}

	long getCurrentXp()
	{
		return startXp + xpGained;
	}

	private int getActionsMin() {return toMinute(getXpAction(actionType).getActions());}

	private int toMinute(int value) {return (int) ((1.0 / (getTimeElapsedInSeconds() / 60)) * value); }

	private int getActionsHr()
	{
		return toHourly(getXpAction(actionType).getActions());
	}

	private int toHourly(int value)
	{
		return (int) ((1.0 / (getTimeElapsedInSeconds() / 3600.0)) * value);
	}

	private long getTimeElapsedInSeconds()
	{
		// If the skill started just now, we can divide by near zero, this results in odd behavior.
		// To prevent that, pretend the skill has been active for a minute (60 seconds)
		// This will create a lower estimate for the first minute,
		// but it isn't ridiculous like saying 2 billion XP per hour.
		return Math.max(60, skillTime / 1000);
	}

	private int getXpRemaining()
	{
		return endLevelExp - (int) getCurrentXp();
	}

	private int getActionsRemaining()
	{
		final XpAction action = getXpAction(actionType);

		if (action.isActionsHistoryInitialized())
		{
			long xpRemaining = getXpRemaining() * action.getActionExps().length;
			long totalActionXp = 0;

			for (int actionXp : action.getActionExps())
			{
				totalActionXp += actionXp;
			}

			// Let's not divide by zero (or negative)
			if (totalActionXp > 0)
			{
				// Make sure to account for the very last action at the end
				long remainder = xpRemaining % totalActionXp;
				long quotient = xpRemaining / totalActionXp;
				return Math.toIntExact(quotient + (remainder > 0 ? 1 : 0));
			}
		}

		return Integer.MAX_VALUE;
	}

	private double getSkillProgress()
	{
		double xpGained = getCurrentXp() - startLevelExp;
		double xpGoal = endLevelExp - startLevelExp;
		return (xpGained / xpGoal) * 100;
	}

	private long getSecondsTillLevel()
	{
		// Java 8 doesn't have good duration / period objects to represent spans of time that can be formatted
		// Rather than importing another dependency like joda time (which is practically built into java 10)
		// below will be a custom formatter that handles spans larger than 1 day
		long seconds = getTimeElapsedInSeconds();

		if (seconds <= 0 || xpGained <= 0)
		{
			return -1;
		}

		// formula is xpRemaining / xpPerSecond
		// xpPerSecond being xpGained / seconds
		// This can be simplified so division is only done once and we can work in whole numbers!
		return (getXpRemaining() * seconds) / xpGained;
	}

	private String getTimeTillLevel()
	{
		long remainingSeconds = getSecondsTillLevel();
		if (remainingSeconds < 0)
		{
			return "\u221e";
		}

		long durationDays = remainingSeconds / (24 * 60 * 60);
		long durationHours = (remainingSeconds % (24 * 60 * 60)) / (60 * 60);
		long durationMinutes = (remainingSeconds % (60 * 60)) / 60;
		long durationSeconds = remainingSeconds % 60;

		if (durationDays > 1)
		{
			return String.format("%d days %02d:%02d:%02d", durationDays, durationHours, durationMinutes, durationSeconds);
		}
		else if (durationDays == 1)
		{
			return String.format("1 day %02d:%02d:%02d", durationHours, durationMinutes, durationSeconds);
		}

		// durationDays = 0 if we got here.
		// return time remaining in hh:mm:ss or mm:ss format
		return getTimeTillLevelShort();
	}

	/**
	 * Get time to level in `hh:mm:ss` or `mm:ss` format,
	 * where `hh` can be > 24.
	 * @return
	 */
	private String getTimeTillLevelShort()
	{
		long remainingSeconds = getSecondsTillLevel();
		if (remainingSeconds < 0)
		{
			return "\u221e";
		}

		long durationHours = remainingSeconds / (60 * 60);
		long durationMinutes = (remainingSeconds % (60 * 60)) / 60;
		long durationSeconds = remainingSeconds % 60;
		if (durationHours > 0)
		{
			return String.format("%02d:%02d:%02d", durationHours, durationMinutes, durationSeconds);
		}

		// Minutes and seconds will always be present
		return String.format("%02d:%02d", durationMinutes, durationSeconds);
	}

	int getXpMin() { return toMinute(xpGained); }

	int getXpHr()
	{
		return toHourly(xpGained);
	}

	boolean update(long currentXp, int goalStartXp, int goalEndXp)
	{
		if (startXp == -1)
		{
			log.warn("Attempted to update skill state " + skill + " but was not initialized with current xp");
			return false;
		}

		long originalXp = xpGained + startXp;
		int actionExp = (int) (currentXp - originalXp);

		// No experience gained
		if (actionExp == 0)
		{
			return false;
		}

		// Update EXPERIENCE action
		final XpAction action = getXpAction(XpActionType.EXPERIENCE);

		if (action.isActionsHistoryInitialized())
		{
			action.getActionExps()[action.getActionExpIndex()] = actionExp;
		}
		else
		{
			// populate all values in our action history array with this first value that we see
			// so the average value of our action history starts out as this first value we see
			for (int i = 0; i < action.getActionExps().length; i++)
			{
				action.getActionExps()[i] = actionExp;
			}

			action.setActionsHistoryInitialized(true);
		}

		action.setActionExpIndex((action.getActionExpIndex() + 1) % action.getActionExps().length);
		action.setActions(action.getActions() + 1);

		// Calculate experience gained
		xpGained = (int) (currentXp - startXp);

		// Determine XP goals, overall has no goals
		if (skill != Skill.OVERALL)
		{
			if (goalStartXp < 0 || currentXp > goalEndXp)
			{
				startLevelExp = Experience.getXpForLevel(Experience.getLevelForXp((int) currentXp));
			}
			else
			{
				startLevelExp = goalStartXp;
			}

			if (goalEndXp <= 0 || currentXp > goalEndXp)
			{
				int currentLevel = Experience.getLevelForXp((int) currentXp);
				endLevelExp = currentLevel + 1 <= Experience.MAX_VIRT_LEVEL
					? Experience.getXpForLevel(currentLevel + 1)
					: Experience.MAX_SKILL_XP;
			}
			else
			{
				endLevelExp = goalEndXp;
			}
		}

		return true;
	}

	public void tick(long delta)
	{
		// Don't tick skills that have not gained XP or have been reset.
		if (xpGained <= 0)
		{
			return;
		}
		skillTime += delta;
	}

	XpSnapshotSingle snapshot()
	{
		return XpSnapshotSingle.builder()
			.startLevel(Experience.getLevelForXp(startLevelExp))
			.endLevel(Experience.getLevelForXp(endLevelExp))
			.xpGainedInSession(xpGained)
			.xpRemainingToGoal(getXpRemaining())
			.xpPerHour(getXpHr())
			.xpPerMin(getXpMin())
			.skillProgressToGoal(getSkillProgress())
			.actionType(actionType)
			.actionsInSession(getXpAction(actionType).getActions())
			.actionsRemainingToGoal(getActionsRemaining())
			.actionsPerHour(getActionsHr())
			.timeTillGoal(getTimeTillLevel())
			.timeTillGoalShort(getTimeTillLevelShort())
			.startGoalXp(startLevelExp)
			.endGoalXp(endLevelExp)
			.build();
	}
}

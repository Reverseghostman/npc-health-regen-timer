package com.npchealthregen;

/**
 * How likely a landed hit is to envenom an NPC, from the worn weapon and serpentine helm.
 *
 * The figures are the OSRS Wiki's (Venom, Serpentine helm and each weapon's own page). Against
 * an NPC a charged serpentine helm makes the toxic blowpipe, trident of the swamp and toxic
 * staff of the dead envenom every landed hit. Without the helm they envenom 25% of landed hits,
 * and the noxious halberd envenoms 33% (50% with the helm).
 *
 * A hit by a player that passes its accuracy roll always deals at least 1 damage, while a failed
 * roll shows 0, so a hitsplat above 0 is a landed hit and the percentage here is the chance that
 * this hit envenoms. The wiki also gives the helm alone 1/6 with a non-poisoned melee weapon and
 * 1/2 with poisoned weapons; those are not modelled, as they depend on the attack style.
 */
final class VenomChance
{
	static final int GUARANTEED_PERCENT = 100;

	enum Weapon
	{
		TOXIC_BLOWPIPE("Toxic blowpipe", 25, 100),
		TRIDENT_OF_THE_SWAMP("Trident of the swamp", 25, 100),
		TOXIC_STAFF_OF_THE_DEAD("Toxic staff of the dead", 25, 100),
		NOXIOUS_HALBERD("Noxious halberd", 33, 50);

		private final String displayName;
		private final int percentWithoutHelm;
		private final int percentWithHelm;

		Weapon(String displayName, int percentWithoutHelm, int percentWithHelm)
		{
			this.displayName = displayName;
			this.percentWithoutHelm = percentWithoutHelm;
			this.percentWithHelm = percentWithHelm;
		}

		String getDisplayName()
		{
			return displayName;
		}
	}

	/** A worn venom weapon, and whether a charged serpentine helm is worn with it. */
	static final class Setup
	{
		private final Weapon weapon;
		private final boolean serpentineHelm;

		Setup(Weapon weapon, boolean serpentineHelm)
		{
			this.weapon = weapon;
			this.serpentineHelm = serpentineHelm;
		}

		Weapon getWeapon()
		{
			return weapon;
		}

		boolean hasSerpentineHelm()
		{
			return serpentineHelm;
		}

		/** @return the chance, in percent, that a landed hit envenoms an NPC */
		int getPercent()
		{
			return serpentineHelm ? weapon.percentWithHelm : weapon.percentWithoutHelm;
		}

		/** @return whether every landed hit envenoms, which is what the venom ring relies on */
		boolean isGuaranteed()
		{
			return getPercent() >= GUARANTEED_PERCENT;
		}

		/** @return the chance as shown to the player, noting a missing helm */
		String describe()
		{
			String percent = getPercent() + "%";
			return serpentineHelm || isGuaranteed() ? percent : percent + " (no helm)";
		}
	}

	private VenomChance()
	{
	}
}

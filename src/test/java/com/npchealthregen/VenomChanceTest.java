package com.npchealthregen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VenomChanceTest
{
	private static final VenomChance.Weapon[] TOXIC_WEAPONS = {
		VenomChance.Weapon.TOXIC_BLOWPIPE,
		VenomChance.Weapon.TRIDENT_OF_THE_SWAMP,
		VenomChance.Weapon.TOXIC_STAFF_OF_THE_DEAD};

	@Test
	public void serpentineHelmMakesTheToxicWeaponsAlwaysEnvenomAnNpc()
	{
		for (VenomChance.Weapon weapon : TOXIC_WEAPONS)
		{
			VenomChance.Setup setup = new VenomChance.Setup(weapon, true);

			assertEquals(weapon.getDisplayName(), 100, setup.getPercent());
			assertTrue(weapon.getDisplayName(), setup.isGuaranteed());
			assertEquals("100%", setup.describe());
		}
	}

	@Test
	public void toxicWeaponsWithoutTheHelmEnvenomOneHitInFour()
	{
		for (VenomChance.Weapon weapon : TOXIC_WEAPONS)
		{
			VenomChance.Setup setup = new VenomChance.Setup(weapon, false);

			assertEquals(weapon.getDisplayName(), 25, setup.getPercent());
			assertFalse(weapon.getDisplayName(), setup.isGuaranteed());
			assertEquals("25% (no helm)", setup.describe());
		}
	}

	@Test
	public void noxiousHalberdIsNeverGuaranteedEvenWithTheHelm()
	{
		VenomChance.Setup bare = new VenomChance.Setup(VenomChance.Weapon.NOXIOUS_HALBERD, false);
		VenomChance.Setup helmed = new VenomChance.Setup(VenomChance.Weapon.NOXIOUS_HALBERD, true);

		assertEquals(33, bare.getPercent());
		assertEquals("33% (no helm)", bare.describe());
		assertEquals(50, helmed.getPercent());
		assertEquals("50%", helmed.describe());
		assertFalse(bare.isGuaranteed());
		assertFalse(helmed.isGuaranteed());
	}

	@Test
	public void setupRemembersItsParts()
	{
		VenomChance.Setup setup = new VenomChance.Setup(VenomChance.Weapon.TOXIC_BLOWPIPE, true);

		assertEquals(VenomChance.Weapon.TOXIC_BLOWPIPE, setup.getWeapon());
		assertTrue(setup.hasSerpentineHelm());
	}
}

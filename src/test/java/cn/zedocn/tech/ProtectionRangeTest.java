package cn.zedocn.tech;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionRangeTest {
    @Test void retainsExistingRadiiAndAllowsTheLargerRange() {
        for (int radius : new int[]{8, 16, 24, 32, 64, 128, 192, 256}) {
            assertEquals(radius, Balance.radius(radius));
            assertEquals(2 * radius * radius, Balance.protectionCost(radius));
        }
    }

    @Test void clampsSavedOrInvalidRadiiWithoutOverflowingTheEnergyBudget() {
        assertEquals(8, Balance.radius(Integer.MIN_VALUE));
        assertEquals(256, Balance.radius(Integer.MAX_VALUE));
        assertEquals(131072, Balance.protectionCost(Integer.MAX_VALUE));
        assertTrue(Balance.protectionCost(256) < Balance.ENERGY_CAPACITY);
    }
}

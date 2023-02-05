package bobby;

import battlecode.common.GameConstants;

public class OptimalResource {

    public static void main(String[] args) {
        for (int d = 1; d < 100; d++) {
            System.out.println("Optimal amount at dist " + d + ": " + optimalAmountAt(d));
        }
    }

    public static int optimalAmountAt(int distance) {
        double bestRate = 0;
        int bestAmount = 0;
        for (int amount = 1; amount <= GameConstants.CARRIER_CAPACITY; amount++) {
            // simulate a roundtrip
            int turns = numTurns(distance, carrierCooldown(0)); // HQ -> well
            turns += numTurns(amount, 10); // collecting. rate is 1, so no need to parametrize further.
            turns += numTurns(distance, carrierCooldown(amount)); // well -> amount

            double rate = 1.0 * amount / turns;
            System.out.println("  amount=" + amount + "; turns=" + turns + "; rate=" + rate);
            if (rate > bestRate) {
                bestRate = rate;
                bestAmount = amount;
            }
        }
        return bestAmount;
    }

    private static int carrierCooldown(int resources) {
        return (int) (GameConstants.CARRIER_MOVEMENT_INTERCEPT + GameConstants.CARRIER_MOVEMENT_SLOPE * resources);
    }

    private static int numTurns(int numActions, int actionCooldown) {
        int turns = 1;
        int currentActions = 0;
        int currentCooldown = 0;
        while (currentActions < numActions) {
            if (currentCooldown < 10) {
                // do an action
                currentActions++;
                currentCooldown += actionCooldown;
            } else {
                turns++;
                currentCooldown -= 10;
            }
        }
        return turns;
    }
}

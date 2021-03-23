package brs.at;

import brs.Burst;
import brs.Constants;
import brs.fluxcapacitor.FluxValues;

import java.util.HashMap;


public class AtConstants {
    //platform based
    public static final int AT_ID_SIZE = 8;
    private static final HashMap<Short, Long> MIN_FEE = new HashMap<>();
    private static final HashMap<Short, Long> STEP_FEE = new HashMap<>();
    private static final HashMap<Short, Long> MAX_STEPS = new HashMap<>();
    private static final HashMap<Short, Long> API_STEP_MULTIPLIER = new HashMap<>();
    private static final HashMap<Short, Long> COST_PER_PAGE = new HashMap<>();
    private static final HashMap<Short, Long> MAX_WAIT_FOR_NUM_OF_BLOCKS = new HashMap<>();
    private static final HashMap<Short, Long> MAX_SLEEP_BETWEEN_BLOCKS = new HashMap<>();
    private static final HashMap<Short, Long> PAGE_SIZE = new HashMap<>();
    private static final HashMap<Short, Long> MAX_MACHINE_CODE_PAGES = new HashMap<>();
    private static final HashMap<Short, Long> MAX_MACHINE_DATA_PAGES = new HashMap<>();
    private static final HashMap<Short, Long> MAX_MACHINE_USER_STACK_PAGES = new HashMap<>();
    private static final HashMap<Short, Long> MAX_MACHINE_CALL_STACK_PAGES = new HashMap<>();
    private static final HashMap<Short, Long> BLOCKS_FOR_RANDOM = new HashMap<>();
    private static final HashMap<Short, Long> MAX_PAYLOAD_FOR_BLOCK = new HashMap<>();
    private static final HashMap<Short, Long> AVERAGE_BLOCK_MINUTES = new HashMap<>();
    private static final AtConstants instance = new AtConstants();

    private AtConstants() {
        // constants for AT version 1
        MIN_FEE.put((short) 1, 1000L);
        STEP_FEE.put((short) 1, Constants.ONE_BURST / 10L);
        MAX_STEPS.put((short) 1, 2000L);
        API_STEP_MULTIPLIER.put((short) 1, 10L);

        COST_PER_PAGE.put((short) 1, Constants.ONE_BURST);

        MAX_WAIT_FOR_NUM_OF_BLOCKS.put((short) 1, 31536000L);
        MAX_SLEEP_BETWEEN_BLOCKS.put((short) 1, 31536000L);

        PAGE_SIZE.put((short) 1, 256L);

        MAX_MACHINE_CODE_PAGES.put((short) 1, 10L);
        MAX_MACHINE_DATA_PAGES.put((short) 1, 10L);
        MAX_MACHINE_USER_STACK_PAGES.put((short) 1, 10L);
        MAX_MACHINE_CALL_STACK_PAGES.put((short) 1, 10L);

        BLOCKS_FOR_RANDOM.put((short) 1, 15L); //for testing 2 -> normally 1440
        MAX_PAYLOAD_FOR_BLOCK.put((short) 1, (Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH)) / 2L); //use at max half size of the block.
        AVERAGE_BLOCK_MINUTES.put((short) 1, 4L);
        // end of AT version 1

        // constants for AT version 2
        MIN_FEE.put((short) 2, 1000L);
        STEP_FEE.put((short) 2, Constants.FEE_QUANT / 10L);
        MAX_STEPS.put((short) 2, 100_000L);
        API_STEP_MULTIPLIER.put((short) 2, 10L);

        COST_PER_PAGE.put((short) 2, Constants.FEE_QUANT * 10);

        MAX_WAIT_FOR_NUM_OF_BLOCKS.put((short) 2, 31536000L);
        MAX_SLEEP_BETWEEN_BLOCKS.put((short) 2, 31536000L);

        PAGE_SIZE.put((short) 2, 256L);

        MAX_MACHINE_CODE_PAGES.put((short) 2, 20L);
        MAX_MACHINE_DATA_PAGES.put((short) 2, 10L);
        MAX_MACHINE_USER_STACK_PAGES.put((short) 2, 10L);
        MAX_MACHINE_CALL_STACK_PAGES.put((short) 2, 10L);

        BLOCKS_FOR_RANDOM.put((short) 2, 15L); //for testing 2 -> normally 1440
        MAX_PAYLOAD_FOR_BLOCK.put((short) 2, (Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH)) / 2L); //use at max half size of the block.
        AVERAGE_BLOCK_MINUTES.put((short) 2, 4L);
        // end of AT version 2
    }

    public static AtConstants getInstance() {
        return instance;
    }

    public short atVersion(int blockHeight) {
        return Burst.getFluxCapacitor().getValue(FluxValues.AT_VERSION, blockHeight);
    }

    //version 3 is the first AT version to support assets 
    public boolean supportAssetsEnabled(int blockHeight) {
        return Burst.getFluxCapacitor().getValue(FluxValues.AT_VERSION, blockHeight) > 2;
    }

    public long stepFee(int height) {
        return STEP_FEE.get(atVersion(height));
    }

    public long maxSteps(int height) {
        if(Burst.getFluxCapacitor().getValue(FluxValues.PRE_POC_PLUS, height)) {
            return 1_000_000L;
        }
        return MAX_STEPS.get(atVersion(height));
    }

    public long apiStepMultiplier(int height) {
        return API_STEP_MULTIPLIER.get(atVersion(height));
    }

    public long costPerPage(int height) {
        return COST_PER_PAGE.get(atVersion(height));
    }

    public long getMaxWaitForNumOfBlocks(int height) {
        return MAX_WAIT_FOR_NUM_OF_BLOCKS.get(atVersion(height));
    }

    public long maxSleepBetweenBlocks(int height) {
        return MAX_SLEEP_BETWEEN_BLOCKS.get(atVersion(height));
    }

    public long pageSize(int height) {
        return PAGE_SIZE.get(atVersion(height));
    }

    public long maxMachineCodePages(int height) {
        return MAX_MACHINE_CODE_PAGES.get(atVersion(height));
    }

    public long maxMachineDataPages(int height) {
        return MAX_MACHINE_DATA_PAGES.get(atVersion(height));
    }

    public long maxMachineUserStackPages(int height) {
        return MAX_MACHINE_USER_STACK_PAGES.get(atVersion(height));
    }

    public long maxMachineCallStackPages(int height) {
        return MAX_MACHINE_CALL_STACK_PAGES.get(atVersion(height));
    }

    public long blocksForRandom(int height) {
        return BLOCKS_FOR_RANDOM.get(atVersion(height));
    }

    public long maxPayloadForBlock(int height) {
        return MAX_PAYLOAD_FOR_BLOCK.get(atVersion(height));
    }

    public long averageBlockMinutes(int height) {
        return AVERAGE_BLOCK_MINUTES.get(atVersion(height));
    }
}

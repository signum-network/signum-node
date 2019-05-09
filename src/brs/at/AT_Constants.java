package brs.at;

import brs.Burst;
import brs.Constants;
import brs.fluxcapacitor.FluxValues;

import java.util.HashMap;
import java.util.NavigableMap;
import java.util.TreeMap;


public class AT_Constants {
  private static final NavigableMap< Integer, Short >  AT_VERSION = new TreeMap<>();
	
  private static final HashMap< Short, Long > MIN_FEE = new HashMap<>();
  private static final HashMap< Short, Long > STEP_FEE  = new HashMap<>();
  private static final HashMap< Short, Long > MAX_STEPS  = new HashMap<>();
  private static final HashMap< Short, Long > API_STEP_MULTIPLIER = new HashMap<>();
	
  private static final HashMap< Short, Long >  COST_PER_PAGE = new HashMap<>();
	
  private static final HashMap< Short, Long >  MAX_WAIT_FOR_NUM_OF_BLOCKS = new HashMap<>();
  private static final HashMap< Short, Long >  MAX_SLEEP_BETWEEN_BLOCKS = new HashMap<>();
	
  private static final HashMap< Short, Long >  PAGE_SIZE = new HashMap<>();
	
  private static final HashMap< Short, Long >  MAX_MACHINE_CODE_PAGES = new HashMap<>();
  private static final HashMap< Short, Long >  MAX_MACHINE_DATA_PAGES = new HashMap<>();
  private static final HashMap< Short, Long >  MAX_MACHINE_USER_STACK_PAGES = new HashMap<>();
  private static final HashMap< Short, Long >  MAX_MACHINE_CALL_STACK_PAGES = new HashMap<>();

  private static final HashMap< Short, Long >  BLOCKS_FOR_RANDOM = new HashMap<>();
	
  private static final HashMap< Short, Long >  MAX_PAYLOAD_FOR_BLOCK = new HashMap<>();
	
  private static final HashMap< Short, Long >  AVERAGE_BLOCK_MINUTES = new HashMap<>();
	
  //platform based
  public static final int AT_ID_SIZE = 8;
	
  private static final AT_Constants instance = new AT_Constants();
	
  private AT_Constants() {
    //version 1
    AT_VERSION.put( 0, (short)1 ); //block 0 version 1
		
    //constants for AT version 1
    MIN_FEE.put( (short) 1, 1000L );
    STEP_FEE.put( (short) 1, Constants.ONE_BURST / 10L );
    MAX_STEPS.put( (short) 1, 2000L );
    API_STEP_MULTIPLIER.put( (short) 1, 10L);
		
    COST_PER_PAGE.put( (short) 1, Constants.ONE_BURST );
		
    MAX_WAIT_FOR_NUM_OF_BLOCKS.put( (short) 1, 31536000L );
    MAX_SLEEP_BETWEEN_BLOCKS.put( (short) 1, 31536000L );
		
    PAGE_SIZE.put( (short) 1, 256L );
		
    MAX_MACHINE_CODE_PAGES.put( (short) 1, 10L );
    MAX_MACHINE_DATA_PAGES.put( (short) 1, 10L );
    MAX_MACHINE_USER_STACK_PAGES.put( (short) 1, 10L );
    MAX_MACHINE_CALL_STACK_PAGES.put( (short) 1, 10L );
		
    BLOCKS_FOR_RANDOM.put( (short) 1, 15L ); //for testing 2 -> normally 1440
    MAX_PAYLOAD_FOR_BLOCK.put( (short) 1, (Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH)) / 2L  ); //use at max half size of the block.
    AVERAGE_BLOCK_MINUTES.put( (short) 1, 4L );
    // end of AT version 1		
  }
	
  public static AT_Constants getInstance( ){
    return instance;
  }
	
  public short AT_VERSION( int blockHeight ){
    return AT_VERSION.floorEntry( blockHeight ).getValue();
  }
	
  public long STEP_FEE( int height ){
    return STEP_FEE.get( AT_VERSION(height) );
  }
	
  public long MAX_STEPS( int height ){
    return MAX_STEPS.get( AT_VERSION(height) );
  }
	
  public long API_STEP_MULTIPLIER( int height ){
    return API_STEP_MULTIPLIER.get( AT_VERSION(height) );
  }
	
  public long COST_PER_PAGE( int height ){
    return COST_PER_PAGE.get( AT_VERSION(height) );
  }
	
  public long get_MAX_WAIT_FOR_NUM_OF_BLOCKS( int height ){
    return MAX_WAIT_FOR_NUM_OF_BLOCKS.get( AT_VERSION(height) );
  }
	
  public long MAX_SLEEP_BETWEEN_BLOCKS( int height ){
    return MAX_SLEEP_BETWEEN_BLOCKS.get( AT_VERSION(height) );
  }
	
  public long PAGE_SIZE( int height ){
    return PAGE_SIZE.get( AT_VERSION(height) );
  }
	
  public long MAX_MACHINE_CODE_PAGES( int height ){
    return MAX_MACHINE_CODE_PAGES.get( AT_VERSION(height) );
  }
	
  public long MAX_MACHINE_DATA_PAGES( int height ){
    return MAX_MACHINE_DATA_PAGES.get( AT_VERSION(height) );
  }
	
  public long MAX_MACHINE_USER_STACK_PAGES( int height ){
    return MAX_MACHINE_USER_STACK_PAGES.get( AT_VERSION(height) );
  }
	
  public long MAX_MACHINE_CALL_STACK_PAGES( int height ){
    return MAX_MACHINE_CALL_STACK_PAGES.get( AT_VERSION(height) );
  }
	
  public long BLOCKS_FOR_RANDOM( int height ){
    return BLOCKS_FOR_RANDOM.get( AT_VERSION(height) );
  }
	
  public long MAX_PAYLOAD_FOR_BLOCK( int height ){
    return MAX_PAYLOAD_FOR_BLOCK.get( AT_VERSION(height) );
  }

  public long AVERAGE_BLOCK_MINUTES(int height) {
    return AVERAGE_BLOCK_MINUTES.get( AT_VERSION(height) );
  }
}

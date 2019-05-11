package brs.at;

import brs.AT;
import brs.Account;
import brs.Burst;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.util.Convert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public abstract class AT_Controller {

  private static final Logger logger = LoggerFactory.getLogger(AT_Controller.class);
  
  private static final Logger debugLogger = Burst.getPropertyService().getBoolean(Props.ENABLE_AT_DEBUG_LOG) ? logger : NOPLogger.NOP_LOGGER;

  private static int runSteps(AT_Machine_State state) {
    state.getMachineState().running = true;
    state.getMachineState().stopped = false;
    state.getMachineState().finished = false;
    state.getMachineState().dead = false;
    state.getMachineState().steps = 0;

    AT_Machine_Processor processor = new AT_Machine_Processor(state, Burst.getPropertyService().getBoolean(Props.ENABLE_AT_DEBUG_LOG));

    //int height = Burst.getBlockchain().getHeight();

    state.setFreeze( false );

    long stepFee = AT_Constants.getInstance().STEP_FEE( state.getCreationBlockHeight() );

    int numSteps = 0;

    while ( state.getMachineState().steps +
            (numSteps = getNumSteps(state.getAp_code().get(state.getMachineState().pc), state.getCreationBlockHeight()))
            <= AT_Constants.getInstance().MAX_STEPS( state.getHeight() )) {

      if ( ( state.getG_balance() < stepFee * numSteps ) ) {
        debugLogger.debug( "stopped - not enough balance" );
        state.setFreeze( true );
        return 3;
      }

      state.setG_balance( state.getG_balance() - (stepFee * numSteps) );
      state.getMachineState().steps += numSteps;
      int rc = processor.processOp( false, false );

      if ( rc >= 0 ) {
        if ( state.getMachineState().stopped ) {
          debugLogger.debug( "stopped" );
          state.getMachineState().running = false;
          return 2;
        }
        else if ( state.getMachineState().finished ) {
          debugLogger.debug( "finished" );
          state.getMachineState().running = false;
          return 1;
        }
      }
      else {
        if ( rc == -1 )
          debugLogger.debug( "error: overflow" );
          else if ( rc==-2 )
          debugLogger.debug( "error: invalid code" );
          else
          debugLogger.debug( "unexpected error" );

        if (state.getMachineState().jumps.contains(state.getMachineState().err)) {
          state.getMachineState().pc = state.getMachineState().err;
        }
        else {
          state.getMachineState().dead = true;
          state.getMachineState().running = false;
          return 0;
        }
      }
    }

    return 5;
  }

  private static int getNumSteps(byte op, int height) {
    if (op >= 0x32 && op < 0x38)
      return (int)AT_Constants.getInstance().API_STEP_MULTIPLIER(height);

    return 1;
  }

  public static void resetMachine( AT_Machine_State state ) {
    state.getMachineState( ).reset( );
    listCode( state, true, true );
  }

  private static void listCode(AT_Machine_State state, boolean disassembly, boolean determine_jumps) {

    AT_Machine_Processor machineProcessor = new AT_Machine_Processor(state, Burst.getPropertyService().getBoolean(Props.ENABLE_AT_DEBUG_LOG));

    int opc = state.getMachineState().pc;
    int osteps = state.getMachineState().steps;

    state.getAp_code().order( ByteOrder.LITTLE_ENDIAN );
    state.getAp_data().order( ByteOrder.LITTLE_ENDIAN );

    state.getMachineState( ).pc = 0;
    state.getMachineState( ).opc = opc;

    while ( true ) {

      int rc = machineProcessor.processOp( disassembly, determine_jumps );
      if ( rc <= 0 ) break;

      state.getMachineState().pc += rc;
    }

    state.getMachineState().steps = osteps;
    state.getMachineState().pc = opc;
  }

  public static int checkCreationBytes( byte[] creation, int height ) throws AT_Exception{
    if (creation == null)
      throw new AT_Exception( "Creation bytes cannot be null" );

    int totalPages;
    try {
      ByteBuffer b = ByteBuffer.allocate( creation.length );
      b.order( ByteOrder.LITTLE_ENDIAN );

      b.put(  creation );
      b.clear();

      AT_Constants instance = AT_Constants.getInstance();

      short version = b.getShort();
      if ( version != instance.AT_VERSION( height ) ) {
        throw new AT_Exception( AT_Error.INCORRECT_VERSION.getDescription() );
      }

      short reserved = b.getShort(); //future: reserved for future needs

      short codePages = b.getShort();
      if ( codePages > instance.MAX_MACHINE_CODE_PAGES( height ) || codePages < 1) {
        throw new AT_Exception( AT_Error.INCORRECT_CODE_PAGES.getDescription() );
      }

      short dataPages = b.getShort();
      if ( dataPages > instance.MAX_MACHINE_DATA_PAGES( height ) || dataPages < 0) {
        throw new AT_Exception( AT_Error.INCORRECT_DATA_PAGES.getDescription() );
      }

      short callStackPages = b.getShort();
      if ( callStackPages > instance.MAX_MACHINE_CALL_STACK_PAGES( height ) || callStackPages < 0) {
        throw new AT_Exception( AT_Error.INCORRECT_CALL_PAGES.getDescription() );
      }

      short userStackPages = b.getShort();
      if ( userStackPages > instance.MAX_MACHINE_USER_STACK_PAGES( height ) || userStackPages < 0) {
        throw new AT_Exception( AT_Error.INCORRECT_USER_PAGES.getDescription() );
      }

      long minActivationAmount = b.getLong();

      int codeLen;
      if ( codePages * 256 < 257 ) {
        codeLen = b.get();
        if ( codeLen < 0 )
          codeLen += (Byte.MAX_VALUE + 1) * 2;
      }
      else if ( codePages * 256 < Short.MAX_VALUE + 1 ) {
        codeLen = b.getShort();
        if ( codeLen < 0 )
          codeLen += (Short.MAX_VALUE + 1) * 2;
      }
      else if ( codePages * 256 <= Integer.MAX_VALUE ) {
        codeLen = b.getInt();
      }
      else {
        throw new AT_Exception( AT_Error.INCORRECT_CODE_LENGTH.getDescription() );
      }

      if ( codeLen < 1 || codeLen > codePages * 256) {
        throw new AT_Exception( AT_Error.INCORRECT_CODE_LENGTH.getDescription() );
      }
      byte[] code = new byte[ codeLen ];
      b.get( code, 0, codeLen );

      int dataLen;
      if ( dataPages * 256 < 257 ) {
        dataLen = b.get();
        if ( dataLen < 0 )
          dataLen += (Byte.MAX_VALUE + 1) * 2;
      }
      else if ( dataPages * 256 < Short.MAX_VALUE + 1 ) {
        dataLen = b.getShort();
        if ( dataLen < 0 )
          dataLen += (Short.MAX_VALUE + 1) * 2;
      }
      else if ( dataPages * 256 <= Integer.MAX_VALUE ) {
        dataLen = b.getInt();
      }
      else {
        throw new AT_Exception( AT_Error.INCORRECT_CODE_LENGTH.getDescription() );
      }
      if ( dataLen < 0 || dataLen > dataPages * 256 ) {
        throw new AT_Exception( AT_Error.INCORRECT_DATA_LENGTH.getDescription() );
      }
      byte[] data = new byte[ dataLen ];
      b.get( data, 0, dataLen );

      totalPages = codePages + dataPages + userStackPages + callStackPages;
      /*if ( ( codePages + dataPages + userStackPages + callStackPages ) * instance.COST_PER_PAGE( height ) < txFeeAmount )
        {
        return AT_Error.INCORRECT_CREATION_FEE.getCode();
        }*/

      if ( b.position() != b.capacity() ) {
        throw new AT_Exception( AT_Error.INCORRECT_CREATION_TX.getDescription() );
      }

      //TODO note: run code in demo mode for checking if is valid

    }
    catch ( BufferUnderflowException e ) {
      throw new AT_Exception( AT_Error.INCORRECT_CREATION_TX.getDescription() );
    }

    return totalPages;
  }

  public static AT_Block getCurrentBlockATs( int freePayload, int blockHeight ){
    List< Long > orderedATs = AT.getOrderedATs();
    Iterator< Long > keys = orderedATs.iterator();

    List< AT > processedATs = new ArrayList< >();

    int costOfOneAT = getCostOfOneAT();
    int payload = 0;
    long totalFee = 0;
    long totalAmount = 0;

    while ( payload <= freePayload - costOfOneAT && keys.hasNext() ) {
      Long id = keys.next();
      AT at = AT.getAT( id );

      long atAccountBalance = getATAccountBalance( id );
      long atStateBalance = at.getG_balance();

      if ( at.freezeOnSameBalance() && (atAccountBalance - atStateBalance < at.minActivationAmount()) ) {
        continue;
      }

      if ( atAccountBalance >= AT_Constants.getInstance().STEP_FEE( at.getCreationBlockHeight() )
           * AT_Constants.getInstance().API_STEP_MULTIPLIER( at.getCreationBlockHeight() ) ) {
        try {
          at.setG_balance( atAccountBalance );
          at.setHeight(blockHeight);
          at.clearTransactions();
          at.setWaitForNumberOfBlocks( at.getSleepBetween() );
          listCode(at, true, true);
          runSteps ( at );

          long fee = at.getMachineState().steps * AT_Constants.getInstance().STEP_FEE( at.getCreationBlockHeight() );
          if ( at.getMachineState().dead ) {
            fee += at.getG_balance();
            at.setG_balance(0L);
          }
          at.setP_balance( at.getG_balance() );

          long amount = makeTransactions( at );
          if (! Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
            totalAmount = amount;
          }
          else {
            totalAmount += amount;
          }

          totalFee += fee;
          AT.addPendingFee(id, fee);

          payload += costOfOneAT;

          processedATs.add( at );

          //at.saveState();
        }
        catch ( Exception e ) {
          debugLogger.debug("Error handling AT", e);
        }
      }
    }

    byte[] bytesForBlock = null;

    try {
      bytesForBlock = getBlockATBytes( processedATs, payload );
    }
    catch ( NoSuchAlgorithmException e ) {
      //should not reach ever here
      logger.info("NoSuchAlgorithmException: ", e);
    }

      return new AT_Block( totalFee, totalAmount, bytesForBlock );
  }

  public static AT_Block validateATs(byte[] blockATs, int blockHeight) throws NoSuchAlgorithmException, AT_Exception {
    if (blockATs == null) {
      return new AT_Block(0, 0, null, true);
    }

    LinkedHashMap<ByteBuffer, byte[]> ats = getATsFromBlock(blockATs);

    List< AT > processedATs = new ArrayList< >();

    boolean validated = true;
    long totalFee = 0;
    MessageDigest digest = MessageDigest.getInstance( "MD5" );
    byte[] md5;
    long totalAmount = 0;

    for ( ByteBuffer atIdBuffer : ats.keySet() ) {
      byte[] atId = atIdBuffer.array();
      AT at = AT.getAT( atId );
      try {
        at.clearTransactions();
        at.setHeight(blockHeight);
        at.setWaitForNumberOfBlocks( at.getSleepBetween() );

        long atAccountBalance = getATAccountBalance( AT_API_Helper.getLong( atId ) );
        if (atAccountBalance < AT_Constants.getInstance().STEP_FEE( at.getCreationBlockHeight() )
            * AT_Constants.getInstance().API_STEP_MULTIPLIER( at.getCreationBlockHeight() ) ) {
          throw new AT_Exception( "AT has insufficient balance to run" );
        }

        if ( at.freezeOnSameBalance() && (atAccountBalance - at.getG_balance() < at.minActivationAmount()) ) {
          throw new AT_Exception( "AT should be frozen due to unchanged balance" );
        }

        if ( at.nextHeight() > blockHeight ) {
          throw new AT_Exception( "AT not allowed to run again yet" );
        }

        at.setG_balance( atAccountBalance );

        listCode(at, true, true);

        runSteps( at );

        long fee = at.getMachineState().steps * AT_Constants.getInstance().STEP_FEE( at.getCreationBlockHeight() );
        if ( at.getMachineState().dead ) {
          fee += at.getG_balance();
          at.setG_balance(0L);
        }
        at.setP_balance( at.getG_balance() );

        if (! Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_4, blockHeight)) {
          totalAmount = makeTransactions( at );
        }
        else {
          totalAmount += makeTransactions(at);
        }

        totalFee += fee;
        AT.addPendingFee(atId, fee);

        processedATs.add(at);

        md5 = digest.digest(at.getBytes());
        if (!Arrays.equals(md5, ats.get(atIdBuffer))) {
          throw new AT_Exception( "Calculated md5 and recieved md5 are not matching" );
        }
      }
      catch ( Exception e ) {
        debugLogger.debug("ATs error", e);
        throw new AT_Exception( "ATs error. Block rejected (" + e + ")");
      }
    }

    for ( AT at : processedATs ) {
        at.saveState();
      }

      return new AT_Block( totalFee, totalAmount, new byte[ 1 ], validated );
  }

  private static LinkedHashMap< ByteBuffer, byte[] > getATsFromBlock(byte[] blockATs) throws AT_Exception {
    if ( blockATs.length > 0 ) {
      if ( blockATs.length % (getCostOfOneAT() ) != 0 ) {
        throw new AT_Exception("blockATs must be a multiple of cost of one AT ( " + getCostOfOneAT() +" )" );
      }
    }

    ByteBuffer b = ByteBuffer.wrap( blockATs );
    b.order( ByteOrder.LITTLE_ENDIAN );

    byte[] temp = new byte[ AT_Constants.AT_ID_SIZE ];

    LinkedHashMap< ByteBuffer, byte[] > ats = new LinkedHashMap<>();

    while ( b.position() < b.capacity() ) {
      b.get( temp, 0, temp.length );
      byte[] md5 = new byte[ 16 ];
      b.get( md5, 0, md5.length );
      ByteBuffer atId = ByteBuffer.allocate(AT_Constants.AT_ID_SIZE);
      atId.put(temp);
      atId.clear();
      if (ats.containsKey(atId)) {
        throw new AT_Exception("AT included in block multiple times");
      }
      ats.put( atId, md5 );
    }

    if ( b.position() != b.capacity() ) {
      throw new AT_Exception("bytebuffer not matching");
    }

    return ats;
  }

  private static byte[] getBlockATBytes(List<AT> processedATs, int payload ) throws NoSuchAlgorithmException {
    if (payload <= 0) {
      return null;
    }

    ByteBuffer b = ByteBuffer.allocate( payload );
    b.order( ByteOrder.LITTLE_ENDIAN );

    MessageDigest digest = MessageDigest.getInstance( "MD5" );
    for ( AT at : processedATs ) {
      b.put( at.getId() );
      digest.update( at.getBytes() );
      b.put( digest.digest() );
    }

    return b.array();
  }

  private static int getCostOfOneAT() {
    return AT_Constants.AT_ID_SIZE + 16;
  }

  //platform based implementations
  //platform based
  private static long makeTransactions( AT at ) throws AT_Exception {
    long totalAmount = 0;
    if (! Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_4, at.getHeight())) {
      for (AT_Transaction tx : at.getTransactions()) {
        if (AT.findPendingTransaction(tx.getRecipientId())) {
          throw new AT_Exception("Conflicting transaction found");
        }
      }
    }
    for (AT_Transaction tx : at.getTransactions() ) {
      totalAmount += tx.getAmount();
      AT.addPendingTransaction(tx);
      logger.debug("Transaction to "
                   + Convert.toUnsignedLong(AT_API_Helper.getLong(tx.getRecipientId()))
                   + " amount "
                   + tx.getAmount() );
    }

    return totalAmount;
  }

  //platform based
  private static long getATAccountBalance( Long id ) {
    //Long accountId = AT_API_Helper.getLong( id );
    Account atAccount = Account.getAccount( id );

    if ( atAccount != null ) {
      return atAccount.getBalanceNQT();
    }

    return 0;
  }
}

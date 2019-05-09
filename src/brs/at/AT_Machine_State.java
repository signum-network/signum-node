/*
 * Copyright (c) 2014 CIYAM Developers

 Distributed under the MIT/X11 software license, please refer to the file license.txt
 in the root project directory or http://www.opensource.org/licenses/mit-license.php.
*/

package brs.at;

import brs.Burst;
import brs.fluxcapacitor.FluxValues;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeSet;


public class AT_Machine_State {

  public class Machine_State {
    transient boolean running;
    transient boolean stopped;
    transient boolean finished;
    transient boolean dead;

    int pc;
    int pcs;

    transient int opc;

    int cs;
    int us;

    int err;

    int steps;

    private byte[] A1 = new byte[8];
    private byte[] A2 = new byte[8];
    private byte[] A3 = new byte[8];
    private byte[] A4 = new byte[8];

    private byte[] B1 = new byte[8];
    private byte[] B2 = new byte[8];
    private byte[] B3 = new byte[8];
    private byte[] B4 = new byte[8];

    final byte[] flags = new byte[2];

    final TreeSet<Integer> jumps = new TreeSet<>();

    Machine_State() {
      pcs = 0;
      reset();
    }

    public boolean isRunning() {
      return running;
    }

    public boolean isStopped() {
      return stopped;
    }

    public boolean isFinished() {
      return finished;
    }

    public boolean isDead() {
      return dead;
    }

    void reset() {
      pc = pcs;
      opc = 0;
      cs = 0;
      us = 0;
      err = -1;
      steps = 0;
      if ( !jumps.isEmpty() )
        jumps.clear();
      flags[0] = 0;
      flags[1] = 0;
      running  = false;
      stopped  = true;
      finished = false;
      dead     = false;
    }

    /*void run()
      {
      running = true;
      }*/

    byte[] getMachineStateBytes() {
      ByteBuffer bytes = ByteBuffer.allocate( getSize() );
      bytes.order( ByteOrder.LITTLE_ENDIAN );

      if(Burst.getFluxCapacitor().getValue(FluxValues.AT_FIX_BLOCK_2)) {
        flags[0] = (byte)((running?1:0)
                          | (stopped?1:0) << 1
                          | (finished?1:0) << 2
                          | (dead?1:0) << 3);
        flags[1] = 0;
      }

      bytes.put( flags );

      bytes.putInt( machineState.pc );
      bytes.putInt( machineState.pcs );
      bytes.putInt( machineState.cs );
      bytes.putInt( machineState.us );
      bytes.putInt( machineState.err );

      bytes.put( A1 );
      bytes.put( A2 );
      bytes.put( A3 );
      bytes.put( A4 );
      bytes.put( B1 );
      bytes.put( B2 );
      bytes.put( B3 );
      bytes.put( B4 );

      return bytes.array();
    }

    private void setMachineState( byte[] machineState ) {
      ByteBuffer bf = ByteBuffer.allocate( getSize() );
      bf.order( ByteOrder.LITTLE_ENDIAN );
      bf.put( machineState );
      bf.flip();

      bf.get( flags, 0, 2 );
      running = (flags[0] & 1) == 1;
      stopped = (flags[0] >>> 1 & 1) == 1;
      finished = (flags[0] >>> 2 & 1) == 1;
      dead = (flags[0] >>> 3 & 1) == 1;

      pc = bf.getInt();
      pcs = bf.getInt();
      cs = bf.getInt();
      us = bf.getInt();
      err = bf.getInt();
      bf.get( A1, 0, 8 );
      bf.get( A2, 0, 8 );
      bf.get( A3, 0, 8 );
      bf.get( A4, 0, 8 );
      bf.get( B1, 0, 8 );
      bf.get( B2, 0, 8 );
      bf.get( B3, 0, 8 );
      bf.get( B4, 0, 8 );
    }

    int getSize() {
      return 2 + 4 + 4 + 4 + 4 + 4 + 4*8 + 4*8;
    }

    public long getSteps() {
      return steps;
    }
  }

  private short version;

  private long g_balance;
  private long p_balance;

  private Machine_State machineState;

  private int csize;
  private int dsize;
  private int c_user_stack_bytes;
  private int c_call_stack_bytes;

  private byte[] atID = new byte[ AT_Constants.AT_ID_SIZE ];
  private byte[] creator = new byte[ AT_Constants.AT_ID_SIZE ];

  private final int creationBlockHeight;

  private int waitForNumberOfBlocks;

  private final int sleepBetween;

  private boolean freezeWhenSameBalance;

  private long minActivationAmount;


  private transient ByteBuffer ap_data;

  private final transient ByteBuffer ap_code;

  private int height;

  private final LinkedHashMap<ByteBuffer, AT_Transaction> transactions;

  protected AT_Machine_State(byte[] atId, byte[] creator, short version,
                             byte[] stateBytes, int csize, int dsize, int c_user_stack_bytes, int c_call_stack_bytes,
                             int creationBlockHeight, int sleepBetween,
                             boolean freezeWhenSameBalance, long minActivationAmount, byte[] apCode) {
    this.atID = atId;
    this.creator = creator;
    this.version = version;
    this.machineState = new Machine_State();
    this.setState( stateBytes );
    this.csize = csize;
    this.dsize = dsize;
    this.c_user_stack_bytes = c_user_stack_bytes;
    this.c_call_stack_bytes = c_call_stack_bytes;
    this.creationBlockHeight = creationBlockHeight;
    this.sleepBetween = sleepBetween;
    this.freezeWhenSameBalance = freezeWhenSameBalance;
    this.minActivationAmount = minActivationAmount;

    this.ap_code = ByteBuffer.allocate( apCode.length );
    ap_code.order( ByteOrder.LITTLE_ENDIAN );
    ap_code.put( apCode );
    ap_code.clear();

    transactions = new LinkedHashMap<>();
  }

  protected AT_Machine_State(byte[] atId, byte[] creator, byte[] creationBytes, int height) {
    this.version = AT_Constants.getInstance().AT_VERSION( height );
    this.atID = atId;
    this.creator = creator;

    ByteBuffer b = ByteBuffer.allocate( creationBytes.length );
    b.order( ByteOrder.LITTLE_ENDIAN );

    b.put( creationBytes );
    b.clear();

    this.version = b.getShort();

    b.getShort(); //future: reserved for future needs

    int pageSize = ( int ) AT_Constants.getInstance().PAGE_SIZE( height );
    short codePages = b.getShort();
    short dataPages = b.getShort();
    short callStackPages = b.getShort();
    short userStackPages = b.getShort();

    this.csize = codePages * pageSize;
    this.dsize = dataPages * pageSize;
    this.c_call_stack_bytes = callStackPages * pageSize;
    this.c_user_stack_bytes = userStackPages * pageSize;

    this.minActivationAmount = b.getLong();

    int codeLen;
    if ( codePages * pageSize < pageSize + 1 ) {
      codeLen = b.get();
      if ( codeLen < 0 )
        codeLen += (Byte.MAX_VALUE + 1) * 2;
    }
    else if ( codePages * pageSize < Short.MAX_VALUE + 1 ) {
      codeLen = b.getShort();
      if( codeLen < 0 )
        codeLen += (Short.MAX_VALUE + 1) * 2;
    }
    else {
      codeLen = b.getInt();
    }
    byte[] code = new byte[ codeLen ];
    b.get( code, 0, codeLen );

    this.ap_code = ByteBuffer.allocate( csize );
    this.ap_code.order( ByteOrder.LITTLE_ENDIAN );
    this.ap_code.put( code );
    this.ap_code.clear();

    int dataLen;
    if ( dataPages * pageSize < 257 ) {
      dataLen = b.get();
      if ( dataLen < 0 )
        dataLen += (Byte.MAX_VALUE + 1) * 2;
    }
    else if ( dataPages * pageSize < Short.MAX_VALUE + 1 ) {
      dataLen = b.getShort();
      if ( dataLen < 0 )
        dataLen += (Short.MAX_VALUE + 1) * 2;
    }
    else {
      dataLen = b.getInt();
    }
    byte[] data = new byte[ dataLen ];
    b.get( data, 0, dataLen );

    this.ap_data = ByteBuffer.allocate( this.dsize + this.c_call_stack_bytes + this.c_user_stack_bytes );
    this.ap_data.order( ByteOrder.LITTLE_ENDIAN );
    this.ap_data.put( data );
    this.ap_data.clear();

    this.height = height;
    this.creationBlockHeight = height;
    this.waitForNumberOfBlocks = 0;
    this.sleepBetween = 0;
    this.freezeWhenSameBalance = false;
    this.transactions = new LinkedHashMap<>();
    this.g_balance = 0;
    this.p_balance = 0;
    this.machineState = new Machine_State();
  }

  byte[] get_A1() {
    return machineState.A1;
  }

  byte[] get_A2() {
    return machineState.A2;
  }

  byte[] get_A3() {
    return machineState.A3;
  }

  byte[] get_A4() {
    return machineState.A4;
  }

  byte[] get_B1() {
    return machineState.B1;
  }

  byte[] get_B2() {
    return machineState.B2;
  }

  byte[] get_B3() {
    return machineState.B3;
  }

  byte[] get_B4() {
    return machineState.B4;
  }

  void set_A1(byte[] A1) {
    this.machineState.A1 = A1.clone();
  }

  void set_A2(byte[] A2){
    this.machineState.A2 = A2.clone();
  }

  void set_A3(byte[] A3) {
    this.machineState.A3 = A3.clone();
  }

  void set_A4(byte[] A4) {
    this.machineState.A4 = A4.clone();
  }

  void set_B1(byte[] B1) {
    this.machineState.B1 = B1.clone();
  }

  void set_B2(byte[] B2) {
    this.machineState.B2 = B2.clone();
  }

  void set_B3(byte[] B3) {
    this.machineState.B3 = B3.clone();
  }

  void set_B4(byte[] B4) {
    this.machineState.B4 = B4.clone();
  }

  void addTransaction(AT_Transaction tx) {
    ByteBuffer recipId = ByteBuffer.wrap(tx.getRecipientId());
    AT_Transaction oldTx = transactions.get(recipId);
    if (oldTx == null) {
      transactions.put(recipId, tx);
    }
    else {
      AT_Transaction newTx = new AT_Transaction(tx.getSenderId(),
                                                tx.getRecipientId(),
                                                oldTx.getAmount() + tx.getAmount(),
                                                tx.getMessage() != null ? tx.getMessage() : oldTx.getMessage());
      transactions.put(recipId, newTx);
    }
  }

  protected void clearTransactions() {
    transactions.clear();
  }

  public Collection<AT_Transaction> getTransactions() {
    return transactions.values();
  }

  protected ByteBuffer getAp_code()  {
    return ap_code;
  }

  public ByteBuffer getAp_data()  {
    return ap_data;
  }

  public int getC_call_stack_bytes() {
    return c_call_stack_bytes;
  }

  public int getC_user_stack_bytes() {
    return c_user_stack_bytes;
  }

  public int getCsize() {
    return csize;
  }

  public int getDsize() {
    return dsize;
  }

  public Long getG_balance() {
    return g_balance;
  }

  public Long getP_balance() {
    return p_balance;
  }

  public byte[] getId() {
    return atID;
  }

  public Machine_State getMachineState() {
    return machineState;
  }

  protected void setC_call_stack_bytes(int c_call_stack_bytes) {
    this.c_call_stack_bytes = c_call_stack_bytes;
  }

  protected void setC_user_stack_bytes(int c_user_stack_bytes) {
    this.c_user_stack_bytes = c_user_stack_bytes;
  }

  protected void setCsize(int csize) {
    this.csize = csize;
  }

  protected void setDsize(int dsize) {
    this.dsize = dsize;
  }

  public void setG_balance(Long g_balance) {
    this.g_balance = g_balance;
  }

  public void setP_balance(Long p_balance) {
    this.p_balance = p_balance;
  }

  public void setMachineState(Machine_State machineState) {
    this.machineState = machineState;
  }

  public void setWaitForNumberOfBlocks(int waitForNumberOfBlocks) {
    this.waitForNumberOfBlocks = waitForNumberOfBlocks;
  }

  protected int getWaitForNumberOfBlocks() {
    return this.waitForNumberOfBlocks;
  }

  public byte[] getCreator() {
    return this.creator;
  }

  public int getCreationBlockHeight() {
    return this.creationBlockHeight;
  }

  public boolean freezeOnSameBalance() {
    return this.freezeWhenSameBalance;
  }

  public long minActivationAmount() {
    return this.minActivationAmount;
  }

  public void setMinActivationAmount(long minActivationAmount) {
    this.minActivationAmount = minActivationAmount;
  }

  public short getVersion() {
    return version;
  }

  public int getSleepBetween() {
    return sleepBetween;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  private byte[] getTransactionBytes() {
    ByteBuffer b = ByteBuffer.allocate( (creator.length + 8 ) * transactions.size() );
    b.order( ByteOrder.LITTLE_ENDIAN );
    for (AT_Transaction tx : transactions.values() ) {
        b.put( tx.getRecipientId() );
        b.putLong( tx.getAmount() );
      }
    return b.array();
  }

  protected byte[] getState() {
    byte[] stateBytes = machineState.getMachineStateBytes();
    byte[] dataBytes = ap_data.array();

    ByteBuffer b = ByteBuffer.allocate( getStateSize() );
    b.order( ByteOrder.LITTLE_ENDIAN );

    b.put( stateBytes );
    b.putLong( g_balance );
    b.putLong( p_balance );
    b.putInt( waitForNumberOfBlocks );
    b.put( dataBytes );

    return b.array();
  }

  private void setState(byte[] state) {
    ByteBuffer b = ByteBuffer.allocate( state.length );
    b.order( ByteOrder.LITTLE_ENDIAN );
    b.put( state );
    b.flip();

    int stateSize = this.machineState.getSize();
    byte[] machineState = new byte[ stateSize ];
    b.get( machineState, 0, stateSize );
    this.machineState.setMachineState( machineState );

    g_balance = b.getLong();
    p_balance = b.getLong();
    waitForNumberOfBlocks = b.getInt();

    byte[] apData = new byte[ b.capacity() - b.position() ];
    b.get( apData );
    ap_data = ByteBuffer.allocate( apData.length );
    ap_data.order( ByteOrder.LITTLE_ENDIAN );
    ap_data.put( apData );
    ap_data.clear();
  }

  private int getStateSize() {
    return ( this.machineState.getSize() + 8 + 8 + 4 + ap_data.capacity() ) ;
  }

  //these bytes are digested with MD5
  public byte[] getBytes() {
    byte[] txBytes = getTransactionBytes();
    byte[] stateBytes = machineState.getMachineStateBytes();
    byte[] dataBytes = ap_data.array();

    ByteBuffer b = ByteBuffer.allocate( atID.length + txBytes.length + stateBytes.length + dataBytes.length );
    b.order( ByteOrder.LITTLE_ENDIAN );

    b.put( atID );
    b.put( stateBytes );
    b.put( dataBytes );
    b.put( txBytes );

    return b.array();
  }

  public void setFreeze(boolean freeze) {
    this.freezeWhenSameBalance = freeze;
  }
}

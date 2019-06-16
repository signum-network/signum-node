package brs.util;

import brs.Block;
import brs.Blockchain;
import brs.Constants;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

public final class DownloadCacheImpl {
  private final int blockCacheMB;

  private final Map<Long, Block> blockCache = new LinkedHashMap<>();
  private final List<Block> forkCache = new ArrayList<>();
  private final Map<Long, Long> reverseCache = new LinkedHashMap<>();
  private final List<Long> unverified = new LinkedList<>();

  private final Logger logger = LoggerFactory.getLogger(DownloadCacheImpl.class);

  private final Blockchain blockchain;
  private final FluxCapacitor fluxCapacitor;

  private int blockCacheSize = 0;

  private Long lastBlockId = null;
  private int lastHeight = -1;
  private BigInteger highestCumulativeDifficulty = BigInteger.ZERO;

  private final StampedLock dcsl = new StampedLock();
  
  private boolean lockedCache = false;
  
  
  public DownloadCacheImpl(PropertyService propertyService, FluxCapacitor fluxCapacitor, Blockchain blockchain) {
    this.blockCacheMB = propertyService.getInt(Props.BRS_BLOCK_CACHE_MB);
    this.fluxCapacitor = fluxCapacitor;
    this.blockchain = blockchain;
  }

  private int getChainHeight() {
    long stamp = dcsl.tryOptimisticRead();
    int retVal = lastHeight;
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        retVal = lastHeight;
      } finally {
         dcsl.unlockRead(stamp);
      }
   }
   if (retVal > -1) {
      return retVal;
    }
    return blockchain.getHeight();
  }
  public void lockCache() {
    long stamp = dcsl.writeLock();
	try {
	  lockedCache = true;
	} finally {
	  dcsl.unlockWrite(stamp);
	}
	setLastVars();
  }
  public void unlockCache() {
    long stamp = dcsl.tryOptimisticRead();
	boolean retVal = lockedCache;
	if (!dcsl.validate(stamp)) {
	  stamp = dcsl.readLock();
	  try {
	    retVal = lockedCache;
	  } finally {
	    dcsl.unlockRead(stamp);
	  }
	}
	
	if(retVal) {
	  stamp = dcsl.writeLock();
	  try {
	    lockedCache = false;
	  } finally {
		dcsl.unlockWrite(stamp);
      }	
	}
  }
  private boolean getLockState() {
    long stamp = dcsl.tryOptimisticRead();
	boolean retVal = lockedCache;
	if (!dcsl.validate(stamp)) {
	  stamp = dcsl.readLock();
	  try {
	    retVal = lockedCache;
      } finally {
		dcsl.unlockRead(stamp);
      }
    }
	return retVal;
  }
  
  public int getBlockCacheSize() {
    long stamp = dcsl.tryOptimisticRead();
    int retVal = blockCacheSize;
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        retVal = blockCacheSize;
      } finally {
         dcsl.unlockRead(stamp);
      }
   }
    return retVal;
  }
 
  
  public boolean isFull() {
    long stamp = dcsl.tryOptimisticRead();
    int retVal = blockCacheSize;
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        retVal = blockCacheSize;
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    return retVal > blockCacheMB * 1024 * 1024;
  }

  public int getUnverifiedSize() {
    long stamp = dcsl.tryOptimisticRead();
    int retVal = unverified.size();
    if (!dcsl.validate(stamp)) {
      stamp = dcsl.readLock();
      try {
        retVal = unverified.size();
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    return retVal;
  }

  public BigInteger getCumulativeDifficulty() {
    long stamp = dcsl.tryOptimisticRead();
    Long lbID = lastBlockId;
    BigInteger retVal = highestCumulativeDifficulty;
    
   
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        lbID = lastBlockId;
        retVal = highestCumulativeDifficulty;
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    if (lbID != null) {
      return retVal;
    }
    setLastVars();
    stamp = dcsl.tryOptimisticRead();
    retVal = highestCumulativeDifficulty;
    if (!dcsl.validate(stamp)) {
      stamp = dcsl.readLock();
      try {
        retVal = highestCumulativeDifficulty;
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    return retVal;
  }

  public long getUnverifiedBlockIdFromPos(int pos) {
    long stamp = dcsl.tryOptimisticRead();
    long reVal = unverified.get(pos);
    
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        reVal = unverified.get(pos);
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    return reVal;
  }
  public Block getFirstUnverifiedBlock() {
	 long stamp = dcsl.writeLock();
	 try {
		 long blockId = unverified.get(0);
		 Block block = blockCache.get(blockId);
		 unverified.remove(blockId);
		 return block;
	 } finally {
      dcsl.unlockWrite(stamp);
    }
  }

  public void removeUnverified(long blockId) {
    long stamp = dcsl.writeLock();
    try {
      unverified.remove(blockId);
    } finally {
      dcsl.unlockWrite(stamp);
    }
  }
  
  public void removeUnverifiedBatch(Collection<Block> blocks) {
    long stamp = dcsl.writeLock();
    try {
      for (Block block : blocks) {
        unverified.remove(block.getId());
      }
    } finally {
      dcsl.unlockWrite(stamp);
    }
  }

  public void resetCache() {
    long stamp = dcsl.writeLock();
    try {
      blockCache.clear();
      reverseCache.clear();
      unverified.clear();
      blockCacheSize = 0;
      lockedCache = true;
    } finally {
     dcsl.unlockWrite(stamp);
    }
    setLastVars();
  }

  public Block getBlock(long blockId) {
	//search the forkCache if we have a forkList
    if(!forkCache.isEmpty()) {
      for (Block block : forkCache) {
        if(block.getId() == blockId) {
        	return block;
        }
      }
    }
    long stamp = dcsl.tryOptimisticRead();
    Block retVal = getBlockInt(blockId);
    if (!dcsl.validate(stamp)) {
      stamp = dcsl.readLock();
      try {
        retVal = getBlockInt(blockId);
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    if(retVal != null) {
      return retVal;
    }
    if (blockchain.hasBlock(blockId)) {
      return blockchain.getBlock(blockId);
    }
    return null;
  }
  private Block getBlockInt(long blockId) {
    if (blockCache.containsKey(blockId)) {
      return blockCache.get(blockId);
    }
    return null;
  }

  public Block getNextBlock(long prevBlockId) {
    long stamp = dcsl.tryOptimisticRead();
    Block retVal = getNextBlockInt(prevBlockId);
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        retVal = getNextBlockInt(prevBlockId);
      } finally {
        dcsl.unlockRead(stamp);
      }
    }
    return retVal;
  }

  private Block getNextBlockInt(long prevBlockId) {
    if (reverseCache.containsKey(prevBlockId)) {
      return blockCache.get(reverseCache.get(prevBlockId));
    }
    return null;
  }

  public boolean hasBlock(long blockId) {
    long stamp = dcsl.tryOptimisticRead();
    boolean retVal =  blockCache.containsKey(blockId);
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        retVal =  blockCache.containsKey(blockId);
      } finally {
        dcsl.unlockRead(stamp);
      }
    }
    if (retVal) {
      return true;
    }
    return blockchain.hasBlock(blockId);
  }

  public boolean canBeFork(long oldBlockId) {
    int curHeight = getChainHeight();
    Block block = dcslRead(() -> getBlockInt(oldBlockId));
    if (block == null && blockchain.hasBlock(oldBlockId)) {
      block = blockchain.getBlock(oldBlockId);
    }
    if (block == null) {
      return false;
    }
    return (curHeight - block.getHeight()) <= Constants.MAX_ROLLBACK;
  }

  public boolean addBlock(Block block) {
    if(!getLockState()) {
	  long stamp = dcsl.writeLock();
      try {
        blockCache.put(block.getId(), block);
        reverseCache.put(block.getPreviousBlockId(), block.getId());
        unverified.add(block.getId());
        blockCacheSize += block.getByteLength();
        lastBlockId = block.getId();
        lastHeight = block.getHeight();
        highestCumulativeDifficulty = block.getCumulativeDifficulty();
        return true;
      } finally {
        dcsl.unlockWrite(stamp);
      }
    }
    return false;
  }
  public void addForkBlock(Block block) {
    forkCache.add(block);
  }
  public void resetForkBlocks() {
    forkCache.clear();  
  }
  public List<Block> getForkList(){
    return forkCache;
  }

  public boolean removeBlock(Block block) {
    long stamp = dcsl.tryOptimisticRead();
    boolean chkVal = blockCache.containsKey(block.getId());
    long lastId = lastBlockId;
    
    if (!dcsl.validate(stamp)) {
      stamp = dcsl.readLock();
      try {
        chkVal = blockCache.containsKey(block.getId());
      } finally {
         dcsl.unlockRead(stamp);
      }
    }
    
    if (chkVal) { // make sure there is something to remove
      stamp = dcsl.writeLock();
      try {
    	unverified.remove(block.getId());
        reverseCache.remove(block.getPreviousBlockId());
        blockCache.remove(block.getId());
        blockCacheSize -= block.getByteLength();
      } finally {
        dcsl.unlockWrite(stamp);
      }
      if (block.getId() == lastId) {
        setLastVars();
      }
      return true;
    }
    return false;
  }

  public int getPoCVersion(long blockId) {
    Block blockImpl = getBlock(blockId);
    return (blockImpl == null || ! fluxCapacitor.getValue(FluxValues.POC2, blockImpl.getHeight()) ) ? 1 : 2;
  }
  
  public long getLastBlockId() {
    Long lId = getLastCacheId();
    if (lId != null) {
      return lId;
    }
    return blockchain.getLastBlock().getId();
  }
  private Long getLastCacheId() {
    long stamp = dcsl.tryOptimisticRead();
    Long lId = lastBlockId;
    if (!dcsl.validate(stamp)) {
     
      stamp = dcsl.readLock();
      try {
        lId = lastBlockId;
      } finally {
        dcsl.unlockRead(stamp);
      }
    }
    return lId;
  }

  private <T> T dcslRead(Supplier<T> supplier) {
    return StampedLockUtils.stampedLockRead(dcsl, supplier);
  }
    
  public Block getLastBlock() {
    Long iLd = getLastCacheId();
    if (iLd != null && blockCache.containsKey(iLd)) {
      return dcslRead(() -> blockCache.get(iLd));
    }
    return blockchain.getLastBlock();
  }

  public int size() {
    return dcslRead(blockCache::size);
  }

  public void printDebug() {
    logger.info("BlockCache size: {}", blockCache.size());
    logger.info("Unverified size: {}", unverified.size());
    logger.info("Verified size: {}", (blockCache.size() - unverified.size()));
    
  }
  private void printLastVars() {
	logger.debug("Cache LastId: {}", lastBlockId);
	logger.debug("Cache lastHeight: {}", lastHeight);
  }
    

  private void setLastVars() {
    long stamp = dcsl.writeLock();
    try {
      if (! blockCache.isEmpty()) {
        lastBlockId = blockCache.get(blockCache.keySet().toArray(new Long[0])[blockCache.keySet().size() - 1]).getId();
        lastHeight = blockCache.get(lastBlockId).getHeight();
        highestCumulativeDifficulty = blockCache.get(lastBlockId).getCumulativeDifficulty();
        logger.debug("Cache set to CacheData");
        printLastVars();
      } else {
        lastBlockId = blockchain.getLastBlock().getId();
        lastHeight = blockchain.getHeight();
        highestCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
        logger.debug("Cache set to ChainData");
        printLastVars();
      }
    } finally {
      dcsl.unlockWrite(stamp);
    }
  }
}

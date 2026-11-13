package brs;

import brs.util.Listener;
import brs.util.ThreadPool;

import java.math.BigInteger;
import java.util.Collection;

public interface Generator {

  enum Event {
    GENERATION_DEADLINE, NONCE_SUBMITTED
  }

  void generateForBlockchainProcessor(ThreadPool threadPool, BlockchainProcessor blockchainProcessor);

  boolean addListener(Listener<GeneratorState> listener, Event eventType);

  boolean removeListener(Listener<GeneratorState> listener, Event eventType);

  GeneratorState addNonce(String secretPhrase, Long nonce);

  GeneratorState addNonce(String secretPhrase, Long nonce, byte[] publicKey);

  Collection<GeneratorState> getAllGenerators();

  byte[] calculateGenerationSignature(byte[] lastGenSig, long lastGenId);

  int calculateScoop(byte[] genSig, long height);

  BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop, int blockHeight);

  BigInteger calculateHit(byte[] genSig, byte[] scoopData);

  /**
   * Calculates the scoops of a batch of blocks in one call, using SIMD instructions where the native
   * library supports them. The hit of block {@code i} is {@code calculateHit(genSig, scoop i)}.
   *
   * @param accountIds generator ids of the blocks
   * @param nonces nonces of the blocks
   * @param scoops scoop numbers of the blocks, see {@link #calculateScoop(byte[], long)}
   * @param blockHeights heights of the blocks, they determine the PoC version
   * @return {@code accountIds.length * 64} bytes, the scoop of block {@code i} starts at offset {@code i * 64}
   * @throws IllegalArgumentException if the arrays differ in length or a scoop is out of range
   */
  byte[] calculateScoops(long[] accountIds, long[] nonces, int[] scoops, int[] blockHeights);

  BigInteger calculateDeadline(BigInteger hit, long capacityBaseTarget, long commitment, long averageCommitment, int blockHeight);

  long estimateCommitment(long generatorId, Block prevBlock);
  
  double getCommitmentFactor(long commitment, long averageCommitment, int blockHeight);
  
  interface GeneratorState {
    byte[] getPublicKey();

    Long getAccountId();

    BigInteger getDeadline();

    BigInteger getDeadlineLegacy();

    long getBlock();
  }
}

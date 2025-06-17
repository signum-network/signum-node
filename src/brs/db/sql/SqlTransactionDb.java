package brs.db.sql;

import brs.Appendix;
import brs.SignumException;
import brs.Transaction;
import brs.TransactionType;
import brs.db.TransactionDb;
import brs.db.cache.TransactionCache;
import brs.schema.tables.records.TransactionRecord;
import brs.util.Convert;

import java.util.ArrayList;
import org.jooq.SelectConditionStep;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

import static brs.schema.Tables.TRANSACTION;

public class SqlTransactionDb implements TransactionDb {

  @Override
  public Transaction findTransaction(long transactionId) {
    Transaction cached = TransactionCache.getInstance().getById(transactionId);
    if (cached != null) {
      return cached;
    }
    return Db.useDSLContext(ctx -> {
      try {
        TransactionRecord transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.ID.eq(transactionId)).fetchOne();
        return loadTransaction(transactionRecord);
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException("Transaction already in database, id = " + transactionId + ", does not pass validation!", e);
      }
    });
  }

  @Override
  public Transaction findTransactionByFullHash(String fullHash) {
    Transaction cached = TransactionCache.getInstance().getByHash(fullHash);
    if (cached != null) {
      return cached;
    }
    return Db.useDSLContext(ctx -> {
      try {
        TransactionRecord transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(Convert.parseHexString(fullHash))).fetchOne();
        return loadTransaction(transactionRecord);
      } catch (SignumException.ValidationException e) {
        throw new RuntimeException("Transaction already in database, full_hash = " + fullHash + ", does not pass validation!", e);
      }
    });
  }

  @Override
  public boolean hasTransaction(long transactionId) {
    if (TransactionCache.getInstance().getById(transactionId) != null) {
      return true;
    }
    return Db.useDSLContext(ctx -> {
      return ctx.fetchExists(ctx.selectFrom(TRANSACTION).where(TRANSACTION.ID.eq(transactionId)));
    });
  }

  @Override
  public boolean hasTransactionByFullHash(String fullHash) {
    if (TransactionCache.getInstance().getByHash(fullHash) != null) {
      return true;
    }
    return Db.useDSLContext(ctx -> {
      return ctx.fetchExists(ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(Convert.parseHexString(fullHash))));
    });
  }

  @Override
  public Transaction loadTransaction(TransactionRecord tr) throws SignumException.ValidationException {
    if (tr == null) {
      return null;
    }

    ByteBuffer buffer = null;
    if (tr.getAttachmentBytes() != null) {
      buffer = ByteBuffer.wrap(tr.getAttachmentBytes());
      buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    TransactionType transactionType = TransactionType.findTransactionType(tr.getType(), tr.getSubtype());
    Transaction.Builder builder = new Transaction.Builder(tr.getVersion(), tr.getSenderPublicKey(),
            tr.getAmount(), tr.getFee(), tr.getTimestamp(), tr.getDeadline(),
            transactionType.parseAttachment(buffer, tr.getVersion()))
            .referencedTransactionFullHash(tr.getReferencedTransactionFullhash())
            .signature(tr.getSignature())
            .blockId(tr.getBlockId())
            .height(tr.getHeight())
            .id(tr.getId())
            .senderId(tr.getSenderId())
            .blockTimestamp(tr.getBlockTimestamp())
            .fullHash(tr.getFullHash());
    if (transactionType.hasRecipient()) {
      builder.recipientId(Optional.ofNullable(tr.getRecipientId()).orElse(0L));
    }
    if (tr.getHasMessage()) {
      builder.message(new Appendix.Message(buffer, tr.getVersion()));
    }
    if (tr.getHasEncryptedMessage()) {
      builder.encryptedMessage(new Appendix.EncryptedMessage(buffer, tr.getVersion()));
    }
    if (tr.getHasPublicKeyAnnouncement()) {
      builder.publicKeyAnnouncement(new Appendix.PublicKeyAnnouncement(buffer, tr.getVersion()));
    }
    if (tr.getHasEncrypttoselfMessage()) {
      builder.encryptToSelfMessage(new Appendix.EncryptToSelfMessage(buffer, tr.getVersion()));
    }
    if (tr.getVersion() > 0) {
      builder.ecBlockHeight(tr.getEcBlockHeight());
      builder.ecBlockId(Optional.ofNullable(tr.getEcBlockId()).orElse(0L));
    }
    if (tr.getVersion() > 1) {
      builder.cashBackId(tr.getCashBackId());
    }

    return builder.build();
  }

  @Override
  public List<Transaction> findBlockTransactions(long blockId, boolean onlySigned) {
    List<Transaction> cached = TransactionCache.getInstance().getBlockTransactions(blockId);
    if (cached != null) {
      if (!onlySigned) {
        return cached;
      } else {
        return cached.stream().filter(t -> t.getSignature() != null).toList();
      }
    }
    return Db.useDSLContext(ctx -> {
      SelectConditionStep<TransactionRecord> select = ctx.selectFrom(TRANSACTION)
          .where(TRANSACTION.BLOCK_ID.eq(blockId));
      if(onlySigned) {
        select = select.and(TRANSACTION.SIGNATURE.isNotNull());
      }
      return select.fetch(record -> {
                try {
                  return loadTransaction(record);
                } catch (SignumException.ValidationException e) {
                  e.printStackTrace();
                  throw new RuntimeException("Invalid transaction :" + e.getMessage(), e);
                }
              });
    });
  }

  public static byte[] getAttachmentBytes(Transaction transaction) {
    int bytesLength = 0;
    for (Appendix appendage : transaction.getAppendages()) {
      bytesLength += appendage.getSize();
    }
    if (bytesLength == 0) {
      return null;
    } else {
      ByteBuffer buffer = ByteBuffer.allocate(bytesLength);
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      for (Appendix appendage : transaction.getAppendages()) {
        appendage.putBytes(buffer);
      }
      return buffer.array();
    }
  }

  public void saveTransactions(List<Transaction> transactions) {
    if (!transactions.isEmpty()) {
      Db.useDSLContext(ctx -> {
        List<TransactionRecord> records = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
          TransactionRecord record = ctx.newRecord(TRANSACTION);
          record.setId(transaction.getId());
          record.setDeadline(transaction.getDeadline());
          record.setSenderPublicKey(transaction.getSenderPublicKey());
          record.setRecipientId(transaction.getRecipientId() == 0 ? null : transaction.getRecipientId());
          record.setAmount(transaction.getAmountNqt());
          record.setFee(transaction.getFeeNqt());
          record.setReferencedTransactionFullhash(Convert.parseHexString(transaction.getReferencedTransactionFullHash()));
          record.setHeight(transaction.getHeight());
          record.setBlockId(transaction.getBlockId());
          record.setSignature(transaction.getSignature());
          record.setTimestamp(transaction.getTimestamp());
          record.setType(transaction.getType().getType());
          record.setSubtype(transaction.getType().getSubtype());
          record.setSenderId(transaction.getSenderId());
          record.setAttachmentBytes(getAttachmentBytes(transaction));
          record.setBlockTimestamp(transaction.getBlockTimestamp());
          record.setFullHash(Convert.parseHexString(transaction.getFullHash()));
          record.setVersion(transaction.getVersion());
          record.setHasMessage(transaction.getMessage() != null);
          record.setHasEncryptedMessage(transaction.getEncryptedMessage() != null);
          record.setHasPublicKeyAnnouncement(transaction.getPublicKeyAnnouncement() != null);
          record.setHasEncrypttoselfMessage(transaction.getEncryptToSelfMessage() != null);
          record.setEcBlockHeight(transaction.getEcBlockHeight());
          record.setEcBlockId(transaction.getEcBlockId() != 0 ? transaction.getEcBlockId() : null);
          record.setCashBackId(transaction.getCashBackId());
          records.add(record);
        }
        ctx.batchInsert(records).execute();
      });
    }
  }

  @Override
  public void optimize() {
    Db.optimizeTable(TRANSACTION.getName());
  }
}

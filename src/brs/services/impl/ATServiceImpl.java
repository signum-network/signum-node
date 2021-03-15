package brs.services.impl;

import brs.at.AT;
import brs.db.store.ATStore;
import brs.services.ATService;

import java.util.Collection;
import java.util.List;

public class ATServiceImpl implements ATService {

  private final ATStore atStore;

  public ATServiceImpl(ATStore atStore) {
    this.atStore = atStore;
  }

  @Override
  public Collection<Long> getAllATIds() {
    return atStore.getAllATIds();
  }

  @Override
  public List<Long> getATsIssuedBy(Long accountId) {
    return atStore.getATsIssuedBy(accountId);
  }

  @Override
  public AT getAT(Long id, int height) {
    return atStore.getAT(id, height);
  }

  @Override
  public AT getAT(Long id) {
    return atStore.getAT(id);
  }

}

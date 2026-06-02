package brs.services;

import brs.at.AT;
import brs.util.CollectionWithIndex;

import java.util.Collection;

public interface ATService {

  Collection<Long> getAllATIds(Long codeHashId);

  int getATCount();

  CollectionWithIndex<Long> getATsIssuedBy(Long accountId, Long codeHashId, int from, int to);

  AT getAT(Long atId);

  AT getAT(Long atId, int height);
}

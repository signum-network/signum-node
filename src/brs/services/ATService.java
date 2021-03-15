package brs.services;

import brs.at.AT;

import java.util.Collection;
import java.util.List;

public interface ATService {

  Collection<Long> getAllATIds();

  List<Long> getATsIssuedBy(Long accountId);

  AT getAT(Long atId);
  
  AT getAT(Long atId, int height);
}

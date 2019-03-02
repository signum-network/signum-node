package brs.http;

public enum APITag {

  ACCOUNTS("Accounts"), ALIASES("Aliases"), AE("Asset Exchange"), CREATE_TRANSACTION("Create Transaction"),
  BLOCKS("Blocks"), DGS("Digital Goods Store"), INFO("Server Info"), MESSAGES("Messages"),
  MINING("Mining"), TRANSACTIONS("Transactions"), TOKENS("Tokens"), VS("Voting System"), AT("Automated Transaction"),
  FEES("Fees"), UTILS("Utils"), DEBUG("Debug"), PEER_INFO("Server Peer Info");

  private final String displayName;

  APITag(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

}

package brs.http;

abstract class AbstractGetUnconfirmedTransactions extends APIServlet.JsonRequestHandler {

  AbstractGetUnconfirmedTransactions(APITag[] apiTags, String... parameters) {
    super(apiTags, parameters);
  }
}

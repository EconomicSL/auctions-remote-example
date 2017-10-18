import java.util.UUID

import org.economicsl.auctions._
import org.economicsl.auctions.messages.{AuctionDataRequest, AuctionDataResponse, MidPointPriceQuoteRequest, PriceQuote}
import org.economicsl.auctions.singleunit.orders.{SingleUnitAskOrder, SingleUnitBidOrder, SingleUnitOrder}
import org.economicsl.auctions.singleunit.participants.SingleUnitAuctionParticipant
import org.economicsl.core.util.Timestamper
import org.economicsl.core.{Currency, Price, Tradable}


class TestSingleUnitAuctionParticipant private (
  val issuer: Issuer,
  val outstandingOrders: Map[Token, (Reference, Order[Tradable])],
  val prices: Map[Tradable, Price],
  val valuations: Map[Tradable, Price])
    extends SingleUnitAuctionParticipant
    with Timestamper {

  def handle[T <: Tradable](auctionDataResponse: AuctionDataResponse[T]): TestSingleUnitAuctionParticipant = {
    auctionDataResponse match {
      case response@AuctionDataResponse(auctionData: PriceQuote[Tradable], _, _, _) =>
        auctionData.value match {
          case Some(price) =>
            withPrices(prices.updated(auctionData.tradable, price))
          case None =>
            this
        }
      case response =>
        this
    }
  }

  def issueOrder[T <: Tradable](protocol: AuctionProtocol[T]): Option[(TestSingleUnitAuctionParticipant, (Token, SingleUnitOrder[T]))] = {
    val price = prices(protocol.tradable)
    val valuation = valuations(protocol.tradable)
    if (price < valuation) {
      val limit = largestMultipleOf(protocol.tickSize, valuation)  // insures that limit price is strictly less than valuation!
      Some((this, (randomToken(), SingleUnitBidOrder(issuer, limit, protocol.tradable))))
    } else if (price > valuation) {
      val limit = smallestMultipleOf(protocol.tickSize, valuation)  // insures that limit price is strictly greater than valuation!
      Some((this, (randomToken(), SingleUnitAskOrder(issuer, limit, protocol.tradable))))
    } else {
      None  // if indifferent then don't trade!
    }
  }

  def requestAuctionData[T <: Tradable](protocol: AuctionProtocol[T]): Option[(SingleUnitAuctionParticipant, (Token, AuctionDataRequest[T]))] = {
    val token = randomToken()
    Some((this, (token, MidPointPriceQuoteRequest(issuer, token, currentTimeMillis()))))
  }

  def withPrices(updated: Map[Tradable, Price]): TestSingleUnitAuctionParticipant = {
    new TestSingleUnitAuctionParticipant(issuer, outstandingOrders, updated, valuations)
  }

  protected def withOutstandingOrders(updated: Map[Token, (Reference, Order[Tradable])]): TestSingleUnitAuctionParticipant = {
    new TestSingleUnitAuctionParticipant(issuer, updated, prices, valuations)
  }

  protected def withValuations(updated: Map[Tradable, Price]): TestSingleUnitAuctionParticipant = {
    new TestSingleUnitAuctionParticipant(issuer, outstandingOrders, prices, updated)
  }

  /** Possible makes sense for this to be a static method for Price object? */
  private[this] def largestMultipleOf(tickSize: Currency, lessThan: Price): Price = {
    Price((lessThan.value / tickSize) * tickSize)
  }

  /** Possible makes sense for this to be a static method for Price object? */
  private[this] def smallestMultipleOf(tickSize: Currency, greaterThan: Price): Price = {
    Price((math.ceil(greaterThan.value.toDouble / tickSize) * tickSize).toLong)  // todo concerns about overflow!
  }

}


object TestSingleUnitAuctionParticipant {

  def apply(issuer: Issuer, prices: Map[Tradable, Price], valuations: Map[Tradable, Price]): TestSingleUnitAuctionParticipant = {
    val outstandingOrders = Map.empty[Token, (Reference, Order[Tradable])]
    new TestSingleUnitAuctionParticipant(issuer, outstandingOrders, prices, valuations)
  }

  def apply(prices: Map[Tradable, Price], valuations: Map[Tradable, Price]): TestSingleUnitAuctionParticipant = {
    val issuer = UUID.randomUUID()
    val outstandingOrders = Map.empty[Token, (Reference, Order[Tradable])]
    new TestSingleUnitAuctionParticipant(issuer, outstandingOrders, prices, valuations)
  }

  def apply(valuations: Map[Tradable, Price]): TestSingleUnitAuctionParticipant = {
    val issuer = UUID.randomUUID()
    val outstandingOrders = Map.empty[Token, (Reference, Order[Tradable])]
    val prices = Map.empty[Tradable, Price]
    new TestSingleUnitAuctionParticipant(issuer, outstandingOrders, prices, valuations)
  }

}

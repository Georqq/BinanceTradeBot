import com.binance.client.SyncRequestClient;
import com.binance.client.exception.BinanceApiException;
import com.binance.client.model.enums.*;
import com.binance.client.model.trade.AccountBalance;
import com.binance.client.model.trade.Order;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BinanceTradeBot {
    private SyncRequestClient syncRequestClient;
    final String pricePatternStr = "Price: (\\d+\\.\\d+)";
    final String SLPatternStr = "⛔️ SL: ≈(\\d+\\.\\d+)";
    final String TP1PatternStr = "\uD83C\uDFAF TP №1: ≈(\\d+\\.\\d+)";
    final String currencyPatternStr = "Currency: (\\S*) ";
    final String typePatternStr = "#FUTURE \uD83D\uDD25\uD83D\uDD25\uD83D\uDD25 [(\uD83D\uDFE2)(\uD83D\uDD34)] #(\\S*) [(\uD83D\uDFE2)(\uD83D\uDD34)] The signal only for futures trading.";
    final String isAveragingPatternStr = "AVERAGING THE ENTRY POINT";
    private double maxOrderPrice = .4; // in USDT
    private TgBot listener;
    private String k1;
    private String k2;
    private final int leverage = 20;
    private Map<String, Double> steps;
    private Map<String, List<Order>> orders;

    public BinanceTradeBot() {
        //RequestOptions options = new RequestOptions();
        init();
    }

    public BinanceTradeBot(TgBot listener) {
        //RequestOptions options = new RequestOptions();
        init();
        this.listener = listener;
    }

    public void findMaxOrderPrice(double depositShare) {
        if (depositShare > 0. && depositShare < 1.) {
            // TODO
            List<AccountBalance> balance = syncRequestClient.getBalance();
        }
    }

    public void setMaxOrderPrice(double price) {
        if (price >= 0.1 && price <= 20.0) {
            maxOrderPrice = price;
            println("Max order price was set to " + maxOrderPrice);
        } else {
            println("Error: price " + price + " is too big");
        }
    }

    public double getMaxOrderPrice() {
        return maxOrderPrice;
    }

    private void init() {
        loadBotDataFromFile();
        syncRequestClient = SyncRequestClient.create(k1, k2);
        k1 = null;
        k2 = null;
        Parcer parcer = new Parcer(".\\data\\exchangeInfo.txt");
        this.steps = parcer.getStepsMap();
        orders = new HashMap<>();
    }

    public void loadBotDataFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(".\\data\\keys2.txt"))) {
            k1 = br.readLine();
            k2 = br.readLine();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String text) {
        placeOrderByMessage(text);
    }

    private void getOpenOrders() {
        //syncRequestClient.getOpenOrders();
    }

    private Order placeOrder(String currency, OrderSide orderSide, PositionSide positionSide, OrderType orderType, double quantity, double price) throws BinanceApiException {
        String quantityStr = String.valueOf(quantity);
        String priceStr = null;
        String stopPriceStr = null;
        if (orderType.equals(OrderType.STOP_MARKET) || orderType.equals(OrderType.TAKE_PROFIT_MARKET)) {
            stopPriceStr = String.valueOf(price);
        } else {
            priceStr = String.valueOf(price);
        }
        syncRequestClient.changeInitialLeverage(currency, leverage);
        Order order = syncRequestClient.postOrder(
                currency,
                orderSide,
                positionSide,
                orderType,
                TimeInForce.GTC,
                quantityStr,
                priceStr,
                null,
                null,
                stopPriceStr,
                null,
                NewOrderRespType.RESULT
        );
        println(order.toString());
        if (orders.get(currency) == null) {
            List<Order> ordersList = new ArrayList<>();
            ordersList.add(order);
            orders.put(currency, ordersList);
        } else {
            orders.get(currency).add(order);
        }
        /*
        if (listener != null) {
            listener.sendMsg(order);
        }
         */
        return order;
    }

    private void placeOrderByMessage(String msg) {
        String currency = getStr(msg, currencyPatternStr);
        PositionSide positionSide = PositionSide.valueOf(getStr(msg, typePatternStr));
        OrderSide orderSide = positionSide.equals(PositionSide.LONG) ? OrderSide.BUY : OrderSide.SELL;
        double price = getDouble(msg, pricePatternStr);
        int priceScale = BigDecimal.valueOf(price).scale();
        double SLPrice = BigDecimal.valueOf(getDouble(msg, SLPatternStr)).setScale(priceScale, RoundingMode.CEILING).doubleValue();
        double TP1Price = BigDecimal.valueOf(getDouble(msg, TP1PatternStr)).setScale(priceScale, RoundingMode.CEILING).doubleValue();
        boolean isAvg = (msg.contains(isAveragingPatternStr));
        double quantity = maxOrderPrice / price * leverage;
        double roundStep;
        try {
            roundStep = steps.get(currency);
        } catch (NullPointerException e) {
            roundStep = steps.get(currency.replace("1000", ""));
        }
        int scale = BigDecimal.valueOf(roundStep).scale();
        double roundedQuantity = round(quantity, scale);
        println(
                "Scale: " + scale +
                        "\n\tCurrency: " + currency +
                        "\n\tOrderSide: " + orderSide +
                        "\n\tPositionSide: " + positionSide +
                        "\n\tPrice: " + price +
                        "\n\tQuantity: " + quantity + ", rounded: " + roundedQuantity +
                        "\n\tSL: " + SLPrice +
                        "\n\tTP1: " + TP1Price +
                        "\n\tAveraging: " + (isAvg ? "Yes" : "No")
        );
        if (price > 0.001) {
            Order order = null, TP, SL;
            if (!isAvg) {
                // closing prev orders
                //List<Order> ordersList = orders.get(currency);
                // orders.remove(currency);
                List<Order> ordersList = syncRequestClient.getOpenOrders(currency);
                for (Order o : ordersList) {
                    syncRequestClient.cancelOrder(o.getSymbol(), o.getOrderId(), o.getClientOrderId());
                    println("Closed order: " + o.getSymbol() + " " + o.getPositionSide() + " " + o.getOrderId());
                }
                // main order
                for (int i = 0; i <= scale; i++) {
                    try {
                        println("Trying to place main order with q = " + roundedQuantity);
                        if (price * roundedQuantity <= maxOrderPrice * leverage * 1.20) {
                            order = placeOrder(currency, orderSide, positionSide, OrderType.LIMIT, roundedQuantity, price);
                            println(currency + ":" + scale);
                            break;
                        } else {
                            println("Too high order price (> 120%), max: " + maxOrderPrice * leverage * 1.20 + ", actual: " + price * roundedQuantity);
                            return;
                        }
                    } catch (BinanceApiException e) {
                        if (e.getMessage().contains("-1111")) {
                            println("Small step, increasing...");
                            int localScale = scale - 1 - i;
                            roundedQuantity = round(roundedQuantity, localScale);
                        }
                    }
                }
                println(currency + " " + roundStep);
                // TP/SL
                OrderSide stopOrderSide = orderSide.equals(OrderSide.BUY) ? OrderSide.SELL : OrderSide.BUY;
                //PositionSide stopOrderPositionSide = positionSide.equals(PositionSide.LONG) ? PositionSide.SHORT : PositionSide.LONG;
                TP = placeOrder(currency, stopOrderSide, positionSide, OrderType.TAKE_PROFIT_MARKET, roundedQuantity, TP1Price);
                SL = placeOrder(currency, stopOrderSide, positionSide, OrderType.STOP_MARKET, roundedQuantity, SLPrice);
                if (order != null) {
                    String pos = "NEW POS:\nORDER\n" + order + "\nTP:\n" + TP + "\nSL:\n" + SL;
                    if (listener != null) {
                        listener.sendMsg(pos);
                    }
                }
            }
        }
    }

    public String getStr(String message, String patternStr) {
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public double getDouble(String message, String patternStr) {
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return -1.;
    }

    public static void println(String text) {
        System.out.println(
                '[' + ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM))
                        + "] " + text
        );
    }

    public static double round(double input, int scale) {
        return BigDecimal.valueOf(input).setScale(scale, RoundingMode.CEILING).doubleValue();
    }
}
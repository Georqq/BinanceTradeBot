import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class TgBot extends TelegramLongPollingBot {
    private static String BOT_NAME;
    private static String BOT_TOKEN;

    private String chatID;
    private final BinanceTradeBot binanceTradeBot;

    public static void main(String[] args)  {
        try {
            // Create the TelegramBotsApi object to register your bots
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Register your newly created AbilityBot
            botsApi.registerBot(new TgBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public TgBot() {
        loadBotDataFromFile();
        binanceTradeBot = new BinanceTradeBot(this);
    }

    public void print(String text) {
        sendMessage(chatID, text);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            chatID = String.valueOf(update.getMessage().getChatId());
            if (text.contains("The signal only for futures trading") && text.contains("Price:") && text.contains("SL: ≈") && text.contains("TP №1: ≈")) {
                binanceTradeBot.sendMessage(text);
            } else if (text.contains("/getmop") || text.contains("/get[mM]ax[oO]rder[pP]rice")) {
                sendMessage(chatID, "Max order price: " + binanceTradeBot.getMaxOrderPrice());
            } else if (text.matches("/setmop \\d+\\.\\d+") || text.matches("/set[mM]ax[oO]rder[pP]rice \\d+\\.\\d+")) {
                double price = Double.parseDouble(text.split(" ")[1]);
                binanceTradeBot.setMaxOrderPrice(price);
                sendMessage(chatID, "Max order price: " + binanceTradeBot.getMaxOrderPrice());
            } else {
                sendMessage(chatID, text);
            }
        }
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.enableHtml(true);
        message.disableWebPagePreview();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        super.onUpdatesReceived(updates);
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    public void loadBotDataFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(".\\data\\bot.txt"))) {
            BOT_NAME = br.readLine();
            BOT_TOKEN = br.readLine();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            Map<String, String> getenv = System.getenv();
            BOT_NAME = getenv.get("BOT_NAME");
            BOT_TOKEN = getenv.get("BOT_TOKEN");
        }
    }

    public void sendMsg(String text) {
        sendMessage(chatID, text);
    }
}

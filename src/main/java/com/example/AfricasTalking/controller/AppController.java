package com.example.AfricasTalking.controller;

import com.africastalking.*;
import com.africastalking.voice.action.*;
import com.africastalking.voice.action.Record;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AppController {

//    @Autowired
//    private JdbcTemplate jdbcTemplate;

//    private static final int HTTP_PORT = 8080;
    @Value("${africastalking.username}")
    private  String USERNAME;

    @Value("${africastalking.apiKey}")
    private  String API_KEY;

    private static void log(String message) {
        message+="Console Message : ";
        System.out.println(message);
    }

    @PostConstruct
    private  void setupAfricastalking() throws IOException {
        AfricasTalking.initialize(USERNAME, API_KEY);
        AfricasTalking.setLogger(new Logger(){
            @Override
            public void log(String message, Object... args) {
                System.out.println(message);
            }
        });
        sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS);
        airtime = AfricasTalking.getService(AirtimeService.class);

    }


    private static Gson gson = new Gson();

    private static SmsService sms;
    private static AirtimeService airtime;


    private static final String baseUrl = " https://8f4b-41-72-192-18.ngrok-free.app";
    private static final String songUrl = "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/49/National_Anthem_of_Kenya.ogg/National_Anthem_of_Kenya.ogg.mp3";

    private static final HashMap<String, String> states = new HashMap<>();


    @PostMapping("/auth/register/{phone}")
    public String sendSms(@PathVariable String phone) throws IOException {
        return gson.toJson(sms.send("Welcome to Awesome Company", "3251", new String[]{phone}, false));
    }

    @PostMapping("/airtime/{phone}")
    public String sendAirtime(@PathVariable String phone, @RequestParam String currencyCode, @RequestParam Float amount) throws IOException {
        return gson.toJson(airtime.send(phone, currencyCode, amount));
    }

    @PostMapping("/voice")
    public String handleVoiceRequest(@RequestBody String requestBody) throws IOException {
        // Parse POST data
        String[] raw = URLDecoder.decode(requestBody).split("&");
        Map<String, String> data = new HashMap<>();
        for (String item : raw) {
            String[] kw = item.split("=");
            if (kw.length == 2) {
                data.put(kw[0], kw[1]);
            }
        }

        // Prep state
        boolean isActive = "1".equals(data.get("isActive"));
        String sessionId = data.get("sessionId");
        String callerNumber = data.get("callerNumber");
        String dtmf = data.get("dtmfDigits");
        String state = isActive ? states.getOrDefault(sessionId, "menu") : "";

        ActionBuilder response = new ActionBuilder();

        switch (state) {
            case "menu":
                states.put(sessionId, "process");
                response
                        .say(new Say("Hello bot " + data.getOrDefault("callerNumber", "There")))
                        .getDigits(new GetDigits(new Say("Press 1 to listen to some song. Press 2 to tell me your name. Press 3 to talk to a human. Press 4 or hang up to quit"), 1, "#", null));
                break;
            case "process":
                switch (dtmf) {
                    case "1":
                        states.put(sessionId, "menu");
                        response
                                .play(new Play(new URL(songUrl)))
                                .redirect(new Redirect(new URL(baseUrl + "/voice")));
                        break;
                    case "2":
                        states.put(sessionId, "name");
                        response.record(new Record(new Say("Please say your full name after the beep"), "#", 30, 0, true, true, null));
                        break;
                    case "3":
                        states.remove(sessionId);
                        response
                                .say(new Say("We are getting our resident human on the line for you, please wait while enjoying this nice tune. You have 30 seconds to enjoy a nice conversation with them"))
                                .dial(new Dial(Arrays.asList("+254718769882"), false, false, null, new URL(songUrl), 30));
                        break;
                    case "4":
                        states.remove(sessionId);
                        response.say(new Say("Bye Bye, Long Live Our Machine Overlords")).reject(new Reject());
                        break;
                    default:
                        states.put(sessionId, "menu");
                        response
                                .say(new Say("Invalid choice, try again and you will be exterminated!"))
                                .redirect(new Redirect(new URL(baseUrl + "/voice")));
                        break;
                }
                break;
            case "name":
                states.put(sessionId, "menu");
                response
                        .say(new Say("Your human name is"))
                        .play(new Play(new URL(data.get("recordingUrl"))))
                        .say(new Say("Now forget is, you new name is bot " + callerNumber))
                        .redirect(new Redirect(new URL(baseUrl + "/voice")));
                break;
            default:
                response.say(new Say("Well, this is unexpected! Bye Bye, Long Live Our Machine Overlords")).reject(new Reject());
                break;
        }

        return response.build();
    }
}

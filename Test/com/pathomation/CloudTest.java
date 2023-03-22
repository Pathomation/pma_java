package com.pathomation;

import org.json.JSONObject;
import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static java.lang.System.out;
import static org.junit.Assert.assertEquals;

public class CloudTest {
    String emailForCloud = "email";
    String password = "password";
    CloudServerData cloudServerData = Core.connectToCloud(emailForCloud, password);

    String serverUrl = "https://my-frankfurt2.pathomation.com";

    @Test
    public void connectCloud() {
        String exp = cloudServerData.getSessionId();
        CloudServerData data = Core.connectToCloud("vitalij@pathomation.com", "Azertyuiop1*");
        String res = data.getSessionId();
        assertEquals(exp, res);
    }

    @Test
    public void pingCloud() {
        System.out.println(Core.ping(String.valueOf(cloudServerData)));
    }

    @Test
    public void getPmaSessionsCloud() {
        Object sessionUrl = Core.getPmaSessions().values();
        assertEquals(sessionUrl.toString(), "[" + serverUrl + "]", Core.getPmaSessions().values().toString());
        System.out.println("***getPmaSessions()***");
        System.out.println(Core.getPmaSessions().keySet());
        System.out.println(Core.getPmaSessions().values());
        System.out.println(sessionUrl);
        System.out.println();
    }

    @Test
    public void getPmaUsernamesCloud() {
        String userName = String.valueOf(Core.getPmaUsernames().values());
        assertEquals(userName, "[" + emailForCloud + "]", String.valueOf(Core.getPmaUsernames().values()));
        System.out.println("***getPmaUsernames()***");
        System.out.println(Core.getPmaUsernames());
        System.out.println(userName);
        System.out.println();
    }

    @Test
    public void connectToMyPathomation() throws IOException {
        URL url = new URL ("https://my.pathomation.com/oauth/token");
        HttpURLConnection con = (HttpURLConnection)url.openConnection();


        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        String jsonInputString = "{\n" +
                "  \"client_id\": 0,\n" +
                "  \"client_secret\": \"string\",\n" +
                "  \"grant_type\": \"string\",\n" +
                "  \"scope\": \"string\",\n" +
                "  \"username\": \"vitalij@pathomation.com\",\n" +
                "  \"password\": \"Azertyuiop1*\"\n" +
                "}";
        System.out.println(jsonInputString);
        out.println(con.getOutputStream());
        try(OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }
    }

    @Test
    public void connectToCloudServerData() {
        CloudServerData cloudServerData = Core.connectToCloud(emailForCloud, password);
        out.println(cloudServerData);
    }
    @Test
    public void getTokenForMyPathomation() throws IOException {
        String url = "https://my.pathomation.com/oauth/token";
        URL urlResource = null;
        JSONObject jsonResponse = null;
        try {
            urlResource = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection con;
        if (url.startsWith("https")) {
            con = (HttpsURLConnection) urlResource.openConnection();
        } else {
            con = (HttpURLConnection) urlResource.openConnection();
        }
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setDoInput(true);

        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        con.setRequestProperty("Accept", "application/json");
        OutputStream os = con.getOutputStream();
        out.println(os);
//        String jsonInputString = "{\n" +
//                "  \"client_id\": 0,\n" +
//                "  \"client_secret\": \"string\",\n" +
//                "  \"grant_type\": \"string\",\n" +
//                "  \"scope\": \"string\",\n" +
//                "  \"username\": \"vitalij@pathomation.com\",\n" +
//                "  \"password\": \"Azertyuiop1*\"\n" +
//                "}";
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("client_id", "0");
        jsonObject.put("client_secret", "string");
        jsonObject.put("grant_type", "string");
        jsonObject.put("scope", "string");
        jsonObject.put("username", "vitalij@pathomation.com");
        jsonObject.put("password", "Azertyuiop1*");




        // we set the json string as the request body

//        os.write(jsonObject.toString().getBytes("UTF-8"));
//        byte[] input = jsonInputString.getBytes("utf-8");
//        os.write(input, 0, input.length);
        os.flush();
        os.close();
        System.out.println();

        int responseCode = con.getResponseCode();
        System.out.println("POST Response Code :: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            // print result
            jsonResponse = new JSONObject(response.toString());
            out.println(jsonResponse);
        }
    }

    @Test
    public void getToken() throws IOException {
        String url = "https://stage.api.mindpeak.ai/v1/login";
        URL urlResource = null;
        JSONObject jsonResponse = null;
        try {
            urlResource = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection con;
        if (url.startsWith("https")) {
            con = (HttpsURLConnection) urlResource.openConnection();
        } else {
            con = (HttpURLConnection) urlResource.openConnection();
        }
        con.setRequestMethod("POST");

        con.setRequestProperty("X-Api-Key", "i3NA7IM0xH2i6iXj90GCD51j7YaQhzfB5tqurM2G");
        con.setUseCaches(false);
        con.setDoOutput(true);

        // we set the json string as the request body
        OutputStream os = con.getOutputStream();
        os.write("username=pathomation_stage&password=2hFzMQqCvhCZ$jpawTn!!DnKj!#R%K".getBytes());
        os.flush();
        os.close();
        System.out.println();

        int responseCode = con.getResponseCode();
        System.out.println("POST Response Code :: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            // print result
            jsonResponse = new JSONObject(response.toString());
            out.println();
            out.println(jsonResponse.getString("id_token") + "   ai_token");
        }
    }
}

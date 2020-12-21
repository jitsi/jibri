package org.jitsi.jibri;

import java.util.Random;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.lang.StringBuilder;

public class LinodeApi {

    private Logger logger = Logger.getLogger(LinodeApi.class.getName());
    // private String instanceId;

    private String getRandPassword(int n) {
        String characterSet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random(System.nanoTime());
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int rIndex = random.nextInt(characterSet.length());
            password.append(characterSet.charAt(rIndex));
        }
        return password.toString();
    }

    public void createNode(String personalAccessToken) {
        String rootPass = getRandPassword(100);
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String payload = "{'type':'g6-standard-4','region':'us-east','backups_enabled':'false','root_pass':'" + rootPass
                + "'}"; // TODO image
        RequestBody body = RequestBody.create(JSON, payload);
        Request request = new Request.Builder().url("https://api.linode.com/v4/linode/instances")
                .header("Authorization", "Bearer " + personalAccessToken).post(body).build();
        try {
            Response response = client.newCall(request).execute();
            logger.info(response.body().string());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
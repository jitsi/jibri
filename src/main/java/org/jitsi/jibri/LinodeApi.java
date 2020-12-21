package org.jitsi.jibri;

import java.util.Random;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

import java.lang.StringBuilder;

public class LinodeApi {

    private Logger logger = Logger.getLogger(LinodeApi.class.getName());

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

    public int createNode(String personalAccessToken) {
        logger.info("your Linode personal access token: " + personalAccessToken);
        String rootPass = getRandPassword(100);
        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        String payload = "{\"type\":\"g6-standard-4\",\"region\":\"us-east\",\"backups_enabled\":false,\"root_pass\":\"" + rootPass
                + "\",\"booted\":true,\"image\":\"linode/debian9\"}"; // TODO image
        RequestBody body = RequestBody.create(JSON, payload);
        Request request = new Request.Builder().url("https://api.linode.com/v4/linode/instances")
                .header("Authorization", "Bearer " + personalAccessToken).post(body).build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            logger.info(json.toString());
            return json.getInt("id");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void deleteNode(String personalAccessToken, int linodeId) {
        if(linodeId == -1){
            logger.info("linodeId is -1, no linode to delete.");
            return;
        }
        logger.info("your Linode personal access token: " + personalAccessToken);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("https://api.linode.com/v4/linode/instances/" + linodeId)
                .header("Authorization", "Bearer " + personalAccessToken).delete().build();
        try {
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            logger.info(json.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

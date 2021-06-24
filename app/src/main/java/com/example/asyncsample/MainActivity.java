package com.example.asyncsample;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ログ接頭辞
    private static final String DEBUG_TAG = "Async_Sample";

    // お天気情報URL
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja";

    // お天気情報APIキー
    private static final String API_KEY = "2bd8bf7b1741cd6677c041b61783bf8c";

    // 都市リスト
    private List<Map<String, String>> list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.list = createList();

        ListView listView = findViewById(R.id.lvCityList);
        String[] from = {"name"};
        int[] to = {android.R.id.text1};
        SimpleAdapter simpleAdapter = new SimpleAdapter(MainActivity.this, this.list, android.R.layout.simple_list_item_1, from, to);
        listView.setAdapter(simpleAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Map<String, String> item = list.get(position);
                String q = item.get("q");
                String url = WEATHER_URL + "&q=" + q + "&appid=" + API_KEY;

                receiveWeatherInfo(url);
            }
        });
    }

    private List<Map<String, String>> createList() {

        List<Map<String, String>> list = new ArrayList<>();

        Map<String, String> map = new HashMap<>();
        map.put("name", "大阪");
        map.put("q", "Osaka");
        list.add(map);

        map = new HashMap<>();
        map.put("name", "神戸");
        map.put("q", "Kobe");
        list.add(map);

        return list;
    }

    @UiThread
    private void receiveWeatherInfo(final String url) {

        // UIスレッドに確実に処理を戻すためのLooper生成
        Looper looper = Looper.getMainLooper();

        // スレッド間の通信を行うhandler生成（生成時の引数にLooperを渡す）
        Handler handler = HandlerCompat.createAsync(looper);

        WeatherInfoBackgroundReceiver receiver = new WeatherInfoBackgroundReceiver(handler, url);

        // 別スレッド（ワーカースレッド）で動作するインスタンス作成
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        // 別スレッド実行
        executorService.submit(receiver);
    }

    /**
     * 非同期でお天気情報にアクセスするクラス
     */
    private class WeatherInfoBackgroundReceiver implements Runnable {

        private final Handler handler;  // スレッドセーフのためfinalで宣言
        private final String url;

        WeatherInfoBackgroundReceiver(Handler handler, String url) {
            this.handler = handler;
            this.url = url;
        }

        @WorkerThread
        @Override
        public void run() {

            // HTTP接続を行うオブジェクト
            HttpURLConnection con = null;

            // HTTPレスポンスを取得するストリーム
            InputStream is = null;

            String result = "";
            try {
                // URLオブジェクト生成
                URL url = new URL(this.url);

                // URLオブジェクトからHttpURLConnectionオブジェクトを取得
                con = (HttpURLConnection) url.openConnection();

                // 接続のタイムアウト時間設定
                con.setConnectTimeout(10000);

                // データ取得のタイムアウト時間設定
                con.setReadTimeout(10000);

                // HTTPメソッドの設定
                con.setRequestMethod("GET");

                // 接続実行
                con.connect();

                // HttpURLConnectionオブジェクトからレスポンスを取得
                is = con.getInputStream();

                // レスポンスデータを文字列に変換
                result = is2String(is);

            } catch (MalformedURLException e) {
                Log.e(DEBUG_TAG, "URL変換失敗", e);
            } catch (SocketException e) {
                Log.e(DEBUG_TAG, "通信タイムアウト", e);
            } catch (IOException e) {
                Log.e(DEBUG_TAG, "通信失敗", e);
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "失敗", e);
            } finally {
                // HttpURLConnectionがnullでない場合は解放
                if (con != null) {
                    con.disconnect();
                }

                // InputStreamがnullでない場合は解放
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, "InputStream解放失敗", e);
                    }
                }
            }

            // UIスレッドに戻った後処理クラスをインスタンス化
            WeatherInfoPostExecutor executor = new WeatherInfoPostExecutor(result);

            // 元スレッド（UIスレッド）に処理を戻す
            this.handler.post(executor);
        }
    }

    /**
     * InputStreamをStringに変換
     * @param is
     * @return
     * @throws IOException
     */
    private String is2String(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuffer sb = new StringBuffer();
        char[] c = new char[1024];
        int line;
        while(0 < (line = reader.read())) {
            sb.append(c, 0, line);
        }
        return sb.toString();
    }

    /**
     * 別スレッドでの処理後にUIスレッドで実行する処理クラス
     */
    private class WeatherInfoPostExecutor implements Runnable {

        private final String result;

        WeatherInfoPostExecutor(String result) {
            this.result = result;
        }

        @UiThread
        @Override
        public void run() {

            String city = "";
            String weather = "";
            String latitude = "";
            String longitude = "";

            try {
                // ルートのJSONオブジェクトを取得
                JSONObject json = new JSONObject(this.result);

                // 都市名取得
                city = json.getString("name");

                // 緯度経度オブジェクト取得
                JSONObject coordJSON = json.getJSONObject("coord");

                // 緯度取得
                latitude = coordJSON.getString("lat");

                // 軽度取得
                longitude = coordJSON.getString("lon");

                // 天気情報JSON配列オブジェクト取得
                JSONArray weatherJSONArray = json.getJSONArray("weather");

                // 現在の天気情報JSONオブジェクト取得
                JSONObject weatherJSON = weatherJSONArray.getJSONObject(0);

                // 現在の天気情報取得
                weather = weatherJSON.getString("description");

            } catch (JSONException e) {
                Log.e(DEBUG_TAG, "JSON解析失敗", e);
            }

            String telop = city + "の天気";
            String desc = "現在は" + weather + "です。\n経度は" + latitude + "です。緯度は" +  longitude + "です。";

            TextView tvTelop = findViewById(R.id.tvWeatherTelop);
            tvTelop.setText(telop);

            TextView tvDesc = findViewById(R.id.tvWeatherDesc);
            tvDesc.setText(desc);

        }
    }
}
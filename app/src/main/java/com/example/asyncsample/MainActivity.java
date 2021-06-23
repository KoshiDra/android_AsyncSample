package com.example.asyncsample;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

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

            // UIスレッドに戻った後処理クラスをインスタンス化
            WeatherInfoPostExecutor executor = new WeatherInfoPostExecutor();

            // 元スレッド（UIスレッド）に処理を戻す
            this.handler.post(executor);
        }
    }

    /**
     * 別スレッドでの処理後にUIスレッドで実行する処理クラス
     */
    private class WeatherInfoPostExecutor implements Runnable {

        @UiThread
        @Override
        public void run() {

        }
    }
}
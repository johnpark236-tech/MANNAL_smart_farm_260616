package com.mannal.agri;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener, RecognitionListener {
    private static final String TAG = "MannalAgri";
    private static final String PRICE_URL = "https://nongup.gg.go.kr/data/62";
    private static final String WEATHER_URL = "https://www.weather.go.kr/w/forecast/overall/mid-term.do";
    // TODO: 스마트폰 실기기 테스트 시 PC와 같은 Wi-Fi에 연결하고,
    // ipconfig로 확인한 PC IPv4 주소를 넣는다.
    // 127.0.0.1은 스마트폰 자기 자신이므로 실기기에서는 사용하면 안 된다.
    private static final String BACKEND_BASE_URL = "http://172.30.1.51:8000";
    private static final String BACKEND_WEATHER_URL = BACKEND_BASE_URL + "/api/weather/farm";
    private static final String BACKEND_KAMIS_PRICE_URL = BACKEND_BASE_URL
            + "/api/prices/kamis?category_code=200&country_code=3411&product_cls_code=01&convert_kg_yn=Y";
    private static final String JOURNAL_PREF = "farm_journal";
    private static final String JOURNAL_KEY = "entries";
    private static final String APP_PREF = "app_settings";
    private static final String TEXT_SCALE_KEY = "text_scale";
    private static final String PRICE_CACHE_KEY = "price_cache";
    private static final String WEATHER_CACHE_KEY = "weather_cache";
    private static final String WEATHER_SPEECH_KEY = "weather_speech";
    private static final String[] DEFAULT_ITEMS = {"상추", "고추", "토마토"};
    private static final String[] DEFAULT_REGIONS = {"수원", "천안", "평택"};

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_PRICE = 1;
    private static final int SCREEN_WEATHER = 2;
    private static final int SCREEN_JOURNAL = 3;
    private static final int VOICE_PERMISSION_REQUEST = 42;
    private static final int WAKE_RESTART_DELAY_MS = 250;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.KOREA);

    private float textScale = 1.0f;
    private int currentScreen = SCREEN_PRICE;
    private String currentWeatherRegion = "수원";
    private String lastWeatherSpeech = "";
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady = false;
    private boolean speakAfterWeatherLookup = false;
    private boolean wakeModeEnabled = false;
    private boolean recognitionActive = false;
    private boolean appPaused = false;
    private boolean weatherSpeechActive = false;
    private boolean voiceWeatherCommandRunning = false;
    private boolean pendingWeatherSpeech = false;
    private boolean stoppingSpeechFromVoice = false;
    private boolean journalDictationMode = false;
    private boolean journalSavingInProgress = false;
    private boolean startJournalAfterPermission = false;
    private String lastPriceMode = "real";
    private final StringBuilder journalDictationBuffer = new StringBuilder();
    private Runnable wakeRestartRunnable;

    private LinearLayout root;
    private LinearLayout content;
    private TextView titleText;
    private TextView subtitleText;
    private Button wakeButton;
    private Button priceTab;
    private Button weatherTab;
    private Button journalTab;

    private EditText[] varietyInputs;
    private TextView priceStatus;
    private LinearLayout priceTable;
    private TextView priceReport;

    private EditText[] regionInputs;
    private Button[] regionButtons;
    private TextView weatherStatus;
    private TextView weatherReport;

    private EditText journalDateInput;
    private EditText journalCropInput;
    private EditText journalWorkInput;
    private EditText journalMemoInput;
    private EditText journalSearchInput;
    private TextView journalStatus;
    private LinearLayout journalList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textScale = settings().getFloat(TEXT_SCALE_KEY, 1.0f);
        lastWeatherSpeech = settings().getString(WEATHER_SPEECH_KEY, "");
        textToSpeech = new TextToSpeech(this, this);
        initSpeechRecognizer();
        configureSystemBars();
        buildShell();
        showHomeScreen();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.KOREA);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    weatherSpeechActive = true;
                }

                @Override
                public void onDone(String utteranceId) {
                    weatherSpeechActive = false;
                    stoppingSpeechFromVoice = false;
                    scheduleWakeRestart();
                }

                @Override
                public void onError(String utteranceId) {
                    weatherSpeechActive = false;
                    stoppingSpeechFromVoice = false;
                    scheduleWakeRestart();
                }
            });
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && weatherSpeechActive) {
            stopWeatherSpeech("화면 터치로 읽기를 중지했습니다.");
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        stopWakeListening();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        appPaused = true;
        wakeModeEnabled = false;
        stopWakeListening();
        updateWakeButton();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        appPaused = false;
        updateWakeButton();
    }

    private void buildShell() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), getStatusBarHeight() + dp(14), dp(16), dp(20));
        root.setBackground(gradient(new int[]{0xFFF4FBF4, 0xFFEAF5F1, 0xFFF8FAF4}));
        scrollView.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        hero.setBackground(rounded(0xFFFFFFFF, 8, 0xFFE0E8DF));
        root.addView(hero, marginBottom(dp(12)));

        titleText = new TextView(this);
        titleText.setTextColor(0xFF183B2A);
        titleText.setTypeface(null, Typeface.BOLD);
        scaledText(titleText, 28);
        hero.addView(titleText);

        subtitleText = new TextView(this);
        subtitleText.setTextColor(0xFF5A6B60);
        subtitleText.setLineSpacing(dp(2), 1.0f);
        scaledText(subtitleText, 15);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.setMargins(0, dp(8), 0, 0);
        hero.addView(subtitleText, subtitleParams);

        LinearLayout fontControls = new LinearLayout(this);
        fontControls.setOrientation(LinearLayout.HORIZONTAL);
        fontControls.setGravity(Gravity.CENTER_VERTICAL);
        fontControls.setPadding(dp(12), dp(8), dp(12), dp(8));
        fontControls.setBackground(rounded(0xFFFFFFFF, 8, 0xFFE0E8DF));
        root.addView(fontControls, marginBottom(dp(10)));

        TextView fontLabel = new TextView(this);
        fontLabel.setText("글자 크기");
        fontLabel.setTextColor(0xFF294337);
        fontLabel.setTypeface(null, Typeface.BOLD);
        scaledText(fontLabel, 15);
        fontControls.addView(fontLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button minusButton = smallButton("-");
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeTextScale(-0.1f);
            }
        });
        fontControls.addView(minusButton, new LinearLayout.LayoutParams(dp(48), dp(42)));

        Button plusButton = smallButton("+");
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeTextScale(0.1f);
            }
        });
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(48), dp(42));
        plusParams.setMargins(dp(8), 0, 0, 0);
        fontControls.addView(plusButton, plusParams);

        wakeButton = actionButton("음성 대기 켜기");
        wakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleWakeMode();
            }
        });

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs, marginBottom(dp(12)));

        priceTab = tabButton("시세");
        weatherTab = tabButton("날씨");
        journalTab = tabButton("영농일지");
        tabs.addView(priceTab, tabParams(0));
        tabs.addView(weatherTab, tabParams(dp(8)));
        tabs.addView(journalTab, tabParams(dp(8)));

        priceTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPriceScreen();
            }
        });
        weatherTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWeatherScreen();
            }
        });
        journalTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showJournalScreen();
            }
        });

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        setContentView(scrollView);
    }

    private LinearLayout.LayoutParams tabParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private void configureSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(0xFFF4FBF4);
            window.setNavigationBarColor(0xFFF8FAF4);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private Button tabButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        scaledText(button, 15);
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(rounded(0xFF2F7652, 8, 0xFF2F7652));
        scaledText(button, 20);
        return button;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(0xFF2F7652, 8, 0xFF2F7652));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        scaledText(button, 17);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        params.setMargins(0, dp(6), 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button largeActionButton(String text) {
        Button button = actionButton(text);
        scaledText(button, 22);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
        );
        params.setMargins(0, 0, 0, dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = actionButton(text);
        button.setTextColor(0xFF244536);
        button.setBackground(rounded(0xFFFFFFFF, 8, 0xFFC8D8CC));
        return button;
    }

    private void selectTab(Button selected) {
        styleTab(priceTab, selected == priceTab);
        styleTab(weatherTab, selected == weatherTab);
        styleTab(journalTab, selected == journalTab);
    }

    private void styleTab(Button button, boolean selected) {
        button.setTextColor(selected ? 0xFFFFFFFF : 0xFF294337);
        button.setBackground(rounded(selected ? 0xFF2F7652 : 0xFFFFFFFF, 8, selected ? 0xFF2F7652 : 0xFFD7E3DA));
    }

    private void clearContent(String title, String subtitle, Button selectedTab) {
        titleText.setText(title);
        subtitleText.setText(subtitle);
        content.removeAllViews();
        selectTab(selectedTab);
    }

    private void showHomeScreen() {
        currentScreen = SCREEN_HOME;
        clearContent("만날농사", "원하는 기능을 눌러주세요.", null);
        content.addView(note("날씨, 영농일지, 시세 중 하나를 선택하세요."));
    }

    private void showPriceScreen() {
        currentScreen = SCREEN_PRICE;
        clearContent("만날농사 시세", "관심 품종 3개를 저장해 두면 다음 실행 때 그대로 불러옵니다.", priceTab);

        varietyInputs = new EditText[3];
        LinearLayout varietyRow = new LinearLayout(this);
        varietyRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            varietyInputs[i] = editText("품종" + (i + 1), loadSlot("item_", i, DEFAULT_ITEMS[i]), true, 1);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
            if (i > 0) {
                params.setMargins(dp(6), 0, 0, 0);
            }
            varietyRow.addView(varietyInputs[i], params);
        }
        content.addView(varietyRow, marginBottom(dp(10)));

        Button lookupButton = actionButton("관심 품종 저장 및 시세 조회");
        lookupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveItemSlots();
                lookupPricesAsync();
            }
        });
        content.addView(lookupButton);

        priceStatus = note("저장된 품종은 날씨 조언 문장에도 함께 사용됩니다.");
        content.addView(priceStatus);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        priceTable = new LinearLayout(this);
        priceTable.setOrientation(LinearLayout.VERTICAL);
        priceTable.setPadding(dp(6), dp(6), dp(6), dp(6));
        priceTable.setBackground(rounded(0xFFFFFFFF, 8, 0xFFDCE7DD));
        hScroll.addView(priceTable);
        content.addView(hScroll, marginBottom(dp(10)));

        priceReport = panelText();
        content.addView(priceReport);

        String cached = settings().getString(PRICE_CACHE_KEY, "");
        if (cached.length() > 0) {
            priceReport.setText(cached);
            priceStatus.setText("저장된 최근 시세 요약입니다. 최신 정보는 조회 버튼으로 갱신하세요.");
        }
    }

    private void saveItemSlots() {
        SharedPreferences.Editor editor = settings().edit();
        for (int i = 0; i < 3; i++) {
            editor.putString("item_" + i, varietyInputs[i].getText().toString().trim());
        }
        editor.apply();
    }

    private String selectedItemsText() {
        List<String> items = currentItems();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(items.get(i));
        }
        return builder.toString();
    }

    private List<String> currentItems() {
        List<String> items = new ArrayList<>();
        if (varietyInputs != null) {
            for (int i = 0; i < varietyInputs.length; i++) {
                addUnique(items, varietyInputs[i].getText().toString().trim());
            }
        } else {
            for (int i = 0; i < 3; i++) {
                addUnique(items, loadSlot("item_", i, DEFAULT_ITEMS[i]));
            }
        }
        if (items.isEmpty()) {
            addUnique(items, seasonalCrops());
        }
        return items;
    }

    private String seasonalCrops() {
        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        if (month >= 3 && month <= 5) {
            return "상추, 고추, 토마토";
        }
        if (month >= 6 && month <= 8) {
            return "고추, 오이, 가지";
        }
        if (month >= 9 && month <= 11) {
            return "배추, 무, 상추";
        }
        return "시금치, 딸기, 마늘";
    }

    private void lookupPricesAsync() {
        if (priceStatus == null) {
            return;
        }
        priceStatus.setText("시세를 조회하고 있습니다...");
        final String query = selectedItemsText();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<PriceRow> rows = lookupPrices(query);
                    final String report = buildPriceReport(rows);
                    settings().edit().putString(PRICE_CACHE_KEY, report).apply();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showPriceResult(rows, report);
                        }
                    });
                } catch (final Exception ex) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            priceStatus.setText("조회 실패: " + ex.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void readTodayPricesForFavoriteItems() {
        showPriceScreen();
        if (priceStatus != null) {
            priceStatus.setText("관심 품종 3개의 시세를 조회하고 읽어드립니다...");
        }
        final String query = selectedItemsText();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<PriceRow> rows = lookupBackendKamisPrices(query);
                    final String report = buildPriceReport(rows);
                    final String modeMessage = priceModeMessage(lastPriceMode);
                    final String speech = (modeMessage + buildPriceSpeech(rows)).trim();
                    settings().edit().putString(PRICE_CACHE_KEY, report).apply();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showPriceResult(rows, report);
                            if (priceStatus != null) {
                                priceStatus.setText((modeMessage + "관심 품종 3개 시세를 읽어드립니다.").trim());
                            }
                            speakText(speech, "price-summary");
                        }
                    });
                } catch (final Exception ex) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String detail = ex.getMessage() == null ? "" : ex.getMessage();
                            String message = detail.contains("시세 정보를") ? detail : "서버에 연결하지 못했습니다. 백엔드 서버와 와이파이 연결을 확인해 주세요.";
                            if (priceStatus != null) {
                                priceStatus.setText(message);
                            }
                            speakText("시세 정보를 가져오지 못했습니다.", "price-summary");
                        }
                    });
                }
            }
        });
    }

    private List<PriceRow> lookupBackendKamisPrices(String query) throws Exception {
        List<String> wanted = splitWanted(query);
        JSONObject response = new JSONObject(fetchText(BACKEND_KAMIS_PRICE_URL));
        lastPriceMode = response.optString("mode", "real");
        if (response.optBoolean("error", false) || "error".equals(lastPriceMode)) {
            throw new Exception(response.optString("message", "시세 정보를 가져오지 못했습니다."));
        }
        JSONArray items = response.optJSONArray("items");
        List<PriceRow> rows = new ArrayList<>();
        if (items == null) {
            items = new JSONArray();
        }
        for (int i = 0; i < wanted.size() && i < 3; i++) {
            String wantedItem = wanted.get(i);
            JSONObject match = findKamisItem(items, wantedItem);
            if (match == null) {
                rows.add(new PriceRow(wantedItem, today(), 0, "가격 정보를 찾지 못했습니다."));
                continue;
            }
            int price = parseInt(match.optString("dpr1", ""));
            String date = match.optString("day1", today());
            String rank = match.optString("rank", "");
            String summary = rank.length() == 0 ? "1kg" : "1kg / " + rank;
            rows.add(new PriceRow(wantedItem, date, price, summary));
        }
        return rows;
    }

    private JSONObject findKamisItem(JSONArray items, String wantedItem) throws Exception {
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.optString("item_name", "");
            String kind = item.optString("kind_name", "");
            if (name.contains(wantedItem) || wantedItem.contains(name) || kind.contains(wantedItem)) {
                return item;
            }
        }
        return null;
    }

    private String priceModeMessage(String mode) {
        if ("fallback".equals(mode)) {
            return "공식 API 연결이 불안정하여 공개 시세 기준으로 안내합니다. ";
        }
        if ("cached".equals(mode)) {
            return "현재 연결이 불안정하여 마지막 확인 시세를 알려드립니다. ";
        }
        return "";
    }

    private List<PriceRow> lookupPrices(String query) throws Exception {
        List<String> wanted = splitWanted(query);
        String text = htmlToText(fetchText(PRICE_URL));
        List<PriceRow> rows = parsePriceRows(text, wanted);
        if (rows.isEmpty()) {
            for (int i = 0; i < wanted.size(); i++) {
                rows.add(new PriceRow(wanted.get(i), today(), 0, "원문에서 일치 항목을 찾지 못했습니다."));
            }
        }
        return rows;
    }

    private List<String> splitWanted(String query) {
        String source = query == null || query.trim().length() == 0 ? seasonalCrops() : query;
        String[] parts = source.split("[,\\n/ ]+");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            addUnique(values, parts[i].trim());
        }
        return values;
    }

    private List<PriceRow> parsePriceRows(String text, List<String> wanted) {
        List<PriceRow> rows = new ArrayList<>();
        String[] lines = text.split("\\n");
        Pattern datePattern = Pattern.compile("(20\\d{2}[-./년 ]\\s*\\d{1,2}[-./월 ]\\s*\\d{1,2})");
        Pattern pricePattern = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace('\t', ' ').trim();
            if (line.length() < 4) {
                continue;
            }
            for (int j = 0; j < wanted.size(); j++) {
                String item = wanted.get(j);
                if (!line.contains(item)) {
                    continue;
                }
                Matcher priceMatcher = pricePattern.matcher(line);
                int price = 0;
                while (priceMatcher.find()) {
                    int parsed = parseInt(priceMatcher.group(1));
                    if (parsed > 100) {
                        price = parsed;
                    }
                }
                Matcher dateMatcher = datePattern.matcher(line);
                String date = dateMatcher.find() ? normalizeDate(dateMatcher.group(1)) : today();
                String basis = line.length() > 80 ? line.substring(0, 80) + "..." : line;
                rows.add(new PriceRow(item, date, price, basis));
                break;
            }
            if (rows.size() >= 12) {
                break;
            }
        }
        return rows;
    }

    private void showPriceResult(List<PriceRow> rows, String report) {
        priceTable.removeAllViews();
        addTableRow("품종", "기준일", "가격", "상태", true);
        for (int i = 0; i < rows.size(); i++) {
            PriceRow row = rows.get(i);
            addTableRow(row.item, row.basisDate, row.priceText(), row.summary, false);
        }
        priceReport.setText(report);
        priceStatus.setText(rows.size() + "개 품종 조회 완료");
    }

    private void addTableRow(String item, String date, String price, String status, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(rounded(header ? 0xFFE8F2EA : 0xFFFFFFFF, 8, 0xFFE1E8E1));
        row.addView(cell(item, 110, header));
        row.addView(cell(date, 110, header));
        row.addView(cell(price, 110, header));
        row.addView(cell(status, 260, header));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(4));
        priceTable.addView(row, params);
    }

    private String buildPriceReport(List<PriceRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("조회 시각: ").append(now()).append("\n");
        builder.append("출처: ").append(PRICE_URL).append("\n\n");
        builder.append("확인 요약\n");
        for (int i = 0; i < rows.size(); i++) {
            PriceRow row = rows.get(i);
            builder.append("- ").append(row.item).append(": ").append(row.priceText());
            builder.append(" / ").append(row.basisDate).append("\n");
        }
        builder.append("\n메모\n");
        builder.append("출하 판단 전에는 산지, 단위, 시장 기준을 함께 확인하세요.");
        return builder.toString();
    }

    private String buildPriceSpeech(List<PriceRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("오늘의 시세입니다. ");
        List<String> spokenItems = new ArrayList<>();
        for (int i = 0; i < rows.size() && spokenItems.size() < 3; i++) {
            PriceRow row = rows.get(i);
            if (spokenItems.contains(row.item)) {
                continue;
            }
            spokenItems.add(row.item);
            if (row.price > 0) {
                builder.append(row.item).append("는 킬로그램당 ")
                        .append(numberFormat.format(row.price)).append("원입니다. ");
            } else {
                builder.append(row.item).append(" 가격 정보를 찾지 못했습니다. ");
            }
        }
        if (spokenItems.isEmpty()) {
            builder.append("등록된 관심 품목 가격 정보를 찾지 못했습니다.");
        }
        return builder.toString();
    }

    private void speakText(String text, String utteranceId) {
        if (!ttsReady || textToSpeech == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }


    private void showWeatherScreen() {
        currentScreen = SCREEN_WEATHER;
        clearContent("작업 날씨", "관심 지역 3개를 저장하고, 선택한 지역의 농사 날씨를 짧게 정리합니다.", weatherTab);

        regionButtons = new Button[3];
        LinearLayout regionRow = new LinearLayout(this);
        regionRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            regionButtons[i] = secondaryButton(loadSlot("region_", i, DEFAULT_REGIONS[i]));
            regionButtons[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String region = regionButtons[index].getText().toString().trim();
                    readBackendFarmWeather(region.length() == 0 ? DEFAULT_REGIONS[index] : region);
                }
            });
            attachRegionRenameTouch(regionButtons[i], index);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
            if (i > 0) {
                params.setMargins(dp(6), 0, 0, 0);
            }
            regionRow.addView(regionButtons[i], params);
        }
        content.addView(regionRow, marginBottom(dp(10)));

        Button readButton = actionButton("일기예보 읽어주기");
        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speakWeather();
            }
        });
        content.addView(readButton);

        weatherStatus = note("작업 참고와 짧은 예보 요약만 읽어줍니다.");
        content.addView(weatherStatus);

        weatherReport = panelText();
        content.addView(weatherReport);

        String cached = settings().getString(WEATHER_CACHE_KEY, "");
        if (cached.length() > 0) {
            weatherReport.setText(cached);
            weatherStatus.setText("저장된 최근 날씨 요약입니다. 날씨 화면에서 갱신하세요.");
        }
    }

    private void saveRegionSlots() {
        SharedPreferences.Editor editor = settings().edit();
        for (int i = 0; i < 3; i++) {
            if (regionButtons != null && regionButtons[i] != null) {
                editor.putString("region_" + i, regionButtons[i].getText().toString().trim());
            }
        }
        editor.apply();
    }

    private void attachRegionRenameTouch(final Button button, final int index) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private Runnable longPressRunnable;
            private boolean longPressHandled;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    longPressHandled = false;
                    longPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            longPressHandled = true;
                            showRegionRenameDialog(index);
                        }
                    };
                    mainHandler.postDelayed(longPressRunnable, 3000);
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }
                    if (longPressHandled) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void showRegionRenameDialog(final int index) {
        final EditText input = editText("예: 천안", regionButtons[index].getText().toString(), true, 1);
        new AlertDialog.Builder(this)
                .setTitle("지역명 변경")
                .setView(input)
                .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        String region = input.getText().toString().trim();
                        if (region.length() == 0) {
                            return;
                        }
                        regionButtons[index].setText(region);
                        settings().edit().putString("region_" + index, region).apply();
                        weatherStatus.setText("지역이 변경되었습니다.");
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void lookupWeatherAsync() {
        if (weatherStatus == null) {
            return;
        }
        weatherStatus.setText(currentWeatherRegion + " 날씨를 조회하고 있습니다...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final WeatherSummary summary = buildWeatherReport(currentWeatherRegion);
                    settings().edit()
                            .putString(WEATHER_CACHE_KEY, summary.display)
                            .putString(WEATHER_SPEECH_KEY, summary.speech)
                            .apply();
                    lastWeatherSpeech = summary.speech;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            weatherReport.setText(summary.display);
                            weatherStatus.setText("날씨 조회 완료");
                            if (speakAfterWeatherLookup) {
                                speakAfterWeatherLookup = false;
                                voiceWeatherCommandRunning = false;
                                speakWeather();
                            }
                        }
                    });
                } catch (final Exception ex) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            speakAfterWeatherLookup = false;
                            voiceWeatherCommandRunning = false;
                            weatherStatus.setText("조회 실패: " + ex.getMessage());
                        }
                    });
                }
            }
        });
    }

    private WeatherSummary buildWeatherReport(String region) throws Exception {
        if (region.length() == 0) {
            region = "수원";
        }
        String text = htmlToText(fetchText(WEATHER_URL));
        String original = trimLong(text, 520);
        String crops = selectedItemsText();
        if (crops.length() == 0) {
            crops = seasonalCrops();
        }
        String advice = buildWeatherAdvice(text, crops);
        String weatherLine = buildWeatherLine(text);

        String speech = region + " 농사 날씨 전해드립니다. "
                + weatherLine + " "
                + crops + " 농가는 고온과 습도 변화로 병해가 생기기 쉬우니, 통풍과 배수로 점검 꼭 챙기시기 바랍니다.";

        StringBuilder display = new StringBuilder();
        display.append("작업 참고\n");
        display.append(advice).append("\n\n");
        display.append("예보 원문 요약\n");
        display.append(region).append(" 기준으로 확인한 중기예보입니다.\n");
        display.append(weatherLine).append("\n");
        display.append("관심 품종: ").append(crops).append("\n\n");
        display.append("짧은 원문 단서\n");
        display.append(original).append("\n\n");
        display.append("출처: ").append(WEATHER_URL);
        return new WeatherSummary(display.toString(), speech);
    }

    private String buildWeatherLine(String text) {
        String lower = text.toLowerCase(Locale.KOREA);
        boolean rain = text.contains("비") || lower.contains("rain") || text.contains("강수");
        boolean hot = text.contains("높") || text.contains("고온") || text.contains("더");
        boolean cold = text.contains("낮") || text.contains("저온") || text.contains("춥");
        StringBuilder builder = new StringBuilder();
        if (hot) {
            builder.append("낮 기온이 오르는 시간대에는 한낮 작업을 피하는 편이 좋겠습니다.");
        } else if (cold) {
            builder.append("기온이 낮은 시간대에는 보온과 활착 상태를 먼저 확인하세요.");
        } else {
            builder.append("기온은 큰 특이 신호 없이 변동 가능성이 있습니다.");
        }
        if (rain) {
            builder.append(" 일부 시간대에는 비 가능성이 있어 방제와 수확 일정은 강수 전후로 나누어 보세요.");
        } else {
            builder.append(" 뚜렷한 강수 단서는 적지만, 작업 전 당일 단기예보를 한 번 더 확인하세요.");
        }
        builder.append(" 바람이 강하게 예보되는 시간에는 농약 살포를 미루는 것이 안전합니다.");
        return builder.toString();
    }

    private String buildWeatherAdvice(String text, String crops) {
        String line = buildWeatherLine(text);
        return "- " + line + "\n"
                + "- " + crops + " 농가는 통풍, 배수로, 병해 발생 여부를 먼저 점검하세요.";
    }

    private void speakWeather() {
        String text = lastWeatherSpeech;
        if (text == null || text.trim().length() == 0) {
            text = settings().getString(WEATHER_SPEECH_KEY, "");
        }
        if (text == null || text.trim().length() == 0) {
            weatherStatus.setText("먼저 관심 지역을 조회해 주세요.");
            return;
        }
        if (!ttsReady || textToSpeech == null) {
            pendingWeatherSpeech = true;
            weatherStatus.setText("음성 엔진을 준비하는 중입니다. 잠시 후 읽어드립니다.");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (pendingWeatherSpeech) {
                        pendingWeatherSpeech = false;
                        speakWeather();
                    }
                }
            }, 900);
            return;
        }
        pendingWeatherSpeech = false;
        stoppingSpeechFromVoice = false;
        wakeModeEnabled = false;
        recognitionActive = false;
        updateWakeButton();
        textToSpeech.stop();
        weatherSpeechActive = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "weather-summary");
        } else {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
        weatherStatus.setText("일기예보를 읽고 있습니다. 화면을 터치하면 멈춥니다.");
    }

    private void stopWeatherSpeech(String message) {
        if (!weatherSpeechActive && !stoppingSpeechFromVoice) {
            return;
        }
        stoppingSpeechFromVoice = true;
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        weatherSpeechActive = false;
        announceVoiceStatus(message);
        if (!appPaused) {
            wakeModeEnabled = false;
            updateWakeButton();
        }
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(this);
        }
    }

    private void toggleWakeMode() {
        if (wakeModeEnabled) {
            wakeModeEnabled = false;
            stopWakeListening();
            announceVoiceStatus("호출어 대기를 껐습니다.");
            updateWakeButton();
            return;
        }
        startWakeMode();
    }

    private void startWakeMode() {
        if (speechRecognizer == null) {
            announceVoiceStatus("이 휴대폰에서 음성 인식을 사용할 수 없습니다.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_REQUEST);
            return;
        }
        wakeModeEnabled = true;
        updateWakeButton();
        announceVoiceStatus("음성 대기중입니다.");
        startWakeListening();
    }

    private void autoStartWakeMode() {
        updateWakeButton();
    }

    private void requestVoicePermissionOrStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_REQUEST);
        }
    }

    private void startWakeListening() {
        if ((!wakeModeEnabled && !journalDictationMode) || appPaused || recognitionActive || speechRecognizer == null) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650);
        try {
            recognitionActive = true;
            speechRecognizer.startListening(intent);
        } catch (Exception ex) {
            recognitionActive = false;
            scheduleWakeRestart();
        }
    }

    private void stopWakeListening() {
        if (wakeRestartRunnable != null) {
            mainHandler.removeCallbacks(wakeRestartRunnable);
        }
        recognitionActive = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private void scheduleWakeRestart() {
        if ((!wakeModeEnabled && !journalDictationMode) || appPaused) {
            return;
        }
        if (wakeRestartRunnable != null) {
            mainHandler.removeCallbacks(wakeRestartRunnable);
        }
        wakeRestartRunnable = new Runnable() {
            @Override
            public void run() {
                startWakeListening();
            }
        };
        mainHandler.postDelayed(wakeRestartRunnable, WAKE_RESTART_DELAY_MS);
    }

    private void updateWakeButton() {
        if (wakeButton == null) {
            return;
        }
        if (wakeModeEnabled && !appPaused) {
            wakeButton.setText("음성 대기 끄기");
            wakeButton.setBackground(rounded(0xFF8A5A2B, 8, 0xFF8A5A2B));
        } else {
            wakeButton.setText("음성 대기 켜기");
            wakeButton.setBackground(rounded(0xFF2F7652, 8, 0xFF2F7652));
        }
    }

    private void handleVoiceCommand(String command) {
        String text = command == null ? "" : command.trim().replace(" ", "");
        if (journalDictationMode) {
            Log.d(TAG, "Journal dictation heard: " + command);
            handleJournalDictation(command);
            return;
        }
        if (weatherSpeechActive && shouldStopSpeechForUserTalk(command)) {
            stopWeatherSpeech("읽기를 중지했습니다.");
            return;
        }
        if (isStopCommand(text)) {
            stopWeatherSpeech("읽기를 중지했습니다.");
            return;
        }
        if (isWeatherReadCommand(text)) {
            if (voiceWeatherCommandRunning) {
                return;
            }
            voiceWeatherCommandRunning = true;
            wakeModeEnabled = true;
            stopWakeListening();
            updateWakeButton();
            readFirstRegionWeatherByVoice(command);
            return;
        }
        if (isJournalStartCommand(text)) {
            beginJournalDictation(command);
            return;
        }
        if (text.contains("만날") || text.contains("농사")) {
            announceVoiceStatus("음성 대기중입니다.");
        } else {
            scheduleWakeRestart();
        }
    }

    private boolean isWeatherReadCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replace(" ", "");
        return normalized.contains("날씨") || normalized.contains("일기예보")
                || normalized.contains("예보") || normalized.contains("비와")
                || normalized.contains("비오") || normalized.contains("비올")
                || normalized.contains("강수") || normalized.contains("기온")
                || normalized.contains("바람") || normalized.contains("농사날씨")
                || normalized.contains("작업날씨");
    }

    private boolean isJournalStartCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replace(" ", "");
        return normalized.contains("일지") || normalized.contains("영농일지")
                || normalized.contains("메모") || normalized.contains("메모해") || normalized.contains("기록해")
                || normalized.contains("기록") || normalized.contains("작업기록")
                || normalized.contains("농사기록") || normalized.contains("오늘기록")
                || normalized.contains("작업메모") || normalized.contains("농사메모")
                || normalized.contains("말로기록") || normalized.contains("음성메모");
    }

    private void beginJournalDictation(String spokenText) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startJournalAfterPermission = true;
            showJournalScreen();
            journalStatus.setText("영농일지 메모를 쓰려면 마이크 권한을 허용해 주세요.");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_REQUEST);
            return;
        }
        stopWakeListening();
        journalDictationMode = true;
        journalDictationBuffer.setLength(0);
        wakeModeEnabled = false;
        showJournalScreen();
        journalStatus.setText("일지 입력 중입니다...\n작업 내용을 말씀하세요. 끝나면 저장해줘라고 말하세요.");
        updateWakeButton();
        speakShortGuide("영농일지를 말씀하세요. 끝나면 저장이라고 말하세요.");
        scheduleWakeRestart();
    }

    private void handleJournalDictation(String spokenText) {
        Log.d(TAG, "Journal dictation heard: " + spokenText);
        if (journalSavingInProgress) {
            Log.d(TAG, "Journal save already in progress");
            return;
        }
        if (isJournalSaveCommand(spokenText)) {
            Log.d(TAG, "Journal save command detected");
            journalSavingInProgress = true;
            String extra = removeJournalSaveCommand(spokenText);
            if (extra.length() > 0) {
                appendJournalDictation(extra);
            }
            saveVoiceJournalFromBuffer();
            return;
        }
        String body = cleanJournalDictation(spokenText);
        if (body.length() > 0) {
            appendJournalDictation(body);
            journalStatus.setText("일지 입력 중입니다...\n임시 기록: " + journalDictationBuffer.toString()
                    + "\n끝나면 저장해줘라고 말하세요.");
        }
        scheduleWakeRestart();
    }

    private void appendJournalDictation(String text) {
        if (journalDictationBuffer.length() > 0) {
            journalDictationBuffer.append(' ');
        }
        journalDictationBuffer.append(text.trim());
    }

    private boolean isJournalSaveCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = normalizeSpeechCommand(text);
        return normalized.equals("저장") || normalized.contains("저장해")
                || normalized.contains("저장해주세요")
                || normalized.contains("저장하자") || normalized.contains("저장시켜")
                || normalized.contains("저장완료") || normalized.contains("저장해라")
                || normalized.contains("저장주라") || normalized.contains("저장좀해줘");
    }

    private String normalizeSpeechCommand(String text) {
        if (text == null) {
            return "";
        }
        return text.replace(" ", "")
                .replace(".", "")
                .replace(",", "")
                .trim();
    }

    private String removeJournalSaveCommand(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        cleaned = cleaned.replace("저장해 주세요", "");
        cleaned = cleaned.replace("저장해주세요", "");
        cleaned = cleaned.replace("저장해 주라", "");
        cleaned = cleaned.replace("저장 주라", "");
        cleaned = cleaned.replace("저장 좀 해 줘", "");
        cleaned = cleaned.replace("저장 좀 해줘", "");
        cleaned = cleaned.replace("저장 해줘", "");
        cleaned = cleaned.replace("저장해 줘", "");
        cleaned = cleaned.replace("저장해줘요", "");
        cleaned = cleaned.replace("저장해줘", "");
        cleaned = cleaned.replace("저장하자", "");
        cleaned = cleaned.replace("저장해라", "");
        cleaned = cleaned.replace("저장시켜", "");
        cleaned = cleaned.replace("저장 완료", "");
        cleaned = cleaned.replace("저장완료", "");
        cleaned = cleaned.replace("저장해", "");
        cleaned = cleaned.replace("저장", "");
        return cleaned.trim();
    }

    private void saveVoiceJournalFromBuffer() {
        String body = journalDictationBuffer.toString().trim();
        Log.d(TAG, "Journal buffer before save: " + body);
        if (body.length() == 0) {
            journalStatus.setText("저장할 일지 내용이 없습니다. 다시 말씀해 주세요.");
            speakShortGuide("저장할 일지 내용이 없습니다. 다시 말씀해 주세요.");
            journalSavingInProgress = false;
            scheduleWakeRestart();
            return;
        }
        String title = makeJournalTitle(body);
        journalCropInput.setText(title);
        journalWorkInput.setText(body);
        journalMemoInput.setText("음성 기록: " + now());
        journalDictationMode = false;
        journalDictationBuffer.setLength(0);
        saveJournalEntry();
        journalSavingInProgress = false;
        journalStatus.setText("영농일지를 저장했습니다.\n최근 저장: " + title + " / " + body);
        speakShortGuide("영농일지를 저장했습니다.");
        wakeModeEnabled = false;
        updateWakeButton();
    }

    private String cleanJournalDictation(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        cleaned = cleaned.replaceFirst("^(일지|영농일지|메모|기록해|기록|작업기록)\\s*", "");
        return cleaned.trim();
    }

    private String makeJournalTitle(String body) {
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() == 0) {
            return "음성 기록";
        }
        int max = Math.min(18, compact.length());
        return compact.substring(0, max);
    }

    private void speakShortGuide(String text) {
        if (ttsReady && textToSpeech != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "journal-guide");
            } else {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private boolean isStopCommand(String text) {
        return text.contains("정지") || text.contains("스톱") || text.contains("스탑")
                || text.contains("그만") || text.contains("중지") || text.contains("멈춰")
                || text.contains("오케이") || text.contains("ok") || text.contains("OK");
    }

    private boolean shouldStopSpeechForUserTalk(String rawText) {
        if (rawText == null) {
            return false;
        }
        String trimmed = rawText.trim();
        if (trimmed.length() == 0) {
            return false;
        }
        String compact = trimmed.replace(" ", "");
        if (isStopCommand(compact)) {
            return true;
        }
        return !isWeatherReadCommand(compact);
    }

    private void readFirstRegionWeatherByVoice(String spokenText) {
        showWeatherScreen();
        String region = loadSlot("region_", 0, DEFAULT_REGIONS[0]);
        weatherStatus.setText("'" + spokenText + "' 명령을 확인했습니다. " + region + " 농사 날씨를 조회하고 읽어드립니다.");
        readBackendFarmWeather(region);
    }

    private void readBackendFarmWeather() {
        readBackendFarmWeather(loadSlot("region_", 0, DEFAULT_REGIONS[0]));
    }

    private void readBackendFarmWeather(final String regionName) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String encodedRegion = URLEncoder.encode(regionName, "UTF-8");
                    JSONObject response = new JSONObject(fetchText(BACKEND_WEATHER_URL + "?region_name=" + encodedRegion));
                    final String mode = response.optString("mode", "real");
                    if (response.optBoolean("error", false) || "error".equals(mode)) {
                        throw new Exception(response.optString("message", "날씨 정보를 가져오지 못했습니다."));
                    }
                    final String summary = response.optString("summary", "");
                    final String advice = response.optString("farm_advice", "");
                    final String modeMessage = weatherModeMessage(mode);
                    JSONObject weather = response.optJSONObject("weather");
                    final String display = modeMessage + buildBackendWeatherDisplay(summary, advice, weather);
                    final String speech = (modeMessage + buildWeatherTtsText(summary, advice)).trim();
                    settings().edit()
                            .putString(WEATHER_CACHE_KEY, display)
                            .putString(WEATHER_SPEECH_KEY, speech)
                            .apply();
                    lastWeatherSpeech = speech;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            weatherReport.setText(display);
                            weatherStatus.setText(regionName + " 농사 날씨를 읽어드립니다.");
                            speakText(speech, "weather-summary");
                        }
                    });
                } catch (final Exception ex) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            String detail = ex.getMessage() == null ? "" : ex.getMessage();
                            String message = detail.contains("날씨 정보를") ? detail : "서버에 연결하지 못했습니다. 백엔드 서버와 와이파이 연결을 확인해 주세요.";
                            weatherStatus.setText(message);
                            weatherReport.setText(message + "\n" + ex.getMessage());
                            speakText(message, "weather-summary");
                        }
                    });
                }
            }
        });
    }

    private String weatherModeMessage(String mode) {
        if ("cached".equals(mode)) {
            return "현재 연결이 불안정하여 마지막으로 확인한 정보를 알려드립니다. ";
        }
        if ("fallback".equals(mode)) {
            return "현재 기상청 API 연결이 불안정합니다. 기상청 날씨누리 확인이 필요합니다. ";
        }
        return "";
    }

    private String buildBackendWeatherDisplay(String summary, String advice, JSONObject weather) {
        StringBuilder builder = new StringBuilder();
        builder.append(summary).append("\n\n");
        if (!containsWeatherAdvice(summary, advice)) {
            builder.append("농사 조언\n").append(advice).append("\n\n");
        }
        if (weather != null) {
            builder.append("날씨 정보\n");
            builder.append("기온: ").append(emptyDash(weather.optString("temperature", ""))).append("도\n");
            builder.append("습도: ").append(emptyDash(weather.optString("humidity", ""))).append("%\n");
            builder.append("강수확률: ").append(emptyDash(weather.optString("rain_probability", ""))).append("%\n");
            builder.append("풍속: ").append(emptyDash(weather.optString("wind_speed", ""))).append("m/s\n");
            builder.append("하늘상태: ").append(emptyDash(weather.optString("sky", ""))).append("\n");
            builder.append("강수형태: ").append(emptyDash(weather.optString("precipitation_type", ""))).append("\n\n");
        }
        builder.append("출처: ").append(BACKEND_WEATHER_URL);
        return builder.toString();
    }

    private String buildWeatherTtsText(String summary, String advice) {
        String cleanSummary = summary == null ? "" : summary.trim();
        String cleanAdvice = advice == null ? "" : advice.trim();
        if (cleanAdvice.length() == 0 || containsWeatherAdvice(cleanSummary, cleanAdvice)) {
            return cleanSummary;
        }
        if (cleanSummary.length() == 0) {
            return cleanAdvice;
        }
        return cleanSummary + " " + cleanAdvice;
    }

    private boolean containsWeatherAdvice(String summary, String advice) {
        String cleanSummary = summary == null ? "" : summary.trim();
        String cleanAdvice = advice == null ? "" : advice.trim();
        return cleanAdvice.length() > 0 && cleanSummary.contains(cleanAdvice);
    }

    private void announceVoiceStatus(String message) {
        if (currentScreen == SCREEN_WEATHER && weatherStatus != null) {
            weatherStatus.setText(message);
        } else {
            subtitleText.setText(message);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VOICE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (startJournalAfterPermission) {
                    startJournalAfterPermission = false;
                    beginJournalDictation("메모해줘");
                }
            } else {
                startJournalAfterPermission = false;
                announceVoiceStatus("마이크 권한이 없어 음성 명령을 사용할 수 없습니다.");
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        if (wakeModeEnabled) {
            announceVoiceStatus("음성 대기중입니다.");
        }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        recognitionActive = false;
        scheduleWakeRestart();
    }

    @Override
    public void onError(int error) {
        recognitionActive = false;
        voiceWeatherCommandRunning = false;
        scheduleWakeRestart();
    }

    @Override
    public void onResults(Bundle results) {
        recognitionActive = false;
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String spokenText = matches.get(0);
            Log.d(TAG, "Speech onResults text=" + spokenText + ", journalMode=" + journalDictationMode);
            if (journalDictationMode) {
                handleJournalDictation(spokenText);
                return;
            }
            handleVoiceCommand(spokenText);
        } else {
            scheduleWakeRestart();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return;
        }
        String rawText = matches.get(0);
        String text = normalizeSpeechCommand(rawText);
        Log.d(TAG, "Speech onPartialResults text=" + rawText + ", journalMode=" + journalDictationMode);
        if (journalDictationMode) {
            if (isJournalSaveCommand(rawText)) {
                try {
                    speechRecognizer.cancel();
                } catch (Exception ignored) {
                }
                recognitionActive = false;
                handleJournalDictation(rawText);
            }
            return;
        }
        if (weatherSpeechActive && shouldStopSpeechForUserTalk(rawText)) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
            recognitionActive = false;
            stopWeatherSpeech("읽기를 중지했습니다.");
        } else if (isStopCommand(text)) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
            recognitionActive = false;
            stopWeatherSpeech("읽기를 중지했습니다.");
        } else if (isWeatherReadCommand(text)) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
            recognitionActive = false;
            handleVoiceCommand(matches.get(0));
        } else if (!journalDictationMode && isJournalStartCommand(text)) {
            try {
                speechRecognizer.cancel();
            } catch (Exception ignored) {
            }
            recognitionActive = false;
            handleVoiceCommand(matches.get(0));
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    private void showJournalScreen() {
        currentScreen = SCREEN_JOURNAL;
        clearContent("영농일지", "작업 기록을 휴대폰 안에 저장하고 검색, 전체 백업까지 할 수 있습니다.", journalTab);

        journalDateInput = editText("날짜", today(), true, 1);
        journalCropInput = editText("작목", "", true, 1);
        journalWorkInput = editText("작업 내용", "", false, 3);
        journalMemoInput = editText("메모 / 시세 / 날씨 판단", "", false, 3);
        content.addView(journalDateInput);
        content.addView(journalCropInput);
        content.addView(journalWorkInput);
        content.addView(journalMemoInput);

        Button saveButton = actionButton("일지 저장");
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveJournalEntry();
            }
        });
        content.addView(saveButton);

        Button backupButton = secondaryButton("TXT 백업");
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backupJournalToTxt();
            }
        });
        content.addView(backupButton);

        journalSearchInput = editText("검색어: 작목, 작업, 메모", "", true, 1);
        content.addView(journalSearchInput);

        Button searchButton = secondaryButton("일지 검색");
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                renderJournalList();
            }
        });
        content.addView(searchButton);

        journalStatus = note("저장된 일지는 앱 내부 저장소에 보관됩니다.");
        content.addView(journalStatus);
        journalList = new LinearLayout(this);
        journalList.setOrientation(LinearLayout.VERTICAL);
        content.addView(journalList);
        renderJournalList();
    }

    private void saveJournalEntry() {
        try {
            String crop = journalCropInput.getText().toString().trim();
            String work = journalWorkInput.getText().toString().trim();
            if (crop.length() == 0 && work.length() == 0) {
                journalStatus.setText("작목이나 작업 내용을 입력해 주세요.");
                return;
            }
            JSONArray entries = loadJournalEntries();
            JSONObject entry = new JSONObject();
            entry.put("date", journalDateInput.getText().toString().trim());
            entry.put("crop", crop);
            entry.put("work", work);
            entry.put("memo", journalMemoInput.getText().toString().trim());
            entry.put("savedAt", now());
            entries.put(entry);
            journalPrefs().edit().putString(JOURNAL_KEY, entries.toString()).apply();
            if (journalSearchInput != null) {
                journalSearchInput.setText("");
            }
            journalStatus.setText("일지를 저장했습니다.\n최근 저장: " + crop + " / " + work);
            journalWorkInput.setText("");
            journalMemoInput.setText("");
            renderJournalList();
        } catch (Exception ex) {
            journalStatus.setText("저장 실패: " + ex.getMessage());
        }
    }

    private void renderJournalList() {
        journalList.removeAllViews();
        JSONArray entries = loadJournalEntries();
        String query = journalSearchInput == null ? "" : journalSearchInput.getText().toString().trim().toLowerCase(Locale.KOREA);
        int shown = 0;
        for (int i = entries.length() - 1; i >= 0; i--) {
            try {
                final int index = i;
                final JSONObject entry = entries.getJSONObject(i);
                String haystack = (entry.optString("date") + " " + entry.optString("crop") + " "
                        + entry.optString("work") + " " + entry.optString("memo")).toLowerCase(Locale.KOREA);
                if (query.length() > 0 && !haystack.contains(query)) {
                    continue;
                }
                TextView item = panelText();
                item.setText(entry.optString("date") + " / " + emptyDash(entry.optString("crop")) + "\n"
                        + "작업: " + emptyDash(entry.optString("work")) + "\n"
                        + "메모: " + emptyDash(entry.optString("memo")) + "\n"
                        + "저장: " + entry.optString("savedAt") + "\n\n길게 누르면 삭제합니다.");
                item.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        confirmDeleteEntry(index);
                        return true;
                    }
                });
                journalList.addView(item);
                shown++;
            } catch (Exception ignored) {
            }
        }
        if (shown == 0) {
            journalList.addView(note(entries.length() == 0 ? "저장된 영농일지가 없습니다." : "검색 결과가 없습니다."));
        }
    }

    private void confirmDeleteEntry(final int index) {
        new AlertDialog.Builder(this)
                .setTitle("일지 삭제")
                .setMessage("선택한 일지를 삭제할까요?")
                .setPositiveButton("삭제", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteJournalEntry(index);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void deleteJournalEntry(int index) {
        try {
            JSONArray entries = loadJournalEntries();
            JSONArray next = new JSONArray();
            for (int i = 0; i < entries.length(); i++) {
                if (i != index) {
                    next.put(entries.getJSONObject(i));
                }
            }
            journalPrefs().edit().putString(JOURNAL_KEY, next.toString()).apply();
            journalStatus.setText("일지를 삭제했습니다.");
            renderJournalList();
        } catch (Exception ex) {
            journalStatus.setText("삭제 실패: " + ex.getMessage());
        }
    }

    private void backupJournalToTxt() {
        try {
            JSONArray entries = loadJournalEntries();
            if (entries.length() == 0) {
                journalStatus.setText("백업할 영농일지가 없습니다.");
                return;
            }
            String fileName = "farm_journal_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new Date()) + ".txt";
            String savedPath = saveTextFile(fileName, buildJournalBackupText(entries));
            journalStatus.setText("TXT 백업 완료: " + savedPath);
        } catch (Exception ex) {
            journalStatus.setText("TXT 백업 실패: " + ex.getMessage());
        }
    }

    private String buildJournalBackupText(JSONArray entries) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("만날농사 영농일지 백업\n");
        builder.append("백업일시: ").append(now()).append("\n");
        builder.append("전체 건수: ").append(entries.length()).append("\n");
        builder.append("========================================\n\n");
        for (int i = entries.length() - 1; i >= 0; i--) {
            JSONObject entry = entries.getJSONObject(i);
            builder.append("날짜: ").append(entry.optString("date")).append("\n");
            builder.append("작목: ").append(entry.optString("crop")).append("\n");
            builder.append("작업: ").append(entry.optString("work")).append("\n");
            builder.append("메모: ").append(entry.optString("memo")).append("\n");
            builder.append("저장: ").append(entry.optString("savedAt")).append("\n");
            builder.append("----------------------------------------\n\n");
        }
        return builder.toString();
    }

    private String saveTextFile(String fileName, String body) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new Exception("다운로드 폴더에 파일을 만들 수 없습니다.");
            }
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                throw new Exception("백업 파일을 열 수 없습니다.");
            }
            try {
                outputStream.write(body.getBytes("UTF-8"));
            } finally {
                outputStream.close();
            }
            return "Download/" + fileName;
        }

        File directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            directory = getFilesDir();
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("백업 폴더를 만들 수 없습니다.");
        }
        File file = new File(directory, fileName);
        FileOutputStream outputStream = new FileOutputStream(file);
        try {
            outputStream.write(body.getBytes("UTF-8"));
        } finally {
            outputStream.close();
        }
        return file.getAbsolutePath();
    }

    private EditText editText(String hint, String value, boolean singleLine, int minLines) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setHint(hint);
        editText.setSingleLine(singleLine);
        editText.setMinLines(minLines);
        editText.setTextColor(0xFF183B2A);
        editText.setHintTextColor(0xFF7C8B80);
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));
        editText.setBackground(rounded(0xFFFFFFFF, 8, 0xFFD7E3DA));
        editText.setHorizontallyScrolling(false);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        scaledText(editText, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        editText.setLayoutParams(params);
        return editText;
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFF5A6B60);
        view.setLineSpacing(dp(2), 1.0f);
        scaledText(view, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private TextView panelText() {
        TextView view = new TextView(this);
        view.setTextColor(0xFF183B2A);
        view.setLineSpacing(dp(3), 1.0f);
        view.setPadding(dp(14), dp(13), dp(14), dp(13));
        view.setBackground(rounded(0xFFFFFFFF, 8, 0xFFD7E3DA));
        scaledText(view, 15);
        view.setSingleLine(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private TextView cell(String text, int widthDp, boolean header) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFF183B2A);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), dp(9), dp(8), dp(9));
        view.setSingleLine(false);
        if (header) {
            view.setTypeface(null, Typeface.BOLD);
        }
        scaledText(view, header ? 14 : 13);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private void changeTextScale(float delta) {
        textScale += delta;
        if (textScale < 0.85f) {
            textScale = 0.85f;
        }
        if (textScale > 1.55f) {
            textScale = 1.55f;
        }
        settings().edit().putFloat(TEXT_SCALE_KEY, textScale).apply();
        refreshCurrentScreen();
    }

    private void refreshCurrentScreen() {
        if (currentScreen == SCREEN_PRICE) {
            showPriceScreen();
        } else if (currentScreen == SCREEN_WEATHER) {
            showWeatherScreen();
        } else {
            showJournalScreen();
        }
    }

    private void scaledText(TextView view, float baseSp) {
        view.setTextSize(baseSp * textScale);
        view.setIncludeFontPadding(true);
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int[] colors) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, bottom);
        return params;
    }

    private String fetchText(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        int status = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(), "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        } finally {
            reader.close();
            connection.disconnect();
        }
        if (status >= 400) {
            throw new Exception("HTTP " + status);
        }
        return builder.toString();
    }

    private String htmlToText(String html) {
        String text = html.replaceAll("(?is)<script.*?</script>", " ");
        text = text.replaceAll("(?is)<style.*?</style>", " ");
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        text = text.replaceAll("(?i)</(p|div|tr|li|h[1-6])>", "\n");
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s+", "\n").trim();
    }

    private JSONArray loadJournalEntries() {
        try {
            return new JSONArray(journalPrefs().getString(JOURNAL_KEY, "[]"));
        } catch (Exception ex) {
            return new JSONArray();
        }
    }

    private String loadSlot(String prefix, int index, String fallback) {
        String value = settings().getString(prefix + index, fallback);
        return value == null || value.trim().length() == 0 ? fallback : value;
    }

    private void addUnique(List<String> values, String raw) {
        if (raw == null) {
            return;
        }
        String[] parts = raw.split("[,\\n/ ]+");
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i].trim();
            if (value.length() > 0 && !values.contains(value)) {
                values.add(value);
            }
        }
    }

    private SharedPreferences journalPrefs() {
        return getSharedPreferences(JOURNAL_PREF, MODE_PRIVATE);
    }

    private SharedPreferences settings() {
        return getSharedPreferences(APP_PREF, MODE_PRIVATE);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(new Date());
    }

    private String normalizeDate(String dateText) {
        return dateText.replace("년", "-").replace("월", "-").replace("일", "")
                .replace("/", "-").replace(".", "-").replace(" ", "").replace("--", "-");
    }

    private int parseInt(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 0) {
            return 0;
        }
        return Integer.parseInt(digits);
    }

    private String trimLong(String text, int max) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= max) {
            return clean;
        }
        return clean.substring(0, max) + "...";
    }

    private String emptyDash(String value) {
        return value == null || value.trim().length() == 0 ? "-" : value;
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return dp(24);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class PriceRow {
        final String item;
        final String basisDate;
        final int price;
        final String summary;

        PriceRow(String item, String basisDate, int price, String summary) {
            this.item = item;
            this.basisDate = basisDate;
            this.price = price;
            this.summary = summary;
        }

        String priceText() {
            if (price <= 0) {
                return "확인 필요";
            }
            return numberFormat.format(price) + "원";
        }
    }

    private class WeatherSummary {
        final String display;
        final String speech;

        WeatherSummary(String display, String speech) {
            this.display = display;
            this.speech = speech;
        }
    }
}



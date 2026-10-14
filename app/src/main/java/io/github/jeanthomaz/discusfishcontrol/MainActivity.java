package io.github.jeanthomaz.discusfishcontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

@SuppressLint("SetTextI18n") // UI strings are localized through tr() at runtime.
@SuppressWarnings("deprecation") // Legacy system-bar APIs support Android 6 through 10.
public class MainActivity extends Activity {
    private static final int REQUEST_BLE_PERMISSIONS = 41;
    private static final int REQUEST_ENABLE_BLUETOOTH = 42;
    private static final UUID SERVICE_UUID = UUID.fromString("0000AE00-0000-1000-8000-00805F9B34FB");
    private static final UUID WRITE_UUID = UUID.fromString("0000AE01-0000-1000-8000-00805F9B34FB");
    private static final UUID STATE_UUID = UUID.fromString("0000AE10-0000-1000-8000-00805F9B34FB");
    private static final UUID WEEK_UUID = UUID.fromString("0000AE03-0000-1000-8000-00805F9B34FB");
    private static final UUID ALARM_UUID = UUID.fromString("0000AE11-0000-1000-8000-00805F9B34FB");
    private static final String HANDSHAKE = "xd_ble_flagpass001";
    private static final String[] MODELS = {
            "NOUR Plus freshwater", "NOUR Plus marine", "NOUR freshwater",
            "NOUR marine", "NOUR pure white"
    };
    private static final String PREFS_NAME = "discus-fish-control";
    private static final String PREF_LANGUAGE = "app-language";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothDevice> foundDevices = new LinkedHashMap<>();
    private final Queue<byte[]> writeQueue = new ArrayDeque<>();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic stateCharacteristic;
    private BluetoothGattCharacteristic weekCharacteristic;
    private BluetoothGattCharacteristic alarmCharacteristic;
    private boolean scanning;
    private boolean writeInFlight;
    private boolean handshakeQueued;
    private boolean connectedReady;
    private boolean modeKnown;
    private boolean autoMode = true;
    private boolean lampOn;
    private String modeHeader;
    private Boolean pendingAutoMode;
    private Boolean pendingPower;

    private LinearLayout root;
    private ScrollView scrollContainer;
    private LinearLayout controlsCard;
    private LinearLayout weeklyCard;
    private LinearLayout scheduleCard;
    private LinearLayout programmingPanel;
    private LinearLayout tabsRow;
    private LinearLayout devicesContainer;
    private TextView devicesLabel;
    private TextView scheduleEmptyView;
    private TextView statusView;
    private Button scanButton;
    private Button sendButton;
    private Button modeButton;
    private Button powerButton;
    private Button saveWeekButton;
    private Button clockButton;
    private Button controlTabButton;
    private Button programTabButton;
    private Button languageButton;
    private Button scheduleTimeButton;
    private Button saveScheduleButton;
    private Button addScheduleButton;
    private android.widget.Switch scheduleEnabledSwitch;
    private LinearLayout scheduleEditorPanel;
    private LinearLayout scheduleTimeline;
    private final LinearLayout[] scheduleSlotCards = new LinearLayout[10];
    private final TextView[] scheduleSlotTimeLabels = new TextView[10];
    private final TextView[] scheduleSlotStateLabels = new TextView[10];
    private final MiniLevelsView[] scheduleSlotLevels = new MiniLevelsView[10];
    private TextView scheduleEditTitleView;
    private TextView scheduleConflictView;
    private int scheduleHour = 12;
    private int scheduleMinute;
    private final CheckBox[] weekDays = new CheckBox[7];
    private final SeekBar[] channelBars = new SeekBar[4];
    private final TextView[] channelValues = new TextView[4];
    private final SeekBar[] scheduleBars = new SeekBar[4];
    private final TextView[] scheduleValues = new TextView[4];
    private final SchedulePoint[] schedulePoints = new SchedulePoint[10];
    private LightGraphView lightGraph;
    private int alarmReadsCompleted;
    private int activeScheduleSlot = -1;
    private boolean programmingTabSelected;
    private boolean loadingSchedulePoint;
    private boolean scheduleDataLoaded;
    private String appLanguage = "pt-BR";

    @Override
    protected void attachBaseContext(Context base) {
        String language = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LANGUAGE, "pt-BR");
        Locale locale = language.equals("en") ? Locale.ENGLISH : new Locale("pt", "BR");
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appLanguage = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_LANGUAGE, "pt-BR");
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        buildScreen();
        configureSystemBars();
        setStatus(tr("Escolha o modelo e busque sua luminária OceanTech.", "Choose the lamp model and search for your OceanTech light."));
    }

    private void buildScreen() {
        scrollContainer = new ScrollView(this);
        scrollContainer.setFillViewport(true);
        scrollContainer.setClipToPadding(true);
        scrollContainer.setBackgroundColor(Color.rgb(242, 247, 247));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scrollContainer.addView(root);
        setContentView(scrollContainer);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appIcon = new ImageView(this);
        appIcon.setImageResource(R.mipmap.ic_launcher);
        header.addView(appIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = label(getString(R.string.app_name));
        title.setTextSize(24);
        TextView subtitle = new TextView(this);
        subtitle.setText(tr("Controle da sua luminária", "Control your aquarium light"));
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(91, 111, 115));
        heading.addView(title);
        heading.addView(subtitle, topMargin(2));
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        languageButton = new Button(this);
        languageButton.setText(appLanguage.equals("en") ? "EN" : "PT-BR");
        languageButton.setContentDescription(tr("Idioma do aplicativo", "App language"));
        languageButton.setOnClickListener(v -> chooseLanguage());
        styleButton(languageButton, false);
        header.addView(languageButton, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(header, topMargin(4));

        LinearLayout connectionCard = card();
        root.addView(connectionCard, topMargin(20));
        connectionCard.addView(sectionTitle(tr("Conexão", "Connection")));
        TextView modelLabel = helperText(tr("Modelo da lâmpada", "Lamp model"));
        connectionCard.addView(modelLabel, topMargin(12));
        Spinner modelSpinner = new Spinner(this);
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MODELS);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setSelection(2);
        connectionCard.addView(modelSpinner, topMargin(2));
        connectionCard.addView(helperText(tr("A busca mostra luminárias identificadas como TO-L-XXXX.", "Search finds lights identified as TO-L-XXXX.")), topMargin(2));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.rgb(35, 77, 78));
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(rounded(Color.rgb(232, 244, 241), Color.TRANSPARENT, 12));
        connectionCard.addView(statusView, topMargin(14));

        scanButton = new Button(this);
        scanButton.setText(tr("Buscar luminária", "Search for lamp"));
        styleButton(scanButton, true);
        scanButton.setOnClickListener(v -> startScanFlow());
        connectionCard.addView(scanButton, topMargin(10));

        devicesLabel = helperText(tr("Dispositivos próximos", "Nearby devices"));
        devicesLabel.setVisibility(android.view.View.GONE);
        devicesContainer = new LinearLayout(this);
        devicesContainer.setOrientation(LinearLayout.VERTICAL);
        connectionCard.addView(devicesLabel, topMargin(12));
        connectionCard.addView(devicesContainer, topMargin(2));

        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(0, dp(4), 0, dp(2));
        tabsRow.setVisibility(android.view.View.GONE);
        controlTabButton = new Button(this);
        controlTabButton.setText(tr("Controle", "Control"));
        styleButton(controlTabButton, true);
        controlTabButton.setOnClickListener(v -> showProgrammingTab(false));
        programTabButton = new Button(this);
        programTabButton.setText(tr("Programação", "Schedule"));
        styleButton(programTabButton, false);
        programTabButton.setOnClickListener(v -> showProgrammingTab(true));
        tabsRow.addView(controlTabButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams programTabParams = new LinearLayout.LayoutParams(0, -2, 1);
        programTabParams.leftMargin = dp(8);
        tabsRow.addView(programTabButton, programTabParams);
        root.addView(tabsRow, topMargin(14));

        controlsCard = card();
        controlsCard.setVisibility(android.view.View.GONE);
        root.addView(controlsCard, topMargin(14));
        controlsCard.addView(sectionTitle(tr("Luminária", "Lamp")));
        modeButton = new Button(this);
        modeButton.setText(tr("Aguardando leitura da luminária", "Waiting for lamp status"));
        modeButton.setEnabled(false);
        modeButton.setOnClickListener(v -> toggleMode());
        styleButton(modeButton, false);
        controlsCard.addView(modeButton, topMargin(12));

        powerButton = new Button(this);
        powerButton.setText(tr("Ligar ou desligar", "Turn on or off"));
        powerButton.setEnabled(false);
        powerButton.setOnClickListener(v -> togglePower());
        styleButton(powerButton, false);
        controlsCard.addView(powerButton, topMargin(4));

        controlsCard.addView(sectionTitle(tr("Canais de cor", "Color channels")), topMargin(20));
        TextView channelHint = helperText(tr("Ajuste os níveis e envie no modo manual.", "Adjust the levels and send them in manual mode."));
        controlsCard.addView(channelHint, topMargin(2));
        for (int i = 0; i < 4; i++) addChannelControl(i);

        sendButton = new Button(this);
        sendButton.setText(tr("Aplicar níveis", "Apply levels"));
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> sendChannelLevels());
        styleButton(sendButton, true);
        controlsCard.addView(sendButton, topMargin(8));

        Button disconnectButton = new Button(this);
        disconnectButton.setText(tr("Desconectar", "Disconnect"));
        disconnectButton.setOnClickListener(v -> disconnect());
        styleButton(disconnectButton, false);
        controlsCard.addView(disconnectButton, topMargin(4));

        programmingPanel = new LinearLayout(this);
        programmingPanel.setOrientation(LinearLayout.VERTICAL);
        programmingPanel.setVisibility(android.view.View.GONE);
        root.addView(programmingPanel, topMargin(14));

        weeklyCard = card();
        weeklyCard.addView(sectionTitle(tr("Repetição semanal", "Weekly repeat")));
        weeklyCard.addView(helperText(tr("Escolha em quais dias a rotina se repete.", "Choose which days the routine repeats.")), topMargin(2));
        String[] dayNames = appLanguage.equals("en")
                ? new String[]{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"}
                : new String[]{"Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"};
        for (int i = 0; i < weekDays.length; i++) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(dayNames[i]);
            checkBox.setTextColor(Color.rgb(35, 54, 60));
            checkBox.setEnabled(false);
            checkBox.setButtonTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
            weekDays[i] = checkBox;
            weeklyCard.addView(checkBox, topMargin(0));
        }
        saveWeekButton = new Button(this);
        saveWeekButton.setText(tr("Salvar repetição", "Save weekly repeat"));
        saveWeekButton.setEnabled(false);
        saveWeekButton.setOnClickListener(v -> saveWeekDays());
        styleButton(saveWeekButton, true);
        weeklyCard.addView(saveWeekButton, topMargin(8));

        clockButton = new Button(this);
        clockButton.setText(tr("Sincronizar relógio", "Sync clock"));
        clockButton.setEnabled(false);
        clockButton.setOnClickListener(v -> syncLampClock());
        styleButton(clockButton, false);
        weeklyCard.addView(clockButton, topMargin(4));

        scheduleCard = card();
        programmingPanel.addView(scheduleCard, topMargin(14));
        scheduleCard.addView(sectionTitle(tr("Programação por horário", "Daily light schedule")));
        TextView scheduleHint = new TextView(this);
        scheduleHint.setText(tr("Cada ponto é uma mudança de intensidade. As linhas mostram como as cores variam ao longo do dia.", "Each point changes the light intensity. The lines show how each color changes during the day."));
        scheduleHint.setTextSize(13);
        scheduleHint.setTextColor(Color.rgb(65, 83, 88));
        scheduleCard.addView(scheduleHint, topMargin(3));
        lightGraph = new LightGraphView(this);
        scheduleCard.addView(lightGraph, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));
        scheduleCard.addView(helperText(tr("Toque em um horário para editar. A curva mostra a mudança de intensidade ao longo do dia.", "Tap a time to edit it. The chart shows how light intensity changes during the day.")), topMargin(8));
        LinearLayout timelineHeader = new LinearLayout(this);
        timelineHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView timelineTitle = sectionTitle(tr("Horários do dia", "Daily schedule"));
        timelineHeader.addView(timelineTitle, new LinearLayout.LayoutParams(0, -2, 1));
        addScheduleButton = new Button(this);
        addScheduleButton.setText(tr("+ Adicionar horário", "+ Add time"));
        addScheduleButton.setOnClickListener(v -> addSchedulePoint());
        styleButton(addScheduleButton, true);
        timelineHeader.addView(addScheduleButton, new LinearLayout.LayoutParams(-2, dp(44)));
        scheduleCard.addView(timelineHeader, topMargin(14));
        scheduleEmptyView = helperText(tr("Nenhum horário configurado. Adicione o primeiro ponto para desenhar a rotina de luz.", "No times set. Add the first point to build your light routine."));
        scheduleCard.addView(scheduleEmptyView, topMargin(4));
        scheduleTimeline = new LinearLayout(this);
        scheduleTimeline.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < schedulePoints.length; i++) {
            scheduleSlotCards[i] = createScheduleSlotCard(i);
            scheduleSlotCards[i].setVisibility(android.view.View.GONE);
        }
        scheduleCard.addView(scheduleTimeline);
        scheduleEditTitleView = sectionTitle(tr("Selecione ou adicione um horário", "Select or add a time"));
        scheduleEditorPanel = card();
        scheduleEditorPanel.setBackground(rounded(Color.rgb(247, 250, 250), Color.rgb(232, 239, 239), 14));
        scheduleEditorPanel.setVisibility(android.view.View.GONE);
        scheduleEditorPanel.addView(scheduleEditTitleView);
        scheduleEditorPanel.addView(helperText(tr("Escolha quando esta mudança acontece e ajuste cada canal.", "Choose when this change happens and set each color channel.")), topMargin(2));
        scheduleConflictView = helperText("");
        scheduleConflictView.setTextColor(Color.rgb(166, 57, 43));
        scheduleConflictView.setVisibility(android.view.View.GONE);
        scheduleEditorPanel.addView(scheduleConflictView, topMargin(4));
        scheduleTimeButton = new Button(this);
        scheduleTimeButton.setText(tr("Escolher horário · 12:00", "Choose time · 12:00"));
        scheduleTimeButton.setEnabled(false);
        scheduleTimeButton.setOnClickListener(v -> chooseScheduleTime());
        styleButton(scheduleTimeButton, false);
        scheduleEditorPanel.addView(scheduleTimeButton, topMargin(8));
        scheduleEnabledSwitch = new android.widget.Switch(this);
        scheduleEnabledSwitch.setText(tr("Ativar mudança de luz neste horário", "Enable this light change"));
        scheduleEnabledSwitch.setChecked(true);
        scheduleEnabledSwitch.setEnabled(false);
        scheduleEnabledSwitch.setButtonTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        scheduleEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!loadingSchedulePoint) updateScheduleGraphPreview();
        });
        scheduleEditorPanel.addView(scheduleEnabledSwitch, topMargin(4));
        scheduleEditorPanel.addView(helperText(tr("Intensidade das cores", "Color intensity")), topMargin(8));
        for (int i = 0; i < 4; i++) addScheduleChannelControl(i);
        saveScheduleButton = new Button(this);
        saveScheduleButton.setText(tr("Salvar horário", "Save time"));
        saveScheduleButton.setEnabled(false);
        saveScheduleButton.setOnClickListener(v -> saveSchedule());
        styleButton(saveScheduleButton, true);
        scheduleEditorPanel.addView(saveScheduleButton, topMargin(8));
        scheduleCard.addView(scheduleEditorPanel, topMargin(12));
        programmingPanel.addView(weeklyCard, topMargin(14));

    }

    private void configureSystemBars() {
        Window window = getWindow();
        android.view.View decor = window.getDecorView();
        int safeAreaColor = Color.rgb(242, 247, 247);
        decor.setBackgroundColor(safeAreaColor);
        int lightFlags = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) lightFlags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(lightFlags);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setStatusBarColor(safeAreaColor);
            window.setNavigationBarColor(safeAreaColor);
            window.setDecorFitsSystemWindows(false);
            window.setNavigationBarContrastEnforced(false);
            decor.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                scrollContainer.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
            decor.requestApplyInsets();
        } else {
            window.setStatusBarColor(safeAreaColor);
            window.setNavigationBarColor(safeAreaColor);
        }
    }

    private String tr(String portuguese, String english) {
        return appLanguage.equals("en") ? english : portuguese;
    }

    private String[] channelNames() {
        return appLanguage.equals("en")
                ? new String[]{"White", "Blue", "Green", "Red"}
                : new String[]{"Branco", "Azul", "Verde", "Vermelho"};
    }

    private void chooseLanguage() {
        String[] options = {"English", "Português (Brasil)"};
        int selected = appLanguage.equals("en") ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(tr("Idioma do aplicativo", "App language"))
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    String nextLanguage = which == 0 ? "en" : "pt-BR";
                    dialog.dismiss();
                    if (nextLanguage.equals(appLanguage)) return;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(PREF_LANGUAGE, nextLanguage).apply();
                    recreate();
                })
                .setNegativeButton(tr("Cancelar", "Cancel"), null)
                .show();
    }

    private LinearLayout createScheduleSlotCard(int slot) {
        LinearLayout eventCard = new LinearLayout(this);
        eventCard.setOrientation(LinearLayout.HORIZONTAL);
        eventCard.setGravity(Gravity.CENTER_VERTICAL);
        eventCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        eventCard.setBackground(rounded(Color.WHITE, Color.rgb(225, 234, 235), 14));
        eventCard.setClickable(true);
        eventCard.setFocusable(true);
        eventCard.setOnClickListener(v -> selectSchedulePoint(slot));

        LinearLayout timeColumn = new LinearLayout(this);
        timeColumn.setOrientation(LinearLayout.VERTICAL);
        timeColumn.setGravity(Gravity.CENTER_VERTICAL);
        scheduleSlotTimeLabels[slot] = label("--:--");
        scheduleSlotTimeLabels[slot].setTextSize(18);
        scheduleSlotStateLabels[slot] = helperText(tr("Ativo", "Active"));
        timeColumn.addView(scheduleSlotTimeLabels[slot]);
        timeColumn.addView(scheduleSlotStateLabels[slot], topMargin(1));
        eventCard.addView(timeColumn, new LinearLayout.LayoutParams(dp(96), -2));

        scheduleSlotLevels[slot] = new MiniLevelsView(this);
        LinearLayout.LayoutParams levelsParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        levelsParams.leftMargin = dp(8);
        eventCard.addView(scheduleSlotLevels[slot], levelsParams);
        return eventCard;
    }

    private void refreshScheduleTimeline(boolean reorder) {
        if (scheduleTimeline == null) return;
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < schedulePoints.length; i++) {
            SchedulePoint point = schedulePoints[i];
            LinearLayout card = scheduleSlotCards[i];
            if (point == null) {
                card.setVisibility(android.view.View.GONE);
                continue;
            }
            card.setVisibility(android.view.View.VISIBLE);
            slots.add(i);
            scheduleSlotTimeLabels[i].setText(twoDigits(point.hour) + ":" + twoDigits(point.minute));
            String state = point.enabled ? tr("Ativo", "Active") : tr("Pausado", "Paused");
            if (!point.saved) state += "  ·  " + tr("Não salvo", "Unsaved");
            if (point.enabled && hasScheduleConflict(i)) state += "  ·  " + tr("Revisar", "Review");
            scheduleSlotStateLabels[i].setText(state);
            scheduleSlotLevels[i].setLevels(point.levels);
            boolean selected = i == activeScheduleSlot;
            card.setBackground(rounded(selected ? Color.rgb(229, 247, 245) : Color.WHITE,
                    selected ? Color.rgb(8, 127, 140) : Color.rgb(225, 234, 235), 14));
        }
        Collections.sort(slots, (a, b) -> {
            SchedulePoint first = schedulePoints[a];
            SchedulePoint second = schedulePoints[b];
            return Integer.compare(first.hour * 60 + first.minute, second.hour * 60 + second.minute);
        });
        if (reorder) {
            scheduleTimeline.removeAllViews();
            for (int index = 0; index < slots.size(); index++) {
                int slot = slots.get(index);
                scheduleTimeline.addView(scheduleSlotCards[slot], topMargin(index == 0 ? 6 : 4));
            }
        }
        scheduleEmptyView.setVisibility(slots.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        if (addScheduleButton != null) {
            addScheduleButton.setEnabled(connectedReady && scheduleDataLoaded && slots.size() < schedulePoints.length);
        }
    }

    private void addSchedulePoint() {
        if (!connectedReady || !scheduleDataLoaded) return;
        int slot = -1;
        for (int i = 0; i < schedulePoints.length; i++) if (schedulePoints[i] == null) { slot = i; break; }
        if (slot < 0) {
            Toast.makeText(this, tr("A luminária já tem dez horários configurados.",
                    "The lamp already has ten schedule points."), Toast.LENGTH_LONG).show();
            return;
        }
        int minuteOfDay = nextAvailableScheduleTime();
        int[] levels = {0, 0, 0, 0};
        ArrayList<SchedulePoint> existing = editableSchedulePoints();
        if (!existing.isEmpty()) {
            Collections.sort(existing, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
            levels = existing.get(existing.size() - 1).levels.clone();
        }
        schedulePoints[slot] = new SchedulePoint(slot, minuteOfDay / 60, minuteOfDay % 60,
                true, levels, false);
        selectSchedulePoint(slot);
        setStatus(tr("Novo horário adicionado à prévia. Ajuste e salve para enviá-lo à lâmpada.",
                "New time added to the preview. Adjust it and save to send it to the lamp."));
    }

    private int nextAvailableScheduleTime() {
        int candidate = editableSchedulePoints().isEmpty() ? 8 * 60 : 12 * 60;
        for (int attempt = 0; attempt < 96; attempt++) {
            int hour = (candidate / 60) % 24;
            int minute = candidate % 60;
            if (!hasScheduleConflict(-1, hour, minute)) return candidate;
            candidate = (candidate + 15) % (24 * 60);
        }
        return 12 * 60;
    }

    private ArrayList<SchedulePoint> editableSchedulePoints() {
        ArrayList<SchedulePoint> points = new ArrayList<>();
        for (int i = 0; i < schedulePoints.length; i++) if (schedulePoints[i] != null) points.add(schedulePoints[i]);
        return points;
    }

    private void addChannelControl(int index) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(8), 0, dp(4));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(channelNames()[index]);
        name.setTextSize(15);
        name.setTextColor(Color.rgb(35, 54, 60));
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        channelValues[index] = new TextView(this);
        channelValues[index].setText("0%");
        channelValues[index].setTextSize(14);
        channelValues[index].setTextColor(Color.rgb(35, 54, 60));
        heading.addView(channelValues[index]);
        line.addView(heading);

        channelBars[index] = new SeekBar(this);
        channelBars[index].setMax(100);
        channelBars[index].setProgress(0);
        channelBars[index].setProgressTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        channelBars[index].setThumbTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        channelBars[index].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                channelValues[index].setText(progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        line.addView(channelBars[index]);
        controlsCard.addView(line, topMargin(2));
    }

    private void addScheduleChannelControl(int index) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(3), 0, dp(2));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = helperText(channelNames()[index]);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        scheduleValues[index] = helperText("0%");
        heading.addView(scheduleValues[index]);
        line.addView(heading);

        scheduleBars[index] = new SeekBar(this);
        scheduleBars[index].setMax(100);
        scheduleBars[index].setProgressTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        scheduleBars[index].setThumbTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        scheduleBars[index].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                scheduleValues[index].setText(progress + "%");
                if (fromUser) updateScheduleGraphPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        line.addView(scheduleBars[index]);
        scheduleEditorPanel.addView(line, topMargin(2));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(Color.WHITE, Color.rgb(227, 235, 235), 18));
        return card;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private TextView sectionTitle(String text) {
        TextView view = label(text);
        view.setTextSize(18);
        return view;
    }

    private TextView helperText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(Color.rgb(96, 113, 116));
        return view;
    }

    private void styleButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setBackgroundTintList(ColorStateList.valueOf(primary
                ? Color.rgb(8, 127, 140) : Color.rgb(232, 241, 241)));
        button.setTextColor(primary ? Color.WHITE : Color.rgb(26, 67, 71));
    }

    private void setConnectedContentVisible(boolean visible) {
        if (tabsRow != null) tabsRow.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!visible) {
            if (controlsCard != null) controlsCard.setVisibility(android.view.View.GONE);
            if (programmingPanel != null) programmingPanel.setVisibility(android.view.View.GONE);
        } else showProgrammingTab(programmingTabSelected);
    }

    private void showProgrammingTab(boolean programming) {
        programmingTabSelected = programming;
        if (controlsCard != null) controlsCard.setVisibility(programming ? android.view.View.GONE : android.view.View.VISIBLE);
        if (programmingPanel != null) programmingPanel.setVisibility(programming ? android.view.View.VISIBLE : android.view.View.GONE);
        if (controlTabButton != null) styleButton(controlTabButton, !programming);
        if (programTabButton != null) styleButton(programTabButton, programming);
    }

    private void loadSchedulePoint(int slot) {
        if (scheduleBars[0] == null || scheduleTimeButton == null || slot < 0 || slot >= 8) return;
        loadingSchedulePoint = true;
        SchedulePoint point = schedulePoints[slot];
        if (point == null) {
            scheduleEditorPanel.setVisibility(android.view.View.GONE);
            loadingSchedulePoint = false;
            refreshScheduleTimeline(false);
            return;
        }
        scheduleEditorPanel.setVisibility(android.view.View.VISIBLE);
        scheduleHour = point.hour;
        scheduleMinute = point.minute;
        scheduleEnabledSwitch.setChecked(point.enabled);
        for (int i = 0; i < scheduleBars.length; i++) scheduleBars[i].setProgress(point.levels[i]);
        scheduleEditTitleView.setText((point.saved ? tr("Editar ponto", "Edit point")
                : tr("Novo ponto", "New point")) + " · " + twoDigits(point.hour) + ":" + twoDigits(point.minute));
        scheduleTimeButton.setText(tr("Alterar horário · ", "Change time · ") + twoDigits(scheduleHour) + ":" + twoDigits(scheduleMinute));
        loadingSchedulePoint = false;
        updateConflictMessage(slot);
        refreshScheduleTimeline(false);
        if (lightGraph != null) lightGraph.invalidate();
    }

    private void selectSchedulePoint(int slot) {
        activeScheduleSlot = slot;
        loadSchedulePoint(slot);
        refreshScheduleTimeline(true);
        if (lightGraph != null) lightGraph.invalidate();
    }

    private void updateScheduleGraphPreview() {
        if (lightGraph == null || activeScheduleSlot < 0 || activeScheduleSlot >= 8) return;
        int slot = activeScheduleSlot;
        int[] levels = new int[scheduleBars.length];
        for (int i = 0; i < levels.length; i++) levels[i] = scheduleBars[i].getProgress();
        SchedulePoint oldPoint = schedulePoints[slot];
        schedulePoints[slot] = new SchedulePoint(slot, scheduleHour, scheduleMinute,
                scheduleEnabledSwitch.isChecked(), levels, oldPoint != null && oldPoint.saved);
        updateConflictMessage(slot);
        refreshScheduleTimeline(true);
        lightGraph.invalidate();
    }

    private boolean hasScheduleConflict(int slot, int hour, int minute) {
        for (int i = 0; i < schedulePoints.length; i++) {
            SchedulePoint other = schedulePoints[i];
            if (i != slot && other != null && other.enabled && other.hour == hour && other.minute == minute) return true;
        }
        return false;
    }

    private boolean hasScheduleConflict(int slot) {
        SchedulePoint point = schedulePoints[slot];
        return point != null && point.enabled && hasScheduleConflict(slot, point.hour, point.minute);
    }

    private void updateConflictMessage(int slot) {
        boolean conflict = hasScheduleConflict(slot);
        scheduleConflictView.setVisibility(conflict ? android.view.View.VISIBLE : android.view.View.GONE);
        scheduleConflictView.setText(conflict
                ? tr("Já existe outro ponto ativo neste horário. Escolha outro horário para evitar sobreposição.",
                "Another active point uses this time. Choose a different time to avoid overlap.")
                : "");
    }

    private void startScanFlow() {
        if (bluetoothAdapter == null) {
            setStatus(tr("Este aparelho não oferece Bluetooth.", "This device does not support Bluetooth."));
            return;
        }
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus(tr("Ative o Bluetooth para pesquisar a lâmpada.", "Turn on Bluetooth to search for the lamp."));
            try {
                startActivityForResult(new android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            } catch (SecurityException exception) {
                requestBlePermissions();
            }
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            setStatus(tr("Não foi possível iniciar o scanner BLE.", "Could not start the BLE scanner."));
            return;
        }
        foundDevices.clear();
        devicesContainer.removeAllViews();
        devicesLabel.setVisibility(android.view.View.GONE);
        scanning = true;
        scanButton.setEnabled(false);
        scanButton.setText(tr("Buscando luminária…", "Searching for lamp…"));
        setStatus(tr("Buscando dispositivos próximos por 12 segundos…", "Searching for nearby devices for 12 seconds…"));
        try {
            scanner.startScan(scanCallback);
            handler.postDelayed(this::stopScan, 12000);
        } catch (SecurityException exception) {
            scanning = false;
            scanButton.setEnabled(true);
            scanButton.setText(tr("Buscar luminária", "Search for lamp"));
            setStatus(tr("Permissão Bluetooth não concedida.", "Bluetooth permission was not granted."));
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String advertisedName = null;
            if (result.getScanRecord() != null) advertisedName = result.getScanRecord().getDeviceName();
            if (advertisedName == null || advertisedName.trim().isEmpty()) {
                try { advertisedName = device.getName(); }
                catch (SecurityException ignored) { }
            }
            if (!isLampName(advertisedName)) return;
            String address = device.getAddress();
            if (!foundDevices.containsKey(address)) {
                foundDevices.put(address, device);
                String lampName = advertisedName;
                runOnUiThread(() -> addFoundDevice(device, lampName, result.getRssi()));
            }
        }
        @Override public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                scanning = false;
                scanButton.setEnabled(true);
                scanButton.setText(tr("Buscar luminária", "Search for lamp"));
                setStatus(tr("A busca BLE falhou (código ", "BLE search failed (code ") + errorCode + tr("). Tente novamente.", "). Try again."));
            });
        }
    };

    private boolean isLampName(String name) {
        // The original app accepts the OceanTech lamp's TO-L- prefix and four-character ID.
        return name != null && name.matches("(?i)^TO-L-.{4}$");
    }

    private void addFoundDevice(BluetoothDevice device, String name, int rssi) {
        Button deviceButton = new Button(this);
        deviceButton.setText(name + "  ·  " + device.getAddress() + "  ·  " + rssi + " dBm");
        styleButton(deviceButton, false);
        deviceButton.setTextSize(13);
        deviceButton.setOnClickListener(v -> connect(device));
        devicesLabel.setVisibility(android.view.View.VISIBLE);
        devicesContainer.addView(deviceButton, topMargin(4));
        setStatus(tr("Encontrados: ", "Found: ") + foundDevices.size() + tr(". Toque no dispositivo da lâmpada.", ". Tap the lamp device to connect."));
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try {
            if (scanner != null && hasBlePermissions()) scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) { }
        scanButton.setEnabled(true);
        scanButton.setText(tr("Buscar luminária", "Search for lamp"));
        if (foundDevices.isEmpty()) setStatus(tr("Nenhuma luminária OceanTech encontrada. Procure um nome TO-L-XXXX e tente novamente.", "No OceanTech lamp found. Look for a TO-L-XXXX name and try again."));
        else setStatus(tr("Busca concluída. Toque no dispositivo da lâmpada para conectar.", "Search complete. Tap the lamp device to connect."));
    }

    private void connect(BluetoothDevice device) {
        stopScan();
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }
        closeGatt();
        setConnectedContentVisible(false);
        scheduleDataLoaded = false;
        activeScheduleSlot = -1;
        for (int i = 0; i < schedulePoints.length; i++) schedulePoints[i] = null;
        if (scheduleTimeline != null) refreshScheduleTimeline(true);
        if (scheduleEditorPanel != null) scheduleEditorPanel.setVisibility(android.view.View.GONE);
        alarmReadsCompleted = 0;
        connectedReady = false;
        modeKnown = false;
        modeHeader = null;
        modeButton.setEnabled(false);
        powerButton.setEnabled(false);
        saveWeekButton.setEnabled(false);
        clockButton.setEnabled(false);
        setScheduleControlsEnabled(false);
        for (CheckBox day : weekDays) day.setEnabled(false);
        sendButton.setEnabled(false);
        handshakeQueued = false;
        pendingPower = null;
        pendingAutoMode = null;
        writeInFlight = false;
        writeQueue.clear();
        setStatus(tr("Conectando a ", "Connecting to ") + device.getAddress() + "…");
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException exception) {
            setStatus(tr("Sem permissão para conectar por Bluetooth.", "Bluetooth connection permission is missing."));
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> setStatus(tr("Conectado. Lendo serviços da lâmpada…", "Connected. Reading lamp services…")));
                try {
                    if (!bluetoothGatt.discoverServices()) {
                        runOnUiThread(() -> setStatus(tr("Não consegui iniciar a leitura dos serviços BLE.", "Could not start BLE service discovery.")));
                    }
                } catch (SecurityException exception) {
                    runOnUiThread(() -> setStatus(tr("A permissão Bluetooth foi removida.", "Bluetooth permission was removed.")));
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedReady = false;
                modeKnown = false;
                modeHeader = null;
                pendingAutoMode = null;
                pendingPower = null;
                writeCharacteristic = null;
                stateCharacteristic = null;
                weekCharacteristic = null;
                alarmCharacteristic = null;
                alarmCharacteristic = null;
                writeQueue.clear();
                writeInFlight = false;
                runOnUiThread(() -> {
                    sendButton.setEnabled(false);
                    modeButton.setEnabled(false);
                    powerButton.setEnabled(false);
                    saveWeekButton.setEnabled(false);
                    clockButton.setEnabled(false);
                    setScheduleControlsEnabled(false);
                    setConnectedContentVisible(false);
                    for (CheckBox day : weekDays) day.setEnabled(false);
                    modeButton.setText(tr("Modo da lâmpada: aguardando leitura", "Lamp mode: waiting for status"));
                    setStatus(tr("Lâmpada desconectada.", "Lamp disconnected."));
                });
                if (gatt == bluetoothGatt) gatt = null;
                try { bluetoothGatt.close(); } catch (SecurityException ignored) { }
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> setStatus(tr("Não consegui ler os serviços BLE da lâmpada.", "Could not read the lamp BLE services.")));
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(WRITE_UUID);
            BluetoothGattCharacteristic state = service == null ? null : service.getCharacteristic(STATE_UUID);
            BluetoothGattCharacteristic week = service == null ? null : service.getCharacteristic(WEEK_UUID);
            BluetoothGattCharacteristic alarm = service == null ? null : service.getCharacteristic(ALARM_UUID);
            if (characteristic == null) {
                runOnUiThread(() -> setStatus(tr("A lâmpada não anunciou o serviço esperado (AE00/AE01).", "The lamp did not advertise the expected service (AE00/AE01).")));
                return;
            }
            writeCharacteristic = characteristic;
            stateCharacteristic = state;
            weekCharacteristic = week != null
                    && (week.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? week : null;
            alarmCharacteristic = alarm != null
                    && (alarm.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? alarm : null;
            if (state == null || (state.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                runOnUiThread(() -> setStatus(tr("Conectou, mas a característica de estado AE10 não pode ser lida.", "Connected, but status characteristic AE10 could not be read.")));
                return;
            }
            enqueueWrite(asciiFrame(HANDSHAKE, false));
            handshakeQueued = true;
            runOnUiThread(() -> setStatus(tr("Serviço encontrado. Confirmando protocolo…", "Service found. Confirming protocol…")));
        }

        @Override public void onCharacteristicWrite(BluetoothGatt bluetoothGatt,
                                                     BluetoothGattCharacteristic characteristic, int status) {
            if (!WRITE_UUID.equals(characteristic.getUuid())) return;
            writeInFlight = false;
            if (status == BluetoothGatt.GATT_SUCCESS && handshakeQueued) {
                handshakeQueued = false;
                connectedReady = true;
                runOnUiThread(() -> {
                    setStatus(tr("Conectado. Lendo o modo e os níveis atuais…", "Connected. Reading the current mode and levels…"));
                });
                BluetoothGattCharacteristic state = stateCharacteristic;
                boolean readStarted = false;
                try {
                    readStarted = state != null && bluetoothGatt.readCharacteristic(state);
                } catch (SecurityException ignored) { }
                if (!readStarted) {
                    runOnUiThread(() -> setStatus(tr("Conectado, mas não consegui ler o estado inicial (AE10).", "Connected, but could not read the initial status (AE10).")));
                }
            } else if (status == BluetoothGatt.GATT_SUCCESS && pendingAutoMode != null) {
                autoMode = pendingAutoMode;
                pendingAutoMode = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    setStatus(autoMode ? tr("Modo automático ativado.", "Automatic mode enabled.") : tr("Modo manual ativado. Agora envie os níveis.", "Manual mode enabled. Send the levels now."));
                });
            } else if (status == BluetoothGatt.GATT_SUCCESS && pendingPower != null) {
                boolean on = pendingPower;
                lampOn = on;
                if (modeHeader != null && modeHeader.length() >= 10) {
                    modeHeader = modeHeader.substring(0, 5) + (on ? "1" : "0") + modeHeader.substring(6);
                }
                pendingPower = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    powerButton.setText(on ? tr("Lâmpada ligada · toque para desligar", "Lamp on · tap to turn off") : tr("Lâmpada desligada · toque para ligar", "Lamp off · tap to turn on"));
                    setStatus(on ? tr("Lâmpada ligada.", "Lamp turned on.") : tr("Lâmpada desligada.", "Lamp turned off."));
                });
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingAutoMode = null;
                pendingPower = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    setStatus(tr("Falha ao enviar dado BLE (", "Failed to send BLE data (") + status + ").");
                });
            }
            writeNext();
        }

        @Override public void onCharacteristicRead(BluetoothGatt bluetoothGatt,
                                                    BluetoothGattCharacteristic characteristic, int status) {
            UUID uuid = characteristic.getUuid();
            if (!STATE_UUID.equals(uuid) && !WEEK_UUID.equals(uuid) && !ALARM_UUID.equals(uuid)) return;
            byte[] value = characteristic.getValue();
            if (Build.VERSION.SDK_INT >= 33) {
                // The API 33 callback below carries the immutable value directly.
                return;
            }
            if (STATE_UUID.equals(uuid)) handleStateRead(value, status);
            else if (WEEK_UUID.equals(uuid)) handleWeekRead(value, status);
            else handleAlarmRead(value, status);
        }

        @Override public void onCharacteristicRead(BluetoothGatt bluetoothGatt,
                                                    BluetoothGattCharacteristic characteristic,
                                                    byte[] value, int status) {
            if (STATE_UUID.equals(characteristic.getUuid())) handleStateRead(value, status);
            else if (WEEK_UUID.equals(characteristic.getUuid())) handleWeekRead(value, status);
            else if (ALARM_UUID.equals(characteristic.getUuid())) handleAlarmRead(value, status);
        }
    };

    private void handleStateRead(byte[] value, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS || value == null || value.length < 12) {
            runOnUiThread(() -> setStatus(tr("Conectado, mas a lâmpada não retornou o estado inicial.", "Connected, but the lamp did not return its initial status.")));
            return;
        }
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 0xff));
        String state = hex.toString();
        if (state.length() < 24 || state.charAt(1) != '6') {
            runOnUiThread(() -> setStatus(tr("Resposta BLE inesperada. Não vou enviar níveis até conseguir identificar o estado.", "Unexpected BLE response. Levels will not be sent until the lamp status is identified.")));
            return;
        }
        modeHeader = state.substring(0, 10);
        lampOn = state.charAt(5) == '1';
        autoMode = state.charAt(7) == '1';
        modeKnown = true;
        int[] channelValues = new int[4];
        for (int i = 0; i < 4; i++) {
            int offset = 16 + i * 2;
            if (state.length() >= offset + 2) {
                try { channelValues[i] = Math.min(100, Integer.parseInt(state.substring(offset, offset + 2), 16)); }
                catch (NumberFormatException ignored) { channelValues[i] = 0; }
            }
        }
        runOnUiThread(() -> {
            for (int i = 0; i < channelBars.length; i++) {
                channelBars[i].setProgress(channelValues[i]);
                channelValuesView(i, channelValues[i]);
            }
            updateModeControls();
            setConnectedContentVisible(true);
            powerButton.setText(lampOn
                    ? tr("Lâmpada ligada · toque para desligar", "Lamp on · tap to turn off")
                    : tr("Lâmpada desligada · toque para ligar", "Lamp off · tap to turn on"));
            powerButton.setEnabled(connectedReady);
            saveWeekButton.setEnabled(false);
            clockButton.setEnabled(connectedReady);
            setScheduleControlsEnabled(connectedReady);
            for (CheckBox day : weekDays) day.setEnabled(false);
            setStatus(tr("Estado lido da lâmpada. Lendo repetição semanal…", "Lamp status read. Reading weekly repeat…"));
        });
        BluetoothGattCharacteristic week = weekCharacteristic;
        BluetoothGatt currentGatt = gatt;
        if (week != null && currentGatt != null) {
            handler.postDelayed(() -> {
                try {
                    if (gatt == currentGatt) {
                        if (!currentGatt.readCharacteristic(week)) beginAlarmReads();
                    }
                } catch (SecurityException ignored) { beginAlarmReads(); }
            }, 300);
        } else {
            beginAlarmReads();
        }
    }

    private void handleWeekRead(byte[] value, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS || value == null) {
            beginAlarmReads();
            return;
        }
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 0xff));
        String data = hex.toString();
        if (data.length() < 14) {
            beginAlarmReads();
            return;
        }
        runOnUiThread(() -> {
            for (int i = 0; i < weekDays.length; i++) {
                weekDays[i].setChecked(data.charAt(i * 2 + 1) == '1');
                weekDays[i].setEnabled(connectedReady);
            }
            saveWeekButton.setEnabled(connectedReady);
            setStatus(tr("Estado e repetição semanal lidos da lâmpada.", "Lamp status and weekly repeat read."));
        });
        beginAlarmReads();
    }

    private void beginAlarmReads() {
        alarmReadsCompleted = 0;
        if (gatt == null || alarmCharacteristic == null) {
            updateScheduleGraph();
            return;
        }
        readNextAlarmRecord();
    }

    private void readNextAlarmRecord() {
        BluetoothGatt currentGatt = gatt;
        BluetoothGattCharacteristic alarm = alarmCharacteristic;
        if (currentGatt == null || alarm == null || alarmReadsCompleted >= 10) {
            updateScheduleGraph();
            return;
        }
        try {
            if (!currentGatt.readCharacteristic(alarm)) {
                alarmReadsCompleted = 10;
                updateScheduleGraph();
            }
        } catch (SecurityException exception) {
            alarmReadsCompleted = 10;
            updateScheduleGraph();
        }
    }

    private void handleAlarmRead(byte[] value, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.length >= 8) {
            StringBuilder hex = new StringBuilder(value.length * 2);
            for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 0xff));
            String record = hex.toString();
            if (record.length() >= 16 && !"0000000000000000".equals(record.substring(0, 16))) {
                int slot = Character.digit(record.charAt(1), 16);
                if (slot >= 0 && slot < schedulePoints.length) {
                    try {
                        int hour = Integer.parseInt(record.substring(4, 6));
                        int minute = Integer.parseInt(record.substring(6, 8));
                        int[] levels = new int[4];
                        for (int i = 0; i < levels.length; i++) {
                            levels[i] = Math.min(100, Integer.parseInt(record.substring(8 + i * 2, 10 + i * 2), 16));
                        }
                        boolean enabled = "1e".equals(record.substring(2, 4));
                        schedulePoints[slot] = new SchedulePoint(slot, hour, minute, enabled, levels, true);
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        alarmReadsCompleted++;
        if (alarmReadsCompleted >= 10) updateScheduleGraph();
        else handler.postDelayed(this::readNextAlarmRecord, 120);
    }

    private void updateScheduleGraph() {
        scheduleDataLoaded = true;
        runOnUiThread(() -> {
            if (lightGraph != null) lightGraph.invalidate();
            if (activeScheduleSlot < 0 || activeScheduleSlot >= 8 || schedulePoints[activeScheduleSlot] == null) {
                activeScheduleSlot = firstSchedulePointByTime();
            }
            refreshScheduleTimeline(true);
            setScheduleControlsEnabled(connectedReady);
            if (activeScheduleSlot >= 0) loadSchedulePoint(activeScheduleSlot);
            else scheduleEditorPanel.setVisibility(android.view.View.GONE);
            setStatus(tr("Programação carregada. Toque em um horário para editar ou adicione um novo.",
                    "Schedule loaded. Tap a time to edit it or add a new one."));
        });
    }

    private int firstSchedulePointByTime() {
        int selected = -1;
        for (int i = 0; i < schedulePoints.length; i++) {
            if (schedulePoints[i] == null) continue;
            if (selected < 0 || schedulePoints[i].hour * 60 + schedulePoints[i].minute
                    < schedulePoints[selected].hour * 60 + schedulePoints[selected].minute) selected = i;
        }
        return selected;
    }

    private void refreshScheduleSlotLabels() { refreshScheduleTimeline(true); }

    private void toggleMode() {
        if (!connectedReady || !modeKnown || modeHeader == null) {
            setStatus(tr("Ainda não consegui ler o estado da lâmpada.", "Could not read the lamp status yet."));
            return;
        }
        boolean nextAutoMode = !autoMode;
        String command = "app_pwm_on" + modeHeader.charAt(4) + modeHeader.charAt(5)
                + (nextAutoMode ? "01" : "00") + modeHeader.charAt(8) + modeHeader.charAt(9);
        pendingAutoMode = nextAutoMode;
        modeButton.setEnabled(false);
        enqueueWrite(asciiFrame(command, true));
        setStatus(nextAutoMode ? tr("Enviando comando para ativar modo automático…", "Sending command to enable automatic mode…") : tr("Enviando comando para ativar modo manual…", "Sending command to enable manual mode…"));
    }

    private void togglePower() {
        if (!connectedReady || !modeKnown || modeHeader == null) {
            setStatus(tr("Ainda não consegui ler o estado da lâmpada.", "Could not read the lamp status yet."));
            return;
        }
        boolean nextOn = !lampOn;
        // The original app writes app_pwm_on + [mode, power] + the remaining mode bytes.
        String command = "app_pwm_on0" + (nextOn ? "1" : "0") + modeHeader.substring(6, 10);
        pendingPower = nextOn;
        powerButton.setEnabled(false);
        enqueueWrite(asciiFrame(command, true));
        setStatus(nextOn ? tr("Enviando comando para ligar…", "Sending command to turn on…") : tr("Enviando comando para desligar…", "Sending command to turn off…"));
    }

    private void saveWeekDays() {
        if (!connectedReady || weekCharacteristic == null) {
            setStatus(tr("A lâmpada não disponibiliza a configuração semanal.", "This lamp does not provide weekly scheduling."));
            return;
        }
        StringBuilder command = new StringBuilder("app_week_data");
        for (CheckBox day : weekDays) command.append(day.isChecked() ? '1' : '0');
        enqueueWrite(asciiFrame(command.toString(), true));
        // The original app applies its weekly timer frequency after saving the seven flags.
        enqueueWrite(asciiFrame("app_set_pwm_fre750", true));
        setStatus(tr("Dias da semana e frequência enviados à lâmpada.", "Weekdays and frequency sent to the lamp."));
    }

    private void syncLampClock() {
        if (!connectedReady) {
            setStatus(tr("Conecte à lâmpada antes de sincronizar o relógio.", "Connect to the lamp before syncing its clock."));
            return;
        }
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR) - 2000;
        int month = now.get(Calendar.MONTH) + 1;
        int dayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        int weekday = now.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int rawSecond = now.get(Calendar.SECOND);
        // Preserve the original app's exact formatting, including its padded-seconds concatenation.
        String secondField = rawSecond < 10 ? "0" + rawSecond + "2" : Integer.toString(rawSecond + 2);
        String command = "RTCSET" + year + twoDigits(month) + "0" + weekday
                + twoDigits(dayOfMonth) + twoDigits(hour) + twoDigits(minute) + secondField;
        enqueueWrite(asciiFrame(command, true));
        setStatus(tr("Relógio do telefone enviado à lâmpada.", "Phone clock sent to the lamp."));
    }

    private void chooseScheduleTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            scheduleHour = hour;
            scheduleMinute = minute;
            SchedulePoint current = schedulePoints[activeScheduleSlot];
            scheduleEditTitleView.setText((current != null && current.saved ? tr("Editar ponto", "Edit point")
                    : tr("Novo ponto", "New point")) + " · " + twoDigits(hour) + ":" + twoDigits(minute));
            scheduleTimeButton.setText(tr("Alterar horário · ", "Change time · ") + twoDigits(hour) + ":" + twoDigits(minute));
            updateScheduleGraphPreview();
        }, scheduleHour, scheduleMinute, true).show();
    }

    private void saveSchedule() {
        if (!connectedReady) {
            setStatus(tr("Conecte à lâmpada antes de salvar um horário.", "Connect to the lamp before saving a time."));
            return;
        }
        int slot = activeScheduleSlot;
        if (scheduleEnabledSwitch.isChecked() && hasScheduleConflict(slot, scheduleHour, scheduleMinute)) {
            setStatus(tr("Esse horário já está usado por outro ponto ativo. Escolha outro minuto antes de salvar.", "Another active point already uses this time. Choose a different minute before saving."));
            Toast.makeText(this, tr("Horário repetido: cada ponto ativo precisa de um horário diferente.", "Duplicate time: each active point needs a different time."), Toast.LENGTH_LONG).show();
            return;
        }
        String enabled = scheduleEnabledSwitch.isChecked() ? "30" : "00";
        StringBuilder command = new StringBuilder("w_a_d")
                .append(slot).append(enabled).append(twoDigits(scheduleHour)).append(twoDigits(scheduleMinute));
        int[] levels = new int[scheduleBars.length];
        for (int i = 0; i < scheduleBars.length; i++) {
            levels[i] = scheduleBars[i].getProgress();
            command.append(twoDigitsHex(levels[i]));
        }
        schedulePoints[slot] = new SchedulePoint(slot, scheduleHour, scheduleMinute,
                scheduleEnabledSwitch.isChecked(), levels, true);
        loadSchedulePoint(slot);
        refreshScheduleTimeline(true);
        if (lightGraph != null) lightGraph.invalidate();
        enqueueWrite(asciiFrame(command.toString(), true));
        setStatus(tr("Horário salvo com as intensidades escolhidas.", "Schedule point saved with the selected light levels."));
    }

    private String twoDigitsHex(int value) {
        return String.format(Locale.US, "%02x", Math.max(0, Math.min(100, value)));
    }

    private void setScheduleControlsEnabled(boolean enabled) {
        boolean canEdit = enabled && scheduleDataLoaded && activeScheduleSlot >= 0;
        boolean canAdd = enabled && scheduleDataLoaded && editableSchedulePoints().size() < schedulePoints.length;
        if (addScheduleButton != null) addScheduleButton.setEnabled(canAdd);
        if (scheduleTimeButton != null) scheduleTimeButton.setEnabled(canEdit);
        if (scheduleEnabledSwitch != null) scheduleEnabledSwitch.setEnabled(canEdit);
        for (SeekBar bar : scheduleBars) if (bar != null) bar.setEnabled(canEdit);
        if (saveScheduleButton != null) saveScheduleButton.setEnabled(canEdit);
    }

    private String twoDigits(int value) {
        return String.format(Locale.US, "%02d", value);
    }

    private void updateModeControls() {
        if (modeKnown) {
            modeButton.setText(autoMode ? tr("Modo atual: automático · toque para manual", "Current mode: automatic · tap for manual") : tr("Modo atual: manual · toque para automático", "Current mode: manual · tap for automatic"));
            modeButton.setEnabled(connectedReady);
            powerButton.setEnabled(connectedReady && pendingPower == null);
            sendButton.setEnabled(connectedReady && !autoMode);
        } else {
            modeButton.setText(tr("Modo da lâmpada: aguardando leitura", "Lamp mode: waiting for status"));
            modeButton.setEnabled(false);
            powerButton.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }

    private void sendChannelLevels() {
        if (!connectedReady || !modeKnown) {
            Toast.makeText(this, tr("Conecte à lâmpada e aguarde a leitura do estado.", "Connect to the lamp and wait for its status to load."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (autoMode) {
            Toast.makeText(this, tr("Mude para o modo manual antes de enviar os níveis.", "Switch to manual mode before sending levels."), Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder command = new StringBuilder("rtvall00");
        for (SeekBar bar : channelBars) command.append(String.format(Locale.US, "%03d", bar.getProgress()));
        enqueueWrite(asciiFrame(command.toString(), true));
        setStatus(tr("Comando enviado: W/B/G/R ", "Command sent: W/B/G/R ") + channelBars[0].getProgress() + "/"
                + channelBars[1].getProgress() + "/" + channelBars[2].getProgress() + "/"
                + channelBars[3].getProgress() + ".");
    }

    private byte[] asciiFrame(String command, boolean padToTwenty) {
        byte[] bytes = command.getBytes(StandardCharsets.US_ASCII);
        int size = padToTwenty ? 20 : bytes.length;
        byte[] frame = new byte[size];
        System.arraycopy(bytes, 0, frame, 0, Math.min(bytes.length, frame.length));
        return frame;
    }

    private synchronized void enqueueWrite(byte[] bytes) {
        writeQueue.add(bytes);
        writeNext();
    }

    private synchronized void writeNext() {
        if (writeInFlight || writeQueue.isEmpty() || gatt == null || writeCharacteristic == null) return;
        byte[] next = writeQueue.poll();
        int properties = writeCharacteristic.getProperties();
        int writeType = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        writeInFlight = true;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                int result = gatt.writeCharacteristic(writeCharacteristic, next, writeType);
                if (result != BluetoothStatusCodes.SUCCESS) {
                    writeInFlight = false;
                    runOnUiThread(() -> setStatus(tr("O Android recusou o comando BLE (", "Android rejected the BLE command (") + result + ")."));
                }
            } else {
                writeCharacteristic.setWriteType(writeType);
                writeCharacteristic.setValue(next);
                if (!gatt.writeCharacteristic(writeCharacteristic)) {
                    writeInFlight = false;
                    runOnUiThread(() -> setStatus(tr("O Android não iniciou o envio BLE.", "Android did not start the BLE write.")));
                }
            }
        } catch (SecurityException exception) {
            writeInFlight = false;
            runOnUiThread(() -> setStatus(tr("Sem permissão para escrever na lâmpada.", "Permission to write to the lamp is missing.")));
        }
    }

    private void disconnect() {
        connectedReady = false;
        modeKnown = false;
        modeHeader = null;
        pendingAutoMode = null;
        pendingPower = null;
        sendButton.setEnabled(false);
        modeButton.setEnabled(false);
        powerButton.setEnabled(false);
        saveWeekButton.setEnabled(false);
        clockButton.setEnabled(false);
        setScheduleControlsEnabled(false);
        for (CheckBox day : weekDays) day.setEnabled(false);
        modeButton.setText(tr("Modo da lâmpada: aguardando leitura", "Lamp mode: waiting for status"));
        closeGatt();
        setStatus(tr("Desconectado.", "Disconnected."));
    }

    private void closeGatt() {
        BluetoothGatt oldGatt = gatt;
        gatt = null;
        writeCharacteristic = null;
        stateCharacteristic = null;
        weekCharacteristic = null;
        alarmCharacteristic = null;
        writeQueue.clear();
        writeInFlight = false;
        if (oldGatt != null) {
            try {
                oldGatt.disconnect();
                oldGatt.close();
            } catch (SecurityException ignored) { }
        }
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLE_PERMISSIONS);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLE_PERMISSIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLE_PERMISSIONS) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) startScanFlow();
            else setStatus(tr("Permita dispositivos próximos/Bluetooth para buscar a lâmpada.", "Allow Nearby Devices/Bluetooth permission to search for the lamp."));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode == RESULT_OK) startScanFlow();
    }

    @Override protected void onDestroy() {
        if (scanning) stopScan();
        closeGatt();
        super.onDestroy();
    }

    private final class LightGraphView extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int[] lineColors = {Color.rgb(111, 119, 124), Color.rgb(48, 105, 206),
                Color.rgb(40, 157, 112), Color.rgb(218, 82, 75)};
        private final String[] lineNames = channelNames();
        private final String[] hourLabels = {"00", "06", "12", "18", "24"};

        LightGraphView(Context context) {
            super(context);
            setBackground(rounded(Color.WHITE, Color.rgb(235, 240, 240), 12));
            setClickable(true);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(36), right = getWidth() - dp(10);
            float top = dp(24), bottom = getHeight() - dp(30);
            float legendX = left;
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(9));
            for (int i = 0; i < lineNames.length; i++) {
                paint.setColor(lineColors[i]);
                canvas.drawCircle(legendX + dp(3), dp(12), dp(3), paint);
                paint.setColor(Color.rgb(90, 106, 110));
                canvas.drawText(lineNames[i], legendX + dp(8), dp(15), paint);
                legendX += dp(i == 0 || i == 3 ? 70 : 57);
            }
            paint.setStrokeWidth(dp(1));
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.rgb(226, 233, 234));
            for (int percent = 0; percent <= 100; percent += 25) {
                float y = bottom - (bottom - top) * percent / 100f;
                canvas.drawLine(left, y, right, y, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(dp(10));
                paint.setColor(Color.rgb(112, 127, 130));
                canvas.drawText(percent + "%", dp(3), y + dp(4), paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(226, 233, 234));
            }
            for (int hour = 0; hour <= 24; hour += 6) {
                float x = left + (right - left) * hour / 24f;
                canvas.drawLine(x, top, x, bottom, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(10));
            paint.setColor(Color.rgb(112, 127, 130));
            for (int i = 0; i < hourLabels.length; i++) {
                float x = left + (right - left) * i / 4f;
                float offset = i == 0 ? 0 : i == 4 ? dp(12) : dp(7);
                canvas.drawText(hourLabels[i], x - offset, getHeight() - dp(8), paint);
            }

            ArrayList<SchedulePoint> points = activeSchedulePoints();
            if (points.isEmpty()) {
                paint.setColor(Color.rgb(125, 139, 142));
                paint.setTextSize(dp(12));
                canvas.drawText(tr("Adicione um horário para ver a curva de luz",
                                "Add a time point to preview the light curve"), left + dp(4),
                        (top + bottom) / 2f, paint);
                return;
            }
            Collections.sort(points, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
            for (int channel = 0; channel < 4; channel++) {
                path.reset();
                for (int i = 0; i < points.size(); i++) {
                    SchedulePoint point = points.get(i);
                    float x = timeX(point, left, right);
                    float y = levelY(point.levels[channel], top, bottom);
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(lineColors[channel]);
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                for (SchedulePoint point : points) {
                    float x = timeX(point, left, right);
                    float y = levelY(point.levels[channel], top, bottom);
                    if (point.slot == activeScheduleSlot) {
                        paint.setColor(Color.WHITE);
                        canvas.drawCircle(x, y, dp(6), paint);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(1.5f));
                        paint.setColor(lineColors[channel]);
                        canvas.drawCircle(x, y, dp(5), paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    paint.setColor(lineColors[channel]);
                    canvas.drawCircle(x, y, dp(point.slot == activeScheduleSlot ? 3 : 2.5f), paint);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float left = dp(36), right = getWidth() - dp(10);
            float top = dp(24), bottom = getHeight() - dp(30);
            float nearest = dp(26);
            int selectedSlot = -1;
            for (SchedulePoint point : activeSchedulePoints()) {
                for (int channel = 0; channel < 4; channel++) {
                    float dx = event.getX() - timeX(point, left, right);
                    float dy = event.getY() - levelY(point.levels[channel], top, bottom);
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance < nearest) { nearest = distance; selectedSlot = point.slot; }
                }
            }
            if (selectedSlot >= 0) {
                if (selectedSlot < schedulePoints.length) {
                    showProgrammingTab(true);
                    selectSchedulePoint(selectedSlot);
                    performClick();
                }
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        private float timeX(SchedulePoint point, float left, float right) {
            float fraction = (point.hour * 60f + point.minute) / (24f * 60f);
            return left + (right - left) * fraction;
        }

        private float levelY(int level, float top, float bottom) {
            return bottom - (bottom - top) * Math.max(0, Math.min(100, level)) / 100f;
        }
    }

    private final class MiniLevelsView extends android.view.View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {Color.rgb(111, 119, 124), Color.rgb(48, 105, 206),
                Color.rgb(40, 157, 112), Color.rgb(218, 82, 75)};
        private final String[] initials = appLanguage.equals("en")
                ? new String[]{"W", "B", "G", "R"} : new String[]{"B", "A", "V", "Vm"};
        private int[] levels = {0, 0, 0, 0};

        MiniLevelsView(Context context) { super(context); }

        void setLevels(int[] values) {
            levels = values.clone();
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float rowHeight = getHeight() / 4f;
            paint.setTextSize(dp(9));
            for (int i = 0; i < 4; i++) {
                float centerY = rowHeight * i + rowHeight / 2f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(colors[i]);
                canvas.drawText(initials[i], 0, centerY + dp(3), paint);
                float left = dp(23), right = getWidth() - dp(31);
                float barTop = centerY - dp(3), barBottom = centerY + dp(3);
                paint.setColor(Color.rgb(229, 235, 236));
                canvas.drawRoundRect(left, barTop, right, barBottom, dp(3), dp(3), paint);
                paint.setColor(colors[i]);
                float fillRight = left + Math.max(dp(2), (right - left) * levels[i] / 100f);
                canvas.drawRoundRect(left, barTop, Math.min(right, fillRight), barBottom,
                        dp(3), dp(3), paint);
                paint.setColor(Color.rgb(84, 101, 105));
                canvas.drawText(levels[i] + "%", getWidth() - dp(28), centerY + dp(3), paint);
            }
        }
    }

    private ArrayList<SchedulePoint> activeSchedulePoints() {
        ArrayList<SchedulePoint> points = new ArrayList<>();
        for (SchedulePoint point : schedulePoints) if (point != null && point.enabled) points.add(point);
        return points;
    }

    private static final class SchedulePoint {
        final int slot;
        final int hour;
        final int minute;
        final boolean enabled;
        final int[] levels;
        final boolean saved;

        SchedulePoint(int slot, int hour, int minute, boolean enabled, int[] levels, boolean saved) {
            this.slot = slot;
            this.hour = hour;
            this.minute = minute;
            this.enabled = enabled;
            this.levels = levels.clone();
            this.saved = saved;
        }
    }

    private void setStatus(String text) {
        if (statusView == null) return;
        statusView.setText(text);
        boolean problem = text.contains("Falha") || text.contains("failed")
                || text.contains("não consegui") || text.contains("could not")
                || text.contains("não foi possível") || text.contains("Unable")
                || text.contains("Sem permissão") || text.contains("permission");
        int background = problem ? Color.rgb(252, 236, 232)
                : text.startsWith("Conectado") || text.startsWith("Connected")
                || text.startsWith("Estado lido") || text.startsWith("Status read")
                ? Color.rgb(232, 244, 241) : Color.rgb(236, 243, 245);
        int foreground = problem ? Color.rgb(145, 57, 43) : Color.rgb(35, 77, 78);
        statusView.setTextColor(foreground);
        statusView.setBackground(rounded(background, Color.TRANSPARENT, 12));
    }

    private void channelValuesView(int index, int value) {
        channelValues[index].setText(value + "%");
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(17);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setTextColor(Color.rgb(20, 40, 45));
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams topMargin(int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginDp);
        return params;
    }
}

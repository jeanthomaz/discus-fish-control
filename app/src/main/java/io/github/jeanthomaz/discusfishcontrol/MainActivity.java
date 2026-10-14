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
    private final Queue<WriteRequest> writeQueue = new ArrayDeque<>();
    private volatile WriteRequest inFlightWrite;
    private Runnable writeTimeout;
    private boolean scheduleWritePending;
    private boolean weekWritePending;
    private boolean clockWritePending;
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
    private Button removeScheduleButton;
    private TextView scheduleCapacityView;
    private TextView scheduleActionFeedback;
    private TextView weekActionFeedback;
    private TextView clockActionFeedback;
    private Button dawnTimeButton;
    private Button duskTimeButton;
    private Button saveCycleButton;
    private TextView cycleSummaryView;
    private TextView cycleValidationView;
    private TextView cycleActionFeedback;
    private int dawnMinuteOfDay = -1;
    private int duskMinuteOfDay = -1;
    private boolean cycleDirty;
    private boolean cycleWritePending;
    private android.widget.Switch scheduleEnabledSwitch;
    private LinearLayout scheduleEditorPanel;
    private LinearLayout scheduleTimeline;
    private final LinearLayout[] scheduleSlotCards = new LinearLayout[ScheduleProtocol.COLOR_POINTS];
    private final TextView[] scheduleSlotTimeLabels = new TextView[ScheduleProtocol.COLOR_POINTS];
    private final TextView[] scheduleSlotStateLabels = new TextView[ScheduleProtocol.COLOR_POINTS];
    private final MiniLevelsView[] scheduleSlotLevels = new MiniLevelsView[ScheduleProtocol.COLOR_POINTS];
    private TextView scheduleEditTitleView;
    private TextView scheduleConflictView;
    private int scheduleHour = 12;
    private int scheduleMinute;
    private final CheckBox[] weekDays = new CheckBox[7];
    private final SeekBar[] channelBars = new SeekBar[4];
    private final TextView[] channelValues = new TextView[4];
    private final SeekBar[] scheduleBars = new SeekBar[4];
    private final TextView[] scheduleValues = new TextView[4];
    private final SchedulePoint[] schedulePoints = new SchedulePoint[ScheduleProtocol.TOTAL_POINTS];
    private final boolean[] scheduleSlotStored = new boolean[ScheduleProtocol.TOTAL_POINTS];
    private final SchedulePoint[] savedSchedulePoints = new SchedulePoint[ScheduleProtocol.TOTAL_POINTS];
    private LightGraphView lightGraph;
    private int alarmReadsCompleted;
    private int activeScheduleSlot = -1;
    private boolean programmingTabSelected = true;
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
        styleButton(controlTabButton, false);
        controlTabButton.setOnClickListener(v -> showProgrammingTab(false));
        programTabButton = new Button(this);
        programTabButton.setText(tr("Programação", "Schedule"));
        styleButton(programTabButton, true);
        programTabButton.setOnClickListener(v -> showProgrammingTab(true));
        tabsRow.addView(programTabButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams controlTabParams = new LinearLayout.LayoutParams(0, -2, 1);
        controlTabParams.leftMargin = dp(8);
        tabsRow.addView(controlTabButton, controlTabParams);
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
        weekActionFeedback = helperText("");
        weekActionFeedback.setVisibility(android.view.View.GONE);
        weeklyCard.addView(weekActionFeedback, topMargin(4));

        clockButton = new Button(this);
        clockButton.setText(tr("Sincronizar relógio", "Sync clock"));
        clockButton.setEnabled(false);
        clockButton.setOnClickListener(v -> syncLampClock());
        styleButton(clockButton, false);
        weeklyCard.addView(clockButton, topMargin(4));
        clockActionFeedback = helperText("");
        clockActionFeedback.setVisibility(android.view.View.GONE);
        weeklyCard.addView(clockActionFeedback, topMargin(4));

        scheduleCard = card();
        programmingPanel.addView(scheduleCard, topMargin(14));
        scheduleCard.addView(sectionTitle(tr("Programação por horário", "Daily light schedule")));
        TextView scheduleHint = new TextView(this);
        scheduleHint.setText(tr("Cada ponto é uma mudança de intensidade. As linhas mostram como as cores variam ao longo do dia.", "Each point changes the light intensity. The lines show how each color changes during the day."));
        scheduleHint.setTextSize(13);
        scheduleHint.setTextColor(Color.rgb(65, 83, 88));
        scheduleCard.addView(scheduleHint, topMargin(3));
        buildCycleEditor();
        lightGraph = new LightGraphView(this);
        scheduleCard.addView(lightGraph, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));
        scheduleCard.addView(helperText(tr("Toque em um horário para editar. A curva mostra a mudança de intensidade ao longo do dia.", "Tap a time to edit it. The chart shows how light intensity changes during the day.")), topMargin(8));
        LinearLayout timelineHeader = new LinearLayout(this);
        timelineHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView timelineTitle = sectionTitle(tr("Mudanças de cor", "Color changes"));
        timelineHeader.addView(timelineTitle, new LinearLayout.LayoutParams(0, -2, 1));
        addScheduleButton = new Button(this);
        addScheduleButton.setText(tr("+ Adicionar horário", "+ Add time"));
        addScheduleButton.setOnClickListener(v -> addSchedulePoint());
        styleButton(addScheduleButton, true);
        timelineHeader.addView(addScheduleButton, new LinearLayout.LayoutParams(-2, dp(44)));
        scheduleCard.addView(timelineHeader, topMargin(14));
        scheduleCapacityView = helperText("");
        scheduleCard.addView(scheduleCapacityView, topMargin(4));
        scheduleEmptyView = helperText(tr("Nenhum horário configurado. Adicione o primeiro ponto para desenhar a rotina de luz.", "No times set. Add the first point to build your light routine."));
        scheduleCard.addView(scheduleEmptyView, topMargin(4));
        scheduleTimeline = new LinearLayout(this);
        scheduleTimeline.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) {
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
        removeScheduleButton = new Button(this);
        removeScheduleButton.setText(tr("Remover horário", "Remove time"));
        removeScheduleButton.setOnClickListener(v -> removeSchedulePoint());
        styleButton(removeScheduleButton, false);
        removeScheduleButton.setTextColor(Color.rgb(160, 55, 43));
        scheduleEditorPanel.addView(removeScheduleButton, topMargin(4));
        scheduleCard.addView(scheduleEditorPanel, topMargin(12));
        scheduleActionFeedback = helperText("");
        scheduleActionFeedback.setVisibility(android.view.View.GONE);
        scheduleCard.addView(scheduleActionFeedback, topMargin(4));
        refreshScheduleTimeline(true);
        setScheduleControlsEnabled(false);
        programmingPanel.addView(weeklyCard, topMargin(14));

    }

    private void buildCycleEditor() {
        LinearLayout cyclePanel = card();
        cyclePanel.setBackground(rounded(Color.rgb(247, 250, 250), Color.rgb(232, 239, 239), 14));
        cyclePanel.addView(sectionTitle(tr("Amanhecer e anoitecer", "Dawn and dusk")));
        cyclePanel.addView(helperText(tr("O amanhecer inicia o ciclo diário, e o anoitecer encerra o ciclo. As mudanças de cor salvas acontecem entre esses horários.",
                "Dawn starts the daily cycle, and dusk ends it. Saved color changes happen between these times.")), topMargin(3));
        dawnTimeButton = addCycleTimeRow(cyclePanel, tr("Amanhecer · início", "Dawn · start"), ScheduleProtocol.DAWN_SLOT);
        duskTimeButton = addCycleTimeRow(cyclePanel, tr("Anoitecer · fim", "Dusk · end"), ScheduleProtocol.DUSK_SLOT);
        cycleSummaryView = helperText("");
        cyclePanel.addView(cycleSummaryView, topMargin(8));
        cycleValidationView = helperText("");
        cycleValidationView.setTextColor(Color.rgb(160, 55, 43));
        cycleValidationView.setVisibility(android.view.View.GONE);
        cyclePanel.addView(cycleValidationView, topMargin(4));
        saveCycleButton = new Button(this);
        saveCycleButton.setText(tr("Salvar amanhecer e anoitecer", "Save dawn and dusk"));
        styleButton(saveCycleButton, true);
        saveCycleButton.setEnabled(false);
        saveCycleButton.setOnClickListener(v -> saveCycleWindow());
        cyclePanel.addView(saveCycleButton, topMargin(6));
        cycleActionFeedback = helperText("");
        cycleActionFeedback.setVisibility(android.view.View.GONE);
        cyclePanel.addView(cycleActionFeedback, topMargin(4));
        scheduleCard.addView(cyclePanel, topMargin(12));
        refreshCycleEditor();
    }

    private Button addCycleTimeRow(LinearLayout panel, String name, int slot) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(name), new LinearLayout.LayoutParams(0, -2, 1));
        Button button = new Button(this);
        styleButton(button, false);
        button.setEnabled(false);
        button.setOnClickListener(v -> chooseCycleTime(slot));
        row.addView(button, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(row, topMargin(8));
        return button;
    }

    private int minutesOf(SchedulePoint point) { return point.hour * 60 + point.minute; }

    private String formatTime(int minuteOfDay) {
        return twoDigits(minuteOfDay / 60) + ":" + twoDigits(minuteOfDay % 60);
    }

    private int savedCycleTime(int slot) {
        SchedulePoint point = savedSchedulePoints[slot];
        return point != null && point.enabled ? minutesOf(point) : -1;
    }

    private void loadCycleFromLamp() {
        SchedulePoint dawn = savedSchedulePoints[ScheduleProtocol.DAWN_SLOT];
        SchedulePoint dusk = savedSchedulePoints[ScheduleProtocol.DUSK_SLOT];
        dawnMinuteOfDay = dawn == null ? -1 : minutesOf(dawn);
        duskMinuteOfDay = dusk == null ? -1 : minutesOf(dusk);
        cycleDirty = false;
        refreshCycleEditor();
    }

    private void refreshCycleEditor() {
        if (dawnTimeButton == null || cycleSummaryView == null) return;
        dawnTimeButton.setText(dawnMinuteOfDay < 0 ? tr("Escolher horário", "Set time")
                : formatTime(dawnMinuteOfDay) + " · " + tr("Alterar", "Change"));
        duskTimeButton.setText(duskMinuteOfDay < 0 ? tr("Escolher horário", "Set time")
                : formatTime(duskMinuteOfDay) + " · " + tr("Alterar", "Change"));
        String summary = tr("Escolha os dois horários para definir o ciclo de luz.", "Choose both times to set the light cycle.");
        if (ScheduleProtocol.validWindow(dawnMinuteOfDay, duskMinuteOfDay)) {
            int duration = duskMinuteOfDay - dawnMinuteOfDay;
            summary = formatTime(dawnMinuteOfDay) + " → " + formatTime(duskMinuteOfDay)
                    + " · " + duration / 60 + "h " + twoDigits(duration % 60) + "m";
        }
        if (cycleDirty) summary += " · " + tr("Não salvo", "Unsaved");
        else if (scheduleDataLoaded && (savedCycleTime(ScheduleProtocol.DAWN_SLOT) < 0
                || savedCycleTime(ScheduleProtocol.DUSK_SLOT) < 0)) {
            summary += " · " + tr("Salve para ativar o ciclo", "Save to activate the cycle");
        }
        cycleSummaryView.setText(summary);
        String problem = cycleWindowProblem();
        cycleValidationView.setText(problem == null ? "" : problem);
        cycleValidationView.setVisibility(problem != null && (cycleDirty || scheduleDataLoaded)
                ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private String cycleWindowProblem() {
        if (dawnMinuteOfDay < 0 || duskMinuteOfDay < 0) {
            return tr("Escolha um horário para o amanhecer e outro para o anoitecer.", "Choose a time for dawn and a time for dusk.");
        }
        if (!ScheduleProtocol.validWindow(dawnMinuteOfDay, duskMinuteOfDay)) {
            return tr("O amanhecer deve acontecer antes do anoitecer, no mesmo dia.", "Dawn must be before dusk on the same day.");
        }
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) {
            SchedulePoint point = savedSchedulePoints[i];
            if (point != null && point.enabled && !ScheduleProtocol.insideWindow(minutesOf(point), dawnMinuteOfDay, duskMinuteOfDay)) {
                return tr("O horário salvo ", "The saved time ") + formatTime(minutesOf(point))
                        + tr(" fica fora deste ciclo. Ajuste ou remova essa mudança de cor antes de salvar os limites.",
                        " is outside this cycle. Adjust or remove that color change before saving the limits.");
            }
        }
        return null;
    }

    private void chooseCycleTime(int slot) {
        if (!connectedReady || !scheduleDataLoaded || cycleWritePending || scheduleWritePending) return;
        if (slot != ScheduleProtocol.DAWN_SLOT && slot != ScheduleProtocol.DUSK_SLOT) return;
        int time = slot == ScheduleProtocol.DAWN_SLOT ? dawnMinuteOfDay : duskMinuteOfDay;
        if (time < 0) time = slot == ScheduleProtocol.DAWN_SLOT ? 6 * 60 : 20 * 60;
        TimePickerDialog picker = new TimePickerDialog(this, (view, hour, minute) -> {
            if (!connectedReady || cycleWritePending || scheduleWritePending) return;
            if (slot == ScheduleProtocol.DAWN_SLOT) dawnMinuteOfDay = hour * 60 + minute;
            else duskMinuteOfDay = hour * 60 + minute;
            cycleDirty = true;
            cycleActionFeedback.setVisibility(android.view.View.GONE);
            refreshCycleEditor();
            if (lightGraph != null) lightGraph.invalidate();
        }, time / 60, time % 60, true);
        picker.setTitle(slot == ScheduleProtocol.DAWN_SLOT ? tr("Amanhecer · início do ciclo", "Dawn · cycle start")
                : tr("Anoitecer · fim do ciclo", "Dusk · cycle end"));
        picker.show();
    }

    private void saveCycleWindow() {
        if (!connectedReady || !scheduleDataLoaded || cycleWritePending || scheduleWritePending) return;
        String problem = cycleWindowProblem();
        if (problem != null) {
            showActionFeedback(cycleActionFeedback, problem, false);
            return;
        }
        SchedulePoint dawn = new SchedulePoint(ScheduleProtocol.DAWN_SLOT,
                dawnMinuteOfDay / 60, dawnMinuteOfDay % 60, true, new int[4], true);
        SchedulePoint dusk = new SchedulePoint(ScheduleProtocol.DUSK_SLOT,
                duskMinuteOfDay / 60, duskMinuteOfDay % 60, true, new int[4], true);
        // Expand an existing end first when moving a whole empty cycle later in the day.
        int oldDusk = savedCycleTime(ScheduleProtocol.DUSK_SLOT);
        SchedulePoint[] anchors = oldDusk >= 0 && dawnMinuteOfDay >= oldDusk
                ? new SchedulePoint[]{dusk, dawn} : new SchedulePoint[]{dawn, dusk};
        byte[][] frames = new byte[2][];
        Runnable[] acknowledged = new Runnable[2];
        for (int i = 0; i < anchors.length; i++) {
            SchedulePoint point = anchors[i];
            frames[i] = asciiFrame(ScheduleProtocol.writeCommand(point.slot, point.hour, point.minute, true, new int[4]), true);
            acknowledged[i] = () -> {
                // Track each accepted write even if the second command subsequently fails.
                savedSchedulePoints[point.slot] = point;
                schedulePoints[point.slot] = point;
                scheduleSlotStored[point.slot] = true;
            };
        }
        cycleWritePending = true;
        saveCycleButton.setText(tr("Salvando…", "Saving…"));
        setScheduleControlsEnabled(connectedReady);
        showActionPending(cycleActionFeedback, tr("Salvando amanhecer e anoitecer…", "Saving dawn and dusk…"));
        enqueueConfirmedWrites(frames, acknowledged, () -> {
            cycleWritePending = false;
            cycleDirty = false;
            saveCycleButton.setText(tr("Salvar amanhecer e anoitecer", "Save dawn and dusk"));
            refreshCycleEditor();
            refreshScheduleTimeline(true);
            setScheduleControlsEnabled(connectedReady);
            if (lightGraph != null) lightGraph.invalidate();
            showActionFeedback(cycleActionFeedback, tr("Ciclo salvo: ", "Cycle saved: ")
                    + formatTime(dawnMinuteOfDay) + " → " + formatTime(duskMinuteOfDay) + ".", true);
        }, () -> {
            cycleWritePending = false;
            cycleDirty = true;
            saveCycleButton.setText(tr("Salvar amanhecer e anoitecer", "Save dawn and dusk"));
            refreshCycleEditor();
            setScheduleControlsEnabled(connectedReady);
            showActionFeedback(cycleActionFeedback,
                    tr("Não foi possível concluir o salvamento dos dois horários. Verifique a conexão e tente novamente.",
                    "Could not finish saving both times. Check the connection and try again."), false);
        });
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
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) {
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
        scheduleCapacityView.setText(slots.size() + "/" + ScheduleProtocol.COLOR_POINTS + " · "
                + tr("mudanças de cor; amanhecer e anoitecer são ajustados acima",
                "color changes; dawn and dusk are adjusted above"));
        if (addScheduleButton != null) {
            boolean full = slots.size() == ScheduleProtocol.COLOR_POINTS;
            addScheduleButton.setText(full ? tr("Limite atingido", "Schedule full")
                    : tr("+ Adicionar horário", "+ Add time"));
            styleButton(addScheduleButton, !full);
            // Keep the full button tappable so it can explain how to free a position.
            addScheduleButton.setEnabled(connectedReady && scheduleDataLoaded && !scheduleWritePending && !cycleWritePending);
        }
    }

    private void addSchedulePoint() {
        if (!connectedReady || !scheduleDataLoaded || scheduleWritePending || cycleWritePending) return;
        int slot = -1;
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) if (schedulePoints[i] == null) { slot = i; break; }
        if (slot < 0) {
            showActionFeedback(scheduleActionFeedback,
                    tr("As 8 mudanças de cor estão ocupadas. Remova uma para adicionar outra. Amanhecer e anoitecer têm controles próprios acima.",
                    "All 8 color-change positions are occupied. Remove one to add another. Dawn and dusk have their own controls above."), false);
            return;
        }
        int minuteOfDay = nextAvailableScheduleTime();
        if (minuteOfDay < 0) {
            showActionFeedback(scheduleActionFeedback,
                    tr("Não há um minuto livre neste ciclo. Amplie o intervalo entre amanhecer e anoitecer.",
                    "There is no free minute in this cycle. Extend the interval between dawn and dusk."), false);
            return;
        }
        int[] levels = {0, 0, 0, 0};
        ArrayList<SchedulePoint> existing = editableSchedulePoints();
        if (!existing.isEmpty()) {
            Collections.sort(existing, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
            levels = existing.get(existing.size() - 1).levels.clone();
        }
        scheduleSlotStored[slot] = false;
        schedulePoints[slot] = new SchedulePoint(slot, minuteOfDay / 60, minuteOfDay % 60,
                true, levels, false);
        scheduleActionFeedback.setVisibility(android.view.View.GONE);
        selectSchedulePoint(slot);
        setStatus(tr("Novo horário adicionado à prévia. Ajuste e salve para enviá-lo à lâmpada.",
                "New time added to the preview. Adjust it and save to send it to the lamp."));
    }

    private int nextAvailableScheduleTime() {
        int dawn = savedCycleTime(ScheduleProtocol.DAWN_SLOT);
        int dusk = savedCycleTime(ScheduleProtocol.DUSK_SLOT);
        ArrayList<SchedulePoint> existing = editableSchedulePoints();
        int[] occupied = new int[existing.size()];
        for (int i = 0; i < existing.size(); i++) occupied[i] = minutesOf(existing.get(i));
        if (ScheduleProtocol.validWindow(dawn, dusk)) {
            return ScheduleProtocol.availableColorTime(dawn, dusk, occupied);
        }
        int candidate = existing.isEmpty() ? 8 * 60 : 12 * 60;
        for (int attempt = 0; attempt < 24 * 60; attempt++) {
            if (!hasScheduleConflict(-1, candidate / 60, candidate % 60)) return candidate;
            candidate = (candidate + 1) % (24 * 60);
        }
        return -1;
    }

    private ArrayList<SchedulePoint> editableSchedulePoints() {
        ArrayList<SchedulePoint> points = new ArrayList<>();
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) if (schedulePoints[i] != null) points.add(schedulePoints[i]);
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
        if (scheduleBars[0] == null || scheduleTimeButton == null || slot < 0 || slot >= ScheduleProtocol.COLOR_POINTS) return;
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
        setScheduleControlsEnabled(connectedReady);
        updateConflictMessage(slot);
        refreshScheduleTimeline(false);
        if (lightGraph != null) lightGraph.invalidate();
    }

    private void selectSchedulePoint(int slot) {
        if (scheduleWritePending || cycleWritePending || slot < 0 || slot >= ScheduleProtocol.COLOR_POINTS) return;
        activeScheduleSlot = slot;
        loadSchedulePoint(slot);
        refreshScheduleTimeline(true);
        if (lightGraph != null) lightGraph.invalidate();
    }

    private void updateScheduleGraphPreview() {
        if (lightGraph == null || activeScheduleSlot < 0 || activeScheduleSlot >= ScheduleProtocol.COLOR_POINTS) return;
        int slot = activeScheduleSlot;
        int[] levels = new int[scheduleBars.length];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = slot < ScheduleProtocol.COLOR_POINTS ? scheduleBars[i].getProgress() : 0;
        }
        schedulePoints[slot] = new SchedulePoint(slot, scheduleHour, scheduleMinute,
                scheduleEnabledSwitch.isChecked(), levels, false);
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
        programmingTabSelected = true;
        setConnectedContentVisible(false);
        scheduleDataLoaded = false;
        activeScheduleSlot = -1;
        for (int i = 0; i < schedulePoints.length; i++) {
            schedulePoints[i] = null;
            savedSchedulePoints[i] = null;
            scheduleSlotStored[i] = false;
        }
        dawnMinuteOfDay = -1;
        duskMinuteOfDay = -1;
        cycleDirty = false;
        refreshCycleEditor();
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
            if (bluetoothGatt != gatt) return;
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
                cancelPendingWrites();
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
            handshakeQueued = true;
            enqueueWrite(asciiFrame(HANDSHAKE, false));
            runOnUiThread(() -> setStatus(tr("Serviço encontrado. Confirmando protocolo…", "Service found. Confirming protocol…")));
        }

        @Override public void onCharacteristicWrite(BluetoothGatt bluetoothGatt,
                                                     BluetoothGattCharacteristic characteristic, int status) {
            if (bluetoothGatt != gatt || !WRITE_UUID.equals(characteristic.getUuid()) || inFlightWrite == null) return;
            String completedCommand = new String(inFlightWrite.bytes, StandardCharsets.US_ASCII);
            if (status == BluetoothGatt.GATT_SUCCESS && handshakeQueued && completedCommand.startsWith(HANDSHAKE)) {
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
            } else if (status == BluetoothGatt.GATT_SUCCESS && completedCommand.startsWith("app_pwm_on") && pendingAutoMode != null) {
                autoMode = pendingAutoMode;
                pendingAutoMode = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    setStatus(autoMode ? tr("Modo automático ativado.", "Automatic mode enabled.") : tr("Modo manual ativado. Agora envie os níveis.", "Manual mode enabled. Send the levels now."));
                });
            } else if (status == BluetoothGatt.GATT_SUCCESS && completedCommand.startsWith("app_pwm_on") && pendingPower != null) {
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
            finishWrite(status == BluetoothGatt.GATT_SUCCESS);
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
        if (currentGatt == null || alarm == null || alarmReadsCompleted >= ScheduleProtocol.TOTAL_POINTS) {
            updateScheduleGraph();
            return;
        }
        try {
            if (!currentGatt.readCharacteristic(alarm)) {
                alarmReadsCompleted = ScheduleProtocol.TOTAL_POINTS;
                updateScheduleGraph();
            }
        } catch (SecurityException exception) {
            alarmReadsCompleted = ScheduleProtocol.TOTAL_POINTS;
            updateScheduleGraph();
        }
    }

    private void handleAlarmRead(byte[] value, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.length >= 8) {
            StringBuilder hex = new StringBuilder(value.length * 2);
            for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 0xff));
            String record = hex.toString();
            if (record.length() >= 16 && !ScheduleProtocol.isClearedRecord(value)) {
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
                        if (slot >= ScheduleProtocol.COLOR_POINTS) levels = new int[4];
                        scheduleSlotStored[slot] = true;
                        schedulePoints[slot] = new SchedulePoint(slot, hour, minute, enabled, levels, true);
                        savedSchedulePoints[slot] = schedulePoints[slot];
                    } catch (NumberFormatException ignored) { }
                }
            }
        }
        alarmReadsCompleted++;
        if (alarmReadsCompleted >= ScheduleProtocol.TOTAL_POINTS) updateScheduleGraph();
        else handler.postDelayed(this::readNextAlarmRecord, 120);
    }

    private void updateScheduleGraph() {
        scheduleDataLoaded = true;
        runOnUiThread(() -> {
            loadCycleFromLamp();
            if (lightGraph != null) lightGraph.invalidate();
            if (activeScheduleSlot < 0 || activeScheduleSlot >= ScheduleProtocol.COLOR_POINTS || schedulePoints[activeScheduleSlot] == null) {
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
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) {
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
        if (!connectedReady || weekCharacteristic == null || weekWritePending) return;
        StringBuilder command = new StringBuilder("app_week_data");
        for (CheckBox day : weekDays) command.append(day.isChecked() ? '1' : '0');
        weekWritePending = true;
        saveWeekButton.setEnabled(false);
        saveWeekButton.setText(tr("Salvando…", "Saving…"));
        for (CheckBox day : weekDays) day.setEnabled(false);
        showActionPending(weekActionFeedback, tr("Enviando repetição semanal…", "Sending weekly repeat…"));
        // Both writes must succeed before the weekly routine is reported as saved.
        enqueueConfirmedWrites(new byte[][] {asciiFrame(command.toString(), true),
                asciiFrame("app_set_pwm_fre750", true)}, () -> {
            finishWeekSave();
            showActionFeedback(weekActionFeedback, tr("Repetição semanal salva.", "Weekly repeat saved."), true);
        }, () -> {
            finishWeekSave();
            showActionFeedback(weekActionFeedback,
                    tr("Não foi possível salvar a repetição. Verifique a conexão e tente novamente.",
                    "Could not save weekly repeat. Check the connection and try again."), false);
        });
    }

    private void finishWeekSave() {
        weekWritePending = false;
        boolean available = connectedReady && weekCharacteristic != null;
        saveWeekButton.setText(tr("Salvar repetição", "Save weekly repeat"));
        saveWeekButton.setEnabled(available);
        for (CheckBox day : weekDays) day.setEnabled(available);
    }

    private void syncLampClock() {
        if (clockWritePending) return;
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
        clockWritePending = true;
        clockButton.setEnabled(false);
        clockButton.setText(tr("Sincronizando…", "Syncing…"));
        showActionPending(clockActionFeedback, tr("Sincronizando relógio…", "Syncing clock…"));
        enqueueConfirmedWrites(new byte[][] {asciiFrame(command, true)}, () -> {
            finishClockSync();
            showActionFeedback(clockActionFeedback, tr("Relógio sincronizado.", "Clock synced."), true);
        }, () -> {
            finishClockSync();
            showActionFeedback(clockActionFeedback,
                    tr("Não foi possível sincronizar o relógio. Tente novamente.", "Could not sync the clock. Try again."), false);
        });
    }

    private void finishClockSync() {
        clockWritePending = false;
        clockButton.setText(tr("Sincronizar relógio", "Sync clock"));
        clockButton.setEnabled(connectedReady);
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
        int slot = activeScheduleSlot;
        if (!connectedReady || scheduleWritePending || cycleWritePending || slot < 0 || slot >= ScheduleProtocol.COLOR_POINTS) return;
        if (scheduleEnabledSwitch.isChecked() && hasScheduleConflict(slot, scheduleHour, scheduleMinute)) {
            showActionFeedback(scheduleActionFeedback,
                    tr("Esse horário já está usado por outro ponto ativo. Escolha outro minuto antes de salvar.",
                    "Another active point already uses this time. Choose a different minute before saving."), false);
            return;
        }
        if (scheduleEnabledSwitch.isChecked()) {
            int dawn = savedCycleTime(ScheduleProtocol.DAWN_SLOT);
            int dusk = savedCycleTime(ScheduleProtocol.DUSK_SLOT);
            if (!ScheduleProtocol.validWindow(dawn, dusk)) {
                showActionFeedback(scheduleActionFeedback,
                        tr("Defina e salve o amanhecer e o anoitecer antes de ativar uma mudança de cor.",
                        "Set and save dawn and dusk before enabling a color change."), false);
                return;
            }
            if (!ScheduleProtocol.insideWindow(scheduleHour * 60 + scheduleMinute, dawn, dusk)) {
                showActionFeedback(scheduleActionFeedback,
                        tr("Escolha um horário entre ", "Choose a time between ") + formatTime(dawn) + " "
                        + tr("e ", "and ") + formatTime(dusk) + tr(". Para ampliar o ciclo, salve os novos limites acima.",
                        ". To extend the cycle, save the new limits above."), false);
                return;
            }
        }
        int[] levels = new int[scheduleBars.length];
        for (int i = 0; i < levels.length; i++) levels[i] = scheduleBars[i].getProgress();
        SchedulePoint point = new SchedulePoint(slot, scheduleHour, scheduleMinute,
                scheduleEnabledSwitch.isChecked(), levels, true);
        String command = ScheduleProtocol.writeCommand(slot, point.hour, point.minute, point.enabled, levels);
        scheduleWritePending = true;
        saveScheduleButton.setText(tr("Salvando…", "Saving…"));
        setScheduleControlsEnabled(connectedReady);
        showActionPending(scheduleActionFeedback, tr("Enviando horário…", "Sending schedule point…"));
        enqueueConfirmedWrites(new byte[][] {asciiFrame(command, true)}, () -> {
            scheduleWritePending = false;
            scheduleSlotStored[slot] = true;
            savedSchedulePoints[slot] = point;
            schedulePoints[slot] = point;
            refreshCycleEditor();
            selectSchedulePoint(slot);
            saveScheduleButton.setText(tr("Salvar horário", "Save time"));
            showActionFeedback(scheduleActionFeedback, tr("Horário salvo: ", "Time saved: ")
                    + twoDigits(point.hour) + ":" + twoDigits(point.minute) + ".", true);
        }, () -> {
            scheduleWritePending = false;
            saveScheduleButton.setText(tr("Salvar horário", "Save time"));
            setScheduleControlsEnabled(connectedReady);
            showActionFeedback(scheduleActionFeedback,
                    tr("Não foi possível salvar o horário. Verifique a conexão e tente novamente.",
                    "Could not save the time. Check the connection and try again."), false);
        });
    }

    private void removeSchedulePoint() {
        int slot = activeScheduleSlot;
        if (!connectedReady || scheduleWritePending || cycleWritePending || slot < 0 || slot >= ScheduleProtocol.COLOR_POINTS) return;
        SchedulePoint point = schedulePoints[slot];
        if (point == null) return;
        // A draft that has never reached the lamp only needs a local removal.
        if (!scheduleSlotStored[slot]) {
            finishScheduleRemoval(slot);
            return;
        }
        scheduleWritePending = true;
        removeScheduleButton.setText(tr("Removendo…", "Removing…"));
        setScheduleControlsEnabled(connectedReady);
        showActionPending(scheduleActionFeedback, tr("Removendo horário da luminária…", "Removing time from the lamp…"));
        enqueueConfirmedWrites(new byte[][] {asciiFrame(ScheduleProtocol.clearCommand(slot), true)},
                () -> finishScheduleRemoval(slot), () -> {
                    scheduleWritePending = false;
                    removeScheduleButton.setText(tr("Remover horário", "Remove time"));
                    setScheduleControlsEnabled(connectedReady);
                    showActionFeedback(scheduleActionFeedback,
                            tr("Não foi possível remover o horário. Verifique a conexão e tente novamente.",
                            "Could not remove the time. Check the connection and try again."), false);
                });
    }

    private void finishScheduleRemoval(int slot) {
        scheduleWritePending = false;
        schedulePoints[slot] = null;
        savedSchedulePoints[slot] = null;
        scheduleSlotStored[slot] = false;
        activeScheduleSlot = firstSchedulePointByTime();
        refreshCycleEditor();
        removeScheduleButton.setText(tr("Remover horário", "Remove time"));
        if (activeScheduleSlot >= 0) loadSchedulePoint(activeScheduleSlot);
        else scheduleEditorPanel.setVisibility(android.view.View.GONE);
        refreshScheduleTimeline(true);
        setScheduleControlsEnabled(connectedReady);
        if (lightGraph != null) lightGraph.invalidate();
        showActionFeedback(scheduleActionFeedback, tr("Horário removido.", "Time removed."), true);
    }

    private void showActionPending(TextView feedback, String message) {
        feedback.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setText(message);
        feedback.setTextColor(Color.rgb(65, 83, 88));
        feedback.setVisibility(android.view.View.VISIBLE);
    }

    private void showActionFeedback(TextView feedback, String message, boolean success) {
        feedback.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setText((success ? "✓ " : "") + message);
        feedback.setTextColor(success ? Color.rgb(20, 110, 80) : Color.rgb(160, 55, 43));
        feedback.setVisibility(android.view.View.VISIBLE);
        setStatus(message);
        Toast.makeText(this, message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
    }

    private void setScheduleControlsEnabled(boolean enabled) {
        boolean canEdit = enabled && scheduleDataLoaded && activeScheduleSlot >= 0
                && activeScheduleSlot < ScheduleProtocol.COLOR_POINTS && !scheduleWritePending && !cycleWritePending;
        if (addScheduleButton != null) addScheduleButton.setEnabled(enabled && scheduleDataLoaded && !scheduleWritePending && !cycleWritePending);
        if (scheduleTimeButton != null) scheduleTimeButton.setEnabled(canEdit);
        if (scheduleEnabledSwitch != null) scheduleEnabledSwitch.setEnabled(canEdit);
        for (SeekBar bar : scheduleBars) {
            if (bar != null) bar.setEnabled(canEdit && activeScheduleSlot < ScheduleProtocol.COLOR_POINTS);
        }
        if (saveScheduleButton != null) saveScheduleButton.setEnabled(canEdit);
        if (removeScheduleButton != null) removeScheduleButton.setEnabled(canEdit);
        boolean canSetCycle = enabled && scheduleDataLoaded && !scheduleWritePending && !cycleWritePending;
        if (dawnTimeButton != null) dawnTimeButton.setEnabled(canSetCycle);
        if (duskTimeButton != null) duskTimeButton.setEnabled(canSetCycle);
        if (saveCycleButton != null) saveCycleButton.setEnabled(canSetCycle);
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

    private static final class WriteBatch {
        int remaining;
        boolean finished;
        final Runnable onSuccess;
        final Runnable onFailure;

        WriteBatch(int count, Runnable onSuccess, Runnable onFailure) {
            remaining = count;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }
    }

    private static final class WriteRequest {
        final byte[] bytes;
        final WriteBatch batch;
        final Runnable onAcknowledged;

        WriteRequest(byte[] bytes, WriteBatch batch) { this(bytes, batch, null); }

        WriteRequest(byte[] bytes, WriteBatch batch, Runnable onAcknowledged) {
            this.bytes = bytes;
            this.batch = batch;
            this.onAcknowledged = onAcknowledged;
        }
    }

    private synchronized void enqueueWrite(byte[] bytes) {
        writeQueue.add(new WriteRequest(bytes, null));
        writeNext();
    }

    private synchronized void enqueueConfirmedWrites(byte[][] frames, Runnable onSuccess, Runnable onFailure) {
        enqueueConfirmedWrites(frames, new Runnable[frames.length], onSuccess, onFailure);
    }

    private synchronized void enqueueConfirmedWrites(byte[][] frames, Runnable[] onAcknowledged,
                                                     Runnable onSuccess, Runnable onFailure) {
        if (gatt == null || writeCharacteristic == null) {
            runOnUiThread(onFailure);
            return;
        }
        WriteBatch batch = new WriteBatch(frames.length, onSuccess, onFailure);
        for (int i = 0; i < frames.length; i++) writeQueue.add(new WriteRequest(frames[i], batch, onAcknowledged[i]));
        writeNext();
    }

    private void completeBatch(WriteBatch batch, boolean success) {
        if (batch == null || batch.finished) return;
        if (!success) {
            batch.finished = true;
            runOnUiThread(batch.onFailure);
        } else if (--batch.remaining == 0) {
            batch.finished = true;
            runOnUiThread(batch.onSuccess);
        }
    }

    private synchronized void finishWrite(boolean success) {
        WriteRequest finished = inFlightWrite;
        inFlightWrite = null;
        writeInFlight = false;
        if (writeTimeout != null) handler.removeCallbacks(writeTimeout);
        writeTimeout = null;
        if (finished != null) {
            if (success && finished.onAcknowledged != null) runOnUiThread(finished.onAcknowledged);
            completeBatch(finished.batch, success);
        }
        writeNext();
    }

    private synchronized void cancelPendingWrites() {
        if (writeTimeout != null) handler.removeCallbacks(writeTimeout);
        writeTimeout = null;
        if (inFlightWrite != null) completeBatch(inFlightWrite.batch, false);
        inFlightWrite = null;
        writeInFlight = false;
        while (!writeQueue.isEmpty()) completeBatch(writeQueue.poll().batch, false);
    }

    private synchronized void writeNext() {
        if (writeInFlight || gatt == null || writeCharacteristic == null) return;
        WriteRequest request;
        do {
            request = writeQueue.poll();
        } while (request != null && request.batch != null && request.batch.finished);
        if (request == null) return;
        final WriteRequest current = request;
        inFlightWrite = current;
        int properties = writeCharacteristic.getProperties();
        int writeType = (properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        writeInFlight = true;
        writeTimeout = () -> {
            if (inFlightWrite != current) return;
            // Close the connection so a late callback cannot confirm a newer command.
            disconnect();
            setStatus(tr("A luminária não confirmou o envio. Reconecte e tente novamente.",
                    "The lamp did not confirm the write. Reconnect and try again."));
        };
        handler.postDelayed(writeTimeout, 10000);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                int result = gatt.writeCharacteristic(writeCharacteristic, current.bytes, writeType);
                if (result != BluetoothStatusCodes.SUCCESS) {
                    runOnUiThread(() -> setStatus(tr("O Android recusou o comando BLE (", "Android rejected the BLE command (") + result + ")."));
                    finishWrite(false);
                }
            } else {
                writeCharacteristic.setWriteType(writeType);
                writeCharacteristic.setValue(current.bytes);
                if (!gatt.writeCharacteristic(writeCharacteristic)) {
                    runOnUiThread(() -> setStatus(tr("O Android não iniciou o envio BLE.", "Android did not start the BLE write.")));
                    finishWrite(false);
                }
            }
        } catch (SecurityException exception) {
            runOnUiThread(() -> setStatus(tr("Sem permissão para escrever na lâmpada.", "Permission to write to the lamp is missing.")));
            finishWrite(false);
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
        connectedReady = false;
        cancelPendingWrites();
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
            if (ScheduleProtocol.validWindow(dawnMinuteOfDay, duskMinuteOfDay)) {
                paint.setColor(Color.rgb(242, 248, 235));
                float dawnX = left + (right - left) * dawnMinuteOfDay / 1440f;
                float duskX = left + (right - left) * duskMinuteOfDay / 1440f;
                canvas.drawRect(dawnX, top, duskX, bottom, paint);
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

            drawCycleMarker(canvas, dawnMinuteOfDay, left, right, top, bottom,
                    tr("Amanhecer", "Dawn"), Color.rgb(161, 112, 39), dp(14));
            drawCycleMarker(canvas, duskMinuteOfDay, left, right, top, bottom,
                    tr("Anoitecer", "Dusk"), Color.rgb(83, 98, 173), dp(28));
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
                    if (i == 0) {
                        if (point.slot == ScheduleProtocol.DAWN_SLOT) {
                            path.moveTo(left, bottom);
                            path.lineTo(x, y);
                        } else path.moveTo(x, y);
                    } else path.lineTo(x, y);
                }
                if (points.get(points.size() - 1).slot == ScheduleProtocol.DUSK_SLOT) path.lineTo(right, bottom);
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

        private void drawCycleMarker(Canvas canvas, int minute, float left, float right,
                                     float top, float bottom, String name, int color, float labelOffset) {
            if (minute < 0) return;
            float x = left + (right - left) * minute / 1440f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color);
            paint.setStrokeWidth(dp(1.5f));
            canvas.drawLine(x, top, x, bottom, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(10));
            canvas.drawText(name, Math.max(left + dp(3), Math.min(x + dp(4), right - dp(62))), top + labelOffset, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float left = dp(36), right = getWidth() - dp(10);
            float top = dp(24), bottom = getHeight() - dp(30);
            if (event.getY() >= top && event.getY() <= bottom) {
                float dawnX = left + (right - left) * dawnMinuteOfDay / 1440f;
                float duskX = left + (right - left) * duskMinuteOfDay / 1440f;
                if (dawnMinuteOfDay >= 0 && Math.abs(event.getX() - dawnX) < dp(12)) {
                    chooseCycleTime(ScheduleProtocol.DAWN_SLOT);
                    performClick();
                    return true;
                }
                if (duskMinuteOfDay >= 0 && Math.abs(event.getX() - duskX) < dp(12)) {
                    chooseCycleTime(ScheduleProtocol.DUSK_SLOT);
                    performClick();
                    return true;
                }
            }
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
                showProgrammingTab(true);
                if (selectedSlot < ScheduleProtocol.COLOR_POINTS) selectSchedulePoint(selectedSlot);
                else chooseCycleTime(selectedSlot);
                performClick();
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
        for (int i = 0; i < ScheduleProtocol.COLOR_POINTS; i++) {
            SchedulePoint point = schedulePoints[i];
            if (point != null && point.enabled) points.add(point);
        }
        SchedulePoint dawn = savedSchedulePoints[ScheduleProtocol.DAWN_SLOT];
        SchedulePoint dusk = savedSchedulePoints[ScheduleProtocol.DUSK_SLOT];
        if (dawnMinuteOfDay >= 0 && (cycleDirty || dawn != null && dawn.enabled)) {
            points.add(new SchedulePoint(ScheduleProtocol.DAWN_SLOT, dawnMinuteOfDay / 60,
                    dawnMinuteOfDay % 60, true, new int[4], !cycleDirty));
        }
        if (duskMinuteOfDay >= 0 && (cycleDirty || dusk != null && dusk.enabled)) {
            points.add(new SchedulePoint(ScheduleProtocol.DUSK_SLOT, duskMinuteOfDay / 60,
                    duskMinuteOfDay % 60, true, new int[4], !cycleDirty));
        }
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

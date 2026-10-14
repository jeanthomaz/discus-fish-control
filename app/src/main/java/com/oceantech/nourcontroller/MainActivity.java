package com.oceantech.nourcontroller;

import android.Manifest;
import android.app.Activity;
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
    private static final String[] CHANNELS = {"Branco", "Azul", "Verde", "Vermelho"};

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
    private TextView statusView;
    private Button scanButton;
    private Button sendButton;
    private Button modeButton;
    private Button powerButton;
    private Button saveWeekButton;
    private Button clockButton;
    private Button controlTabButton;
    private Button programTabButton;
    private Button scheduleTimeButton;
    private Button saveScheduleButton;
    private Spinner scheduleSpinner;
    private ArrayAdapter<String> scheduleAdapter;
    private final int[] scheduleSpinnerSlots = new int[8];
    private android.widget.Switch scheduleEnabledSwitch;
    private LinearLayout scheduleEditorPanel;
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
    private int activeScheduleSlot;
    private boolean programmingTabSelected;
    private boolean loadingSchedulePoint;
    private boolean scheduleDataLoaded;
    private boolean refreshingScheduleOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        buildScreen();
        configureSystemBars();
        setStatus("Escolha o modelo e busque sua luminária OceanTech.");
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
        TextView title = label("Acará Controle");
        title.setTextSize(24);
        TextView subtitle = new TextView(this);
        subtitle.setText("Controle da sua luminária");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(91, 111, 115));
        heading.addView(title);
        heading.addView(subtitle, topMargin(2));
        header.addView(heading);
        root.addView(header, topMargin(4));

        LinearLayout connectionCard = card();
        root.addView(connectionCard, topMargin(20));
        connectionCard.addView(sectionTitle("Conexão"));
        TextView modelLabel = helperText("Modelo da lâmpada");
        connectionCard.addView(modelLabel, topMargin(12));
        Spinner modelSpinner = new Spinner(this);
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, MODELS);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner.setAdapter(modelAdapter);
        modelSpinner.setSelection(2);
        connectionCard.addView(modelSpinner, topMargin(2));
        connectionCard.addView(helperText("A busca mostra luminárias identificadas como TO-L-XXXX."), topMargin(2));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTextColor(Color.rgb(35, 77, 78));
        statusView.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusView.setBackground(rounded(Color.rgb(232, 244, 241), Color.TRANSPARENT, 12));
        connectionCard.addView(statusView, topMargin(14));

        scanButton = new Button(this);
        scanButton.setText("Buscar luminária");
        styleButton(scanButton, true);
        scanButton.setOnClickListener(v -> startScanFlow());
        connectionCard.addView(scanButton, topMargin(10));

        devicesLabel = helperText("Dispositivos próximos");
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
        controlTabButton.setText("Controle");
        styleButton(controlTabButton, true);
        controlTabButton.setOnClickListener(v -> showProgrammingTab(false));
        programTabButton = new Button(this);
        programTabButton.setText("Programação");
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
        controlsCard.addView(sectionTitle("Luminária"));
        modeButton = new Button(this);
        modeButton.setText("Aguardando leitura da luminária");
        modeButton.setEnabled(false);
        modeButton.setOnClickListener(v -> toggleMode());
        styleButton(modeButton, false);
        controlsCard.addView(modeButton, topMargin(12));

        powerButton = new Button(this);
        powerButton.setText("Ligar ou desligar");
        powerButton.setEnabled(false);
        powerButton.setOnClickListener(v -> togglePower());
        styleButton(powerButton, false);
        controlsCard.addView(powerButton, topMargin(4));

        controlsCard.addView(sectionTitle("Canais de cor"), topMargin(20));
        TextView channelHint = helperText("Ajuste os níveis e envie no modo manual.");
        controlsCard.addView(channelHint, topMargin(2));
        for (int i = 0; i < CHANNELS.length; i++) addChannelControl(i);

        sendButton = new Button(this);
        sendButton.setText("Aplicar níveis");
        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> sendChannelLevels());
        styleButton(sendButton, true);
        controlsCard.addView(sendButton, topMargin(8));

        Button disconnectButton = new Button(this);
        disconnectButton.setText("Desconectar");
        disconnectButton.setOnClickListener(v -> disconnect());
        styleButton(disconnectButton, false);
        controlsCard.addView(disconnectButton, topMargin(4));

        programmingPanel = new LinearLayout(this);
        programmingPanel.setOrientation(LinearLayout.VERTICAL);
        programmingPanel.setVisibility(android.view.View.GONE);
        root.addView(programmingPanel, topMargin(14));

        weeklyCard = card();
        weeklyCard.addView(sectionTitle("Repetição semanal"));
        weeklyCard.addView(helperText("Escolha em quais dias a rotina se repete."), topMargin(2));
        String[] dayNames = {"Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira",
                "Quinta-feira", "Sexta-feira", "Sábado"};
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
        saveWeekButton.setText("Salvar repetição");
        saveWeekButton.setEnabled(false);
        saveWeekButton.setOnClickListener(v -> saveWeekDays());
        styleButton(saveWeekButton, true);
        weeklyCard.addView(saveWeekButton, topMargin(8));

        clockButton = new Button(this);
        clockButton.setText("Sincronizar relógio");
        clockButton.setEnabled(false);
        clockButton.setOnClickListener(v -> syncLampClock());
        styleButton(clockButton, false);
        weeklyCard.addView(clockButton, topMargin(4));

        scheduleCard = card();
        programmingPanel.addView(scheduleCard, topMargin(14));
        scheduleCard.addView(sectionTitle("Programação por horário"));
        TextView scheduleHint = new TextView(this);
        scheduleHint.setText("Cada ponto é uma mudança de intensidade. As linhas mostram como as cores variam ao longo do dia.");
        scheduleHint.setTextSize(13);
        scheduleHint.setTextColor(Color.rgb(65, 83, 88));
        scheduleCard.addView(scheduleHint, topMargin(3));
        lightGraph = new LightGraphView(this);
        scheduleCard.addView(lightGraph, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230)));
        scheduleCard.addView(helperText("Toque em uma mudança no gráfico para editar. Você também pode escolher o horário abaixo."), topMargin(8));
        scheduleCard.addView(sectionTitle("Horário para editar"), topMargin(12));
        scheduleSpinner = new Spinner(this);
        scheduleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        scheduleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        scheduleSpinner.setAdapter(scheduleAdapter);
        scheduleSpinner.setEnabled(false);
        scheduleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                                int position, long id) {
                if (!refreshingScheduleOptions && position >= 0 && position < 8) {
                    selectSchedulePoint(scheduleSpinnerSlots[position]);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        scheduleCard.addView(scheduleSpinner, topMargin(2));
        scheduleEditTitleView = sectionTitle("Configurar horário");
        scheduleEditorPanel = card();
        scheduleEditorPanel.setBackground(rounded(Color.rgb(247, 250, 250), Color.rgb(232, 239, 239), 14));
        scheduleEditorPanel.addView(scheduleEditTitleView);
        scheduleEditorPanel.addView(helperText("Edite um horário por vez, como na programação da luminária."), topMargin(2));
        scheduleConflictView = helperText("");
        scheduleConflictView.setTextColor(Color.rgb(166, 57, 43));
        scheduleConflictView.setVisibility(android.view.View.GONE);
        scheduleEditorPanel.addView(scheduleConflictView, topMargin(4));
        scheduleTimeButton = new Button(this);
        scheduleTimeButton.setText("Escolher horário · 12:00");
        scheduleTimeButton.setEnabled(false);
        scheduleTimeButton.setOnClickListener(v -> chooseScheduleTime());
        styleButton(scheduleTimeButton, false);
        scheduleEditorPanel.addView(scheduleTimeButton, topMargin(8));
        scheduleEnabledSwitch = new android.widget.Switch(this);
        scheduleEnabledSwitch.setText("Ativar mudança de luz neste horário");
        scheduleEnabledSwitch.setChecked(true);
        scheduleEnabledSwitch.setEnabled(false);
        scheduleEnabledSwitch.setButtonTintList(ColorStateList.valueOf(Color.rgb(8, 127, 140)));
        scheduleEnabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!loadingSchedulePoint) updateScheduleGraphPreview();
        });
        scheduleEditorPanel.addView(scheduleEnabledSwitch, topMargin(4));
        scheduleEditorPanel.addView(helperText("Intensidade das cores"), topMargin(8));
        for (int i = 0; i < CHANNELS.length; i++) addScheduleChannelControl(i);
        saveScheduleButton = new Button(this);
        saveScheduleButton.setText("Salvar horário");
        saveScheduleButton.setEnabled(false);
        saveScheduleButton.setOnClickListener(v -> saveSchedule());
        styleButton(saveScheduleButton, true);
        scheduleEditorPanel.addView(saveScheduleButton, topMargin(8));
        scheduleCard.addView(scheduleEditorPanel, topMargin(12));
        programmingPanel.addView(weeklyCard, topMargin(14));

        TextView note = new TextView(this);
        note.setText("Os comandos e horários estão sendo adaptados do protocolo do app original.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(102, 119, 122));
        root.addView(note, topMargin(14));
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
            if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
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

    private void addChannelControl(int index) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, dp(8), 0, dp(4));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(CHANNELS[index]);
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
        TextView name = helperText(CHANNELS[index]);
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
            scheduleHour = 12;
            scheduleMinute = 0;
            scheduleEnabledSwitch.setChecked(false);
            for (SeekBar bar : scheduleBars) bar.setProgress(0);
        } else {
            scheduleHour = point.hour;
            scheduleMinute = point.minute;
            scheduleEnabledSwitch.setChecked(point.enabled);
            for (int i = 0; i < scheduleBars.length; i++) scheduleBars[i].setProgress(point.levels[i]);
        }
        scheduleEditTitleView.setText(point == null ? "Novo horário" : "Editar horário · "
                + twoDigits(point.hour) + ":" + twoDigits(point.minute));
        scheduleTimeButton.setText("Escolher horário · " + twoDigits(scheduleHour) + ":" + twoDigits(scheduleMinute));
        loadingSchedulePoint = false;
        updateConflictMessage(slot);
        refreshScheduleSlotLabels();
        if (lightGraph != null) lightGraph.invalidate();
    }

    private void selectSchedulePoint(int slot) {
        activeScheduleSlot = slot;
        loadSchedulePoint(slot);
    }

    private void updateScheduleGraphPreview() {
        if (lightGraph == null || activeScheduleSlot < 0 || activeScheduleSlot >= 8) return;
        int slot = activeScheduleSlot;
        int[] levels = new int[scheduleBars.length];
        for (int i = 0; i < levels.length; i++) levels[i] = scheduleBars[i].getProgress();
        schedulePoints[slot] = new SchedulePoint(slot, scheduleHour, scheduleMinute,
                scheduleEnabledSwitch.isChecked(), levels);
        updateConflictMessage(slot);
        refreshScheduleSlotLabels();
        lightGraph.invalidate();
    }

    private boolean hasScheduleConflict(int slot, int hour, int minute) {
        for (int i = 0; i < 8; i++) {
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
                ? "Já existe outro ponto ativo neste horário. Escolha outro horário para evitar sobreposição."
                : "");
    }

    private void startScanFlow() {
        if (bluetoothAdapter == null) {
            setStatus("Este aparelho não oferece Bluetooth.");
            return;
        }
        if (!hasBlePermissions()) {
            requestBlePermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Ative o Bluetooth para pesquisar a lâmpada.");
            startActivityForResult(new android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            setStatus("Não foi possível iniciar o scanner BLE.");
            return;
        }
        foundDevices.clear();
        devicesContainer.removeAllViews();
        devicesLabel.setVisibility(android.view.View.GONE);
        scanning = true;
        scanButton.setEnabled(false);
        scanButton.setText("Buscando luminária…");
        setStatus("Buscando dispositivos próximos por 12 segundos…");
        try {
            scanner.startScan(scanCallback);
            handler.postDelayed(this::stopScan, 12000);
        } catch (SecurityException exception) {
            scanning = false;
            scanButton.setEnabled(true);
            scanButton.setText("Buscar luminária");
            setStatus("Permissão Bluetooth não concedida.");
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
                scanButton.setText("Buscar luminária");
                setStatus("A busca BLE falhou (código " + errorCode + "). Tente novamente.");
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
        setStatus("Encontrados: " + foundDevices.size() + ". Toque no dispositivo da lâmpada.");
    }

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try {
            if (scanner != null && hasBlePermissions()) scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) { }
        scanButton.setEnabled(true);
        scanButton.setText("Buscar luminária");
        if (foundDevices.isEmpty()) setStatus("Nenhuma luminária OceanTech encontrada. Procure um nome TO-L-XXXX e tente novamente.");
        else setStatus("Busca concluída. Toque no dispositivo da lâmpada para conectar.");
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
        for (int i = 0; i < schedulePoints.length; i++) schedulePoints[i] = null;
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
        setStatus("Conectando a " + device.getAddress() + "…");
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException exception) {
            setStatus("Sem permissão para conectar por Bluetooth.");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> setStatus("Conectado. Lendo serviços da lâmpada…"));
                bluetoothGatt.discoverServices();
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
                    modeButton.setText("Modo da lâmpada: aguardando leitura");
                    setStatus("Lâmpada desconectada.");
                });
                if (gatt == bluetoothGatt) gatt = null;
                bluetoothGatt.close();
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> setStatus("Não consegui ler os serviços BLE da lâmpada."));
                return;
            }
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(WRITE_UUID);
            BluetoothGattCharacteristic state = service == null ? null : service.getCharacteristic(STATE_UUID);
            BluetoothGattCharacteristic week = service == null ? null : service.getCharacteristic(WEEK_UUID);
            BluetoothGattCharacteristic alarm = service == null ? null : service.getCharacteristic(ALARM_UUID);
            if (characteristic == null) {
                runOnUiThread(() -> setStatus("A lâmpada não anunciou o serviço esperado (AE00/AE01)."));
                return;
            }
            writeCharacteristic = characteristic;
            stateCharacteristic = state;
            weekCharacteristic = week != null
                    && (week.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? week : null;
            alarmCharacteristic = alarm != null
                    && (alarm.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? alarm : null;
            if (state == null || (state.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                runOnUiThread(() -> setStatus("Conectou, mas a característica de estado AE10 não pode ser lida."));
                return;
            }
            enqueueWrite(asciiFrame(HANDSHAKE, false));
            handshakeQueued = true;
            runOnUiThread(() -> setStatus("Serviço encontrado. Confirmando protocolo…"));
        }

        @Override public void onCharacteristicWrite(BluetoothGatt bluetoothGatt,
                                                     BluetoothGattCharacteristic characteristic, int status) {
            if (!WRITE_UUID.equals(characteristic.getUuid())) return;
            writeInFlight = false;
            if (status == BluetoothGatt.GATT_SUCCESS && handshakeQueued) {
                handshakeQueued = false;
                connectedReady = true;
                runOnUiThread(() -> {
                    setStatus("Conectado. Lendo o modo e os níveis atuais…");
                });
                BluetoothGattCharacteristic state = stateCharacteristic;
                if (state == null || !bluetoothGatt.readCharacteristic(state)) {
                    runOnUiThread(() -> setStatus("Conectado, mas não consegui ler o estado inicial (AE10)."));
                }
            } else if (status == BluetoothGatt.GATT_SUCCESS && pendingAutoMode != null) {
                autoMode = pendingAutoMode;
                pendingAutoMode = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    setStatus(autoMode ? "Modo automático ativado." : "Modo manual ativado. Agora envie os níveis.");
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
                    powerButton.setText(on ? "Lâmpada ligada · toque para desligar" : "Lâmpada desligada · toque para ligar");
                    setStatus(on ? "Lâmpada ligada." : "Lâmpada desligada.");
                });
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                pendingAutoMode = null;
                pendingPower = null;
                runOnUiThread(() -> {
                    updateModeControls();
                    setStatus("Falha ao enviar dado BLE (" + status + ").");
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
            runOnUiThread(() -> setStatus("Conectado, mas a lâmpada não retornou o estado inicial."));
            return;
        }
        StringBuilder hex = new StringBuilder(value.length * 2);
        for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 0xff));
        String state = hex.toString();
        if (state.length() < 24 || state.charAt(1) != '6') {
            runOnUiThread(() -> setStatus("Resposta BLE inesperada. Não vou enviar níveis até conseguir identificar o estado."));
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
                    ? "Lâmpada ligada · toque para desligar" : "Lâmpada desligada · toque para ligar");
            powerButton.setEnabled(connectedReady);
            saveWeekButton.setEnabled(false);
            clockButton.setEnabled(connectedReady);
            setScheduleControlsEnabled(connectedReady);
            for (CheckBox day : weekDays) day.setEnabled(false);
            setStatus("Estado lido da lâmpada. Lendo repetição semanal…");
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
            setStatus("Estado e repetição semanal lidos da lâmpada.");
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
                        schedulePoints[slot] = new SchedulePoint(slot, hour, minute, enabled, levels);
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
            refreshScheduleSlotLabels();
            setScheduleControlsEnabled(connectedReady);
            loadSchedulePoint(activeScheduleSlot);
            setStatus("Programação carregada. Escolha um ponto da lista ou toque no gráfico para editar.");
        });
    }

    private void refreshScheduleSlotLabels() {
        if (scheduleSpinner == null || scheduleAdapter == null) return;
        refreshingScheduleOptions = true;
        scheduleAdapter.clear();
        ArrayList<Integer> orderedSlots = new ArrayList<>();
        for (int i = 0; i < 8; i++) if (schedulePoints[i] != null) orderedSlots.add(i);
        Collections.sort(orderedSlots, (a, b) -> {
            SchedulePoint first = schedulePoints[a];
            SchedulePoint second = schedulePoints[b];
            return Integer.compare(first.hour * 60 + first.minute, second.hour * 60 + second.minute);
        });
        for (int i = 0; i < 8; i++) if (schedulePoints[i] == null) orderedSlots.add(i);
        int chronologicalNumber = 0;
        int newPointNumber = 0;
        for (int i = 0; i < orderedSlots.size(); i++) {
            int slot = orderedSlots.get(i);
            scheduleSpinnerSlots[i] = slot;
            SchedulePoint point = schedulePoints[slot];
            String label;
            if (point == null) {
                label = "Adicionar novo horário" + (++newPointNumber > 1 ? " " + newPointNumber : "");
            } else {
                label = "Horário " + (++chronologicalNumber) + "  ·  "
                        + twoDigits(point.hour) + ":" + twoDigits(point.minute)
                        + (point.enabled ? "  ·  Ativo" : "  ·  Desativado");
                if (point.enabled && hasScheduleConflict(slot)) label += "  ·  Revisar horário";
            }
            scheduleAdapter.add(label);
        }
        scheduleAdapter.notifyDataSetChanged();
        int selectedPosition = 0;
        for (int i = 0; i < 8; i++) if (scheduleSpinnerSlots[i] == activeScheduleSlot) selectedPosition = i;
        scheduleSpinner.setSelection(selectedPosition, false);
        refreshingScheduleOptions = false;
    }

    private void toggleMode() {
        if (!connectedReady || !modeKnown || modeHeader == null) {
            setStatus("Ainda não consegui ler o estado da lâmpada.");
            return;
        }
        boolean nextAutoMode = !autoMode;
        String command = "app_pwm_on" + modeHeader.charAt(4) + modeHeader.charAt(5)
                + (nextAutoMode ? "01" : "00") + modeHeader.charAt(8) + modeHeader.charAt(9);
        pendingAutoMode = nextAutoMode;
        modeButton.setEnabled(false);
        enqueueWrite(asciiFrame(command, true));
        setStatus(nextAutoMode ? "Enviando comando para ativar modo automático…" : "Enviando comando para ativar modo manual…");
    }

    private void togglePower() {
        if (!connectedReady || !modeKnown || modeHeader == null) {
            setStatus("Ainda não consegui ler o estado da lâmpada.");
            return;
        }
        boolean nextOn = !lampOn;
        // The original app writes app_pwm_on + [mode, power] + the remaining mode bytes.
        String command = "app_pwm_on0" + (nextOn ? "1" : "0") + modeHeader.substring(6, 10);
        pendingPower = nextOn;
        powerButton.setEnabled(false);
        enqueueWrite(asciiFrame(command, true));
        setStatus(nextOn ? "Enviando comando para ligar…" : "Enviando comando para desligar…");
    }

    private void saveWeekDays() {
        if (!connectedReady || weekCharacteristic == null) {
            setStatus("A lâmpada não disponibiliza a configuração semanal.");
            return;
        }
        StringBuilder command = new StringBuilder("app_week_data");
        for (CheckBox day : weekDays) command.append(day.isChecked() ? '1' : '0');
        enqueueWrite(asciiFrame(command.toString(), true));
        // The original app applies its weekly timer frequency after saving the seven flags.
        enqueueWrite(asciiFrame("app_set_pwm_fre750", true));
        setStatus("Dias da semana e frequência enviados à lâmpada.");
    }

    private void syncLampClock() {
        if (!connectedReady) {
            setStatus("Conecte à lâmpada antes de sincronizar o relógio.");
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
        setStatus("Relógio do telefone enviado à lâmpada.");
    }

    private void chooseScheduleTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            scheduleHour = hour;
            scheduleMinute = minute;
            scheduleEditTitleView.setText("Editar horário · " + twoDigits(hour) + ":" + twoDigits(minute));
            scheduleTimeButton.setText("Escolher horário · " + twoDigits(hour) + ":" + twoDigits(minute));
            updateScheduleGraphPreview();
        }, scheduleHour, scheduleMinute, true).show();
    }

    private void saveSchedule() {
        if (!connectedReady) {
            setStatus("Conecte à lâmpada antes de salvar um horário.");
            return;
        }
        int slot = activeScheduleSlot;
        if (scheduleEnabledSwitch.isChecked() && hasScheduleConflict(slot, scheduleHour, scheduleMinute)) {
            setStatus("Esse horário já está usado por outro ponto ativo. Escolha outro minuto antes de salvar.");
            Toast.makeText(this, "Horário repetido: cada ponto ativo precisa de um horário diferente.", Toast.LENGTH_LONG).show();
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
                scheduleEnabledSwitch.isChecked(), levels);
        refreshScheduleSlotLabels();
        if (lightGraph != null) lightGraph.invalidate();
        enqueueWrite(asciiFrame(command.toString(), true));
        setStatus("Horário " + (slot + 1) + " salvo com as cores e intensidades escolhidas.");
    }

    private String twoDigitsHex(int value) {
        return String.format(Locale.US, "%02x", Math.max(0, Math.min(100, value)));
    }

    private void setScheduleControlsEnabled(boolean enabled) {
        boolean canEdit = enabled && scheduleDataLoaded;
        if (scheduleSpinner != null) scheduleSpinner.setEnabled(canEdit);
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
            modeButton.setText(autoMode ? "Modo atual: automático · toque para manual" : "Modo atual: manual · toque para automático");
            modeButton.setEnabled(connectedReady);
            powerButton.setEnabled(connectedReady && pendingPower == null);
            sendButton.setEnabled(connectedReady && !autoMode);
        } else {
            modeButton.setText("Modo da lâmpada: aguardando leitura");
            modeButton.setEnabled(false);
            powerButton.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }

    private void sendChannelLevels() {
        if (!connectedReady || !modeKnown) {
            Toast.makeText(this, "Conecte à lâmpada e aguarde a leitura do estado.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (autoMode) {
            Toast.makeText(this, "Mude para o modo manual antes de enviar os níveis.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder command = new StringBuilder("rtvall00");
        for (SeekBar bar : channelBars) command.append(String.format(Locale.US, "%03d", bar.getProgress()));
        enqueueWrite(asciiFrame(command.toString(), true));
        setStatus("Comando enviado: W/B/G/R " + channelBars[0].getProgress() + "/"
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
                    runOnUiThread(() -> setStatus("O Android recusou o comando BLE (" + result + ")."));
                }
            } else {
                writeCharacteristic.setWriteType(writeType);
                writeCharacteristic.setValue(next);
                if (!gatt.writeCharacteristic(writeCharacteristic)) {
                    writeInFlight = false;
                    runOnUiThread(() -> setStatus("O Android não iniciou o envio BLE."));
                }
            }
        } catch (SecurityException exception) {
            writeInFlight = false;
            runOnUiThread(() -> setStatus("Sem permissão para escrever na lâmpada."));
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
        modeButton.setText("Modo da lâmpada: aguardando leitura");
        closeGatt();
        setStatus("Desconectado.");
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
            try { oldGatt.disconnect(); } catch (SecurityException ignored) { }
            oldGatt.close();
        }
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return Build.VERSION.SDK_INT >= 23
                ? checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                : true;
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLE_PERMISSIONS);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLE_PERMISSIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLE_PERMISSIONS) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) startScanFlow();
            else setStatus("Permita dispositivos próximos/Bluetooth para buscar a lâmpada.");
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
        private final int[] lineColors = {Color.rgb(111, 119, 124), Color.rgb(48, 105, 206),
                Color.rgb(40, 157, 112), Color.rgb(218, 82, 75)};
        private final String[] lineNames = {"Branco", "Azul", "Verde", "Vermelho"};

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
            String[] hourLabels = {"00", "06", "12", "18", "24"};
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
                canvas.drawText("Adicione um horário para ver a curva de luz", left + dp(4),
                        (top + bottom) / 2f, paint);
                return;
            }
            Collections.sort(points, (a, b) -> Integer.compare(a.hour * 60 + a.minute, b.hour * 60 + b.minute));
            for (int channel = 0; channel < 4; channel++) {
                Path path = new Path();
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
                    canvas.drawCircle(timeX(point, left, right), levelY(point.levels[channel], top, bottom),
                            dp(3), paint);
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
                if (selectedSlot < 8) {
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

        SchedulePoint(int slot, int hour, int minute, boolean enabled, int[] levels) {
            this.slot = slot;
            this.hour = hour;
            this.minute = minute;
            this.enabled = enabled;
            this.levels = levels.clone();
        }
    }

    private void setStatus(String text) {
        if (statusView == null) return;
        statusView.setText(text);
        boolean problem = text.contains("Falha") || text.contains("não consegui")
                || text.contains("não foi possível") || text.contains("Sem permissão");
        int background = problem ? Color.rgb(252, 236, 232)
                : text.startsWith("Conectado") || text.startsWith("Estado lido")
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

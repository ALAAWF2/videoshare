package com.orange.videoplayer;

import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private static final long CONTROLLER_TIMEOUT_MS = 3000L;
    private static final String ACTION_PIP_PLAY_PAUSE = "com.orange.videoplayer.PIP_PLAY_PAUSE";
    private static final String ACTION_PIP_NEXT = "com.orange.videoplayer.PIP_NEXT";

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_BRIGHTNESS = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final int GESTURE_SCRUB = 3;

    private ExoPlayer player;
    private LinkStore store;
    private SettingsStore settingsStore;
    private long entryId = -1;

    private float currentPlaybackSpeed = 1.0f;
    private float speedBeforeHold = 1.0f;
    private boolean isLongPressActive = false;
    private boolean isUserSeeking = false;
    private boolean isControlsVisible = true;
    private boolean isApplyingRemoteSync = false;
    private String currentUrl = null;
    private boolean directRetryAttempted = false;

    private View topBar;
    private View bottomControls;
    private View touchOverlay;
    private MaterialToolbar toolbar;
    private TextView tvPosition;
    private TextView tvDuration;
    private TextView tvPreviewBubble;
    private SeekBar seekBar;
    private ImageButton btnPlayPause;
    private MaterialButton btnSpeed;
    private ImageButton btnSettings;

    private TextView tvSleepTimerPill;
    private ImageButton btnNextEpisode;
    private ImageButton btnSleep;
    private ImageButton btnPip;
    private ImageButton btnWatchParty;
    private View layoutPartyReactions;
    private TextView tvPartyBadge;
    private FrameLayout flyingEmojiContainer;
    private final WatchPartyManager partyManager = WatchPartyManager.getInstance();

    private View layoutErrorPanel;
    private TextView tvErrorMessage;
    private MaterialButton btnErrorRetry;
    private MaterialButton btnErrorBack;

    private TextView tvIndicatorSeek;
    private TextView tvIndicatorGesture;
    private TextView tvIndicatorSpeed;
    private ImageView ivIndicatorPlayPause;
    private TextView tvIndicatorHold;

    private AudioManager audioManager;
    private int maxVolume = 15;
    private int initialVolume = 0;
    private float initialBrightness = 0.5f;
    private long scrubStartPos = 0L;
    private long targetScrubPos = 0L;
    private int activeGesture = GESTURE_NONE;
    private boolean hasDecidedAxis = false;

    private JSONObject nextCandidate = null;
    private long sleepTimerEndTimeMs = 0L;
    private MediaSession mediaSession;
    private BroadcastReceiver pipReceiver;

    private long hostBasePositionMs = 0L;
    private long hostBaseLocalTimeMs = 0L;
    private boolean hostStateIsPlaying = false;
    private long lastResyncRequestTime = 0L;
    private long bufferingStartTime = 0L;

    // Quality selection state (v6.1)
    private DefaultTrackSelector trackSelector;
    private MaterialButton btnQuality;
    private static final int QUALITY_AUTO = -1;
    private static final int QUALITY_MAX = -2;
    private int qualityMode = QUALITY_AUTO;
    private static final String KEY_QUALITY_MODE = "quality_mode";

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveTask = new Runnable() {
        @Override
        public void run() {
            savePosition();
            updateNextCandidate();
            saveHandler.postDelayed(this, 5000);
        }
    };

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            updateProgress();

            // Local monotonic drift correction for Guest (Zero network traffic)
            if (partyManager.isPartyActive() && !partyManager.isHost() && player != null && player.getPlaybackState() == Player.STATE_READY) {
                long nowMonotonic = SystemClock.elapsedRealtime();
                long expectedHostPos = hostStateIsPlaying
                        ? (hostBasePositionMs + (nowMonotonic - hostBaseLocalTimeMs))
                        : hostBasePositionMs;
                long currentPos = player.getCurrentPosition();
                long drift = currentPos - expectedHostPos;
                long absDrift = Math.abs(drift);

                if (absDrift <= 250) {
                    player.setPlaybackSpeed(currentPlaybackSpeed);
                } else if (absDrift <= 1200) {
                    if (drift > 0) {
                        player.setPlaybackSpeed(currentPlaybackSpeed * 0.96f);
                    } else {
                        player.setPlaybackSpeed(currentPlaybackSpeed * 1.04f);
                    }
                } else if (absDrift <= 5000) {
                    player.seekTo(expectedHostPos);
                    player.setPlaybackSpeed(currentPlaybackSpeed);
                } else {
                    player.seekTo(expectedHostPos);
                    player.setPlaybackSpeed(currentPlaybackSpeed);
                    if (nowMonotonic - lastResyncRequestTime > 5000) {
                        lastResyncRequestTime = nowMonotonic;
                        partyManager.requestSync();
                    }
                }
            }

            progressHandler.postDelayed(this, 250);
        }
    };

    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsTask = this::hideControls;

    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!settingsStore.isLongPressEnabled() || player == null) return;
            isLongPressActive = true;
            speedBeforeHold = currentPlaybackSpeed;
            float holdSpeed = settingsStore.getLongPressSpeed();
            player.setPlaybackSpeed(holdSpeed);

            String text = SettingsStore.formatSpeed(holdSpeed) + " ⏩";
            tvIndicatorHold.setText(text);
            tvIndicatorHold.setAlpha(1.0f);
            tvIndicatorHold.setVisibility(View.VISIBLE);

            try {
                touchOverlay.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignored) {
            }
        }
    };

    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (sleepTimerEndTimeMs <= 0) {
                if (tvSleepTimerPill != null) tvSleepTimerPill.setVisibility(View.GONE);
                return;
            }
            long remainingMs = sleepTimerEndTimeMs - SystemClock.elapsedRealtime();
            if (remainingMs <= 0) {
                sleepTimerEndTimeMs = 0L;
                if (tvSleepTimerPill != null) tvSleepTimerPill.setVisibility(View.GONE);
                if (player != null) player.pause();
                Toast.makeText(PlayerActivity.this, R.string.sleep_timer_ended, Toast.LENGTH_SHORT).show();
            } else {
                long totalSec = (remainingMs + 999) / 1000;
                long h = totalSec / 3600;
                long m = (totalSec % 3600) / 60;
                long s = totalSec % 60;
                String text = h > 0
                        ? String.format(Locale.US, "🌙 %02d:%02d:%02d", h, m, s)
                        : String.format(Locale.US, "🌙 %02d:%02d", m, s);
                if (tvSleepTimerPill != null) {
                    tvSleepTimerPill.setText(text);
                    tvSleepTimerPill.setVisibility(View.VISIBLE);
                }
                sleepTimerHandler.postDelayed(this, 1000L);
            }
        }
    };

    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private int touchSlop = 0;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Immersive fullscreen: hide status bar + navigation bar, swipe to show transiently
        applyFullscreen();

        store = new LinkStore(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        currentPlaybackSpeed = settingsStore.getDefaultSpeed();

        String url = null;
        String customName = getIntent().getStringExtra("name");
        JSONObject entry = null;
        if (getIntent().hasExtra("id")) {
            long id = getIntent().getLongExtra("id", -1);
            entry = store.get(id);
            if (entry != null) url = entry.optString("url");
        }
        if (url == null && getIntent().getStringExtra("url") != null) {
            url = getIntent().getStringExtra("url");
        }
        if (url == null && getIntent().getData() != null) {
            url = getIntent().getData().toString();
        }
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        currentUrl = url;
        if (entry == null) {
            String nameToSave = (customName != null && !customName.trim().isEmpty())
                    ? customName.trim()
                    : LinkStore.autoName(url);
            long id = store.add(nameToSave, url);
            entry = store.get(id);
        }
        entryId = entry != null ? entry.optLong("id") : -1;

        long seekPos = getIntent().getLongExtra("pos", -1);
        if (seekPos < 0 && entry != null) seekPos = entry.optLong("pos");

        initViews(entry, url, customName);
        initGestures();
        initMediaSession();
        initPlayer(url, seekPos);
        updateNextCandidate();
    }

    private void applyFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (insetsController != null) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void initViews(JSONObject entry, String url, String customName) {
        topBar = findViewById(R.id.top_bar);
        bottomControls = findViewById(R.id.bottom_controls);
        touchOverlay = findViewById(R.id.touch_overlay);
        tvPosition = findViewById(R.id.tv_position);
        tvDuration = findViewById(R.id.tv_duration);
        tvPreviewBubble = findViewById(R.id.tv_preview_bubble);
        seekBar = findViewById(R.id.seek_bar);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnSpeed = findViewById(R.id.btn_speed);
        btnSettings = findViewById(R.id.btn_settings);

        tvSleepTimerPill = findViewById(R.id.tv_sleep_timer_pill);
        btnNextEpisode = findViewById(R.id.btn_next_episode);
        btnSleep = findViewById(R.id.btn_sleep);
        btnWatchParty = findViewById(R.id.btn_watch_party);
        btnPip = findViewById(R.id.btn_pip);
        layoutPartyReactions = findViewById(R.id.layout_party_reactions);
        tvPartyBadge = findViewById(R.id.tv_party_badge);
        flyingEmojiContainer = findViewById(R.id.flying_emoji_container);

        layoutErrorPanel = findViewById(R.id.layout_error_panel);
        tvErrorMessage = findViewById(R.id.tv_error_message);
        btnErrorRetry = findViewById(R.id.btn_error_retry);
        btnErrorBack = findViewById(R.id.btn_error_back);

        tvIndicatorSeek = findViewById(R.id.tv_indicator_seek);
        tvIndicatorGesture = findViewById(R.id.tv_indicator_gesture);
        tvIndicatorSpeed = findViewById(R.id.tv_indicator_speed);
        ivIndicatorPlayPause = findViewById(R.id.iv_indicator_play_pause);
        tvIndicatorHold = findViewById(R.id.tv_indicator_hold);

        toolbar = findViewById(R.id.toolbar);
        String title = (customName != null && !customName.isEmpty())
                ? customName
                : (entry != null ? entry.optString("name") : LinkStore.autoName(url));
        toolbar.setTitle(title);
        String host = null;
        try {
            host = Uri.parse(url).getHost();
        } catch (Exception ignored) {
        }
        toolbar.setSubtitle(host);
        toolbar.setNavigationOnClickListener(v -> finish());

        btnSpeed.setText(SettingsStore.formatSpeed(currentPlaybackSpeed));
        btnSpeed.setOnClickListener(v -> cycleSpeed());

        btnQuality = findViewById(R.id.btn_quality);
        qualityMode = getSharedPreferences("myplyr_settings", Context.MODE_PRIVATE)
                .getInt(KEY_QUALITY_MODE, QUALITY_AUTO);
        updateQualityLabel();
        btnQuality.setOnClickListener(v -> showQualityDialog());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(PlayerActivity.this, SettingsActivity.class));
            resetAutoHideControls();
        });

        tvSleepTimerPill.setOnClickListener(v -> {
            showSleepTimerDialog();
            resetAutoHideControls();
        });

        btnSleep.setOnClickListener(v -> {
            showSleepTimerDialog();
            resetAutoHideControls();
        });

        btnWatchParty.setOnClickListener(v -> {
            String partyTitle = (toolbar != null && toolbar.getTitle() != null) ? toolbar.getTitle().toString() : "فيديو";
            WatchPartyDialog.show(PlayerActivity.this, currentUrl, partyTitle, (isActive, isHost, roomId) -> updatePartyUI());
            resetAutoHideControls();
        });

        View btnFire = findViewById(R.id.btn_emoji_fire);
        if (btnFire != null) btnFire.setOnClickListener(v -> onEmojiClicked("🔥"));
        View btnHeart = findViewById(R.id.btn_emoji_heart);
        if (btnHeart != null) btnHeart.setOnClickListener(v -> onEmojiClicked("❤️"));
        View btnLaugh = findViewById(R.id.btn_emoji_laugh);
        if (btnLaugh != null) btnLaugh.setOnClickListener(v -> onEmojiClicked("😂"));
        View btnPopcorn = findViewById(R.id.btn_emoji_popcorn);
        if (btnPopcorn != null) btnPopcorn.setOnClickListener(v -> onEmojiClicked("🍿"));

        setupWatchPartyListener();
        updatePartyUI();

        btnNextEpisode.setOnClickListener(v -> {
            playNextEpisode();
            resetAutoHideControls();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnPip.setVisibility(View.VISIBLE);
            btnPip.setOnClickListener(v -> enterPipMode());
        } else {
            btnPip.setVisibility(View.GONE);
        }

        btnErrorRetry.setOnClickListener(v -> {
            if (layoutErrorPanel != null) layoutErrorPanel.setVisibility(View.GONE);
            directRetryAttempted = false;
            if (player != null) {
                player.prepare();
                player.play();
            }
        });

        btnErrorBack.setOnClickListener(v -> finish());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        long pos = (long) (progress * (double) dur / 1000.0);
                        String timeStr = formatTime(pos);
                        tvPosition.setText(timeStr);
                        if (tvPreviewBubble != null) {
                            tvPreviewBubble.setText(timeStr);
                            tvPreviewBubble.setVisibility(View.VISIBLE);
                            int availableWidth = sb.getWidth() - sb.getPaddingStart() - sb.getPaddingEnd();
                            if (availableWidth > 0) {
                                float ratio = (float) progress / sb.getMax();
                                float targetX = sb.getPaddingStart() + (ratio * availableWidth) - (tvPreviewBubble.getWidth() / 2f);
                                tvPreviewBubble.setTranslationX(Math.max(0, targetX));
                            }
                        }
                    }
                    resetAutoHideControls();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
                cancelAutoHideControls();
                if (tvPreviewBubble != null) {
                    tvPreviewBubble.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (player != null) {
                    long dur = player.getDuration();
                    if (dur > 0) {
                        long targetPos = (long) (sb.getProgress() * (double) dur / 1000.0);
                        player.seekTo(targetPos);
                        tvPosition.setText(formatTime(targetPos));
                    }
                }
                if (tvPreviewBubble != null) {
                    tvPreviewBubble.setVisibility(View.GONE);
                }
                isUserSeeking = false;
                scheduleAutoHideControls();
            }
        });
    }

    private void showSleepTimerDialog() {
        String[] labels = new String[]{
                getString(R.string.sleep_timer_off),
                getString(R.string.sleep_timer_15m),
                getString(R.string.sleep_timer_30m),
                getString(R.string.sleep_timer_45m),
                getString(R.string.sleep_timer_60m),
                getString(R.string.sleep_timer_90m)
        };
        int[] minutes = new int[]{0, 15, 30, 45, 60, 90};

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sleep_timer)
                .setItems(labels, (dialog, which) -> {
                    int min = minutes[which];
                    if (min == 0) {
                        sleepTimerEndTimeMs = 0L;
                        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
                        if (tvSleepTimerPill != null) tvSleepTimerPill.setVisibility(View.GONE);
                    } else {
                        sleepTimerEndTimeMs = SystemClock.elapsedRealtime() + min * 60 * 1000L;
                        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
                        sleepTimerHandler.post(sleepTimerRunnable);
                        Toast.makeText(PlayerActivity.this, getString(R.string.sleep_timer_set, labels[which]), Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                    resetAutoHideControls();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateNextCandidate() {
        String currentName = toolbar != null && toolbar.getTitle() != null
                ? toolbar.getTitle().toString()
                : "";
        nextCandidate = store != null ? store.getNextCandidate(entryId, currentName) : null;
        if (btnNextEpisode != null) {
            btnNextEpisode.setVisibility(nextCandidate != null ? View.VISIBLE : View.GONE);
        }
        updatePipActions();
    }

    private void playNextEpisode() {
        if (nextCandidate == null || player == null) return;
        savePosition();
        entryId = nextCandidate.optLong("id");
        String nextUrl = nextCandidate.optString("url");
        String nextName = nextCandidate.optString("name");
        if (nextUrl == null || nextUrl.isEmpty()) return;

        if (toolbar != null) {
            toolbar.setTitle(nextName);
            String host = null;
            try {
                host = Uri.parse(nextUrl).getHost();
            } catch (Exception ignored) {
            }
            toolbar.setSubtitle(host);
        }

        player.stop();
        player.setMediaItem(MediaItem.fromUri(nextUrl));
        player.seekTo(0);
        player.prepare();
        player.play();

        if (layoutErrorPanel != null) layoutErrorPanel.setVisibility(View.GONE);
        updateProgress();
        updateNextCandidate();
        resetAutoHideControls();
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(this, "MyPlyrSession");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (player != null) {
                    if (player.getPlaybackState() == Player.STATE_ENDED) {
                        player.seekTo(0);
                    }
                    player.play();
                    updatePipActions();
                }
            }

            @Override
            public void onPause() {
                if (player != null) {
                    player.pause();
                    updatePipActions();
                }
            }

            @Override
            public void onSkipToNext() {
                playNextEpisode();
            }
        });
        mediaSession.setActive(true);

        pipReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                if (ACTION_PIP_PLAY_PAUSE.equals(intent.getAction())) {
                    togglePlayPause();
                    updatePipActions();
                } else if (ACTION_PIP_NEXT.equals(intent.getAction())) {
                    playNextEpisode();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PIP_PLAY_PAUSE);
        filter.addAction(ACTION_PIP_NEXT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipReceiver, filter);
        }
    }

    private void updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                List<RemoteAction> actions = new ArrayList<>();

                boolean isPlaying = player != null && player.isPlaying();
                int iconRes = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
                String title = getString(isPlaying ? R.string.exo_controls_pause : R.string.exo_controls_play);
                Intent playPauseIntent = new Intent(ACTION_PIP_PLAY_PAUSE);
                playPauseIntent.setPackage(getPackageName());
                PendingIntent playPausePending = PendingIntent.getBroadcast(
                        this, 101, playPauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                );
                actions.add(new RemoteAction(Icon.createWithResource(this, iconRes), title, title, playPausePending));

                if (nextCandidate != null) {
                    Intent nextIntent = new Intent(ACTION_PIP_NEXT);
                    nextIntent.setPackage(getPackageName());
                    PendingIntent nextPending = PendingIntent.getBroadcast(
                            this, 102, nextIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
                    );
                    actions.add(new RemoteAction(Icon.createWithResource(this, R.drawable.ic_next_episode), getString(R.string.next_episode), getString(R.string.next_episode), nextPending));
                }

                builder.setActions(actions);
                setPictureInPictureParams(builder.build());
            } catch (Exception ignored) {
            }
        }
        updateMediaSessionState();
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;
        try {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE;
            if (nextCandidate != null) {
                actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
            }
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder().setActions(actions);
            boolean isPlaying = player != null && player.isPlaying();
            int state = isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
            long pos = player != null ? player.getCurrentPosition() : 0;
            stateBuilder.setState(state, pos, currentPlaybackSpeed);
            mediaSession.setPlaybackState(stateBuilder.build());
        } catch (Exception ignored) {
        }
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                updatePipActions();
                hideControls();
                enterPictureInPictureMode(builder.build());
            } catch (Exception e) {
                try {
                    enterPictureInPictureMode();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && settingsStore != null && settingsStore.isPipEnabled()
                && player != null && player.isPlaying()) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideControls();
            if (topBar != null) topBar.setVisibility(View.GONE);
            if (bottomControls != null) bottomControls.setVisibility(View.GONE);
            View indicators = findViewById(R.id.center_indicators_container);
            if (indicators != null) indicators.setVisibility(View.GONE);
            if (layoutErrorPanel != null) layoutErrorPanel.setVisibility(View.GONE);
        } else {
            View indicators = findViewById(R.id.center_indicators_container);
            if (indicators != null) indicators.setVisibility(View.VISIBLE);
            showControls();
            applyFullscreen();
        }
    }

    private float getScreenBrightness() {
        float brightness = getWindow().getAttributes().screenBrightness;
        if (brightness < 0) {
            try {
                int sysBrightness = Settings.System.getInt(
                        getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS);
                brightness = sysBrightness / 255.0f;
            } catch (Exception e) {
                brightness = 0.5f;
            }
        }
        return Math.max(0.01f, Math.min(1.0f, brightness));
    }

    private void showGestureIndicator(String text) {
        tvIndicatorGesture.animate().cancel();
        tvIndicatorGesture.setText(text);
        tvIndicatorGesture.setAlpha(1.0f);
        tvIndicatorGesture.setVisibility(View.VISIBLE);
    }

    private void hideGestureIndicator() {
        tvIndicatorGesture.animate().cancel();
        tvIndicatorGesture.animate()
                .alpha(0.0f)
                .setDuration(350)
                .withEndAction(() -> tvIndicatorGesture.setVisibility(View.GONE))
                .start();
    }

    private void initGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                return handleDoubleTap(e);
            }
        });

        touchOverlay.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    hasDecidedAxis = false;
                    activeGesture = GESTURE_NONE;
                    isLongPressActive = false;

                    if (settingsStore.isLongPressEnabled()) {
                        longPressHandler.postDelayed(longPressRunnable, 350);
                    }
                    gestureDetector.onTouchEvent(event);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float currentX = event.getX();
                    float currentY = event.getY();
                    float dx = Math.abs(currentX - touchDownX);
                    float dy = Math.abs(currentY - touchDownY);

                    if (!hasDecidedAxis) {
                        if (dx > touchSlop * 1.5f || dy > touchSlop * 1.5f) {
                            hasDecidedAxis = true;
                            longPressHandler.removeCallbacks(longPressRunnable);

                            float width = touchOverlay.getWidth();
                            if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

                            if (dy > dx) {
                                // Vertical swipe: Left ~45% = Brightness, Right ~55% = Volume
                                if (touchDownX < (width * 0.45f) && settingsStore.isBrightnessGestureEnabled()) {
                                    activeGesture = GESTURE_BRIGHTNESS;
                                    initialBrightness = getScreenBrightness();
                                } else if (touchDownX >= (width * 0.45f) && settingsStore.isVolumeGestureEnabled()) {
                                    activeGesture = GESTURE_VOLUME;
                                    if (audioManager == null) {
                                        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                                    }
                                    if (audioManager != null) {
                                        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                                        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    }
                                }
                            } else {
                                // Horizontal swipe: Scrub through video
                                if (settingsStore.isScrubEnabled() && player != null && player.getDuration() > 0) {
                                    activeGesture = GESTURE_SCRUB;
                                    scrubStartPos = player.getCurrentPosition();
                                    targetScrubPos = scrubStartPos;
                                    cancelAutoHideControls();
                                }
                            }
                        }
                    }

                    if (activeGesture == GESTURE_BRIGHTNESS) {
                        float height = touchOverlay.getHeight();
                        if (height <= 0) height = getResources().getDisplayMetrics().heightPixels;
                        float delta = (touchDownY - currentY) / (height * 0.75f);
                        float newBrightness = Math.max(0.01f, Math.min(1.0f, initialBrightness + delta));
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.screenBrightness = newBrightness;
                        getWindow().setAttributes(lp);

                        int pct = Math.round(newBrightness * 100);
                        showGestureIndicator("☀ " + pct + "%");
                        return true;
                    } else if (activeGesture == GESTURE_VOLUME) {
                        float height = touchOverlay.getHeight();
                        if (height <= 0) height = getResources().getDisplayMetrics().heightPixels;
                        if (audioManager != null && maxVolume > 0) {
                            float delta = (touchDownY - currentY) / (height * 0.75f);
                            int newVol = Math.max(0, Math.min(maxVolume, Math.round(initialVolume + delta * maxVolume)));
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                            int pct = Math.round((float) newVol / maxVolume * 100);
                            String icon = (newVol == 0) ? "🔇 " : "🔊 ";
                            showGestureIndicator(icon + pct + "%");
                        }
                        return true;
                    } else if (activeGesture == GESTURE_SCRUB) {
                        float width = touchOverlay.getWidth();
                        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
                        long dur = player != null ? player.getDuration() : 0;
                        if (dur > 0) {
                            long windowMs = settingsStore.getScrubWindowSeconds() * 1000L;
                            float deltaX = currentX - touchDownX;
                            long deltaMs = (long) ((deltaX / width) * windowMs);
                            targetScrubPos = Math.max(0, Math.min(dur, scrubStartPos + deltaMs));
                            showGestureIndicator("▶ " + formatTime(targetScrubPos) + " / " + formatTime(dur));
                        }
                        return true;
                    }

                    if (!hasDecidedAxis) {
                        if (dx > touchSlop || dy > touchSlop) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                        }
                        gestureDetector.onTouchEvent(event);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    longPressHandler.removeCallbacks(longPressRunnable);

                    if (isLongPressActive) {
                        isLongPressActive = false;
                        tvIndicatorHold.setVisibility(View.GONE);
                        if (player != null) {
                            player.setPlaybackSpeed(speedBeforeHold);
                        }
                        return true;
                    }

                    if (activeGesture == GESTURE_SCRUB) {
                        if (player != null && player.getDuration() > 0) {
                            player.seekTo(targetScrubPos);
                            tvPosition.setText(formatTime(targetScrubPos));
                            seekBar.setProgress((int) (targetScrubPos * 1000 / player.getDuration()));
                        }
                        hideGestureIndicator();
                        activeGesture = GESTURE_NONE;
                        scheduleAutoHideControls();
                        return true;
                    }

                    if (activeGesture == GESTURE_BRIGHTNESS || activeGesture == GESTURE_VOLUME) {
                        hideGestureIndicator();
                        activeGesture = GESTURE_NONE;
                        return true;
                    }

                    if (hasDecidedAxis) {
                        activeGesture = GESTURE_NONE;
                        return true;
                    }

                    return gestureDetector.onTouchEvent(event);
            }
            return false;
        });
    }

    private boolean handleDoubleTap(MotionEvent e) {
        if (!settingsStore.isDoubleTapEnabled() || player == null) return false;
        long dur = player.getDuration();
        if (dur <= 0) return false; // live stream or unset duration

        float width = touchOverlay.getWidth();
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;

        // Double-tap on the RIGHT ~55% is forward; on the LEFT ~45% is backward
        boolean isForward = e.getX() > (width * 0.45f);
        int seconds = settingsStore.getDoubleTapSeconds();
        long deltaMs = (isForward ? seconds : -seconds) * 1000L;

        long currentPos = player.getCurrentPosition();
        long targetPos = Math.max(0, Math.min(dur, currentPos + deltaMs));
        player.seekTo(targetPos);

        if (!isUserSeeking) {
            seekBar.setProgress((int) (targetPos * 1000 / dur));
            tvPosition.setText(formatTime(targetPos));
        }

        String indicatorText = (isForward ? "+" : "−") + seconds + " ث";
        showTransientIndicator(tvIndicatorSeek, indicatorText);

        resetAutoHideControls();
        return true;
    }

    private void initPlayer(String url, long seekPos) {
        directRetryAttempted = false;
        PlayerView playerView = findViewById(R.id.player_view);
        trackSelector = new DefaultTrackSelector(this);
        // Like VLC: no bandwidth cap — never let the network estimator downgrade quality
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoBitrate(Integer.MAX_VALUE)
                .setExceedVideoConstraintsIfNecessary(true));
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();
        playerView.setPlayer(player);

        player.setPlaybackSpeed(currentPlaybackSpeed);
        player.setMediaItem(MediaItem.fromUri(url));
        if (seekPos > 3000) player.seekTo(seekPos - 3000);
        player.prepare();
        player.play();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayPauseButton(isPlaying);
                updatePipActions();
                if (isPlaying) {
                    scheduleAutoHideControls();
                } else {
                    cancelAutoHideControls();
                }
                if (!isApplyingRemoteSync && partyManager.isPartyActive() && partyManager.isHost() && player != null) {
                    partyManager.broadcastSync(player.getCurrentPosition(), isPlaying);
                }
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                if (!isApplyingRemoteSync && partyManager.isPartyActive() && partyManager.isHost() && player != null) {
                    partyManager.broadcastSync(player.getCurrentPosition(), player.isPlaying());
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    long dur = player.getDuration();
                    if (dur > 0 && entryId > 0) store.updatePosition(entryId, dur, dur);
                    updatePlayPauseButton(false);
                    showControls();
                } else if (playbackState == Player.STATE_BUFFERING) {
                    bufferingStartTime = SystemClock.elapsedRealtime();
                } else if (playbackState == Player.STATE_READY) {
                    if (bufferingStartTime > 0 && (SystemClock.elapsedRealtime() - bufferingStartTime > 2500)) {
                        if (partyManager.isPartyActive() && !partyManager.isHost()) {
                            partyManager.requestSync();
                        }
                    }
                    bufferingStartTime = 0L;
                    if (layoutErrorPanel != null) layoutErrorPanel.setVisibility(View.GONE);
                    updateProgress();
                    // Apply saved quality preference once tracks are known
                    if (qualityMode != QUALITY_AUTO) {
                        applyQualityMode(qualityMode);
                    }
                }
                updatePipActions();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (!directRetryAttempted && currentUrl != null) {
                    directRetryAttempted = true;
                    IptvApiClient apiClient = new IptvApiClient();
                    apiClient.resolveDirectUrl(currentUrl, new IptvApiClient.Callback<String>() {
                        @Override
                        public void onSuccess(String resolvedUrl) {
                            if (resolvedUrl == null || resolvedUrl.isEmpty() || resolvedUrl.equalsIgnoreCase(currentUrl)) {
                                showErrorPanel();
                                return;
                            }
                            currentUrl = resolvedUrl;
                            if (entryId >= 0) store.updateUrl(entryId, resolvedUrl);
                            if (layoutErrorPanel != null) layoutErrorPanel.setVisibility(View.GONE);
                            player.setMediaItem(MediaItem.fromUri(resolvedUrl));
                            if (entryId >= 0) {
                                JSONObject e2 = store.get(entryId);
                                if (e2 != null) {
                                    long p = e2.optLong("pos");
                                    if (p > 3000) player.seekTo(p);
                                }
                            }
                            player.prepare();
                            player.play();
                            Toast.makeText(PlayerActivity.this, R.string.used_direct_link, Toast.LENGTH_SHORT).show();
                            showControls();
                        }

                        @Override
                        public void onError(String error) {
                            showErrorPanel();
                        }
                    });
                } else {
                    showErrorPanel();
                }
            }
        });

        scheduleAutoHideControls();
    }

    private void showErrorPanel() {
        if (tvErrorMessage != null) {
            tvErrorMessage.setText(R.string.error_play_failed_hint);
        }
        if (layoutErrorPanel != null) {
            layoutErrorPanel.setVisibility(View.VISIBLE);
        }
        showControls();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            flashPlayPauseIndicator(false);
        } else {
            if (player.getPlaybackState() == Player.STATE_ENDED) {
                player.seekTo(0);
            }
            player.play();
            flashPlayPauseIndicator(true);
        }
        resetAutoHideControls();
        updatePipActions();
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void cycleSpeed() {
        if (player == null) return;
        float nextSpeed = SettingsStore.getNextSpeed(currentPlaybackSpeed);
        currentPlaybackSpeed = nextSpeed;
        player.setPlaybackSpeed(currentPlaybackSpeed);
        settingsStore.setDefaultSpeed(currentPlaybackSpeed);

        String speedLabel = SettingsStore.formatSpeed(currentPlaybackSpeed);
        btnSpeed.setText(speedLabel);
        showTransientIndicator(tvIndicatorSpeed, speedLabel);

        resetAutoHideControls();
        updatePipActions();
    }

    // ===== Quality control (v6.1) =====
    private void applyQualityMode(int mode) {
        if (player == null || trackSelector == null) return;
        // Remove any bandwidth-based cap so network estimate never downgrades us
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setMaxVideoBitrate(Integer.MAX_VALUE)
                .setExceedVideoConstraintsIfNecessary(true));
        if (mode == QUALITY_AUTO) {
            clearPinnedVideo();
            return;
        }
        java.util.TreeSet<Integer> heights = collectVideoHeights();
        if (heights.isEmpty()) return;
        int targetH = (mode == QUALITY_MAX) ? heights.last() : mode;
        Integer chosen = heights.ceiling(targetH);
        if (chosen == null) chosen = heights.floor(targetH);
        if (chosen != null) pinVideoHeight(chosen);
    }

    private java.util.TreeSet<Integer> collectVideoHeights() {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        androidx.media3.common.Tracks tracks = player.getCurrentTracks();
        for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSupported(i)) {
                    int h = g.getTrackFormat(i).height;
                    if (h > 0) set.add(h);
                }
            }
        }
        return set;
    }

    private void pinVideoHeight(int h) {
        androidx.media3.common.Tracks tracks = player.getCurrentTracks();
        for (androidx.media3.common.Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                if (g.isTrackSupported(i) && g.getTrackFormat(i).height == h) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .addOverride(new TrackSelectionOverride(g.getMediaTrackGroup(), i))
                            .build());
                    return;
                }
            }
        }
    }

    private void clearPinnedVideo() {
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build());
    }

    private void showQualityDialog() {
        resetAutoHideControls();
        if (player == null) return;
        java.util.TreeSet<Integer> hSet = collectVideoHeights();
        final java.util.List<Integer> heights = new java.util.ArrayList<>(hSet);
        java.util.Collections.sort(heights, java.util.Collections.reverseOrder());
        if (heights.isEmpty()) {
            Toast.makeText(this, "لا توجد دقات متعددة لهذا البث", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] items = new CharSequence[heights.size() + 2];
        items[0] = "تلقائي";
        items[1] = "أعلى جودة (" + heights.get(0) + "p)";
        for (int i = 0; i < heights.size(); i++) {
            items[i + 2] = heights.get(i) + "p";
        }
        int checked = qualityMode == QUALITY_AUTO ? 0 : (qualityMode == QUALITY_MAX ? 1 : -1);
        new MaterialAlertDialogBuilder(this)
                .setTitle("الجودة")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    if (which == 0) qualityMode = QUALITY_AUTO;
                    else if (which == 1) qualityMode = QUALITY_MAX;
                    else qualityMode = heights.get(which - 2);
                    getSharedPreferences("myplyr_settings", Context.MODE_PRIVATE)
                            .edit().putInt(KEY_QUALITY_MODE, qualityMode).apply();
                    updateQualityLabel();
                    applyQualityMode(qualityMode);
                    d.dismiss();
                    Toast.makeText(this, "الجودة: " + items[which], Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void updateQualityLabel() {
        if (btnQuality == null) return;
        if (qualityMode == QUALITY_AUTO) btnQuality.setText("AUTO");
        else if (qualityMode == QUALITY_MAX) btnQuality.setText("MAX");
        else btnQuality.setText(qualityMode + "p");
    }

    private void showTransientIndicator(TextView view, String text) {
        view.setText(text);
        view.animate().cancel();
        view.setAlpha(1.0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(0.0f)
                .setDuration(300)
                .setStartDelay(1000)
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    private void flashPlayPauseIndicator(boolean isPlay) {
        ivIndicatorPlayPause.setImageResource(isPlay ? R.drawable.ic_play : R.drawable.ic_pause);
        ivIndicatorPlayPause.animate().cancel();
        ivIndicatorPlayPause.setAlpha(1.0f);
        ivIndicatorPlayPause.setScaleX(0.85f);
        ivIndicatorPlayPause.setScaleY(0.85f);
        ivIndicatorPlayPause.setVisibility(View.VISIBLE);
        ivIndicatorPlayPause.animate()
                .alpha(0.0f)
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(400)
                .setStartDelay(150)
                .withEndAction(() -> ivIndicatorPlayPause.setVisibility(View.GONE))
                .start();
    }

    private void toggleControls() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        isControlsVisible = true;
        topBar.animate().cancel();
        bottomControls.animate().cancel();
        topBar.setVisibility(View.VISIBLE);
        bottomControls.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1.0f).setDuration(200).start();
        bottomControls.animate().alpha(1.0f).setDuration(200).start();

        if (player != null && player.isPlaying()) {
            scheduleAutoHideControls();
        }
    }

    private void hideControls() {
        isControlsVisible = false;
        topBar.animate().cancel();
        bottomControls.animate().cancel();
        topBar.animate().alpha(0.0f).setDuration(200).withEndAction(() -> topBar.setVisibility(View.GONE)).start();
        bottomControls.animate().alpha(0.0f).setDuration(200).withEndAction(() -> bottomControls.setVisibility(View.GONE)).start();
        cancelAutoHideControls();
    }

    private void scheduleAutoHideControls() {
        cancelAutoHideControls();
        controlsHandler.postDelayed(hideControlsTask, CONTROLLER_TIMEOUT_MS);
    }

    private void cancelAutoHideControls() {
        controlsHandler.removeCallbacks(hideControlsTask);
    }

    private void resetAutoHideControls() {
        if (isControlsVisible && player != null && player.isPlaying()) {
            scheduleAutoHideControls();
        }
    }

    private void updateProgress() {
        if (player != null && !isUserSeeking) {
            long pos = player.getCurrentPosition();
            long dur = player.getDuration();
            if (dur > 0) {
                int progress = (int) (pos * 1000 / dur);
                seekBar.setProgress(progress);
                tvPosition.setText(formatTime(pos));
                tvDuration.setText(formatTime(dur));
            } else {
                seekBar.setProgress(0);
                tvPosition.setText(formatTime(pos));
                tvDuration.setText(dur < 0 ? getString(R.string.live_stream) : "00:00");
            }
        }
    }

    public static String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private void savePosition() {
        if (player != null) {
            long pos = player.getCurrentPosition();
            long dur = player.getDuration();
            if (entryId > 0 && pos > 0 && dur > 0 && pos < dur) {
                store.updatePosition(entryId, pos, dur);
            }
            if (currentUrl != null && !currentUrl.isEmpty()) {
                String title = (toolbar != null && toolbar.getTitle() != null) ? toolbar.getTitle().toString() : LinkStore.autoName(currentUrl);
                HistoryStore.getInstance(this).addOrUpdate(title, currentUrl, null, "video", pos, dur);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        saveHandler.postDelayed(saveTask, 5000);
        progressHandler.post(progressTask);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsStore != null) {
            if (btnSpeed != null) btnSpeed.setText(SettingsStore.formatSpeed(currentPlaybackSpeed));
        }
        if (sleepTimerEndTimeMs > 0) {
            long remaining = sleepTimerEndTimeMs - SystemClock.elapsedRealtime();
            if (remaining <= 0) {
                sleepTimerEndTimeMs = 0L;
                if (tvSleepTimerPill != null) tvSleepTimerPill.setVisibility(View.GONE);
                if (player != null) player.pause();
                Toast.makeText(this, R.string.sleep_timer_ended, Toast.LENGTH_SHORT).show();
            } else {
                sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
                sleepTimerHandler.post(sleepTimerRunnable);
            }
        }
        if (partyManager.isPartyActive() && !partyManager.isHost()) {
            partyManager.requestSync();
        }
        updateNextCandidate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveHandler.removeCallbacks(saveTask);
        progressHandler.removeCallbacks(progressTask);
        cancelAutoHideControls();
        longPressHandler.removeCallbacks(longPressRunnable);
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        savePosition();

        boolean inPip = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                inPip = isInPictureInPictureMode();
            } catch (Exception ignored) {
            }
        }
        if (!inPip) {
            if (player != null) player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveHandler.removeCallbacks(saveTask);
        progressHandler.removeCallbacks(progressTask);
        cancelAutoHideControls();
        longPressHandler.removeCallbacks(longPressRunnable);
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);

        if (pipReceiver != null) {
            try {
                unregisterReceiver(pipReceiver);
            } catch (Exception ignored) {
            }
            pipReceiver = null;
        }

        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Exception ignored) {
            }
            mediaSession = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }

        partyManager.setListener(null);
    }

    private void onEmojiClicked(String emoji) {
        spawnFlyingEmoji(emoji);
        partyManager.sendEmoji(emoji);
    }

    private void updatePartyUI() {
        boolean active = partyManager.isPartyActive();
        if (layoutPartyReactions != null) {
            layoutPartyReactions.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        if (tvPartyBadge != null && active) {
            tvPartyBadge.setText(partyManager.getRoomId());
        }
        if (btnWatchParty != null) {
            btnWatchParty.setColorFilter(active
                    ? ContextCompat.getColor(this, R.color.accent)
                    : ContextCompat.getColor(this, R.color.text));
        }
    }

    private void setupWatchPartyListener() {
        partyManager.setListener(new WatchPartyManager.Listener() {
            @Override
            public void onSyncReceived(long targetPosMs, boolean isPlaying, long seq) {
                if (player != null && !partyManager.isHost()) {
                    hostBasePositionMs = targetPosMs;
                    hostBaseLocalTimeMs = SystemClock.elapsedRealtime();
                    hostStateIsPlaying = isPlaying;

                    isApplyingRemoteSync = true;
                    try {
                        long current = player.getCurrentPosition();
                        long diff = Math.abs(current - targetPosMs);

                        // If difference is large or stopped, seek immediately
                        if (diff > 1200 || player.getPlaybackState() == Player.STATE_ENDED) {
                            player.seekTo(targetPosMs);
                            player.setPlaybackSpeed(currentPlaybackSpeed);
                        }

                        if (isPlaying && !player.isPlaying()) {
                            player.play();
                        } else if (!isPlaying && player.isPlaying()) {
                            player.pause();
                        }
                    } finally {
                        isApplyingRemoteSync = false;
                    }
                }
            }

            @Override
            public void onSyncRequested() {
                if (partyManager.isHost() && player != null) {
                    partyManager.broadcastSync(player.getCurrentPosition(), player.isPlaying());
                }
            }

            @Override
            public void onEmojiReceived(String emoji, String sender) {
                spawnFlyingEmoji(emoji);
            }

            @Override
            public void onChatReceived(String message, String sender) {
                Toast.makeText(PlayerActivity.this, sender + ": " + message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPeerCountChanged(int peerCount) {
                updatePartyUI();
            }

            @Override
            public void onConnectionStatus(boolean isConnected) {
                updatePartyUI();
            }
        });
    }

    private void spawnFlyingEmoji(String emoji) {
        if (flyingEmojiContainer == null) return;
        TextView tv = new TextView(this);
        tv.setText(emoji);
        tv.setTextSize(32);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        int randomOffset = (int) ((Math.random() - 0.5) * 300);
        params.leftMargin = randomOffset;
        tv.setLayoutParams(params);

        flyingEmojiContainer.addView(tv);

        tv.animate()
                .translationY(-600f)
                .scaleX(1.8f)
                .scaleY(1.8f)
                .alpha(0f)
                .setDuration(2200)
                .withEndAction(() -> flyingEmojiContainer.removeView(tv))
                .start();
    }
}

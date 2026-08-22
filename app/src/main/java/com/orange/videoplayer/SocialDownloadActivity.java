package com.orange.videoplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SocialDownloadActivity extends AppCompatActivity {

    private EditText etUrl;
    private ImageButton btnClear;
    private MaterialButton btnPaste;
    private MaterialButton btnResolve;

    private View layoutLoading;
    private View cardError;
    private TextView tvErrorMessage;

    private View cardResult;
    private TextView tvPlatformBadge;
    private TextView tvResultTitle;
    private MaterialButton btnDownloadVideo;
    private MaterialButton btnChooseQuality;
    private MaterialButton btnDownloadAudio;
    private View layoutPostDownload;
    private MaterialButton btnViewDownloads;

    private YtdlpExtractor.MediaInfo currentMedia;
    private boolean isFromShareIntent = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SettingsStore settingsStore = new SettingsStore(this);
        setTheme(settingsStore.getThemeResId());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_social_download);

        initViews();
        setupListeners();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etUrl = findViewById(R.id.et_url);
        btnClear = findViewById(R.id.btn_clear);
        btnPaste = findViewById(R.id.btn_paste);
        btnResolve = findViewById(R.id.btn_resolve);

        layoutLoading = findViewById(R.id.layout_loading);
        cardError = findViewById(R.id.card_error);
        tvErrorMessage = findViewById(R.id.tv_error_message);

        cardResult = findViewById(R.id.card_result);
        tvPlatformBadge = findViewById(R.id.tv_platform_badge);
        tvResultTitle = findViewById(R.id.tv_result_title);
        btnDownloadVideo = findViewById(R.id.btn_download_video);
        btnChooseQuality = findViewById(R.id.btnChooseQuality);
        btnDownloadAudio = findViewById(R.id.btn_download_audio);
        layoutPostDownload = findViewById(R.id.layout_post_download);
        btnViewDownloads = findViewById(R.id.btn_view_downloads);
    }

    private void setupListeners() {
        etUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {
                btnClear.setVisibility((s != null && s.length() > 0) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClear.setOnClickListener(v -> {
            etUrl.setText("");
            cardResult.setVisibility(View.GONE);
            cardError.setVisibility(View.GONE);
            layoutPostDownload.setVisibility(View.GONE);
            btnChooseQuality.setVisibility(View.GONE);
            currentMedia = null;
        });

        btnPaste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                if (item.getText() != null) {
                    String raw = item.getText().toString().trim();
                    String extracted = extractUrl(raw);
                    etUrl.setText(extracted != null ? extracted : raw);
                    etUrl.setSelection(etUrl.getText().length());
                }
            }
        });

        btnResolve.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                showError(getString(R.string.social_error_invalid_url));
                return;
            }
            hideKeyboard();
            isFromShareIntent = false;
            startResolving(url);
        });

        btnViewDownloads.setOnClickListener(v -> {
            startActivity(new Intent(SocialDownloadActivity.this, DownloadsActivity.class));
        });
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String url = null;

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            url = extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
            isFromShareIntent = true;
        } else if (intent.hasExtra("url")) {
            url = intent.getStringExtra("url");
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            url = intent.getData().toString();
        }

        if (url != null && !url.trim().isEmpty()) {
            etUrl.setText(url.trim());
            etUrl.setSelection(etUrl.getText().length());
            startResolving(url.trim());
        }
    }

    private void startResolving(String url) {
        setResolvingState(true);
        cardResult.setVisibility(View.GONE);
        cardError.setVisibility(View.GONE);
        layoutPostDownload.setVisibility(View.GONE);
        currentMedia = null;

        YtdlpExtractor.extract(this, url, new YtdlpExtractor.Callback() {
            @Override
            public void onSuccess(YtdlpExtractor.MediaInfo mediaInfo) {
                if (isFinishing() || isDestroyed()) return;
                setResolvingState(false);
                displayResult(mediaInfo);

                // If opened via share sheet, auto-show the quality picker dialog for 1-tap download
                if (isFromShareIntent && mediaInfo.videoFormats != null && !mediaInfo.videoFormats.isEmpty()) {
                    showQualityDialog(mediaInfo);
                }
            }

            @Override
            public void onError(String arabicMessage) {
                if (isFinishing() || isDestroyed()) return;
                setResolvingState(false);
                showError(arabicMessage);
            }
        });
    }

    private void setResolvingState(boolean resolving) {
        btnResolve.setEnabled(!resolving);
        btnPaste.setEnabled(!resolving);
        layoutLoading.setVisibility(resolving ? View.VISIBLE : View.GONE);
    }

    private void displayResult(YtdlpExtractor.MediaInfo media) {
        currentMedia = media;
        cardError.setVisibility(View.GONE);
        cardResult.setVisibility(View.VISIBLE);
        layoutPostDownload.setVisibility(View.GONE);

        tvPlatformBadge.setText(media.getPlatformName());
        tvResultTitle.setText(media.title);

        // Clicking download video opens quality selection directly so user always chooses the desired quality
        btnDownloadVideo.setText("تحميل الفيديو (اختيار الجودة)");
        btnDownloadVideo.setOnClickListener(v -> {
            if (media.videoFormats != null && !media.videoFormats.isEmpty()) {
                showQualityDialog(media);
            } else {
                startDownload(media.title, media.webpageUrl, null, false);
            }
        });

        // Always enable Audio download option
        btnDownloadAudio.setVisibility(View.VISIBLE);
        btnDownloadAudio.setOnClickListener(v -> {
            startDownload(media.title + " (صوت)", media.webpageUrl, null, true);
        });

        // Choose quality button also triggers the quality dialog
        if (media.videoFormats != null && media.videoFormats.size() > 1) {
            btnChooseQuality.setVisibility(View.VISIBLE);
            btnChooseQuality.setOnClickListener(v -> showQualityDialog(media));
        } else {
            btnChooseQuality.setVisibility(View.GONE);
        }
    }

    private void showQualityDialog(YtdlpExtractor.MediaInfo media) {
        if (isFinishing() || isDestroyed() || media == null) return;

        List<YtdlpExtractor.FormatItem> formats = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        // Option 0: Best Auto Quality
        labels.add("★ أفضل جودة تلقائية (أعلى دقة + صوت مدمج)");
        formats.add(null);

        // 1. Add all Video formats with clear labels and sizes
        for (YtdlpExtractor.FormatItem f : media.videoFormats) {
            StringBuilder sb = new StringBuilder();
            sb.append("🎬 ").append(f.getDisplayQuality());
            sb.append(" · ").append(f.ext.toUpperCase());
            String size = f.getFormattedSize();
            if (!size.isEmpty()) {
                sb.append(" (").append(size).append(")");
            }
            labels.add(sb.toString());
            formats.add(f);
        }

        // 2. Add Audio formats if available
        for (YtdlpExtractor.FormatItem f : media.audioFormats) {
            StringBuilder sb = new StringBuilder();
            sb.append("🎵 ").append(f.getDisplayQuality());
            String size = f.getFormattedSize();
            if (!size.isEmpty()) {
                sb.append(" (").append(size).append(")");
            }
            labels.add(sb.toString());
            formats.add(f);
        }

        if (labels.isEmpty()) return;

        String[] itemsArray = labels.toArray(new String[0]);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.social_quality_title)
                .setItems(itemsArray, (dialog, which) -> {
                    if (which >= 0 && which < formats.size()) {
                        YtdlpExtractor.FormatItem selected = formats.get(which);
                        if (selected == null) {
                            // Best auto
                            startDownload(media.title, media.webpageUrl, null, false);
                        } else {
                            String downloadTitle = media.title + (selected.isAudioOnly ? " (صوت)" : " [" + selected.getDisplayQuality() + "]");
                            startDownload(downloadTitle, media.webpageUrl, selected.formatId, selected.isAudioOnly);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startDownload(String title, String url, String formatId, boolean audioOnly) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(this, DownloadService.class);
            intent.setAction(DownloadService.ACTION_START_DOWNLOAD);
            intent.putExtra(DownloadService.EXTRA_TITLE, title);
            intent.putExtra(DownloadService.EXTRA_URL, url);
            if (formatId != null && !formatId.isEmpty()) {
                intent.putExtra(DownloadService.EXTRA_FORMAT, formatId);
            }
            intent.putExtra(DownloadService.EXTRA_AUDIO_ONLY, audioOnly);
            if (currentMedia != null && currentMedia.thumbnailUrl != null) {
                intent.putExtra(DownloadService.EXTRA_ICON, currentMedia.thumbnailUrl);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }

            Toast.makeText(this, R.string.social_download_started, Toast.LENGTH_SHORT).show();
            layoutPostDownload.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            String err = e.getMessage() != null ? e.getMessage() : getString(R.string.download_failed);
            Toast.makeText(this, getString(R.string.download_failed) + ": " + err, Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message) {
        cardResult.setVisibility(View.GONE);
        btnChooseQuality.setVisibility(View.GONE);
        cardError.setVisibility(View.VISIBLE);
        tvErrorMessage.setText(message != null ? message : getString(R.string.social_error_network));
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://[^\\s<>\"']+").matcher(text);
        return m.find() ? m.group() : null;
    }
}

package com.orange.videoplayer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class WatchPartyDialog {

    public interface Callback {
        void onRoomStateChanged(boolean isActive, boolean isHost, String roomId);
    }

    public static void show(Context context, String streamUrl, String title, Callback callback) {
        if (context == null) return;

        WatchPartyManager manager = WatchPartyManager.getInstance();
        View v = LayoutInflater.from(context).inflate(R.layout.dialog_watch_party, null);

        ImageButton btnClose = v.findViewById(R.id.btn_close);
        LinearLayout layoutActiveRoom = v.findViewById(R.id.layout_active_room);
        LinearLayout layoutInactiveRoom = v.findViewById(R.id.layout_inactive_room);

        TextView tvRoomCode = v.findViewById(R.id.tv_room_code);
        TextView tvRoleBadge = v.findViewById(R.id.tv_role_badge);
        TextView tvPeersCount = v.findViewById(R.id.tv_peers_count);

        MaterialButton btnShareRoom = v.findViewById(R.id.btn_share_room);
        MaterialButton btnCopyLink = v.findViewById(R.id.btn_copy_link);
        MaterialButton btnShareHtmlFile = v.findViewById(R.id.btn_share_html_file);
        MaterialButton btnLeaveRoom = v.findViewById(R.id.btn_leave_room);

        MaterialButton btnCreateRoom = v.findViewById(R.id.btn_create_room);
        EditText etJoinCode = v.findViewById(R.id.et_join_code);
        MaterialButton btnJoinRoom = v.findViewById(R.id.btn_join_room);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(v)
                .setCancelable(true)
                .create();

        btnClose.setOnClickListener(btn -> dialog.dismiss());

        Runnable refreshUI = () -> {
            boolean active = manager.isPartyActive();
            layoutActiveRoom.setVisibility(active ? View.VISIBLE : View.GONE);
            layoutInactiveRoom.setVisibility(active ? View.GONE : View.VISIBLE);

            if (active) {
                String code = manager.getRoomId();
                tvRoomCode.setText(code);
                tvRoleBadge.setText(manager.isHost() ? R.string.watch_party_you_are_host : R.string.watch_party_you_are_guest);
                tvPeersCount.setText(context.getString(R.string.watch_party_connected_peers, manager.getPeerCount()));
            }
        };

        refreshUI.run();

        btnCreateRoom.setOnClickListener(btn -> {
            String roomId = manager.createRoom(streamUrl, title);
            LocalPartyServer.start(roomId, streamUrl, title);
            refreshUI.run();
            Toast.makeText(context, "تم إنشاء الغرفة: " + roomId, Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onRoomStateChanged(true, true, roomId);
        });

        btnJoinRoom.setOnClickListener(btn -> {
            String code = etJoinCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(context, "يرجى إدخال رمز الغرفة", Toast.LENGTH_SHORT).show();
                return;
            }
            manager.joinRoom(code, streamUrl, title);
            LocalPartyServer.start(manager.getRoomId(), streamUrl, title);
            refreshUI.run();
            Toast.makeText(context, "تم الانضمام للغرفة: " + code, Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onRoomStateChanged(true, false, code);
        });

        btnShareRoom.setOnClickListener(btn -> {
            String roomId = manager.getRoomId();
            String shareText = WatchPartyWebPlayer.buildFullShareMessage(roomId, streamUrl, title);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.watch_party_title)));
        });

        btnCopyLink.setOnClickListener(btn -> {
            String roomId = manager.getRoomId();
            String shareText = WatchPartyWebPlayer.buildFullShareMessage(roomId, streamUrl, title);

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Watch Party Link", shareText);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, R.string.watch_party_copied, Toast.LENGTH_SHORT).show();
            }
        });

        btnShareHtmlFile.setOnClickListener(btn -> {
            String roomId = manager.getRoomId();
            WatchPartyWebPlayer.shareHtmlPlayerFile(context, roomId, streamUrl, title);
        });

        btnLeaveRoom.setOnClickListener(btn -> {
            manager.leaveRoom();
            LocalPartyServer.stop();
            refreshUI.run();
            Toast.makeText(context, "تمت مغادرة الغرفة", Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onRoomStateChanged(false, false, null);
        });

        dialog.show();
    }
}

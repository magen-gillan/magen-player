package com.magen.player.addon.ytdl.ipc;

import com.magen.player.addon.ytdl.ipc.IYtDlCallback;

interface IYtDlService {
    int getAddonVersion();
    boolean isReady();
    Bundle getStatus();
    void runInstall(IYtDlCallback callback);
    void runUpdate(boolean nightly, IYtDlCallback callback);
    Bundle resolveStream(String url, in Bundle options);
    Bundle extractPlaylist(String url, in Bundle options);
}

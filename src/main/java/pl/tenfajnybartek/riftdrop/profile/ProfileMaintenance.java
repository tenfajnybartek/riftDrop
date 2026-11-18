package pl.tenfajnybartek.riftdrop.profile;

import pl.tenfajnybartek.riftdrop.storage.ProfileDatabase;

public final class ProfileMaintenance implements Runnable {
    private final PlayerProfiles users;
    private final ProfileDatabase database;
    private int secondsUntilSave;

    public ProfileMaintenance(PlayerProfiles users, ProfileDatabase database) {
        this.users = users;
        this.database = database;
    }

    @Override
    public void run() {
        users.refreshOnlineProfiles();
        if (secondsUntilSave-- > 0) return;
        secondsUntilSave = 29;
        users.save();
        database.refreshRanking();
    }
}

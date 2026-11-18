package pl.tenfajnybartek.dropplugin.tasks;

import pl.tenfajnybartek.dropplugin.database.Database;
import pl.tenfajnybartek.dropplugin.managers.UserManager;

public final class SaverTask implements Runnable {
    private final UserManager users;
    private final Database database;
    private int secondsUntilSave;

    public SaverTask(UserManager users, Database database) {
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

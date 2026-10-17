package cn.zedocn.tech;
public interface ControlledTrial {
    int techPlayers();
    void techPlayers(int players);
    int techFailedSpawns();
    void techSpawnResult(boolean success);
}

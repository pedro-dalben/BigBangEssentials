package com.pedrodalben.bigbangessentials.jobs.database;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JobsRepository extends JdbcRepository {

    public JobsRepository() {
        super();
    }

    /**
     * Checks if database is active/ready.
     */
    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    public CompletableFuture<Map<String, JobProgress>> loadPlayerJobs(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        String sql = "SELECT job_id, level, xp, skill_points, active FROM bbe_player_jobs WHERE uuid = ?";
        return getDatabase().queryList("loadPlayerJobs", sql,
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> {
                    String jobId = rs.getString("job_id").toLowerCase();
                    int level = rs.getInt("level");
                    double xp = rs.getDouble("xp");
                    int skillPoints = rs.getInt("skill_points");
                    boolean active = rs.getBoolean("active");
                    return new JobProgressDb(jobId, level, xp, skillPoints, active);
                }
        ).thenCompose(list -> {
            Map<String, JobProgress> map = new HashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (JobProgressDb dbProg : list) {
                CompletableFuture<Void> skillFuture = loadPlayerJobSkills(uuid, dbProg.jobId)
                        .thenAccept(skills -> {
                            JobProgress progress = new JobProgress(dbProg.level, dbProg.xp, dbProg.skillPoints, dbProg.active, skills);
                            map.put(dbProg.jobId, progress);
                        });
                futures.add(skillFuture);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> map);
        });
    }

    public CompletableFuture<Void> savePlayerJob(UUID uuid, String jobId, JobProgress progress) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_player_jobs (uuid, job_id, level, xp, skill_points, active) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), skill_points = VALUES(skill_points), active = VALUES(active)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_player_jobs (uuid, job_id, level, xp, skill_points, active) VALUES (?, ?, ?, ?, ?, ?)";
        }

        return getDatabase().executeUpdate("savePlayerJob", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobId.toLowerCase());
                    stmt.setInt(3, progress.getLevel());
                    stmt.setDouble(4, progress.getXp());
                    stmt.setInt(5, progress.getSkillPoints());
                    stmt.setInt(6, progress.isActive() ? 1 : 0);
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Map<String, Integer>> loadPlayerJobSkills(UUID uuid, String jobId) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        String sql = "SELECT skill_id, skill_rank FROM bbe_player_job_skills WHERE uuid = ? AND job_id = ?";
        return getDatabase().queryList("loadPlayerJobSkills", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobId.toLowerCase());
                },
                rs -> {
                    String skillId = rs.getString("skill_id").toLowerCase();
                    int rank = rs.getInt("skill_rank");
                    return new SkillDb(skillId, rank);
                }
        ).thenApply(list -> {
            Map<String, Integer> map = new HashMap<>();
            for (SkillDb entry : list) {
                map.put(entry.skillId, entry.rank);
            }
            return map;
        });
    }

    public CompletableFuture<Void> savePlayerJobSkill(UUID uuid, String jobId, String skillId, int rank) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_player_job_skills (uuid, job_id, skill_id, skill_rank) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE skill_rank = VALUES(skill_rank)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_player_job_skills (uuid, job_id, skill_id, skill_rank) VALUES (?, ?, ?, ?)";
        }

        return getDatabase().executeUpdate("savePlayerJobSkill", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobId.toLowerCase());
                    stmt.setString(3, skillId.toLowerCase());
                    stmt.setInt(4, rank);
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<Map<String, Double>> loadPlayerJobEarnings(UUID uuid, long cycleStart) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        String sql = "SELECT job_id, amount FROM bbe_player_job_earnings WHERE uuid = ? AND cycle_start = ?";
        return getDatabase().queryList("loadPlayerJobEarnings", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setLong(2, cycleStart);
                },
                rs -> {
                    String jobId = rs.getString("job_id").toLowerCase();
                    double amount = rs.getDouble("amount");
                    return new EarningDb(jobId, amount);
                }
        ).thenApply(list -> {
            Map<String, Double> map = new HashMap<>();
            for (EarningDb entry : list) {
                map.put(entry.jobId, entry.amount);
            }
            return map;
        });
    }

    public CompletableFuture<Void> savePlayerJobEarnings(UUID uuid, String jobId, long cycleStart, double amount) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_player_job_earnings (uuid, job_id, cycle_start, amount) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_player_job_earnings (uuid, job_id, cycle_start, amount) VALUES (?, ?, ?, ?)";
        }

        return getDatabase().executeUpdate("savePlayerJobEarnings", sql,
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, jobId.toLowerCase());
                    stmt.setLong(3, cycleStart);
                    stmt.setDouble(4, amount);
                }
        ).thenApply(rows -> null);
    }

    public CompletableFuture<List<RankingEntry>> loadRanking(String jobId) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        String sql = "SELECT uuid, level, xp FROM bbe_player_jobs WHERE job_id = ? ORDER BY level DESC, xp DESC LIMIT 10";
        return getDatabase().queryList("loadRanking", sql,
                stmt -> stmt.setString(1, jobId.toLowerCase()),
                rs -> {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int level = rs.getInt("level");
                    double xp = rs.getDouble("xp");
                    return new RankingEntry(uuid, level, xp);
                }
        );
    }

    // Helper classes for parsing db results
    private static class JobProgressDb {
        final String jobId;
        final int level;
        final double xp;
        final int skillPoints;
        final boolean active;

        JobProgressDb(String jobId, int level, double xp, int skillPoints, boolean active) {
            this.jobId = jobId;
            this.level = level;
            this.xp = xp;
            this.skillPoints = skillPoints;
            this.active = active;
        }
    }

    private static class SkillDb {
        final String skillId;
        final int rank;

        SkillDb(String skillId, int rank) {
            this.skillId = skillId;
            this.rank = rank;
        }
    }

    private static class EarningDb {
        final String jobId;
        final double amount;

        EarningDb(String jobId, double amount) {
            this.jobId = jobId;
            this.amount = amount;
        }
    }

    // Domain models used externally
    public static class JobProgress {
        private int level;
        private double xp;
        private int skillPoints;
        private boolean active;
        private final Map<String, Integer> skills;

        public JobProgress(int level, double xp, int skillPoints, boolean active, Map<String, Integer> skills) {
            this.level = level;
            this.xp = xp;
            this.skillPoints = skillPoints;
            this.active = active;
            this.skills = new HashMap<>(skills);
        }

        public JobProgress(int initialLevel) {
            this(initialLevel, 0.0, 0, false, new HashMap<>());
        }

        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }

        public double getXp() { return xp; }
        public void setXp(double xp) { this.xp = xp; }

        public int getSkillPoints() { return skillPoints; }
        public void setSkillPoints(int skillPoints) { this.skillPoints = skillPoints; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public Map<String, Integer> getSkills() { return skills; }
        public int getSkillRank(String skillId) {
            return skills.getOrDefault(skillId.toLowerCase(), 0);
        }
        public void setSkillRank(String skillId, int rank) {
            skills.put(skillId.toLowerCase(), rank);
        }
    }

    public static class RankingEntry {
        private final UUID uuid;
        private final int level;
        private final double xp;

        public RankingEntry(UUID uuid, int level, double xp) {
            this.uuid = uuid;
            this.level = level;
            this.xp = xp;
        }

        public UUID getUuid() { return uuid; }
        public int getLevel() { return level; }
        public double getXp() { return xp; }
    }
}

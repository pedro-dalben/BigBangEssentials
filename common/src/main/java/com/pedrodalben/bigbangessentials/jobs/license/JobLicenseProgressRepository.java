package com.pedrodalben.bigbangessentials.jobs.license;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for persisting and retrieving in-progress job license quests and their objectives.
 */
public class JobLicenseProgressRepository extends JdbcRepository {

    public JobLicenseProgressRepository() {
        super();
    }

    private boolean isDatabaseAvailable() {
        return DatabaseManager.getInstance().isReady();
    }

    private record ProgressDb(String jobId, long startedAt, String status, long lastProgressAt) {}
    private record ObjectiveDb(String jobId, String objectiveId, int currentAmount, int requiredAmount, Long completedAt) {}

    public CompletableFuture<Map<String, InProgressLicense>> loadInProgressLicenses(UUID uuid) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        String sqlProg = "SELECT job_id, started_at, status, last_progress_at FROM bbe_job_license_progress WHERE uuid = ?";
        return getDatabase().queryList("loadLicenseProgress", sqlProg,
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> new ProgressDb(rs.getString("job_id").toLowerCase(), rs.getLong("started_at"), rs.getString("status"), rs.getLong("last_progress_at"))
        ).thenCompose(progList -> {
            String sqlObj = "SELECT job_id, objective_id, current_amount, required_amount, completed_at FROM bbe_job_license_objectives WHERE uuid = ?";
            return getDatabase().queryList("loadLicenseObjectives", sqlObj,
                    stmt -> stmt.setString(1, uuid.toString()),
                    rs -> {
                        long comp = rs.getLong("completed_at");
                        Long compOpt = rs.wasNull() ? null : comp;
                        return new ObjectiveDb(rs.getString("job_id").toLowerCase(), rs.getString("objective_id"), rs.getInt("current_amount"), rs.getInt("required_amount"), compOpt);
                    }
            ).thenApply(objList -> {
                Map<String, List<ObjectiveDb>> objMap = new HashMap<>();
                for (ObjectiveDb obj : objList) {
                    objMap.computeIfAbsent(obj.jobId(), k -> new ArrayList<>()).add(obj);
                }
                Map<String, InProgressLicense> result = new HashMap<>();
                for (ProgressDb prog : progList) {
                    List<ObjectiveDb> myObjs = objMap.getOrDefault(prog.jobId(), Collections.emptyList());
                    List<JobLicenseObjective> objectives = new ArrayList<>();
                    for (ObjectiveDb od : myObjs) {
                        objectives.add(new JobLicenseObjective(od.objectiveId(), "", od.requiredAmount(), od.currentAmount(),
                                od.completedAt() != null ? Optional.of(od.completedAt()) : Optional.empty(),
                                Collections.emptyList(), Collections.emptyList(), false, false, ""));
                    }
                    result.put(prog.jobId(), new InProgressLicense(prog.jobId(), prog.startedAt(), prog.status(), prog.lastProgressAt(), objectives));
                }
                return result;
            });
        });
    }

    public CompletableFuture<Void> saveInProgressLicense(UUID uuid, InProgressLicense progress) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sqlProg;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sqlProg = "INSERT INTO bbe_job_license_progress (uuid, job_id, started_at, status, last_progress_at) VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE status = VALUES(status), last_progress_at = VALUES(last_progress_at)";
        } else {
            sqlProg = "INSERT OR REPLACE INTO bbe_job_license_progress (uuid, job_id, started_at, status, last_progress_at) VALUES (?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("saveLicenseProgress", sqlProg, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, progress.jobId().toLowerCase());
            stmt.setLong(3, progress.startedAt());
            stmt.setString(4, progress.status());
            stmt.setLong(5, progress.lastProgressAt());
        }).thenCompose(rows -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (JobLicenseObjective obj : progress.objectives()) {
                futures.add(saveObjectiveProgress(uuid, progress.jobId(), obj));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        });
    }

    public CompletableFuture<Void> saveObjectiveProgress(UUID uuid, String jobId, JobLicenseObjective obj) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_job_license_objectives (uuid, job_id, objective_id, current_amount, required_amount, completed_at) VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE current_amount = VALUES(current_amount), required_amount = VALUES(required_amount), completed_at = VALUES(completed_at)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_job_license_objectives (uuid, job_id, objective_id, current_amount, required_amount, completed_at) VALUES (?, ?, ?, ?, ?, ?)";
        }
        return getDatabase().executeUpdate("saveObjectiveProgress", sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobId.toLowerCase());
            stmt.setString(3, obj.objectiveId());
            stmt.setInt(4, obj.currentAmount());
            stmt.setInt(5, obj.requiredAmount());
            if (obj.completedAt().isPresent()) {
                stmt.setLong(6, obj.completedAt().get());
            } else {
                stmt.setNull(6, java.sql.Types.BIGINT);
            }
        }).thenApply(rows -> null);
    }

    public CompletableFuture<Void> deleteInProgressLicense(UUID uuid, String jobId) {
        if (!isDatabaseAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        String sqlProg = "DELETE FROM bbe_job_license_progress WHERE uuid = ? AND job_id = ?";
        String sqlObj = "DELETE FROM bbe_job_license_objectives WHERE uuid = ? AND job_id = ?";
        return getDatabase().executeUpdate("deleteLicenseProgress", sqlProg, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobId.toLowerCase());
        }).thenCompose(rows -> getDatabase().executeUpdate("deleteLicenseObjectives", sqlObj, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, jobId.toLowerCase());
        })).thenApply(rows -> null);
    }
}

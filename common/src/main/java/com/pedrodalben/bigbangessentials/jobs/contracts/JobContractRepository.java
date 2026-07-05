package com.pedrodalben.bigbangessentials.jobs.contracts;

import com.pedrodalben.bigbangessentials.database.DatabaseManager;
import com.pedrodalben.bigbangessentials.database.DatabaseType;
import com.pedrodalben.bigbangessentials.database.api.DatabaseAPI;
import com.pedrodalben.bigbangessentials.database.repository.JdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JobContractRepository extends JdbcRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobContractRepository.class);
    private static final JobContractRepository INSTANCE = new JobContractRepository();

    public static JobContractRepository getInstance() {
        return INSTANCE;
    }

    private JobContractRepository() {}

    public void saveContract(JobContract contract) {
        if (!DatabaseAPI.isAvailable() || contract == null) return;
        String sql;
        if (DatabaseManager.getInstance().getType() == DatabaseType.MYSQL) {
            sql = "INSERT INTO bbe_jobs_contracts (contract_id, uuid, template_id, period_type, generated_at, expires_at, status, objective_snapshot, reward_snapshot, seed_reference, progress_amount, claimed_at, reroll_count) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE status = VALUES(status), progress_amount = VALUES(progress_amount), claimed_at = VALUES(claimed_at), reroll_count = VALUES(reroll_count)";
        } else {
            sql = "INSERT OR REPLACE INTO bbe_jobs_contracts (contract_id, uuid, template_id, period_type, generated_at, expires_at, status, objective_snapshot, reward_snapshot, seed_reference, progress_amount, claimed_at, reroll_count) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }
        getDatabase().executeUpdate("saveJobContract", sql, stmt -> {
            stmt.setString(1, contract.contractId());
            stmt.setString(2, contract.playerUuid().toString());
            stmt.setString(3, contract.templateId());
            stmt.setString(4, contract.periodType().name());
            stmt.setLong(5, contract.generatedAt());
            stmt.setLong(6, contract.expiresAt());
            stmt.setString(7, contract.status().name());
            stmt.setString(8, contract.objectiveSnapshot());
            stmt.setString(9, contract.rewardSnapshot());
            stmt.setString(10, contract.seedReference() != null ? contract.seedReference() : "");
            stmt.setInt(11, contract.progressAmount());
            if (contract.claimedAt() != null) {
                stmt.setLong(12, contract.claimedAt());
            } else {
                stmt.setNull(12, java.sql.Types.BIGINT);
            }
            stmt.setInt(13, contract.rerollCount());
        });
    }

    public List<JobContract> getActiveContracts(UUID playerUuid) {
        if (!DatabaseAPI.isAvailable() || playerUuid == null) return List.of();
        String sql = "SELECT * FROM bbe_jobs_contracts WHERE uuid = ? AND status IN ('ACTIVE', 'COMPLETED') ORDER BY generated_at DESC";
        return getDatabase().queryList("getActiveContracts", sql, stmt -> {
            stmt.setString(1, playerUuid.toString());
        }, rs -> {
            return new JobContract(
                rs.getString("contract_id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("template_id"),
                ContractPeriodType.valueOf(rs.getString("period_type")),
                rs.getLong("generated_at"),
                rs.getLong("expires_at"),
                ContractStatus.valueOf(rs.getString("status")),
                rs.getString("objective_snapshot"),
                rs.getString("reward_snapshot"),
                rs.getString("seed_reference"),
                rs.getInt("progress_amount"),
                rs.getObject("claimed_at") != null ? rs.getLong("claimed_at") : null,
                rs.getInt("reroll_count")
            );
        }).join();
    }

    public Optional<JobContract> getContractBySeed(UUID playerUuid, String seedReference) {
        if (!DatabaseAPI.isAvailable() || playerUuid == null || seedReference == null) return Optional.empty();
        String sql = "SELECT * FROM bbe_jobs_contracts WHERE uuid = ? AND seed_reference = ?";
        return getDatabase().querySingle("getContractBySeed", sql, stmt -> {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, seedReference);
        }, rs -> {
            return new JobContract(
                rs.getString("contract_id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("template_id"),
                ContractPeriodType.valueOf(rs.getString("period_type")),
                rs.getLong("generated_at"),
                rs.getLong("expires_at"),
                ContractStatus.valueOf(rs.getString("status")),
                rs.getString("objective_snapshot"),
                rs.getString("reward_snapshot"),
                rs.getString("seed_reference"),
                rs.getInt("progress_amount"),
                rs.getObject("claimed_at") != null ? rs.getLong("claimed_at") : null,
                rs.getInt("reroll_count")
            );
        }).join();
    }
}

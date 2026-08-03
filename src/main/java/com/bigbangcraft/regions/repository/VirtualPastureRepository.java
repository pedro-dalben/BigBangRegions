package com.bigbangcraft.regions.repository;

import com.bigbangcraft.regions.storage.DatabaseManager;
import com.bigbangcraft.regions.virtualpasture.VirtualPastureRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable source of truth; the service owns the hot aggregate counters. */
public final class VirtualPastureRepository {
    private final DatabaseManager databaseManager;

    public VirtualPastureRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<VirtualPastureRecord> loadAll() {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "SELECT dimension_key, block_pos, region_id, owner_uuid, chunk_x, chunk_z, state, pending_expires_at FROM virtual_pastures");
                 ResultSet result = statement.executeQuery()) {
                List<VirtualPastureRecord> records = new ArrayList<>();
                while (result.next()) records.add(read(result));
                return records;
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to load virtual pasture index", error);
            }
        }
    }

    public void upsert(VirtualPastureRecord record) {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "INSERT INTO virtual_pastures(dimension_key, block_pos, region_id, owner_uuid, chunk_x, chunk_z, state, pending_expires_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(dimension_key, block_pos) DO UPDATE SET "
                    + "region_id=excluded.region_id, owner_uuid=excluded.owner_uuid, chunk_x=excluded.chunk_x, chunk_z=excluded.chunk_z, "
                    + "state=excluded.state, pending_expires_at=excluded.pending_expires_at")) {
                statement.setString(1, record.dimensionKey());
                statement.setLong(2, record.blockPos());
                statement.setString(3, record.regionId());
                statement.setString(4, record.ownerUuid() == null ? null : record.ownerUuid().toString());
                statement.setInt(5, record.chunkX());
                statement.setInt(6, record.chunkZ());
                statement.setString(7, record.state().name());
                if (record.pendingExpiresAt() == null) statement.setNull(8, java.sql.Types.BIGINT);
                else statement.setLong(8, record.pendingExpiresAt());
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to save virtual pasture index", error);
            }
        }
    }

    public void delete(String dimensionKey, long blockPos) {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "DELETE FROM virtual_pastures WHERE dimension_key=? AND block_pos=?")) {
                statement.setString(1, dimensionKey);
                statement.setLong(2, blockPos);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to delete virtual pasture index", error);
            }
        }
    }

    public void deleteRegion(String regionId) {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "DELETE FROM virtual_pastures WHERE region_id=?")) {
                statement.setString(1, regionId);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to delete virtual pasture region index", error);
            }
        }
    }

    public void transferOwner(String regionId, UUID ownerUuid) {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "UPDATE virtual_pastures SET owner_uuid=? WHERE region_id=?")) {
                statement.setString(1, ownerUuid == null ? null : ownerUuid.toString());
                statement.setString(2, regionId);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to transfer virtual pasture ownership", error);
            }
        }
    }

    public void deleteExpiredPending(long now) {
        synchronized (databaseManager) {
            try (PreparedStatement statement = databaseManager.getConnection().prepareStatement(
                "DELETE FROM virtual_pastures WHERE state='PENDING' AND pending_expires_at<?")) {
                statement.setLong(1, now);
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException("Failed to clean virtual pasture reservations", error);
            }
        }
    }

    private static VirtualPastureRecord read(ResultSet result) throws SQLException {
        String owner = result.getString("owner_uuid");
        long expires = result.getLong("pending_expires_at");
        Long pendingExpiresAt = result.wasNull() ? null : expires;
        return new VirtualPastureRecord(result.getString("dimension_key"), result.getLong("block_pos"),
            result.getString("region_id"), owner == null ? null : UUID.fromString(owner), result.getInt("chunk_x"),
            result.getInt("chunk_z"), VirtualPastureRecord.State.valueOf(result.getString("state")), pendingExpiresAt);
    }
}

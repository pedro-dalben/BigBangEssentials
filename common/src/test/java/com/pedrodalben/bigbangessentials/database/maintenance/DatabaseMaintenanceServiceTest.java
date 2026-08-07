package com.pedrodalben.bigbangessentials.database.maintenance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMaintenanceServiceTest {

    @Test
    void testDatabaseMaintenanceServiceInstantiationAndManualRun() {
        DatabaseMaintenanceService service = DatabaseMaintenanceService.getInstance();
        assertNotNull(service);

        assertDoesNotThrow(service::performMaintenance);
    }
}

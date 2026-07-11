package com.pedrodalben.bigbangessentials.rankup;

import com.pedrodalben.bigbangessentials.rankup.database.RankupRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RankupPersistenceTest {

    @Test
    void testRepositoryInstance() {
        RankupRepository repo = new RankupRepository();
        assertNotNull(repo);
    }
}

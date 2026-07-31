package com.pedrodalben.bigbangessentials.pokemarket;

import com.google.gson.JsonObject;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingStatus;
import com.pedrodalben.bigbangessentials.pokemarket.model.ListingType;
import com.pedrodalben.bigbangessentials.pokemarket.model.TradeOperationStatus;
import com.pedrodalben.bigbangessentials.pokemarket.service.ListingStateMachine;
import com.pedrodalben.bigbangessentials.pokemarket.service.MarketPricingService;
import com.pedrodalben.bigbangessentials.pokemarket.service.PokeMarketTradeService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PokeMarketDomainTest {
    @Test void stateMachineRejectsArbitraryTransitions() {
        assertTrue(ListingStateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.RESERVED));
        assertFalse(ListingStateMachine.canTransition(ListingStatus.SOLD, ListingStatus.ACTIVE));
        assertThrows(IllegalStateException.class, () -> ListingStateMachine.transition(ListingStatus.SOLD, ListingStatus.ACTIVE));
    }

    @Test void stateMachineAllowsTradeTransitions() {
        assertTrue(ListingStateMachine.canTransition(ListingStatus.RESERVED, ListingStatus.TRADED));
        assertTrue(ListingStateMachine.canTransition(ListingStatus.TRADED, ListingStatus.CLAIMED));
        assertFalse(ListingStateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.TRADED));
    }

    @Test void stateMachineRejectsInvalidTradeTransitions() {
        assertFalse(ListingStateMachine.canTransition(ListingStatus.PREPARING, ListingStatus.TRADED));
        assertFalse(ListingStateMachine.canTransition(ListingStatus.SOLD, ListingStatus.TRADED));
    }

    @Test void stateMachineAllowsCancellingReservedListings() {
        assertTrue(ListingStateMachine.canTransition(ListingStatus.RESERVED, ListingStatus.CANCELLED));
        assertTrue(ListingStateMachine.canTransition(ListingStatus.RESERVED, ListingStatus.ADMIN_CANCELLED));
        assertTrue(ListingStateMachine.canTransition(ListingStatus.ADMIN_CANCELLED, ListingStatus.CLAIMED));
    }

    @Test void tradeOperationStatusesAreConfigured() {
        assertNotNull(TradeOperationStatus.valueOf("CREATED"));
        assertNotNull(TradeOperationStatus.valueOf("LISTING_RESERVED"));
        assertNotNull(TradeOperationStatus.valueOf("OFFER_VALIDATED"));
        assertNotNull(TradeOperationStatus.valueOf("OFFER_IN_ESCROW"));
        assertNotNull(TradeOperationStatus.valueOf("CLAIMS_CREATED"));
        assertNotNull(TradeOperationStatus.valueOf("COMPLETED"));
        assertNotNull(TradeOperationStatus.valueOf("RECONCILIATION_REQUIRED"));
        assertNotNull(TradeOperationStatus.valueOf("FAILED"));
    }

    @Test void tradeRequirementValidationSpecies() throws Exception {
        Method validate = PokeMarketTradeService.class.getDeclaredMethod("validateRequirements",
            com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary.class, JsonObject.class);
        validate.setAccessible(true);
        var summary = new com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary(
            java.util.UUID.randomUUID(), "Pikachu", "", false, 50, 3);
        JsonObject req = new JsonObject();
        req.addProperty("species", "Pikachu");
        assertNull(validate.invoke(null, summary, req), "Matching species should pass");
        req.addProperty("species", "Raichu");
        assertNotNull(validate.invoke(null, summary, req), "Mismatched species should fail");
    }

    @Test void tradeRequirementValidationShiny() throws Exception {
        Method validate = PokeMarketTradeService.class.getDeclaredMethod("validateRequirements",
            com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary.class, JsonObject.class);
        validate.setAccessible(true);
        var nonShiny = new com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary(
            java.util.UUID.randomUUID(), "Charizard", "", false, 50, 3);
        var shiny = new com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary(
            java.util.UUID.randomUUID(), "Charizard", "", true, 50, 3);
        JsonObject reqShiny = new JsonObject(); reqShiny.addProperty("shiny", "required");
        assertNotNull(validate.invoke(null, nonShiny, reqShiny));
        assertNull(validate.invoke(null, shiny, reqShiny));
        JsonObject reqNoShiny = new JsonObject(); reqNoShiny.addProperty("shiny", "prohibited");
        assertNull(validate.invoke(null, nonShiny, reqNoShiny));
        assertNotNull(validate.invoke(null, shiny, reqNoShiny));
    }

    @Test void tradeRequirementValidationLevel() throws Exception {
        Method validate = PokeMarketTradeService.class.getDeclaredMethod("validateRequirements",
            com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary.class, JsonObject.class);
        validate.setAccessible(true);
        var pkm = new com.pedrodalben.bigbangessentials.pokemarket.cobblemon.PokemonSummary(
            java.util.UUID.randomUUID(), "Gengar", "", false, 75, 5);
        JsonObject req = new JsonObject(); req.addProperty("level_min", 50); req.addProperty("level_max", 100);
        assertNull(validate.invoke(null, pkm, req));
        req.addProperty("level_min", 80);
        assertNotNull(validate.invoke(null, pkm, req));
    }

    @Test void moneyMathIsDeterministic() {
        assertEquals(new BigDecimal("2000.00"), MarketPricingService.fee(new BigDecimal("100000.00"), new BigDecimal("2.00"), BigDecimal.ZERO, new BigDecimal("100.00")));
        assertEquals(new BigDecimal("95000.00"), MarketPricingService.net(new BigDecimal("100000.00"), new BigDecimal("5000.00")));
        assertThrows(IllegalArgumentException.class, () -> MarketPricingService.normalize(new BigDecimal("NaN")));
    }

    @Test void listingTypeEnumHasTrade() {
        assertTrue(ListingType.valueOf("POKEMON_TRADE") == ListingType.POKEMON_TRADE);
        assertTrue(ListingType.valueOf("MONEY") == ListingType.MONEY);
    }
}

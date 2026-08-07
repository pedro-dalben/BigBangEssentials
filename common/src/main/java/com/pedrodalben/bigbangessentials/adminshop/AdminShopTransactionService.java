package com.pedrodalben.bigbangessentials.adminshop;

import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationReceipt;
import com.pedrodalben.bigbangessentials.api.economy.EconomyOperationStatus;
import com.pedrodalben.bigbangessentials.api.permissions.PermissionAPI;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.economy.gems.api.*;
import com.pedrodalben.bigbangessentials.economy.gems.service.GemsServiceImpl;
import com.pedrodalben.bigbangessentials.economy.managers.EconomyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class AdminShopTransactionService {
    public enum Operation { BUY, SELL }
    public record Result(boolean success, String message) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminShopTransactionService.class);
    private static final AdminShopTransactionService INSTANCE = new AdminShopTransactionService();
    private final AdminShopManager manager = AdminShopManager.getInstance();
    private final GemsServiceImpl gems = new GemsServiceImpl();
    private final Map<String, CompletableFuture<?>> productQueues = new ConcurrentHashMap<>();

    public static AdminShopTransactionService getInstance() { return INSTANCE; }
    static String currencyPermission(String currency) { return "bigbangessentials.adminshop." + currency; }

    public CompletableFuture<Result> executeAsync(ServerPlayer player, String productId, Operation operation) {
        return executeAsync(player, productId, operation, -1);
    }

    public CompletableFuture<Result> executeAsync(ServerPlayer player, String productId, Operation operation, int quantity) {
        if (player == null) return CompletableFuture.completedFuture(fail("§cJogador indisponível."));
        if (manager.getStateStatus() == AdminShopManager.StateStatus.ERROR || manager.getStateStatus() == AdminShopManager.StateStatus.DATABASE_UNAVAILABLE) {
            return CompletableFuture.completedFuture(fail("§cO AdminShop está indisponível no momento."));
        }

        CompletableFuture<Result> resultFuture = new CompletableFuture<>();

        productQueues.compute(productId, (id, currentQueue) -> {
            CompletableFuture<?> previous = (currentQueue != null) ? currentQueue : CompletableFuture.completedFuture(null);

            CompletableFuture<Void> myTurn = previous.handle((ignored, err) -> null)
                    .thenCompose(ignored -> runSingleTransactionPipeline(player, productId, operation, quantity))
                    .handle((res, err) -> {
                        if (err != null) {
                            Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                            if (cause instanceof com.pedrodalben.bigbangessentials.adminshop.exception.AdminShopConcurrencyException) {
                                resultFuture.complete(fail("§cConflito de transação simultânea. Por favor, tente novamente."));
                            } else if (cause instanceof com.pedrodalben.bigbangessentials.adminshop.exception.AdminShopUnavailableException) {
                                resultFuture.complete(fail("§cO AdminShop está temporariamente indisponível."));
                            } else {
                                String msg = cause.getMessage();
                                resultFuture.complete(fail(msg != null && msg.startsWith("§") ? msg : "§cA transação falhou. ID do produto: " + productId));
                            }
                        } else {
                            resultFuture.complete(res);
                        }
                        return null;
                    });

            return myTurn;
        });

        resultFuture.whenComplete((res, err) -> {
            productQueues.computeIfPresent(productId, (id, queueFuture) -> queueFuture.isDone() ? null : queueFuture);
        });

        return resultFuture;
    }

    private CompletableFuture<Result> runSingleTransactionPipeline(ServerPlayer player, String productId, Operation operation, int quantity) {
        if (productId == null || operation == null) return CompletableFuture.completedFuture(fail("§cOperação indisponível."));
        AdminShopConfig.Product product = manager.config().product(productId);
        if (product == null) return CompletableFuture.completedFuture(fail("§cProduto não encontrado."));

        int q = quantity > 0 ? quantity : product.quantity;
        q = Math.max(1, Math.min(q, product.maxQuantity > 0 ? product.maxQuantity : 64));
        final int finalQuantity = q;

        boolean buy = operation == Operation.BUY;
        BigDecimal unitPrice = price(product, buy, productId);
        if ((buy && !product.buyEnabled) || (!buy && (!product.sellEnabled || product.isCommand())) || unitPrice == null)
            return CompletableFuture.completedFuture(fail("§cOperação não disponível para este produto."));

        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(finalQuantity))
                .setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());

        String currency = manager.config().currency(productId);
        if (currency == null || !PermissionAPI.hasPermission(player.getUUID(), currencyPermission(currency))
                || product.permission != null && !product.permission.isBlank() && !PermissionAPI.hasPermission(player.getUUID(), product.permission))
            return CompletableFuture.completedFuture(fail("§cPermissão insuficiente."));

        ItemStack stack = product.stack(finalQuantity);
        if (!product.isCommand() && (stack.isEmpty() || buy && !hasRoom(player, stack) || !buy && count(player, stack) < finalQuantity))
            return CompletableFuture.completedFuture(fail(buy ? "§cEspaço insuficiente no inventário." : "§cItens insuficientes no inventário."));
        if (product.isCommand() && (product.command == null || !product.command.contains("{transaction}")))
            return CompletableFuture.completedFuture(fail("§cComando inválido."));

        String tx = UUID.randomUUID().toString();
        String economicKey = "adminshop:" + (buy ? "buy:" : "sell:") + tx;

        return manager.sql.reserveTransactionAsync(tx, player.getUUID(), productId, operation, finalQuantity, totalPrice, currency, economicKey, product.stock, product.limit)
                .thenCompose(reserve -> {
                    if (!reserve.success()) {
                        return CompletableFuture.completedFuture(fail(reserve.reason()));
                    }

                    if (product.stock >= 0 && reserve.remaining() >= 0) manager.state.remaining.put(productId, reserve.remaining());
                    if (product.limit >= 0 && reserve.used() >= 0) manager.state.limits.put(player.getUUID() + ":" + productId, reserve.used());
                    if (reserve.demand() != -1) manager.state.demand.put(productId, reserve.demand());

                    Prepared prepared = new Prepared(player, productId, product, operation, buy, currency, totalPrice, stack.copy(), tx,
                            economicKey, reserve.used(), reserve.remaining(), reserve.demand(),
                            product.limit >= 0, product.stock >= 0, true,
                            product.stock >= 0 ? "RESERVED" : "NOT_APPLICABLE", new Object(), finalQuantity);

                    CompletableFuture<Long> oldGems = "gems".equals(currency)
                            ? gems.getBalanceAsync(player.getUUID()).thenApply(balance -> balance.totalBalance())
                            : CompletableFuture.completedFuture(0L);

                    return oldGems.thenCompose(balance -> finance(prepared, balance))
                            .thenCompose(finance -> {
                                AtomicBoolean statePersisted = new AtomicBoolean(true);
                                return onServer(player, () -> applyItems(prepared))
                                        .thenCompose(items -> finish(prepared, finance, items, statePersisted)
                                                .exceptionallyCompose(error -> compensate(prepared, finance, items, statePersisted.get(), error)));
                            })
                            .exceptionallyCompose(error -> compensate(prepared, financeFrom(error), null, false, error));
                });
    }

    private CompletableFuture<Finance> finance(Prepared p, long oldGems) {
        if (p.buy) {
            if ("gems".equals(p.currency)) {
                GemReservationRequest request = new GemReservationRequest(p.player.getUUID(), p.price.longValueExact(), "adminshop",
                        "buy_" + p.productId, p.economicKey + ":reserve", p.tx, Duration.ofSeconds(30), metadata(p.tx));
                return gems.reserveAsync(request).thenCompose(reserved -> {
                    if (!reserved.success()) return failed(new SagaFailure("§cGemas insuficientes."));
                    GemCaptureRequest capture = new GemCaptureRequest(reserved.reservationId(), "adminshop", "buy_" + p.productId,
                            p.player.getUUID(), p.economicKey, p.tx, metadata(p.tx));
                    return gems.captureAsync(capture).thenCompose(captured -> {
                        if (!captured.success()) return failed(new FinanceFailure(new Finance(null, reserved.reservationId(), false), "§cGemas insuficientes."));
                        return CompletableFuture.completedFuture(new Finance(gemReceipt(captured, p.player.getUUID(), p.price, oldGems, p.economicKey), reserved.reservationId(), true));
                    });
                }).exceptionallyCompose(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof FinanceFailure financeFailure) return failed(financeFailure);
                    return failed(cause);
                });
            }
            return EconomyManager.getInstance().debitAsync(p.player.getUUID(), p.price, p.economicKey, "AdminShop purchase", metadata(p.tx))
                    .thenCompose(receipt -> receipt.status() == EconomyOperationStatus.COMPLETED
                            ? CompletableFuture.completedFuture(new Finance(receipt, null, true))
                            : failed(new SagaFailure(receipt.status() == EconomyOperationStatus.REJECTED ? "§cSaldo insuficiente." : "§cEconomia indisponível.")));
        }
        if ("gems".equals(p.currency)) {
            GemCreditRequest request = new GemCreditRequest(p.player.getUUID(), p.price.longValueExact(), "adminshop", "sell_" + p.productId,
                    p.player.getUUID(), p.economicKey, p.tx, metadata(p.tx));
            return gems.creditAsync(request).thenCompose(credited -> credited.success()
                    ? CompletableFuture.completedFuture(new Finance(gemReceipt(credited, p.player.getUUID(), p.price, oldGems, p.economicKey), null, true))
                    : failed(new SagaFailure("§cO crédito em gemas não pôde ser aplicado.")));
        }
        return EconomyManager.getInstance().creditAsync(p.player.getUUID(), p.price, p.economicKey, "AdminShop sale", metadata(p.tx))
                .thenCompose(receipt -> receipt.status() == EconomyOperationStatus.COMPLETED
                        ? CompletableFuture.completedFuture(new Finance(receipt, null, true))
                        : failed(new SagaFailure("§cO crédito não pôde ser aplicado.")));
    }

    private ItemOutcome applyItems(Prepared p) {
        if (p.buy && p.product.isCommand()) {
            String command = p.product.command.replace("{player}", p.player.getName().getString()).replace("{transaction}", p.tx);
            if (command.startsWith("/")) command = command.substring(1);
            try {
                int result = p.player.getServer().getCommands().getDispatcher().execute(command, p.player.createCommandSourceStack().withPermission(4));
                return new ItemOutcome(result > 0, result > 0, 0, true);
            } catch (Exception error) {
                return new ItemOutcome(false, false, 0, true);
            }
        }
        if (p.buy) {
            int accepted = addItemsStrict(p.player, p.stack);
            return new ItemOutcome(accepted == p.quantity, accepted > 0, accepted, false);
        }
        int removed = remove(p.player, p.stack, p.quantity);
        return new ItemOutcome(removed == p.quantity, removed > 0, removed, false);
    }

    private CompletableFuture<Result> finish(Prepared p, Finance finance, ItemOutcome items, AtomicBoolean statePersisted) {
        if (!items.success) return failed(new SagaFailure("§cO item não pôde ser aplicado."));
        return manager.sql.updateAuditAsync(p.tx, p.buy ? AdminShopAuditStatus.MONEY_APPLIED : AdminShopAuditStatus.ITEM_APPLIED,
                        finance.receipt, "APPLIED", p.stockStage, "RESERVED", "RESERVED", null)
                .thenCompose(audited -> audited ? manager.sql.logAsync(p.tx, p.player.getUUID(), p.productId, p.operation.name(), p.currency, p.price) : failed(new SagaFailure("§cA auditoria da transação falhou.")))
                .thenCompose(logged -> logged ? manager.sql.updateAuditAsync(p.tx, AdminShopAuditStatus.COMPLETED, finance.receipt,
                        "APPLIED", p.stockStage, "APPLIED", "APPLIED", null).thenApply(done -> {
                            if (!done) throw new SagaFailure("§cA auditoria final não pôde ser gravada.");
                            manager.state.processed.remove(p.tx);
                            return new Result(true, "§aTransação concluída: §f" + (p.product.displayName == null ? p.productId : p.product.displayName) + " §7(x" + p.quantity + " " + p.price + " " + p.currency + ")");
                        }) : failed(new SagaFailure("§cO registro da transação não pôde ser gravado.")));
    }

    private CompletableFuture<Result> compensate(Prepared p, Finance finance, ItemOutcome items, boolean statePersisted, Throwable failure) {
        Throwable error = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        String reason = error == null || error.getMessage() == null ? "transaction_failed" : error.getMessage();

        CompletableFuture<Boolean> item = restoreItems(p, items);
        CompletableFuture<Boolean> money = compensateAsync(p, finance);
        CompletableFuture<Boolean> state = manager.sql.releaseTransactionAsync(p.tx, p.player.getUUID(), p.productId, p.operation, p.quantity, p.product.stock, p.product.limit, reason);

        rollbackRamState(p);

        return CompletableFuture.allOf(item, state, money).thenCompose(ignored -> {
            boolean ok = Boolean.TRUE.equals(item.getNow(false)) && Boolean.TRUE.equals(state.getNow(false)) && Boolean.TRUE.equals(money.getNow(false));
            AdminShopAuditStatus status = ok ? AdminShopAuditStatus.ROLLED_BACK : AdminShopAuditStatus.RECONCILIATION_REQUIRED;
            LOGGER.error("AdminShop transaction compensation [tx={} player={} product={} op={}]: status={} failure={}", p.tx, p.player.getUUID(), p.productId, p.operation, status, reason, error);
            return manager.sql.updateAuditAsync(p.tx, status, finance == null ? null : finance.receipt,
                    ok ? "ROLLED_BACK" : "RECONCILIATION_REQUIRED", p.stockStage, "ROLLED_BACK", "ROLLED_BACK", reason)
                    .thenApply(audited -> ok && audited ? fail(reason) : fail("§cA transação falhou e requer reconciliação. ID: " + p.tx));
        });
    }

    void rollbackRamState(Prepared p) {
        String limitKey = p.player.getUUID() + ":" + p.productId;
        if (p.buy && p.product.stock >= 0) {
            long curStock = manager.state.remaining.getOrDefault(p.productId, p.product.stock);
            manager.state.remaining.put(p.productId, curStock + p.quantity);
        }
        if (p.product.limit >= 0) {
            long curUsed = manager.state.limits.getOrDefault(limitKey, 0L);
            long restoredUsed = Math.max(0L, curUsed - p.quantity);
            if (restoredUsed == 0L && !p.hadLimit) {
                manager.state.limits.remove(limitKey);
            } else {
                manager.state.limits.put(limitKey, restoredUsed);
            }
        }
        long curDemand = manager.state.demand.getOrDefault(p.productId, p.oldDemand);
        long restoredDemand = p.buy ? curDemand - p.quantity : curDemand + p.quantity;
        if (restoredDemand != 0 || p.hadDemand) {
            manager.state.demand.put(p.productId, restoredDemand);
        } else {
            manager.state.demand.remove(p.productId);
        }
    }

    private static Finance financeFrom(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof FinanceFailure failure ? failure.finance : null;
    }

    private CompletableFuture<Boolean> compensateAsync(Prepared p, Finance finance) {
        if (finance == null) return CompletableFuture.completedFuture(true);
        if ("gems".equals(p.currency)) {
            if (finance.reservation != null && !finance.captured) return gems.releaseAsync(new GemReleaseRequest(finance.reservation, "adminshop", "rollback", p.player.getUUID(), "rollback", p.economicKey + ":release", p.tx, metadata(p.tx))).thenApply(GemOperationResult::success);
            if (finance.receipt == null || finance.receipt.status() != EconomyOperationStatus.COMPLETED) return CompletableFuture.completedFuture(true);
            return (p.buy ? gems.creditAsync(new GemCreditRequest(p.player.getUUID(), p.price.longValueExact(), "adminshop", "compensate", p.player.getUUID(), p.economicKey + ":compensate", p.tx, metadata(p.tx)))
                    : gems.debitAsync(new GemDebitRequest(p.player.getUUID(), p.price.longValueExact(), "adminshop", "compensate", p.player.getUUID(), p.economicKey + ":compensate", p.tx, metadata(p.tx)))).thenApply(GemOperationResult::success);
        }
        if (finance.receipt == null || finance.receipt.status() != EconomyOperationStatus.COMPLETED) return CompletableFuture.completedFuture(true);
        return p.buy ? EconomyManager.getInstance().creditAsync(p.player.getUUID(), p.price, p.economicKey + ":compensate", "AdminShop purchase compensation", metadata(p.tx)).thenApply(r -> r.status() == EconomyOperationStatus.COMPLETED)
                : EconomyManager.getInstance().debitAsync(p.player.getUUID(), p.price, p.economicKey + ":compensate", "AdminShop sale compensation", metadata(p.tx)).thenApply(r -> r.status() == EconomyOperationStatus.COMPLETED);
    }

    private CompletableFuture<Boolean> restoreItems(Prepared p, ItemOutcome items) {
        if (items == null || !items.applied) return CompletableFuture.completedFuture(true);
        return onServer(p.player, () -> p.buy ? (!items.command && remove(p.player, p.stack, items.amount) == items.amount)
                : addItemsStrict(p.player, p.stack.copyWithCount(items.amount)) == items.amount);
    }

    private CompletableFuture<Boolean> persistState(Prepared p) {
        return CompletableFuture.supplyAsync(() -> {
            long used = manager.state.limits.getOrDefault(p.player.getUUID() + ":" + p.productId, p.oldUsed);
            long remaining = manager.state.remaining.getOrDefault(p.productId, p.oldRemaining);
            long demand = manager.state.demand.getOrDefault(p.productId, p.oldDemand);
            manager.saveStateDelta(p.productId, p.oldRemaining, remaining, p.player.getUUID(), p.oldUsed, used, p.oldDemand, demand,
                    p.hadRemaining, p.hadLimit, p.hadDemand, manager.state.remaining.containsKey(p.productId),
                    manager.state.limits.containsKey(p.player.getUUID() + ":" + p.productId), manager.state.demand.containsKey(p.productId));
            return true;
        });
    }

    private CompletableFuture<Boolean> restorePrepared(Prepared p, boolean persisted) {
        return onServer(p.player, () -> {
            String limitKey = p.player.getUUID() + ":" + p.productId;
            long currentRemaining = manager.state.remaining.getOrDefault(p.productId, p.oldRemaining);
            long currentUsed = manager.state.limits.getOrDefault(limitKey, p.oldUsed);
            long currentDemand = manager.state.demand.getOrDefault(p.productId, p.oldDemand);
            boolean currentHasRemaining = manager.state.remaining.containsKey(p.productId);
            boolean currentHasLimit = manager.state.limits.containsKey(limitKey);
            boolean currentHasDemand = manager.state.demand.containsKey(p.productId);

            if (p.buy && p.product.stock >= 0) {
                long curStock = manager.state.remaining.getOrDefault(p.productId, p.product.stock);
                manager.state.remaining.put(p.productId, curStock + p.quantity);
            }
            if (p.product.limit >= 0) {
                long curUsed = manager.state.limits.getOrDefault(limitKey, 0L);
                long restoredUsed = Math.max(0L, curUsed - p.quantity);
                if (restoredUsed == 0L && !p.hadLimit) {
                    manager.state.limits.remove(limitKey);
                } else {
                    manager.state.limits.put(limitKey, restoredUsed);
                }
            }
            manager.state.processed.remove(p.tx);

            if (!persisted) return CompletableFuture.completedFuture(true);

            long targetRemaining = p.buy && p.product.stock >= 0 ? currentRemaining + p.quantity : currentRemaining;
            long targetUsed = p.product.limit >= 0 ? Math.max(0L, currentUsed - p.quantity) : currentUsed;

            return CompletableFuture.supplyAsync(() -> {
                try {
                    manager.saveStateDelta(p.productId, currentRemaining, targetRemaining, p.player.getUUID(),
                            currentUsed, targetUsed, currentDemand, p.oldDemand,
                            currentHasRemaining, currentHasLimit, currentHasDemand,
                            p.hadRemaining, targetUsed > 0 || p.hadLimit, p.hadDemand);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("Failed to persist state compensation delta for tx={}", p.tx, e);
                    return false;
                }
            });
        }).thenCompose(value -> value);
    }

    private CompletableFuture<Result> rollbackPrepared(Prepared p, boolean persisted) {
        return restorePrepared(p, persisted).thenApply(ignored -> fail("§cA auditoria da transação está indisponível."));
    }

    private static int addItemsStrict(ServerPlayer player, ItemStack wanted) {
        ItemStack remaining = wanted.copy();
        int before = remaining.getCount();
        player.getInventory().add(remaining);
        return before - remaining.getCount();
    }

    private static <T> CompletableFuture<T> failed(Throwable error) { return CompletableFuture.failedFuture(error); }
    private static <T> CompletableFuture<T> onServer(ServerPlayer player, Supplier<T> action) {
        if (player.getServer() == null || player.getServer().isSameThread()) {
            try { return CompletableFuture.completedFuture(action.get()); } catch (Throwable error) { return CompletableFuture.failedFuture(error); }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        player.getServer().execute(() -> { try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result;
    }

    private static Map<String, String> metadata(String tx) { return Map.of("source", "adminshop", "reference", tx); }

    record Prepared(ServerPlayer player, String productId, AdminShopConfig.Product product, Operation operation, boolean buy,
                            String currency, BigDecimal price, ItemStack stack, String tx, String economicKey, long oldUsed,
                            long oldRemaining, long oldDemand, boolean hadLimit, boolean hadRemaining, boolean hadDemand,
                            String stockStage, Object lock, int quantity) {}
    private record Finance(EconomyOperationReceipt receipt, UUID reservation, boolean captured) {}
    private record ItemOutcome(boolean success, boolean applied, int amount, boolean command) {}
    private static final class FinanceFailure extends SagaFailure {
        private final Finance finance;
        private FinanceFailure(Finance finance, String message) { super(message); this.finance = finance; }
    }

    @Deprecated
    public Result execute(ServerPlayer player, String productId, Operation operation) {
        if (player == null || player.getServer() != null && player.getServer().isSameThread()) {
            return fail("§cUse o caminho assíncrono do AdminShop.");
        }
        return executeAsync(player, productId, operation).join();
    }

    @Deprecated
    @SuppressWarnings("unused")
    private Result executeLocked(ServerPlayer player, String productId, Operation operation) {
        AdminShopConfig.Product product = manager.config().product(productId);
        if (player == null || product == null) return fail("§cProduto indisponível.");
        boolean buy = operation == Operation.BUY;
        BigDecimal price = price(product, buy, productId);
        if ((buy && !product.buyEnabled) || (!buy && (!product.sellEnabled || product.isCommand())) || price == null) return fail("§cEsta operação não está disponível.");
        String currency = manager.config().currency(productId);
        if (currency == null) return fail("§cMoeda inválida.");
        if (!PermissionAPI.hasPermission(player.getUUID(), currencyPermission(currency))
            || product.permission != null && !product.permission.isBlank() && !PermissionAPI.hasPermission(player.getUUID(), product.permission)) {
            return fail("§cVocê não possui permissão.");
        }

        return executeAsync(player, productId, operation, product.quantity).join();
    }

    private boolean updateAudit(String tx, AdminShopAuditStatus status, EconomyOperationReceipt receipt, String item, String stock, String limit, String demand, String failure) {
        if (!manager.sql.updateAudit(tx, status, receipt, item, stock, limit, demand, failure)) throw new IllegalStateException("AdminShop audit update failed: " + tx);
        return true;
    }

    private boolean restoreState(String tx, UUID player, String playerProduct, String productId, long used, long oldDemand, long remaining,
                                 boolean hadLimit, boolean hadDemand, boolean hadRemaining, boolean persisted) {
        long currentRemaining = manager.state.remaining.getOrDefault(productId, remaining);
        long currentUsed = manager.state.limits.getOrDefault(playerProduct, used);
        long currentDemand = manager.state.demand.getOrDefault(productId, oldDemand);
        boolean currentHasRemaining = manager.state.remaining.containsKey(productId);
        boolean currentHasLimit = manager.state.limits.containsKey(playerProduct);
        boolean currentHasDemand = manager.state.demand.containsKey(productId);
        restore(manager.state.limits, playerProduct, used, hadLimit);
        restore(manager.state.demand, productId, oldDemand, hadDemand);
        restore(manager.state.remaining, productId, remaining, hadRemaining);
        manager.state.processed.remove(tx);
        if (!persisted) return true;
        try {
            manager.saveStateDelta(productId, currentRemaining, remaining, player, currentUsed, used,
                    currentDemand, oldDemand, currentHasRemaining, currentHasLimit, currentHasDemand,
                    hadRemaining, hadLimit, hadDemand);
            return true;
        } catch (Exception e) { LOGGER.error("AdminShop state compensation failed for {}", tx, e); return false; }
    }

    private boolean compensateItem(ServerPlayer player, ItemStack stack, int quantity, boolean buy, boolean itemApplied, boolean commandAttempted, int removed) {
        if (buy) {
            if (commandAttempted) return false;
            if (!itemApplied) return true;
            return remove(player, stack, quantity) == quantity;
        }
        if (!itemApplied && removed == 0) return true;
        ItemStack restored = stack.copyWithCount(removed);
        return addItems(player, restored) == removed;
    }

    private boolean compensateMoney(UUID player, BigDecimal price, String currency, boolean buy, String economicKey,
                                    EconomyOperationReceipt receipt, String tx) {
        if (receipt == null || receipt.status() != EconomyOperationStatus.COMPLETED) return true;
        if ("gems".equals(currency)) {
            GemOperationResult result = buy
                ? gems.credit(new GemCreditRequest(player, price.longValueExact(), "adminshop", "compensate", player, economicKey + ":compensate", tx, Map.of("source", "adminshop", "reference", tx)))
                : gems.debit(new GemDebitRequest(player, price.longValueExact(), "adminshop", "compensate", player, economicKey + ":compensate", tx, Map.of("source", "adminshop", "reference", tx)));
            return result.success();
        }
        EconomyOperationReceipt compensation = buy
            ? EconomyManager.getInstance().credit(player, price, economicKey + ":compensate", "AdminShop purchase compensation", Map.of("source", "adminshop", "reference", tx))
            : EconomyManager.getInstance().debit(player, price, economicKey + ":compensate", "AdminShop sale compensation", Map.of("source", "adminshop", "reference", tx));
        return compensation.status() == EconomyOperationStatus.COMPLETED;
    }

    private static EconomyOperationReceipt gemReceipt(GemOperationResult result, UUID player, BigDecimal amount, long before, String key) {
        BigDecimal after = result.balance() == null ? BigDecimal.valueOf(before) : BigDecimal.valueOf(result.balance().totalBalance());
        return new EconomyOperationReceipt(result.transactionId(), player, amount, result.success() ? EconomyOperationStatus.COMPLETED : EconomyOperationStatus.REJECTED,
            BigDecimal.valueOf(before), after, key);
    }

    private static Result fail(String message) { return new Result(false, message); }
    private static void restore(Map<String, Long> state, String key, long value, boolean wasPresent) { if (wasPresent) state.put(key, value); else state.remove(key); }

    static BigDecimal price(AdminShopConfig.Product product, boolean buy, String id) {
        BigDecimal base = buy ? product.buyPrice : product.sellPrice;
        if (base == null || product.dynamic == null || !product.dynamic.enabled) return base;
        long demand = AdminShopManager.getInstance().state.demand.getOrDefault(id, 0L);
        BigDecimal multiplier = BigDecimal.ONE.add(product.dynamic.step.multiply(BigDecimal.valueOf(demand)))
            .max(product.dynamic.minMultiplier).min(product.dynamic.maxMultiplier);
        return base.multiply(multiplier).setScale(ConfigManager.getEconomyCurrencyScale(), ConfigManager.getEconomyRoundingMode());
    }

    private static long count(ServerPlayer player, ItemStack wanted) {
        long count = 0; for (ItemStack stack : player.getInventory().items) if (ItemStack.isSameItemSameComponents(stack, wanted)) count += stack.getCount(); return count;
    }
    private static boolean hasRoom(ServerPlayer player, ItemStack wanted) {
        int free = 0; for (ItemStack stack : player.getInventory().items) free += stack.isEmpty() ? wanted.getMaxStackSize() : ItemStack.isSameItemSameComponents(stack, wanted) ? Math.max(0, stack.getMaxStackSize() - stack.getCount()) : 0; return free >= wanted.getCount();
    }
    private static int addItems(ServerPlayer player, ItemStack wanted) {
        ItemStack remaining = wanted.copy();
        int before = remaining.getCount();
        player.getInventory().add(remaining);
        int accepted = before - remaining.getCount();
        if (!remaining.isEmpty()) {
            boolean pending = com.pedrodalben.bigbangessentials.crates.service.CratePendingDeliveryService.getInstance()
                    .storePending(player.getUUID(), remaining.copy(), "adminshop");
            if (pending) return before;
            if (accepted > 0) remove(player, wanted, accepted);
        }
        return accepted;
    }
    private static int remove(ServerPlayer player, ItemStack wanted, int amount) {
        int removed = 0; for (ItemStack stack : player.getInventory().items) if (amount > 0 && ItemStack.isSameItemSameComponents(stack, wanted)) { int n = Math.min(amount, stack.getCount()); stack.shrink(n); amount -= n; removed += n; } return removed;
    }

    private static class SagaFailure extends RuntimeException {
        private final String message;
        private SagaFailure(String message) { super(message); this.message = message; }
    }
}

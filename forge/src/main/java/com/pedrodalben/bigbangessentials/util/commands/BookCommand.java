package com.pedrodalben.bigbangessentials.util.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.pedrodalben.bigbangessentials.config.ConfigManager;
import com.pedrodalben.bigbangessentials.util.CommandSourceHelper;
import com.pedrodalben.bigbangessentials.util.MessageUtil;
import com.pedrodalben.bigbangessentials.util.PermissionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;

/**
 * Implements the /book command - Gives players a writable book or converts books
 * Allows easy creation and editing of books
 */
public class BookCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookCommand.class);
    
    /**
     * Register the /book command
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!ConfigManager.getInstance().isCommandEnabled("book")) return;
        
        dispatcher.register(
            Commands.literal("book")
                .requires(source -> PermissionValidator.validateAnyPermission(
                    source,
                    "bigbangessentials.book",
                    "bigbangessentials.book.unlock",
                    "bigbangessentials.book.title",
                    "bigbangessentials.book.author"
                ).hasPermission())
                // /book - Give a writable book (requires player)
                .executes(ctx -> {
                    ServerPlayer player = CommandSourceHelper.requirePlayer(ctx.getSource(), "commands.bigbangessentials.book.player_only");
                    if (player == null) return 0;

                    PermissionValidator.PermissionResult permResult =
                        PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.book");
                    if (!permResult.hasPermission()) {
                        ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                        return 0;
                    }
                    
                    return giveWritableBook(player);
                })
                // /book unlock - Convert written book back to writable
                .then(Commands.literal("unlock")
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.book.unlock").hasPermission())
                    .executes(ctx -> {
                        PermissionValidator.PermissionResult permResult = 
                            PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.book.unlock");
                        if (!permResult.hasPermission()) {
                            ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                            return 0;
                        }
                        
                        ServerPlayer player = permResult.getPlayer();
                        return unlockBook(player);
                    })
                )
                // /book title <title> - Set title of book in hand
                .then(Commands.literal("title")
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.book.title").hasPermission())
                    .then(Commands.argument("title", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            PermissionValidator.PermissionResult permResult = 
                                PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.book.title");
                            if (!permResult.hasPermission()) {
                                ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                return 0;
                            }
                            
                            ServerPlayer player = permResult.getPlayer();
                            String title = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "title");
                            return setBookTitle(player, title);
                        })
                    )
                )
                // /book author <author> - Set author of book in hand
                .then(Commands.literal("author")
                    .requires(source -> PermissionValidator.validatePermission(
                        source, "bigbangessentials.book.author").hasPermission())
                    .then(Commands.argument("author", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> {
                            PermissionValidator.PermissionResult permResult = 
                                PermissionValidator.validatePermission(ctx.getSource(), "bigbangessentials.book.author");
                            if (!permResult.hasPermission()) {
                                ctx.getSource().sendFailure(MessageUtil.error(permResult.getErrorMessage()));
                                return 0;
                            }
                            
                            ServerPlayer player = permResult.getPlayer();
                            String author = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "author");
                            return setBookAuthor(player, author);
                        })
                    )
                )
        );
    }
    
    /**
     * Give a writable book to the player
     */
    private static int giveWritableBook(ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        
        // Add book to player's inventory
        if (!player.getInventory().add(book)) {
            // If inventory is full, drop it at their feet
            player.drop(book, false);
            player.sendSystemMessage(MessageUtil.warning("commands.bigbangessentials.book.inventory_full"));
        } else {
            player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.book.given"));
        }
        
        return 1;
    }
    
    /**
     * Convert a written book back to writable
     */
    private static int unlockBook(ServerPlayer player) {
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.getItem() != Items.WRITTEN_BOOK) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.book.not_written_book"));
            return 0;
        }

        ItemStack writableBook = new ItemStack(Items.WRITABLE_BOOK);
        if (heldItem.hasTag()) {
            CompoundTag oldTag = heldItem.getTag();
            if (oldTag != null && oldTag.contains("pages", net.minecraft.nbt.Tag.TAG_LIST)) {
                ListTag oldPages = oldTag.getList("pages", net.minecraft.nbt.Tag.TAG_STRING);
                ListTag newPages = new ListTag();
                for (int i = 0; i < oldPages.size(); i++) {
                    String pageJson = oldPages.getString(i);
                    try {
                        Component comp = Component.Serializer.fromJson(pageJson);
                        newPages.add(net.minecraft.nbt.StringTag.valueOf(comp != null ? comp.getString() : pageJson));
                    } catch (Exception e) {
                        newPages.add(net.minecraft.nbt.StringTag.valueOf(pageJson));
                    }
                }
                writableBook.getOrCreateTag().put("pages", newPages);
            }
        }

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, writableBook);
        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.book.unlocked"));
        return 1;
    }

    /**
     * Set the title of a writable book
     */
    private static int setBookTitle(ServerPlayer player, String title) {
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.book.not_writable_book"));
            return 0;
        }
        if (title.length() > 32) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.book.title_too_long"));
            return 0;
        }
        heldItem.getOrCreateTag().putString("PresetTitle", title);
        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.book.title_set", title));
        return 1;
    }

    /**
     * Set the author of a writable book
     */
    private static int setBookAuthor(ServerPlayer player, String author) {
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.book.not_writable_book"));
            return 0;
        }
        if (author.length() > 16) {
            player.sendSystemMessage(MessageUtil.error("commands.bigbangessentials.book.author_too_long"));
            return 0;
        }
        heldItem.getOrCreateTag().putString("PresetAuthor", author);
        player.sendSystemMessage(MessageUtil.success("commands.bigbangessentials.book.author_set", author));
        return 1;
    }
}

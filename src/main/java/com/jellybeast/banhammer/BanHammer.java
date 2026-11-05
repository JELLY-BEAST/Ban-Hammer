package com.jellybeast.banhammer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class BanHammer implements ModInitializer {
    public static final String MOD_ID = "banhammer";
    private static final String ITEM_MARKER = "jellybeast_ban_hammer";
    private static BanDataStore store;

    @Override
    public void onInitialize() {
        Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        BanHammerWhitelist.load(configDir.resolve("banhammer-whitelist.json"));
        store = new BanDataStore(configDir.resolve("banhammer-bans.json"));

        registerCommands();
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            if (!isHammer(stack) || !player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer admin) || !BanHammerWhitelist.isAllowed(admin.getUUID())) {
                player.displayClientMessage(Component.literal("§cYou are not authorized to use the Ban Hammer."), false);
                return InteractionResult.FAIL;
            }
            admin.sendSystemMessage(Component.literal("§6§lBAN HAMMER §7— §fPending ban settings"));
            admin.sendSystemMessage(Component.literal("§e/banhammer reason <reason>"));
            admin.sendSystemMessage(Component.literal("§e/banhammer duration <seconds|minutes|hours|days|weeks|years|permanent> [amount]"));
            admin.sendSystemMessage(Component.literal("§7Set the reason and duration, then hit the target player."));
            return InteractionResult.SUCCESS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer admin) || !(target instanceof ServerPlayer victim)) return InteractionResult.PASS;
            if (!isHammer(admin.getMainHandItem())) return InteractionResult.PASS;
            if (!BanHammerWhitelist.isAllowed(admin.getUUID())) {
                admin.sendSystemMessage(Component.literal("§cYou are not authorized to use the Ban Hammer."));
                return InteractionResult.FAIL;
            }
            PendingBan pending = PendingBan.get(admin.getUUID());
            if (pending == null) {
                admin.sendSystemMessage(Component.literal("§cNo ban configured. Sneak-right-click the hammer first, then set reason and duration."));
                return InteractionResult.FAIL;
            }
            store.ban(victim.getUUID(), victim.getGameProfile().getName(), pending.reason(), pending.expiresAt());
            String durationText = pending.permanent() ? "Permanent" : pending.displayDuration();
            victim.connection.disconnect(Component.literal("You have been banned.\nReason: " + pending.reason() + "\nDuration: " + durationText));
            Component announcement = Component.literal("§c§l" + victim.getGameProfile().getName()
                    + " §chas been banned by §6" + admin.getGameProfile().getName()
                    + "§c for §f" + pending.reason() + "§c. Time: §f" + durationText);
            world.getServer().getPlayerList().broadcastSystemMessage(announcement, false);
            PendingBan.clear(admin.getUUID());
            return InteractionResult.SUCCESS;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            BanDataStore.BanRecord record = store.get(player.getUUID());
            if (record != null) {
                if (record.expiresAt() > 0 && System.currentTimeMillis() >= record.expiresAt()) {
                    store.unban(player.getUUID());
                } else {
                    String remaining = record.expiresAt() == 0 ? "Permanent" : "Temporary";
                    handler.disconnect(Component.literal("You are banned.\nReason: " + record.reason() + "\nDuration: " + remaining));
                }
            }
        });
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("BanHammer")
                .requires(source -> source.getEntity() instanceof ServerPlayer p && BanHammerWhitelist.isAllowed(p.getUUID()))
                .executes(ctx -> give(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("Discard").executes(ctx -> discard(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("reason")
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PendingBan.setReason(p.getUUID(), StringArgumentType.getString(ctx, "reason"));
                            p.sendSystemMessage(Component.literal("§aBan reason saved."));
                            return 1;
                        })))
                .then(Commands.literal("duration")
                    .then(Commands.argument("unit", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                ServerPlayer p = ctx.getSource().getPlayerOrException();
                                String unit = StringArgumentType.getString(ctx, "unit").toLowerCase(Locale.ROOT);
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                PendingBan.setDuration(p.getUUID(), unit, amount);
                                p.sendSystemMessage(Component.literal("§aBan duration saved."));
                                return 1;
                            })))
                        .then(Commands.literal("permanent").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PendingBan.setPermanent(p.getUUID());
                            p.sendSystemMessage(Component.literal("§aPermanent ban selected."));
                            return 1;
                        })))
            )
        );
    }

    private static int give(ServerPlayer player) {
        ItemStack hammer = new ItemStack(Items.MACE);
        hammer.set(DataComponents.CUSTOM_NAME, Component.literal("𝐵̥̊⃝𝑎̥̊⃝𝑛̥̊⃝ 𝐻̥̊⃝𝑎̥̊⃝𝑚̥̊⃝𝑚̥̊⃝𝑒̥̊⃝𝑟̥̊⃝"));
        hammer.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ITEM_MARKER, true);
        hammer.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().add(hammer);
        player.sendSystemMessage(Component.literal("§6Ban Hammer issued."));
        return 1;
    }

    private static int discard(ServerPlayer player) {
        int removed = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isHammer(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }
        player.sendSystemMessage(Component.literal("§aRemoved " + removed + " Ban Hammer(s)."));
        return 1;
    }

    public static boolean isHammer(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.MACE)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(ITEM_MARKER);
    }
}

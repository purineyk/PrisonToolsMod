package jp.purineyk.prisontools;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

public class PrisonToolsMod implements ModInitializer {
    public static final String MOD_ID = "prisontools";
    private static final Map<String, String> DOOR_KEYS = new ConcurrentHashMap<>();

    public static final KeyItem RED_KEY = key("red_key", "red");
    public static final KeyItem BLUE_KEY = key("blue_key", "blue");
    public static final KeyItem GREEN_KEY = key("green_key", "green");
    public static final KeyItem PURPLE_KEY = key("purple_key", "purple");
    public static final KeyItem YELLOW_KEY = key("yellow_key", "yellow");
    public static final KeyItem BLACK_KEY = key("black_key", "black");
    public static final KeyItem MASTER_KEY = key("master_key", "master");
    public static final Item BATON = register("baton", Item::new, new Item.Properties().stacksTo(1));

    private static KeyItem key(String name, String colour) {
        return register(name, properties -> new KeyItem(colour, properties), new Item.Properties().stacksTo(1));
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name));
        T item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static String doorKey(BlockPos pos, Level level) {
        return level.dimension().identifier() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Override
    public void onInitialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(tab -> {
            tab.accept(RED_KEY); tab.accept(BLUE_KEY); tab.accept(GREEN_KEY);
            tab.accept(PURPLE_KEY); tab.accept(YELLOW_KEY); tab.accept(BLACK_KEY);
            tab.accept(MASTER_KEY); tab.accept(BATON);
        });

        registerDispenserKey(RED_KEY);
        registerDispenserKey(BLUE_KEY);
        registerDispenserKey(GREEN_KEY);
        registerDispenserKey(PURPLE_KEY);
        registerDispenserKey(YELLOW_KEY);
        registerDispenserKey(BLACK_KEY);
        registerDispenserKey(MASTER_KEY);

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!level.isClientSide() && player.getItemInHand(hand).is(BATON) && entity instanceof Player target
                    && target != player && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 6, false, true, true));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.JUMP_BOOST, 60, 128, false, false, true));
            }
            return InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BlockPos feet = player.blockPosition();
                if (!player.getTags().contains("prison")) continue;
                if (!player.level().getBlockState(feet.below()).is(Blocks.GOLD_BLOCK)) continue;
                if (!hasPoweredLever(player.level(), feet, 6) || player.getInventory().isEmpty()) continue;
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c警報！持ち込み禁止品を検知！"));
                toggleNearbyLamps(player.level(), feet, 10);
            }
        });
    }

    private static void registerDispenserKey(KeyItem item) {
        DispenserBlock.registerBehavior(item, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Direction direction = source.state().getValue(DispenserBlock.FACING);
                BlockPos target = source.pos().relative(direction);
                BlockState state = source.level().getBlockState(target);
                if (state.is(Blocks.IRON_DOOR)) {
                    String registered = DOOR_KEYS.get(doorKey(target, source.level()));
                    if (item.colour().equals("master") || item.colour().equals(registered)) {
                        source.level().setBlock(target, state.setValue(DoorBlock.OPEN, true), 3);
                        return stack;
                    }
                }
                return super.execute(source, stack);
            }
        });
    }

    private static boolean hasPoweredLever(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LEVER) && state.getValue(LeverBlock.POWERED)) return true;
        }
        return false;
    }

    private static void toggleNearbyLamps(Level level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.REDSTONE_LAMP)) {
                level.setBlock(pos, state.setValue(RedstoneLampBlock.LIT, !state.getValue(RedstoneLampBlock.LIT)), 3);
            }
        }
    }

    public static void bindDoor(BlockPos pos, Level level, String colour, Player player) {
        DOOR_KEYS.put(doorKey(pos, level), colour);
        if (!level.isClientSide()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aこの鉄のドアの鍵を「" + colour + "」に設定しました。"));
        }
    }
}

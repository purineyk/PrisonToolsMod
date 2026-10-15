package jp.purineyk.prisontools;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

public class KeyItem extends Item {
    private final String colour;

    public KeyItem(String colour, Properties properties) {
        super(properties);
        this.colour = colour;
    }

    public String colour() {
        return colour;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().getBlockState(context.getClickedPos()).is(Blocks.IRON_DOOR)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            PrisonToolsMod.bindDoor(context.getClickedPos(), context.getLevel(), colour, player);
        }
        return InteractionResult.SUCCESS;
    }
}

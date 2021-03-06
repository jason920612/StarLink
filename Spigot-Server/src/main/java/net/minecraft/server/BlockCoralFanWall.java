package net.minecraft.server;

import java.util.Random;

public class BlockCoralFanWall extends BlockCoralFanWallAbstract {

    private final Block c;

    protected BlockCoralFanWall(Block block, Block.Info block_info) {
        super(block_info);
        this.c = block;
    }

    @Override
    public void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag) {
        this.a(iblockdata, (GeneratorAccess) world, blockposition);
    }

    @Override
    public void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, Random random) {
        if (!b_(iblockdata, worldserver, blockposition)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, this.c.getBlockData().set(BlockCoralFanWall.b, false).set(BlockCoralFanWall.a, iblockdata.get(BlockCoralFanWall.a))).isCancelled()) {
                return;
            }
            // CraftBukkit end
            worldserver.setTypeAndData(blockposition, (IBlockData) ((IBlockData) this.c.getBlockData().set(BlockCoralFanWall.b, false)).set(BlockCoralFanWall.a, iblockdata.get(BlockCoralFanWall.a)), 2);
        }

    }

    @Override
    public IBlockData updateState(IBlockData iblockdata, EnumDirection enumdirection, IBlockData iblockdata1, GeneratorAccess generatoraccess, BlockPosition blockposition, BlockPosition blockposition1) {
        if (enumdirection.opposite() == iblockdata.get(BlockCoralFanWall.a) && !iblockdata.canPlace(generatoraccess, blockposition)) {
            return Blocks.AIR.getBlockData();
        } else {
            if ((Boolean) iblockdata.get(BlockCoralFanWall.b)) {
                generatoraccess.getFluidTickList().a(blockposition, FluidTypes.WATER, FluidTypes.WATER.a((IWorldReader) generatoraccess));
            }

            this.a(iblockdata, generatoraccess, blockposition);
            return super.updateState(iblockdata, enumdirection, iblockdata1, generatoraccess, blockposition, blockposition1);
        }
    }
}

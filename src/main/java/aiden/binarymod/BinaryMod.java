package aiden.binarymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedList;
import java.util.Queue;

public class BinaryMod implements ModInitializer {
    private static final Queue<BlockPlacement> placementQueue = new LinkedList<>();

    @Override
    public void onInitialize() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        // Tick loop to place blocks
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int blocksPerTick = 8;
            for (int i = 0; i < blocksPerTick && !placementQueue.isEmpty(); i++) {
                BlockPlacement bp = placementQueue.poll();
                if (bp != null) {
                    bp.place();
                }
            }
        });
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("binary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .then(CommandManager.argument("data", StringArgumentType.string())
                                        .executes(ctx -> {
                                            int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                            int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
                                            String input = StringArgumentType.getString(ctx, "data");

                                            ServerWorld world = ctx.getSource().getWorld();
                                            int baseX = chunkX << 4;
                                            int baseZ = chunkZ << 4;
                                            int startY = -60;

                                            // Clear existing data
                                            for (int y = startY; y <= 319; y++) {
                                                for (int x = 0; x < 16; x++) {
                                                    for (int z = 0; z < 16; z++) {
                                                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                                                    }
                                                }
                                            }

                                            // Convert to binary string
                                            StringBuilder binary = new StringBuilder();
                                            for (char c : input.toCharArray()) {
                                                binary.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
                                            }

                                            int bitIndex = 0;
                                            int y = startY;

                                            while (bitIndex < binary.length()) {
                                                for (int z = 0; z < 16 && bitIndex < binary.length(); z++) {
                                                    for (int x = 0; x < 16 && bitIndex < binary.length(); x++) {
                                                        char bit = binary.charAt(bitIndex++);
                                                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                        placementQueue.add(new BlockPlacement(world, pos, bit));
                                                    }
                                                }
                                                y++;
                                            }

                                            ctx.getSource().sendMessage(Text.literal("Binary data is being placed..."));
                                            return 1;
                                        })))));

        dispatcher.register(CommandManager.literal("clearbinary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                    int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");

                                    ServerWorld world = ctx.getSource().getWorld();
                                    int baseX = chunkX << 4;
                                    int baseZ = chunkZ << 4;
                                    int startY = -60;

                                    for (int y = startY; y <= 319; y++) {
                                        for (int x = 0; x < 16; x++) {
                                            for (int z = 0; z < 16; z++) {
                                                BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                world.setBlockState(pos, Blocks.AIR.getDefaultState());
                                            }
                                        }
                                    }

                                    ctx.getSource().sendMessage(Text.literal("Binary data cleared successfully."));
                                    return 1;
                                }))));

        dispatcher.register(CommandManager.literal("readbinary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                    int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");

                                    ServerWorld world = ctx.getSource().getWorld();
                                    int baseX = chunkX << 4;
                                    int baseZ = chunkZ << 4;
                                    int startY = -60;

                                    StringBuilder binary = new StringBuilder();

                                    for (int y = startY; y <= 319; y++) {
                                        for (int z = 0; z < 16; z++) {
                                            for (int x = 0; x < 16; x++) {
                                                BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                if (world.getBlockState(pos).isOf(Blocks.BLACK_WOOL)) {
                                                    binary.append("0");
                                                } else if (world.getBlockState(pos).isOf(Blocks.WHITE_WOOL)) {
                                                    binary.append("1");
                                                }
                                            }
                                        }
                                    }

                                    // Convert binary to text
                                    StringBuilder output = new StringBuilder();
                                    for (int i = 0; i + 7 < binary.length(); i += 8) {
                                        String byteString = binary.substring(i, i + 8);
                                        char c = (char) Integer.parseInt(byteString, 2);
                                        output.append(c);
                                    }

                                    ctx.getSource().sendMessage(Text.literal("Read: " + output.toString()));
                                    return 1;
                                }))));
    }

    // Helper class for scheduled placement
    private static class BlockPlacement {
        private final ServerWorld world;
        private final BlockPos pos;
        private final char bit;

        public BlockPlacement(ServerWorld world, BlockPos pos, char bit) {
            this.world = world;
            this.pos = pos;
            this.bit = bit;
        }

        public void place() {
            world.setBlockState(pos, bit == '0' ? Blocks.BLACK_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState());
            world.spawnParticles(
                    ParticleTypes.DRAGON_BREATH,
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    10,
                    0.2, 0.2, 0.2,
                    0.01
            );
        }
    }
}

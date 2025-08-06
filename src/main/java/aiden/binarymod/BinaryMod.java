package aiden.binarymod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class BinaryMod implements ModInitializer {

    public static final Queue<BlockPlacement> placementQueue = new LinkedList<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /binary command
            dispatcher.register(CommandManager.literal("binary")
                    .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                            .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("data", StringArgumentType.string())
                                            .executes(ctx -> {
                                                int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                                int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
                                                String data = StringArgumentType.getString(ctx, "data");

                                                StringBuilder binary = new StringBuilder();
                                                for (char c : data.toCharArray()) {
                                                    binary.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
                                                }

                                                ServerWorld world = ctx.getSource().getWorld();
                                                int baseX = chunkX << 4;
                                                int baseZ = chunkZ << 4;

                                                // Clear previous blocks
                                                for (int y = -60; y < -60 + 16; y++) {
                                                    for (int z = 0; z < 16; z++) {
                                                        for (int x = 0; x < 16; x++) {
                                                            BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                            placementQueue.add(new BlockPlacement(world, pos, null));
                                                        }
                                                    }
                                                }

                                                // Place new blocks
                                                int bitIndex = 0;
                                                int y = -60;
                                                while (bitIndex < binary.length()) {
                                                    for (int z = 0; z < 16 && bitIndex < binary.length(); z++) {
                                                        for (int x = 0; x < 16 && bitIndex < binary.length(); x++) {
                                                            char bit = binary.charAt(bitIndex++);
                                                            BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                            placementQueue.add(new BlockPlacement(world, pos,
                                                                    bit == '0' ? Blocks.BLACK_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState()));
                                                        }
                                                    }
                                                    y++;
                                                }

                                                ctx.getSource().sendMessage(Text.literal("Binary data placed successfully."));
                                                return 1;
                                            })))));

            // /clearbinary command
            dispatcher.register(CommandManager.literal("clearbinary")
                    .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                            .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
                                        ServerWorld world = ctx.getSource().getWorld();
                                        int baseX = chunkX << 4;
                                        int baseZ = chunkZ << 4;

                                        // From Y = -60 to 319 (entire vertical buildable height)
                                        for (int y = -60; y <= 319; y++) {
                                            for (int z = 0; z < 16; z++) {
                                                for (int x = 0; x < 16; x++) {
                                                    BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                    placementQueue.add(new BlockPlacement(world, pos, null));
                                                }
                                            }
                                        }

                                        ctx.getSource().sendMessage(Text.literal("§cCleared all blocks in chunk (" + chunkX + ", " + chunkZ + ")"));
                                        return 1;
                                    }))));


            // /readbinary command
            dispatcher.register(CommandManager.literal("readbinary")
                    .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                            .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                        int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");

                                        ServerWorld world = ctx.getSource().getWorld();
                                        int baseX = chunkX << 4;
                                        int baseZ = chunkZ << 4;

                                        StringBuilder binary = new StringBuilder();
                                        for (int y = -60; y < -60 + 16; y++) {
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

                                        StringBuilder textOutput = new StringBuilder();
                                        for (int i = 0; i + 8 <= binary.length(); i += 8) {
                                            String byteStr = binary.substring(i, i + 8);
                                            int charCode = Integer.parseInt(byteStr, 2);
                                            textOutput.append((char) charCode);
                                        }

                                        ctx.getSource().sendMessage(Text.literal("§aBinary reads as: " + textOutput));
                                        return 1;
                                    }))));

            // /filebinary command
            dispatcher.register(CommandManager.literal("filebinary")
                    .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                            .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("filename", StringArgumentType.string())
                                            .executes(ctx -> {
                                                int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                                int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");
                                                String filename = StringArgumentType.getString(ctx, "filename");

                                                File targetFile = new File(System.getProperty("user.dir"), filename);
                                                if (!targetFile.exists()) {
                                                    ctx.getSource().sendMessage(Text.literal("§cFile not found: " + filename));
                                                    return 0;
                                                }

                                                byte[] fileBytes;
                                                try {
                                                    fileBytes = Files.readAllBytes(targetFile.toPath());
                                                } catch (IOException e) {
                                                    ctx.getSource().sendMessage(Text.literal("§cFailed to read file: " + e.getMessage()));
                                                    return 0;
                                                }

                                                StringBuilder binary = new StringBuilder();
                                                for (byte b : fileBytes) {
                                                    binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
                                                }

                                                ServerWorld world = ctx.getSource().getWorld();
                                                int baseX = chunkX << 4;
                                                int baseZ = chunkZ << 4;

                                                // Clear previous
                                                for (int y = -60; y < -60 + 16; y++) {
                                                    for (int z = 0; z < 16; z++) {
                                                        for (int x = 0; x < 16; x++) {
                                                            BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                            placementQueue.add(new BlockPlacement(world, pos, null));
                                                        }
                                                    }
                                                }

                                                int bitIndex = 0;
                                                int y = -60;
                                                while (bitIndex < binary.length()) {
                                                    for (int z = 0; z < 16 && bitIndex < binary.length(); z++) {
                                                        for (int x = 0; x < 16 && bitIndex < binary.length(); x++) {
                                                            char bit = binary.charAt(bitIndex++);
                                                            BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                            placementQueue.add(new BlockPlacement(world, pos,
                                                                    bit == '0' ? Blocks.BLACK_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState()));
                                                        }
                                                    }
                                                    y++;
                                                }

                                                ctx.getSource().sendMessage(Text.literal("§aBinary data from file placed successfully."));
                                                return 1;
                                            })))));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int blocksPerTick = 8;
            for (int i = 0; i < blocksPerTick && !placementQueue.isEmpty(); i++) {
                placementQueue.poll().run();
            }
        });
    }

    public static class BlockPlacement implements Runnable {
        private final ServerWorld world;
        private final BlockPos pos;
        private final net.minecraft.block.BlockState state;

        public BlockPlacement(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
            this.world = world;
            this.pos = pos;
            this.state = state;
        }

        @Override
        public void run() {
            if (state == null) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            } else {
                world.setBlockState(pos, state);
            }
        }
    }
}

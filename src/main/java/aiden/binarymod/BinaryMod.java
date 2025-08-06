package aiden.binarymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class BinaryMod implements ModInitializer {

    private static final Queue<Runnable> tickTaskQueue = new LinkedList<>();
    private static final Map<Character, Block> HEX_BLOCK_MAP = new HashMap<>();
    private static final Map<Block, Character> BLOCK_HEX_MAP = new HashMap<>();

    static {
        HEX_BLOCK_MAP.put('0', Blocks.WHITE_CONCRETE);
        HEX_BLOCK_MAP.put('1', Blocks.ORANGE_CONCRETE);
        HEX_BLOCK_MAP.put('2', Blocks.MAGENTA_CONCRETE);
        HEX_BLOCK_MAP.put('3', Blocks.LIGHT_BLUE_CONCRETE);
        HEX_BLOCK_MAP.put('4', Blocks.YELLOW_CONCRETE);
        HEX_BLOCK_MAP.put('5', Blocks.LIME_CONCRETE);
        HEX_BLOCK_MAP.put('6', Blocks.PINK_CONCRETE);
        HEX_BLOCK_MAP.put('7', Blocks.GRAY_CONCRETE);
        HEX_BLOCK_MAP.put('8', Blocks.LIGHT_GRAY_CONCRETE);
        HEX_BLOCK_MAP.put('9', Blocks.CYAN_CONCRETE);
        HEX_BLOCK_MAP.put('A', Blocks.PURPLE_CONCRETE);
        HEX_BLOCK_MAP.put('B', Blocks.BLUE_CONCRETE);
        HEX_BLOCK_MAP.put('C', Blocks.BROWN_CONCRETE);
        HEX_BLOCK_MAP.put('D', Blocks.GREEN_CONCRETE);
        HEX_BLOCK_MAP.put('E', Blocks.RED_CONCRETE);
        HEX_BLOCK_MAP.put('F', Blocks.BLACK_CONCRETE);

        for (Map.Entry<Character, Block> entry : HEX_BLOCK_MAP.entrySet()) {
            BLOCK_HEX_MAP.put(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (int i = 0; i < 8 && !tickTaskQueue.isEmpty(); i++) {
                Runnable task = tickTaskQueue.poll();
                if (task != null) task.run();
            }
        });
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("binary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .then(CommandManager.argument("data", StringArgumentType.string())
                                        .executes(ctx -> {
                                            return placeHexData(ctx.getSource().getWorld(),
                                                    IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                    IntegerArgumentType.getInteger(ctx, "chunkZ"),
                                                    StringArgumentType.getString(ctx, "data"));
                                        })))));

        dispatcher.register(CommandManager.literal("filebinary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .then(CommandManager.argument("filename", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String filename = StringArgumentType.getString(ctx, "filename");
                                            Path path = Path.of(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft", "BinaryFiles", filename);
                                            if (!Files.exists(path)) {
                                                ctx.getSource().sendMessage(Text.literal("File not found: " + filename));
                                                return 0;
                                            }
                                            try {
                                                String content = Files.readString(path);
                                                return placeHexData(ctx.getSource().getWorld(),
                                                        IntegerArgumentType.getInteger(ctx, "chunkX"),
                                                        IntegerArgumentType.getInteger(ctx, "chunkZ"),
                                                        content);
                                            } catch (IOException e) {
                                                ctx.getSource().sendMessage(Text.literal("Failed to read file: " + e.getMessage()));
                                                return 0;
                                            }
                                        })))));

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

                                    StringBuilder hexData = new StringBuilder();
                                    for (int y = startY; y <= 319; y++) {
                                        for (int z = 0; z < 16; z++) {
                                            for (int x = 0; x < 16; x++) {
                                                BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                                                Block block = world.getBlockState(pos).getBlock();
                                                if (BLOCK_HEX_MAP.containsKey(block)) {
                                                    hexData.append(BLOCK_HEX_MAP.get(block));
                                                }
                                            }
                                        }
                                    }

                                    StringBuilder output = new StringBuilder();
                                    for (int i = 0; i + 1 < hexData.length(); i += 2) {
                                        String byteStr = hexData.substring(i, i + 2);
                                        char c = (char) Integer.parseInt(byteStr, 16);
                                        output.append(c);
                                    }

                                    ctx.getSource().sendMessage(Text.literal("Read: " + output.toString()));
                                    return 1;
                                }))));

        dispatcher.register(CommandManager.literal("clearbinary")
                .then(CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerWorld world = ctx.getSource().getWorld();
                                    int chunkX = IntegerArgumentType.getInteger(ctx, "chunkX");
                                    int chunkZ = IntegerArgumentType.getInteger(ctx, "chunkZ");

                                    int startX = chunkX << 4;
                                    int startZ = chunkZ << 4;

                                    // Loop through the Y-axis range from -60 to 319
                                    for (int y = -60; y <= 319; y++) {
                                        for (int dx = 0; dx < 16; dx++) { // X from 0 to 15 (16 blocks)
                                            for (int dz = 0; dz < 16; dz++) { // Z from 0 to 15 (16 blocks)
                                                BlockPos pos = new BlockPos(startX + dx, y, startZ + dz);
                                                // Set every block in the chunk to AIR
                                                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                                            }
                                        }
                                    }

                                    ctx.getSource().sendMessage(Text.literal("Cleared chunk " + chunkX + ", " + chunkZ));
                                    return 1;
                                }))));
    }

    private static int placeHexData(ServerWorld world, int chunkX, int chunkZ, String input) {
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

        StringBuilder hex = new StringBuilder();
        for (char c : input.toCharArray()) {
            hex.append(String.format("%02X", (int) c));
        }

        int y = startY;
        int[] index = {0};

        while (index[0] < hex.length()) {
            for (int z = 0; z < 16 && index[0] < hex.length(); z++) {
                for (int x = 0; x < 16 && index[0] < hex.length(); x++) {
                    int currentY = y;
                    int currentX = x;
                    int currentZ = z;
                    char hexChar = hex.charAt(index[0]++);
                    Block block = HEX_BLOCK_MAP.get(hexChar);
                    if (block != null) {
                        tickTaskQueue.add(() -> {
                            BlockPos pos = new BlockPos(baseX + currentX, currentY, baseZ + currentZ);
                            world.setBlockState(pos, block.getDefaultState());
                        });
                    }
                }
            }
            y++;
        }

        return 1;
    }
}

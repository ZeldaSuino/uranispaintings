package io.github.zeldasuino.uranispaintings;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import static io.github.zeldasuino.uranispaintings.Uranispaintings.LOGGER;
import static io.github.zeldasuino.uranispaintings.Uranispaintings.PAINTING_TAG;

@Mod(Uranispaintings.MODID)
public class Uranispaintings {

    public static final String MODID = "uranispaintings";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> PAINTING_TAG = ITEMS.register("painting_tag", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> PAINTING_TAG.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(PAINTING_TAG.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    public Uranispaintings() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ModEvents.class);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }


}

class ModEvents {
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(WarpingCapability.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPainting(AttachCapabilitiesEvent<Entity> event) {

        Class<? extends Entity> x;
        try {
            x = (Class<? extends Entity>) Class.forName("immersive_paintings.entity.AbstractImmersiveDecorationEntity");
        }
        // pig class only for testing purposes
        // to be removed
        catch (ClassNotFoundException e){
            x = Pig.class;
        }
        if (event.getObject().getClass().getSuperclass() == x || event.getObject().getClass() == x) {
            Entity e = event.getObject();

            if (!e.getCapability(WarpingProvider.WARP).isPresent()) {
                event.addCapability(new ResourceLocation(Uranispaintings.MODID, "warp"), new WarpingProvider());
            }
        }
    }

    @SubscribeEvent
    public static void testWarp(TickEvent.ServerTickEvent e){
        Player p = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;
        if (p != null && p.isCrouching()) {
            // client only

            for (Entity entidade : level.getEntities(p, p.getBoundingBox().inflate(1))) {
                entidade.getCapability(WarpingProvider.WARP).ifPresent((warp) -> {
                    LOGGER.info("!!!");
                    if (warp.isLinked) {
                        p.sendSystemMessage(Component.literal("linked"));
                        p.setPos(warp.linkedPainting.getCenter().subtract(0, -0.5, 0));
                    } else {

                        if (warp.containsTag) {
                            p.addItem(PAINTING_TAG.get().getDefaultInstance());
                            warp.containsTag = false;
                        }
                        p.sendSystemMessage(Component.literal("unlinked"));
                    }
                });
            }
        }
    }
}

class WarpingCapability{
    public boolean  isLinked;
    public boolean  containsTag;
    public BlockPos linkedPainting = new BlockPos(0, 0, 0);

    WarpingCapability(){
        isLinked = false;
        containsTag = true;
    }

    void loadData(CompoundTag nbt){
        isLinked = nbt.getBoolean("linked");
        containsTag = nbt.getBoolean("containstag");
        linkedPainting = Utils.intToBlockPos(nbt.getIntArray("linkedpainting"));
    }

    void saveData(CompoundTag nbt){
        nbt.putBoolean("linked", isLinked);
        nbt.putBoolean("containstag", containsTag);
        nbt.putIntArray("linkedpainting", Utils.blockPosToInt(linkedPainting));
    }
}

class WarpingProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static Capability<WarpingCapability> WARP = CapabilityManager.get(new CapabilityToken<WarpingCapability>(){});

    private WarpingCapability warp;
    private final LazyOptional<WarpingCapability> optional = LazyOptional.of(this::createObj);

    private WarpingCapability createObj(){
        if (warp == null){
            warp = new WarpingCapability();
        }
        return warp;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        return (capability.equals(WARP)) ? optional.cast(): LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createObj().saveData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createObj().loadData(nbt);
    }
}

class Utils {
    public static Vec3 intToVec3(int[] array) {
        return new Vec3(array[0], array[1], array[2]);
    }

    public static int[] vec3ToInt(Vec3 vector) {
        return new int[]{(int) vector.x, (int) vector.y, (int) vector.z};
    }

    public static int[] blockPosToInt(BlockPos bp) {
        return new int[]{bp.getX(), bp.getY(), bp.getZ()};
    }

    public static BlockPos intToBlockPos(int[] xyz) {
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }
}
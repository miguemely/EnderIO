package crazypants.enderio.machine.capbank;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.enderio.core.api.client.gui.IAdvancedTooltipProvider;
import com.enderio.core.client.handlers.SpecialTooltipHandler;
import com.enderio.core.common.util.Util;
import com.enderio.core.common.vecmath.Vector3d;

import crazypants.enderio.BlockEio;
import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.ModObject;
import crazypants.enderio.api.redstone.IRedstoneConnectable;
import crazypants.enderio.machine.IoMode;
import crazypants.enderio.machine.capbank.network.CapBankClientNetwork;
import crazypants.enderio.machine.capbank.network.ICapBankNetwork;
import crazypants.enderio.machine.capbank.network.NetworkUtil;
import crazypants.enderio.machine.capbank.packet.PacketGuiChange;
import crazypants.enderio.machine.capbank.packet.PacketNetworkEnergyRequest;
import crazypants.enderio.machine.capbank.packet.PacketNetworkEnergyResponse;
import crazypants.enderio.machine.capbank.packet.PacketNetworkIdRequest;
import crazypants.enderio.machine.capbank.packet.PacketNetworkIdResponse;
import crazypants.enderio.machine.capbank.packet.PacketNetworkStateRequest;
import crazypants.enderio.machine.capbank.packet.PacketNetworkStateResponse;
import crazypants.enderio.machine.capbank.render.CapBankRenderMapper;
import crazypants.enderio.machine.power.PowerDisplayUtil;
import crazypants.enderio.network.PacketHandler;
import crazypants.enderio.power.PowerHandlerUtil;
import crazypants.enderio.render.EnumMergingBlockRenderMode;
import crazypants.enderio.render.IOMode;
import crazypants.enderio.render.IRenderMapper;
import crazypants.enderio.render.ISmartRenderAwareBlock;
import crazypants.enderio.render.SmartModelAttacher;
import crazypants.enderio.render.TextureRegistry;
import crazypants.enderio.render.TextureRegistry.TextureSupplier;
import crazypants.enderio.tool.ToolUtil;
import crazypants.enderio.waila.IWailaInfoProvider;

public class BlockCapBank extends BlockEio<TileCapBank> implements IGuiHandler, IAdvancedTooltipProvider, IWailaInfoProvider, IRedstoneConnectable,
    ISmartRenderAwareBlock {

  @SideOnly(Side.CLIENT)
  private static CapBankRenderMapper CAPBANK_RENDER_MAPPER;

  public static BlockCapBank create() {

    PacketHandler.INSTANCE.registerMessage(PacketNetworkStateResponse.class, PacketNetworkStateResponse.class, PacketHandler.nextID(), Side.CLIENT);
    PacketHandler.INSTANCE.registerMessage(PacketNetworkStateRequest.class, PacketNetworkStateRequest.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketNetworkIdRequest.class, PacketNetworkIdRequest.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketNetworkIdResponse.class, PacketNetworkIdResponse.class, PacketHandler.nextID(), Side.CLIENT);
    PacketHandler.INSTANCE.registerMessage(PacketNetworkEnergyRequest.class, PacketNetworkEnergyRequest.class, PacketHandler.nextID(), Side.SERVER);
    PacketHandler.INSTANCE.registerMessage(PacketNetworkEnergyResponse.class, PacketNetworkEnergyResponse.class, PacketHandler.nextID(), Side.CLIENT);
    PacketHandler.INSTANCE.registerMessage(PacketGuiChange.class, PacketGuiChange.class, PacketHandler.nextID(), Side.SERVER);

    BlockCapBank res = new BlockCapBank();
    res.init();
    return res;
  }

  public static final TextureSupplier gaugeIcon = TextureRegistry.registerTexture("blocks/capacitorBankOverlays");
  public static final TextureSupplier fillBarIcon = TextureRegistry.registerTexture("blocks/capacitorBankFillBar");
  public static final TextureSupplier infoPanelIcon = TextureRegistry.registerTexture("blocks/capBankInfoPanel");

  protected BlockCapBank() {
    super(ModObject.blockCapBank.unlocalisedName, TileCapBank.class, BlockItemCapBank.class);
    setHardness(2.0F);
    setDefaultState(this.blockState.getBaseState().withProperty(EnumMergingBlockRenderMode.RENDER, EnumMergingBlockRenderMode.AUTO)
        .withProperty(CapBankType.KIND, CapBankType.NONE));
  }

  @Override
  protected void init() {
    super.init();
    EnderIO.guiHandler.registerGuiHandler(GuiHandler.GUI_ID_CAP_BANK, this);
    setLightOpacity(255);
    SmartModelAttacher.register(this, EnumMergingBlockRenderMode.RENDER, EnumMergingBlockRenderMode.DEFAULTS, EnumMergingBlockRenderMode.AUTO);
  }

  @Override
  protected BlockState createBlockState() {
    return new BlockState(this, new IProperty[] { EnumMergingBlockRenderMode.RENDER, CapBankType.KIND });
  }

  @Override
  public IBlockState getStateFromMeta(int meta) {
    return getDefaultState().withProperty(CapBankType.KIND, CapBankType.getTypeFromMeta(meta));
  }

  @Override
  public int getMetaFromState(IBlockState state) {
    return CapBankType.getMetaFromType(state.getValue(CapBankType.KIND));
  }

  @Override
  public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
    return state.withProperty(EnumMergingBlockRenderMode.RENDER, EnumMergingBlockRenderMode.AUTO);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
    return getMapper().getExtendedState(state, world, pos);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public EnumWorldBlockLayer getBlockLayer() {
    return EnumWorldBlockLayer.CUTOUT;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void getSubBlocks(Item p_149666_1_, CreativeTabs p_149666_2_, List<ItemStack> list) {
    int meta = 0;
    for (CapBankType type : CapBankType.types()) {
      if (type.isCreative()) {
        list.add(BlockItemCapBank.createItemStackWithPower(meta, type.getMaxEnergyStored() / 2));
      } else {
        list.add(BlockItemCapBank.createItemStackWithPower(meta, 0));
        list.add(BlockItemCapBank.createItemStackWithPower(meta, type.getMaxEnergyStored()));
      }
      meta++;
    }
  }

  @Override
  public int damageDropped(IBlockState st) {
    return getMetaFromState(st);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addCommonEntries(ItemStack itemstack, EntityPlayer entityplayer, List<String> list, boolean flag) {
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addBasicEntries(ItemStack itemstack, EntityPlayer entityplayer, List<String> list, boolean flag) {
    list.add(PowerDisplayUtil.formatStoredPower(PowerHandlerUtil.getStoredEnergyForItem(itemstack),
        CapBankType.getTypeFromMeta(itemstack.getItemDamage()).getMaxEnergyStored()));
    if (itemstack.getTagCompound() != null && itemstack.getTagCompound().hasKey("Items")) {
      NBTTagList itemList = (NBTTagList) itemstack.getTagCompound().getTag("Items");
      String msg = EnderIO.lang.localizeExact("tile.blockCapBank.tooltip.hasItems");
      list.add(EnumChatFormatting.GOLD + MessageFormat.format(msg, itemList.tagCount()));
    }
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addDetailedEntries(ItemStack itemstack, EntityPlayer entityplayer, List<String> list, boolean flag) {
    SpecialTooltipHandler.addDetailedTooltipFromResources(list, itemstack);
  }

  @Override
  public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityPlayer, EnumFacing faceHit, float hitX, float hitY,
      float hitZ) {

    TileEntity te = world.getTileEntity(pos);
    if (!(te instanceof TileCapBank)) {
      return false;
    }

    TileCapBank tcb = (TileCapBank) te;

    if (entityPlayer.isSneaking() && entityPlayer.getCurrentEquippedItem() == null && faceHit.getFrontOffsetY() == 0) {
      InfoDisplayType newDisplayType = tcb.getDisplayType(faceHit).next();
      if (newDisplayType == InfoDisplayType.NONE) {
        tcb.setDefaultIoMode(faceHit);
      } else {
        tcb.setIoMode(faceHit, IoMode.DISABLED);
      }
      tcb.setDisplayType(faceHit, newDisplayType);
      return true;
    }

    if (!entityPlayer.isSneaking() && ToolUtil.isToolEquipped(entityPlayer)) {

      IoMode ioMode = tcb.getIoMode(faceHit);
      if (faceHit.getFrontOffsetY() == 0) {
        if (ioMode == IoMode.DISABLED) {
          InfoDisplayType newDisplayType = tcb.getDisplayType(faceHit).next();
          tcb.setDisplayType(faceHit, newDisplayType);
          if (newDisplayType == InfoDisplayType.NONE) {
            tcb.setDefaultIoMode(faceHit);
          }
        } else {
          tcb.toggleIoModeForFace(faceHit);
        }
      } else {
        tcb.toggleIoModeForFace(faceHit);
      }

      if (world.isRemote) {
        world.markBlockForUpdate(pos);
      } else {
        world.notifyNeighborsOfStateChange(pos, EnderIO.blockCapBank);
        world.markBlockForUpdate(pos);
      }

      return true;
    }

    return super.onBlockActivated(world, pos, state, entityPlayer, faceHit, hitX, hitY, hitZ);
  }

  @Override
  public boolean doNormalDrops(IBlockAccess world, BlockPos pos) {
    return false;
  }

  @Override
  protected boolean openGui(World world, BlockPos pos, EntityPlayer entityPlayer, EnumFacing side) {
    if (!world.isRemote) {
      entityPlayer.openGui(EnderIO.instance, GuiHandler.GUI_ID_CAP_BANK, world, pos.getX(), pos.getY(), pos.getZ());
    }
    return true;
  }

  @Override
  public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
    if (te instanceof TileCapBank) {
      return new ContainerCapBank(player.inventory, (TileCapBank) te);
    }
    return null;
  }

  @Override
  public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
    if (te instanceof TileCapBank) {
      return new GuiCapBank(player, player.inventory, (TileCapBank) te);
    }
    return null;
  }

  @Override
  public boolean isSideSolid(IBlockAccess world, BlockPos pos, EnumFacing side) {
    return true;
  }

  @Override
  public boolean isOpaqueCube() {
    return false;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public boolean shouldSideBeRendered(IBlockAccess par1IBlockAccess, BlockPos pos, EnumFacing side) {
    Block i1 = par1IBlockAccess.getBlockState(pos).getBlock();
    return i1 == this ? false : super.shouldSideBeRendered(par1IBlockAccess, pos, side);
  }

  @SideOnly(Side.CLIENT)
  public TextureAtlasSprite getGaugeIcon() {
    return gaugeIcon.get(TextureAtlasSprite.class);
  }

  @SideOnly(Side.CLIENT)
  public TextureAtlasSprite getFillBarIcon() {
    return fillBarIcon.get(TextureAtlasSprite.class);
  }

  @SideOnly(Side.CLIENT)
  public TextureAtlasSprite getInfoPanelIcon() {
    return infoPanelIcon.get(TextureAtlasSprite.class);
  }

  @Override
  public void onNeighborBlockChange(World world, BlockPos pos, IBlockState state, Block neighborBlock) {
    if (world.isRemote) {
      return;
    }
    TileEntity tile = world.getTileEntity(pos);
    if (tile instanceof TileCapBank) {
      TileCapBank te = (TileCapBank) tile;
      te.onNeighborBlockChange(neighborBlock);
    }
  }

  @Override
  public int quantityDropped(Random r) {
    return 0;
  }

  @Override
  public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase player, ItemStack stack) {
    super.onBlockPlacedBy(world, pos, state, player, stack);

    TileCapBank cb = getTileEntity(world, pos);
    if (cb == null) {
      return;
    }
    if (stack.getTagCompound() != null) {
      cb.readCommonNBT(stack.getTagCompound());
    }

    Collection<TileCapBank> neigbours = NetworkUtil.getNeigbours(cb);
    if (neigbours.isEmpty()) {
      int heading = MathHelper.floor_double(player.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
      EnumFacing dir = getDirForHeading(heading);
      cb.setDisplayType(dir, InfoDisplayType.LEVEL_BAR);
    } else {
      boolean modifiedDisplayType;
      modifiedDisplayType = setDisplayToVerticalFillBar(cb, getTileEntity(world, pos.down()));
      modifiedDisplayType |= setDisplayToVerticalFillBar(cb, getTileEntity(world, pos.up()));
      if (modifiedDisplayType) {
        cb.validateDisplayTypes();
      }
    }

    if (world.isRemote) {
      return;
    }
    world.markBlockForUpdate(pos);
  }

  protected boolean setDisplayToVerticalFillBar(TileCapBank cb, TileCapBank capBank) {
    boolean modifiedDisplayType = false;
    if (capBank != null) {
      for (EnumFacing dir : EnumFacing.VALUES) {
        if (dir.getFrontOffsetY() == 0 && capBank.getDisplayType(dir) == InfoDisplayType.LEVEL_BAR && capBank.getType() == cb.getType()) {
          cb.setDisplayType(dir, InfoDisplayType.LEVEL_BAR);
          modifiedDisplayType = true;
        }
      }
    }
    return modifiedDisplayType;
  }

  protected EnumFacing getDirForHeading(int heading) {
    switch (heading) {
    case 0:
      return EnumFacing.values()[2];
    case 1:
      return EnumFacing.values()[5];
    case 2:
      return EnumFacing.values()[3];
    case 3:
    default:
      return EnumFacing.values()[4];
    }
  }

  @Override
  public boolean removedByPlayer(World world, BlockPos pos, EntityPlayer player, boolean willHarvest) {
    if (!world.isRemote && (!player.capabilities.isCreativeMode)) {
      TileEntity te = world.getTileEntity(pos);
      if (te instanceof TileCapBank) {
        TileCapBank cb = (TileCapBank) te;
        cb.moveInventoryToNetwork();
      }
    }
    return super.removedByPlayer(world, pos, player, willHarvest);
  }

  @Override
  protected void processDrop(IBlockAccess world, BlockPos pos, TileCapBank te, ItemStack drop) {
    drop.setTagCompound(new NBTTagCompound());
    if (te != null) {
      te.writeCommonNBT(drop.getTagCompound());
    }
  }

  @Override
  public void breakBlock(World world, BlockPos pos, IBlockState state) {
    if (!world.isRemote) {
      TileEntity te = world.getTileEntity(pos);
      if (!(te instanceof TileCapBank)) {
        return;
      }
      TileCapBank cb = (TileCapBank) te;
      cb.onBreakBlock();
    }
    super.breakBlock(world, pos, state);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public AxisAlignedBB getSelectedBoundingBox(World world, BlockPos pos) {
    TileCapBank tr = getTileEntity(world, pos);
    if (tr == null) {
      return super.getSelectedBoundingBox(world, pos);
    }
    ICapBankNetwork network = tr.getNetwork();
    if (!tr.getType().isMultiblock() || network == null) {
      return super.getSelectedBoundingBox(world, pos);
    }

    Vector3d min = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
    Vector3d max = new Vector3d(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
    for (TileCapBank bc : network.getMembers()) {
      int x = bc.getPos().getX();
      int y = bc.getPos().getY();
      int z = bc.getPos().getZ();
      min.x = Math.min(min.x, x);
      max.x = Math.max(max.x, x + 1);
      min.y = Math.min(min.y, y);
      max.y = Math.max(max.y, y + 1);
      min.z = Math.min(min.z, z);
      max.z = Math.max(max.z, z + 1);
    }
    return new AxisAlignedBB(min.x, min.y, min.z, max.x, max.y, max.z);
  }

  @Override
  public boolean hasComparatorInputOverride() {
    return true;
  }

  @Override
  public int getComparatorInputOverride(World w, BlockPos pos) {
    TileEntity te = w.getTileEntity(pos);
    if (te instanceof TileCapBank) {
      return ((TileCapBank) te).getComparatorOutput();
    }
    return 0;
  }

  @Override
  public void getWailaInfo(List<String> tooltip, EntityPlayer player, World world, int x, int y, int z) {
    TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
    if (te instanceof TileCapBank) {
      TileCapBank cap = (TileCapBank) te;
      if (cap.getNetwork() != null) {
        if (world.isRemote && shouldDoWorkThisTick(world, new BlockPos(x, y, z), 20)) {
          PacketHandler.INSTANCE.sendToServer(new PacketNetworkStateRequest(cap));
        }
        ICapBankNetwork nw = cap.getNetwork();
        if (world.isRemote) {
          ((CapBankClientNetwork) nw).requestPowerUpdate(cap, 2);
        }

        if (SpecialTooltipHandler.showAdvancedTooltips()) {
          String format = Util.TAB + Util.ALIGNRIGHT + EnumChatFormatting.WHITE;
          String suffix = Util.TAB + Util.ALIGNRIGHT + PowerDisplayUtil.abrevation() + PowerDisplayUtil.perTickStr();
          tooltip.add(String.format("%s : %s%s%s", EnderIO.lang.localize("capbank.maxIO"), format, PowerDisplayUtil.formatPower(nw.getMaxIO()), suffix));
          tooltip.add(String.format("%s : %s%s%s", EnderIO.lang.localize("capbank.maxIn"), format, PowerDisplayUtil.formatPower(nw.getMaxInput()), suffix));
          tooltip.add(String.format("%s : %s%s%s", EnderIO.lang.localize("capbank.maxOut"), format, PowerDisplayUtil.formatPower(nw.getMaxOutput()), suffix));
          tooltip.add("");
        }

        long stored = nw.getEnergyStoredL();
        long max = nw.getMaxEnergyStoredL();
        tooltip.add(String.format("%s%s%s / %s%s%s %s", EnumChatFormatting.WHITE, PowerDisplayUtil.formatPower(stored), EnumChatFormatting.RESET,
            EnumChatFormatting.WHITE, PowerDisplayUtil.formatPower(max), EnumChatFormatting.RESET, PowerDisplayUtil.abrevation()));

        int change = Math.round(nw.getAverageChangePerTick());
        String color = EnumChatFormatting.WHITE.toString();
        if (change > 0) {
          color = EnumChatFormatting.GREEN.toString() + "+";
        } else if (change < 0) {
          color = EnumChatFormatting.RED.toString();
        }
        tooltip.add(String.format("%s%s%s", color, PowerDisplayUtil.formatPowerPerTick(change), " " + EnumChatFormatting.RESET.toString()));
      }
    }
  }

  @Override
  public int getDefaultDisplayMask(World world, int x, int y, int z) {
    return IWailaInfoProvider.BIT_DETAILED;
  }

  /* IRedstoneConnectable */

  @Override
  public boolean shouldRedstoneConduitConnect(World world, int x, int y, int z, EnumFacing from) {
    return true;
  }

  @Override
  @SideOnly(Side.CLIENT)
  public IRenderMapper getRenderMapper(IBlockState state, IBlockAccess world, BlockPos pos) {
    return getMapper();
  }

  @Override
  @SideOnly(Side.CLIENT)
  public IRenderMapper getRenderMapper(ItemStack stack) {
    return getMapper();
  }
  
  @SideOnly(Side.CLIENT)
  public CapBankRenderMapper getMapper() {
    if(CAPBANK_RENDER_MAPPER == null) {
      CAPBANK_RENDER_MAPPER = new CapBankRenderMapper();
    }
    return CAPBANK_RENDER_MAPPER;
  }

  @SideOnly(Side.CLIENT)
  public IOMode.EnumIOMode mapIOMode(InfoDisplayType displayType, IoMode mode) {
    switch (displayType) {
    case IO:
      return IOMode.EnumIOMode.CAPACITORBANK;
    case LEVEL_BAR:
      switch (mode) {
      case NONE:
        return IOMode.EnumIOMode.CAPACITORBANK;
      case PULL:
        return IOMode.EnumIOMode.CAPACITORBANKINPUTSMALL;
      case PUSH:
        return IOMode.EnumIOMode.CAPACITORBANKOUTPUTSMALL;
      case PUSH_PULL:
        return IOMode.EnumIOMode.CAPACITORBANK;
      case DISABLED:
        return IOMode.EnumIOMode.CAPACITORBANKLOCKEDSMALL;
      }
    case NONE:
      switch (mode) {
      case NONE:
        return IOMode.EnumIOMode.CAPACITORBANK;
      case PULL:
        return IOMode.EnumIOMode.CAPACITORBANKINPUT;
      case PUSH:
        return IOMode.EnumIOMode.CAPACITORBANKOUTPUT;
      case PUSH_PULL:
        return IOMode.EnumIOMode.CAPACITORBANK;
      case DISABLED:
        return IOMode.EnumIOMode.CAPACITORBANKLOCKED;
      }
    }
    throw new RuntimeException("Hey, leave our enums alone!");
  }
}

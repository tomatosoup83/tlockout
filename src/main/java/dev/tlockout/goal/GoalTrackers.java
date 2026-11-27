package dev.tlockout.goal;

import dev.tlockout.GoalType;
import dev.tlockout.goal.tracker.AdvancementCountGoalTracker;
import dev.tlockout.goal.tracker.AdvancementGoalTracker;
import dev.tlockout.goal.tracker.ArmorPieceGoalTracker;
import dev.tlockout.goal.tracker.ArmorSetGoalTracker;
import dev.tlockout.goal.tracker.ArmorTimerGoalTracker;
import dev.tlockout.goal.tracker.BannerShieldGoalTracker;
import dev.tlockout.goal.tracker.BiomeGoalTracker;
import dev.tlockout.goal.tracker.BlockActionGoalTracker;
import dev.tlockout.goal.tracker.BreedGoalTracker;
import dev.tlockout.goal.tracker.BreedUniqueGoalTracker;
import dev.tlockout.goal.tracker.BrewPotionGoalTracker;
import dev.tlockout.goal.tracker.ColoredSheepKillGoalTracker;
import dev.tlockout.goal.tracker.ConditionalKillGoalTracker;
import dev.tlockout.goal.tracker.ConsumeItemGoalTracker;
import dev.tlockout.goal.tracker.DamageTotalGoalTracker;
import dev.tlockout.goal.tracker.DeathGoalTracker;
import dev.tlockout.goal.tracker.DimensionGoalTracker;
import dev.tlockout.goal.tracker.DurabilityGoalTracker;
import dev.tlockout.goal.tracker.EmptyHungerGoalTracker;
import dev.tlockout.goal.tracker.ItemGoalTracker;
import dev.tlockout.goal.tracker.ItemSetGoalTracker;
import dev.tlockout.goal.tracker.MilkEffectGoalTracker;
import dev.tlockout.goal.tracker.MineBlockGoalTracker;
import dev.tlockout.goal.tracker.MobKillGoalTracker;
import dev.tlockout.goal.tracker.MobKillGroupGoalTracker;
import dev.tlockout.goal.tracker.MobKillTotalGoalTracker;
import dev.tlockout.goal.tracker.MobKillUniqueGoalTracker;
import dev.tlockout.goal.tracker.OpponentGoalTracker;
import dev.tlockout.goal.tracker.PlayerKillGoalTracker;
import dev.tlockout.goal.tracker.PositionGoalTracker;
import dev.tlockout.goal.tracker.RideGoalTracker;
import dev.tlockout.goal.tracker.StatisticGoalTracker;
import dev.tlockout.goal.tracker.StatusEffectCountGoalTracker;
import dev.tlockout.goal.tracker.StatusEffectGoalTracker;
import dev.tlockout.goal.tracker.TameGoalTracker;
import dev.tlockout.goal.tracker.UniqueFoodsGoalTracker;
import dev.tlockout.goal.tracker.UniqueInventoryGoalTracker;
import dev.tlockout.goal.tracker.WolfArmorGoalTracker;
import dev.tlockout.goal.tracker.WorkstationGoalTracker;
import dev.tlockout.goal.tracker.XpLevelGoalTracker;
import java.util.EnumMap;
import java.util.Map;

/** Maps each goal family to its tracker. New families register here. */
public final class GoalTrackers {

    private static final Map<GoalType, GoalTracker> BY_TYPE = new EnumMap<>(GoalType.class);

    static {
        BY_TYPE.put(GoalType.ITEM, new ItemGoalTracker());
        BY_TYPE.put(GoalType.DURABILITY, new DurabilityGoalTracker());
        BY_TYPE.put(GoalType.MOB_KILL, new MobKillGoalTracker());
        BY_TYPE.put(GoalType.PLAYER_KILL, new PlayerKillGoalTracker());
        BY_TYPE.put(GoalType.BIOME, new BiomeGoalTracker());
        BY_TYPE.put(GoalType.TAME, new TameGoalTracker());
        BY_TYPE.put(GoalType.BREED, new BreedGoalTracker());
        BY_TYPE.put(GoalType.DIMENSION, new DimensionGoalTracker());
        BY_TYPE.put(GoalType.CONSUME_ITEM, new ConsumeItemGoalTracker());
        BY_TYPE.put(GoalType.UNIQUE_FOODS, new UniqueFoodsGoalTracker());
        BY_TYPE.put(GoalType.ADVANCEMENT, new AdvancementGoalTracker());
        BY_TYPE.put(GoalType.ADVANCEMENT_COUNT, new AdvancementCountGoalTracker());
        BY_TYPE.put(GoalType.STATUS_EFFECT, new StatusEffectGoalTracker());
        BY_TYPE.put(GoalType.STATUS_EFFECT_COUNT, new StatusEffectCountGoalTracker());
        BY_TYPE.put(GoalType.MILK_EFFECT, new MilkEffectGoalTracker());
        BY_TYPE.put(GoalType.DEATH_BY, new DeathGoalTracker());
        BY_TYPE.put(GoalType.DAMAGE_TOTAL, new DamageTotalGoalTracker());
        BY_TYPE.put(GoalType.XP_LEVEL, new XpLevelGoalTracker());
        BY_TYPE.put(GoalType.POSITION, new PositionGoalTracker());
        BY_TYPE.put(GoalType.EMPTY_HUNGER, new EmptyHungerGoalTracker());
        BY_TYPE.put(GoalType.RIDE, new RideGoalTracker());
        BY_TYPE.put(GoalType.STAT_COUNTER, new StatisticGoalTracker());
        BY_TYPE.put(GoalType.ARMOR_SET, new ArmorSetGoalTracker());
        BY_TYPE.put(GoalType.ARMOR_PIECE, new ArmorPieceGoalTracker());
        BY_TYPE.put(GoalType.ARMOR_TIMER, new ArmorTimerGoalTracker());
        BY_TYPE.put(GoalType.WOLF_ARMOR, new WolfArmorGoalTracker());
        BY_TYPE.put(GoalType.ITEM_SET, new ItemSetGoalTracker());
        BY_TYPE.put(GoalType.UNIQUE_INVENTORY, new UniqueInventoryGoalTracker());
        BY_TYPE.put(GoalType.MOB_KILL_TOTAL, new MobKillTotalGoalTracker());
        BY_TYPE.put(GoalType.MOB_KILL_UNIQUE, new MobKillUniqueGoalTracker());
        BY_TYPE.put(GoalType.MOB_KILL_GROUP, new MobKillGroupGoalTracker());
        BY_TYPE.put(GoalType.COLORED_SHEEP_KILL, new ColoredSheepKillGoalTracker());
        BY_TYPE.put(GoalType.OPPONENT, new OpponentGoalTracker());
        BY_TYPE.put(GoalType.WORKSTATION, new WorkstationGoalTracker());
        BY_TYPE.put(GoalType.BREW_POTION, new BrewPotionGoalTracker());
        BY_TYPE.put(GoalType.MINE_BLOCK, new MineBlockGoalTracker());
        BY_TYPE.put(GoalType.BLOCK_ACTION, new BlockActionGoalTracker());
        BY_TYPE.put(GoalType.BANNER_SHIELD, new BannerShieldGoalTracker());
        BY_TYPE.put(GoalType.CONDITIONAL_KILL, new ConditionalKillGoalTracker());
        BY_TYPE.put(GoalType.BREED_UNIQUE, new BreedUniqueGoalTracker());
    }

    private GoalTrackers() {
    }

    public static GoalTracker of(GoalType type) {
        GoalTracker tracker = BY_TYPE.get(type);
        if (tracker == null) {
            throw new IllegalStateException("no tracker registered for goal type " + type);
        }
        return tracker;
    }
}

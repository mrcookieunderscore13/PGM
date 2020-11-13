package tc.oc.pgm.spawns.states;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAttackEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import tc.oc.pgm.api.event.PlayerItemTransferEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.spawns.Spawn;
import tc.oc.pgm.spawns.SpawnMatchModule;
import tc.oc.pgm.util.named.NameStyle;

/** Player is waiting to spawn as a participant */
public abstract class Spawning extends Participating {

  protected boolean spawnRequested;

  public Spawning(SpawnMatchModule smm, MatchPlayer player) {
    super(smm, player);
    this.spectatingIndex = 0;
    this.spawnRequested = options.auto;
  }

  @Override
  public void enterState() {
    super.enterState();

    player.setDead(true);
    player.resetGamemode();
  }

  public void requestSpawn() {
    this.spawnRequested = true;
  }

  @Override
  public void onEvent(PlayerInteractEvent event) {
    super.onEvent(event);
    event.setCancelled(true);
    if (options.betterSpecate && !player.isLegacy()) {
      player.sendMessage(TextComponent.of(event.getAction().toString()));
      if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
        cycleSpectate(true);
      }
      if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
        player.sendMessage(TextComponent.of("Cycling right"));
        cycleSpectate(false);
      }
    }
    if (event.getAction() == Action.LEFT_CLICK_AIR
        || event.getAction() == Action.LEFT_CLICK_BLOCK) {
      requestSpawn();
    }
  }

  @Override
  public void onEvent(PlayerInteractEntityEvent event) {
    super.onEvent(event);
    if (options.betterSpecate && player.isLegacy()) {
      player.sendMessage("cycling right");
      cycleSpectate(false);
    }
  }

  @Override
  public void onEvent(PlayerAttackEntityEvent event) {
    super.onEvent(event);
    event.setCancelled(true);
    requestSpawn();
  }

  @Override
  public void onEvent(final PlayerItemTransferEvent event) {
    super.onEvent(event);
    event.setCancelled(true);
  }

  @Override
  public void onEvent(EntityDamageEvent event) {
    super.onEvent(event);
    event.setCancelled(true);
  }

  @Override
  public void onEvent(MatchPlayerDeathEvent event) {
    super.onEvent(event);
    player.sendMessage(TextComponent.of(event.getVictim().getId().toString()));
    player.sendMessage(TextComponent.of(player.getBukkit().getUniqueId().toString()));
    if (options.betterSpecate
        && !player.isLegacy()
        && event.getVictim().getId() == player.getBukkit().getSpectatorTarget().getUniqueId()) {
      cycleSpectate(true);
    }
  }

  @Override
  public void tick() {
    if (!trySpawn()) {
      updateTitle();
      if (player.isLegacy()) sendMessage();
    }

    super.tick();
  }

  protected boolean trySpawn() {
    if (!spawnRequested) return false;

    Spawn spawn = chooseSpawn();
    if (spawn == null) return false;

    Location location = spawn.getSpawn(player);
    if (location == null) return false;

    transition(new Alive(smm, player, spawn, location));
    return true;
  }

  public @Nullable Spawn chooseSpawn() {
    if (spawnRequested) {
      return smm.chooseSpawn(player);
    } else {
      return null;
    }
  }

  private int spectatingIndex;

  protected void cycleSpectate(boolean left) {
    player.sendMessage(TextComponent.of("cycle spectate called"));
    List<MatchPlayer> players =
        player.getParty().getPlayers().stream()
            .filter(c -> c.isAlive())
            .collect(Collectors.toList());
    if (players.size() == 0) {
      player.getBukkit().setSpectatorTarget(null);
      player.setFrozen(true);
      player.sendMessage(TextComponent.of("No players to spec"));
      return;
    }
    if (left) {
      spectatingIndex = (spectatingIndex--) % players.size();
    } else {
      spectatingIndex = (spectatingIndex++) % players.size();
    }
    if (player.getGameMode() == GameMode.SPECTATOR) {
      player.getBukkit().setSpectatorTarget(players.get(spectatingIndex).getBukkit());
      player.sendMessage(
          TranslatableComponent.of(
                  "death.respawn.spectating",
                  players.get(spectatingIndex).getName(NameStyle.CONCISE))
              .color(TextColor.AQUA));
    }
  }

  public void sendMessage() {}

  public void updateTitle() {
    player.showTitle(getTitle(), getSubtitle().color(TextColor.GREEN), 0, 3, 3);
  }

  protected abstract Component getTitle();

  protected Component getSubtitle() {
    if (!spawnRequested) {
      return TranslatableComponent.of("death.respawn.unconfirmed");
    } else if (options.message != null) {
      return options.message;
    } else {
      return TranslatableComponent.of("death.respawn.confirmed.waiting");
    }
  }
}

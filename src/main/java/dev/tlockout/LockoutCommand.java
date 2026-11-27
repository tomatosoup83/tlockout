package dev.tlockout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class LockoutCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "start", "stop", "reroll", "mode", "goalstowin", "forcewin", "board", "compass", "status",
            "join", "leave", "team", "track", "autoreset", "keepinventory", "reload");

    private final TLockout plugin;

    public LockoutCommand(TLockout plugin) {
        this.plugin = plugin;
    }

    private ChallengeManager challenge() {
        return plugin.challenge();
    }

    private SideManager sides() {
        return challenge().sides();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> start(sender, label, args);
            case "stop" -> stop(sender);
            case "reroll" -> reroll(sender, label, args);
            case "mode" -> mode(sender, label, args);
            case "goalstowin" -> goalsToWin(sender, label, args);
            case "forcewin" -> forceWin(sender, label, args);
            case "board" -> board(sender);
            case "compass" -> compass(sender);
            case "status" -> status(sender);
            case "join" -> join(sender, label, args);
            case "leave" -> leave(sender);
            case "team" -> team(sender, label, args);
            case "track" -> track(sender, label, args);
            case "autoreset" -> autoReset(sender, label, args);
            case "keepinventory" -> keepInventory(sender, label, args);
            case "reload" -> reload(sender);
            default -> help(sender, label);
        }
        return true;
    }

    // ---------------------------------------------------------------- round

    private void start(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (challenge().running() || challenge().countdown()) {
            sender.sendMessage(plugin.msg("<red>A round is already "
                    + (challenge().countdown() ? "starting" : "running")
                    + ". Use <white>/" + label + " stop</white> first."));
            return;
        }
        if (plugin.catalog() == null || plugin.catalog().size() < Board.CELLS) {
            sender.sendMessage(plugin.msg("<red>Not enough goal templates in goals.yml (need "
                    + Board.CELLS + ")."));
            return;
        }

        // Testing escape hatch: normally every side needs a player, but a one-player test round
        // should still be startable. --allow-empty is admin-only through this command.
        boolean allowEmpty = false;
        boolean seedSet = false;
        long seed = System.nanoTime();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--allow-empty")) {
                allowEmpty = true;
                continue;
            }
            if (seedSet) {
                sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                        + " start [seed] [--allow-empty]</white>"));
                return;
            }
            try {
                seed = Long.parseLong(arg);
                seedSet = true;
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("<red>Seed must be a whole number, e.g. <white>"
                        + label + " start 12345</white>. Add <white>--allow-empty</white> for a "
                        + "testing round."));
                return;
            }
        }

        if (sides().isFfa()) {
            sides().enrollOnlineForFfa();
            List<Side> participants = sides().ordered();
            if (participants.size() < 2 && !allowEmpty) {
                sender.sendMessage(plugin.msg("<red>FFA needs at least 2 players online (found <white>"
                        + participants.size() + "</white>). Wait for another player, or use <white>/"
                        + label + " start --allow-empty</white> for a testing round."));
                return;
            }
            plugin.launchRound(label, seed, participants, List.of(), "Lockout round started!", "--allow-empty");
            return;
        }

        List<Side> sides = sides().ordered();
        List<Side> emptySides = new ArrayList<>();
        List<String> emptySideNames = new ArrayList<>();
        for (Side side : sides) {
            if (side.size() == 0) {
                emptySides.add(side);
                emptySideNames.add(side.displayName());
            }
        }
        if (!emptySides.isEmpty() && !allowEmpty) {
            boolean one = emptySideNames.size() == 1;
            sender.sendMessage(plugin.msg("<red>" + (one ? "Team" : "Teams") + " <white>"
                    + String.join(", ", emptySideNames) + "</white>"
                    + (one ? " has" : " have") + " no players. Everyone needs to <white>/" + label
                    + " join a team</white> first, or use <white>/" + label
                    + " start --allow-empty</white> for a testing round."));
            return;
        }

        plugin.launchRound(label, seed, sides, emptySideNames, "Lockout round started!", "--allow-empty");
    }

    private void mode(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("<gray>Current mode: <white>" + plugin.mode().displayName()
                    + "</white> (first to <white>" + challenge().winTarget()
                    + "</white> goals wins). Use <white>/" + label + " mode <teams|ffa></white>."));
            return;
        }
        LockoutMode next;
        try {
            next = LockoutMode.parse(args[1]);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label + " mode <teams|ffa></white>"));
            return;
        }
        if (challenge().running() || challenge().countdown()) {
            sender.sendMessage(plugin.msg("<red>Cannot switch modes while a round is "
                    + (challenge().countdown() ? "starting" : "running")
                    + ". Stop it first with <white>/" + label + " stop</white>."));
            return;
        }
        try {
            plugin.setMode(next);
        } catch (RuntimeException ex) {
            sender.sendMessage(plugin.msg("<red>Could not switch mode: " + ex.getMessage()));
            return;
        }
        String hint = next == LockoutMode.FFA
                ? "Online players are enrolled automatically on <white>/" + label + " start</white>."
                : "Players can pick teams with <white>/" + label + " join <team></white>.";
        plugin.getServer().sendMessage(plugin.msg("<gold>Lockout mode set to <white><bold>"
                + next.displayName() + "</bold></white></gold> <gray>(first to <white>"
                + challenge().winTarget() + "</white> goals wins). " + hint + "</gray>"));
    }

    private void goalsToWin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("<gray>Goals to win (<white>" + plugin.mode().displayName()
                    + "</white>): <white>" + challenge().winTarget()
                    + "</white>. Use <white>/" + label + " goalstowin <1-" + Board.CELLS + "></white>."));
            return;
        }
        int target;
        try {
            target = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.msg("<red>Enter a whole number between 1 and " + Board.CELLS
                    + ", e.g. <white>/" + label + " goalstowin 5</white>."));
            return;
        }
        if (target < 1 || target > Board.CELLS) {
            sender.sendMessage(plugin.msg("<red>Goals to win must be between 1 and " + Board.CELLS + "."));
            return;
        }
        plugin.getServer().sendMessage(plugin.msg("<gold>Goals to win (<white>"
                + plugin.mode().displayName() + "</white>) set to <white><bold>" + target
                + "</bold></white>.</gold>"));
        plugin.setWinTargetForCurrentMode(target);
    }

    private void forceWin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (!challenge().running()) {
            sender.sendMessage(plugin.msg("<red>No round is currently running."));
            return;
        }
        if (args.length < 2) {
            String targetHint = sides().isFfa() ? "<player>" : "<team>";
            sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label + " forcewin " + targetHint
                    + "</white>. Available: <white>" + forceWinOptionsDisplay() + "</white>"));
            return;
        }
        Side target = resolveSide(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("<red>Unknown " + (sides().isFfa() ? "player" : "team")
                    + " <white>" + args[1] + "</white>. Available: <white>"
                    + forceWinOptionsDisplay() + "</white>"));
            return;
        }
        challenge().forceWin(target, sender.getName());
    }

    private Side resolveSide(String raw) {
        Side byId = sides().byId(raw);
        if (byId != null) {
            return byId;
        }
        for (Side side : sides().ordered()) {
            if (side.displayName().equalsIgnoreCase(raw)) {
                return side;
            }
        }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) {
            return sides().sideOf(online.getUniqueId());
        }
        return null;
    }

    private String forceWinOptionsDisplay() {
        List<String> labels = new ArrayList<>();
        for (Side side : sides().ordered()) {
            labels.add(sides().isFfa() ? side.displayName() : side.id());
        }
        return labels.isEmpty() ? "(none)" : String.join(", ", labels);
    }

    private void reroll(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (plugin.catalog() == null || plugin.catalog().size() < Board.CELLS) {
            sender.sendMessage(plugin.msg("<red>Not enough goal templates in goals.yml (need "
                    + Board.CELLS + ")."));
            return;
        }
        if (args.length > 2) {
            sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label + " reroll [seed]</white>"));
            return;
        }
        long seed = System.nanoTime();
        if (args.length == 2) {
            try {
                seed = Long.parseLong(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("<red>Seed must be a whole number, e.g. <white>/"
                        + label + " reroll 12345</white>."));
                return;
            }
        }

        plugin.rerollChallenge(seed, label);
    }

    private void stop(CommandSender sender) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (challenge().state() == ChallengeManager.State.IDLE) {
            sender.sendMessage(plugin.msg("<red>No challenge is running."));
            return;
        }
        plugin.stopChallenge();
        plugin.getServer().sendMessage(plugin.msg("<yellow>The lockout challenge has been stopped.</yellow>"));
    }

    private void board(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("<red>Only players can open the board."));
            return;
        }
        if (!player.hasPermission("tlockout.play")) {
            deny(sender);
            return;
        }
        plugin.boardGui().open(player);
    }

    private void compass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("<red>Only players can receive a Board Compass."));
            return;
        }
        if (!player.hasPermission("tlockout.play")) {
            deny(sender);
            return;
        }
        if (challenge().board() == null) {
            player.sendMessage(plugin.msg("<red>No challenge is running."));
            return;
        }
        if (plugin.boardCompass().has(player)) {
            player.sendMessage(plugin.msg("<gray>You already have a Board Compass.</gray>"));
            return;
        }
        plugin.boardCompass().give(player);
        player.sendMessage(plugin.msg("<green>Gave you a Board Compass.</green>"));
    }

    private void status(CommandSender sender) {
        Board board = challenge().board();
        if (board == null) {
            sender.sendMessage(plugin.msg("<red>No challenge is running."));
            return;
        }
        String modeTag = challenge().isFfa() ? " <aqua>[FFA]</aqua>" : "";
        sender.sendMessage(plugin.msg("<gold><bold>Lockout</bold></gold>" + modeTag
                + " <gray>seed <white>" + board.seed()
                + "</white> - <white>" + board.claimedCount() + "/" + Board.CELLS + "</white> claimed - first to <white>"
                + challenge().winTarget() + "</white>"));

        if (challenge().isFfa()) {
            int rank = 1;
            for (Side side : challenge().rankedSides()) {
                sender.sendMessage(Component.text("  #" + rank + " ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(side.displayName(), side.color()))
                        .append(Component.text(": " + challenge().claimsFor(side.id()) + " goals",
                                NamedTextColor.GRAY)));
                rank++;
            }
        } else {
            for (Side side : sides().ordered()) {
                sender.sendMessage(Component.text("  ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(side.displayName(), side.color()))
                        .append(Component.text(": " + challenge().claimsFor(side.id()) + " goals, "
                                + side.size() + " players", NamedTextColor.GRAY)));
            }
        }
        if (challenge().finished() && challenge().winner() != null) {
            List<Side> winners = challenge().winners();
            if (winners.size() > 1) {
                Component line = Component.text("  Winners (tie): ", NamedTextColor.GOLD);
                for (int i = 0; i < winners.size(); i++) {
                    if (i > 0) {
                        line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                    }
                    Side w = winners.get(i);
                    line = line.append(Component.text(w.displayName(), w.color()));
                }
                sender.sendMessage(line);
            } else {
                Side winner = challenge().winner();
                sender.sendMessage(Component.text("  Winner: ", NamedTextColor.GRAY)
                        .append(Component.text(winner.displayName(), winner.color())));
            }
        }
        for (int cell = 0; cell < Board.CELLS; cell++) {
            Board.Claim claim = board.claim(cell);
            if (claim == null) {
                continue;
            }
            Side side = sides().byId(claim.sideId());
            Component claimLine = Component.text("  - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(board.cell(cell).display(), NamedTextColor.WHITE))
                    .append(Component.text(" by ", NamedTextColor.GRAY))
                    .append(Component.text(claim.playerName(), claim.color()));
            if (side != null && !challenge().isFfa()) {
                claimLine = claimLine.append(Component.text(" (" + side.displayName() + ")", side.color()));
            }
            sender.sendMessage(claimLine);
        }
    }

    // ----------------------------------------------------------------- teams

    private void join(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("<red>Only players can join."));
            return;
        }
        if (!player.hasPermission("tlockout.play")) {
            deny(sender);
            return;
        }
        if (sides().isFfa()) {
            joinFfa(player, label, args);
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("<gray>Pick a team: <white>"
                    + sideIds() + "</white> - e.g. <white>/" + label + " join " + firstSideId() + "</white>"));
            return;
        }
        Side side = sides().byId(args[1]);
        if (side == null) {
            player.sendMessage(plugin.msg("<red>Unknown team. Available: <white>" + sideIds() + "</white>"));
            return;
        }
        Side previous = sides().sideOf(player.getUniqueId());
        if (!sides().assign(player.getUniqueId(), side.id())) {
            player.sendMessage(plugin.msg("<red>You are already on a team; teams are locked while a round is running."));
            return;
        }
        if (challenge().running()) {
            boolean wasUnassigned = previous == null;
            plugin.onMidGameTeamJoin(player, wasUnassigned);
            String resetNote = (wasUnassigned && plugin.playerReset().enabled()) ? " (player reset)" : "";
            plugin.getServer().sendMessage(plugin.prefix()
                    .append(Component.text(player.getName(), side.color()))
                    .append(Component.text(" joined ", NamedTextColor.GRAY))
                    .append(Component.text(side.displayName(), side.color()))
                    .append(Component.text(" mid-round" + resetNote + ".", NamedTextColor.GRAY)));
            return;
        }
        player.sendMessage(plugin.prefix()
                .append(Component.text("You joined ", NamedTextColor.GRAY))
                .append(Component.text(side.displayName(), side.color())));
    }

    private void joinFfa(Player player, String label, String[] args) {
        Material preferred = null;
        if (args.length >= 2) {
            preferred = ConcreteColors.parseConcrete(args[1]);
            if (preferred == null) {
                player.sendMessage(plugin.msg("<red>Unknown colour <white>" + args[1]
                        + "</white>. Pick one of: <white>" + String.join(", ", ConcreteColors.colorNames())
                        + "</white> (or omit for auto)."));
                return;
            }
        }
        Side previous = sides().sideOf(player.getUniqueId());
        if (previous != null && (challenge().running() || challenge().countdown())) {
            player.sendMessage(plugin.msg("<red>You are already in this FFA round."));
            return;
        }
        Side enrolled = sides().enrollFfa(player, preferred);
        if (challenge().running()) {
            plugin.onMidGameTeamJoin(player, true);
            String resetNote = plugin.playerReset().enabled() ? " (player reset)" : "";
            plugin.getServer().sendMessage(plugin.prefix()
                    .append(Component.text(player.getName(), enrolled.color()))
                    .append(Component.text(" joined the FFA mid-round" + resetNote + ".", NamedTextColor.GRAY)));
            return;
        }
        String colorLabel = ConcreteColors.colorLabel(enrolled.concrete());
        if (previous != null && preferred != null) {
            player.sendMessage(plugin.prefix()
                    .append(Component.text("Updated your FFA colour to ", NamedTextColor.GRAY))
                    .append(Component.text(colorLabel, enrolled.color())));
        } else {
            player.sendMessage(plugin.prefix()
                    .append(Component.text("You joined FFA (", NamedTextColor.GRAY))
                    .append(Component.text(colorLabel, enrolled.color()))
                    .append(Component.text("). Use /" + label + " leave to spectate.", NamedTextColor.GRAY)));
        }
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("<red>Only players can leave."));
            return;
        }
        if (challenge().running() || challenge().countdown()) {
            player.sendMessage(plugin.msg(sides().isFfa()
                    ? "<red>You cannot leave while an FFA round is running."
                    : "<red>Teams are locked while a round is running."));
            return;
        }
        if (sides().isFfa()) {
            sides().optOutFfa(player.getUniqueId());
            player.sendMessage(plugin.msg(
                    "<gray>You opted out of FFA and will spectate until you run <white>/lo join</white>.</gray>"));
            return;
        }
        if (sides().sideOf(player.getUniqueId()) == null) {
            player.sendMessage(plugin.msg("<red>You are not on a team."));
            return;
        }
        sides().remove(player.getUniqueId());
        player.sendMessage(plugin.msg("<gray>You left your team."));
    }

    private void team(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                    + " team <list|auto|set <player> <team>></white>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> teamList(sender);
            case "auto" -> teamAuto(sender);
            case "set" -> teamSet(sender, label, args);
            default -> sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                    + " team <list|auto|set <player> <team>></white>"));
        }
    }

    private void teamList(CommandSender sender) {
        if (sides().isFfa()) {
            List<Side> enrolled = sides().ordered();
            sender.sendMessage(plugin.msg("<gold>FFA Roster</gold> <gray>(online players auto-enroll on start):</gray>"));
            if (enrolled.isEmpty()) {
                sender.sendMessage(Component.text("  (none enrolled yet)", NamedTextColor.DARK_GRAY));
            } else {
                for (Side side : enrolled) {
                    sender.sendMessage(Component.text("  - ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(side.displayName(), side.color()))
                            .append(Component.text(" (" + ConcreteColors.colorLabel(side.concrete()) + ")",
                                    NamedTextColor.GRAY)));
                }
            }
            if (!sides().ffaOptOuts().isEmpty()) {
                List<String> optOuts = new ArrayList<>();
                for (UUID id : sides().ffaOptOuts()) {
                    Player online = Bukkit.getPlayer(id);
                    String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(id).getName();
                    optOuts.add(name != null ? name : "offline");
                }
                sender.sendMessage(Component.text("  Spectating (opted out): " + String.join(", ", optOuts),
                        NamedTextColor.DARK_GRAY));
            }
            return;
        }
        for (Side side : sides().ordered()) {
            List<String> names = new ArrayList<>();
            for (var id : side.members()) {
                Player online = Bukkit.getPlayer(id);
                names.add(online == null ? "offline" : online.getName());
            }
            sender.sendMessage(Component.text("  ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(side.displayName(), side.color()))
                    .append(Component.text(" (" + names.size() + "): " + String.join(", ", names),
                            NamedTextColor.GRAY)));
        }
    }

    private void teamAuto(CommandSender sender) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (sides().isFfa()) {
            sender.sendMessage(plugin.msg(
                    "<gray>FFA mode auto-enrolls all online players on <white>/lo start</white>. Switch with <white>/lo mode teams</white> for teams.</gray>"));
            return;
        }
        if (challenge().running() || challenge().countdown()) {
            sender.sendMessage(plugin.msg("<red>Teams are locked while a round is running."));
            return;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.size() < 2) {
            sender.sendMessage(plugin.msg("<red>Auto-balance needs at least 2 players online."));
            return;
        }
        Collections.shuffle(players);
        List<Side> order = sides().ordered();
        for (int i = 0; i < players.size(); i++) {
            sides().assign(players.get(i).getUniqueId(), order.get(i % order.size()).id());
        }
        sender.sendMessage(plugin.msg("<green>Balanced " + players.size() + " players across "
                + order.size() + " teams.</green>"));
        teamList(sender);
    }

    private void teamSet(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        if (sides().isFfa()) {
            sender.sendMessage(plugin.msg("<gray>Teams commands are only used in Teams mode. Switch with <white>/"
                    + label + " mode teams</white>."));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                    + " team set <player> <team></white>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(plugin.msg("<red><white>" + args[2] + "</white> is not online."));
            return;
        }
        Side side = sides().byId(args[3]);
        if (side == null) {
            sender.sendMessage(plugin.msg("<red>Unknown team. Available: <white>" + sideIds() + "</white>"));
            return;
        }
        Side previous = sides().sideOf(target.getUniqueId());
        if (previous != null && previous.id().equals(side.id())) {
            sender.sendMessage(plugin.msg("<gray><white>" + target.getName() + "</white> is already on "
                    + side.displayName() + ".</gray>"));
            return;
        }
        sides().forceAssign(target.getUniqueId(), side.id());
        if (challenge().running()) {
            boolean wasUnassigned = previous == null;
            plugin.onMidGameTeamJoin(target, wasUnassigned);
            String resetNote = (wasUnassigned && plugin.playerReset().enabled()) ? " (player reset)" : "";
            plugin.getServer().sendMessage(plugin.prefix()
                    .append(Component.text(target.getName(), side.color()))
                    .append(Component.text(wasUnassigned ? " joined " : " was moved to ", NamedTextColor.GRAY))
                    .append(Component.text(side.displayName(), side.color()))
                    .append(Component.text(" mid-round" + resetNote + ".", NamedTextColor.GRAY)));
            return;
        }
        sender.sendMessage(plugin.msg("<green>Moved " + target.getName() + " to " + side.displayName()
                + ".</green>"));
        target.sendMessage(plugin.prefix()
                .append(Component.text("You were moved to ", NamedTextColor.GRAY))
                .append(Component.text(side.displayName(), side.color())));
    }

    private void track(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("<red>Only players can track goals."));
            return;
        }
        if (!player.hasPermission("tlockout.play")) {
            deny(sender);
            return;
        }
        if (challenge().board() == null) {
            player.sendMessage(plugin.msg("<red>No challenge is running."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                    + " track <1-" + Board.CELLS + "|clear></white>"));
            return;
        }
        boolean ffa = sides().isFfa();
        if (args[1].equalsIgnoreCase("clear")) {
            Side side = challenge().sideOf(player);
            if (side == null) {
                player.sendMessage(plugin.msg(ffa
                        ? "<red>Join the FFA first with <white>/" + label + " join</white>."
                        : "<red>Join a team first."));
                return;
            }
            side.clearTargets();
            plugin.refreshViews();
            player.sendMessage(plugin.msg(ffa
                    ? "<gray>Cleared your tracked goals.</gray>"
                    : "<gray>Cleared your team's tracked goals.</gray>"));
            return;
        }
        int number;
        try {
            number = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("<red>Goal numbers run from 1 to " + Board.CELLS + "."));
            return;
        }
        if (number < 1 || number > Board.CELLS) {
            player.sendMessage(plugin.msg("<red>Goal numbers run from 1 to " + Board.CELLS + "."));
            return;
        }
        switch (challenge().toggleTarget(player, number - 1)) {
            case ADDED -> player.sendMessage(plugin.msg(ffa
                    ? "<green>Tracking goal #" + number + ".</green>"
                    : "<green>Tracking goal #" + number
                            + ".</green> <dark_gray>Shared with your team.</dark_gray>"));
            case REMOVED -> player.sendMessage(plugin.msg("<gray>No longer tracking goal #" + number + ".</gray>"));
            case FULL -> player.sendMessage(plugin.msg((ffa
                    ? "<red>You are already tracking "
                    : "<red>Your team is already tracking ")
                    + Side.MAX_TARGETS + " goals.</red>"));
            case CLAIMED -> player.sendMessage(plugin.msg("<red>That goal is already claimed.</red>"));
            case UNAVAILABLE -> player.sendMessage(plugin.msg("<red>No round is running."));
        }
        plugin.refreshViews();
    }

    private void autoReset(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        PlayerResetService reset = plugin.playerReset();
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("<gray>Auto-reset on round start is currently "
                    + (reset.enabled() ? "<green>ON</green>" : "<red>OFF</red>")
                    + ". Use <white>/" + label + " autoreset <on|off|toggle></white>."));
            return;
        }
        boolean next;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable" -> next = true;
            case "off", "false", "disable" -> next = false;
            case "toggle" -> next = !reset.enabled();
            default -> {
                sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                        + " autoreset [on|off|toggle]</white>"));
                return;
            }
        }
        reset.setEnabled(next);
        sender.sendMessage(plugin.msg("<gray>Auto-reset on round start is now "
                + (next ? "<green>ON</green>" : "<red>OFF</red>") + ".</gray>"));
    }

    private void keepInventory(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        KeepInventoryService keep = plugin.keepInventory();
        if (args.length < 2) {
            sender.sendMessage(plugin.msg("<gray>Keep inventory during rounds is currently "
                    + (keep.enabled() ? "<green>ON</green>" : "<red>OFF</red>")
                    + ". Use <white>/" + label + " keepinventory <on|off|toggle></white>."));
            return;
        }
        boolean next;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable" -> next = true;
            case "off", "false", "disable" -> next = false;
            case "toggle" -> next = !keep.enabled();
            default -> {
                sender.sendMessage(plugin.msg("<gray>Usage: <white>/" + label
                        + " keepinventory [on|off|toggle]</white>"));
                return;
            }
        }
        keep.setEnabled(next);
        sender.sendMessage(plugin.msg("<gray>Keep inventory during rounds is now "
                + (next ? "<green>ON</green>" : "<red>OFF</red>") + ".</gray>"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("tlockout.admin")) {
            deny(sender);
            return;
        }
        try {
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("<green>Reloaded config.yml and goals.yml ("
                    + plugin.catalog().size() + " templates). Any running challenge was stopped.</green>"));
        } catch (RuntimeException ex) {
            sender.sendMessage(plugin.msg("<red>Reload failed: " + ex.getMessage()));
        }
    }

    // ---------------------------------------------------------------- helper

    private String sideIds() {
        List<String> ids = new ArrayList<>();
        for (Side side : sides().ordered()) {
            ids.add(side.id());
        }
        return String.join(", ", ids);
    }

    private String firstSideId() {
        List<Side> ordered = sides().ordered();
        return ordered.isEmpty() ? "red" : ordered.get(0).id();
    }

    private void help(CommandSender sender, String label) {
        String version = plugin.getPluginMeta().getVersion();
        sender.sendMessage(plugin.msg("<gold><bold>TLockout</bold></gold> <aqua>v" + version
                + "</aqua> <dark_gray>-</dark_gray> <gray>usage"));
        sender.sendMessage(Component.text("  /" + label + " join [team|color]", NamedTextColor.WHITE)
                .append(Component.text(" - join a team (or FFA with optional colour)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " leave", NamedTextColor.WHITE)
                .append(Component.text(" - leave your team / opt out of FFA", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " mode [teams|ffa]", NamedTextColor.WHITE)
                .append(Component.text(" - switch between Teams and FFA mode (admin)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " goalstowin [1-25]", NamedTextColor.WHITE)
                .append(Component.text(" - set goals needed to win (admin)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " forcewin <team|player>", NamedTextColor.WHITE)
                .append(Component.text(" - force end the round and award the win (admin)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " team list", NamedTextColor.WHITE)
                .append(Component.text(" - show team rosters", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " start [seed] [--allow-empty]", NamedTextColor.WHITE)
                .append(Component.text(" - start a round (admin; --allow-empty for testing)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " reroll [seed]", NamedTextColor.WHITE)
                .append(Component.text(" - stop and start a fresh testing board (admin)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " team auto|set", NamedTextColor.WHITE)
                .append(Component.text(" - balance or move players (admin)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " track <1-25|clear>", NamedTextColor.WHITE)
                .append(Component.text(" - track goals on your team's HUD", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " board", NamedTextColor.WHITE)
                .append(Component.text(" - open the board", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " compass", NamedTextColor.WHITE)
                .append(Component.text(" - get a Board Compass if you lost yours", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " status", NamedTextColor.WHITE)
                .append(Component.text(" - show scores and claims", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " autoreset [on|off|toggle]", NamedTextColor.WHITE)
                .append(Component.text(" - toggle clean-match player reset on start (admin)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " keepinventory [on|off|toggle]", NamedTextColor.WHITE)
                .append(Component.text(" - toggle keeping inventory & XP on death during rounds (admin)",
                        NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /" + label + " stop | reload", NamedTextColor.WHITE)
                .append(Component.text(" - end the round / reload config (admin)", NamedTextColor.GRAY)));
    }

    private void deny(CommandSender sender) {
        sender.sendMessage(plugin.msg("<red>You don't have permission to do that."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            String current = args.length > 1 ? args[args.length - 1] : "";
            boolean hasFlag = false;
            for (int i = 1; i < args.length - 1; i++) {
                if (args[i].equalsIgnoreCase("--allow-empty")) {
                    hasFlag = true;
                }
            }
            return hasFlag ? List.of() : filter(List.of("--allow-empty"), current);
        }
        if (args.length == 2) {
            if (sub.equals("mode")) {
                return filter(List.of("teams", "ffa"), args[1]);
            }
            if (sub.equals("goalstowin")) {
                return filter(List.of("1", "3", "5", "8", "10", "13", "15", "20", "25"), args[1]);
            }
            if (sub.equals("forcewin")) {
                List<String> options = new ArrayList<>();
                for (Side side : sides().ordered()) {
                    options.add(sides().isFfa() ? side.displayName() : side.id());
                }
                return filter(options, args[1]);
            }
            if (sub.equals("join")) {
                return filter(sides().isFfa() ? ConcreteColors.colorNames() : sideIdList(), args[1]);
            }
            if (sub.equals("team")) {
                return filter(List.of("list", "auto", "set"), args[1]);
            }
            if (sub.equals("autoreset") || sub.equals("keepinventory")) {
                return filter(List.of("on", "off", "toggle"), args[1]);
            }
            if (sub.equals("track")) {
                List<String> options = new ArrayList<>();
                options.add("clear");
                for (int i = 1; i <= Board.CELLS; i++) {
                    options.add(String.valueOf(i));
                }
                return filter(options, args[1]);
            }
        }
        if (sub.equals("team") && args.length >= 3 && args[1].equalsIgnoreCase("set")) {
            if (args.length == 3) {
                List<String> names = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    names.add(player.getName());
                }
                return filter(names, args[2]);
            }
            if (args.length == 4) {
                return filter(sideIdList(), args[3]);
            }
        }
        return List.of();
    }

    private List<String> sideIdList() {
        List<String> ids = new ArrayList<>();
        for (Side side : sides().ordered()) {
            ids.add(side.id());
        }
        return ids;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}

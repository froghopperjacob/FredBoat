/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.audio.player;

import io.prometheus.client.Gauge;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerRegistry {

    public static final float DEFAULT_VOLUME = 1f;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Gauge playingPlayers = new Gauge.Builder()
            .name("fredboat_music_players_playing_total")
            .help("Total playing music players")
            .register();

    private final Map<Long, GuildPlayer> REGISTRY = Collections.synchronizedMap(new HashMap<>());

    private PlayerRegistry() {
        //update gauge of playing players regularly
        scheduler.scheduleAtFixedRate(() -> {
            try {
                playingPlayers.set(playingCount());
            } catch (Exception ignored) {
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    //internal holder pattern
    private static PlayerRegistry instance() {
        return RegistryHolder.INSTANCE;
    }

    private static class RegistryHolder {
        private static final PlayerRegistry INSTANCE = new PlayerRegistry();
    }

    public static void put(long guildId, GuildPlayer guildPlayer) {
        instance().REGISTRY.put(guildId, guildPlayer);
    }

    public static GuildPlayer get(Guild guild) {
        return get(guild.getJDA(), guild.getIdLong());
    }

    public static GuildPlayer get(JDA jda, long guildId) {
        GuildPlayer player = instance().REGISTRY.get(guildId);
        if (player == null) {
            player = new GuildPlayer(jda.getGuildById(guildId));
            player.setVolume(DEFAULT_VOLUME);
            instance().REGISTRY.put(guildId, player);
        }

        // Attempt to set the player as a sending handler. Important after a shard revive
        if (!LavalinkManager.ins.isEnabled() && jda.getGuildById(guildId) != null) {
            jda.getGuildById(guildId).getAudioManager().setSendingHandler(player);
        }

        return player;
    }

    public static GuildPlayer getExisting(Guild guild) {
        return getExisting(guild.getJDA(), guild.getIdLong());
    }

    public static GuildPlayer getExisting(JDA jda, long guildId) {
        if (instance().REGISTRY.containsKey(guildId)) {
            return get(jda, guildId);
        }
        return null;
    }

    public static GuildPlayer remove(long guildId) {
        return instance().REGISTRY.remove(guildId);
    }

    public static Map<Long, GuildPlayer> getRegistry() {
        return instance().REGISTRY;
    }

    public static List<GuildPlayer> getPlayingPlayers() {
        return instance().REGISTRY.values().stream()
                .filter(GuildPlayer::isPlaying)
                .collect(Collectors.toList());
    }

    public static void destroyPlayer(Guild g) {
        destroyPlayer(g.getJDA(), g.getIdLong());
    }

    public static void destroyPlayer(JDA jda, long guildId) {
        GuildPlayer player = getExisting(jda, guildId);
        if (player != null) {
            player.destroy();
            remove(guildId);
        }
    }

    private long playingCount() {
        return REGISTRY.values().stream()
                .filter(GuildPlayer::isPlaying)
                .count();
    }
}

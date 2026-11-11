package com.colen.tempora.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.Nullable;

import com.colen.tempora.Tempora;
import com.colen.tempora.loggers.generic.column.names.NameRole;
import com.colen.tempora.loggers.generic.column.names.NameRoles;
import com.colen.tempora.networking.packets.PacketNameFilter;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Names of every item, block, mob, achievement and anything else of a NameRole as this client shows them, so an f:
// filter matches what the player sees in their own language. Built the first time it's needed and again after the
// language changes. Each role lists its own names (NameRole.shownNames), the same ones lookup results show
@SideOnly(Side.CLIENT)
public final class ClientNameIndex {

    // One thing of a role and the name it's shown by
    private static final class Named {

        final NameRole role;
        final Object[] values;
        // Through TextFold.fold
        final String name;

        Named(NameRole role, Object[] values, String name) {
            this.role = role;
            this.values = values;
            this.name = TextFold.fold(name);
        }
    }

    private static final class Request {

        final int id;
        final String text;

        Request(int id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private static @Nullable String builtFor;
    // Loggers other mods register later can bring roles of their own
    private static int builtForRoles;
    private static List<Named> names = new ArrayList<>();

    // Requests from the server waiting for the client thread
    private static final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();

    // Any thread, from PacketResolveNames
    public static void requested(int requestID, String text) {
        requests.add(new Request(requestID, text));
    }

    // Answers the server's requests on the client thread. One that fails gets no answer, the server then tells the
    // player it got none
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Request request;
        while ((request = requests.poll()) != null) {
            try {
                Tempora.NETWORK.sendToServer(new PacketNameFilter(request.id, resolve(request.text).fitForPacket()));
            } catch (Exception e) {
                Tempora.LOG.error("Couldn't work out what f:{} names.", request.text, e);
            }
        }
    }

    public static ResolvedNames resolve(String text) {
        rebuildIfNeeded();

        List<String> terms = TextFold.terms(text);
        ResolvedNames.Builder builder = new ResolvedNames.Builder();
        for (Named named : names) {
            if (TextFold.matchesShownName(terms, named.name)) builder.add(named.role, named.values);
        }
        return builder.build();
    }

    private static void rebuildIfNeeded() {
        String language = Minecraft.getMinecraft()
            .getLanguageManager()
            .getCurrentLanguage()
            .getLanguageCode();
        List<NameRole> roles = NameRoles.known();
        if (language.equals(builtFor) && roles.size() == builtForRoles) return;

        long start = System.currentTimeMillis();
        names = allNames(roles);
        builtFor = language;
        builtForRoles = roles.size();
        Tempora.LOG.info(
            "Indexed {} names of {} kinds of things in {} for f: filters in {} ms.",
            names.size(),
            roles.size(),
            language,
            System.currentTimeMillis() - start);
    }

    private static List<Named> allNames(List<NameRole> roles) {
        List<Named> names = new ArrayList<>();
        for (NameRole role : roles) {
            try {
                role.shownNames((name, values) -> {
                    if (name != null && !name.isEmpty() && NameRoles.fits(role, values))
                        names.add(new Named(role, values.clone(), name));
                });
            } catch (Throwable t) {
                // Whatever it gave before failing still counts
                Tempora.LOG.error(
                    "Couldn't list the names of {} for f: filters.",
                    role.getClass()
                        .getName(),
                    t);
            }
        }
        return names;
    }
}

package com.colen.tempora.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;

import com.colen.tempora.networking.PlayerPackets;
import com.colen.tempora.networking.packets.PacketResolveNames;

// Asks a player's client what an f: filter's text matches, since names are only known in their language on their
// client. The lookup waits for the answer and runs when it comes back, or fails if it doesn't come in time. Answers
// get handled on the server tick, whatever thread the packet arrived on
public final class NameRequests {

    // Long enough for a client to index a full pack the first time (well under a second) on a slow connection
    public static final long ANSWER_TIMEOUT_MILLIS = 10_000;

    private static final AtomicInteger nextID = new AtomicInteger();
    private static final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Answer> answers = new ConcurrentLinkedQueue<>();

    private NameRequests() {}

    private static final class Pending {

        final UUID player;
        final String text;
        // Server thread, with the filter for the text as asked and the names the client found for it
        final Consumer<TextFilter> resolved;
        // Server thread, named too much or no answer in time
        final Consumer<CommandException> failed;
        final long deadline;

        Pending(UUID player, String text, Consumer<TextFilter> resolved, Consumer<CommandException> failed,
            long deadline) {
            this.player = player;
            this.text = text;
            this.resolved = resolved;
            this.failed = failed;
            this.deadline = deadline;
        }
    }

    private static final class Answer {

        final UUID player;
        final int requestID;
        final ResolvedNames names;

        Answer(UUID player, int requestID, ResolvedNames names) {
            this.player = player;
            this.requestID = requestID;
            this.names = names;
        }
    }

    // Asks the player's client and returns the request's ID. A player with no connection never answers, so that
    // times out like a client that doesn't
    public static int ask(EntityPlayerMP player, String text, Consumer<TextFilter> resolved,
        Consumer<CommandException> failed) {
        int id = nextID.incrementAndGet();
        pending.put(
            id,
            new Pending(
                player.getUniqueID(),
                text,
                resolved,
                failed,
                System.currentTimeMillis() + ANSWER_TIMEOUT_MILLIS));
        PlayerPackets.sendTo(new PacketResolveNames(id, text), player);
        return id;
    }

    // Any thread. Any client can send one, so it only counts for a request that player made
    public static void answered(EntityPlayerMP player, int requestID, ResolvedNames names) {
        answers.add(new Answer(player.getUniqueID(), requestID, names));
    }

    // Server tick. Runs the callbacks of answered requests, then fails ones past their deadline
    public static void onServerTick() {
        Answer answer;
        while ((answer = answers.poll()) != null) {
            Pending request = pending.get(answer.requestID);
            // Late, made up, or someone else's
            if (request == null || !request.player.equals(answer.player)) continue;
            pending.remove(answer.requestID);

            TextFilter filter;
            try {
                filter = TextFilter.of(request.text, answer.names);
            } catch (CommandException e) {
                request.failed.accept(e);
                continue;
            }
            request.resolved.accept(filter);
        }

        long now = System.currentTimeMillis();
        Iterator<Pending> it = pending.values()
            .iterator();
        while (it.hasNext()) {
            Pending request = it.next();
            if (now < request.deadline) continue;
            it.remove();
            request.failed.accept(new CommandException("tempora.command.lookup.name_no_answer", request.text));
        }
    }
}

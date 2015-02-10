package io.mesosphere.mesos.util;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import org.apache.mesos.Protos;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public final class Functions {

    private Functions() {}

    @NotNull
    public static <A> Optional<A> headOption(@NotNull final Iterable<A> iterable) {
        final Iterator<A> iter = iterable.iterator();
        if (iter.hasNext()) {
            return Optional.fromNullable(iter.next());
        } else {
            return Optional.absent();
        }
    }

    @NotNull
    @SafeVarargs
    public static <K, V> Map<K, V> unmodifiableHashMap(@NotNull final Tuple2<K, V>... tuples) {
        final Map<K, V> map = Maps.newHashMap();
        for (final Tuple2<K, V> tuple : tuples) {
            map.put(tuple._1, tuple._2);
        }
        return Collections.unmodifiableMap(map);
    }

    @NotNull
    public static Function<String, Protos.CommandInfo.URI> extract() {
        return CommandInfoUri.INSTANCE_EXTRACT;
    }

    @NotNull
    public static Function<String, Protos.CommandInfo.URI> doNotExtract() {
        return CommandInfoUri.INSTANCE;
    }

    private static final class CommandInfoUri implements Function<String, Protos.CommandInfo.URI> {
        private static final CommandInfoUri INSTANCE = new CommandInfoUri();

        private static final CommandInfoUri INSTANCE_EXTRACT = new CommandInfoUri(true);

        private final boolean extract;

        public CommandInfoUri() {
            this(false);
        }

        public CommandInfoUri(final boolean extract) {
            this.extract = extract;
        }
        @Override
        @NotNull
        public Protos.CommandInfo.URI apply(final String input) {
            final boolean shouldExtract = extract;
            return ProtoUtils.commandUri(input, shouldExtract);
        }
    }

}

package redis.commands;

import redis.storage.Entry;

import java.util.ArrayList;
import java.util.List;

final class ReadStorageEntries {
    private ReadStorageEntries() {
    }

    static Effect readEntries(List<Entry> entries) {
        List<Effect> effect = new ArrayList<>(entries.size());
        for (var entry : entries) {
            List<Effect> tupleOnes = new ArrayList<>(entry.tuples().size() * 2);
            for (var tuple : entry.tuples()) {
                tupleOnes.add(Messages.bulkMessage(tuple.key()));
                tupleOnes.add(Messages.bulkMessage(tuple.value()));
            }
            var singleEntry = Messages.array(Messages.bulkMessage(entry.id()), Messages.array(tupleOnes));
            effect.add(singleEntry);
        }
        return Messages.array(effect);

    }
}

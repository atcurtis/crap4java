package org.xiphis.collection;

import net.jcip.annotations.NotThreadSafe;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @param <K>
 * @param <V>
 */
@NotThreadSafe
public class SinisaHashMap<K, V> extends AbstractMap<K, V> implements MultiMap<K, V>, java.io.Serializable {

    private final List<HashLink<K, V>> entries;
    private int bLength = 1;
    private transient Set<Entry<K, V>> entrySet;
    private transient Set<K> keySet;
    private transient int modCount;

    private static final int NO_RECORD = -1;
    private static final int LOWFIND = 1;
    private static final int LOWUSED = 2;
    private static final int HIGHFIND = 4;
    private static final int HIGHUSED = 8;
    private static final int HALFBUFF = 16;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final HashLink EMPTY = new HashLink(-1, null, null);

    public SinisaHashMap() {
        this(null);
    }

    public SinisaHashMap(Map<K, V> map) {
        if (map instanceof SinisaHashMap) {
            SinisaHashMap<K, V> smap = (SinisaHashMap<K, V>) map;
            entries = smap.entries.stream().map(HashLink::new).collect(Collectors.toList());
            bLength = smap.bLength;
        } else if (map instanceof MultiMap) {
            entries = new ArrayList<>();
            map.forEach(this::add);
        } else {
            entries = new ArrayList<>();
            if (map != null) {
                putAll(map);
            }
        }
    }

    protected int hashValue(K object) {
        return object.hashCode();
    }

    private int hashValueObject(Object object) {
        //noinspection unchecked,rawtypes
        return ((SinisaHashMap) this).hashValue(object);
    }

    private static int hashMask(int hashNr, int buffMax, int maxLength) {
        int result = hashNr & (buffMax - 1);
        if (result < maxLength) {
            return result;
        }
        return hashNr & ((buffMax >> 1) - 1);
    }

    HashLink<K, V> search(int hashCode, Object key) {
        int size = entries.size();
        boolean flag = true;
        if (size > 0) {
            int idx = hashMask(hashCode, bLength, size);
            HashLink<K, V> entry;
            do {
                entry = entries.get(idx);
                if (Objects.equals(key, entry.getKey())) {
                    return entry;
                }
                if (flag) {
                    flag = false;
                    if (hashMask(entry.getKeyHash(), bLength, size) != idx) {
                        break;
                    }
                }
            } while ((idx = entry.getNext()) != NO_RECORD);
        }
        return null;
    }

    @Override
    public Collection<V> getAll(K key) {

        HashLink<K, V> entry = search(hashValue(key), key);
        if (entry == null) {
            return Collections.emptyList();
        }
        if (entry.getNext() == NO_RECORD) {
            return Collections.singletonList(entry.getValue());
        }
        LinkedList<V> results = new LinkedList<>();
        results.add(entry.getValue());
        do {
            entry = entries.get(entry.getNext());
            if (Objects.equals(key, entry.getKey())) {
                results.addFirst(entry.getValue());
            }
        } while (entry.getNext() != NO_RECORD);
        return results;
    }

    @Override
    public boolean containsKey(Object key) {
        return search(hashValueObject(key), key) != null;
    }

    @Override
    public V get(Object key) {
        HashLink<K, V> entry = search(hashValueObject(key), key);
        return entry != null ? entry.getValue() : null;
    }

    private void set(int index, HashLink<K, V> entry) {
        entries.set(index, Objects.requireNonNull(entry));
    }


    @Override
    public V put(K key, V value) {
        int hashValue = hashValue(key);
        HashLink<K, V> entry = search(hashValue, key);
        if (entry != null) {
            key = entry.getKey();
            if (entry.getNext() == NO_RECORD || !Objects.equals(key, entries.get(entry.getNext()).getKey())) {
                return entry.setValue(value);
            }
        }
        add(key, value);
        return null;
    }

    @Override
    public void add(K key, V value) {
        add(hashValue(key), key, value);
    }

    private void add(final int keyHashValue, K key, V value) {
        int halfbuff = bLength >> 1;
        int firstIndex;
        int flag = 0;
        int records = entries.size();
        int empty = records;
        int gpos = NO_RECORD, gpos2 = NO_RECORD, pos;
        //noinspection unchecked
        entries.add(EMPTY);
        modCount++;
        HashLink<K, V> ptr = null, ptr2 = null;
        HashLink<K, V> p;
        pos = firstIndex = records - halfbuff;
        if (pos != records) {
            do {
                p = entries.get(pos);
                int hashNr = p.getKeyHash();
                if (keyHashValue == hashNr && Objects.equals(key, p.getKey())) {
                    key = p.getKey();
                }
                if (flag == 0) {  // first loop, check if ok
                    if (hashMask(hashNr, bLength, records) != firstIndex) {
                        break;
                    }
                }

                switch ((hashNr & halfbuff) == 0 ? flag & ~HALFBUFF : flag | HALFBUFF) {
                    case HIGHFIND:
                    case HIGHFIND|HIGHUSED:
                        flag = LOWFIND | HIGHFIND;
                        /* key shall be moved to the current empty position */
                        gpos = empty;
                        empty = pos; // This place is now free
                        ptr = entries.get(pos);
                        break;
                    case 0:
                        flag = LOWFIND | LOWUSED; // key isn't changed
                        gpos = pos;
                        ptr = entries.get(pos);
                        break;
                    case LOWFIND:
                    case LOWFIND|HIGHFIND:
                    case LOWFIND|HIGHFIND|HIGHUSED:
                        // change link of previous LOW key
                        set(gpos, ptr.setNext(pos));
                        flag &= HIGHFIND;
                        flag |= LOWFIND | LOWUSED;
                        // fall through
                    case LOWFIND|LOWUSED:
                    case LOWFIND|LOWUSED|HIGHFIND:
                    case LOWFIND|LOWUSED|HIGHFIND|HIGHUSED:
                        gpos = pos;
                        ptr = entries.get(pos);
                        break;
                    case HALFBUFF:
                    case HALFBUFF|LOWFIND:
                    case HALFBUFF|LOWFIND|LOWUSED:
                        flag &= LOWFIND;
                        flag |= HIGHFIND;
                        // key shall be moved to the last (empty) position
                        gpos2 = empty;
                        empty = pos;
                        ptr2 = entries.get(pos);
                        break;
                    case HALFBUFF|HIGHFIND:
                    case HALFBUFF|HIGHFIND|LOWFIND:
                    case HALFBUFF|HIGHFIND|LOWFIND|LOWUSED:
                        // Change link of previous hash key and save
                        set(gpos2, ptr2.setNext(pos));
                        flag &= LOWFIND;
                        flag |= HIGHFIND | HIGHUSED;
                        // fall through
                    case HALFBUFF|HIGHFIND|HIGHUSED:
                    case HALFBUFF|HIGHFIND|HIGHUSED|LOWFIND:
                    case HALFBUFF|HIGHFIND|HIGHUSED|LOWFIND|LOWUSED:
                        gpos2 = pos;
                        ptr2 = entries.get(pos);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            } while ((pos = entries.get(pos).getNext()) != NO_RECORD);

            if ((flag & (LOWFIND | LOWUSED)) == LOWFIND) {
                set(gpos, ptr.setNext(NO_RECORD));
            }
            if ((flag & (HIGHFIND | HIGHUSED)) == HIGHFIND) {
                set(gpos2, ptr2.setNext(NO_RECORD));
            }
        }

        pos = hashMask(keyHashValue, bLength, records + 1);
        if (pos == empty) {
            set(pos, new HashLink<>(keyHashValue, key, value));
        } else {
            HashLink<K, V> tmp = entries.get(pos);
            set(empty, tmp);
            int tmpHashValue;
            if (Objects.equals(key, tmp.getKey())) {
                key = tmp.getKey();
                tmpHashValue = keyHashValue;
            } else {
                tmpHashValue = tmp.getKeyHash();
            }
            gpos = hashMask(tmpHashValue, bLength, records + 1);
            if (pos == gpos) {
                set(pos, new HashLink<>(keyHashValue, key, value).setNext(empty));
            } else {
                set(pos, new HashLink<>(keyHashValue, key, value));
                moveLink(pos, gpos, empty);
            }
        }
        assert entries.size() == records + 1;
        if (records + 1 == bLength) {
            bLength += bLength;
        }
    }

    private void moveLink(int find, int nextLink, int newLink) {
        HashLink<K, V> oldLink;
        do {
            oldLink = entries.get(nextLink);
        } while ((nextLink = oldLink.getNext()) != find && nextLink != NO_RECORD);
        oldLink.setNext(newLink);
    }

    @Override
    public V remove(Object key) {
        final int records = entries.size();
        if (records < 1) {
            return null;
        }
        int bLength = this.bLength;
        int pos = hashMask(hashValueObject(key), bLength, records);
        int gpos = NO_RECORD;
        HashLink<K, V> entry;

        while (!Objects.equals(key, (entry = entries.get(pos)).getKey())) {
            gpos = pos;
            if (entry.getNext() == NO_RECORD) {
                return null;
            }
            pos = entry.getNext();
        }
        V found = entry.getValue();
        int lastPos = records - 1;
        if (lastPos < this.bLength >> 1) {
            this.bLength >>= 1;
        }
        //noinspection ConstantValue
        do {
            int empty = pos;
            if (gpos != NO_RECORD) {
                entries.get(gpos).setNext(entry.getNext()); // unlink current ptr
            } else if (entry.getNext() != NO_RECORD) {
                empty = entry.getNext();
                set(pos, entries.get(empty));
            }

            if (empty == lastPos) {
                break;
            }

            // move the last key
            int lastPosHash = entries.get(lastPos).getKeyHash();
            // pos is where lastpos should be
            pos = hashMask(lastPosHash, this.bLength, records - 1);
            if (pos == empty) { // Move to empty position
                entries.set(empty, entries.get(lastPos));
                break;
            }
            entry = entries.get(pos);
            int posHash = entry.getKeyHash();
            // pos3 is where the pos should be
            int pos3 = hashMask(posHash, this.bLength, records - 1);
            if (pos != pos3) {
                // pos is on wrong position
                set(empty, entry); // save it here
                set(pos, entries.get(lastPos)); // this should be here
                moveLink(pos, pos3, empty);
                break;
            }
            int idx;
            int pos2 = hashMask(lastPosHash, bLength, records);
            if (pos2 == hashMask(posHash, bLength, records)) {
                if (pos2 != records - 1) {
                    set(empty, entries.get(lastPos));
                    moveLink(lastPos, pos, empty);
                    break;
                }
                idx = pos;
            } else {
                idx = NO_RECORD;
            }

            set(empty, entries.get(lastPos));
            entry = entries.get(pos);
            moveLink(idx, empty, entry.getNext());
            entry.setNext(empty);
        } while (false);

        entries.remove(lastPos);
        modCount++;
        return found;
    }

    @Override
    public void clear() {
        entries.clear();
        bLength = 1;
        keySet = null;
        modCount++;
    }

    private <E> E modCheck(int mod, E value) {
        if (modCount != mod) {
            throw new ConcurrentModificationException();
        }
        return value;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        if (entrySet == null) {
            entrySet = new AbstractSet<Entry<K, V>>() {
                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return new Iterator<>() {
                        final int mod = modCount;
                        final Iterator<HashLink<K, V>> it = entries.iterator();
                        @Override
                        public boolean hasNext() {
                            return modCheck(mod, it).hasNext();
                        }

                        @Override
                        public Entry<K, V> next() {
                            return modCheck(mod, it).next();
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }
        return entrySet;
    }

    public Set<K> keySet() {
        if (keySet == null) {
            keySet = new AbstractSet<K>() {

                Stream<K> distinctStream() {
                    return entries.stream().map(HashLink::getKey).distinct();
                }

                public Iterator<K> iterator() {
                    return new Iterator<>() {

                        final Iterator<K> it = distinctStream().iterator();

                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        public K next() {
                            return it.next();
                        }
                    };
                }

                public int size() {
                    return Math.toIntExact(distinctStream().count());
                }

                public boolean contains(Object k) {
                    return containsKey(k);
                }
            };
        }
        return keySet;
    }

    public static <K, V> int check(Map<K, V> map) {
        SinisaHashMap<K, V> smap = (SinisaHashMap<K, V>) map;
        List<HashLink<K, V>> entries = smap.entries;

        int error;
        int i,recLink,found,max_links,seek,links,idx;
        int records;
        int blength;
        HashLink<K, V> hashInfo;
        Logger log = Logger.getLogger(smap.getClass().getName());

        records = entries.size(); blength = smap.bLength;
        error=0;

        for (i=found=max_links=seek=0 ; i < records ; i++) {
            if (hashMask(entries.get(i).getKeyHash(), blength, records) == i) {
                found++; seek++; links=1;
                for (idx = entries.get(i).getNext() ;
                     idx != NO_RECORD && found < records + 1;
                     idx = hashInfo.getNext()) {
                    if (idx >= records) {
                        log.warning(String.format(
                                "Found pointer outside array to %d from link starting at %d",
                                idx,i));
                        error=1;
                    }
                    hashInfo = entries.get(idx);
                    seek += ++links;
                    if ((recLink= hashMask(hashInfo.getKeyHash(), blength, records)) != i) {
                        log.warning(String.format(
                                "Record in wrong link at %d: Start %d  Record: 0x%x  Record-link %d",
                                idx, i, (long) System.identityHashCode(hashInfo.getValue()), recLink));
                        error=1;
                    } else {
                        found++;
                    }
                }
                if (links > max_links) {
                    max_links=links;
                }
            }
        }
        if (found != records) {
            log.warning(String.format("Found %d of %d records", found, records));
            error=1;
        }
        if (records != 0) {
            log.info(String.format("records: %d   seeks: %d   max links: %d   hitrate: %.2f",
                    records,seek,max_links,(float) seek / (float) records));
        }
        return error;
    }

    public static final class HashLink<K, V> extends SimpleEntry<K, V> {
        private static final long serialVersionUID = -7499721149061103585L;
        private final int keyHash;
        private int next = NO_RECORD;

        HashLink(HashLink<K, V> link) {
            super(link.getKey(), link.getValue());
            this.keyHash = link.getKeyHash();
            this.next = link.next;
        }

        HashLink(int keyHash, K key, V value) {
            super(key, value);
            this.keyHash = keyHash;
        }

        public int getKeyHash() {
            return keyHash;
        }

        public int getNext() {
            return next;
        }

        HashLink<K, V> setNext(int next) {
            this.next = next;
            return this;
        }
    }

    private abstract class AbstractSet<E> extends java.util.AbstractSet<E> {
        @Override
        public void clear() {
            SinisaHashMap.this.clear();
        }

        @Override
        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }
}

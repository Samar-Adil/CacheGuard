package com.cacheguard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared machinery for LRU and MRU: a {@link HashMap} for O(1) lookup plus a doubly
 * linked list kept in recency order (most recently used right after the head sentinel).
 * Hits, inserts and evictions are all O(1) pointer updates.
 */
abstract class RecencyListCache<K> extends AbstractCache<K> {

    private static final class Node<K> {
        final K key;
        Node<K> prev;
        Node<K> next;

        Node(K key) {
            this.key = key;
        }
    }

    private final Map<K, Node<K>> index;
    private final Node<K> head = new Node<>(null); // head.next = most recently used
    private final Node<K> tail = new Node<>(null); // tail.prev = least recently used

    RecencyListCache(CacheAlgorithm algorithm, int capacity) {
        super(algorithm, capacity);
        this.index = new HashMap<>(Math.max(16, capacity * 2));
        head.next = tail;
        tail.prev = head;
    }

    /** True for MRU (evict the newest), false for LRU (evict the oldest). */
    protected abstract boolean evictsMostRecent();

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public boolean contains(K key) {
        return index.containsKey(key);
    }

    @Override
    protected void onHit(K key) {
        Node<K> node = index.get(key);
        unlink(node);
        linkFirst(node);
    }

    @Override
    protected K evict() {
        Node<K> victim = victimNode();
        unlink(victim);
        index.remove(victim.key);
        return victim.key;
    }

    @Override
    protected void insert(K key) {
        Node<K> node = new Node<>(key);
        index.put(key, node);
        linkFirst(node);
    }

    @Override
    protected Optional<K> peekVictim() {
        return Optional.of(victimNode().key);
    }

    @Override
    protected void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public List<K> contents() {
        List<K> out = new ArrayList<>(index.size());
        if (evictsMostRecent()) {
            for (Node<K> n = head.next; n != tail; n = n.next) {
                out.add(n.key);
            }
        } else {
            for (Node<K> n = tail.prev; n != head; n = n.prev) {
                out.add(n.key);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** "recent #1" is the most recently used key. O(n). */
    @Override
    public String annotation(K key) {
        int rank = 1;
        for (Node<K> n = head.next; n != tail; n = n.next, rank++) {
            if (n.key.equals(key)) {
                return "recent #" + rank;
            }
        }
        return "";
    }

    private Node<K> victimNode() {
        return evictsMostRecent() ? head.next : tail.prev;
    }

    private void unlink(Node<K> n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void linkFirst(Node<K> n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }
}

package net.ultragrav.asyncworld.test.utils;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Based on ArrayList, but uses a lock to prevent concurrent access
 *
 * @param <E> Type of list
 */
public class LockingList<E> extends ArrayList<E> {
    @Getter
    private final CustomLock lock = new CustomLock(true);

    @Override
    public int size() {
        return lock.perform(super::size);
    }

    @Override
    public boolean isEmpty() {
        return lock.perform(super::isEmpty);
    }

    @Override
    public boolean contains(Object o) {
        return lock.perform(() -> super.contains(o));
    }

    public LockingList<E> copy() {
        LockingList<E> lockingList = new LockingList<>();
        lock.perform(() -> lockingList.addAll(this));
        return lockingList;
    }

    @NotNull
    @Override
    public Iterator<E> iterator() { //NOTE do not use iterator.remove() with this cuz it won't do anything
        List<E> list = new ArrayList<>();
        lock.perform(() -> list.addAll(this));
        return list.iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return lock.perform((Supplier<Object[]>) super::toArray);
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return lock.perform(() -> super.toArray(a));
    }

    @Override
    public boolean add(E e) {
        return lock.perform(() -> super.add(e));
    }

    public boolean addInternal(E e) {
        return super.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return lock.perform(() -> super.remove(o));
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return lock.perform(() -> super.containsAll(c));
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        return lock.perform(() -> super.addAll(c));
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        return lock.perform(() -> super.addAll(index, c));
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return lock.perform(() -> super.removeAll(c));
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return lock.perform(() -> super.retainAll(c));
    }

    @Override
    public void clear() {
        lock.perform(super::clear);
    }

    @Override
    public E get(int index) {
        return lock.perform(() -> super.get(index));
    }

    @Override
    public E set(int index, E element) {
        return lock.perform(() -> super.set(index, element));
    }

    @Override
    public void add(int index, E element) {
        lock.perform(() -> super.add(index, element));
    }

    @Override
    public E remove(int index) {
        return lock.perform(() -> super.remove(index));
    }

    @Override
    public int indexOf(Object o) {
        return lock.perform(() -> super.indexOf(o));
    }

    @Override
    public int lastIndexOf(Object o) {
        return lock.perform(() -> super.lastIndexOf(o));
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return lock.perform((Supplier<ListIterator<E>>) super::listIterator);
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return lock.perform(() -> super.listIterator(index));
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return lock.perform(() -> super.subList(fromIndex, toIndex));
    }

    @Override
    public void forEach(Consumer<? super E> consumer) {
        lock.perform(() -> super.forEach(consumer));
    }
}

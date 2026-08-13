package com.smartfitagent.event;
import java.util.*; import java.util.concurrent.CopyOnWriteArrayList;
public class LearningEventBus { private final List<java.util.function.Consumer<LearningEvent>> listeners = new CopyOnWriteArrayList<>(); public void register(java.util.function.Consumer<LearningEvent> l){listeners.add(l);} public void publish(LearningEvent e){for(var l:listeners)l.accept(e);} }

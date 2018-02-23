/**
 *
 */
package edu.cuny.hunter.streamrefactoring.core.utils;

/**
 * @author raffi
 *
 */
public class TimeCollector {

	private long collectedTime;
	private long start;
	private boolean started;

	public void clear() {
		assert !started : "Shouldn't clear a running time collector.";

		collectedTime = 0;
	}

	public long getCollectedTime() {
		return collectedTime;
	}

	public void start() {
		assert !started : "Time colletor is already started.";
		started = true;

		start = System.currentTimeMillis();
	}

	public void stop() {
		assert started : "Trying to stop a time collector that isn't started.";
		started = false;

		final long elapsed = System.currentTimeMillis() - start;
		collectedTime += elapsed;
	}
}

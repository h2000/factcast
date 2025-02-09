package org.factcast.factus.projector;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projection.Projection;

@Slf4j
@Getter
public abstract class AbstractTransactionalLens implements ProjectorLens {
  final AtomicInteger count = new AtomicInteger();
  final AtomicLong start = new AtomicLong(0);
  protected final Class<? extends Projection> projectionName;

  @Setter protected int bulkSize = 1;
  @Setter protected long flushTimeout = 0;

  boolean flushCycle = false;

  public AbstractTransactionalLens(Projection projection) {
    projectionName = projection.getClass();
  }

  @Override
  public void beforeFactProcessing(Fact f) {
    // reset the value for this cycle
    flushCycle = false;

    if (bulkSize > 1) {
      long now = System.currentTimeMillis();
      start.getAndUpdate(
          l -> {
            return l > 0 ? l : now;
          });
    }
  }

  @Override
  public void afterFactProcessing(Fact f) {
    count.incrementAndGet();
    if (shouldFlush()) {
      flush();
    }
  }

  @VisibleForTesting
  public boolean shouldFlush() {
    return shouldFlush(false);
  }

  @VisibleForTesting
  public boolean shouldFlush(boolean withinProcessing) {

    if (flushCycle) {
      // it has been detected already that in this cycle, we're flushing
      return true;
    }

    int factsProcessed = count.get();
    if (withinProcessing) {
      // +1 because the increment happens AFTER processing
      factsProcessed++;
    }

    boolean bufferFull = factsProcessed >= bulkSize;
    if (bufferFull) {
      log.trace(
          "Bulk considered full on {}. (applied: {}, bulk size: {})",
          projectionName,
          factsProcessed,
          bulkSize);
    }

    boolean timedOut = timedOut();
    if (timedOut) {
      log.trace(
          "Bulk considered timed out on {}. (Bulk age: {}ms, Bulk timeout: {})",
          projectionName,
          System.currentTimeMillis() - start.get(),
          flushTimeout);
    }

    flushCycle = bufferFull || timedOut;
    return flushCycle;
  }

  private boolean timedOut() {
    return (flushTimeout > 0) && (System.currentTimeMillis() - start.get() > flushTimeout);
  }

  @Override
  public void onCatchup(Projection p) {
    if (count.get() > 0) {
      flush();
    }
    // disable bulk applying from here on
    if (isBulkApplying()) {
      log.debug("Disabling bulk application after catchup for {}", projectionName);
      bulkSize = 1;
      flushTimeout = 0;
    }
  }

  @VisibleForTesting
  public boolean isBulkApplying() {
    return bulkSize > 1;
  }

  @Override
  public boolean skipStateUpdate() {
    return isBulkApplying() && !shouldFlush(true);
  }

  public void flush() {
    if (bulkSize > 1) {
      start.set(0);
      int processed = count.getAndSet(0);
      if (processed > 0) {
        log.trace("Flushing on {}, number of facts processed={}", projectionName, processed);
      }
    }
    // otherwise we can silently commit, not to "flush" the logs
    doFlush();
  }

  @Override
  public void afterFactProcessingFailed(Fact f, Throwable justForInformation) {

    start.set(0);
    int rolledBack = count.getAndSet(0);

    log.warn(
        "Rolling back transaction on {} with number of facts processed={} for fact {} due to ",
        projectionName,
        rolledBack,
        f,
        justForInformation);

    doClear();
  }

  protected abstract void doClear();

  protected abstract void doFlush();
}
